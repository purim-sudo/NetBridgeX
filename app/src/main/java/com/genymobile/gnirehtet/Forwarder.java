package com.genymobile.gnirehtet;

import android.net.VpnService;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/* JADX INFO: loaded from: classes2.dex */
public class Forwarder {
    private static final int BUFSIZE = 262144;
    private static final int DUMMY_PORT = 4242;
    private Future<?> deviceToTunnelFuture;
    private final PersistentRelayTunnel tunnel;
    private Future<?> tunnelToDeviceFuture;
    private final FileDescriptor vpnFileDescriptor;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(3);
    private static final String TAG = Forwarder.class.getSimpleName();
    private static final byte[] DUMMY_ADDRESS = {42, 42, 42, 42};

    public Forwarder(VpnService vpnService, FileDescriptor vpnFileDescriptor, RelayTunnelListener listener) {
        this.vpnFileDescriptor = vpnFileDescriptor;
        this.tunnel = new PersistentRelayTunnel(vpnService, listener);
    }

    public void forward() {
        this.deviceToTunnelFuture = EXECUTOR_SERVICE.submit(new Runnable() { // from class: com.genymobile.gnirehtet.Forwarder.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Forwarder.this.forwardDeviceToTunnel(Forwarder.this.tunnel);
                } catch (InterruptedIOException e) {
                    Log.d(Forwarder.TAG, "Device to tunnel interrupted");
                } catch (IOException e2) {
                    Log.e(Forwarder.TAG, "Device to tunnel exception", e2);
                }
            }
        });
        this.tunnelToDeviceFuture = EXECUTOR_SERVICE.submit(new Runnable() { // from class: com.genymobile.gnirehtet.Forwarder.2
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Forwarder.this.forwardTunnelToDevice(Forwarder.this.tunnel);
                } catch (InterruptedIOException e) {
                    Log.d(Forwarder.TAG, "Device to tunnel interrupted");
                } catch (IOException e2) {
                    Log.e(Forwarder.TAG, "Tunnel to device exception", e2);
                }
            }
        });
    }

    public void stop() {
        this.tunnel.close();
        this.tunnelToDeviceFuture.cancel(true);
        this.deviceToTunnelFuture.cancel(true);
        wakeUpReadWorkaround();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void forwardDeviceToTunnel(Tunnel tunnel) throws IOException {
        Log.d(TAG, "Device to tunnel forwarding started");
        FileInputStream vpnInput = new FileInputStream(this.vpnFileDescriptor);
        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            int r = vpnInput.read(buffer);
            if (r == -1) {
                Log.d(TAG, "VPN closed");
                Log.d(TAG, "Device to tunnel forwarding stopped");
                return;
            } else if (r > 0) {
                int version = buffer[0] >> 4;
                if (version == 4) {
                    tunnel.send(buffer, r);
                } else {
                    Log.w(TAG, "Unexpected packet IP version: " + version);
                }
            } else {
                Log.d(TAG, "Empty read");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void forwardTunnelToDevice(Tunnel tunnel) throws IOException {
        Log.d(TAG, "Tunnel to device forwarding started");
        FileOutputStream vpnOutput = new FileOutputStream(this.vpnFileDescriptor);
        IPPacketOutputStream packetOutputStream = new IPPacketOutputStream(vpnOutput);
        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            int w = tunnel.receive(buffer);
            if (w == -1) {
                Log.d(TAG, "Tunnel closed");
                Log.d(TAG, "Tunnel to device forwarding stopped");
                return;
            } else if (w > 0) {
                packetOutputStream.write(buffer, 0, w);
            } else {
                Log.d(TAG, "Empty write");
            }
        }
    }

    private void wakeUpReadWorkaround() {
        EXECUTOR_SERVICE.execute(new Runnable() { // from class: com.genymobile.gnirehtet.Forwarder.3
            @Override // java.lang.Runnable
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    InetAddress dummyAddr = InetAddress.getByAddress(Forwarder.DUMMY_ADDRESS);
                    DatagramPacket packet = new DatagramPacket(new byte[0], 0, dummyAddr, Forwarder.DUMMY_PORT);
                    socket.send(packet);
                } catch (IOException e) {
                }
            }
        });
    }
}