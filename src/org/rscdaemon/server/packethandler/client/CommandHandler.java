/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import java.util.ArrayList;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.ObjectXMLBuilder;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.ThordurHandler;
import org.rscdaemon.server.packetbuilder.loginserver.MiscPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;
import org.rscdaemon.server.util.Logger;

public class CommandHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    public long lasttime;
    String lastplayer;
    public long lastmessage;
    public int levelallowed = 1337;
    public int enterallowed = 0;

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        String s = new String(p.getData()).trim();
        int firstSpace = s.indexOf(" ");
        String cmd = s;
        String[] args = new String[]{};
        if (firstSpace != -1) {
            cmd = s.substring(0, firstSpace).trim();
            args = s.substring(firstSpace + 1).trim().split(" ");
        }
        try {
            this.handleCommand(cmd.toLowerCase(), args, player);
        }
        catch (Exception e) {
            /* A command that throws is almost always bad arguments (::pass
               with no name used to hit args[0] here and vanish). Tell the
               player instead of swallowing it. */
            player.getActionSender().sendMessage("@gry@ Invalid command or arguments.");
        }
    }

    public void handleCommand(String cmd, String[] args, Player player) throws Exception {
        int id;
        MiscPacketBuilder loginServer = world.getServer().getLoginConnector().getActionSender();
        if (cmd.equals("myrate")) {
            int totaladd = 0;
            if (player.isSubscriber()) {
                totaladd += Config.SUBSCRIBER_EXP_MULT;
            }
            player.getActionSender().sendMessage("@gry@ Your personal experience multiplier is " + (Config.EXP_MULT + totaladd) + "x.");
        }
        if (cmd.equals("online")) {
            String playerslist = "";
            int playerscounter = 0;
            for (Player p : world.getPlayers()) {
                playerslist = p.isAdmin() ? "#adm# @yel@" + p.getUsername() + ", " + playerslist : (p.isMod() ? "#mod# @whi@" + p.getUsername() + ", " + playerslist : (p.isPMod() ? "#pmd# @gre@" + p.getUsername() + ", " + playerslist : "@cya@" + p.getUsername() + ", " + playerslist));
                ++playerscounter;
            }
            player.getActionSender().sendAlert("There are " + playerscounter + " players online: " + playerslist, false);
            return;
        }
        if (cmd.equals("say")) {
            int pid = player.getSkillTotal();
            boolean waittime = false;
            if (this.lasttime == 0L) {
                this.lasttime = System.currentTimeMillis();
            }
            ArrayList<Player> informOfChatMessage = new ArrayList<Player>();
            for (Player p : world.getPlayers()) {
                informOfChatMessage.add(p);
            }
            String newStr = "@gre@";
            for (int i = 0; i < args.length; ++i) {
                newStr = newStr + args[i] + " ";
            }
            if (player.isAdmin()) {
                newStr = "@que@@whi@[@red@Admin@whi@] " + player.getUsername() + ": " + newStr;
            } else if (player.isMod()) {
                newStr = "@que@@red@[@whi@Mod@red@]@whi@ " + player.getUsername() + ": " + newStr;
            } else if (player.isPMod()) {
                newStr = "@que@@whi@[@gre@Player Mod@whi@] " + player.getUsername() + ": " + newStr;
            } else if (System.currentTimeMillis() - this.lasttime > 20000L || !player.getUsername().equals(this.lastplayer)) {
                newStr = "@que@@whi@" + player.getUsername() + ": " + newStr;
            } else {
                long timeremaining = 20L - (System.currentTimeMillis() - this.lasttime) / 1000L;
                player.getActionSender().sendMessage("@gry@ You need to wait " + timeremaining + " seconds before using ::say again.");
                waittime = true;
            }
            if (!waittime) {
                this.lasttime = System.currentTimeMillis();
                this.lastplayer = player.getUsername();
                for (Player pl : informOfChatMessage) {
                    pl.getActionSender().sendMessage(newStr);
                }
            }
            return;
        }
        if (!player.isPMod()) {
            return;
        }
        /* Testing tools, not player abilities: these used to sit above the
           gate where anyone could exhaust or skull themselves. */
        if (cmd.equals("blackout")) {
            player.setBlackOut();
            player.getActionSender().sendMessage("@gry@ Blackout sent.");
            return;
        }
        if (cmd.equals("fatigue")) {
            player.setFatigue(100);
            player.getActionSender().sendFatigue();
            player.getActionSender().sendMessage("@gry@ Fatigue has been set to 100.");
            return;
        }
        if (cmd.equals("skull")) {
            player.addSkull(1200000L);
            player.getActionSender().sendMessage("@gry@ Skull added for 20 minutes.");
            return;
        }
        if (cmd.equals("info")) {
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: INFO name");
                return;
            }
            loginServer.requestPlayerInfo(player, DataConversions.usernameToHash(args[0]));
            return;
        }
        if (cmd.equals("ban") || cmd.equals("unban")) {
            boolean banned = cmd.equals("ban");
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: " + (banned ? "BAN" : "UNBAN") + " name");
                return;
            }
            /*
             * The teleport into (or back out of) Thordur's Black Hole used to
             * happen right here, the moment the request was queued, and so it
             * happened whatever the login server went on to answer. Banning an
             * admin threw them into the hole and then told the moderator "You
             * cannot ban a (p)mod or admin!" -- the punishment landed and the
             * ban did not. The login server owns that decision, so the
             * teleport now lives in the reply handler and only runs when the
             * ban really took: see MiscPacketBuilder.banPlayer.
             */
            loginServer.banPlayer(player, DataConversions.usernameToHash(args[0]), banned);
            return;
        }
        if (cmd.equals("kick")) {
            long PlayerHash = DataConversions.usernameToHash(args[0]);
            Player p = world.getPlayer(PlayerHash);
            if (p != null) {
                p.getActionSender().sendLogout();
                p.destroy(true);
                player.getActionSender().sendMessage("@gry@ " + p.getUsername() + " has been kicked from the server.");
                Logger.mod(player.getUsername() + " kicked " + p.getUsername() + " from the server.");
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid username or the player is currently offline.");
            }
            return;
        }
        if (cmd.equals("modroom")) {
            Logger.mod(player.getUsername() + " teleported to the mod room");
            player.teleport(534, 755, true);
            return;
        }
        if (cmd.equals("npc")) {
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: NPC id");
                return;
            }
            if (player.getLocation().inEden()) {
                player.getActionSender().sendMessage("@gry@ NPC disabled.");
                return;
            }
            id = Integer.parseInt(args[0]);
            if (EntityHandler.getNpcDef(id) != null) {
                final Npc n = new Npc(id, player.getX(), player.getY(), player.getX() - 2, player.getX() + 2, player.getY() - 2, player.getY() + 2);
                n.setRespawn(false);
                world.registerNpc(n);
                world.getDelayedEventHandler().add(new SingleEvent(null, 60000){

                    public void action() {
                        Mob opponent = n.getOpponent();
                        if (opponent != null) {
                            opponent.resetCombat(CombatState.ERROR);
                        }
                        n.resetCombat(CombatState.ERROR);
                        world.unregisterNpc(n);
                        n.remove();
                    }
                });
                ObjectXMLBuilder.AppendNPCXMLOutput(id, player.getX(), player.getY(), player.getX() - 5, player.getX() + 5, player.getY() - 5, player.getY() + 5);
                Logger.mod(player.getUsername() + " spawned a " + n.getDef().getName() + " at " + player.getLocation().toString());
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid id");
            }
            return;
        }
        if (cmd.equals("teleport")) {
            int y;
            if (args.length != 2) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: TELEPORT x y");
                return;
            }
            int x = Integer.parseInt(args[0]);
            if (world.withinWorld(x, y = Integer.parseInt(args[1]))) {
                Logger.mod(player.getUsername() + " teleported from " + player.getLocation().toString() + " to (" + x + ", " + y + ")");
                player.teleport(x, y, true);
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid coordinates!");
            }
            return;
        }
        if (cmd.equals("goto") || cmd.equals("summon")) {
            boolean summon = cmd.equals("summon");
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: " + (summon ? "SUMMON" : "GOTO") + " name");
                return;
            }
            long usernameHash = DataConversions.usernameToHash(args[0]);
            Player affectedPlayer = world.getPlayer(usernameHash);
            if (affectedPlayer != null) {
                if (summon) {
                    if (affectedPlayer.getLocation().inEden()) {
                        player.getActionSender().sendMessage("@gry@ Summoning disabled.");
                        return;
                    }
                    Logger.mod(player.getUsername() + " summoned " + affectedPlayer.getUsername() + " from " + affectedPlayer.getLocation().toString() + " to " + player.getLocation().toString());
                    affectedPlayer.teleport(player.getX(), player.getY(), true);
                    affectedPlayer.getActionSender().sendMessage("You have been summoned by " + player.getUsername());
                } else {
                    Logger.mod(player.getUsername() + " went from " + player.getLocation() + " to " + affectedPlayer.getUsername() + " at " + affectedPlayer.getLocation().toString());
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                }
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid player, maybe they aren't currently on this server?");
            }
            return;
        }
        if (cmd.equals("take") || cmd.equals("put")) {
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: TAKE name");
                return;
            }
            Player affectedPlayer = world.getPlayer(DataConversions.usernameToHash(args[0]));
            if (affectedPlayer.getLocation().inEden()) {
                player.getActionSender().sendMessage("@gry@ Take/put disabled.");
                return;
            }
            if (affectedPlayer == null) {
                player.getActionSender().sendMessage("@gry@ Invalid player, maybe they aren't currently online?");
                return;
            }
            Logger.mod(player.getUsername() + " took " + affectedPlayer.getUsername() + " from " + affectedPlayer.getLocation().toString() + " to admin room");
            affectedPlayer.teleport(539, 764, true);
            if (cmd.equals("take")) {
                player.teleport(539, 761, true);
            }
            return;
        }
        if (!player.isAdmin()) {
            return;
        }
        if (cmd.equals("shutdown")) {
            Logger.mod(player.getUsername() + " shut down the server!");
            world.getServer().kill();
            return;
        }
        if (cmd.equals("appearance")) {
            player.setChangingAppearance(true);
            player.getActionSender().sendAppearanceScreen();
            return;
        }
        if (cmd.equals("npco")) {
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: NPC id");
                return;
            }
            if (player.getLocation().inEden()) {
                player.getActionSender().sendMessage("@gry@ NPC disabled.");
                return;
            }
            id = Integer.parseInt(args[0]);
            if (EntityHandler.getNpcDef(id) != null) {
                final Npc n = new Npc(id, player.getX(), player.getY(), player.getX() - 2, player.getX() + 2, player.getY() - 2, player.getY() + 2);
                n.setRespawn(false);
                world.registerNpc(n);
                world.getDelayedEventHandler().add(new SingleEvent(null, 60000){

                    public void action() {
                        Mob opponent = n.getOpponent();
                        if (opponent != null) {
                            opponent.resetCombat(CombatState.ERROR);
                        }
                        n.resetCombat(CombatState.ERROR);
                        world.unregisterNpc(n);
                        n.remove();
                    }
                });
                Logger.mod(player.getUsername() + " spawned a " + n.getDef().getName() + " at " + player.getLocation().toString());
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid id");
            }
            return;
        }
        if (cmd.equals("item")) {
            if (args.length < 1 || args.length > 2) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: ITEM id [amount]");
                return;
            }
            id = Integer.parseInt(args[0]);
            if (EntityHandler.getItemDef(id) != null) {
                int amount = 1;
                if (args.length == 2 && EntityHandler.getItemDef(id).isStackable()) {
                    amount = Integer.parseInt(args[1]);
                }
                InvItem item = new InvItem(id, amount);
                player.getInventory().add(item);
                player.getActionSender().sendInventory();
                Logger.mod(player.getUsername() + " spawned themself " + amount + " " + item.getDef().getName() + "(s)");
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid id");
            }
            return;
        }
        if (cmd.equals("object")) {
            if (args.length < 1 || args.length > 2) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: OBJECT id [direction]");
                return;
            }
            if (player.getLocation().inEden()) {
                player.getActionSender().sendMessage("@gry@ Object disabled.");
                return;
            }
            id = Integer.parseInt(args[0]);
            if (id < 0) {
                GameObject object = world.getTile(player.getLocation()).getGameObject();
                if (object != null) {
                    world.unregisterGameObject(object);
                }
            } else if (EntityHandler.getGameObjectDef(id) != null) {
                int dir = args.length == 2 ? Integer.parseInt(args[1]) : 0;
                world.registerGameObject(new GameObject(player.getLocation(), id, dir, 0));
                ObjectXMLBuilder.AppendObjectXMLOutput(id, player.getX(), player.getY(), dir);
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid id");
            }
            return;
        }
        if (cmd.equals("message")) {
            boolean waitmessage = false;
            if (this.lastmessage == 0L) {
                this.lastmessage = System.currentTimeMillis();
            }
            String message = "";
            for (int i = 1; i < args.length; ++i) {
                message = message + args[i] + " ";
            }
            long PlayerHash = DataConversions.usernameToHash(args[0]);
            Player p = world.getPlayer(PlayerHash);
            if (p != null) {
                if (p.isAdmin()) {
                    p.getActionSender().sendAlert("#adm#" + player.getUsername() + ": " + message, false);
                } else if (p.isMod()) {
                    p.getActionSender().sendAlert("#mod#" + player.getUsername() + ": " + message, false);
                } else if (p.isPMod()) {
                    p.getActionSender().sendAlert("#pmd#" + player.getUsername() + ": " + message, false);
                } else if (System.currentTimeMillis() - this.lastmessage < 5L) {
                    long timemessage = 30L - (System.currentTimeMillis() - this.lastmessage) / 1000L;
                    player.getActionSender().sendMessage("@gry@ You need to wait " + timemessage + " seconds between global messages.");
                    waitmessage = true;
                }
                if (!waitmessage) {
                    this.lastmessage = System.currentTimeMillis();
                    p.getActionSender().sendAlert(player.getUsername() + ": " + message, false);
                    player.getActionSender().sendMessage("Sent to " + p.getUsername() + ": " + message);
                }
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid player, maybe they aren't currently online?");
            }
            return;
        }
        if (cmd.equals("global")) {
            String globalMsg = "";
            for (int i = 0; i < args.length; ++i) {
                globalMsg = globalMsg + args[i] + " ";
            }
            for (Player p : world.getPlayers()) {
                p.getActionSender().sendAlert("#adm#" + player.getUsername() + ": " + globalMsg, false);
            }
            return;
        }
        if (cmd.equals("summonall")) {
            /* One player standing in Eden used to abort the loop mid-way,
               leaving half the server summoned and no message; now they are
               simply skipped, and the summoner hears about it up front. */
            player.getActionSender().sendMessage("@gry@ You are summoning all players on the server.");
            for (Player p : world.getPlayers()) {
                if (p == player || p.getLocation().inEden()) {
                    continue;
                }
                p.teleport(player.getX(), player.getY(), true);
                p.getActionSender().sendMessage("@gry@ You have been summoned by " + player.getUsername());
            }
            return;
        }
        if (cmd.equals("ha")) {
            long PlayerHash = DataConversions.usernameToHash(args[0]);
            Player p = world.getPlayer(PlayerHash);
            if (p != null) {
                String newStr = "";
                for (int i = 1; i < args.length; ++i) {
                    newStr = newStr + args[i] + " ";
                }
                p.addMessageToChatQueue(DataConversions.stringToByteArray(newStr));
                player.getActionSender().sendMessage("@gry@ You made " + p.getUsername() + " say ' " + newStr);
                Logger.mod(player.getUsername() + " made " + p.getUsername() + " say but not see " + newStr);
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid player name or maybe they aren't online?");
            }
            return;
        }
        if (cmd.equals("start")) {
            GameObject flame1 = new GameObject(Point.location(228, 134), 1036, 1, 0);
            world.registerGameObject(flame1);
            world.delayedRemoveObject(flame1, 2000);
            GameObject flame2 = new GameObject(Point.location(228, 133), 1036, 0, 0);
            world.registerGameObject(flame2);
            world.delayedRemoveObject(flame2, 2000);
            GameObject flame3 = new GameObject(Point.location(228, 132), 1036, 0, 0);
            world.registerGameObject(flame3);
            world.delayedRemoveObject(flame3, 2000);
            GameObject flame4 = new GameObject(Point.location(228, 131), 1036, 0, 0);
            world.registerGameObject(flame4);
            world.delayedRemoveObject(flame4, 2000);
            GameObject flame5 = new GameObject(Point.location(228, 130), 1036, 0, 0);
            world.registerGameObject(flame5);
            world.delayedRemoveObject(flame5, 2000);
            GameObject flame6 = new GameObject(Point.location(228, 129), 1036, 0, 0);
            world.registerGameObject(flame6);
            world.delayedRemoveObject(flame6, 2000);
            GameObject flame7 = new GameObject(Point.location(228, 128), 1036, 0, 0);
            world.registerGameObject(flame7);
            world.delayedRemoveObject(flame7, 2000);
            GameObject flame8 = new GameObject(Point.location(228, 127), 1036, 0, 0);
            world.registerGameObject(flame8);
            world.delayedRemoveObject(flame8, 2000);
            GameObject flame9 = new GameObject(Point.location(228, 126), 1036, 0, 0);
            world.registerGameObject(flame9);
            world.delayedRemoveObject(flame9, 2000);
            GameObject flame10 = new GameObject(Point.location(229, 134), 5, 0, 2);
            world.delayedRemoveObject(flame10, 1000);
            GameObject flame11 = new GameObject(Point.location(229, 133), 5, 0, 2);
            world.delayedRemoveObject(flame11, 1000);
            GameObject flame12 = new GameObject(Point.location(229, 132), 5, 0, 2);
            world.delayedRemoveObject(flame12, 1000);
            GameObject flame13 = new GameObject(Point.location(229, 131), 5, 0, 2);
            world.delayedRemoveObject(flame13, 1000);
            GameObject flame14 = new GameObject(Point.location(229, 130), 5, 0, 2);
            world.delayedRemoveObject(flame14, 1000);
            GameObject flame15 = new GameObject(Point.location(229, 129), 5, 0, 2);
            world.delayedRemoveObject(flame15, 1000);
            GameObject flame16 = new GameObject(Point.location(229, 128), 5, 0, 2);
            world.delayedRemoveObject(flame16, 1000);
            GameObject flame17 = new GameObject(Point.location(229, 127), 5, 0, 2);
            world.delayedRemoveObject(flame17, 1000);
            GameObject flame18 = new GameObject(Point.location(229, 126), 5, 0, 2);
            world.delayedRemoveObject(flame18, 1000);
            return;
        }
        if (cmd.equals("start2")) {
            GameObject rail1 = new GameObject(Point.location(228, 134), 5, 1, 1);
            world.registerDoor(rail1);
            GameObject rail2 = new GameObject(Point.location(228, 133), 5, 1, 1);
            world.registerDoor(rail2);
            GameObject rail3 = new GameObject(Point.location(228, 132), 5, 1, 1);
            world.registerDoor(rail3);
            GameObject rail4 = new GameObject(Point.location(228, 131), 5, 1, 1);
            world.registerDoor(rail4);
            GameObject rail5 = new GameObject(Point.location(228, 130), 5, 1, 1);
            world.registerDoor(rail5);
            GameObject rail6 = new GameObject(Point.location(228, 129), 5, 1, 1);
            world.registerDoor(rail6);
            GameObject rail7 = new GameObject(Point.location(228, 128), 5, 1, 1);
            world.registerDoor(rail7);
            GameObject rail8 = new GameObject(Point.location(228, 127), 5, 1, 1);
            world.registerDoor(rail8);
            GameObject rail9 = new GameObject(Point.location(228, 126), 5, 1, 1);
            world.registerDoor(rail9);
            GameObject rail10 = new GameObject(Point.location(229, 134), 5, 1, 1);
            world.registerDoor(rail10);
            GameObject rail11 = new GameObject(Point.location(229, 133), 5, 1, 1);
            world.registerDoor(rail11);
            GameObject rail12 = new GameObject(Point.location(229, 132), 5, 1, 1);
            world.registerDoor(rail12);
            GameObject rail13 = new GameObject(Point.location(229, 131), 5, 1, 1);
            world.registerDoor(rail13);
            GameObject rail14 = new GameObject(Point.location(229, 130), 5, 1, 1);
            world.registerDoor(rail14);
            GameObject rail15 = new GameObject(Point.location(229, 129), 5, 1, 1);
            world.registerDoor(rail15);
            GameObject rail16 = new GameObject(Point.location(229, 128), 5, 1, 1);
            world.registerDoor(rail16);
            GameObject rail17 = new GameObject(Point.location(229, 127), 5, 1, 1);
            world.registerDoor(rail17);
            GameObject rail18 = new GameObject(Point.location(229, 126), 5, 1, 1);
            world.registerDoor(rail18);
            return;
        }
        if (cmd.equals("10goto")) {
            if (args.length != 1) {
                player.getActionSender().sendMessage("@gry@ Invalid args.");
                return;
            }
            long usernameHash = DataConversions.usernameToHash(args[0]);
            Player affectedPlayer = world.getPlayer(usernameHash);
            if (affectedPlayer != null) {
                if (player.isMod()) {
                    Logger.mod(player.getUsername() + " went from " + player.getLocation() + " to " + affectedPlayer.getUsername() + " at " + affectedPlayer.getLocation().toString());
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                    player.teleport(affectedPlayer.getX(), affectedPlayer.getY(), true);
                }
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid player, maybe they aren't currently on this server?");
            }
            return;
        }
        if (cmd.equals("door")) {
            if (player.getLocation().inEden()) {
                player.getActionSender().sendMessage("@gry@ Door disabled.");
                return;
            }
            if (!player.getLocation().inModRoom()) {
                player.getActionSender().sendMessage("@gry@ This command cannot be used outside of the mod room");
                return;
            }
            if (args.length < 1 || args.length > 2) {
                player.getActionSender().sendMessage("@gry@ Invalid args. Syntax: DOOR id [direction]");
                return;
            }
            id = Integer.parseInt(args[0]);
            if (id < 0) {
                GameObject door = world.getTile(player.getLocation()).getDoor();
                if (door != null) {
                    world.unregisterGameObject(door);
                }
            } else if (EntityHandler.getDoorDef(id) != null) {
                int dir = args.length == 2 ? Integer.parseInt(args[1]) : 0;
                world.registerGameObject(new GameObject(player.getLocation(), id, dir, 1));
            } else {
                player.getActionSender().sendMessage("@gry@ Invalid id");
            }
            return;
        }
        if (cmd.equals("dropall")) {
            player.getInventory().getItems().clear();
            player.getActionSender().sendInventory();
        }
        if (cmd.equals("allowevent")) {
            this.enterallowed = id = Integer.parseInt(args[0]);
            player.getActionSender().sendMessage("@gry@ enterallowed: " + args[1]);
        }
        if (cmd.equals("allowlevel")) {
            this.levelallowed = id = Integer.parseInt(args[0]);
            player.getActionSender().sendMessage("@gry@ levelallowed: " + args[1]);
        }
        if (cmd.equals("exprate")) {
            Config.EXP_MULT = id = Integer.parseInt(args[0]);
            for (Player p : world.getPlayers()) {
                p.getActionSender().sendMessage("@gry@ The experience multiplier has been set to " + id + "x.");
            }
        }
        if (cmd.equals("setstat")) {
            if (args.length != 3) {
                player.getActionSender().sendMessage("@gry@ Invalid syntax! SETSTAT username stat lvl");
                return;
            }
            long usernameHash = DataConversions.usernameToHash(args[0]);
            Player affectedPlayer = world.getPlayer(usernameHash);
            int stat = Formulae.getStatIndex(args[1]);
            /*
             * getStatIndex returns -1 for a name it does not know, and every
             * line below indexes a stat array with it. A typo used to take the
             * packet thread down with an ArrayIndexOutOfBounds; it matters
             * more now that slot 17 answers to "thieving" instead of "quest",
             * because anyone with the old name in their fingers lands here.
             */
            if (stat == -1) {
                player.getActionSender().sendMessage("@gry@ Unknown stat '" + args[1] + "'.");
                return;
            }
            int lvl = Integer.parseInt(args[2]);
            if (lvl < 0 || lvl > 99) {
                player.getActionSender().sendMessage("Invalid " + args[1] + " level.");
                return;
            }
            affectedPlayer.setCurStat(stat, lvl);
            affectedPlayer.setMaxStat(stat, lvl);
            affectedPlayer.setExp(stat, Formulae.lvlToXp(lvl) * 4);
            int hitpointsXp = 4616 + (affectedPlayer.getExp(0) + affectedPlayer.getExp(1) + affectedPlayer.getExp(2)) / 3;
            int hitpointsLVL = Formulae.experienceToLevel(hitpointsXp);
            if (hitpointsLVL < 10) {
                if (hitpointsLVL > affectedPlayer.getCurStat(3)) {
                    affectedPlayer.setCurStat(3, 10);
                }
                hitpointsLVL = 10;
                affectedPlayer.setMaxStat(3, 10);
                affectedPlayer.setExp(3, 4620);
                affectedPlayer.getActionSender().sendStats();
            } else {
                if (hitpointsLVL > affectedPlayer.getCurStat(3)) {
                    affectedPlayer.setCurStat(3, hitpointsLVL);
                }
                affectedPlayer.setMaxStat(3, hitpointsLVL);
                affectedPlayer.setExp(3, hitpointsXp);
                affectedPlayer.getActionSender().sendStats();
            }
            int comb = Formulae.getCombatlevel(affectedPlayer.getMaxStats());
            if (comb != affectedPlayer.getCombatLevel()) {
                affectedPlayer.setCombatLevel(comb);
                affectedPlayer.getActionSender().sendStats();
            }
            affectedPlayer.getActionSender().sendStats();
            affectedPlayer.getActionSender().sendMessage("@gry@ Your " + args[1] + " has been set to level " + lvl + " by " + player.getUsername());
            player.getActionSender().sendMessage("@gry@ You have set " + affectedPlayer.getUsername() + "'s " + args[1] + " to level " + lvl);
        }
    }
}

