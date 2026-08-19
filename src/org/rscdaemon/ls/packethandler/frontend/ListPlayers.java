/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.frontend;

import java.util.ArrayList;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.FPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.FPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;

public class ListPlayers
implements PacketHandler {
    private static final FPacketBuilder builder = new FPacketBuilder();

    public void handlePacket(Packet p, final Connection session) throws Exception {
        String[] params = ((FPacket)p).getParameters();
        try {
            final int worldID = Integer.parseInt(params[0]);
            System.out.println("Frontend requested player list for world " + worldID);
            World world = Server.getServer().getWorld(worldID);
            if (world == null) {
                throw new Exception("Unknown world");
            }
            world.getActionSender().playerListRequest(new PacketHandler(){

                public void handlePacket(Packet p, Connection s) throws Exception {
                    builder.setID(1);
                    ArrayList<String> params = new ArrayList<String>();
                    int count = p.readInt();
                    for (int c = 0; c < count; ++c) {
                        params.add(p.readLong() + "," + p.readShort() + "," + p.readShort() + "," + worldID);
                    }
                    builder.setParameters(params.toArray(new String[params.size()]));
                    session.write((Object)builder.toPacket());
                }
            });
        }
        catch (Exception e) {
            builder.setID(0);
            session.write((Object)builder.toPacket());
        }
    }
}

