/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.Collection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.StatefulEntityCollection;

public class PlayerPositionPacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        StatefulEntityCollection<Player> watchedPlayers = this.playerToUpdate.getWatchedPlayers();
        Collection<Player> newPlayers = watchedPlayers.getNewEntities();
        Collection<Player> knownPlayers = watchedPlayers.getKnownEntities();
        RSCPacketBuilder packet = new RSCPacketBuilder();
        packet.setID(145);
        packet.addBits(this.playerToUpdate.getX(), 11);
        packet.addBits(this.playerToUpdate.getY(), 13);
        packet.addBits(this.playerToUpdate.getSprite(), 4);
        packet.addBits(knownPlayers.size(), 8);
        for (Player p : knownPlayers) {
            if (this.playerToUpdate.getIndex() == p.getIndex()) continue;
            packet.addBits(p.getIndex(), 16);
            if (watchedPlayers.isRemoving(p)) {
                packet.addBits(1, 1);
                packet.addBits(1, 1);
                packet.addBits(12, 4);
                continue;
            }
            if (p.hasMoved()) {
                packet.addBits(1, 1);
                packet.addBits(0, 1);
                packet.addBits(p.getSprite(), 3);
                continue;
            }
            if (p.spriteChanged()) {
                packet.addBits(1, 1);
                packet.addBits(1, 1);
                packet.addBits(p.getSprite(), 4);
                continue;
            }
            packet.addBits(0, 1);
        }
        for (Player p : newPlayers) {
            byte[] offsets = DataConversions.getMobPositionOffsets(p.getLocation(), this.playerToUpdate.getLocation());
            packet.addBits(p.getIndex(), 16);
            packet.addBits(offsets[0], 5);
            packet.addBits(offsets[1], 5);
            packet.addBits(p.getSprite(), 4);
            packet.addBits(0, 1);
        }
        return packet.toPacket();
    }
}

