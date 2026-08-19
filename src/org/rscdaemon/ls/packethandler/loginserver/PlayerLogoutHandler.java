/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class PlayerLogoutHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        world.unregisterPlayer(p.readLong());
    }
}

