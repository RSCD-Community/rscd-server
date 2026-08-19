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

public class ReportHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        final long user = p.readLong();
        final long reported = p.readLong();
        final byte reason = p.readByte();
        world.getActionSender().requestReportInfo(reported, new PacketHandler(){

            public void handlePacket(Packet p, Connection session) throws Exception {
                short x = p.readShort();
                short y = p.readShort();
                String status = p.readString();
                try {
                    Server.db.updateQuery("INSERT INTO `rscd_reports`(`from`, `about`, `time`, `reason`, `x`, `y`, `status`) VALUES('" + user + "', '" + reported + "', '" + System.currentTimeMillis() / 1000L + "', '" + reason + "', '" + x + "', '" + y + "', '" + status + "')");
                }
                catch (MysqlException e) {
                    Server.error(e);
                }
            }
        });
    }
}

