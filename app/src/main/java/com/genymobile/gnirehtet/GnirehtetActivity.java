package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private static final int VPN_REQUEST_CODE = 0;
    private TextView statusView;
    private TextView pathView;
    private boolean notificationPermissionRequested;
    private VpnConfiguration requestedConfig;
    private String requestedProxyBindAddress;
    private int requestedProxyPort;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handleIntent(getIntent());
        refreshUi();
        mainHandler.post(statusRefresh);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        refreshUi();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("NetBridgeX");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Windows reverse tethering over USB");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(8), 0, dp(24));
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        this.statusView = new TextView(this);
        this.statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        this.statusView.setGravity(Gravity.CENTER_HORIZONTAL);
        this.statusView.setPadding(0, dp(16), 0, dp(16));
        content.addView(this.statusView, new LinearLayout.LayoutParams(-1, -2));

        this.pathView = new TextView(this);
        this.pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        this.pathView.setGravity(Gravity.CENTER_HORIZONTAL);
        this.pathView.setPadding(0, 0, 0, dp(24));
        content.addView(this.pathView, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this);
        start.setText("Start NetBridgeX");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFromUi();
            }
        });
        content.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("Stop NetBridgeX");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGnirehtet();
                refreshUi();
            }
        });
        content.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        Button refresh = new Button(this);
        refresh.setText("Refresh status");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshUi();
            }
        });
        content.addView(refresh, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("NetBridgeX uses Android VPN + the Windows Gnirehtet relay. Wi-Fi and mobile data can be disabled; the USB relay remains the transport.");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        info.setPadding(0, dp(28), 0, 0);
        content.addView(info, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void startFromUi() {
        String[] dns = new String[]{"8.8.8.8"};
        String[] routes = new String[]{"0.0.0.0/0"};
        VpnConfiguration config = new VpnConfiguration(
                Net.toInetAddresses(dns),
                Net.toCIDRs(routes),
                new CIDR[0],
                new String[0],
                new String[0],
                null,
                0);
        this.requestedProxyBindAddress = null;
        this.requestedProxyPort = 0;
        startGnirehtet(config, null, 0);
        refreshUi();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String rawProxyBind = intent.getStringExtra(EXTRA_PROXY_BIND_ADDRESS);
        int rawProxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1);
        boolean command = false;
        if (ACTION_GNIREHTET_START.equals(action)) {
            command = true;
            VpnConfiguration config = createConfig(intent);
            this.requestedProxyBindAddress = rawProxyBind;
            this.requestedProxyPort = rawProxyPort;
            startGnirehtet(config, rawProxyBind, rawProxyPort);
        } else if (ACTION_GNIREHTET_STOP.equals(action)) {
            command = true;
            stopGnirehtet();
        }
        if (command) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshUi();
                }
            }, 800L);
        }
    }

    private static VpnConfiguration createConfig(Intent intent) {
        String[] dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS);
        if (dnsServers == null) dnsServers = new String[0];
        String[] routes = intent.getStringArrayExtra(EXTRA_ROUTES);
        if (routes == null) routes = new String[0];
        String[] excludedRoutes = intent.getStringArrayExtra(EXTRA_EXCLUDED_ROUTES);
        if (excludedRoutes == null) excludedRoutes = new String[0];
        String[] apps = intent.getStringArrayExtra(EXTRA_APPS);
        if (apps == null) apps = new String[0];
        String[] excludedApps = intent.getStringArrayExtra(EXTRA_EXCLUDED_APPS);
        if (excludedApps == null) excludedApps = new String[0];
        String proxyBindAddress = intent.getStringExtra(EXTRA_PROXY_BIND_ADDRESS);
        int proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, 0);
        return new VpnConfiguration(
                Net.toInetAddresses(dnsServers),
                Net.toCIDRs(routes),
                Net.toCIDRs(excludedRoutes),
                apps,
                excludedApps,
                proxyBindAddress,
                proxyPort);
    }

    private boolean startGnirehtet(VpnConfiguration config, String proxyBindAddress, int proxyPort) {
        if (shouldRequestNotificationPermission()) {
            this.requestedConfig = config;
            this.notificationPermissionRequested = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            GnirehtetService.start(this, config, proxyBindAddress, proxyPort);
            return true;
        }
        this.requestedConfig = config;
        startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        return false;
    }

    private void stopGnirehtet() {
        GnirehtetService.stop(this);
    }

    private void refreshUi() {
        if (this.statusView == null) return;
        boolean vpnActive = false;
        String vpnInterface = null;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpnActive = true;
                    LinkProperties lp = cm.getLinkProperties(network);
                    if (lp != null) vpnInterface = lp.getInterfaceName();
                    break;
                }
            }
        }
        boolean authorized = VpnService.prepare(this) == null;
        if (vpnActive) {
            this.statusView.setText("STATUS: ACTIVE");
            this.statusView.setTextColor(Color.rgb(0, 128, 0));
            this.pathView.setText("VPN interface: " + (vpnInterface == null ? "tun0" : vpnInterface) + "\nWindows relay path: USB / ADB reverse");
        } else {
            this.statusView.setText(authorized ? "STATUS: STOPPED" : "STATUS: VPN APPROVAL NEEDED");
            this.statusView.setTextColor(Color.rgb(180, 90, 0));
            this.pathView.setText("VPN authorized: " + (authorized ? "yes" : "no") + "\nStart NetBridgeX to establish the Windows tunnel.");
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean shouldRequestNotificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                && !this.notificationPermissionRequested
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            GnirehtetService.start(this, this.requestedConfig, this.requestedProxyBindAddress, this.requestedProxyPort);
        }
        this.requestedConfig = null;
        this.requestedProxyBindAddress = null;
        this.requestedProxyPort = 0;
        refreshUi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (this.requestedConfig != null) {
                GnirehtetService.start(this, this.requestedConfig, this.requestedProxyBindAddress, this.requestedProxyPort);
            }
            this.requestedConfig = null;
            refreshUi();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(this.statusRefresh);
        super.onDestroy();
    }
}
