/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.Rows;
import org.rscdaemon.server.util.sql.MysqlException;
import java.util.Map;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.loginserver.PlayerLoginPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.util.DataConversions;

public class PlayerLoginHandler
implements PacketHandler {
    private PlayerLoginPacketBuilder builder = new PlayerLoginPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        World world = (World)session.getAttachment();
        long user = p.readLong();
        String ip = DataConversions.IPToString(p.readLong());
        String pass = p.readString(32).trim();
        String className = p.readString();
        byte loginCode = this.validatePlayer(user, pass, ip);
        this.builder.setUID(uID);
        if (loginCode == 0 || loginCode == 1 || loginCode == 99) {
            this.builder.setPlayer(Server.getServer().findSave(user, world), loginCode);
            world.registerPlayer(user, ip);
        } else {
            this.builder.setPlayer(null, loginCode);
        }
        LSPacket packet = this.builder.getPacket();
        if (packet != null) {
            session.write((Object)packet);
        }
    }

    private byte validatePlayer(long user, String pass, String ip) {
        Server server = Server.getServer();
        byte returnVal = 0;
        try {
            // The player row carries group_id and the ban flag directly — the
            // legacy site's `users`/`bans` tables do not exist in this schema.
            Rows result = Server.db.getQuery("SELECT r.pass, r.banned, r.owner, r.group_id FROM `rscd_players` AS r WHERE `user`=" + user);
            if (!result.next() || !pass.equalsIgnoreCase(result.getString("pass"))) {
                return 2;
            }
            if (result.getInt("banned") == 1) {
                return 6;
            }
            if (result.getInt("group_id") == 1 || result.getInt("group_id") == 2) {
                returnVal = 99;
            }
            int owner = result.getInt("owner");
            for (World w : server.getWorlds()) {
                for (Map.Entry<Long, Integer> player : w.getPlayers()) {
                    if (player.getKey() == user) {
                        return 3;
                    }
                    if (player.getValue() != owner) continue;
                    return 9;
                }
                if (!w.hasPlayer(user)) continue;
                return 3;
            }
            return returnVal;
        }
        catch (MysqlException e) {
            System.out.println("Exception in PlayerLoginHandler :" + e.getMessage());
            return 7;
        }
    }
}

