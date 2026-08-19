/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.net.Packet;

public final class LSPacket
extends Packet {
    private int pID;
    private long uID;

    public LSPacket(Connection session, int pID, long uID, byte[] pData, boolean bare) {
        super(session, pData, bare);
        this.pID = pID;
        this.uID = uID;
    }

    public LSPacket(Connection session, int pID, long uID, byte[] pData) {
        this(session, pID, uID, pData, false);
    }

    public int getID() {
        return this.pID;
    }

    public long getUID() {
        return this.uID;
    }

    public String toString() {
        return super.toString() + " pid = " + this.pID + " uid = " + this.uID;
    }
}

