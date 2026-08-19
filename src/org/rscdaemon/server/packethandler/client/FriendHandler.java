/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.loginserver.MiscPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;

public class FriendHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private MiscPacketBuilder loginSender = world.getServer().getLoginConnector().getActionSender();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        long user = player.getUsernameHash();
        long friend = p.readLong();
        switch (pID) {
            case 168: {
                if (player.friendCount() >= 200) {
                    player.getActionSender().sendMessage("@gry@ Your friend list is too full");
                    return;
                }
                this.loginSender.addFriend(user, friend);
                player.addFriend(friend, 0);
                break;
            }
            case 52: {
                this.loginSender.removeFriend(user, friend);
                player.removeFriend(friend);
                break;
            }
            case 25: {
                if (player.ignoreCount() >= 200) {
                    player.getActionSender().sendMessage("@gry@ Your ignore list is too full");
                    return;
                }
                this.loginSender.addIgnore(user, friend);
                player.addIgnore(friend);
                break;
            }
            case 108: {
                this.loginSender.removeIgnore(user, friend);
                player.removeIgnore(friend);
                break;
            }
            case 254: {
                this.loginSender.sendPM(user, friend, player.isPMod(), p.getRemainingData());
            }
        }
    }
}

