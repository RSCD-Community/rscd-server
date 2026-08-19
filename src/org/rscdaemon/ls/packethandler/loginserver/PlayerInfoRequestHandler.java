/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.LSPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class PlayerInfoRequestHandler
implements PacketHandler {
    public void handlePacket(Packet p, final Connection session) throws Exception {
        final long uID = ((LSPacket)p).getUID();
        long user = p.readLong();
        final World w = Server.getServer().findWorld(user);
        if (w == null) {
            LSPacketBuilder builder = new LSPacketBuilder();
            builder.setUID(uID);
            builder.addByte((byte)0);
            session.write((Object)builder.toPacket());
            return;
        }
        w.getActionSender().requestPlayerInfo(user, new PacketHandler(){

            public void handlePacket(Packet p, Connection s) throws Exception {
                LSPacketBuilder builder = new LSPacketBuilder();
                builder.setUID(uID);
                if (p.readByte() == 0) {
                    builder.addByte((byte)0);
                } else {
                    builder.addByte((byte)1);
                    builder.addShort(w == null ? 0 : w.getID());
                    builder.addBytes(p.getRemainingData());
                }
                session.write((Object)builder.toPacket());
            }
        });
    }
}

