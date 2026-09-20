[Reading 65 lines from start (total: 65 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.util.Log;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/* JADX INFO: loaded from: classes2.dex */
public final class RelayTunnel implements Tunnel {
    private static final String LOCAL_ABSTRACT_NAME = "gnirehtet";
    private static final String TAG = RelayTunnel.class.getSimpleName();
    private final LocalSocket localSocket = new LocalSocket();

    private RelayTunnel() {
    }

    public static RelayTunnel open(VpnService vpnService) throws IOException {
        Log.d(TAG, "Opening a new relay tunnel...");
        return new RelayTunnel();
    }

    public void connect() throws IOException {
        this.localSocket.connect(new LocalSocketAddress(LOCAL_ABSTRACT_NAME));
        try {
            this.localSocket.setSendBufferSize(1048576);
            this.localSocket.setReceiveBufferSize(1048576);
        } catch (IOException e) {
            Log.w(TAG, "Could not enlarge relay socket buffers", e);
        }
        readClientId(this.localSocket.getInputStream());
    }

    private static void readClientId(InputStream inputStream) throws IOException {
        Log.d(TAG, "Requesting client id");
        int clientId = new DataInputStream(inputStream).readInt();
        Log.d(TAG, "Connected to the relay server as #" + Binary.unsigned(clientId));
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public void send(byte[] packet, int len) throws IOException {
        this.localSocket.getOutputStream().write(packet, 0, len);
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public int receive(byte[] packet) throws IOException {
        int r = this.localSocket.getInputStream().read(packet);
        return r;
    }

    @Override // com.genymobile.gnirehtet.Tunnel
    public void close() {
        try {
            if (this.localSocket.getFileDescriptor() != null) {
                this.localSocket.shutdownInput();
                this.localSocket.shutdownOutput();
            }
            this.localSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]