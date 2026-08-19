/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;
import org.rscdaemon.server.util.Config;

public class StatRequestPacketBuilder {
    public static final World world = World.getWorld();
    private long uID;

    public void setUID(long uID) {
        this.uID = uID;
    }

    public LSPacket getPacket() {
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setUID(this.uID);
        packet.addInt(world.countPlayers());
        packet.addInt(world.countNpcs());
        packet.addLong(Config.START_TIME);
        return packet.toPacket();
    }
}

