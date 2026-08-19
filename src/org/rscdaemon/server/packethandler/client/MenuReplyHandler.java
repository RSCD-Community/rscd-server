/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class MenuReplyHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        MenuHandler menuHandler = player.getMenuHandler();
        if (menuHandler == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        byte option = p.readByte();
        String reply = menuHandler.getOption(option);
        player.resetMenuHandler();
        if (reply == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        menuHandler.handleReply(option, reply);
    }
}

