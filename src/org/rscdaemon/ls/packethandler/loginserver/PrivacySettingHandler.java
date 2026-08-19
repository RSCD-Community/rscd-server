/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.Rows;
import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class PrivacySettingHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        Server server = Server.getServer();
        long user = p.readLong();
        boolean on = p.readByte() == 1;
        byte idx = p.readByte();
        switch (idx) {
            case 0: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET block_chat=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 1: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET block_private=" + (on ? 1 : 0) + " WHERE user=" + user);
                    Rows result = Server.db.getQuery("SELECT user FROM `rscd_friends` WHERE friend=" + user + " AND user NOT IN (SELECT friend FROM `rscd_friends` WHERE user=" + user + ")");
                    while (result.next()) {
                        long friend = result.getLong("user");
                        World w = server.findWorld(friend);
                        if (w == null) continue;
                        if (on) {
                            w.getActionSender().friendLogout(friend, user);
                            continue;
                        }
                        w.getActionSender().friendLogin(friend, user, world.getID());
                    }
                    break;
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                    break;
                }
            }
            case 2: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET block_trade=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 3: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET block_duel=" + (on ? 1 : 0) + " WHERE user=" + user);
                    break;
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
            }
        }
        server.findSave(user, world).setPrivacySetting(idx, on);
    }
}

