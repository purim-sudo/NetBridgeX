package com.genymobile.gnirehtet;

import java.io.IOException;

/* JADX INFO: loaded from: classes2.dex */
public interface Tunnel {
    void close();

    int receive(byte[] bArr) throws IOException;

    void send(byte[] bArr, int i) throws IOException;
}