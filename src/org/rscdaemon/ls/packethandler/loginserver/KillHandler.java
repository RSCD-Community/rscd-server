/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class KillHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        try {
            Server.db.updateQuery("INSERT INTO `rscd_kills`(`user`, `killed`, `time`, `type`) VALUES('" + p.readLong() + "', '" + p.readLong() + "', " + (int)(System.currentTimeMillis() / 1000L) + ", " + p.readByte() + ")");
        }
        catch (MysqlException sQLException) {
            // empty catch block
        }
    }
}

