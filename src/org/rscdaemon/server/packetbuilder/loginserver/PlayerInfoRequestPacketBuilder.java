/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;

public class PlayerInfoRequestPacketBuilder {
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
        if (this.player == null) {
            packet.addByte((byte)0);
        } else {
            packet.addByte((byte)1);
            packet.addShort(this.player.getX());
            packet.addShort(this.player.getY());
            packet.addLong(this.player.getCurrentLogin());
            packet.addLong(this.player.getLastMoved());
            packet.addByte((byte)(this.player.getPrivacySetting(0) ? 1 : 0));
            packet.addShort(this.player.getFatigue());
            packet.addBytes(this.player.getStatus().toString().getBytes());
        }
        return packet.toPacket();
    }
}

