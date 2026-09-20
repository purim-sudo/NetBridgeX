[Reading 24 lines from start (total: 24 lines, 0 remaining)]

package com.genymobile.gnirehtet;

/* JADX INFO: loaded from: classes2.dex */
public class InvalidCIDRException extends Exception {
    private String cidr;

    private static String createMessage(String cidr) {
        return "Invalid CIDR:" + cidr;
    }

    public InvalidCIDRException(String cidr, Throwable cause) {
        super(createMessage(cidr), cause);
        this.cidr = cidr;
    }

    public InvalidCIDRException(String cidr) {
        super(createMessage(cidr));
        this.cidr = cidr;
    }

    public String getCIDR() {
        return this.cidr;
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]