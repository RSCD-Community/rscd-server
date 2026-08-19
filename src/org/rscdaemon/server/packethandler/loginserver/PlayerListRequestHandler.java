/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.loginserver.PlayerListRequestPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Logger;

public class PlayerListRequestHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private PlayerListRequestPacketBuilder builder = new PlayerListRequestPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        Logger.event("LOGIN_SERVER requested player list (uID: " + uID + ")");
        this.builder.setUID(uID);
        LSPacket temp = this.builder.getPacket();
        if (temp != null) {
            session.write((Object)temp);
        }
    }
}

