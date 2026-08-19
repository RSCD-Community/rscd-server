/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.net.Handler;
import org.rscdaemon.ls.LoginEngine;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;

public class LSConnectionHandler
implements Handler {
    private LoginEngine engine;

    public LSConnectionHandler(LoginEngine engine) {
        this.engine = engine;
    }

    public void exceptionCaught(Connection session, Throwable cause) {
    }

    public void messageReceived(Connection session, Object message) {
        if (session.isClosing()) {
            return;
        }
        this.engine.getLSPacketQueue().add((LSPacket)message);
    }

    public void messageSent(Connection session, Object message) {
    }

    public void connectionClosed(Connection session) {
        World world = (World)session.getAttachment();
        if (world != null) {
            Server.getServer().setIdle(world, true);
            world.clearPlayers();
            Server.error("Connection to world " + world.getID() + " lost!");
        }
    }

    public void connectionIdle(Connection session) {
    }

    public void connectionOpened(Connection session) {
    }
}

