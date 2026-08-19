/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packetbuilder;

import java.util.Random;
import org.rscdaemon.ls.LoginEngine;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.packetbuilder.StaticPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class LSPacketBuilder
extends StaticPacketBuilder {
    private int pID = 0;
    private long uID = 0L;
    private static Random rand = new Random();

    public LSPacketBuilder setID(int pID) {
        this.pID = pID;
        return this;
    }

    public LSPacketBuilder setUID(long uID) {
        this.uID = uID;
        return this;
    }

    public LSPacketBuilder setHandler(LoginEngine engine, PacketHandler handler) {
        this.uID = rand.nextLong();
        engine.setHandler(this.uID, handler);
        return this;
    }

    public LSPacket toPacket() {
        byte[] data = new byte[this.curLength];
        System.arraycopy(this.payload, 0, data, 0, this.curLength);
        return new LSPacket(null, this.pID, this.uID, data, this.bare);
    }
}

