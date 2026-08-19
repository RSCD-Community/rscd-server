/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class GameSettingHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        long user = p.readLong();
        boolean on = p.readByte() == 1;
        byte idx = p.readByte();
        switch (idx) {
            case 0: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET cameraauto=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 2: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET onemouse=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 3: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET soundoff=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 4: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET showroof=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 5: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET autoscreenshot=" + (on ? 1 : 0) + " WHERE user=" + user);
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
                break;
            }
            case 6: {
                try {
                    Server.db.updateQuery("UPDATE `rscd_players` SET combatwindow=" + (on ? 1 : 0) + " WHERE user=" + user);
                    break;
                }
                catch (MysqlException e) {
                    Server.error(e.getMessage());
                }
            }
        }
        Server.getServer().findSave(user, world).setGameSetting(idx, on);
    }
}

