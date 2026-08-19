/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.PlayerSave;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class FriendHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        Server server = Server.getServer();
        long user = p.readLong();
        long friend = p.readLong();
        PlayerSave save = server.findSave(user, world);
        switch (((LSPacket)p).getID()) {
            case 10: {
                boolean avoidBlock = p.readByte() == 1;
                byte[] message = p.getRemainingData();
                World w = server.findWorld(friend);
                if (w == null) break;
                w.getActionSender().sendPM(user, friend, avoidBlock, message);
                break;
            }
            case 11: {
                try {
                    World w;
                    save.addFriend(friend);
                    Server.db.updateQuery("INSERT INTO `rscd_friends`(`user`, `friend`) VALUES('" + user + "', '" + friend + "')");
                    if (Server.db.getQuery("SELECT 1 FROM `rscd_players` AS p LEFT JOIN `rscd_friends` AS f ON f.user=p.user WHERE (p.block_private=0 OR f.friend=" + user + ") AND p.user=" + friend).next() && (w = server.findWorld(friend)) != null) {
                        world.getActionSender().friendLogin(user, friend, w.getID());
                    }
                    if (!Server.db.getQuery("SELECT 1 FROM `rscd_players` AS p LEFT JOIN `rscd_friends` AS f ON f.friend=p.user WHERE p.block_private=1 AND f.user=" + friend + " AND p.user=" + user).next() || (w = server.findWorld(friend)) == null) break;
                    w.getActionSender().friendLogin(friend, user, world.getID());
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 12: {
                try {
                    World w;
                    save.removeFriend(friend);
                    Server.db.updateQuery("DELETE FROM `rscd_friends` WHERE `user` LIKE '" + user + "' AND `friend` LIKE '" + friend + "'");
                    if (!Server.db.getQuery("SELECT 1 FROM `rscd_players` WHERE block_private=1 AND user=" + user).next() || (w = server.findWorld(friend)) == null) break;
                    w.getActionSender().friendLogout(friend, user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 13: {
                try {
                    save.addIgnore(friend);
                    Server.db.updateQuery("INSERT INTO `rscd_ignores`(`user`, `ignore`) VALUES('" + user + "', '" + friend + "')");
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 14: {
                try {
                    save.removeIgnore(friend);
                    Server.db.updateQuery("DELETE FROM `rscd_ignores` WHERE `user` LIKE '" + user + "' AND `ignore` LIKE '" + friend + "'");
                    break;
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
            }
        }
    }
}

