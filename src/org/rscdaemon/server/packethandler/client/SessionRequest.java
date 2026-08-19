/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Formulae;

public class SessionRequest
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        byte userByte = p.readByte();
        player.setClassName(p.readString().trim());
        long serverKey = Formulae.generateSessionKey(userByte);
        player.setServerKey(serverKey);
        if (System.getProperty("rscd.sessiondebug") != null) {
            org.rscdaemon.server.util.Logger.print("[session] handshake from " + session.getRemoteAddress()
                + " serverKey=" + serverKey + " player=" + System.identityHashCode(player)
                + " at " + System.currentTimeMillis());
        }
        RSCPacketBuilder pb = new RSCPacketBuilder();
        pb.setBare(true);
        pb.addLong(serverKey);
        session.write((Object)pb.toPacket());
    }
}

