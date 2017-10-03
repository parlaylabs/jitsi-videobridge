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
package org.jitsi.videobridge.rest;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.jitsi.util.Logger;
import org.json.simple.*;
import org.osgi.framework.*;

import org.jitsi.videobridge.*;
import org.jitsi.osgi.*;
import org.eclipse.jetty.continuation.*;


//TEMP
import java.util.concurrent.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Implements a {@code Servlet} which enables long polling with asynchronous
 * HTTP request handling.
 *
 * @author Lyubomir Marinov
 */
class LongPollingServlet
    extends HttpServlet
{
    private static final String CONTINUATION_PUSH_EVENT_LISTENER = "push_listener";
    private static final Integer CONTINUATION_TIMEOUT_MILLIS = 60000;

    /**
     * The <tt>Logger</tt> used by the <tt>Channel</tt> class and its instances
     * to print debug information.
     */
    private static final Logger logger = Logger.getLogger(LongPollingServlet.class);

    private BundleContext bundleContext;

    public LongPollingServlet(BundleContext bundleContext)
    {
      this.bundleContext = bundleContext;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    protected void doGet(
        final HttpServletRequest request,
        final HttpServletResponse response)
        throws IOException,
               ServletException
    {
        final Continuation continuation = ContinuationSupport.getContinuation(request);

        // We don't get the target here (TODO: is that something that could
        //  be fixed?), so we need to do a bit more work to extract the bits of the path
        //  we're interested in.
        // The stuff we're interested in starts after the /colibri/
        String colibriPath = request.getRequestURL().toString().split("colibri/")[1];
        String target = colibriPath.split("/")[0];

        if (target.equals("subscribeToEvents"))
        {
          String confId = colibriPath.split("/")[1];
          Videobridge videobridge = getVideobridge();
          final Conference conference = videobridge.getConference(confId, null);

          if (continuation.isExpired())
          {
            Conference.PushEventListener listener = (Conference.PushEventListener) continuation
                .getAttribute(CONTINUATION_PUSH_EVENT_LISTENER);
            if (listener != null && conference != null) {
              conference.removePushEventListener(listener);
            }
            logger.info("subscribeToEvents request timed out for conference " + confId);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
          }

          continuation.setTimeout(CONTINUATION_TIMEOUT_MILLIS);
          continuation.suspend();

          logger.info("LongPolling subscribing to conference events for conference " + confId);
          if (conference == null)
          {
            logger.info("Couldn't find conference " + confId);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Couldn't find conference " + confId);
            continuation.complete();
          }
          else
          {
            logger.info("Found conference, waiting for event");
            Conference.PushEventListener listener = new Conference.PushEventListener() {
              @Override
              public boolean handleEvent(JSONObject event) {
                try {
                  if (continuation.isExpired() || !continuation.isSuspended())
                  {
                    logger.info("Couldn't handle event, continuation already completed " + event.toJSONString());
                    return false;
                  }
                  else
                  {
                    event.writeJSONString(response.getWriter());
                    continuation.complete();
                    return true;
                  }
                } catch (Exception e) {
                  logger.warn("Failed to handle event " + event.toJSONString(), e);
                }
                return false;
              }
            };
            continuation.setAttribute(CONTINUATION_PUSH_EVENT_LISTENER, listener);
            conference.addPushEventListener(listener);
          }
        }
    }

    /**
     * Gets the {@code Videobridge} instance available to this Jetty
     * {@code Handler}.
     *
     * @return the {@code Videobridge} instance available to this Jetty
     * {@code Handler} or {@code null} if no {@code Videobridge} instance is
     * available to this Jetty {@code Handler}
     */
    public Videobridge getVideobridge()
    {
        return ServiceUtils2.getService(this.bundleContext, Videobridge.class);
    }
}
