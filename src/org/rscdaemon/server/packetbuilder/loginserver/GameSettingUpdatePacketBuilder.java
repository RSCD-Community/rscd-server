/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;

public class GameSettingUpdatePacketBuilder {
    private Player player;
    private int index;
    private boolean on;

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public LSPacket getPacket() {
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setID(7);
        packet.addLong(this.player.getUsernameHash());
        packet.addByte((byte)(this.on ? 1 : 0));
        packet.addByte((byte)this.index);
        return packet.toPacket();
    }
}

