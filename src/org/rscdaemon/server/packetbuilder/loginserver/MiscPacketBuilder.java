/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import java.util.ArrayList;
import java.util.List;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.LoginConnector;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.ThordurHandler;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.packethandler.PlayerLogin;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.EntityList;
import org.rscdaemon.server.util.Logger;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class MiscPacketBuilder {
    /** Item 387, the only way back out of the Black Hole. See banPlayer. */
    private static final int DISK_OF_RETURNING = 387;
    private World world = World.getWorld();
    private final LoginConnector connector;
    private List<LSPacket> packets = new ArrayList<LSPacket>();

    public MiscPacketBuilder(LoginConnector connector) {
        this.connector = connector;
    }

    public List<LSPacket> getPackets() {
        return this.packets;
    }

    public void clearPackets() {
        this.packets.clear();
    }

    public void registerWorld() {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(1);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                MiscPacketBuilder.this.connector.setRegistered(p.readByte() == 1);
            }
        });
        s.addShort(Config.SERVER_NUM);
        EntityList<Player> players = this.world.getPlayers();
        s.addShort(players.size());
        for (Player player : players) {
            s.addLong(player.getUsernameHash());
            s.addLong(DataConversions.IPToLong(player.getCurrentIP()));
        }
        this.packets.add(s.toPacket());
    }

    public void unregisterWorld() {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(2);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                /* MINA's close() handed back a CloseFuture and this waited on it.
   The transport closes asynchronously and fires connectionClosed
   when the socket is actually gone, so there is nothing to join. */
                session.close();
                MiscPacketBuilder.this.world.getServer().unbind();
                MiscPacketBuilder.this.world.getServer().getEngine().kill();
            }
        });
        this.packets.add(s.toPacket());
    }

    public void logKill(long user, long killed, boolean stake) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(3);
        s.addLong(user);
        s.addLong(killed);
        s.addByte((byte)(stake ? 2 : 1));
        this.packets.add(s.toPacket());
    }

    public void banPlayer(final Player mod, final long user, final boolean ban) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(ban ? 4 : 5);
        s.addLong(user);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                if (p.readByte() == 1) {
                    Logger.mod(mod.getUsername() + " " + (ban ? "banned" : "unbanned") + " " + DataConversions.hashToUsername(user));
                    /*
                     * Only now, with the login server's answer in hand. A
                     * currently-connected player is dropped into (or fetched
                     * back out of) Thordur's Black Hole rather than just
                     * kicked -- the real historical use of the moderator
                     * "black hole" teleport. Landing there works and walking
                     * away does not: WalkRequest refuses every step taken
                     * inside it. See ThordurHandler and Point.inBlackHole.
                     */
                    Player target = MiscPacketBuilder.this.world.getPlayer(user);
                    if (target != null) {
                        if (ban) {
                            target.getActionSender().sendMessage("@red@You have been banned by a moderator.");
                            target.teleport(ThordurHandler.BANNED_X, ThordurHandler.BANNED_Y, false);
                            /*
                             * The disk is the only way out of the hole, so
                             * anyone who habitually carries one is carrying a
                             * key to their own cell. Taken on the way in --
                             * a punishment you can spin your way out of is
                             * not one. Buying another means talking to
                             * Thordur, who is not in there.
                             */
                            int disks = target.getInventory().countId(DISK_OF_RETURNING);
                            if (disks > 0) {
                                target.getInventory().remove(DISK_OF_RETURNING, disks);
                                target.getActionSender().sendInventory();
                                target.getActionSender().sendMessage("@red@Your disk of returning crumbles to dust.");
                            }
                        } else if (target.getLocation().inBlackHole()) {
                            target.getActionSender().sendMessage("@gre@You have been unbanned.");
                            target.teleport(ThordurHandler.THORDUR_X, ThordurHandler.THORDUR_Y, false);
                        }
                    }
                }
                mod.getActionSender().sendMessage(p.readString());
            }
        });
        this.packets.add(s.toPacket());
    }

    public void requestPlayerInfo(final Player mod, final long user) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(8);
        s.addLong(user);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                if (p.readByte() == 1) {
                    Logger.mod(mod.getUsername() + " requested information on " + DataConversions.hashToUsername(user));
                    short world = p.readShort();
                    Point location = Point.location(p.readShort(), p.readShort());
                    long loginDate = p.readLong();
                    int lastMoved = (int)((System.currentTimeMillis() - p.readLong()) / 1000L);
                    boolean chatBlock = p.readByte() == 1;
                    short fatigue = p.readShort();
                    String state = p.readString();
                    mod.getActionSender().sendAlert("@whi@" + DataConversions.hashToUsername(user) + " is currently on world @or1@" + world + "@whi@ at @or1@" + location.toString() + "@whi@ (@or1@" + location.getDescription() + "@whi@). State is @or1@" + state + "@whi@. Logged in @or1@" + DataConversions.timeSince(loginDate) + "@whi@ ago. Last moved @or1@" + lastMoved + " secs @whi@ ago. Chat block is @or1@" + (chatBlock ? "on" : "off") + "@whi@. Fatigue is at @or1@" + fatigue + "@whi@.", false);
                } else {
                    mod.getActionSender().sendMessage("Invalid player, maybe they aren't currently online?");
                }
            }
        });
        this.packets.add(s.toPacket());
    }

    public void saveProfiles() {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(9);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                if (p.readByte() != 1) {
                    Logger.error("Error saving all profiles!");
                }
            }
        });
        this.packets.add(s.toPacket());
    }

    public void sendPM(long user, long friend, boolean avoidBlock, byte[] message) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(10);
        s.addLong(user);
        s.addLong(friend);
        s.addByte((byte)(avoidBlock ? 1 : 0));
        s.addBytes(message);
        this.packets.add(s.toPacket());
    }

    public void addFriend(long user, long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(11);
        s.addLong(user);
        s.addLong(friend);
        this.packets.add(s.toPacket());
    }

    public void removeFriend(long user, long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(12);
        s.addLong(user);
        s.addLong(friend);
        this.packets.add(s.toPacket());
    }

    public void addIgnore(long user, long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(13);
        s.addLong(user);
        s.addLong(friend);
        this.packets.add(s.toPacket());
    }

    public void removeIgnore(long user, long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(14);
        s.addLong(user);
        s.addLong(friend);
        this.packets.add(s.toPacket());
    }

    public void reportUser(long user, long reported, byte reason) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(15);
        s.addLong(user);
        s.addLong(reported);
        s.addByte(reason);
        this.packets.add(s.toPacket());
    }

    /**
     * Book a party. The fee is already out of the player's inventory when
     * this is sent -- the guard took it mid-conversation -- so both failure
     * replies hand it back. Success answers in plain chat rather than
     * resuming the conversation, because the reply is asynchronous and the
     * player may have walked away from the guard by then.
     */
    public void partyBook(final Player player, final long start) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(16);
        s.addLong(player.getUsernameHash());
        s.addLong(start);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                byte status = p.readByte();
                if (status == 0) {
                    player.getActionSender().sendMessage("@gre@The guard adds your party to the schedule");
                    MiscPacketBuilder.this.partyList();
                    return;
                }
                player.getInventory().add(new org.rscdaemon.server.model.InvItem(10, org.rscdaemon.server.model.PartySchedule.FEE));
                player.getActionSender().sendInventory();
                if (status == 1) {
                    player.getActionSender().sendMessage("@ora@You already have a party on the schedule - the guard returns your fee");
                } else if (status == 3) {
                    player.getActionSender().sendMessage("@ora@That time slot is already taken - the guard returns your fee");
                } else {
                    player.getActionSender().sendMessage("@ora@The guard cannot find the schedule just now and returns your fee");
                }
            }
        });
        this.packets.add(s.toPacket());
    }

    /**
     * Party Animals credit: count items just left the cannons while a
     * scheduled party was running. Which host owns the moment is decided in
     * the database, where the calendar lives -- the game-side cache can be
     * minutes stale about a booking made moments ago. The reply is a status
     * byte with nothing in it; it is read so the connection's reply
     * accounting stays clean, and that is all.
     */
    public void partyShots(int count) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(18);
        s.addByte((byte)count);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                p.readByte();
            }
        });
        this.packets.add(s.toPacket());
    }

    /** Refresh the game-side calendar cache from the database. */
    public void partyList() {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(17);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                org.rscdaemon.server.model.PartySchedule.setCache(MiscPacketBuilder.readParties(p));
            }
        });
        this.packets.add(s.toPacket());
    }

    /** Fetch the calendar fresh and open it as the schedule item's page. */
    public void partyBookPage(final Player player) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(17);
        s.setHandler(this.connector, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                List<org.rscdaemon.server.model.PartySchedule.Entry> parties = MiscPacketBuilder.readParties(p);
                org.rscdaemon.server.model.PartySchedule.setCache(parties);
                player.getActionSender().sendAlert(
                    org.rscdaemon.server.model.PartySchedule.bookText(parties), true);
            }
        });
        this.packets.add(s.toPacket());
    }

    private static List<org.rscdaemon.server.model.PartySchedule.Entry> readParties(Packet p) throws Exception {
        int count = p.readByte();
        List<org.rscdaemon.server.model.PartySchedule.Entry> parties =
            new ArrayList<org.rscdaemon.server.model.PartySchedule.Entry>();
        for (int i = 0; i < count; i++) {
            parties.add(new org.rscdaemon.server.model.PartySchedule.Entry(p.readLong(), p.readLong()));
        }
        return parties;
    }

    public void playerLogout(long user) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(30);
        s.addLong(user);
        this.packets.add(s.toPacket());
    }

    public void playerLogin(Player player) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(31);
        s.setHandler(this.connector, new PlayerLogin(player));
        s.addLong(player.getUsernameHash());
        s.addLong(DataConversions.IPToLong(player.getCurrentIP()));
        /* Already MD5 -- Player.load hashed it at the door and the plaintext
           was never kept. Same bytes on the wire as before. */
        s.addBytes(player.getPasswordHash().getBytes());
        s.addBytes(player.getClassName().getBytes());
        this.packets.add(s.toPacket());
    }

    public void logAction(String message, int type) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(32);
        s.addByte((byte)type);
        s.addBytes(message.getBytes());
        this.packets.add(s.toPacket());
    }
}

