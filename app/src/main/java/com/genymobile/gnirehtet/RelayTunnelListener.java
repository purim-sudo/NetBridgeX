package com.genymobile.gnirehtet;

import android.os.Handler;

/* JADX INFO: loaded from: classes2.dex */
public class RelayTunnelListener {
    static final int MSG_RELAY_TUNNEL_CONNECTED = 0;
    static final int MSG_RELAY_TUNNEL_DISCONNECTED = 1;
    private final Handler handler;

    public RelayTunnelListener(Handler handler) {
        this.handler = handler;
    }

    public void notifyRelayTunnelConnected() {
        this.handler.sendEmptyMessage(MSG_RELAY_TUNNEL_CONNECTED);
    }

    public void notifyRelayTunnelDisconnected() {
        this.handler.sendEmptyMessage(MSG_RELAY_TUNNEL_DISCONNECTED);
    }
}