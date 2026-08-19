/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.util.TreeMap;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.ClientUpdater;
import org.rscdaemon.server.DelayedEventHandler;
import org.rscdaemon.server.LoginConnector;
import org.rscdaemon.server.ObjectXMLBuilder;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.PacketQueue;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.packethandler.PacketHandlerDef;
import org.rscdaemon.server.quest.QuestLoader;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.PersistenceManager;

public final class GameEngine
extends Thread {
    /** How stale a player's saved profile is allowed to get, in milliseconds. */
    public static final int SAVE_INTERVAL = 60000;
    private static final World world = World.getWorld();
    private PacketQueue<RSCPacket> packetQueue;
    private boolean running = true;
    private TreeMap<Integer, PacketHandler> packetHandlers = new TreeMap();
    private ClientUpdater clientUpdater = new ClientUpdater();
    private DelayedEventHandler eventHandler = new DelayedEventHandler();
    private long lastSentClientUpdate = 0L;

    public GameEngine() {
        this.packetQueue = new PacketQueue();
        QuestLoader.initClasses();
        this.loadPacketHandlers();
        for (Shop shop : world.getShops()) {
            shop.initRestock();
        }
    }

    public void run() {
        Logger.print("GameEngine now running");
        ObjectXMLBuilder.InitiateBuilder();
        this.eventHandler.add(new DelayedEvent(null, 5000){

            /*
             * The autosave sweep.
             *
             * It used to be one event on a 900000ms timer that saved everybody
             * at once, which meant a player could lose up to fifteen minutes of
             * progress to a crash or a kill -- in practice the only saves that
             * ever landed were the one on logout and the one on shutdown.
             *
             * So the schedule is per player now, not global. This runs often
             * and saves only the players whose last write is older than
             * SAVE_INTERVAL, which bounds the loss at a minute and, because
             * people log in at different moments, spreads the writes out
             * instead of firing every player's eight queries at the login
             * server in the same tick.
             *
             * saveProfiles() is not called here any more. It never saved
             * anything -- the login server's handler for it only logs the
             * request and replies true -- and at this cadence it would have
             * done nothing but fill the log.
             */
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : world.getPlayers()) {
                    if (now - p.getLastSaved() >= (long)SAVE_INTERVAL) {
                        p.save();
                    }
                }
            }
        });
        while (this.running) {
            try {
                Thread.sleep(50L);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            try {
                this.processLoginServer();
                this.processIncomingPackets();
                this.processEvents();
                this.processClients();
            }
            catch (Throwable t) {
                /* Last line of defence. Anything thrown out of a tick used to
                   end this thread, and the thread ending is the world ending:
                   every session dropped, every unsaved minute gone. A tick
                   that goes wrong is worth a log entry and the next tick, not
                   the server. */
                Logger.error("Uncaught exception in the game loop");
                Logger.error(t);
            }
        }
    }

    public void emptyWorld() {
        for (Player p : world.getPlayers()) {
            p.save();
            p.getActionSender().sendLogout();
        }
        world.getServer().getLoginConnector().getActionSender().saveProfiles();
    }

    public void kill() {
        Logger.print("Terminating GameEngine");
        this.running = false;
    }

    public void processLoginServer() {
        LoginConnector connector = world.getServer().getLoginConnector();
        if (connector != null) {
            connector.processIncomingPackets();
            connector.sendQueuedPackets();
        }
    }

    private void processIncomingPackets() {
        for (RSCPacket p : this.packetQueue.getPackets()) {
            Connection session = p.getSession();
            Player player = (Player)session.getAttachment();
            player.ping();
            PacketHandler handler = this.packetHandlers.get(p.getID());
            if (handler != null) {
                try {
                    handler.handlePacket(p, session);
                }
                catch (Exception e) {
                    Logger.error("Exception with p[" + p.getID() + "] from " + player.getUsername() + " [" + player.getCurrentIP() + "]: " + e.getMessage());
                    player.getActionSender().sendLogout();
                    player.destroy(false);
                }
                continue;
            }
            Logger.error("Unhandled packet from " + player.getCurrentIP() + ": " + p.getID());
        }
    }

    private void processEvents() {
        this.eventHandler.doEvents();
    }

    private void processClients() {
        this.clientUpdater.sendQueuedPackets();
        long now = System.currentTimeMillis();
        if (now - this.lastSentClientUpdate >= 600L) {
            this.lastSentClientUpdate = now;
            this.clientUpdater.updateClients();
        }
    }

    public PacketQueue getPacketQueue() {
        return this.packetQueue;
    }

    protected void loadPacketHandlers() {
        PacketHandlerDef[] handlerDefs;
        for (PacketHandlerDef handlerDef : handlerDefs = (PacketHandlerDef[])PersistenceManager.load("PacketHandlers.xml")) {
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
}

