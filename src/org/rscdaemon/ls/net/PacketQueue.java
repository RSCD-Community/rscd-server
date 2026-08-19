/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.net;

import java.util.ArrayList;
import java.util.List;
import org.rscdaemon.ls.net.Packet;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class PacketQueue<T extends Packet> {
    private ArrayList<T> packets = new ArrayList();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void add(T p) {
        ArrayList<T> arrayList = this.packets;
        synchronized (arrayList) {
            this.packets.add(p);
        }
    }

    public boolean hasPackets() {
        return !this.packets.isEmpty();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<T> getPackets() {
        List tmpList;
        ArrayList<T> arrayList = this.packets;
        synchronized (arrayList) {
            tmpList = (List)this.packets.clone();
            this.packets.clear();
        }
        return tmpList;
    }
}

