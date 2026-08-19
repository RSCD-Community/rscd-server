/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class FollowRequest
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        Player affectedPlayer = world.getPlayer(p.readShort());
        if (affectedPlayer == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        player.setFollowing(affectedPlayer, 1);
        player.getActionSender().sendMessage("@pnk@ You follow close behind " + affectedPlayer.getUsername());
        affectedPlayer.getActionSender().sendMessage("@pnk@ " + player.getUsername() + " has decided to follow you.");
    }
}

