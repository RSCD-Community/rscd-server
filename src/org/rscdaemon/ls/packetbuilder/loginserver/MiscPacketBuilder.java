/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packetbuilder.loginserver;

import java.util.ArrayList;
import java.util.List;
import org.rscdaemon.ls.LoginEngine;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.packetbuilder.LSPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class MiscPacketBuilder {
    private List<LSPacket> packets = new ArrayList<LSPacket>();
    private LoginEngine engine = Server.getServer().getEngine();

    public List<LSPacket> getPackets() {
        return this.packets;
    }

    public void clearPackets() {
        this.packets.clear();
    }

    public void requestStats(PacketHandler handler) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(1);
        s.setHandler(this.engine, handler);
        this.packets.add(s.toPacket());
    }

    public void playerListRequest(PacketHandler handler) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(2);
        s.setHandler(this.engine, handler);
        this.packets.add(s.toPacket());
    }

    public void shutdown() {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(3);
        this.packets.add(s.toPacket());
    }

    public void update(String reason) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(4);
        s.addBytes(reason.getBytes());
        this.packets.add(s.toPacket());
    }

    public void alert(String message) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(5);
        s.addBytes(message.getBytes());
        this.packets.add(s.toPacket());
    }

    public void alert(long user, String message) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(6);
        s.addLong(user);
        s.addBytes(message.getBytes());
        this.packets.add(s.toPacket());
    }

    public void logoutUser(long user) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(7);
        s.addLong(user);
        this.packets.add(s.toPacket());
    }

    public void requestReportInfo(long user, PacketHandler handler) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(8);
        s.addLong(user);
        s.setHandler(this.engine, handler);
        this.packets.add(s.toPacket());
    }

    public void requestPlayerInfo(long user, PacketHandler handler) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(9);
        s.addLong(user);
        s.setHandler(this.engine, handler);
        this.packets.add(s.toPacket());
    }

    public void sendPM(long user, long friend, boolean avoidBlock, byte[] message) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(10);
        s.addLong(user);
        s.addLong(friend);
        s.addByte((byte)(avoidBlock ? 1 : 0));
        s.addBytes(message);
        this.packets.add(s.toPacket());
    }

    public void friendLogin(long user, long friend, int w) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(11);
        s.addLong(user);
        s.addLong(friend);
        s.addShort(w);
        this.packets.add(s.toPacket());
    }

    public void friendLogout(long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(12);
        s.addLong(friend);
        this.packets.add(s.toPacket());
    }

    public void friendLogout(long user, long friend) {
        LSPacketBuilder s = new LSPacketBuilder();
        s.setID(13);
        s.addLong(friend);
        s.addLong(user);
        this.packets.add(s.toPacket());
    }
}

