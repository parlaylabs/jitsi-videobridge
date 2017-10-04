package org.jitsi.videobridge.rest;

import org.eclipse.jetty.server.Request;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Endpoint;
import org.jitsi.videobridge.Videobridge;
import org.osgi.framework.BundleContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Created by ubuntu on 9/19/16.
 */
public class HighfiveHandlerImpl extends HandlerImpl {
    // Corresponds to values in VideobridgeRest in fatline
    private static final String CONFERENCE_ID_HEADER = "conference-id";
    private static final String ENDPOINT_ID_HEADER = "endpoint-id";
    private static final String CAMERA_ASPECT_RATIO_HEADER = "camera-aspect-ratio";
    public HighfiveHandlerImpl(BundleContext bundleContext, boolean enableShutdown, boolean enableColibri)
    {
        super(bundleContext, enableShutdown, enableColibri);
    }

    protected void doPatchConferenceJSON(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException,
            ServletException
    {
        super.doPatchConferenceJSON(target, baseRequest, request, response);
        List<String> requestHeaders = Collections.list(request.getHeaderNames());
        if (requestHeaders.contains(CAMERA_ASPECT_RATIO_HEADER) &&
            requestHeaders.contains(ENDPOINT_ID_HEADER) &&
            requestHeaders.contains(CONFERENCE_ID_HEADER))
        {
            String endpointId = request.getHeader(ENDPOINT_ID_HEADER);
            String conferenceId = request.getHeader(CONFERENCE_ID_HEADER);
            String aspectRatio = request.getHeader(CAMERA_ASPECT_RATIO_HEADER);
            Videobridge videobridge = getVideobridge();
            if (videobridge == null)
            {
                return;
            }
            Conference conference = getVideobridge().getConference(conferenceId, null);
            if (conference == null)
            {
                return;
            }
            Endpoint endpoint = conference.getEndpoint(endpointId);
            if (endpoint == null)
            {
                return;
            }

//            if (aspectRatio.equals(Endpoint.ASPECT_RATIO_SQUARE))
//            {
//
//                endpoint.setCameraStreamAspectRatio(Endpoint.AspectRatio.SQUARE);
//            }
//            else if (aspectRatio.equals(Endpoint.ASPECT_RATIO_LANDSACPE))
//            {
//                endpoint.setCameraStreamAspectRatio(Endpoint.AspectRatio.LANDSCAPE);
//            }
            else
            {
                logger.error("Can't set aspect ratio for endpoint " + endpointId + " to " + aspectRatio);
            }
        }
        else if (requestHeaders.contains(CAMERA_ASPECT_RATIO_HEADER) ||
                requestHeaders.contains(ENDPOINT_ID_HEADER) ||
                requestHeaders.contains(CONFERENCE_ID_HEADER))
        {
            logger.error("Missing aspect ratio header information");
        }
    }
}
