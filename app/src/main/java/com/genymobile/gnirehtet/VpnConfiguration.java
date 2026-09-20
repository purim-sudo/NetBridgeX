[Reading 115 lines from start (total: 115 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: classes2.dex */
public class VpnConfiguration implements Parcelable {
    public static final Parcelable.Creator<VpnConfiguration> CREATOR = new Parcelable.Creator<VpnConfiguration>() { // from class: com.genymobile.gnirehtet.VpnConfiguration.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public VpnConfiguration createFromParcel(Parcel source) {
            return new VpnConfiguration(source);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public VpnConfiguration[] newArray(int size) {
            return new VpnConfiguration[size];
        }
    };
    private final String[] apps;
    private final InetAddress[] dnsServers;
    private final String[] excludedApps;
    private final CIDR[] excludedRoutes;
    private final String proxyBindAddress;
    private final int proxyPort;
    private final CIDR[] routes;

    public VpnConfiguration() {
        this.dnsServers = new InetAddress[0];
        this.routes = new CIDR[0];
        this.excludedRoutes = new CIDR[0];
        this.apps = new String[0];
        this.excludedApps = new String[0];
        this.proxyBindAddress = null;
        this.proxyPort = 0;
    }

    public VpnConfiguration(InetAddress[] dnsServers, CIDR[] routes, CIDR[] excludedRoutes, String[] apps, String[] excludedApps, String proxyBindAddress, int proxyPort) {
        this.dnsServers = dnsServers;
        this.routes = routes;
        this.excludedRoutes = excludedRoutes;
        this.apps = apps;
        this.excludedApps = excludedApps;
        this.proxyBindAddress = proxyBindAddress;
        this.proxyPort = proxyPort;
    }

    private VpnConfiguration(Parcel source) {
        int dnsCount = source.readInt();
        this.dnsServers = new InetAddress[dnsCount];
        for (int i = 0; i < dnsCount; i++) {
            try {
                this.dnsServers[i] = InetAddress.getByAddress(source.createByteArray());
            } catch (UnknownHostException e) {
                throw new AssertionError("Invalid address", e);
            }
        }
        this.routes = (CIDR[]) source.createTypedArray(CIDR.CREATOR);
        this.excludedRoutes = (CIDR[]) source.createTypedArray(CIDR.CREATOR);
        this.apps = source.createStringArray();
        this.excludedApps = source.createStringArray();
        this.proxyBindAddress = source.readString();
        this.proxyPort = source.readInt();
    }

    public InetAddress[] getDnsServers() {
        return this.dnsServers;
    }

    public CIDR[] getRoutes() {
        return this.routes;
    }

    public CIDR[] getExcludedRoutes() {
        return this.excludedRoutes;
    }

    public String[] getApps() {
        return this.apps;
    }

    public String[] getExcludedApps() {
        return this.excludedApps;
    }

    public String getProxyBindAddress() {
        return this.proxyBindAddress;
    }

    public int getProxyPort() {
        return this.proxyPort;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.dnsServers.length);
        for (InetAddress addr : this.dnsServers) {
            dest.writeByteArray(addr.getAddress());
        }
        dest.writeTypedArray(this.routes, 0);
        dest.writeTypedArray(this.excludedRoutes, 0);
        dest.writeStringArray(this.apps);
        dest.writeStringArray(this.excludedApps);
        dest.writeString(this.proxyBindAddress);
        dest.writeInt(this.proxyPort);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]