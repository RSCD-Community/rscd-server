/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder;

import org.rscdaemon.server.LoginConnector;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.StaticPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.DataConversions;

public class LSPacketBuilder
extends StaticPacketBuilder {
    private int pID = 0;
    private long uID = 0L;

    public LSPacketBuilder setID(int pID) {
        this.pID = pID;
        return this;
    }

    public LSPacketBuilder setUID(long uID) {
        this.uID = uID;
        return this;
    }

    public LSPacketBuilder setHandler(LoginConnector connector, PacketHandler handler) {
        this.uID = DataConversions.getRandom().nextLong();
        connector.setHandler(this.uID, handler);
        return this;
    }

    public LSPacket toPacket() {
        byte[] data = new byte[this.curLength];
        System.arraycopy(this.payload, 0, data, 0, this.curLength);
        return new LSPacket(null, this.pID, this.uID, data, this.bare);
    }
}

