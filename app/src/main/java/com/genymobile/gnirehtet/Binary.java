package com.genymobile.gnirehtet;

/* JADX INFO: loaded from: classes2.dex */
public final class Binary {
    private static final int MAX_STRING_PACKET_SIZE = 20;

    private Binary() {
    }

    public static int unsigned(byte value) {
        return value & 255;
    }

    public static int unsigned(short value) {
        return 65535 & value;
    }

    public static long unsigned(int value) {
        return ((long) value) & 4294967295L;
    }

    public static String buildPacketString(byte[] data, int len) {
        int limit = Math.min(MAX_STRING_PACKET_SIZE, len);
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(len).append(" bytes] ");
        for (int i = 0; i < limit; i++) {
            if (i != 0) {
                String sep = i % 4 == 0 ? "  " : " ";
                builder.append(sep);
            }
            builder.append(String.format("%02X", Integer.valueOf(data[i] & 255)));
        }
        if (limit < len) {
            builder.append(" ... +").append(len - limit).append(" bytes");
        }
        return builder.toString();
    }
}