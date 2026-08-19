/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder;

import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.StaticPacketBuilder;

public class RSCPacketBuilder
extends StaticPacketBuilder {
    private int pID = 0;

    public RSCPacketBuilder setID(int pID) {
        this.pID = pID;
        return this;
    }

    public RSCPacket toPacket() {
        byte[] data = new byte[this.curLength];
        System.arraycopy(this.payload, 0, data, 0, this.curLength);
        return new RSCPacket(null, this.pID, data, this.bare);
    }
}

