/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.Collection;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.StatefulEntityCollection;

public class NpcPositionPacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        StatefulEntityCollection<Npc> watchedNpcs = this.playerToUpdate.getWatchedNpcs();
        Collection<Npc> newNpcs = watchedNpcs.getNewEntities();
        Collection<Npc> knownNpcs = watchedNpcs.getKnownEntities();
        RSCPacketBuilder packet = new RSCPacketBuilder();
        packet.setID(77);
        packet.addBits(knownNpcs.size(), 8);
        for (Npc n : knownNpcs) {
            packet.addBits(n.getIndex(), 16);
            if (watchedNpcs.isRemoving(n)) {
                packet.addBits(1, 1);
                packet.addBits(1, 1);
                packet.addBits(12, 4);
                continue;
            }
            if (n.hasMoved()) {
                packet.addBits(1, 1);
                packet.addBits(0, 1);
                packet.addBits(n.getSprite(), 3);
                continue;
            }
            if (n.spriteChanged()) {
                packet.addBits(1, 1);
                packet.addBits(1, 1);
                packet.addBits(n.getSprite(), 4);
                continue;
            }
            packet.addBits(0, 1);
        }
        for (Npc n : newNpcs) {
            byte[] offsets = DataConversions.getMobPositionOffsets(n.getLocation(), this.playerToUpdate.getLocation());
            packet.addBits(n.getIndex(), 16);
            packet.addBits(offsets[0], 5);
            packet.addBits(offsets[1], 5);
            packet.addBits(n.getSprite(), 4);
            packet.addBits(n.getID(), 10);
        }
        return packet.toPacket();
    }
}

