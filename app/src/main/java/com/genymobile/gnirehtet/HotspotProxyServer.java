package com.genymobile.gnirehtet;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes2.dex */
public final class HotspotProxyServer {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int MAX_HEADERS = 128;
    private static final int MAX_REQUEST_LINE = 8192;
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final String TAG = HotspotProxyServer.class.getSimpleName();
    private final String bindAddress;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final int port;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public HotspotProxyServer(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public synchronized boolean start() {
        if (this.running) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(this.bindAddress);
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(address, this.port), 64);
            this.serverSocket = socket;
            this.running = true;
            Thread acceptThread = new Thread(new Runnable() { // from class: com.genymobile.gnirehtet.HotspotProxyServer$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    HotspotProxyServer.this.acceptLoop();
                }
            }, "NetBridgeX-Proxy-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            Log.i(TAG, "Hotspot HTTP CONNECT proxy listening on " + this.bindAddress + ":" + this.port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Could not start hotspot proxy on " + this.bindAddress + ":" + this.port, e);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        this.running = false;
        ServerSocket socket = this.serverSocket;
        this.serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Cannot close proxy listener", e);
            }
            this.executor.shutdownNow();
            try {
                this.executor.awaitTermination(1L, TimeUnit.SECONDS);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
            }
        } else {
            this.executor.shutdownNow();
            try {
                this.executor.awaitTermination(1L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void acceptLoop() {
        while (this.running) {
            try {
                final Socket client = this.serverSocket.accept();
                client.setSoTimeout(15000);
                this.executor.execute(new Runnable() { // from class: com.genymobile.gnirehtet.HotspotProxyServer$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        HotspotProxyServer.this.lambda$acceptLoop$0(client);
                    }
                });
            } catch (IOException e) {
                if (this.running) {
                    Log.w(TAG, "Proxy accept failed", e);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: handleClient, reason: merged with bridge method [inline-methods] */
    public void lambda$acceptLoop$0(Socket client) {
        String header;
        try {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
                OutputStream clientOut = client.getOutputStream();
                String requestLine = reader.readLine();
                if (requestLine != null && requestLine.length() <= MAX_REQUEST_LINE) {
                    List<String> headers = new ArrayList<>();
                    for (int i = 0; i < MAX_HEADERS && (header = reader.readLine()) != null && !header.isEmpty(); i++) {
                        if (header.length() > MAX_REQUEST_LINE) {
                            writeResponse(clientOut, "431 Request Header Fields Too Large", "Header too large");
                            if (client != null) {
                                client.close();
                                return;
                            }
                            return;
                        }
                        headers.add(header);
                    }
                    String[] parts = requestLine.split(" ", 3);
                    if (parts.length < 3) {
                        writeResponse(clientOut, "400 Bad Request", "Invalid request line");
                        if (client != null) {
                            client.close();
                            return;
                        }
                        return;
                    }
                    String method = parts[0].toUpperCase(Locale.US);
                    String target = parts[1];
                    if ("CONNECT".equals(method)) {
                        handleConnect(client, clientOut, target);
                        if (client != null) {
                            client.close();
                            return;
                        }
                        return;
                    }
                    writeResponse(clientOut, "501 Not Implemented", "NetBridgeX currently supports HTTPS via HTTP CONNECT proxy");
                    if (client != null) {
                        client.close();
                        return;
                    }
                    return;
                }
                writeResponse(clientOut, "400 Bad Request", "Invalid request");
                if (client != null) {
                    client.close();
                }
            } catch (Throwable th) {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (IOException e) {
            Log.d(TAG, "Proxy client closed: " + e.getMessage());
        }
    }

    private void handleConnect(Socket client, OutputStream clientOut, String target) throws IOException {
        HostPort hostPort = parseHostPort(target, 443);
        Socket remote = new Socket();
        try {
            remote.connect(new InetSocketAddress(hostPort.host, hostPort.port), 15000);
            remote.setSoTimeout(15000);
            PrintWriter writer = new PrintWriter(clientOut, false, StandardCharsets.ISO_8859_1);
            writer.print("HTTP/1.1 200 Connection Established\r\n");
            writer.print("Proxy-Agent: NetBridgeX\r\n");
            writer.print("\r\n");
            writer.flush();
            relay(client, remote);
        } finally {
            try {
                remote.close();
            } catch (IOException e) {
                Log.d(TAG, "Cannot close remote proxy socket", e);
            }
        }
    }

    private static HostPort parseHostPort(String value, int defaultPort) throws IOException {
        if (value == null || value.isEmpty()) {
            throw new IOException("Missing CONNECT target");
        }
        if (value.charAt(0) == '[') {
            int close = value.indexOf(93);
            if (close < 0) {
                throw new IOException("Invalid IPv6 CONNECT target");
            }
            String host = value.substring(1, close);
            int port = defaultPort;
            if (close + 1 < value.length() && value.charAt(close + 1) == ':') {
                port = Integer.parseInt(value.substring(close + 2));
            }
            return new HostPort(host, port);
        }
        int separator = value.lastIndexOf(58);
        if (separator > 0 && separator < value.length() - 1) {
            try {
                return new HostPort(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
            } catch (NumberFormatException e) {
            }
        }
        return new HostPort(value, defaultPort);
    }

    private static void relay(final Socket client, final Socket remote) throws IOException {
        Thread upstream = new Thread(new Runnable() { // from class: com.genymobile.gnirehtet.HotspotProxyServer$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                HotspotProxyServer.copy(client, remote);
            }
        }, "NetBridgeX-Proxy-Up");
        Thread downstream = new Thread(new Runnable() { // from class: com.genymobile.gnirehtet.HotspotProxyServer$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                HotspotProxyServer.copy(remote, client);
            }
        }, "NetBridgeX-Proxy-Down");
        upstream.start();
        downstream.start();
        try {
            upstream.join();
            downstream.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void copy(Socket source, Socket destination) {
        try {
            try {
                try {
                    InputStream in = source.getInputStream();
                    OutputStream out = destination.getOutputStream();
                    byte[] buffer = new byte[32768];
                    while (true) {
                        int read = in.read(buffer);
                        if (read < 0) {
                            break;
                        } else if (read != 0) {
                            out.write(buffer, 0, read);
                            out.flush();
                        }
                    }
                    destination.shutdownOutput();
                } catch (IOException e) {
                    Log.d(TAG, "Proxy stream ended: " + e.getMessage());
                    destination.shutdownOutput();
                }
            } catch (Throwable th) {
                try {
                    destination.shutdownOutput();
                } catch (IOException e2) {
                }
                throw th;
            }
        } catch (IOException e3) {
        }
    }

    private static void writeResponse(OutputStream out, String status, String message) throws IOException {
        String body = message + "\r\n";
        PrintWriter writer = new PrintWriter(out, false, StandardCharsets.ISO_8859_1);
        writer.print("HTTP/1.1 " + status + "\r\n");
        writer.print("Content-Type: text/plain; charset=utf-8\r\n");
        writer.print("Content-Length: " + body.getBytes(StandardCharsets.ISO_8859_1).length + "\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.print(body);
        writer.flush();
    }

    private static final class HostPort {
        private final String host;
        private final int port;

        private HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}