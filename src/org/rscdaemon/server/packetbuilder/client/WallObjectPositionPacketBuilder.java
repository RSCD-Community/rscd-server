/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.Collection;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.StatefulEntityCollection;

public class WallObjectPositionPacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        StatefulEntityCollection<GameObject> watchedObjects = this.playerToUpdate.getWatchedObjects();
        if (watchedObjects.changed()) {
            byte[] offsets;
            Collection<GameObject> newObjects = watchedObjects.getNewEntities();
            Collection<GameObject> knownObjets = watchedObjects.getKnownEntities();
            RSCPacketBuilder packet = new RSCPacketBuilder();
            packet.setID(95);
            for (GameObject o : knownObjets) {
                if (o.getType() != 1 || !watchedObjects.isRemoving(o)) continue;
                offsets = DataConversions.getObjectPositionOffsets(o.getLocation(), this.playerToUpdate.getLocation());
                packet.addShort(60000);
                packet.addByte(offsets[0]);
                packet.addByte(offsets[1]);
                packet.addByte((byte)o.getDirection());
            }
            /* Same one-tick spawn-and-remove race as ItemPositionPacketBuilder:
               an object never seen by the client gets no removal, so it must
               not get an add either, or it stays on screen until a relog. */
            for (GameObject o : newObjects) {
                if (watchedObjects.isRemoving(o)) continue;
                if (o.getType() != 1) continue;
                offsets = DataConversions.getObjectPositionOffsets(o.getLocation(), this.playerToUpdate.getLocation());
                packet.addShort(o.getID());
                packet.addByte(offsets[0]);
                packet.addByte(offsets[1]);
                packet.addByte((byte)o.getDirection());
            }
            return packet.toPacket();
        }
        return null;
    }
}

