/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import java.util.ArrayList;
import java.util.Map;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.util.Formulae;

public class WieldHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy() && !player.inCombat()) {
            return;
        }
        if (player.isDueling() && player.getDuelSetting(3)) {
            player.getActionSender().sendMessage("@gry@ Armour is disabled in this duel");
            return;
        }
        player.resetAllExceptDueling();
        short idx = p.readShort();
        if (idx < 0 || idx >= 30) {
            player.setSuspiciousPlayer(true);
            return;
        }
        InvItem item = player.getInventory().get(idx);
        if (item == null || !item.isWieldable()) {
            player.setSuspiciousPlayer(true);
            return;
        }
        switch (pID) {
            case 181: {
                if (item.isWielded()) break;
                this.wieldItem(player, item);
                break;
            }
            case 92: {
                if (!item.isWielded()) break;
                this.unWieldItem(player, item, true);
            }
        }
        player.getActionSender().sendInventory();
        player.getActionSender().sendEquipmentStats();
    }

    private void wieldItem(Player player, InvItem item) {
        String youNeed = "";
        for (Map.Entry<Integer, Integer> e : item.getWieldableDef().getStatsRequired()) {
            if (player.getMaxStat(e.getKey()) >= e.getValue()) continue;
            youNeed = youNeed + e.getValue() + " " + Formulae.statArray[e.getKey()] + ", ";
        }
        if (!youNeed.equals("")) {
            player.getActionSender().sendMessage("@gry@ You must have at least " + youNeed.substring(0, youNeed.length() - 2) + " to use this item.");
            return;
        }
        if (!checkQuestLockedWear(player, item.getID())) {
            return;
        }
        if (!checkGodCapeConflict(player, item.getID())) {
            return;
        }
        if (EntityHandler.getItemWieldableDef(item.getID()).femaleOnly() && player.isMale()) {
            player.getActionSender().sendMessage("@pnk@ You have failed to fit into the armour.");
            return;
        }
        ArrayList<InvItem> items = player.getInventory().getItems();
        for (InvItem i : items) {
            if (!item.wieldingAffectsItem(i) || !i.isWielded()) continue;
            this.unWieldItem(player, i, false);
        }
        item.setWield(true);
        player.getActionSender().sendSound("click");
        player.updateWornItems(item.getWieldableDef().getWieldPos(), item.getWieldableDef().getSprite());
    }

    /*
     * Several wieldables are locked behind a quest as well as (or instead of)
     * a level, and WieldHandler had no such check at all before 2026-08-02 --
     * only the stat gate above. That alone let anyone with 60 Attack/Defense
     * wear a Dragon sword, axe or square shield, or anyone at all wear a Cape
     * of legends, with no quest ever done.
     *
     * Dragon medium helmet (795) is a dragon item that is NOT here on purpose
     * -- level 60 Defense only, per classic.runescape.wiki, no quest gate.
     *
     * The two-line message for the square shield is quoted directly off the
     * wiki: "you have not earned the right to wear this yet" followed by which
     * quest is missing. Everything else here reuses that same first line --
     * RSC reused this exact wording across its quest-locked wieldables rather
     * than writing one per item -- with the quest name swapped in; that reuse
     * is inferred for the others, not itself sourced the way the shield's
     * exact text is.
     *
     * Cape of legends (1288) is also meant to be unobtainable without the
     * quest -- the Legends-Guild Shop only sells it after completion -- but
     * that is a separate, not-yet-built gate on the shop counter, not this
     * one. This check exists so that if a cape ever reaches an inventory some
     * other way (trade before the untradeable check existed, an admin grant,
     * a future bug), it still can't be worn without the quest.
     */
    private static boolean checkQuestLockedWear(Player player, int itemId) {
        int quest;
        String questName;

        switch (itemId) {
            case 593:
                quest = Quests.LOST_CITY;
                questName = "Lost City";
                break;
            case 594:
                quest = Quests.HEROS_QUEST;
                questName = "Hero's";
                break;
            case 1278:
            case 1288:
                quest = Quests.LEGENDS_QUEST;
                questName = "Legends'";
                break;
            case 401:
            case 407:
                /*
                 * Rune Plate Mail Body (male, 401) and Rune Plate Mail top
                 * (female, 407) -- the two gendered sprites for the same
                 * armour. Both are sold nowhere except Oziach's shop, which
                 * DragonSlayer.talkToOziach already gates on completed(); this
                 * is the same belt-and-braces reasoning as the cape above, for
                 * the same reason.  Rune Chain Mail Body (400) is a different
                 * item and correctly has no gate at all -- it was never part
                 * of the quest reward.
                 */
                quest = Quests.DRAGON_SLAYER;
                questName = "Dragon Slayer";
                break;
            case 1006:
                /*
                 * Klank's gauntlets are handed over mid-quest, in the dwarf
                 * camp, and are wearable from that moment -- the quest does
                 * not have to be finished. The gate is the stage Klank gives
                 * them at (HAVE_DOLL, 11 -- low five bits are the stage, the
                 * rest is the quest's scratch space).
                 */
                int ugp = player.getQuestStage(Quests.UNDERGROUND_PASS);
                if (ugp != -1 && (ugp & 31) >= 11) {
                    return true;
                }
                quest = Quests.UNDERGROUND_PASS;
                questName = "Underground Pass";
                break;
            case 1000:
                /*
                 * Staff of Iban (1000, repaired -- 1031, the damaged form, is
                 * not wieldable and never reaches this switch), Underground
                 * Pass's end-of-quest reward. A notable PVP item -- flagged
                 * by the user as on par with the Cape of legends for how much
                 * players chase it -- and had no wear-gate at all before this
                 * fix, same class of gap as every other quest-reward
                 * wieldable above.
                 */
                quest = Quests.UNDERGROUND_PASS;
                questName = "Underground Pass";
                break;
            default:
                return true;
        }

        if (player.getQuestManager().completed(quest)) {
            return true;
        }

        player.getActionSender().sendMessage("@gry@ You have not earned the right to wear this yet.");
        player.getActionSender().sendMessage("@gry@ You need to complete the " + questName + " quest.");
        return false;
    }

    /*
     * God capes (Zamorak 1213, Saradomin 1214, Guthix 1215) may only be
     * possessed one at a time in real RSC -- praying at a different statue in
     * the Mage Arena requires dropping the one you're currently holding
     * first, there is no direct swap. God staves (1216-1218) have no such
     * rule -- a player can legitimately own and carry all three, and the
     * normal single-weapon-slot wield-swap already limits them to wielding
     * one at a time, so they are not checked here.
     */
    private static final int[] GOD_CAPES = {1213, 1214, 1215};

    private static boolean isGodCape(int itemId) {
        for (int id : GOD_CAPES) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkGodCapeConflict(Player player, int itemId) {
        if (!isGodCape(itemId)) {
            return true;
        }
        for (InvItem i : player.getInventory().getItems()) {
            if (i.getID() == itemId || !isGodCape(i.getID())) {
                continue;
            }
            player.getActionSender().sendMessage("@gry@ You must drop your " + EntityHandler.getItemDef(i.getID()).getName() + " before wearing a different god's cape.");
            return false;
        }
        return true;
    }

    private void unWieldItem(Player player, InvItem item, boolean sound) {
        item.setWield(false);
        if (sound) {
            player.getActionSender().sendSound("click");
        }
        player.updateWornItems(item.getWieldableDef().getWieldPos(), player.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
    }
}

