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
 * Represents the speech activity of the <tt>Endpoint</tt>s in a
 * <tt>Conference</tt>. Identifies the dominant speaker <tt>Endpoint</tt> in the
 * <tt>Conference</tt> and maintains an ordered list of the <tt>Endpoint</tt>s
 * in the <tt>Conference</tt> sorted by recentness of speaker domination and/or
 * speech activity.
 *
 * @author Lyubomir Marinov
 */
public abstract class ConferenceSpeechActivity
    extends PropertyChangeNotifier
    implements PropertyChangeListener
{
    /**
     * The name of the <tt>ConferenceSpeechActivity</tt> property
     * <tt>dominantEndpoint</tt> which identifies the dominant speaker in a
     * multipoint conference.
     */
    public static final String DOMINANT_ENDPOINT_PROPERTY_NAME
        = ConferenceSpeechActivity.class.getName() + ".dominantEndpoint";

    /**
     * The name of the <tt>ConferenceSpeechActivity</tt> property
     * <tt>endpoints</tt> which lists the <tt>Endpoint</tt>s
     * participating in/contributing to a <tt>Conference</tt>.
     */
    public static final String ENDPOINTS_PROPERTY_NAME
        = ConferenceSpeechActivity.class.getName() + ".endpoints";

    /**
     * Retrieves a JSON representation of
     * {@link #dominantSpeakerIdentification} for the purposes of the REST API
     * of Videobridge.
     *
     * @return a <tt>JSONObject</tt> which represents
     * <tt>dominantSpeakerIdentification</tt> for the purposes of the REST API
     * of Videobridge
     */
    public JSONObject doGetDominantSpeakerIdentificationJSON()
    {
        return null;
    }

    /**
     * Gets the <tt>Endpoint</tt> which is the dominant speaker in the
     * multipoint conference represented by this instance.
     *
     * @return the <tt>Endpoint</tt> which is the dominant speaker in the
     * multipoint conference represented by this instance or <tt>null</tt>
     */
    public abstract Endpoint getDominantEndpoint();

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
    public abstract List<Endpoint> getEndpoints();

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
    public void levelChanged(Channel channel, long ssrc, int level)
    {
    }
}
