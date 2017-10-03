package org.jitsi.videobridge.metadata;

import java.net.InetAddress;

public interface InstanceMetadata {
    /**
     * @return this server instance's unique ID, or null if not avaiable.
     */
    String getId();

    /**
     * @return this server instance's host name, or null if not available.
     */
    String getHostname();

    /**
     * @return this server instance's public IP address, or null if not available.
     */
    InetAddress getPublicIpAddress();

    /**
     * @return this server instance's internal IP address, or null if not available.
     */
    InetAddress getInternalIpAddress();

    /**
     * @return this server's DC provider, or null if not available.
     */
    DataCenterProvider getDataCenterProvider();

    /**
     * @return the region in which this server is deployed, or null if not available
     */
    String getRegion();

    /**
     * @return the AZ in which this server is deployed, or null if not available
     */
    String getAvailabilityZone();
}