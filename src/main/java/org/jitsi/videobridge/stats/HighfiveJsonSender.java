package org.jitsi.videobridge.stats;

import com.google.gson.JsonParseException;
import com.ning.http.client.*;
import net.java.sip.communicator.util.Logger;
import org.apache.http.HttpStatus;

import javax.json.JsonObject;

public class HighfiveJsonSender {
  private static final Logger logger
          = Logger.getLogger(HighfiveJsonSender.class);
  private String baseUrl = null;

  private  AsyncHttpClient asyncHttpClient = null;

  public HighfiveJsonSender(String baseUrl) {

    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
    builder.setMaxRequestRetry(5);
    asyncHttpClient = new AsyncHttpClient(builder.build());

    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "error");
    this.baseUrl = baseUrl;
  }

  public void publish(String urlPath, JsonObject obj) {
    final String postUrl = baseUrl + urlPath;
    try {

      RequestBuilder requestBuilder = new RequestBuilder("POST");
      requestBuilder.setUrl(postUrl)
              .addHeader("Accept", "application/x.highfive.v2+json")
              .addHeader("Content-Type", "application/json")
              .setBody(obj.toString());


      asyncHttpClient.executeRequest(requestBuilder.build(), new AsyncCompletionHandler(){

        @Override
        public Response onCompleted(Response response) throws Exception{
          if (response.getStatusCode() != HttpStatus.SC_OK) {
            logger.error(String.format("HTTP response not OK. for telemetry service post : %s, body: %s", postUrl, response.getResponseBody()));
          }
          return response;
        }

        @Override
        public void onThrowable(Throwable t){
          logger.error(String.format("Error posting stats to telemetry service, %s", postUrl));
        }
      });

    } catch (JsonParseException e) {
      logger.error(String.format("Error while sending timeseries to telemetry service, %s", postUrl));
    }
  }
}
