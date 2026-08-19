/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class FriendLogin
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        Player player = world.getPlayer(p.readLong());
        if (player == null) {
            return;
        }
        long friend = p.readLong();
        if (player.isFriendsWith(friend)) {
            player.getActionSender().sendFriendUpdate(friend, p.readShort());
        }
    }
}

