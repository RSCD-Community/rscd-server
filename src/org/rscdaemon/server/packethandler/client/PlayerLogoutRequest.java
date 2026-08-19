/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class PlayerLogoutRequest
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.getLocation().inWilderness() && System.currentTimeMillis() - player.getLastMoved() < 7000L) {
            player.getActionSender().sendMessage("@gry@ Please stand still for 7 seconds before logging out in the wilderness");
            player.getActionSender().sendCantLogout();
        } else if (player.canLogout()) {
            player.destroy(true);
        } else {
            player.getActionSender().sendCantLogout();
        }
    }
}

