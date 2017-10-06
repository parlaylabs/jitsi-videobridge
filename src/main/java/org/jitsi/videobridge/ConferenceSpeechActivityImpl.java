/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.videobridge;

import java.beans.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.json.simple.*;

/**
 * Standard implementation of <tt>ConferenceSpeechActivity</tt>,
 * represents the speech activity of the <tt>Endpoint</tt>s in a
 * <tt>Conference</tt>. Identifies the dominant speaker <tt>Endpoint</tt> in the
 * <tt>Conference</tt> and maintains an ordered list of the <tt>Endpoint</tt>s
 * in the <tt>Conference</tt> sorted by recentness of speaker domination and/or
 * speech activity.
 *
 * @author Lyubomir Marinov
 */
public class ConferenceSpeechActivityImpl
    extends ConferenceSpeechActivity
{
    /**
     * The pool of threads utilized by <tt>ConferenceSpeechActivityImpl</tt>.
     */
    private static final ExecutorService executorService
        = ExecutorUtils.newCachedThreadPool(true, "ConferenceSpeechActivityImpl");

    /**
     * The <tt>Logger</tt> used by the <tt>ConferenceSpeechActivityImpl</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(ConferenceSpeechActivityImpl.class);

    /**
     * Parses an <tt>Object</tt> as a synchronization source identifier (SSRC).
     *
     * @param obj the <tt>Object</tt> to parse as an SSRC
     * @return the SSRC represented by <tt>obj</tt> or <tt>-1</tt> if
     * <tt>obj</tt> could not be parsed as an SSRC
     */
    private static long parseSSRC(Object obj)
    {
        long l;

        if (obj == null)
        {
            l = -1L;
        }
        else if (obj instanceof Number)
        {
            l = ((Number) obj).longValue();
        }
        else
        {
            String s = obj.toString();

            if (s == null)
            {
                l = -1L;
            }
            else
            {
                try
                {
                    l = Long.parseLong(s);
                }
                catch (NumberFormatException ex)
                {
                    l = -1L;
                }
            }
        }
        return l;
    }

    /**
     * Resolves a synchronization source identifier (SSRC) of a received RTP
     * stream as an <tt>Endpoint</tt> identifier (ID).
     *
     * @param jsonObject the <tt>JSONObject</tt> from which the SSRC is to be
     * read and into which the <tt>Endpoint</tt> ID is to be written
     * @param ssrcKey the key in <tt>jsonObject</tt> with which the SSRC to be
     * resolved is associated
     * @param conference
     * @param endpointKey the key in <tt>jsonObject</tt> with which the resolved
     * <tt>Endpoint</tt> ID is to be associated
     */
    @SuppressWarnings("unchecked")
    private static void resolveSSRCAsEndpoint(
            JSONObject jsonObject,
            String ssrcKey,
            Conference conference,
            String endpointKey)
    {
        long ssrc = parseSSRC(jsonObject.get(ssrcKey));

        if (ssrc != -1)
        {
            Endpoint endpoint
                = conference.findEndpointByReceiveSSRC(ssrc, MediaType.AUDIO);

            if (endpoint != null)
            {
                jsonObject.put(endpointKey, endpoint.getID());
            }
        }
    }

    /**
     * The <tt>ActiveSpeakerChangedListener</tt> which listens to
     * {@link #activeSpeakerDetector} about changes in the active/dominant
     * speaker in this multipoint conference.
     */
    private final ActiveSpeakerChangedListener activeSpeakerChangedListener
        = new ActiveSpeakerChangedListener()
                {
                    @Override
                    public void activeSpeakerChanged(long ssrc)
                    {
                        ConferenceSpeechActivityImpl.this.activeSpeakerChanged(
                                ssrc);
                    }
                };

    /**
     * The <tt>ActiveSpeakerDetector</tt> which detects/identifies the
     * active/dominant speaker in {@link #conference}.
     */
    private ActiveSpeakerDetector activeSpeakerDetector;

    /**
     * The <tt>Object</tt> which synchronizes the access to
     * {@link #activeSpeakerDetector}.
     */
    private final Object activeSpeakerDetectorSyncRoot = new Object();

    /**
     * The <tt>Conference</tt> for which this instance represents the speech
     * activity of its <tt>Endpoint</tt>s. The <tt>Conference</tt> is weakly
     * referenced because <tt>ConferenceSpeechActivityImpl</tt> is a part of
     * <tt>Conference</tt> and the operation of the former in the absence of the
     * latter is useless.
     */
    private final WeakReference<Conference> conference;

    /**
     * The <tt>Endpoint</tt> which is the dominant speaker in
     * {@link #conference}.
     */
    private WeakReference<Endpoint> dominantEndpoint;

    /**
     * The indicator which signals to {@link #eventDispatcher} that
     * {@link #dominantEndpoint} was changed and <tt>eventDispatcher</tt> may
     * have to fire an event.
     */
    private boolean dominantEndpointChanged = false;

    /**
     * The <tt>DominantSpeakerIdentification</tt> instance, if any, employed by
     * {@link #activeSpeakerDetector}.
     */
    private DominantSpeakerIdentification dominantSpeakerIdentification;

    /**
     * The ordered list of <tt>Endpoint</tt>s participating in
     * {@link #conference} with the dominant (speaker) <tt>Endpoint</tt> at the
     * beginning of the list i.e. the dominant speaker history.
     */
    private List<WeakReference<Endpoint>> endpoints;

    /**
     * The indicator which signals to {@link #eventDispatcher} that the
     * <tt>endpoints</tt> set of {@link #conference} was changed and
     * <tt>eventDispatcher</tt> may have to fire an event.
     */
    private boolean endpointsChanged = false;

    /**
     * The background/daemon thread which fires <tt>PropertyChangeEvent</tt>s to
     * notify registered <tt>PropertyChangeListener</tt>s about changes of the
     * values of the <tt>dominantEndpoint</tt> and <tt>endpoints</tt> properties
     * of this instance.
     */
    private EventDispatcher eventDispatcher;

    /**
     * The time in milliseconds of the last execution of
     * {@link #eventDispatcher}.
     */
    private long eventDispatcherTime;

    /**
     * The <tt>PropertyChangeListener</tt> implementation employed by this
     * instance to listen to changes in the values of properties of interest to
     * this instance. For example, listens to {@link #conference} in order to
     * notify about changes in the list of <tt>Endpoint</tt>s participating in
     * the multipoint conference. The implementation keeps a
     * <tt>WeakReference</tt> to this instance and automatically removes itself
     * from <tt>PropertyChangeNotifier</tt>s.
     */
    private final PropertyChangeListener propertyChangeListener
        = new WeakReferencePropertyChangeListener(this);

    /**
     * The <tt>Object</tt> used to synchronize the access to the state of this
     * instance.
     */
    private final Object syncRoot = new Object();

    /**
     * Initializes a new <tt>ConferenceSpeechActivityImpl</tt> instance which is to
     * represent the speech activity in a specific <tt>Conference</tt>.
     *
     * @param conference the <tt>Conference</tt> whose speech activity is to be
     * represented by the new instance
     */
    public ConferenceSpeechActivityImpl(Conference conference)
    {
        this.conference = new WeakReference<>(conference);

        /*
         * The PropertyChangeListener will weakly reference this instance and
         * will unregister itself from the conference sooner or later.
         */
        conference.addPropertyChangeListener(propertyChangeListener);
    }

    /**
     * Notifies this multipoint conference that the active/dominant speaker has
     * changed to one identified by a specific synchronization source
     * identifier/SSRC.
     *
     * @param ssrc the synchronization source identifier/SSRC of the new
     * active/dominant speaker
     */
    private void activeSpeakerChanged(long ssrc)
    {
        Conference conference = getConference();

        if ((conference != null) && !conference.isExpired())
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "The dominant speaker in conference "
                            + conference.getID() + " is now the SSRC " + ssrc
                            + ".");
            }

            Endpoint endpoint
                = conference.findEndpointByReceiveSSRC(ssrc, MediaType.AUDIO);
            boolean maybeStartEventDispatcher = false;

            synchronized (syncRoot)
            {
                if (endpoint == null)
                {
                    /*
                     * We will NOT automatically elect a new dominant speaker
                     * HERE.
                     */
                    maybeStartEventDispatcher = true;
                }
                else
                {
                    Endpoint dominantEndpoint = getDominantEndpoint();

                    if (!endpoint.equals(dominantEndpoint))
                    {
                        this.dominantEndpoint = new WeakReference<>(endpoint);
                        maybeStartEventDispatcher = true;
                    }
                }
                if (maybeStartEventDispatcher)
                {
                    dominantEndpointChanged = true;
                    maybeStartEventDispatcher();
                }
            }
        }
    }

    /**
     * Retrieves a JSON representation of
     * {@link #dominantSpeakerIdentification} for the purposes of the REST API
     * of Videobridge.
     *
     * @return a <tt>JSONObject</tt> which represents
     * <tt>dominantSpeakerIdentification</tt> for the purposes of the REST API
     * of Videobridge
     */
    @Override
    public JSONObject doGetDominantSpeakerIdentificationJSON()
    {
        DominantSpeakerIdentification dominantSpeakerIdentification
            = getDominantSpeakerIdentification();
        JSONObject jsonObject;

        if (dominantSpeakerIdentification == null)
        {
            // We do not know how to represent ActiveSpeakerDetector at the time
            // of this writing, we know how to represent
            // DominantSpeakerIdentification only.
            jsonObject = null;
        }
        else
        {
            Conference conference = getConference();

            if ((conference == null) || conference.isExpired())
            {
                jsonObject = null;
            }
            else
            {
                jsonObject = dominantSpeakerIdentification.doGetJSON();
                if (jsonObject != null)
                {
                    // Resolve the dominantSpeaker of
                    // DominantSpeakerIdentification which is a synchronization
                    // source identifier (SSRC) as an Endpoint.
                    resolveSSRCAsEndpoint(
                            jsonObject,
                            "dominantSpeaker",
                            conference,
                            "dominantEndpoint");

                    // Resolve the ssrc of each one of the speakers of
                    // DominantSpeakerIdentification as an Endpoint.
                    Object speakers = jsonObject.get("speakers");

                    if (speakers != null)
                    {
                        if (speakers instanceof JSONObject[])
                        {
                            for (JSONObject speaker : (JSONObject[]) speakers)
                            {
                                resolveSSRCAsEndpoint(
                                        speaker,
                                        "ssrc",
                                        conference,
                                        "endpoint");
                            }
                        }
                        else if (speakers instanceof JSONArray)
                        {
                            for (Object speaker : (JSONArray) speakers)
                            {
                                if (speaker instanceof JSONObject)
                                {
                                    resolveSSRCAsEndpoint(
                                            (JSONObject) speaker,
                                            "ssrc",
                                            conference,
                                            "endpoint");
                                }
                            }
                        }
                    }
                }
            }
        }
        return jsonObject;
    }

    /**
     * Notifies this <tt>ConferenceSpeechActivityImpl</tt> that an
     * <tt>EventDispatcher</tt> has permanently stopped executing in its
     * associated background thread. If the specified <tt>EventDispatcher</tt>
     * is {@link #eventDispatcher}, this instance will note that it no longer
     * has an associated (executing) <tt>EventDispatcher</tt>.
     *
     * @param eventDispatcher the <tt>EventDispatcher</tt> which has exited
     */
    private void eventDispatcherExited(EventDispatcher eventDispatcher)
    {
        synchronized (syncRoot)
        {
            if (this.eventDispatcher == eventDispatcher)
            {
                this.eventDispatcher = eventDispatcher;
                eventDispatcherTime = 0;
            }
        }
    }

    /**
     * Gets the <tt>ActiveSpeakerDetector</tt> which detects/identifies the
     * active/dominant speaker in this <tt>Conference</tt>.
     *
     * @return the <tt>ActiveSpeakerDetector</tt> which detects/identifies the
     * active/dominant speaker in this <tt>Conference</tt>
     */
    private ActiveSpeakerDetector getActiveSpeakerDetector()
    {
        ActiveSpeakerDetector activeSpeakerDetector;
        boolean addActiveSpeakerChangedListener = false;

        synchronized (activeSpeakerDetectorSyncRoot)
        {
            activeSpeakerDetector = this.activeSpeakerDetector;
            if (activeSpeakerDetector == null)
            {
                ActiveSpeakerDetectorImpl asdi
                    = new ActiveSpeakerDetectorImpl();

                this.activeSpeakerDetector = activeSpeakerDetector = asdi;
                addActiveSpeakerChangedListener = true;

                /*
                 * Find the DominantSpeakerIdentification instance employed by
                 * activeSpeakerDetector, if possible, in order to enable
                 * additional functionality (e.g. debugging).
                 */
                ActiveSpeakerDetector impl = asdi.getImpl();

                if (impl instanceof DominantSpeakerIdentification)
                {
                    dominantSpeakerIdentification
                        = (DominantSpeakerIdentification) impl;
                }
                else
                {
                    dominantSpeakerIdentification = null;
                }
            }
        }

        /*
         * Listen to the activeSpeakerDetector about speaker switches in order
         * to track the dominant speaker in the multipoint conference.
         */
        if (addActiveSpeakerChangedListener)
        {
            Conference conference = getConference();

            if ((conference != null) && !conference.isExpired())
            {
                activeSpeakerDetector.addActiveSpeakerChangedListener(
                        activeSpeakerChangedListener);

                DominantSpeakerIdentification dominantSpeakerIdentification
                    = this.dominantSpeakerIdentification;

                if (dominantSpeakerIdentification != null)
                {
                    dominantSpeakerIdentification.addPropertyChangeListener(
                            propertyChangeListener);
                }
            }
        }

        return activeSpeakerDetector;
    }

    /**
     * Gets the <tt>Conference</tt> whose speech activity is represented by this
     * instance.
     *
     * @return the <tt>Conference</tt> whose speech activity is represented by
     * this instance
     */
    private Conference getConference()
    {
        Conference conference = this.conference.get();

        if ((conference == null) || conference.isExpired())
        {
            /*
             * The Conference has expired so there is no point to listen to
             * ActiveSpeakerDetector. Remove the activeSpeakerChangedListener
             * for the purposes of completeness, not because it is strictly
             * necessary.
             */
            ActiveSpeakerDetector activeSpeakerDetector
                = this.activeSpeakerDetector;

            if (activeSpeakerDetector != null)
            {
                activeSpeakerDetector.removeActiveSpeakerChangedListener(
                        activeSpeakerChangedListener);
            }

            DominantSpeakerIdentification dominantSpeakerIdentification
                = this.dominantSpeakerIdentification;

            if (dominantSpeakerIdentification != null)
            {
                dominantSpeakerIdentification.removePropertyChangeListener(
                        propertyChangeListener);
            }
        }

        return conference;
    }

    /**
     * Gets the <tt>Endpoint</tt> which is the dominant speaker in the
     * multipoint conference represented by this instance.
     *
     * @return the <tt>Endpoint</tt> which is the dominant speaker in the
     * multipoint conference represented by this instance or <tt>null</tt>
     */
    @Override
    public Endpoint getDominantEndpoint()
    {
        Endpoint dominantEndpoint;

        synchronized (syncRoot)
        {
            if (this.dominantEndpoint == null)
            {
                dominantEndpoint = null;
            }
            else
            {
                dominantEndpoint = this.dominantEndpoint.get();
                if (dominantEndpoint == null)
                    this.dominantEndpoint = null;
            }
        }
        return dominantEndpoint;
    }

    /**
     * Gets the <tt>DominantSpeakerIdentification</tt> instance, if any,
     * employed by {@link #activeSpeakerDetector}.
     *
     * @return the <tt>DominantSpeakerIdentification</tt> instance, if any,
     * employed by <tt>activeSpeakerDetector</tt>
     */
    private DominantSpeakerIdentification getDominantSpeakerIdentification()
    {
        // Make sure that dominantSpeakerIdentification is initialized.
        getActiveSpeakerDetector();

        return dominantSpeakerIdentification;
    }

    /**
     * Gets the ordered list of <tt>Endpoint</tt>s participating in the
     * multipoint conference represented by this instance with the dominant
     * (speaker) <tt>Endpoint</tt> at the beginning of the list i.e. the
     * dominant speaker history.
     *
     * @return the ordered list of <tt>Endpoint</tt>s participating in the
     * multipoint conference represented by this instance with the dominant
     * (speaker) <tt>Endpoint</tt> at the beginning of the list
     */
    @Override
    public List<Endpoint> getEndpoints()
    {
        List<Endpoint> ret;

        synchronized (syncRoot)
        {
            /*
             * The list of Endpoints of this instance is ordered by recentness
             * of speaker domination and/or speech activity. The list of
             * Endpoints of Conference is ordered by recentness of Endpoint
             * instance initialization. The list of Endpoints of this instance
             * is initially populated with the Endpoints of the conference.
             */
            if (endpoints == null)
            {
                Conference conference = getConference();

                if (conference == null)
                {
                    endpoints = new ArrayList<>();
                }
                else
                {
                    List<Endpoint> conferenceEndpoints
                        = conference.getEndpoints();

                    endpoints = new ArrayList<>(conferenceEndpoints.size());
                    for (Endpoint endpoint : conferenceEndpoints)
                        endpoints.add(new WeakReference<>(endpoint));
                }
            }

            // The return value is the list of Endpoints of this instance.
            ret = new ArrayList<>(endpoints.size());
            for (Iterator<WeakReference<Endpoint>> i = endpoints.iterator();
                    i.hasNext();)
            {
                Endpoint endpoint = i.next().get();

                if (endpoint != null)
                    ret.add(endpoint);
            }
        }
        return ret;
    }

    /**
     * Notifies this instance that a new audio level was received or measured by
     * a <tt>Channel</tt> for an RTP stream with a specific synchronization
     * source identifier/SSRC.
     *
     * @param channel the <tt>Channel</tt> which received or measured the new
     * audio level for the RTP stream identified by the specified <tt>ssrc</tt>
     * @param ssrc the synchronization source identifier/SSRC of the RTP stream
     * for which a new audio level was received or measured by the specified
     * <tt>channel</tt>
     * @param level the new audio level which was received or measured by the
     * specified <tt>channel</tt> for the RTP stream with the specified
     * <tt>ssrc</tt>
     */
    @Override
    public void levelChanged(Channel channel, long ssrc, int level)
    {
        // ActiveSpeakerDetector
        ActiveSpeakerDetector activeSpeakerDetector
            = getActiveSpeakerDetector();

        if (activeSpeakerDetector != null)
            activeSpeakerDetector.levelChanged(ssrc, level);

        // Endpoint
        Endpoint endpoint = channel.getEndpoint();

        if (endpoint != null)
            endpoint.audioLevelChanged(channel, ssrc, level);
    }

    /**
     * Starts a new <tt>EventDispatcher</tt> or notifies an existing one to fire
     * events to registered listeners about changes of the values of the
     * <tt>dominantEndpoint</tt> and <tt>endpoints</tt> properties of this
     * instance.
     */
    private void maybeStartEventDispatcher()
    {
        synchronized (syncRoot)
        {
            if (this.eventDispatcher == null)
            {
                EventDispatcher eventDispatcher = new EventDispatcher(this);
                boolean scheduled = false;

                this.eventDispatcher = eventDispatcher;
                eventDispatcherTime = 0;
                try
                {
                    executorService.execute(eventDispatcher);
                    scheduled = true;
                }
                finally
                {
                    if (!scheduled && (this.eventDispatcher == eventDispatcher))
                    {
                        this.eventDispatcher = null;
                        eventDispatcherTime = 0;
                    }
                }
            }
            else
            {
                syncRoot.notify();
            }
        }
    }

    /**
     * Notifies this instance that there was a change in the value of a property
     * of an object in which this instance is interested.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which specifies the object of
     * interest, the name of the property and the old and new values of that
     * property
     */
    @Override
    public void propertyChange(PropertyChangeEvent ev)
    {
        // Cease to execute as soon as the Conference expires.
        Conference conference = getConference();

        if ((conference == null) || conference.isExpired())
            return;

        String propertyName = ev.getPropertyName();

        if (Conference.ENDPOINTS_PROPERTY_NAME.equals(propertyName))
        {
            if (conference.equals(ev.getSource()))
            {
                synchronized (syncRoot)
                {
                    endpointsChanged = true;
                    maybeStartEventDispatcher();
                }
            }
        }
        else if (DominantSpeakerIdentification.DOMINANT_SPEAKER_PROPERTY_NAME
                .equals(propertyName))
        {
            DominantSpeakerIdentification dominantSpeakerIdentification
                = this.dominantSpeakerIdentification;

            if ((dominantSpeakerIdentification != null)
                    && dominantSpeakerIdentification.equals(ev.getSource()))
            {
                // TODO Auto-generated method stub
            }
        }
    }

    /**
     * Runs in the background thread of {@link #eventDispatcher} to possibly
     * fire events.
     *
     * @param eventDispatcher the <tt>EventDispatcher</tt> which is calling back
     * to this instance
     * @return <tt>true</tt> if the specified <tt>eventDispatcher</tt> is to
     * continue with its next iteration and call back to this instance again or
     * <tt>false</tt> to have the specified <tt>eventDispatcher</tt> break out
     * of its loop  and not call back to this instance again
     */
    private boolean runInEventDispatcher(EventDispatcher eventDispatcher)
    {
        boolean endpointsChanged = false;
        boolean dominantEndpointChanged = false;

        synchronized (syncRoot)
        {
            /*
             * Most obviously, an EventDispatcher should cease to execute as
             * soon as this ConferenceSpeechActivityImpl stops employing it.
             */
            if (this.eventDispatcher != eventDispatcher)
                return false;

            /*
             * As soon as the Conference associated with this instance expires,
             * kill all background threads.
             */
            Conference conference = getConference();

            if ((conference == null) || conference.isExpired())
                return false;

            long now = System.currentTimeMillis();

            if (!this.dominantEndpointChanged && !this.endpointsChanged)
            {
                long wait = 100 - (now - eventDispatcherTime);

                if (wait > 0)
                {
                    try
                    {
                        syncRoot.wait(wait);
                    }
                    catch (InterruptedException ie)
                    {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                }
            }
            eventDispatcherTime = now;

            /*
             * Synchronize the set of Endpoints of this instance with the set of
             * Endpoints of the conference.
             */
            List<Endpoint> conferenceEndpoints = conference.getEndpoints();

            if (endpoints == null)
            {
                endpoints = new ArrayList<>(conferenceEndpoints.size());
                for (Endpoint endpoint : conferenceEndpoints)
                {
                    endpoints.add(new WeakReference<>(endpoint));
                }
                endpointsChanged = true;
            }
            else
            {
                /*
                 * Remove the Endpoints of this instance which are no longer in
                 * the conference.
                 */
                for (Iterator<WeakReference<Endpoint>> i = endpoints.iterator();
                        i.hasNext();)
                {
                    Endpoint endpoint = i.next().get();

                    if (endpoint == null)
                    {
                        i.remove();
                        endpointsChanged = true;
                    }
                    else if (conferenceEndpoints.contains(endpoint))
                    {
                        conferenceEndpoints.remove(endpoint);
                    }
                    else
                    {
                        i.remove();
                        endpointsChanged = true;
                    }
                }
                /*
                 * Add the Endpoints of the conference which are not in this
                 * instance yet.
                 */
                if (!conferenceEndpoints.isEmpty())
                {
                    for (Endpoint endpoint : conferenceEndpoints)
                    {
                        endpoints.add(new WeakReference<>(endpoint));
                    }
                    endpointsChanged = true;
                }
            }
            this.endpointsChanged = false;

            /*
             * Make sure that the dominantEndpoint is at the top of the list of
             * the Endpoints of this instance.
             */
            Endpoint dominantEndpoint = getDominantEndpoint();

            if (dominantEndpoint != null)
            {
                int dominantEndpointIndex = -1;

                for (int i = 0, count = endpoints.size(); i < count; ++i)
                {
                    if (dominantEndpoint.equals(endpoints.get(i).get()))
                    {
                        dominantEndpointIndex = i;
                        break;
                    }
                }
                if ((dominantEndpointIndex != -1)
                        && (dominantEndpointIndex != 0))
                {
                    WeakReference<Endpoint> weakReference
                        = endpoints.remove(dominantEndpointIndex);

                    endpoints.add(0, weakReference);
                    endpointsChanged = true;
                }
            }

            /*
             * The activeSpeakerDetector decides when the dominantEndpoint
             * changes at the time of this writing.
             */
            if (this.dominantEndpointChanged)
            {
                dominantEndpointChanged = true;
                this.dominantEndpointChanged = false;
            }
        }

        if (endpointsChanged)
            firePropertyChange(ENDPOINTS_PROPERTY_NAME, null, null);
        if (dominantEndpointChanged)
            firePropertyChange(DOMINANT_ENDPOINT_PROPERTY_NAME, null, null);

        return true;
    }

    /**
     * Represents a background/daemon thread which fires events to registered
     * listeners notifying about changes in the values of the
     * <tt>dominantEndpoint</tt> and <tt>endpoints</tt> properties of a specific
     * <tt>ConferenceSpeechActivityImpl</tt>. Because <tt>EventDispatcher</tt> runs
     * in a background/daemon <tt>Thread</tt> which is a garbage collection
     * root, it keeps a <tt>WeakReference</tt> to the specified
     * <tt>ConferenceSpeechActivityImpl</tt> in order to not accidentally prevent
     * its garbage collection.
     */
    private static class EventDispatcher
        implements Runnable
    {
        /**
         * The <tt>ConferenceSpeechActivityImpl</tt> which has initialized this
         * instance and on behalf of which this instance is to fire events to
         * registered listeners in the background.
         */
        private final WeakReference<ConferenceSpeechActivityImpl> owner;

        /**
         * Initializes a new <tt>EventDispatcher</tt> instance which is to fire
         * events in the background to registered listeners on behalf of a
         * specific <tt>ConferenceSpeechActivityImpl</tt>.
         *
         * @param owner the <tt>ConferenceSpeechActivityImpl</tt> which is
         * initializing the new instance
         */
        public EventDispatcher(ConferenceSpeechActivityImpl owner)
        {
            this.owner = new WeakReference<>(owner);
        }

        /**
         * Runs in a background/daemon thread and notifies registered listeners
         * about changes in the values of the <tt>dominantEndpoint</tt> and
         * <tt>endpoints</tt> properties of {@link #owner}.
         */
        @Override
        public void run()
        {
            try
            {
                do
                {
                    ConferenceSpeechActivityImpl owner = this.owner.get();

                    if ((owner == null) || !owner.runInEventDispatcher(this))
                        break;
                }
                while (true);
            }
            finally
            {
                /*
                 * Notify the ConferenceSpeechActivityImpl that this EventDispatcher
                 * has exited in order to allow the former to forget about the
                 * latter.
                 */
                ConferenceSpeechActivityImpl owner = this.owner.get();

                if (owner != null)
                    owner.eventDispatcherExited(this);
            }
        }
    }
}
