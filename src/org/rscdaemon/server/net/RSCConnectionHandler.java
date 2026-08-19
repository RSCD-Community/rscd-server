/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.net;

import java.net.InetSocketAddress;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.net.Handler;
import org.rscdaemon.server.GameEngine;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.PacketQueue;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.util.Logger;

public class RSCConnectionHandler
implements Handler {
    private PacketQueue<RSCPacket> packets;

    public RSCConnectionHandler(GameEngine engine) {
        this.packets = engine.getPacketQueue();
    }

    /*
     * This was empty, and that is how two decoder bugs stayed invisible for
     * years: a NegativeArraySizeException out of the codec landed here, was
     * swallowed, and left the connection running on a desynced stream. The
     * player saw actions go missing; the log said nothing at all.
     *
     * An IOException here is just a client that vanished -- a crash, a closed
     * laptop, a dropped connection -- and is far too common to log. Anything
     * else is a defect in our own code and gets recorded.
     */
    public void exceptionCaught(Connection session, Throwable cause) {
        if (!(cause instanceof java.io.IOException)) {
            /* Null when the failure was not on any one connection -- the
               acceptor reports a broken listen socket that way, and it has no
               session to name. Dereferencing it turned a logged accept failure
               into an unlogged NullPointerException on the way to logging it. */
            Logger.error("Connection error from "
                    + (session == null ? "the listener" : String.valueOf(session.getRemoteAddress()))
                    + ": " + cause);
            Logger.error(cause);
        }
    }

    public void messageReceived(Connection session, Object message) {
        Player player = (Player)session.getAttachment();
        if (session.isClosing() || player.destroyed()) {
            return;
        }
        RSCPacket p = (RSCPacket)message;
        player.addPacket(p);
        this.packets.add(p);
    }

    public void messageSent(Connection session, Object message) {
    }

    public void connectionClosed(Connection session) {
        Player player = (Player)session.getAttachment();
        if (!player.destroyed()) {
            player.destroy(false);
        }
    }

    /*
     * Thirty seconds with nothing sent or received in either direction.
     *
     * For a player who is in the world, destroy() is the whole job: the socket
     * is closed by ClientUpdater.sendQueuedPackets on a later tick, once the
     * queued logout packet has actually gone out, and destroy(false) may
     * legitimately defer while the player is still in combat.
     *
     * A connection that never reached the world has no such owner. Nothing
     * walks it, so marking it destroyed accomplishes nothing and the socket
     * survives until the process does -- which is what a port scan, an
     * abandoned login screen, or a client that connected and then went away
     * used to leave behind, one thread pair at a time.
     */
    public void connectionIdle(Connection session) {
        Player player = (Player)session.getAttachment();
        if (!player.destroyed()) {
            player.destroy(false);
        }
        if (!player.loggedIn()) {
            session.close();
        }
    }

    /*
     * sessionCreated and sessionOpened were separate because MINA installed the
     * codec in the first and the application state in the second. The codec now
     * belongs to the acceptor, so there is one hook and one place that decides
     * what a new connection is.
     */
    public void connectionOpened(Connection connection) {
        connection.setAttachment(new Player(connection));
        connection.setIdleTime(30);
        connection.setWriteTimeout(30);
        Logger.connection("Connection from: "
            + ((InetSocketAddress)connection.getRemoteAddress()).getAddress().getHostAddress());
    }
}

