[Reading 144 lines from start (total: 144 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/* JADX INFO: loaded from: classes2.dex */
public class GnirehtetActivity extends Activity {
    public static final String ACTION_GNIREHTET_START = "com.netbridgex.android.START";
    public static final String ACTION_GNIREHTET_STOP = "com.netbridgex.android.STOP";
    public static final String EXTRA_APPS = "apps";
    public static final String EXTRA_DNS_SERVERS = "dnsServers";
    public static final String EXTRA_EXCLUDED_APPS = "excludedApps";
    public static final String EXTRA_EXCLUDED_ROUTES = "excludedRoutes";
    public static final String EXTRA_PROXY_BIND_ADDRESS = "proxyBindAddress";
    public static final String EXTRA_PROXY_PORT = "proxyPort";
    public static final String EXTRA_ROUTES = "routes";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = GnirehtetActivity.class.getSimpleName();
    private static final Class<?> TETHER_SHELL_ENTRYPOINT = NetBridgeXTetherShell.class;
    private static final int VPN_REQUEST_CODE = 0;
    private boolean notificationPermissionRequested;
    private VpnConfiguration requestedConfig;
    private String requestedProxyBindAddress;
    private int requestedProxyPort;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String rawProxyBind = intent.getStringExtra(EXTRA_PROXY_BIND_ADDRESS);
        int rawProxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1);
        Log.d(TAG, "Received request " + action + " proxyBind=" + rawProxyBind + " proxyPort=" + rawProxyPort);
        boolean finish = true;
        if (ACTION_GNIREHTET_START.equals(action)) {
            VpnConfiguration config = createConfig(intent);
            this.requestedProxyBindAddress = rawProxyBind;
            this.requestedProxyPort = rawProxyPort;
            finish = startGnirehtet(config, rawProxyBind, rawProxyPort);
        } else if (ACTION_GNIREHTET_STOP.equals(action)) {
            stopGnirehtet();
        }
        if (finish) {
            // Keep the activity alive briefly after requesting the VPN foreground service.
            // Some OEM Android builds reject/kill an immediately-finishing caller before the
            // foreground VPN service has completed startup.
            mainHandler.postDelayed(this::finish, 5000L);
        }
    }

    private static VpnConfiguration createConfig(Intent intent) {
        String[] apps;
        String[] excludedApps;
        String[] dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS);
        if (dnsServers == null) {
            dnsServers = new String[0];
        }
        String[] routes = intent.getStringArrayExtra(EXTRA_ROUTES);
        if (routes == null) {
            routes = new String[0];
        }
        String[] excludedRoutes = intent.getStringArrayExtra(EXTRA_EXCLUDED_ROUTES);
        if (excludedRoutes == null) {
            excludedRoutes = new String[0];
        }
        String[] apps2 = intent.getStringArrayExtra(EXTRA_APPS);
        if (apps2 != null) {
            apps = apps2;
        } else {
            apps = new String[0];
        }
        String[] excludedApps2 = intent.getStringArrayExtra(EXTRA_EXCLUDED_APPS);
        if (excludedApps2 != null) {
            excludedApps = excludedApps2;
        } else {
            excludedApps = new String[0];
        }
        String proxyBindAddress = intent.getStringExtra(EXTRA_PROXY_BIND_ADDRESS);
        int proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, 0);
        return new VpnConfiguration(Net.toInetAddresses(dnsServers), Net.toCIDRs(routes), Net.toCIDRs(excludedRoutes), apps, excludedApps, proxyBindAddress, proxyPort);
    }

    private boolean startGnirehtet(VpnConfiguration config, String proxyBindAddress, int proxyPort) {
        Log.i(TAG, "Starting NetBridgeX VPN; proxyBind=" + proxyBindAddress + " proxyPort=" + proxyPort);
        if (shouldRequestNotificationPermission()) {
            this.requestedConfig = config;
            this.notificationPermissionRequested = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            Log.d(TAG, "VPN was already authorized");
            GnirehtetService.start(this, config, proxyBindAddress, proxyPort);
            return true;
        }
        Log.w(TAG, "VPN requires the authorization from the user, requesting...");
        requestAuthorization(vpnIntent, config);
        return false;
    }

    private void stopGnirehtet() {
        GnirehtetService.stop(this);
    }

    private void requestAuthorization(Intent vpnIntent, VpnConfiguration config) {
        this.requestedConfig = config;
        startActivityForResult(vpnIntent, 0);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == -1) {
            GnirehtetService.start(this, this.requestedConfig, this.requestedProxyBindAddress, this.requestedProxyPort);
        }
        this.requestedConfig = null;
        this.requestedProxyBindAddress = null;
        this.requestedProxyPort = 0;
        finish();
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            startGnirehtet(this.requestedConfig, this.requestedProxyBindAddress, this.requestedProxyPort);
        }
    }

    private boolean shouldRequestNotificationPermission() {
        return (Build.VERSION.SDK_INT < 33 || this.notificationPermissionRequested || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) ? false : true;
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]