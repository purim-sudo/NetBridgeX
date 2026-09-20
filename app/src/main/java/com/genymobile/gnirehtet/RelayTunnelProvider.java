[Reading 91 lines from start (total: 91 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.net.VpnService;
import java.io.IOException;

/* JADX INFO: loaded from: classes2.dex */
public class RelayTunnelProvider {
    private static final int DELAY_BETWEEN_ATTEMPTS_MS = 5000;
    private long lastFailureTimestamp;
    private final RelayTunnelListener listener;
    private RelayTunnel tunnel;
    private final VpnService vpnService;
    private final Object getCurrentTunnelLock = new Object();
    private boolean first = true;

    public RelayTunnelProvider(VpnService vpnService, RelayTunnelListener listener) {
        this.vpnService = vpnService;
        this.listener = listener;
    }

    public RelayTunnel getCurrentTunnel() throws InterruptedException, IOException {
        synchronized (this.getCurrentTunnelLock) {
            synchronized (this) {
                if (this.tunnel != null) {
                    return this.tunnel;
                }
                waitUntilNextAttemptSlot();
                this.tunnel = RelayTunnel.open(this.vpnService);
                boolean notifyDisconnectedOnError = this.first;
                this.first = false;
                connectTunnel(notifyDisconnectedOnError);
                return this.tunnel;
            }
        }
    }

    private void connectTunnel(boolean notifyDisconnectedOnError) throws IOException {
        try {
            this.tunnel.connect();
            notifyConnected();
        } catch (IOException e) {
            touchFailure();
            if (notifyDisconnectedOnError) {
                notifyDisconnected();
            }
            throw e;
        }
    }

    public synchronized void invalidateTunnel() {
        if (this.tunnel != null) {
            touchFailure();
            this.tunnel.close();
            this.tunnel = null;
            notifyDisconnected();
        }
    }

    public synchronized void invalidateTunnel(Tunnel tunnelToInvalidate) {
        if (this.tunnel == tunnelToInvalidate || tunnelToInvalidate == null) {
            invalidateTunnel();
        }
    }

    private synchronized void touchFailure() {
        this.lastFailureTimestamp = System.currentTimeMillis();
    }

    private void waitUntilNextAttemptSlot() throws InterruptedException {
        if (this.first) {
            return;
        }
        long delay = (this.lastFailureTimestamp + 5000) - System.currentTimeMillis();
        while (delay > 0) {
            wait(delay);
            delay = (this.lastFailureTimestamp + 5000) - System.currentTimeMillis();
        }
    }

    private void notifyConnected() {
        if (this.listener != null) {
            this.listener.notifyRelayTunnelConnected();
        }
    }

    private void notifyDisconnected() {
        if (this.listener != null) {
            this.listener.notifyRelayTunnelDisconnected();
        }
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]