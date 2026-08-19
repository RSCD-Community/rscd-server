/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;

public class ReportInfoRequestPacketBuilder {
    public static final World world = World.getWorld();
    private long uID;
    private Player player;

    public void setUID(long uID) {
        this.uID = uID;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public LSPacket getPacket() {
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setUID(this.uID);
        packet.addShort(this.player.getX());
        packet.addShort(this.player.getY());
        packet.addBytes(this.player.getStatus().toString().getBytes());
        return packet.toPacket();
    }
}

