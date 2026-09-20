[Reading 12 lines from start (total: 12 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import java.io.IOException;

/* JADX INFO: loaded from: classes2.dex */
public interface Tunnel {
    void close();

    int receive(byte[] bArr) throws IOException;

    void send(byte[] bArr, int i) throws IOException;
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]