/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.model;

import org.rscdaemon.server.util.sql.Rows;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.PlayerSave;
import org.rscdaemon.ls.packetbuilder.loginserver.MiscPacketBuilder;
import org.rscdaemon.ls.util.DataConversions;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class World {
    private Connection session;
    private int id = -1;
    private MiscPacketBuilder actionSender = new MiscPacketBuilder();
    private TreeMap<Long, Integer> players = new TreeMap();
    private TreeMap<Long, PlayerSave> saves = new TreeMap();

    public Collection<Map.Entry<Long, PlayerSave>> getAssosiatedSaves() {
        return this.saves.entrySet();
    }

    public void assosiateSave(PlayerSave save) {
        this.saves.put(save.getUser(), save);
    }

    public void unassosiateSave(PlayerSave save) {
        this.saves.remove(save.getUser());
    }

    public PlayerSave getSave(long user) {
        return this.saves.get(user);
    }

    public World(int id, Connection session) {
        this.id = id;
        this.setSession(session);
    }

    public void setSession(Connection session) {
        this.session = session;
    }

    public Connection getSession() {
        return this.session;
    }

    public MiscPacketBuilder getActionSender() {
        return this.actionSender;
    }

    public int getID() {
        return this.id;
    }

    public void registerPlayer(long user, String ip) {
        Server server = Server.getServer();
        try {
            Rows result = Server.db.getQuery("SELECT owner, block_private FROM `rscd_players` WHERE `user`=" + user);
            if (!result.next()) {
                return;
            }
            int owner = result.getInt("owner");
            boolean blockPrivate = result.getInt("block_private") == 1;
            result = Server.db.getQuery("SELECT user FROM `rscd_friends` WHERE `friend`=" + user + (blockPrivate ? " AND user IN (SELECT friend FROM `rscd_friends` WHERE `user`=" + user + ")" : ""));
            while (result.next()) {
                long friend = result.getLong("user");
                World w = server.findWorld(friend);
                if (w == null) continue;
                w.getActionSender().friendLogin(friend, user, this.id);
            }
            long now = (int)(System.currentTimeMillis() / 1000L);
            Server.db.updateQuery("INSERT INTO `rscd_logins`(`user`, `time`, `ip`) VALUES('" + user + "', '" + now + "', '" + ip + "')");
            Server.db.updateQuery("UPDATE `rscd_players` SET login_date=" + now + ", login_ip='" + ip + "' WHERE user=" + user);
            this.setPresence(user, true);
            this.players.put(user, owner);
            System.out.println("Added " + DataConversions.hashToUsername(user) + " to world " + this.id);
        }
        catch (Exception e) {
            Server.error(e);
        }
    }

    public void unregisterPlayer(long user) {
        for (World w : Server.getServer().getWorlds()) {
            w.getActionSender().friendLogout(user);
        }
        this.players.remove(user);
        this.setPresence(user, false);
        System.out.println("Removed " + DataConversions.hashToUsername(user) + " from world " + this.id);
    }

    public void clearPlayers() {
        for (Map.Entry<Long, Integer> player : this.getPlayers()) {
            long user = player.getKey();
            for (World w : Server.getServer().getWorlds()) {
                w.getActionSender().friendLogout(user);
            }
            System.out.println("Removed " + DataConversions.hashToUsername(user) + " from world " + this.id);
        }
        this.players.clear();
        this.clearPresence();
    }

    /**
     * Records whether a player is currently present in this world.
     *
     * `rscd_players`.`world` is the column the website's status page reads --
     * status.php selects players WHERE world=<id> and counts them by joining
     * rscd_worlds ON rscd_players.world=rscd_worlds.id. Nothing in RSCD ever
     * wrote it, and it defaults to 1, so every account has always looked
     * permanently logged into world 1. `online` is kept in step with it for
     * anything reading that column instead.
     *
     * NULL, not 0, means "not in a world": 0 would be a world id like any
     * other, and the column is nullable precisely so absence is representable.
     *
     * There is no injection surface here. `user` is a long and `id` an int,
     * both primitives -- no caller-supplied string reaches the statement.
     */
    private void setPresence(long user, boolean present) {
        this.runPresenceUpdate("UPDATE `rscd_players` SET `world`=" + (present ? Integer.toString(this.id) : "NULL") + ", `online`=" + (present ? 1 : 0) + " WHERE `user`=" + user);
    }

    /**
     * Drops every player's presence in this world. Called when the game server
     * disconnects, so an unclean shutdown does not leave accounts stranded
     * looking online forever. Keyed on the world id rather than on the
     * in-memory player list, so it also clears rows left behind by a previous
     * process that died without cleaning up.
     */
    public void clearPresence() {
        this.runPresenceUpdate("UPDATE `rscd_players` SET `world`=NULL, `online`=0 WHERE `world`=" + this.id);
    }

    /**
     * Logs the query that failed rather than just the stack trace: a transient
     * database error while updating a cosmetic presence flag is worth seeing in
     * context and worth ignoring otherwise.
     *
     * This used to matter far more than it does now -- Server.error(Exception)
     * called System.exit(1), so routing a failed presence UPDATE through it
     * would have taken the whole login server down. That is fixed at the
     * source; see Server.error().
     */
    private void runPresenceUpdate(String query) {
        try {
            Server.db.updateQuery(query);
        }
        catch (Exception e) {
            Server.error("Presence update failed on world " + this.id + ": " + e.getMessage());
        }
    }

    public Collection<Map.Entry<Long, Integer>> getPlayers() {
        return this.players.entrySet();
    }

    public boolean hasPlayer(long user) {
        return this.players.containsKey(user);
    }
}

