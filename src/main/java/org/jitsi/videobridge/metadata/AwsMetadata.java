///
// Copyright (c) 2016. Highfive Technologies, Inc.
///
package org.jitsi.videobridge.metadata;

import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;


public class AwsMetadata implements InstanceMetadata {
    private static final Logger log = LoggerFactory.getLogger(AwsMetadata.class);
    private static final String METADATA_URI_BASE = "http://169.254.169.254/";
    private static final String REGION_SUFFIX = "/latest/dynamic/instance-identity/document";
    private static final String ZONE_SUFFIX = "/latest/meta-data/placement/availability-zone";
    private static final String PUBLIC_IPV4_SUFFIX = "/latest/meta-data/public-ipv4";
    private static final String PRIVATE_IPV4_SUFFIX = "latest/meta-data/local-ipv4";
    private static final String HOSTNAME_SUFFIX = "/latest/meta-data/hostname";
    private static final String ID_SUFFIX = "/latest/meta-data/instance-id";

    private final URI baseUri;

    private String id;
    private String region;
    private String hostname;
    private String zone;
    private InetAddress publicIp;
    private InetAddress privateIp;

    public AwsMetadata() {
        try {
            baseUri = new URI(METADATA_URI_BASE);
        } catch (URISyntaxException e) {
            Preconditions.checkState(false, "Unexpected URI Syntax Exception: {}", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataCenterProvider getDataCenterProvider() {
        return DataCenterProvider.AWS;
    }

    @Override
    public String getRegion() {
        if (region == null) {
            try {
                String dynamicData = getMetadataAt(REGION_SUFFIX);
                if (dynamicData != null) {
                    JSONObject obj = (JSONObject) JSONValue.parse(dynamicData);
                    region = (String) obj.get("region");
                }
            } catch (ClassCastException e) {
                log.error("Unable to get region, expecting a JSON Object", e);
            }
        }
        return region;
    }

    @Override
    public String getAvailabilityZone() {
        if (zone == null) {
            zone = getMetadataAt(ZONE_SUFFIX);
        }
        return zone;
    }

    @Override
    public String getId() {
        if (id == null) {
            id = getMetadataAt(ID_SUFFIX);
        }

        return id;
    }

    @Override
    public String getHostname() {
        if (hostname == null) {
            hostname = getMetadataAt(HOSTNAME_SUFFIX);
        }
        return hostname;
    }

    @Override
    public InetAddress getPublicIpAddress() {
        if (publicIp == null) {
            String ip = getMetadataAt(PUBLIC_IPV4_SUFFIX);
            if (ip != null) {
                publicIp = InetAddresses.forString(ip);
            }
        }
        return publicIp;
    }

    @Override
    public InetAddress getInternalIpAddress() {
        if (privateIp == null) {
            String ip = getMetadataAt(PRIVATE_IPV4_SUFFIX);
            if (ip != null) {
                privateIp = InetAddresses.forString(ip);
            }
        }
        return privateIp;
    }

    private String getMetadataAt(String suffix) {
        try {
            URI resolved = baseUri.resolve(suffix);
            StringBuilder builder = new StringBuilder();
            String line = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resolved.toURL().openStream()))) {
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            }
        } catch (Exception e) {
            // ignore for now to make sure local servers don't go verbose
        }
        return null;
    }
}
