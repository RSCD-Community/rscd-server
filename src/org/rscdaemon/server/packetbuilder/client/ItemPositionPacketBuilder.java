/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.client;

import java.util.Collection;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.StatefulEntityCollection;

public class ItemPositionPacketBuilder {
    private Player playerToUpdate;

    public void setPlayer(Player p) {
        this.playerToUpdate = p;
    }

    public RSCPacket getPacket() {
        StatefulEntityCollection<Item> watchedItems = this.playerToUpdate.getWatchedItems();
        if (watchedItems.changed()) {
            byte[] offsets;
            Collection<Item> newItems = watchedItems.getNewEntities();
            Collection<Item> knownItems = watchedItems.getKnownEntities();
            RSCPacketBuilder packet = new RSCPacketBuilder();
            packet.setID(109);
            for (Item i : knownItems) {
                if (!watchedItems.isRemoving(i)) continue;
                offsets = DataConversions.getObjectPositionOffsets(i.getLocation(), this.playerToUpdate.getLocation());
                packet.addShort(i.getID() + 32768);
                packet.addByte(offsets[0]);
                packet.addByte(offsets[1]);
            }
            /*
             * GHOST GROUND ITEMS. The removal loop above only considers items
             * the client already knows about, which is right -- you cannot
             * remove what was never sent. But an item that spawned and was
             * taken again inside one tick never reached knownItems, so no
             * removal is emitted for it, and without this check the loop below
             * would still tell the client to ADD it. Nothing ever takes it away
             * again: entitiesToRemove is cleared at the end of the tick, so the
             * item sits on the client's floor until a relog rebuilds the region.
             *
             * Found while chasing a reported ghost-bones bug. It is NOT that
             * bug -- there the pile had been on the floor for half a minute and
             * was visibly clicked, so the client plainly knew about it -- but it
             * is a real way to strand an item on the client's floor, so it is
             * fixed here rather than left for someone to hit later.
             */
            /*
             * isRemoved() as well as isRemoving(). The two are not the same
             * thing: isRemoving means somebody queued a removal this tick,
             * which only ever happens in revalidateWatchedItems, which only
             * looks at knownEntities. An item added to newEntities early in a
             * tick and taken later in the same one is therefore removed in the
             * world and NOT flagged as removing, so the guard above waves it
             * through and the client is told to draw something that is already
             * gone. The next tick's revalidate does catch it -- it is known by
             * then -- so this was self-correcting rather than permanent, but
             * announcing a dead item and retracting it a tick later is still a
             * flicker nobody asked for, and it depends on the revalidate pass
             * to clean up rather than being right in the first place.
             */
            for (Item i : newItems) {
                if (watchedItems.isRemoving(i) || i.isRemoved()) continue;
                offsets = DataConversions.getObjectPositionOffsets(i.getLocation(), this.playerToUpdate.getLocation());
                packet.addShort(i.getID());
                packet.addByte(offsets[0]);
                packet.addByte(offsets[1]);
            }
            return packet.toPacket();
        }
        return null;
    }
}

