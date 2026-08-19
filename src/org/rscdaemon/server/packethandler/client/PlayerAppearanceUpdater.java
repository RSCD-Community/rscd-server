/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.PlayerAppearance;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.Quests;

public class PlayerAppearanceUpdater
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (!player.isChangingAppearance()) {
            player.setSuspiciousPlayer(true);
            return;
        }
        player.setChangingAppearance(false);
        byte headGender = p.readByte();
        byte headType = p.readByte();
        byte bodyGender = p.readByte();
        p.readByte();
        byte hairColour = p.readByte();
        byte topColour = p.readByte();
        byte trouserColour = p.readByte();
        byte skinColour = p.readByte();
        int headSprite = headType + 1;
        int bodySprite = bodyGender + 1;
        PlayerAppearance appearance = new PlayerAppearance(hairColour, topColour, trouserColour, skinColour, headSprite, bodySprite);
        if (!appearance.isValid()) {
            player.setSuspiciousPlayer(true);
            return;
        }
        player.setMale(headGender == 1);
        if (player.isMale()) {
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < inv.size(); ++slot) {
                InvItem i = inv.get(slot);
                if (!i.isWieldable() || i.getWieldableDef().getWieldPos() != 1 || !i.isWielded() || !i.getWieldableDef().femaleOnly()) continue;
                i.setWield(false);
                player.updateWornItems(i.getWieldableDef().getWieldPos(), player.getPlayerAppearance().getSprite(i.getWieldableDef().getWieldPos()));
                player.getActionSender().sendUpdateItem(slot);
                break;
            }
        }
        int[] oldWorn = player.getWornItems();
        int[] oldAppearance = player.getPlayerAppearance().getSprites();
        player.setAppearance(appearance);
        int[] newAppearance = player.getPlayerAppearance().getSprites();
        for (int i = 0; i < 12; ++i) {
            if (oldWorn[i] != oldAppearance[i]) continue;
            player.updateWornItems(i, newAppearance[i]);
        }
        /*
         * The tutorial's skip prompt. Offered from here rather than from login
         * because the appearance screen is modal on the client: a menu sent
         * while it is still up cannot be seen, and this is the moment it comes
         * down.
         *
         * The quest ignores this unless the account has never started it, so
         * the Make over mage -- who opens the same screen -- cannot re-offer
         * the tutorial to someone who finished it years ago.
         */
        player.getQuestManager().note(Quests.TUTORIAL_ISLAND, "appearance-chosen");
    }
}

