/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.loginserver.WorldRegisteredPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.util.DataConversions;

public class RegisterWorld
implements PacketHandler {
    private WorldRegisteredPacketBuilder builder = new WorldRegisteredPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        this.builder.setUID(uID);
        this.builder.setSuccess(false);
        Server server = Server.getServer();
        if (((LSPacket)p).getID() == 1) {
            short id = p.readShort();
            if (server.getWorld(id) == null) {
                World world = server.getIdleWorld(id);
                if (world == null) {
                    world = new World(id, session);
                    server.registerWorld(world);
                    System.out.println("Registering world: " + id);
                } else {
                    world.setSession(session);
                    server.setIdle(world, false);
                    System.out.println("Reattached to world " + id);
                }
                // Wipe any presence rows left over from a previous run before
                // trusting the list the game server is about to send. The
                // disconnect path clears them normally, but it never runs if
                // the login server itself died, and stale rows would otherwise
                // leave accounts looking permanently online.
                world.clearPresence();
                int playerCount = p.readShort();
                for (int i = 0; i < playerCount; ++i) {
                    world.registerPlayer(p.readLong(), DataConversions.IPToString(p.readLong()));
                }
                session.setAttachment((Object)world);
                this.builder.setSuccess(true);
            }
        } else {
            World world = (World)session.getAttachment();
            /* An explicit unregister only removes the World object from the
               server's maps -- it never used to touch rscd_players.world/
               .online for whoever was still in it, which is a no-op today
               (registerWorld's own clearPresence catches it on reconnect) but
               leaves the DB looking permanently online for however long the
               world takes to come back, or forever if it does not. Clearing
               here is the same call the disconnect fallback already makes,
               just on the clean-shutdown path instead of the crash path. */
            world.clearPlayers();
            server.unregisterWorld(world);
            System.out.println("UnRegistering world: " + world.getID());
            session.setAttachment(null);
            this.builder.setSuccess(true);
        }
        LSPacket temp = this.builder.getPacket();
        if (temp != null) {
            session.write((Object)temp);
        }
    }
}

