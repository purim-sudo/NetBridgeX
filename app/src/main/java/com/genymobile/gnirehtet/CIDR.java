[Reading 90 lines from start (total: 90 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.net.IpPrefix;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import java.net.InetAddress;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: classes2.dex */
public class CIDR implements Parcelable {
    public static final Parcelable.Creator<CIDR> CREATOR = new Parcelable.Creator<CIDR>() { // from class: com.genymobile.gnirehtet.CIDR.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CIDR createFromParcel(Parcel source) {
            return new CIDR(source);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CIDR[] newArray(int size) {
            return new CIDR[size];
        }
    };
    private InetAddress address;
    private int prefixLength;

    public CIDR(InetAddress address, int prefixLength) {
        this.address = address;
        this.prefixLength = prefixLength;
    }

    private CIDR(Parcel source) {
        try {
            this.address = InetAddress.getByAddress(source.createByteArray());
            this.prefixLength = source.readInt();
        } catch (UnknownHostException e) {
            throw new AssertionError("Invalid address", e);
        }
    }

    public static CIDR parse(String cidr) throws InvalidCIDRException {
        InetAddress address;
        int prefix;
        int slashIndex = cidr.indexOf(47);
        try {
            if (slashIndex != -1) {
                address = Net.toInetAddress(cidr.substring(0, slashIndex));
                prefix = Integer.parseInt(cidr.substring(slashIndex + 1));
            } else {
                address = Net.toInetAddress(cidr);
                prefix = 32;
            }
            return new CIDR(address, prefix);
        } catch (IllegalArgumentException e) {
            Log.e("Error", e.getMessage(), e);
            throw new InvalidCIDRException(cidr, e);
        } catch (Throwable e2) {
            Log.e("Error", e2.getMessage(), e2);
            throw e2;
        }
    }

    public InetAddress getAddress() {
        return this.address;
    }

    public int getPrefixLength() {
        return this.prefixLength;
    }

    public IpPrefix getIpPrefix() {
        return new IpPrefix(this.address, this.prefixLength);
    }

    public String toString() {
        return this.address.getHostAddress() + "/" + this.prefixLength;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(this.address.getAddress());
        dest.writeInt(this.prefixLength);
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]