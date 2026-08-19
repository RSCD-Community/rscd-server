/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.loginserver.PlayerInfoRequestPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Logger;

public class PlayerInfoRequestHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private PlayerInfoRequestPacketBuilder builder = new PlayerInfoRequestPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        Logger.event("LOGIN_SERVER requested player information (uID: " + uID + ")");
        this.builder.setUID(uID);
        this.builder.setPlayer(world.getPlayer(p.readLong()));
        LSPacket temp = this.builder.getPacket();
        if (temp != null) {
            session.write((Object)temp);
        }
    }
}

