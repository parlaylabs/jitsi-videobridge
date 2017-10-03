package org.jitsi.videobridge.logging;

import net.java.sip.communicator.util.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jitsi.videobridge.metadata.AwsMetadata;
import org.jitsi.videobridge.metadata.DataCenterProvider;
import org.jitsi.videobridge.metadata.InstanceMetadata;
import org.jitsi.videobridge.metadata.LocalMetadata;
import org.jitsi.videobridge.version.BuildConstant;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class HighfiveLoggingHandler extends StreamHandler {
  private static final Logger logger
          = Logger.getLogger(HighfiveLoggingHandler.class);
  public static final String KAFKA_HOST_PORT = "%s.kafka.host.port";
  public static final String KAFKA_TOPIC_PREFIX = "%s.kafka.topic.prefix";

  private KafkaAppender kafkaAppender = null;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

  private static JsonArray roles = Json.createArrayBuilder()
          .add("HTTP")
          .add("Jitsi-Bridge")
          .build();
  private JsonObject bridgeServerId = null;
  public static final String DATACENTER_PROVIDER_PROPERTY = "org.jitsi.videobrirdge.metadata.datacenter.provider";

  public HighfiveLoggingHandler() {
    LogManager manager = LogManager.getLogManager();
    try {
      manager.readConfiguration();
      final String kafkaHostPort = manager.getProperty(String.format(KAFKA_HOST_PORT, getClass().getName()));
      final String kafkaTopicPrefix = manager.getProperty(String.format(KAFKA_TOPIC_PREFIX, getClass().getName()));
      if (kafkaHostPort != null && kafkaTopicPrefix != null) {
        this.kafkaAppender = new KafkaAppender(String.format("%s_jvb_logs", kafkaTopicPrefix), kafkaHostPort);
      }
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

      InstanceMetadata metadata;
      switch (DataCenterProvider.valueOf(manager.getProperty(DATACENTER_PROVIDER_PROPERTY))) {
        case AWS:
          metadata = new AwsMetadata();
          break;
        default:
          metadata = new LocalMetadata();
          break;
      }

      String ipAddress = null;
      String publicIpAddress = null;
      String id = metadata.getId();
      String hostName = metadata.getHostname();

      final InetAddress internalIpAddress = metadata.getInternalIpAddress();
      if (internalIpAddress != null) {
        ipAddress = internalIpAddress.getHostAddress();
      }
      if (ipAddress == null) {
        ipAddress = InetAddress.getLocalHost().getHostAddress();
      }
      if (metadata.getPublicIpAddress() != null) {
        publicIpAddress = metadata.getPublicIpAddress().getHostAddress();
      }
      if (hostName == null) {
        hostName = ipAddress;
      }
      if (id == null) {
        id = ipAddress;
      }

      JsonObject dataCenterId = Json.createObjectBuilder()
              .add("provider", metadata.getDataCenterProvider().toString())
              .add("region", StringUtils.defaultString(metadata.getRegion()))
              .add("zone", StringUtils.defaultString(metadata.getAvailabilityZone()))
              .build();

      this.bridgeServerId = Json.createObjectBuilder()
              .add("id", id)
              .add("ipAddress", ipAddress)
              .add("publicIpAddress", StringUtils.defaultString(publicIpAddress))
              .add("hostname", hostName)
              .add("version", BuildConstant.VERSION)
              .add("datacenter", dataCenterId)
              .add("roles", roles)
              .build();
    } catch (IOException e) {
      logger.error(String.format("Error loading %s", getClass().getName()), e);
    }
  }

  @Override
  public void publish(LogRecord record) {
    if (kafkaAppender != null) {
      kafkaAppender.sendEntry(buildServerLogsRequest(record));
    }
  }

  @Override
  public void close() {
    if(kafkaAppender != null) {
      kafkaAppender.close();
    }
  }

  private String parseLoglevel(Level logLevel) {
    if (logLevel == Level.SEVERE) {
      return "LOG_ERROR";
    } else if (logLevel == Level.WARNING) {
      return "LOG_WARN";
    } else if (logLevel == Level.CONFIG) {
      return "LOG_INFO";
    } else if (logLevel == Level.FINE) {
      return "LOG_DEBUG";
    } else if (logLevel == Level.INFO) {
      return "LOG_INFO";
    } else {
      return "LOG_TRACE";
    }
  }


  private JsonObject buildServerLogsRequest(LogRecord record) {
    final Date recordTime = new Date(record.getMillis());
    JsonObjectBuilder serverLogRequestBuilder = Json.createObjectBuilder()
        .add("serverId", bridgeServerId)
        .add("timestamp", dateFormat.format(recordTime))
        .add("level", parseLoglevel(record.getLevel()))
        .add("loggerName", record.getLoggerName())
        .add("message", record.getMessage());

    final Throwable thrown = record.getThrown();
    if(thrown != null) {

      final JsonObjectBuilder exception = Json.createObjectBuilder();
      if(thrown.getMessage() != null) {
        exception.add("message", thrown.getMessage());
      }
      if(thrown.getStackTrace() != null) {
        exception.add("stackTrace", ExceptionUtils.getStackTrace(thrown));
      }
      if(thrown.getClass() != null) {
        exception.add("class", thrown.getClass().toString());
      }
      if(thrown.getCause() != null) {
        exception.add("cause", thrown.getCause().toString());
      }

      serverLogRequestBuilder.add("exception", exception);
    }

    return serverLogRequestBuilder.build();
  }

}
