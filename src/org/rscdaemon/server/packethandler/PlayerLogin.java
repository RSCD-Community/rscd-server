/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Bank;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.PlayerAppearance;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.packetbuilder.client.MiscPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class PlayerLogin
implements PacketHandler {
    private static final World world = World.getWorld();
    private Player player;

    public PlayerLogin(Player player) {
        this.player = player;
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        int loginCode = p.readByte();
        RSCPacketBuilder pb = new RSCPacketBuilder();
        pb.setBare(true);
        pb.addByte((byte)loginCode);
        this.player.getSession().write((Object)pb.toPacket());
        if (loginCode == 0 || loginCode == 1 || loginCode == 99) {
            this.player.setOwner(p.readInt());
            this.player.setGroupID(p.readInt());
            this.player.setSubscriptionExpires(p.readLong());
            if (Config.EVERYONE_SUBSCRIBER) {
                this.player.refreshSubscriptionOneYear();
            }
            this.player.setLastIP(DataConversions.IPToString(p.readLong()));
            this.player.setLastLogin(p.readLong());
            this.player.setLocation(Point.location(p.readShort(), p.readShort()), true);
            /*
             * A brand new account starts on Tutorial Island, in the guide's
             * room, and not wherever the account row happened to default to.
             *
             * Done here rather than in the database schema on purpose: the
             * column default is one server's business, and every community
             * server running this code should put its new players in the same
             * place. It also has to happen before registerPlayer() below, or
             * the player is briefly registered on the old tile and has to be
             * teleported off it.
             *
             * The old default was (492,3294), which is not a place: it is
             * inside the blocked void, so a new account could not move at all.
             */
            if (this.player.getLastLogin() == 0L) {
                this.player.setLocation(Point.location(219, 744), true);
            }
            this.player.setFatigue(p.readShort());
            this.player.setCombatStyle(p.readByte());
            this.player.setPrivacySetting(0, p.readByte() == 1);
            this.player.setPrivacySetting(1, p.readByte() == 1);
            this.player.setPrivacySetting(2, p.readByte() == 1);
            this.player.setPrivacySetting(3, p.readByte() == 1);
            this.player.setGameSetting(0, p.readByte() == 1);
            this.player.setGameSetting(2, p.readByte() == 1);
            this.player.setGameSetting(3, p.readByte() == 1);
            this.player.setGameSetting(4, p.readByte() == 1);
            this.player.setGameSetting(5, p.readByte() == 1);
            this.player.setGameSetting(6, p.readByte() == 1);
            PlayerAppearance appearance = new PlayerAppearance(p.readShort(), p.readShort(), p.readShort(), p.readShort(), p.readShort(), p.readShort());
            if (!appearance.isValid()) {
                /*
                 * Return, rather than falling through. The response byte has
                 * already gone out and the session is closed, but without this
                 * the method carried on and ran registerPlayer() -- putting a
                 * destroyed player on a closed socket into the world, where it
                 * was visible to everyone else until the next tick reaped it.
                 * The `loginCode = 7` that used to be here was dead: the code
                 * byte is written at the top of this method and never re-read.
                 */
                this.player.destroy(true);
                this.player.getSession().close();
                return;
            }
            this.player.setAppearance(appearance);
            this.player.setWornItems(this.player.getPlayerAppearance().getSprites());
            this.player.setMale(p.readByte() == 1);
            long skull = p.readLong();
            if (skull > 0L) {
                this.player.addSkull(skull);
            }
            for (int i = 0; i < 19; ++i) {
                int exp = (int)p.readLong();
                this.player.setExp(i, exp);
                this.player.setMaxStat(i, Formulae.experienceToLevel(exp));
                this.player.setCurStat(i, p.readShort());
            }
            this.player.setCombatLevel(Formulae.getCombatlevel(this.player.getMaxStats()));
            Inventory inventory = new Inventory(this.player);
            int invCount = p.readShort();
            for (int i = 0; i < invCount; ++i) {
                InvItem item = new InvItem(p.readShort(), p.readInt());
                if (p.readByte() == 1 && item.isWieldable()) {
                    item.setWield(true);
                    this.player.updateWornItems(item.getWieldableDef().getWieldPos(), item.getWieldableDef().getSprite());
                }
                inventory.add(item);
            }
            this.player.setInventory(inventory);
            Bank bank = new Bank();
            int bnkCount = p.readShort();
            for (int i = 0; i < bnkCount; ++i) {
                bank.add(new InvItem(p.readShort(), p.readInt()));
            }
            this.player.setBank(bank);
            int friendCount = p.readShort();
            for (int i = 0; i < friendCount; ++i) {
                this.player.addFriend(p.readLong(), p.readShort());
            }
            int ignoreCount = p.readShort();
            for (int i = 0; i < ignoreCount; ++i) {
                this.player.addIgnore(p.readLong());
            }
            int questCount = p.readShort();
            for (int i = 0; i < questCount; ++i) {
                this.player.setQuestStage(p.readShort(), p.readInt());
            }
            this.player.setAmuletCharges(p.readByte());
            this.player.setPoison(p.readByte(), p.readByte());
            // The Quest objects were built in the Player constructor, before any
            // of this arrived, so every one of them currently thinks it is
            // unstarted. Hand them their saved progress now.
            this.player.getQuestManager().restoreProgress();
            world.registerPlayer(this.player);
            this.player.updateViewedPlayers();
            this.player.updateViewedObjects();
            MiscPacketBuilder sender = this.player.getActionSender();
            sender.sendServerInfo();
            sender.sendFatigue();
            sender.sendWorldInfo();
            sender.sendInventory();
            sender.sendEquipmentStats();
            sender.sendStats();
            sender.sendPrivacySettings();
            sender.sendGameSettings();
            sender.sendFriendList();
            sender.sendIgnoreList();
            sender.sendCombatStyle();
            sender.sendQuests();
            int timeTillShutdown = world.getServer().timeTillShutdown();
            if (timeTillShutdown > -1) {
                sender.startShutdown(timeTillShutdown / 1000);
            }
            if (this.player.getLastLogin() == 0L) {
                this.player.setChangingAppearance(true);
                sender.sendAppearanceScreen();
            }
            /* Poison ticks again from here. Started after the stats have gone
               out, so the first hit lands on a client that already knows what
               the player's hitpoints are. */
            org.rscdaemon.server.model.Poison.resume(this.player);
            sender.sendLoginBox();
            sender.sendMessage("@gre@ Welcome to " + Config.SERVER_NAME + "!");
            /*
             * A 1x multiplier is authentic RSC and not worth announcing --
             * only say something when there is an actual boost to report.
             */
            if (Config.EXP_MULT > 1) {
                sender.sendMessage("@gry@ The experience multiplier is currently set to " + Config.EXP_MULT + "x");
            }
            if (this.player.isSubscriber()) {
                /*
                 * Above 0, not above 1: this figure is added to EXP_MULT
                 * rather than multiplied by it, so 1 is already a doubling and
                 * 0 is the only value that means "no bonus". Tested the wrong
                 * way round, a world running the two settings at 1 and 1 gave
                 * every account 2x and said nothing about it at login.
                 */
                if (Config.SUBSCRIBER_EXP_MULT > 0) {
                    sender.sendMessage("@or1@ Subscribers get " + Config.SUBSCRIBER_EXP_MULT + "x experience on top of that.");
                }
                /*
                 * everyone_subscriber means there is no real subscription
                 * behind this, so a day count -- or an admin's "unlimited",
                 * which is really the same claim -- would be reporting on a
                 * status nobody actually holds. Said once, not per-branch.
                 */
                if (Config.EVERYONE_SUBSCRIBER) {
                    sender.sendMessage("@or1@ This server runs without subscriptions -- everyone gets the bonus.");
                    sender.sendMessage("@or1@ Consider donating or contributing to help keep it running. Enjoy!");
                } else if (this.player.isAdmin()) {
                    sender.sendMessage("@or1@ Administrators have unlimited subscription days.");
                } else {
                    sender.sendMessage("@or1@ You have @red@" + this.player.getSubDays() + " @or1@subscription days remaining.");
                }
            }
            this.player.setLoggedIn(true);
            this.player.setBusy(false);
        } else {
            /*
             * A refused login -- wrong password, already online, banned, full.
             *
             * destroy() only marks the player and queues the logout packet;
             * the socket is closed by ClientUpdater.sendQueuedPackets, which
             * walks world.getPlayers(). This player never got as far as
             * registerPlayer(), so it is not in that list and nothing else
             * will ever close the connection: every rejected sign-in left a
             * socket, a reader thread, a writer thread and a Player object
             * behind for the lifetime of the process.
             *
             * close() queues its wake-up behind the response byte written
             * above, so the client is still told why it was refused.
             */
            this.player.destroy(true);
            this.player.getSession().close();
        }
    }
}

