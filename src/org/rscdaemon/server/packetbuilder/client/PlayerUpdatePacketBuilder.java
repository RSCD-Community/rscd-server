/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.List;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.PlayerAppearance;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;

public class PlayerUpdatePacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        List<Bubble> bubblesNeedingDisplayed = this.playerToUpdate.getBubblesNeedingDisplayed();
        List<ChatMessage> chatMessagesNeedingDisplayed = this.playerToUpdate.getChatMessagesNeedingDisplayed();
        List<Player> playersNeedingHitsUpdate = this.playerToUpdate.getPlayersRequiringHitsUpdate();
        List<Projectile> projectilesNeedingDisplayed = this.playerToUpdate.getProjectilesNeedingDisplayed();
        List<Player> playersNeedingAppearanceUpdate = this.playerToUpdate.getPlayersRequiringAppearanceUpdate();
        int updateSize = bubblesNeedingDisplayed.size() + chatMessagesNeedingDisplayed.size() + playersNeedingHitsUpdate.size() + projectilesNeedingDisplayed.size() + playersNeedingAppearanceUpdate.size();
        if (updateSize > 0) {
            RSCPacketBuilder updates = new RSCPacketBuilder();
            updates.setID(53);
            updates.addShort(updateSize);
            for (Bubble bubble : bubblesNeedingDisplayed) {
                updates.addShort(bubble.getOwner().getIndex());
                updates.addByte((byte)0);
                updates.addShort(bubble.getID());
            }
            for (ChatMessage chatMessage : chatMessagesNeedingDisplayed) {
                updates.addShort(chatMessage.getSender().getIndex());
                updates.addByte((byte)(chatMessage.getRecipient() == null ? 1 : 6));
                updates.addByte((byte)chatMessage.getLength());
                updates.addBytes(chatMessage.getMessage());
            }
            for (Player player : playersNeedingHitsUpdate) {
                updates.addShort(player.getIndex());
                updates.addByte((byte)2);
                updates.addByte((byte)player.getLastDamage());
                updates.addByte((byte)player.getCurStat(3));
                updates.addByte((byte)player.getMaxStat(3));
            }
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
            for (Player player : playersNeedingAppearanceUpdate) {
                PlayerAppearance appearance = player.getPlayerAppearance();
                updates.addShort(player.getIndex());
                updates.addByte((byte)5);
                updates.addShort(player.getAppearanceID());
                updates.addLong(player.getUsernameHash());
                updates.addByte((byte)player.getWornItems().length);
                for (int i : player.getWornItems()) {
                    // Two bytes per worn slot, not one: the value is an AnimationDef
                    // array index + 1, and the table outgrew a byte the day the first
                    // appended worn look (kiteshield) landed past index 254. The
                    // client's read side widened in the same change.
                    updates.addShort(i);
                }
                updates.addByte(appearance.getHairColour());
                updates.addByte(appearance.getTopColour());
                updates.addByte(appearance.getTrouserColour());
                updates.addByte(appearance.getSkinColour());
                updates.addByte((byte)player.getCombatLevel());
                updates.addByte((byte)(player.isSkulled() ? 1 : 0));
                updates.addByte((byte)(player.isAdmin() ? 3 : (player.isMod() ? 2 : (player.isPMod() ? 1 : 0))));
            }
            return updates.toPacket();
        }
        return null;
    }
}

