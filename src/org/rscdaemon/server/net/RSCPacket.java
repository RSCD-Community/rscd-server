/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.net.Packet;

public final class RSCPacket
extends Packet {
    private int pID;

    public RSCPacket(Connection session, int pID, byte[] pData, boolean bare) {
        super(session, pData, bare);
        this.pID = pID;
    }

    public RSCPacket(Connection session, int pID, byte[] pData) {
        this(session, pID, pData, false);
    }

    public int getID() {
        return this.pID;
    }

    public String toString() {
        return super.toString() + " pid = " + this.pID;
    }
}

