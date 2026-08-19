/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.Formulae;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class MiscPacketBuilder {
    private Player player;
    private List<RSCPacket> packets = new ArrayList<RSCPacket>();

    public MiscPacketBuilder(Player player) {
        this.player = player;
    }

    public List<RSCPacket> getPackets() {
        return this.packets;
    }

    public void clearPackets() {
        this.packets.clear();
    }

    public void sendScreenshot() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(181);
        this.packets.add(s.toPacket());
    }

    public void sendCombatStyle() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(129);
        s.addByte((byte)this.player.getCombatStyle());
        this.packets.add(s.toPacket());
    }

    public void sendFatigue() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(126);
        s.addShort(this.player.getFatigue());
        this.packets.add(s.toPacket());
    }

    public void hideMenu() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(127);
        this.packets.add(s.toPacket());
    }

    public void sendMenu(String[] options) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(223);
        s.addByte((byte)options.length);
        for (String option : options) {
            s.addByte((byte)option.length());
            s.addBytes(option.getBytes());
        }
        this.packets.add(s.toPacket());
    }

    public void showBank() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(93);
        s.addByte((byte)this.player.getBank().size());
        s.addByte((byte)-64);
        for (InvItem i : this.player.getBank().getItems()) {
            s.addShort(i.getID());
            s.addInt(i.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void hideBank() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(171);
        this.packets.add(s.toPacket());
    }

    public void updateBankItem(int slot, int newId, int amount) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(139);
        s.addByte((byte)slot);
        s.addShort(newId);
        s.addInt(amount);
        this.packets.add(s.toPacket());
    }

    public void showShop(Shop shop) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(253);
        s.addByte((byte)shop.size());
        s.addByte((byte)(shop.isGeneral() ? 1 : 0));
        s.addByte((byte)shop.getSellModifier());
        s.addByte((byte)shop.getBuyModifier());
        for (InvItem i : shop.getItems()) {
            s.addShort(i.getID());
            s.addShort(i.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void hideShop() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(220);
        this.packets.add(s.toPacket());
    }

    public void startShutdown(int seconds) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(172);
        s.addShort((int)((double)seconds / 32.0 * 50.0));
        this.packets.add(s.toPacket());
    }

    public void sendAlert(String message, boolean big) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(big ? 64 : 148);
        s.addBytes(message.getBytes());
        this.packets.add(s.toPacket());
    }

    public void sendSound(String soundName) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(11);
        s.addBytes(soundName.getBytes());
        this.packets.add(s.toPacket());
    }

    public void sendDied() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(165);
        this.packets.add(s.toPacket());
    }

    public void sendPrivateMessage(long usernameHash, byte[] message) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(170);
        s.addLong(usernameHash);
        s.addBytes(message);
        this.packets.add(s.toPacket());
    }

    public void sendFriendUpdate(long usernameHash, int world) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(25);
        s.addLong(usernameHash);
        s.addByte((byte)(world == Config.SERVER_NUM ? 99 : world));
        this.packets.add(s.toPacket());
    }

    public void sendFriendList() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(249);
        s.addByte((byte)this.player.getFriendList().size());
        for (Map.Entry<Long, Integer> friend : this.player.getFriendList()) {
            int world = friend.getValue();
            s.addLong(friend.getKey());
            s.addByte((byte)(world == Config.SERVER_NUM ? 99 : world));
        }
        this.packets.add(s.toPacket());
    }

    public void sendIgnoreList() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(2);
        s.addByte((byte)this.player.getIgnoreList().size());
        for (Long usernameHash : this.player.getIgnoreList()) {
            s.addLong(usernameHash);
        }
        this.packets.add(s.toPacket());
    }

    public void sendTradeAccept() {
        Player with = this.player.getWishToTrade();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(251);
        s.addLong(with.getUsernameHash());
        s.addByte((byte)with.getTradeOffer().size());
        for (InvItem item : with.getTradeOffer()) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        s.addByte((byte)this.player.getTradeOffer().size());
        for (InvItem item : this.player.getTradeOffer()) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void sendDuelAccept() {
        Player with = this.player.getWishToDuel();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(147);
        s.addLong(with.getUsernameHash());
        s.addByte((byte)with.getDuelOffer().size());
        for (InvItem item : with.getDuelOffer()) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        s.addByte((byte)this.player.getDuelOffer().size());
        for (InvItem item : this.player.getDuelOffer()) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        s.addByte((byte)(this.player.getDuelSetting(0) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(1) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(2) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(3) ? 1 : 0));
        this.packets.add(s.toPacket());
    }

    public void sendTradeAcceptUpdate() {
        Player with = this.player.getWishToTrade();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s1 = new RSCPacketBuilder();
        s1.setID(18);
        s1.addByte((byte)(this.player.isTradeOfferAccepted() ? 1 : 0));
        this.packets.add(s1.toPacket());
        RSCPacketBuilder s2 = new RSCPacketBuilder();
        s2.setID(92);
        s2.addByte((byte)(with.isTradeOfferAccepted() ? 1 : 0));
        this.packets.add(s2.toPacket());
    }

    public void sendDuelAcceptUpdate() {
        Player with = this.player.getWishToDuel();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s1 = new RSCPacketBuilder();
        s1.setID(97);
        s1.addByte((byte)(this.player.isDuelOfferAccepted() ? 1 : 0));
        this.packets.add(s1.toPacket());
        RSCPacketBuilder s2 = new RSCPacketBuilder();
        s2.setID(65);
        s2.addByte((byte)(with.isDuelOfferAccepted() ? 1 : 0));
        this.packets.add(s2.toPacket());
    }

    public void sendDuelSettingUpdate() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(198);
        s.addByte((byte)(this.player.getDuelSetting(0) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(1) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(2) ? 1 : 0));
        s.addByte((byte)(this.player.getDuelSetting(3) ? 1 : 0));
        this.packets.add(s.toPacket());
    }

    public void sendTradeItems() {
        Player with = this.player.getWishToTrade();
        if (with == null) {
            return;
        }
        ArrayList<InvItem> items = with.getTradeOffer();
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(250);
        s.addByte((byte)items.size());
        for (InvItem item : items) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void sendDuelItems() {
        Player with = this.player.getWishToDuel();
        if (with == null) {
            return;
        }
        ArrayList<InvItem> items = with.getDuelOffer();
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(63);
        s.addByte((byte)items.size());
        for (InvItem item : items) {
            s.addShort(item.getID());
            s.addInt(item.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void sendTradeWindowOpen() {
        Player with = this.player.getWishToTrade();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(4);
        s.addShort(with.getIndex());
        this.packets.add(s.toPacket());
    }

    public void sendDuelWindowOpen() {
        Player with = this.player.getWishToDuel();
        if (with == null) {
            return;
        }
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(229);
        s.addShort(with.getIndex());
        this.packets.add(s.toPacket());
    }

    public void sendTradeWindowClose() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(187);
        this.packets.add(s.toPacket());
    }

    public void sendDuelWindowClose() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(160);
        this.packets.add(s.toPacket());
    }

    public void sendAppearanceScreen() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(207);
        this.packets.add(s.toPacket());
    }

    public void sendServerInfo() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(110);
        s.addLong(Config.START_TIME);
        s.addBytes(Config.SERVER_LOCATION.getBytes());
        this.packets.add(s.toPacket());
    }

    /*
     * GHOST GROUND ITEMS, the reported one this time: packet 115 clears
     * every ground item, object and door in the addressed 8x8 blocks, and
     * the client has handled it all along -- the server just never sent it.
     *
     * It exists because the ordinary removal entries in packets 109/27/23
     * address tiles as one signed byte relative to the player. That works
     * while the player walks, but a teleport (death respawn included) moves
     * them hundreds of tiles in one tick, the next revalidate pass then
     * emits removals whose offsets wrap, and the client keeps the pile:
     * still there when the player walks back, minutes after the server
     * despawned it. This packet's offsets are two bytes, so it can address
     * the blocks the player just left from wherever they land.
     *
     * Must be queued BEFORE the location changes: it goes out ahead of the
     * tick's position packet, so the client resolves the offsets against
     * where it still thinks it is standing -- which is where the doomed
     * entities are. Purges every block the watch range (+/-16) can touch;
     * anything the client knows is inside it. The matching server-side
     * forget is the caller's job (Player.setLocation).
     */
    public void sendRegionPurge() {
        int x = this.player.getX();
        int y = this.player.getY();
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(115);
        for (int bx = x - 16 >> 3; bx <= x + 16 >> 3; ++bx) {
            for (int by = y - 16 >> 3; by <= y + 16 >> 3; ++by) {
                s.addShort((bx << 3) - x);
                s.addShort((by << 3) - y);
            }
        }
        this.packets.add(s.toPacket());
    }

    public void sendTeleBubble(int x, int y, boolean grab) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(23);
        s.addByte((byte)(grab ? 1 : 0));
        s.addByte((byte)(x - this.player.getX()));
        s.addByte((byte)(y - this.player.getY()));
        this.packets.add(s.toPacket());
    }

    public void sendMessage(String message) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(48);
        s.addBytes(message.getBytes());
        this.packets.add(s.toPacket());
    }

    public void sendRemoveItem(int slot) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(191);
        s.addByte((byte)slot);
        this.packets.add(s.toPacket());
    }

    public void sendUpdateItem(int slot) {
        InvItem item = this.player.getInventory().get(slot);
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(228);
        s.addByte((byte)slot);
        s.addShort(item.getID() + (item.isWielded() ? 32768 : 0));
        if (item.getDef().isStackable()) {
            s.addInt(item.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void sendInventory() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(114);
        s.addByte((byte)this.player.getInventory().size());
        for (InvItem item : this.player.getInventory().getItems()) {
            s.addShort(item.getID() + (item.isWielded() ? 32768 : 0));
            if (!item.getDef().isStackable()) continue;
            s.addInt(item.getAmount());
        }
        this.packets.add(s.toPacket());
    }

    public void sendEquipmentStats() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(177);
        s.addShort(this.player.getArmourPoints());
        s.addShort(this.player.getWeaponAimPoints());
        s.addShort(this.player.getWeaponPowerPoints());
        s.addShort(this.player.getMagicPoints());
        s.addShort(this.player.getPrayerPoints());
        s.addShort(this.player.getRangePoints());
        this.packets.add(s.toPacket());
    }

    public void sendStat(int stat) {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(208);
        s.addByte((byte)stat);
        s.addByte((byte)this.player.getCurStat(stat));
        s.addByte((byte)this.player.getMaxStat(stat));
        // Stored in quarter-units; the client is shown whole experience.
        s.addInt(this.player.getExp(stat) / 4);
        this.packets.add(s.toPacket());
    }

    public void sendStats() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(180);
        for (int lvl : this.player.getCurStats()) {
            s.addByte((byte)lvl);
        }
        for (int lvl : this.player.getMaxStats()) {
            s.addByte((byte)lvl);
        }
        for (int exp : this.player.getExps()) {
            // Stored in quarter-units; the client is shown whole experience.
            s.addInt(exp / 4);
        }
        // Jagex put the quest point total on the end of this packet. The client
        // reads it only if the packet is long enough (mudclient.java:8104), so
        // sending it is backwards-compatible with an older client.
        s.addByte((byte)this.player.getQuestPoints());
        this.packets.add(s.toPacket());
    }

    /**
     * The quest tab: one byte per quest, in the client's QUEST_NAMES order, 1 for
     * completed. Anything the server does not know about stays 0, which is what
     * the client draws in red.
     */
    /*
     * One byte per quest: 0 not started, 1 started, 2 complete. Used to be a
     * plain 0/1 completion flag -- the middle value is new, an addition to
     * this client's own quest tab rather than a change to RSC's, which only
     * ever drew red/green. See QuestManager.fillProgress.
     */
    public void sendQuests() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(5);
        byte[] progress = this.player.getQuestProgress();
        for (byte state : progress) {
            s.addByte(state);
        }
        this.packets.add(s.toPacket());
    }

    public void sendWorldInfo() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(131);
        s.addShort(this.player.getIndex());
        s.addShort(2304);
        s.addShort(1776);
        s.addShort(Formulae.getHeight(this.player.getLocation()));
        s.addShort(944);
        this.packets.add(s.toPacket());
    }

    public void sendPrayers() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(209);
        for (int x = 0; x < org.rscdaemon.server.entityhandling.EntityHandler.prayerCount(); ++x) {
            s.addByte((byte)(this.player.isPrayerActivated(x) ? 1 : 0));
        }
        this.packets.add(s.toPacket());
    }

    public void sendGameSettings() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(152);
        s.addByte((byte)(this.player.getGameSetting(0) ? 1 : 0));
        s.addByte((byte)(this.player.getGameSetting(2) ? 1 : 0));
        s.addByte((byte)(this.player.getGameSetting(3) ? 1 : 0));
        s.addByte((byte)(this.player.getGameSetting(4) ? 1 : 0));
        s.addByte((byte)(this.player.getGameSetting(5) ? 1 : 0));
        s.addByte((byte)(this.player.getGameSetting(6) ? 1 : 0));
        this.packets.add(s.toPacket());
    }

    public void sendPrivacySettings() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(158);
        s.addByte((byte)(this.player.getPrivacySetting(0) ? 1 : 0));
        s.addByte((byte)(this.player.getPrivacySetting(1) ? 1 : 0));
        s.addByte((byte)(this.player.getPrivacySetting(2) ? 1 : 0));
        s.addByte((byte)(this.player.getPrivacySetting(3) ? 1 : 0));
        this.packets.add(s.toPacket());
    }

    public RSCPacket sendLogout() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(222);
        RSCPacket packet = s.toPacket();
        this.packets.add(packet);
        return packet;
    }

    public void sendCantLogout() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(136);
        this.packets.add(s.toPacket());
    }

    public void sendLoginBox() {
        RSCPacketBuilder s = new RSCPacketBuilder();
        s.setID(248);
        s.addShort(this.player.getDaysSinceLastLogin());
        s.addLong(0L);
        s.addBytes(this.player.getLastIP().getBytes());
        this.packets.add(s.toPacket());
    }
}

