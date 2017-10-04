package org.jitsi.videobridge.stats;

import net.java.sip.communicator.util.Logger;
//import org.jitsi.impl.neomedia.java8.Optional;
//import org.jitsi.impl.neomedia.stats.StatisticsTable;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.stats.MediaStreamStats2;
import org.jitsi.service.neomedia.stats.ReceiveTrackStats;
import org.jitsi.service.neomedia.stats.SendTrackStats;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Content;
import org.jitsi.videobridge.Endpoint;
import org.jitsi.videobridge.RtpChannel;
import org.jitsi.videobridge.Videobridge;
//import org.jitsi.videobridge.metadata.AwsMetadata;
//import org.jitsi.videobridge.metadata.DataCenterProvider;
//import org.jitsi.videobridge.metadata.InstanceMetadata;
//import org.jitsi.videobridge.metadata.LocalMetadata;
//import org.jitsi.videobridge.version.BuildConstant;
import org.osgi.framework.BundleContext;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.jitsi.videobridge.stats.VideobridgeStatistics.*;

public class HighfiveStatsTransport
//    extends StatsTransport
{
  private static final Logger logger
      = Logger.getLogger(HighfiveStatsTransport.class);

//  private static final String TELEMETRY_SERVER_ADDRESS = "%s.hfserver";
//
//  private static final String CLIENT_TIMESERIES_PATH
//      = "/telemetry/timeseries/";
//
//  private static final String SERVER_TIMESERIES_PATH
//      = "/telemetry/timeseries/server/";
//
//  private static final MediaType[] MEDIA_TYPES
//      = { MediaType.AUDIO, MediaType.VIDEO, MediaType.DATA };
//
//  public static final String JAVA_UTIL_LOGGING_CONFIG_FILE = "java.util.logging.config.file";
//
//  public static final String DATACENTER_PROVIDER_PROPERTY = "org.jitsi.videobrirdge.metadata.datacenter.provider";
//
//  private HighfiveJsonSender highfiveJsonSender = null;
//
//  private static JsonArray roles = Json.createArrayBuilder()
//          .add("HTTP")
//          .add("Jitsi-Bridge")
//          .build();
//
//  private String hostName = null;
//
//
//  public HighfiveStatsTransport() {
//    // Use the logging configuration file to read other properties
//    if (System.getProperty(JAVA_UTIL_LOGGING_CONFIG_FILE) != null) {
//      final String configFilePath = System.getProperty(JAVA_UTIL_LOGGING_CONFIG_FILE);
//
//
//      final Properties properties = new Properties();
//      try {
//        properties.load(new FileInputStream(configFilePath));
//        final String hfServerUrlPropertyName = String.format(TELEMETRY_SERVER_ADDRESS, getClass().getName());
//        final String hfServerUrl = properties.getProperty(hfServerUrlPropertyName);
//        if (hfServerUrl != null) {
//          highfiveJsonSender = new HighfiveJsonSender(hfServerUrl);
//        } else {
//          logger.error(String.format("Property name %s for server telemetry url is not set in %s file!", hfServerUrlPropertyName, configFilePath));
//        }
//
//        InstanceMetadata metadata;
//        switch (DataCenterProvider.valueOf(properties.getProperty(DATACENTER_PROVIDER_PROPERTY))) {
//          case AWS:
//            metadata = new AwsMetadata();
//            break;
//          default:
//            metadata = new LocalMetadata();
//            break;
//        }
//
//        hostName = metadata.getHostname();
//        if (hostName == null) {
//          InetAddress ip = InetAddress.getLocalHost();
//          hostName = ip.getHostAddress();
//        }
//      } catch (IOException e) {
//        logger.error(String.format("Error loading properties file from classpath: %s", configFilePath));
//      }
//    }
//  }
//
//  /**
//   * {@inheritDoc}
//   */
//  @Override
//  public void publishStatistics(Statistics stats) {
//    publishClientStats();
//    publishBridgeStats(stats);
//  }
//
//  /**
//   * Publish client related status in bridge's perspective
//   * bridge -> conference -> clients -> media -> channel
//   */
//  private void publishClientStats() {
//    BundleContext bundleContext = getBundleContext();
//    if (bundleContext != null) {
//      Collection<Videobridge> videobridges
//          = Videobridge.getVideobridges(bundleContext);
//      for (Videobridge videobridge : videobridges) {
//        Conference[] conferences = videobridge.getConferences();
//        if (conferences.length != 0) {
//          for (Conference conference : conferences) {
//            List<Endpoint> endpoints = conference.getEndpoints();
//            for (Endpoint endpoint : endpoints) {
//              for (MediaType mediaType : MEDIA_TYPES) {
//                for (RtpChannel rc : endpoint.getChannels(mediaType, false)) {
//                  processChannelStats(rc, new NameBuilder().setMediaType(mediaType));
//                }
//              }
//            }
//          }
//        }
//      }
//    }
//  }
//
//  /**
//   * Enforce a schema for JVB counter names.
//   * Format: jvb\<_mediaType\>_ssrc_SSRC.counterName
//   */
//  class NameBuilder {
//    private static final String JVB_PREFIX = "jvb";
//    private Optional<MediaType> mediaType = Optional.empty();
//
//    NameBuilder setMediaType(MediaType mediaType) {
//      this.mediaType = Optional.of(mediaType);
//      return this;
//    }
//
//    public String get(String name){
//      return get(Optional.<Integer>empty(), name);
//    }
//
//    public String get(int ssrc, String name) {
//      return get(Optional.of(ssrc), name);
//    }
//
//    public String get(Optional<Integer> ssrc, String name) {
//      StringBuilder buffer = new StringBuilder(JVB_PREFIX);
//      if (mediaType.isPresent()) {
//        buffer.append(String.format("_%s", mediaType.get().toString()));
//      }
//      if (ssrc.isPresent()) {
//        buffer.append(String.format("_ssrc_%d", ssrc.get() & 0xffff_ffffL));
//      }
//      buffer.append(String.format(".%s", name));
//      return buffer.toString();
//    }
//  }
//
//  /**
//   * Publish bridge status
//   */
//  private void publishBridgeStats(Statistics stats) {
//    JsonObject timeseriesCounter = buildServerTimeseries(stats);
//    if (highfiveJsonSender != null) {
//      highfiveJsonSender.publish(SERVER_TIMESERIES_PATH, timeseriesCounter);
//    }
//  }
//
//  private void processChannelStats(RtpChannel channel, NameBuilder nameBuilder) {
//    if (channel == null) {
//      logger.debug("Could not log the channel expired event " +
//          "because the channel is null.");
//      return;
//    }
//    Content content = channel.getContent();
//    if (content == null) {
//      logger.debug("Could not log the channel expired event " +
//          "because the content is null.");
//      return;
//    }
//    Conference conference = content.getConference();
//    if (conference == null) {
//      logger.debug("Could not log the channel expired event " +
//          "because the conference is null.");
//      return;
//    }
//    MediaStreamStats2 stats = null;
//    if(channel.getReceiveSSRCs().length == 0) {
//      return;
//    }
//    MediaStream stream = channel.getStream();
//    if (stream == null) {
//      return;
//    }
//    stats = stream.getMediaStreamStats();
//    if(stats == null) {
//      return;
//    }
//    Endpoint endpoint = channel.getEndpoint();
//    if (endpoint == null) {
//      return;
//    }
//    JsonObject timeseriesCounter = buildClientTimeseries(nameBuilder, stats, stream.getStatisticsTable());
//    if (highfiveJsonSender != null) {
//      highfiveJsonSender.publish(CLIENT_TIMESERIES_PATH + endpoint.getID(), timeseriesCounter);
//    }
//  }
//
//  private JsonObject buildTimeseriesValues(String counterName, long timestamp, Double value) {
//    try {
//      JsonObject point = Json.createObjectBuilder()
//          .add("timestamp", timestamp)
//          .add("value", value)
//          .build();
//      JsonArray points = Json.createArrayBuilder()
//          .add(point)
//          .build();
//      JsonObjectBuilder timeseriesValuesBuilder = Json.createObjectBuilder()
//          .add("counter_name", counterName)
//          .add("points", points);
//      return timeseriesValuesBuilder.build();
//    }catch (NumberFormatException e) {
//      logger.error(String.format("Failed to store timeseries for counterName: %s, value: %f%n", counterName, value));
//      throw e;
//    }
//  }
//
//  private JsonObject buildClientTimeseries(NameBuilder nameBuilder, MediaStreamStats2 stats, StatisticsTable statisticsTable) {
//    long nowTimeMs = System.currentTimeMillis();
//
//    JsonArrayBuilder timeseriesCountersBuilder = Json.createArrayBuilder();
//
//    // send stats for received streams
//    for (ReceiveTrackStats receivedStat : stats.getAllReceiveStats()) {
//      int ssrc = (int)receivedStat.getSSRC();
//      timeseriesCountersBuilder
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc,"NbBytes_Jvb_Received"), nowTimeMs, (double) receivedStat.getBytes()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "NbPackets_Jvb_Received"), nowTimeMs, (double) receivedStat.getPackets()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc,"Jitter_Jvb_Received"), nowTimeMs, receivedStat.getJitter()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "RttMs_Jvb_Received"), nowTimeMs, (double) receivedStat.getRtt()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "Bitrate_Jvb_Received"), nowTimeMs, (double) receivedStat.getBitrate()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "PacketsLost_Jvb_Received"), nowTimeMs, (double) receivedStat.getPacketsLost()));
//    }
//    // send stats for sent streams
//    for (SendTrackStats sentStat : stats.getAllSendStats()) {
//      int ssrc = (int)sentStat.getSSRC();
//      timeseriesCountersBuilder
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "NbBytes_Jvb_Sent"), nowTimeMs, (double) (sentStat.getBytes())))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "NbPackets_Jvb_Sent"), nowTimeMs, (double) (sentStat.getPackets())))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "Jitter_Jvb_Sent"), nowTimeMs, sentStat.getJitter()))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "RttMs_Jvb_Sent"), nowTimeMs, (double) (sentStat.getRtt())))
//          .add(buildTimeseriesValues(nameBuilder.get(ssrc, "Bitrate_Jvb_Sent"), nowTimeMs, (double) (sentStat.getBitrate())));
//    }
//    for (Map.Entry<StatisticsTable.Key, Long> counter : statisticsTable.toMap().entrySet()) {
//      StatisticsTable.Key key = counter.getKey();
//      String name = nameBuilder.get(key.getStreamId(), key.getName());
//      timeseriesCountersBuilder
//          .add(buildTimeseriesValues(name, nowTimeMs, counter.getValue().doubleValue()));
//    }
//    JsonObjectBuilder clientTimeSeriesBuilder = Json.createObjectBuilder()
//        .add("counters", timeseriesCountersBuilder.build());
//    return clientTimeSeriesBuilder.build();
//  }
//
//  private JsonObject buildServerTimeseries(Statistics stats) {
//    long nowTimeMs = System.currentTimeMillis();
//    JsonObjectBuilder bridgeServerIdBuilder = Json.createObjectBuilder()
//            .add("url", hostName)
//            .add("version", BuildConstant.VERSION)
//            .add("roles", roles);
//    JsonArrayBuilder timeseriesCountersBuilder = buildBridgeStats(stats, nowTimeMs);
//    JsonObjectBuilder serverTimeseriesBuilder = Json.createObjectBuilder()
//        .add("server", bridgeServerIdBuilder.build())
//        .add("counters", timeseriesCountersBuilder.build());
//    return serverTimeseriesBuilder.build();
//  }
//
//  private JsonArrayBuilder buildBridgeStats(Statistics stats, long nowTimeMs) {
//    JsonArrayBuilder builder = Json.createArrayBuilder();
//    NameBuilder nameBuilder = new NameBuilder();
//    for (Map.Entry<String, String> entry : serverStats.entrySet()) {
//      builder.add(
//        buildTimeseriesValues(nameBuilder.get(entry.getKey()), nowTimeMs, stats.getStatAsDouble(entry.getValue())));
//    }
//    return builder;
//  }
//
//  private final Map<String, String> serverStats = Collections.unmodifiableMap(new HashMap<String, String>() {
//    {
//      put("audioChannels", AUDIOCHANNELS);
//      put("videoChannels", VIDEOCHANNELS);
//      put("conferences", CONFERENCES);
//      put("cpuUsage", CPU_USAGE);
//      put("bitrateDownloadBps", BITRATE_DOWNLOAD);
//      put("bitrateUploadBps", BITRATE_UPLOAD);
//      put("memoryUsedMB", USED_MEMORY);
//      put("memoryTotalMB", TOTAL_MEMORY);
//      put("participants", NUMBEROFPARTICIPANTS);
//      put("threads", NUMBEROFTHREADS);
//    }
//  });
}
