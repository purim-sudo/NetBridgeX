package com.genymobile.gnirehtet;

import android.content.Context;
import android.content.Intent;
import com.netbridgex.android.R;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import java.io.IOException;
import java.net.InetAddress;

public class GnirehtetService extends VpnService {
    private static final String ACTION_START_VPN = "com.genymobile.gnirehtet.START_VPN";
    private static final String ACTION_STOP_VPN = "com.genymobile.gnirehtet.STOP_VPN";
    private static final String EXTRA_PROXY_BIND_ADDRESS = "proxyBindAddress";
    private static final String EXTRA_PROXY_PORT = "proxyPort";
    private static final String EXTRA_VPN_CONFIGURATION = "vpnConfiguration";
    private static final int MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT = 2;
    // NetBridgeX USB-only transport uses a conservative 1500-byte IPv4 MTU.
    private static final int MTU = 1500;
    private static final long RELAY_DISCONNECT_SHUTDOWN_DELAY_MS = 5000;
    public static final boolean VERBOSE = false;
    private boolean closing;
    private Forwarder forwarder;
    private HotspotProxyServer hotspotProxy;
    private ParcelFileDescriptor vpnInterface;
    private static final String TAG = GnirehtetService.class.getSimpleName();
    private static final InetAddress VPN_ADDRESS = Net.toInetAddress(new byte[]{10, 0, 0, 2});
    private final Notifier notifier = new Notifier(this);
    private final Handler handler = new RelayTunnelConnectionStateHandler(this);

    public static void start(Context context, VpnConfiguration config) {
        start(context, config, null, 0);
    }

    public static void start(Context context, VpnConfiguration config, String proxyBindAddress, int proxyPort) {
        Intent intent = new Intent(context, (Class<?>) GnirehtetService.class).setAction(ACTION_START_VPN).putExtra(EXTRA_VPN_CONFIGURATION, config);
        if (proxyBindAddress != null && proxyPort > 0) {
            intent.putExtra("proxyBindAddress", proxyBindAddress);
            intent.putExtra("proxyPort", proxyPort);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, (Class<?>) GnirehtetService.class).setAction(ACTION_STOP_VPN);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand action=" + (intent == null ? "null" : intent.getAction()));
        if (intent != null && ACTION_STOP_VPN.equals(intent.getAction())) {
            Log.i(TAG, "Explicit stop requested");
            close();
            stopSelf(startId);
            return MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT;
        }
        if (intent == null || !ACTION_START_VPN.equals(intent.getAction())) {
            Log.w(TAG, "Ignoring empty or unknown start request");
            stopSelf(startId);
            return MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT;
        }
        if (isRunning()) {
            Log.d(TAG, "VPN already running, ignoring START request");
            return MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT;
        }
        try {
            this.notifier.start();
            Log.i(TAG, "Foreground notification started");
        } catch (Throwable foregroundFailure) {
            Log.e(TAG, "Foreground service startup failed", foregroundFailure);
            stopSelf(startId);
            return MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT;
        }
        VpnConfiguration config = (VpnConfiguration) intent.getParcelableExtra(EXTRA_VPN_CONFIGURATION);
        if (config == null) {
            config = new VpnConfiguration();
        }
        String proxyBindAddress = intent.getStringExtra("proxyBindAddress");
        int proxyPort = intent.getIntExtra("proxyPort", 0);
        Log.i(TAG, "Proxy configuration: bind=" + proxyBindAddress + " port=" + proxyPort);
        if (setupVpn(config)) {
            startHotspotProxy(proxyBindAddress, proxyPort);
            startForwarding();
        } else {
            close();
        }
        return MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT;
    }

    private synchronized boolean isRunning() {
        return (this.vpnInterface == null || this.closing) ? false : true;
    }

    private boolean setupVpn(VpnConfiguration config) {
        VpnService.Builder builder = new VpnService.Builder()
                .addAddress(VPN_ADDRESS, 32)
                .setSession(getString(R.string.app_name))
                .setBlocking(true)
                .setMtu(MTU);

        // The reverse-tether relay is currently IPv4. Do not capture IPv6 packets
        // into a relay that cannot transport them end-to-end.
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            builder.setUnderlyingNetworks(new Network[0]);
        }

        CIDR[] routes = config.getRoutes();
        if (routes.length == 0) {
            builder.addRoute("0.0.0.0", 0);
        } else {
            for (CIDR route : routes) {
                builder.addRoute(route.getAddress(), route.getPrefixLength());
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            for (CIDR cidr : config.getExcludedRoutes()) {
                builder.excludeRoute(cidr.getIpPrefix());
            }
        }

        InetAddress[] dnsServers = config.getDnsServers();
        if (dnsServers.length == 0) {
            builder.addDnsServer("8.8.8.8");
        } else {
            for (InetAddress dnsServer : dnsServers) {
                builder.addDnsServer(dnsServer);
            }
        }

        try {
            Log.i(TAG, "Establishing VPN interface");
            this.vpnInterface = builder.establish();
            if (this.vpnInterface != null) {
                return true;
            }
            Log.w(TAG, "VPN starting failed or authorization was revoked");
            return false;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            Log.e(TAG, "Could not establish VPN interface", e);
            return false;
        }
    }

    private void startHotspotProxy(String bindAddress, int port) {
        if (bindAddress == null || bindAddress.isEmpty() || port <= 0) {
            return;
        }
        HotspotProxyServer proxy = new HotspotProxyServer(bindAddress, port);
        if (proxy.start()) {
            this.hotspotProxy = proxy;
        } else {
            Log.w(TAG, "Hotspot proxy could not be started");
        }
    }

    private void startForwarding() {
        ParcelFileDescriptor descriptor = this.vpnInterface;
        if (descriptor == null) {
            close();
        } else {
            this.forwarder = new Forwarder(this, descriptor.getFileDescriptor(), new RelayTunnelListener(this.handler));
            this.forwarder.forward();
        }
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "VPN authorization revoked");
        close();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    private synchronized void close() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        Forwarder localForwarder = this.forwarder;
        this.forwarder = null;
        ParcelFileDescriptor localInterface = this.vpnInterface;
        this.vpnInterface = null;
        HotspotProxyServer localProxy = this.hotspotProxy;
        this.hotspotProxy = null;
        if (localProxy != null) {
            localProxy.stop();
        }
        if (localForwarder != null) {
            localForwarder.stop();
        }
        if (localInterface != null) {
            try {
                localInterface.close();
            } catch (IOException e) {
                Log.w(TAG, "Cannot close VPN file descriptor", e);
            }
        }
        this.handler.removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
        this.notifier.stop();
        stopSelf();
        this.closing = false;
    }

    private static final class RelayTunnelConnectionStateHandler extends Handler {
        private final GnirehtetService vpnService;

        private RelayTunnelConnectionStateHandler(GnirehtetService vpnService) {
            this.vpnService = vpnService;
        }

        @Override
        public void handleMessage(Message message) {
            if (!this.vpnService.isRunning()) {
                return;
            }
            switch (message.what) {
                case 0:
                    removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
                    this.vpnService.notifier.setFailure(false);
                    break;
                case 1:
                    this.vpnService.notifier.setFailure(true);
                    removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
                    sendEmptyMessageDelayed(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT, RELAY_DISCONNECT_SHUTDOWN_DELAY_MS);
                    break;
                case MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT:
                    Log.i(GnirehtetService.TAG, "Relay disconnected, stopping VPN");
                    this.vpnService.close();
                    break;
            }
        }
    }
}
