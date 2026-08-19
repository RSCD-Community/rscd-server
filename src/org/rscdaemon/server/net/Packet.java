/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.net;

import java.net.InetSocketAddress;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.Logger;

/**
 * An inbound message: the raw bytes plus a read cursor (caret) the typed
 * read methods advance. "Bare" marks the frames that carry no id/length
 * header -- the session-id and login exchanges that happen before the
 * connection speaks the normal protocol; everything after them is an
 * RSCPacket, which adds the packet id.
 */
public class Packet {
    protected Connection session;
    protected int pLength;
    protected byte[] pData;
    protected int caret = 0;
    protected boolean bare;
    protected long time;

    public Packet(Connection session, byte[] pData, boolean bare) {
        this.session = session;
        this.pData = pData;
        this.pLength = pData.length;
        this.bare = bare;
        this.time = System.currentTimeMillis();
    }

    public Packet(Connection session, byte[] pData) {
        this(session, pData, false);
    }

    public Connection getSession() {
        return this.session;
    }

    public boolean isBare() {
        return this.bare;
    }

    public long getCreated() {
        return this.time;
    }

    public int getLength() {
        return this.pLength;
    }

    public byte[] getData() {
        return this.pData;
    }

    public byte[] readBytes(int length) {
        byte[] data = new byte[length];
        try {
            for (int i = 0; i < length; ++i) {
                data[i] = this.pData[i + this.caret];
            }
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
        this.caret += length;
        return data;
    }

    public byte[] getRemainingData() {
        byte[] data = new byte[this.pLength - this.caret];
        for (int i = 0; i < data.length; ++i) {
            data[i] = this.pData[i + this.caret];
        }
        this.caret += data.length;
        return data;
    }

    public byte readByte() {
        return this.pData[this.caret++];
    }

    public short readShort() {
        try {
            return (short)((short)((this.pData[this.caret++] & 0xFF) << 8) | (short)(this.pData[this.caret++] & 0xFF));
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
            return 0;
        }
    }

    public int readInt() {
        try {
            return (this.pData[this.caret++] & 0xFF) << 24 | (this.pData[this.caret++] & 0xFF) << 16 | (this.pData[this.caret++] & 0xFF) << 8 | this.pData[this.caret++] & 0xFF;
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
            return 0;
        }
    }

    public long readLong() {
        try {
            return (long)(this.pData[this.caret++] & 0xFF) << 56 | (long)(this.pData[this.caret++] & 0xFF) << 48 | (long)(this.pData[this.caret++] & 0xFF) << 40 | (long)(this.pData[this.caret++] & 0xFF) << 32 | (long)(this.pData[this.caret++] & 0xFF) << 24 | (long)(this.pData[this.caret++] & 0xFF) << 16 | (long)(this.pData[this.caret++] & 0xFF) << 8 | (long)(this.pData[this.caret++] & 0xFF);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
            return 0L;
        }
    }

    public String readString() {
        return this.readString(this.pLength - this.caret);
    }

    public String readString(int length) {
        String rv = new String(this.pData, this.caret, length);
        this.caret += length;
        return rv;
    }

    public void skip(int x) {
        this.caret += x;
    }

    public int remaining() {
        return this.pData.length - this.caret;
    }

    public String printData() {
        if (this.pLength == 0) {
            return "";
        }
        String data = "";
        for (int i = 0; i < this.pLength; ++i) {
            data = data + " " + this.pData[i];
        }
        return data.substring(1);
    }

    public String toString() {
        String origin = this.session == null ? "this" : ((InetSocketAddress)this.session.getRemoteAddress()).getAddress().getHostAddress();
        return "origin = " + origin + " length = " + this.pLength;
    }
}

