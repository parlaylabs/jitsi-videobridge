package org.jitsi.videobridge.metadata;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class LocalMetadata implements InstanceMetadata {
    @Override
    public String getId() {
        try {
            return InetAddress.getLocalHost().toString();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public InetAddress getPublicIpAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public InetAddress getInternalIpAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public DataCenterProvider getDataCenterProvider() { return DataCenterProvider.LOCAL; }

    @Override
    public String getRegion() {
        return null;
    }

    @Override
    public String getAvailabilityZone() {
        return null;
    }
}