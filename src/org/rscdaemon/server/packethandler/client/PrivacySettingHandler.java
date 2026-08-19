/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.loginserver.PrivacySettingUpdatePacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;

public class PrivacySettingHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private PrivacySettingUpdatePacketBuilder builder = new PrivacySettingUpdatePacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        int i;
        Player player = (Player)session.getAttachment();
        boolean[] newSettings = new boolean[4];
        for (i = 0; i < 4; ++i) {
            newSettings[i] = p.readByte() == 1;
        }
        this.builder.setPlayer(player);
        for (i = 0; i < 4; ++i) {
            this.builder.setIndex(i);
            if (newSettings[i] && !player.getPrivacySetting(i)) {
                this.builder.setOn(true);
            } else {
                if (newSettings[i] || !player.getPrivacySetting(i)) continue;
                this.builder.setOn(false);
            }
            LSPacket packet = this.builder.getPacket();
            if (packet == null) continue;
            world.getServer().getLoginConnector().getSession().write((Object)packet);
        }
        for (i = 0; i < 4; ++i) {
            player.setPrivacySetting(i, newSettings[i]);
        }
    }
}

