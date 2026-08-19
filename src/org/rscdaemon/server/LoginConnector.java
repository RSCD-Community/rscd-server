/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.TreeMap;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.net.LSConnectionHandler;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.PacketQueue;
import org.rscdaemon.server.packetbuilder.loginserver.MiscPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.packethandler.PacketHandlerDef;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.codec.LSCodec;
import org.rscdaemon.server.util.net.Connector;
import org.rscdaemon.server.util.net.Handler;
import org.rscdaemon.server.util.PersistenceManager;

public class LoginConnector {
    private PacketQueue<LSPacket> packetQueue;
    private TreeMap<Integer, PacketHandler> packetHandlers = new TreeMap();
    private Connection session;
    private boolean running = true;
    private boolean registered = false;
    private TreeMap<Long, PacketHandler> uniqueHandlers = new TreeMap();
    private MiscPacketBuilder actionSender = new MiscPacketBuilder(this);
    private Handler connectionHandler = new LSConnectionHandler(this);
    private int connectionAttempts = 0;

    public LoginConnector() {
        this.packetQueue = new PacketQueue();
        this.loadPacketHandlers();
        this.reconnect();
    }

    /**
     * Connects to the login server, and keeps trying until it succeeds.
     *
     * Rewritten on 2026-08-02. It used to call System.exit(1) after a hundred
     * failures, so a login server that went away for a few minutes -- a
     * restart, a deploy, a network blip -- took the whole game world down with
     * every player on it, even though the game server was otherwise perfectly
     * able to keep running. It also retried by recursing into itself, spending
     * a hundred stack frames on what is a loop.
     *
     * Now it loops, backs off, and RESETS rather than gives up: the attempt
     * counter clears on every success, and there is no failure count at which
     * the process dies. A dependency being temporarily unreachable is a thing
     * to wait out, not a reason to drop everybody.
     *
     * Backoff grows to ten seconds and stays there, so a login server that is
     * down for an hour costs a few hundred log lines rather than a few hundred
     * thousand, and comes back within ten seconds of returning.
     */
    private static final long BACKOFF_STEP_MS = 500L;
    private static final long BACKOFF_MAX_MS = 10000L;

    public boolean reconnect() {
        while (this.running) {
            this.connectionAttempts++;

            try {
                Logger.print("Attempting to connect to LS (attempt " + this.connectionAttempts + ")");
                /* MINA returned a ConnectFuture that this code immediately
                   blocked on with join(3000) and then tested. connect() simply
                   does that: it returns the live Connection or throws.
                   TCP_NODELAY is set by the transport for every connection. */
                Connector conn = new Connector(this.connectionHandler, new LSCodec());
                Connection connected = conn.connect(
                    new InetSocketAddress(Config.LS_IP, Config.LS_PORT), 3000);

                if (connected != null && connected.isConnected()) {
                    this.session = connected;
                    Logger.print("Registering world (" + Config.SERVER_NUM + ") with LS");
                    this.actionSender.registerWorld();
                    this.connectionAttempts = 0;   // reset on success
                    return true;
                }
            }
            catch (Exception e) {
                Logger.print("Error connecting to LS: " + e.getMessage());
            }

            long wait = Math.min(BACKOFF_MAX_MS, this.connectionAttempts * BACKOFF_STEP_MS);

            try {
                Thread.sleep(wait);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /* Only reached once kill() has cleared running -- the server is going
           down anyway, so stop trying. */
        return false;
    }

    public Connection getSession() {
        return this.session;
    }

    public void kill() {
        this.running = false;
        Logger.print("Unregistering world (" + Config.SERVER_NUM + ") with LS");
        this.actionSender.unregisterWorld();
    }

    public MiscPacketBuilder getActionSender() {
        return this.actionSender;
    }

    public boolean running() {
        return this.running;
    }

    public boolean isRegistered() {
        return this.registered;
    }

    public PacketQueue getPacketQueue() {
        return this.packetQueue;
    }

    public void setRegistered(boolean registered) {
        if (registered) {
            this.registered = true;
            Logger.print("World successfully registered with LS");
        } else {
            Logger.error(new Exception("Error registering world"));
        }
    }

    private void loadPacketHandlers() {
        PacketHandlerDef[] handlerDefs;
        for (PacketHandlerDef handlerDef : handlerDefs = (PacketHandlerDef[])PersistenceManager.load("LSPacketHandlers.xml")) {
            try {
                String className = handlerDef.getClassName();
                Class<?> c = Class.forName(className);
                if (c == null) continue;
                PacketHandler handler = (PacketHandler)c.newInstance();
                for (int packetID : handlerDef.getAssociatedPackets()) {
                    this.packetHandlers.put(packetID, handler);
                }
            }
            catch (Exception e) {
                Logger.error(e);
            }
        }
    }

    public void setHandler(long uID, PacketHandler handler) {
        this.uniqueHandlers.put(uID, handler);
    }

    public void processIncomingPackets() {
        for (LSPacket p : this.packetQueue.getPackets()) {
            PacketHandler handler = this.uniqueHandlers.get(p.getUID());
            if (handler != null || (handler = this.packetHandlers.get(p.getID())) != null) {
                try {
                    handler.handlePacket(p, this.session);
                }
                catch (Exception e) {
                    // getMessage() alone throws away the one thing that makes a
                    // handler fault diagnosable. "Index 2 out of bounds for
                    // length 1" names no class, no line and no field, so it was
                    // impossible to tell which of the ~40 reads in PlayerLogin
                    // -- or which array underneath them -- had actually failed.
                    Logger.fatal("Exception with p[" + p.getID() + "] from LOGIN_SERVER", e);
                }
                finally {
                    // Was inside the try, after the call, so a handler that
                    // threw stayed registered forever. Unique handlers are keyed
                    // on the request UID and are one-shot by design; leaving a
                    // dead one behind means a later reply that happens to reuse
                    // the UID gets delivered to it instead of falling through to
                    // the ID-based handler.
                    this.uniqueHandlers.remove(p.getUID());
                }
                continue;
            }
            Logger.error("Unhandled packet from LS: " + p.getID());
        }
    }

    public void sendQueuedPackets() {
        List<LSPacket> packets = this.actionSender.getPackets();
        for (LSPacket packet : packets) {
            this.session.write((Object)packet);
        }
        this.actionSender.clearPackets();
    }
}

