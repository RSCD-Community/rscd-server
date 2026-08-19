/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.net.Handler;
import org.rscdaemon.ls.LoginEngine;
import org.rscdaemon.ls.net.FPacket;

public class FConnectionHandler
implements Handler {
    private LoginEngine engine;

    public FConnectionHandler(LoginEngine engine) {
        this.engine = engine;
    }

    public void exceptionCaught(Connection session, Throwable cause) {
    }

    public void messageReceived(Connection session, Object message) {
        if (session.isClosing()) {
            return;
        }
        this.engine.getFPacketQueue().add((FPacket)message);
    }

    public void messageSent(Connection session, Object message) {
        session.close();
    }

    public void connectionClosed(Connection session) {
    }

    public void connectionIdle(Connection session) {
    }

    public void connectionOpened(Connection session) {
    }
}

