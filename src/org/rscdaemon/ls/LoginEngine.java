/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls;

import java.util.List;
import java.util.TreeMap;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.FPacket;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.PacketQueue;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.packethandler.PacketHandlerDef;
import org.rscdaemon.ls.util.PersistenceManager;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class LoginEngine
extends Thread {
    private Server server;
    private PacketQueue<LSPacket> LSPacketQueue;
    private PacketQueue<FPacket> FPacketQueue;
    private boolean running = true;
    private TreeMap<Integer, PacketHandler> LSPacketHandlers = new TreeMap();
    private TreeMap<Integer, PacketHandler> FPacketHandlers = new TreeMap();
    private TreeMap<Long, PacketHandler> uniqueHandlers = new TreeMap();

    public LoginEngine(Server server) {
        this.server = server;
        this.LSPacketQueue = new PacketQueue();
        this.FPacketQueue = new PacketQueue();
        this.loadPacketHandlers();
    }

    @Override
    public void run() {
        System.out.println("LoginEngine now running");
        while (this.running) {
            try {
                Thread.sleep(50L);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            this.processIncomingPackets();
            this.processOutgoingPackets();
        }
    }

    public void setHandler(long uID, PacketHandler handler) {
        this.uniqueHandlers.put(uID, handler);
    }

    public PacketQueue<LSPacket> getLSPacketQueue() {
        return this.LSPacketQueue;
    }

    public PacketQueue<FPacket> getFPacketQueue() {
        return this.FPacketQueue;
    }

    public void processOutgoingPackets() {
        for (World w : this.server.getWorlds()) {
            List<LSPacket> packets = w.getActionSender().getPackets();
            for (LSPacket packet : packets) {
                w.getSession().write((Object)packet);
            }
            w.getActionSender().clearPackets();
        }
    }

    /*
     * A handler that will not load no longer kills the process -- see
     * Server.error() -- so it has to say which packets it just left unhandled,
     * or the loss is silent and shows up later as "Unhandled packet from
     * server: 27" with nothing connecting the two.
     */
    protected void loadPacketHandlers() {
        PacketHandler handler;
        Class<?> c;
        String className;
        PacketHandlerDef[] handlerDefs;
        for (PacketHandlerDef handlerDef : handlerDefs = (PacketHandlerDef[])PersistenceManager.load("LSPacketHandlers.xml")) {
            try {
                className = handlerDef.getClassName();
                c = Class.forName(className);
                if (c == null) continue;
                handler = (PacketHandler)c.newInstance();
                for (int packetID : handlerDef.getAssociatedPackets()) {
                    this.LSPacketHandlers.put(packetID, handler);
                }
            }
            catch (Exception e) {
                Server.error("Could not load LS handler " + handlerDef.getClassName() + " ("
                    + e + "); packets " + describePackets(handlerDef) + " are now unhandled");
            }
        }
        for (PacketHandlerDef handlerDef : handlerDefs = (PacketHandlerDef[])PersistenceManager.load("FPacketHandlers.xml")) {
            try {
                className = handlerDef.getClassName();
                c = Class.forName(className);
                if (c == null) continue;
                handler = (PacketHandler)c.newInstance();
                for (int packetID : handlerDef.getAssociatedPackets()) {
                    this.FPacketHandlers.put(packetID, handler);
                }
            }
            catch (Exception e) {
                Server.error("Could not load frontend handler " + handlerDef.getClassName() + " ("
                    + e + "); packets " + describePackets(handlerDef) + " are now unhandled");
            }
        }
    }

    private static String describePackets(PacketHandlerDef def) {
        StringBuilder ids = new StringBuilder();
        for (int packetID : def.getAssociatedPackets()) {
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(packetID);
        }
        return ids.length() == 0 ? "(none listed)" : ids.toString();
    }

    private void processIncomingPackets() {
        PacketHandler handler;
        for (LSPacket lSPacket : this.LSPacketQueue.getPackets()) {
            handler = this.uniqueHandlers.get(lSPacket.getUID());
            if (handler != null || (handler = this.LSPacketHandlers.get(lSPacket.getID())) != null) {
                try {
                    handler.handlePacket(lSPacket, lSPacket.getSession());
                    this.uniqueHandlers.remove(lSPacket.getUID());
                }
                catch (Exception e) {
                    Server.error("Exception with p[" + lSPacket.getID() + "]: " + e);
                }
                continue;
            }
            Server.error("Unhandled packet from server: " + lSPacket.getID());
        }
        for (FPacket fPacket : this.FPacketQueue.getPackets()) {
            handler = this.FPacketHandlers.get(fPacket.getID());
            if (handler != null) {
                try {
                    handler.handlePacket(fPacket, fPacket.getSession());
                }
                catch (Exception e) {
                    Server.error("Exception with p[" + fPacket.getID() + "]: " + e);
                }
                continue;
            }
            Server.error("Unhandled packet from frontend: " + fPacket.getID());
        }
    }
}

