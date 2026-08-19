/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.loginserver.GameSettingUpdatePacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;

public class GameSettingHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private GameSettingUpdatePacketBuilder builder = new GameSettingUpdatePacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        byte idx = p.readByte();
        if (idx < 0 || idx > 6) {
            player.setSuspiciousPlayer(true);
            return;
        }
        boolean on = p.readByte() == 1;
        player.setGameSetting(idx, on);
        this.builder.setPlayer(player);
        this.builder.setIndex(idx);
        this.builder.setOn(on);
        LSPacket packet = this.builder.getPacket();
        if (packet != null) {
            world.getServer().getLoginConnector().getSession().write((Object)packet);
        }
    }
}

