/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.List;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;

public class NpcUpdatePacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        List<Npc> npcsNeedingHitsUpdate = this.playerToUpdate.getNpcsRequiringHitsUpdate();
        List<ChatMessage> npcMessagesNeedingDisplayed = this.playerToUpdate.getNpcMessagesNeedingDisplayed();
        List<Projectile> projectilesNeedingDisplayed = this.playerToUpdate.getNpcProjectilesNeedingDisplayed();
        int updateSize = npcMessagesNeedingDisplayed.size() + npcsNeedingHitsUpdate.size() + projectilesNeedingDisplayed.size();
        if (updateSize > 0) {
            RSCPacketBuilder updates = new RSCPacketBuilder();
            updates.setID(190);
            updates.addShort(updateSize);
            for (ChatMessage cm : npcMessagesNeedingDisplayed) {
                updates.addShort(cm.getSender().getIndex());
                updates.addByte((byte)1);
                updates.addShort(cm.getRecipient().getIndex());
                updates.addByte((byte)cm.getLength());
                updates.addBytes(cm.getMessage());
            }
            for (Npc n : npcsNeedingHitsUpdate) {
                updates.addShort(n.getIndex());
                updates.addByte((byte)2);
                updates.addByte((byte)n.getLastDamage());
                updates.addByte((byte)n.getHits());
                updates.addByte((byte)n.getDef().getHits());
            }
            /*
             * Shots fired by an npc, written exactly the way the player block
             * writes shots fired by a player: caster index, 3 if the thing
             * being shot at is an npc and 4 if it is a player, sprite, victim
             * index. The two blocks share this numbering deliberately -- it
             * is what the client already expects on both sides -- so a
             * projectile only ever changes blocks by changing who fired it.
             */
            for (Projectile projectile : projectilesNeedingDisplayed) {
                Mob victim = projectile.getVictim();
                if (victim instanceof Npc) {
                    updates.addShort(projectile.getCaster().getIndex());
                    updates.addByte((byte)3);
                    updates.addShort(projectile.getType());
                    updates.addShort(((Npc)victim).getIndex());
                    continue;
                }
                if (!(victim instanceof Player)) continue;
                updates.addShort(projectile.getCaster().getIndex());
                updates.addByte((byte)4);
                updates.addShort(projectile.getType());
                updates.addShort(((Player)victim).getIndex());
            }
            return updates.toPacket();
        }
        return null;
    }
}

