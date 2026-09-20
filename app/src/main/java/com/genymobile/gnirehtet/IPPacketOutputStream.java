[Reading 95 lines from start (total: 95 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes2.dex */
public class IPPacketOutputStream extends OutputStream implements AutoCloseable {
    private static final int MAX_IP_PACKET_LENGTH = 65536;
    private static final String TAG = IPPacketOutputStream.class.getSimpleName();
    private final ByteBuffer buffer = ByteBuffer.allocate(131072);
    private final OutputStream target;

    public IPPacketOutputStream(OutputStream target) {
        this.target = target;
    }

    @Override // java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        this.target.close();
    }

    @Override // java.io.OutputStream, java.io.Flushable
    public void flush() throws IOException {
        this.target.flush();
    }

    @Override // java.io.OutputStream
    public void write(byte[] b, int off, int len) throws IOException {
        if (len > MAX_IP_PACKET_LENGTH) {
            throw new IOException("IPPacketOutputStream does not support writing more than one packet at a time");
        }
        if (BuildConfig.DEBUG && len > this.buffer.remaining()) {
            Log.e(TAG, len + " must be <= than " + this.buffer.remaining());
            Log.e(TAG, this.buffer.toString());
            throw new AssertionError("Buffer is unexpectedly full");
        }
        this.buffer.put(b, off, len);
        this.buffer.flip();
        sink();
        this.buffer.compact();
    }

    @Override // java.io.OutputStream
    public void write(int b) throws IOException {
        if (!this.buffer.hasRemaining()) {
            throw new IOException("IPPacketOutputStream buffer is full");
        }
        this.buffer.put((byte) b);
        this.buffer.flip();
        sink();
        this.buffer.compact();
    }

    private void sink() throws IOException {
        while (sinkPacket()) {
        }
    }

    private boolean sinkPacket() throws IOException {
        int version = readPacketVersion(this.buffer);
        if (version == -1) {
            return false;
        }
        if (version != 4) {
            Log.e(TAG, "Unsupported packet received, IP version is:" + version);
            Log.d(TAG, "Clearing buffer");
            this.buffer.clear();
            return false;
        }
        int packetLength = readPacketLength(this.buffer);
        if (packetLength == -1 || packetLength > this.buffer.remaining()) {
            return false;
        }
        this.target.write(this.buffer.array(), this.buffer.arrayOffset() + this.buffer.position(), packetLength);
        this.buffer.position(this.buffer.position() + packetLength);
        return true;
    }

    public static int readPacketVersion(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        byte versionAndIHL = buffer.get(buffer.position());
        return (versionAndIHL & 240) >> 4;
    }

    public static int readPacketLength(ByteBuffer buffer) {
        if (buffer.limit() < buffer.position() + 4) {
            return -1;
        }
        return Binary.unsigned(buffer.getShort(buffer.position() + 2));
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]