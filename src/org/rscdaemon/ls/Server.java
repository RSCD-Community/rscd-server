/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.TreeMap;
import org.rscdaemon.ls.LoginEngine;
import org.rscdaemon.ls.model.PlayerSave;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.DatabaseConnection;
import org.rscdaemon.ls.net.FConnectionHandler;
import org.rscdaemon.ls.net.LSConnectionHandler;
import org.rscdaemon.ls.util.Config;
import org.rscdaemon.ls.codec.FCodec;
import org.rscdaemon.ls.codec.LSCodec;
import org.rscdaemon.server.util.net.Acceptor;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Handler;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Server {
    public static DatabaseConnection db;
    private LoginEngine engine;
    private Acceptor serverAcceptor;
    private Acceptor frontendAcceptor;
    private static Server server;
    private TreeMap<Integer, World> worlds = new TreeMap();
    private TreeMap<Integer, World> idleWorlds = new TreeMap();

    public static Server getServer() {
        if (server == null) {
            server = new Server();
        }
        return server;
    }

    private Server() {
        try {
            this.engine = new LoginEngine(this);
            this.engine.start();
            this.serverAcceptor = this.createListener(Config.LS_IP, Config.LS_PORT, new LSConnectionHandler(this.engine), new LSCodec());
            this.frontendAcceptor = this.createListener(Config.QUERY_IP, Config.QUERY_PORT, new FConnectionHandler(this.engine), new FCodec());
        }
        catch (IOException e) {
            /* Genuinely fatal: with no listener bound there is nothing to
               serve, and main() returns immediately after this, so without
               the exit the process would sit there with a LoginEngine thread
               spinning and no way in. */
            Server.fatal("could not bind the login server on " + Config.LS_IP + ":" + Config.LS_PORT
                + " or the frontend on " + Config.QUERY_IP + ":" + Config.QUERY_PORT
                + " -- is another copy already running?", e);
        }
    }

    /* The codec now belongs to the listener rather than being installed per
       connection by the handler, so it is passed in alongside. */
    private Acceptor createListener(String ip, int port, Handler handler, Codec codec) throws IOException {
        Acceptor acceptor = new Acceptor(handler, codec);
        acceptor.bind(new InetSocketAddress(ip, port));
        return acceptor;
    }

    public PlayerSave findSave(long user, World world) {
        PlayerSave save = null;
        save = PlayerSave.loadPlayer(user);
        return save;
    }

    public World findWorld(long user) {
        for (World w : this.getWorlds()) {
            if (!w.hasPlayer(user)) continue;
            return w;
        }
        return null;
    }

    public World getIdleWorld(int id) {
        return this.idleWorlds.get(id);
    }

    public void setIdle(World world, boolean idle) {
        if (idle) {
            this.worlds.remove(world.getID());
            this.idleWorlds.put(world.getID(), world);
        } else {
            this.idleWorlds.remove(world.getID());
            this.worlds.put(world.getID(), world);
        }
    }

    public Collection<World> getWorlds() {
        return this.worlds.values();
    }

    public World getWorld(int id) {
        if (id < 0) {
            return null;
        }
        return this.worlds.get(id);
    }

    public boolean isRegistered(World world) {
        return this.getWorld(world.getID()) != null;
    }

    public boolean registerWorld(World world) {
        int id = world.getID();
        if (id < 0 || this.getWorld(id) != null) {
            return false;
        }
        this.worlds.put(id, world);
        return true;
    }

    public boolean unregisterWorld(World world) {
        int id = world.getID();
        if (id < 0) {
            return false;
        }
        if (this.getWorld(id) != null) {
            this.worlds.remove(id);
            return true;
        }
        if (this.getIdleWorld(id) != null) {
            this.idleWorlds.remove(id);
            return true;
        }
        return false;
    }

    public LoginEngine getEngine() {
        return this.engine;
    }

    public void kill() {
        try {
            this.serverAcceptor.unbind();
            this.frontendAcceptor.unbind();
            db.close();
        }
        catch (Exception e) {
            Server.error(e);
        }
    }

    /**
     * Logs. Does not exit.
     *
     * The Exception overload used to call System.exit(1), which meant any one
     * of the forty-odd call sites could take the whole login server down: a
     * malformed packet from one client, a codec error on one connection, a
     * failed save for one player, a handler class that would not load. Every
     * player on every world was dropped because one of them sent something the
     * decoder disliked. A login server that logs a bad packet is strictly
     * better than one that dies on it.
     *
     * Startup failures that genuinely leave nothing to serve call fatal()
     * instead, so exiting is a deliberate decision at the call site rather
     * than a side effect of logging.
     */
    public static void error(Object o) {
        if (o instanceof Throwable) {
            ((Throwable)o).printStackTrace();
            return;
        }
        System.err.println(o.toString());
    }

    /**
     * Logs and exits. For startup failures only -- no port to listen on, no
     * database to authenticate against -- where the process has nothing left
     * to do and failing loudly is more diagnosable than limping. Nothing is
     * connected yet at that point, so there is no one to drop.
     */
    public static void fatal(String what, Throwable cause) {
        System.err.println("FATAL: " + what);
        if (cause != null) {
            cause.printStackTrace();
        }
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        String configFile = "conf/ls/Conf.xml";
        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.isFile()) {
                /* The path as given, not f.getName(). getName() threw the
                   directory away, so a config anywhere but the working
                   directory silently failed to load and every setting fell
                   back to the values compiled into Config -- including the
                   dead 2011 MySQL credentials. It only ever looked like it
                   worked because ls.conf sits in the launch directory, where
                   the stripped name happens to resolve to the same file. */
                configFile = f.getPath();
            } else {
                Server.error("No config file at '" + args[0] + "'; trying " + configFile + " instead");
            }
        }
        System.out.println("Login Server starting up...");
        Config.initConfig(configFile);
        db = new DatabaseConnection();
        System.out.println("Connected to MySQL");
        Server.getServer();
    }
}

