package com.genymobile.gnirehtet;

import android.net.VpnService;
import android.util.Log;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes2.dex */
public class PersistentRelayTunnel implements Tunnel {
    private static final String TAG = PersistentRelayTunnel.class.getSimpleName();
    private final RelayTunnelProvider provider;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public PersistentRelayTunnel(VpnService vpnService, RelayTunnelListener listener) {
        this.provider = new RelayTunnelProvider(vpnService, listener);
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public void send(byte[] packet, int len) throws IOException {
        while (!this.stopped.get()) {
            Tunnel tunnel = null;
            try {
                tunnel = this.provider.getCurrentTunnel();
                tunnel.send(packet, len);
                return;
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Cannot send to tunnel", e);
                if (tunnel != null) {
                    this.provider.invalidateTunnel(tunnel);
                }
            }
        }
        throw new InterruptedIOException("Persistent tunnel stopped");
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public int receive(byte[] packet) throws IOException {
        while (!this.stopped.get()) {
            Tunnel tunnel = null;
            try {
                tunnel = this.provider.getCurrentTunnel();
                int r = tunnel.receive(packet);
                if (r == -1) {
                    Log.d(TAG, "Tunnel read EOF");
                    this.provider.invalidateTunnel(tunnel);
                } else {
                    return r;
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Cannot receive from tunnel", e);
                if (tunnel != null) {
                    this.provider.invalidateTunnel(tunnel);
                }
            }
        }
        throw new InterruptedIOException("Persistent tunnel stopped");
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public void close() {
        this.stopped.set(true);
        this.provider.invalidateTunnel();
    }
}