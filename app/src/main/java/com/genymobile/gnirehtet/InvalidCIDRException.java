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