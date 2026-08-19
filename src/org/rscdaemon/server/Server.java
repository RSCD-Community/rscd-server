/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.rscdaemon.server.GameEngine;
import org.rscdaemon.server.LoginConnector;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.RSCConnectionHandler;
import org.rscdaemon.server.net.websocket.WebSocketBridge;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.Heartbeat;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.ServerKey;
import org.rscdaemon.server.codec.RSCCodec;
import org.rscdaemon.server.util.net.Acceptor;

public class Server {
    private static final World world = World.getWorld();
    private GameEngine engine;
    private Acceptor acceptor;
    private Acceptor wsAcceptor;
    private DelayedEvent updateEvent;
    private LoginConnector connector;
    private boolean running = true;

    public LoginConnector getLoginConnector() {
        return this.connector;
    }

    public boolean running() {
        return this.running;
    }

    public boolean shutdownForUpdate() {
        if (this.updateEvent != null) {
            return false;
        }
        this.updateEvent = new SingleEvent(null, 65000){

            public void action() {
                Server.this.kill();
            }
        };
        world.getDelayedEventHandler().add(this.updateEvent);
        return true;
    }

    public int timeTillShutdown() {
        if (this.updateEvent == null) {
            return -1;
        }
        return this.updateEvent.timeTillNextRun();
    }

    public Server() {
        world.setServer(this);
        this.installShutdownHook();
        try {
            this.connector = new LoginConnector();
            this.engine = new GameEngine();
            this.engine.start();
            while (!this.connector.isRegistered()) {
                Thread.sleep(100L);
            }
            /* The party hall calendar: first fetch plus the once-a-minute
               herald. After registration on purpose -- its packets ride the
               LS link that just came up. */
            org.rscdaemon.server.model.PartySchedule.install(world);
            // And the cannons' trickle, which needs the world data loaded.
            org.rscdaemon.server.model.PartyCannon.install(world);
            /* setDisconnectOnUnbind(true) and setReuseAddress(true) were the
               only two things configured on MINA's acceptor; the first is what
               unbind() does here by definition, and the second is set on the
               server socket. So the configuration object had nothing left to
               carry. */
            this.acceptor = new Acceptor(new RSCConnectionHandler(this.engine), new RSCCodec());
            this.acceptor.bind(new InetSocketAddress(Config.SERVER_IP, Config.SERVER_PORT));
            /* The browser door. Same handler, same codec: once the bridge has
               unwrapped the WebSocket framing, a browser player IS a TCP
               player -- nothing downstream can tell them apart. ws_port = 0
               turns the listener off. */
            if (Config.WS_PORT > 0) {
                this.wsAcceptor = WebSocketBridge.bind(new RSCConnectionHandler(this.engine),
                        new RSCCodec(), Config.SERVER_IP, Config.WS_PORT);
            }
            // After the binds, so the registry never gets told about a world
            // whose port did not come up.
            Heartbeat.start();
        }
        catch (Exception e) {
            /* Startup, so this genuinely is fatal -- there is no acceptor, no
               engine, or no registration, and nobody is connected to drop.
               It used to be Logger.error(e), which exited only as a side
               effect of logging; now that error() just logs, the intent has to
               be stated. */
            Logger.fatal("the server could not start", e);
        }
    }

    /**
     * Save everybody when the process is asked to stop.
     *
     * Without this, `systemctl restart` -- or any other SIGTERM, which is how
     * a deploy ends -- took the JVM down with no save at all, so every player
     * online lost whatever they had done since the last autosave. kill() only
     * ever ran from the ::update command and the login server's shutdown
     * packet, which is not how the server actually gets restarted.
     *
     * A shutdown hook is the only place this can go: the JVM runs hooks to
     * completion on SIGTERM, and will not exit until they return. That last
     * part matters more than it looks -- the save packets are handed to a
     * writer thread, so returning too early would exit before they left the
     * socket. Hence the drain wait.
     */
    private void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread("rscd-shutdown"){

            public void run() {
                Server.this.shutdown();
            }
        });
    }

    private synchronized void shutdown() {
        if (this.running) {
            this.running = false;
            if (this.engine != null) {
                /* Stop the game loop before saving off it. emptyWorld walks
                   the player list, and doing that from this thread while the
                   loop is still mutating it is how you get a
                   ConcurrentModificationException in the one code path that
                   has to work. The loop sleeps 50ms a tick, so it is gone
                   well inside the wait below. */
                this.engine.kill();
                try {
                    Thread.sleep(200L);
                }
                catch (InterruptedException interruptedException) {
                    // empty catch block
                }
                Logger.print("Saving " + world.getPlayers().size() + " player(s) before shutdown");
                this.engine.emptyWorld();
            }
            if (this.connector != null) {
                this.connector.kill();
                /* The game loop is what normally flushes this queue, and it
                   has just stopped, so the unregister-world packet kill()
                   posts would otherwise sit in it and the registry would go on
                   listing a world that is gone. */
                this.connector.sendQueuedPackets();
            }
        }
        this.drainLoginConnection();
        this.unbind();
    }

    /**
     * Wait for the queued save packets to actually reach the login server.
     *
     * Bounded, because a hung or dead login connection must not stop the
     * process from exiting -- systemd would eventually SIGKILL us anyway, and
     * a save that cannot be delivered is not going to become deliverable.
     */
    private void drainLoginConnection() {
        if (this.connector == null || this.connector.getSession() == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            int pending = this.connector.getSession().pendingWrites();
            if (pending == 0) {
                return;
            }
            try {
                Thread.sleep(50L);
            }
            catch (InterruptedException e) {
                return;
            }
        }
        Logger.print("Gave up waiting for " + this.connector.getSession().pendingWrites()
                + " packet(s) to reach the login server");
    }

    public GameEngine getEngine() {
        return this.engine;
    }

    public boolean isInitialized() {
        return this.engine != null && this.connector != null;
    }

    public void kill() {
        Logger.print("RSCD Server shutting down...");
        this.running = false;
        this.engine.emptyWorld();
        this.connector.kill();
    }

    public void unbind() {
        try {
            if (this.acceptor != null) {
                this.acceptor.unbind();
            }
        }
        catch (Exception e) {
            Logger.error(e);
        }
        try {
            if (this.wsAcceptor != null) {
                this.wsAcceptor.unbind();
            }
        }
        catch (Exception e) {
            Logger.error(e);
        }
    }

    public static void main(String[] args) throws IOException {
        String configFile = "conf/server/Conf.xml";
        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.isFile()) {
                /* The path as given, not f.getName(). getName() threw the
                   directory away, so a config anywhere but the working
                   directory silently failed to load and every setting fell
                   back to the values compiled into Config -- including the
                   change-me server key, which is the one setting the server is
                   supposed to refuse to start on. It only ever looked like it
                   worked because world.xml sat in the launch directory, where
                   the stripped name happened to resolve to the same file.
                   The login server had the identical bug; see ls.Server. */
                configFile = f.getPath();
            } else {
                Logger.print("No config file at '" + args[0] + "'; trying "
                        + configFile + " instead");
            }
        }
        Logger.print("RSCD Server starting up...");
        Config.initConfig(configFile);
        // Before anything binds or connects: a server without an identity of
        // its own does not get to join a world list under somebody else's.
        if (ServerKey.ensure(Config.CONFIG_FILE) == null) {
            return;
        }
        new Server();
    }
}

