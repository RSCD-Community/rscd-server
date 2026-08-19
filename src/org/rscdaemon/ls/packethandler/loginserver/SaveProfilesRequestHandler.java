/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.loginserver.ReplyPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class SaveProfilesRequestHandler
implements PacketHandler {
    private ReplyPacketBuilder builder = new ReplyPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        World world = (World)session.getAttachment();
        System.out.println("World " + world.getID() + " requested we save all profiles");
        boolean success = true;
        this.builder.setUID(uID);
        this.builder.setSuccess(success);
        LSPacket packet = this.builder.getPacket();
        if (packet != null) {
            session.write((Object)packet);
        }
    }
}

