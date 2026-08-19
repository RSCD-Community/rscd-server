/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.net.Handler;
import org.rscdaemon.server.LoginConnector;
import org.rscdaemon.server.Server;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.util.Logger;

public class LSConnectionHandler
implements Handler {
    private LoginConnector connector;

    public LSConnectionHandler(LoginConnector connector) {
        this.connector = connector;
    }

    /* Empty for the same reason, with the same consequence -- see the note in
       RSCConnectionHandler. This link carries world registration and player
       counts, so a silently swallowed codec fault shows up as a world that
       mysteriously stops updating. */
    public void exceptionCaught(Connection session, Throwable cause) {
        if (!(cause instanceof java.io.IOException)) {
            // Null when the acceptor reports a failure that belongs to no
            // single connection. See RSCConnectionHandler.exceptionCaught.
            Logger.error("Login server connection error from "
                    + (session == null ? "the listener" : String.valueOf(session.getRemoteAddress()))
                    + ": " + cause);
            Logger.error(cause);
        }
    }

    public void messageReceived(Connection session, Object message) {
        if (session.isClosing()) {
            return;
        }
        LSPacket p = (LSPacket)message;
        this.connector.getPacketQueue().add(p);
    }

    public void messageSent(Connection session, Object message) {
    }

    public void connectionClosed(Connection session) {
        Server server = World.getWorld().getServer();
        if (server != null && server.running()) {
            Logger.error(new Exception("Lost connection the login server!"));
        }
    }

    public void connectionIdle(Connection session) {
    }

    public void connectionOpened(Connection session) {
    }
}

