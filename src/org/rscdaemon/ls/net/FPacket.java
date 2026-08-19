/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.net;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.net.Packet;

public final class FPacket
extends Packet {
    private int pID;
    private String[] parameters;

    public FPacket(Connection session, int pID, String[] parameters, boolean bare) {
        super(session, new byte[0], bare);
        this.pID = pID;
        this.parameters = parameters;
    }

    public FPacket(Connection session, int pID, String[] parameters) {
        this(session, pID, parameters, false);
    }

    public int getID() {
        return this.pID;
    }

    public String[] getParameters() {
        return this.parameters;
    }

    public int countParameters() {
        return this.parameters.length;
    }

    public String toString() {
        return super.toString() + " pid = " + this.pID + " parameter count = " + this.parameters.length;
    }
}

