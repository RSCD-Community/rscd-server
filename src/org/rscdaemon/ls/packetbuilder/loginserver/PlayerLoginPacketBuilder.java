/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packetbuilder.loginserver;

import org.rscdaemon.server.util.sql.Rows;
import org.rscdaemon.server.util.sql.MysqlException;
import java.util.ArrayList;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.BankItem;
import org.rscdaemon.ls.model.InvItem;
import org.rscdaemon.ls.model.PlayerSave;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.packetbuilder.LSPacketBuilder;
import org.rscdaemon.server.util.DataConversions;

/**
 * The login server's answer to a world's "this player wants in": the login
 * result code, and -- when the code says yes -- the entire character in one
 * packet, in the exact field order PlayerLogin on the world reads it back.
 * Change either side and the other must move in lockstep; a drifted field
 * here corrupts every field after it.
 *
 * The friends section also settles online visibility at login: a friend
 * reads as online (their world id) only if they would see YOU -- either
 * their privacy is open or they have added you back -- so block-private is
 * enforced at the source rather than trusted to the client.
 */
public class PlayerLoginPacketBuilder {
    private PlayerSave save;
    private byte loginCode;
    private long uID;

    public void setUID(long uID) {
        this.uID = uID;
    }

    public void setPlayer(PlayerSave save, byte loginCode) {
        this.save = save;
        this.loginCode = loginCode;
    }

    public LSPacket getPacket() {
        Server server = Server.getServer();
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setUID(this.uID);
        packet.addByte(this.loginCode);
        if (this.save != null) {
            packet.addInt(this.save.getOwner());
            packet.addInt(this.save.getGroup());
            packet.addLong(this.save.getSubscriptionExpires());
            packet.addLong(this.save.getLastIP());
            packet.addLong(this.save.getLastLogin());
            packet.addShort(this.save.getX());
            packet.addShort(this.save.getY());
            packet.addShort(this.save.getFatigue());
            packet.addByte(this.save.getCombatStyle());
            packet.addByte((byte)(this.save.blockChat() ? 1 : 0));
            packet.addByte((byte)(this.save.blockPrivate() ? 1 : 0));
            packet.addByte((byte)(this.save.blockTrade() ? 1 : 0));
            packet.addByte((byte)(this.save.blockDuel() ? 1 : 0));
            packet.addByte((byte)(this.save.cameraAuto() ? 1 : 0));
            packet.addByte((byte)(this.save.oneMouse() ? 1 : 0));
            packet.addByte((byte)(this.save.soundOff() ? 1 : 0));
            packet.addByte((byte)(this.save.showRoof() ? 1 : 0));
            packet.addByte((byte)(this.save.autoScreenshot() ? 1 : 0));
            packet.addByte((byte)(this.save.combatWindow() ? 1 : 0));
            packet.addShort(this.save.getHairColour());
            packet.addShort(this.save.getTopColour());
            packet.addShort(this.save.getTrouserColour());
            packet.addShort(this.save.getSkinColour());
            packet.addShort(this.save.getHeadSprite());
            packet.addShort(this.save.getBodySprite());
            packet.addByte((byte)(this.save.isMale() ? 1 : 0));
            packet.addLong(this.save.getSkullTime());
            for (int i = 0; i < 19; ++i) {
                packet.addLong(this.save.getExp(i));
                packet.addShort(this.save.getStat(i));
            }
            int invCount = this.save.getInvCount();
            packet.addShort(invCount);
            for (int i = 0; i < invCount; ++i) {
                InvItem item = this.save.getInvItem(i);
                packet.addShort(item.getID());
                packet.addInt(item.getAmount());
                packet.addByte((byte)(item.isWielded() ? 1 : 0));
            }
            int bankCount = this.save.getBankCount();
            packet.addShort(bankCount);
            for (int i = 0; i < bankCount; ++i) {
                BankItem item = this.save.getBankItem(i);
                packet.addShort(item.getID());
                packet.addInt(item.getAmount());
            }
            ArrayList<Long> friendsWithUs = new ArrayList<Long>();
            try {
                Rows result = Server.db.getQuery("SELECT p.user FROM `rscd_friends` AS f INNER JOIN `rscd_players` AS p ON p.user=f.friend WHERE p.block_private=0 AND f.user=" + this.save.getUser());
                while (result.next()) {
                    friendsWithUs.add(result.getLong("user"));
                }
                result = Server.db.getQuery("SELECT user FROM `rscd_friends` WHERE friend=" + this.save.getUser());
                while (result.next()) {
                    friendsWithUs.add(result.getLong("user"));
                }
            }
            catch (MysqlException e) {
                Server.error(e);
            }
            int friendCount = this.save.getFriendCount();
            packet.addShort(friendCount);
            for (int i = 0; i < friendCount; ++i) {
                long friend = this.save.getFriend(i);
                World world = server.findWorld(friend);
                packet.addLong(friend);
                packet.addShort(world == null || !friendsWithUs.contains(friend) ? 0 : world.getID());
            }
            int ignoreCount = this.save.getIgnoreCount();
            packet.addShort(ignoreCount);
            for (int i = 0; i < ignoreCount; ++i) {
                packet.addLong(this.save.getIgnore(i));
            }
            java.util.Map<Integer, Integer> quests = this.save.getQuestStages();
            packet.addShort(quests.size());
            for (java.util.Map.Entry<Integer, Integer> q : quests.entrySet()) {
                packet.addShort(q.getKey());
                packet.addInt(q.getValue());
            }
            packet.addByte((byte)this.save.getAmuletCharges());
            packet.addByte((byte)this.save.getPoisonStrength());
            packet.addByte((byte)this.save.getPoisonHits());
        }
        return packet.toPacket();
    }
}

