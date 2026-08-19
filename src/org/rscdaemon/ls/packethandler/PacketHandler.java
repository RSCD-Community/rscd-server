/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.net.Packet;

public interface PacketHandler {
    public void handlePacket(Packet var1, Connection var2) throws Exception;
}

