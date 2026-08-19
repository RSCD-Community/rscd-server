/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packetbuilder.loginserver;

import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.packetbuilder.LSPacketBuilder;

public class WorldRegisteredPacketBuilder {
    private long uID;
    private boolean success;

    public void setUID(long uID) {
        this.uID = uID;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LSPacket getPacket() {
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setUID(this.uID);
        packet.addByte((byte)(this.success ? 1 : 0));
        return packet.toPacket();
    }
}

