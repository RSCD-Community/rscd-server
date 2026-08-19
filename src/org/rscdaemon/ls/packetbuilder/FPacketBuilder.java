/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packetbuilder;

import org.rscdaemon.ls.net.FPacket;
import org.rscdaemon.ls.packetbuilder.StaticPacketBuilder;

public class FPacketBuilder
extends StaticPacketBuilder {
    private int pID = 0;
    private String[] parameters = new String[0];

    public FPacketBuilder setID(int pID) {
        this.pID = pID;
        return this;
    }

    public FPacketBuilder setParameters(String[] parameters) {
        this.parameters = parameters;
        return this;
    }

    public FPacket toPacket() {
        return new FPacket(null, this.pID, this.parameters, this.bare);
    }
}

