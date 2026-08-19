/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;

public class DropHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        if (player.getLocation().inEden()) {
            player.getActionSender().sendMessage("@gry@ Dropping disabled.");
            return;
        }
        player.resetAll();
        short idx = p.readShort();
        if (idx < 0 || idx >= player.getInventory().size()) {
            player.setSuspiciousPlayer(true);
            return;
        }
        final InvItem item = player.getInventory().get(idx);
        if (item == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        /*
         * The actual reason Thordur's Black Hole was pulled from real RSC:
         * players were tricked into dropping the Disk of Returning while
         * trapped, leaving them stuck until a moderator fished them out. See
         * ThordurHandler. Fixed at the root -- the disk simply cannot be
         * dropped in there, full stop, regardless of who asks or why.
         */
        if (item.getID() == 387 && player.getLocation().inBlackHole()) {
            player.getActionSender().sendMessage("@gry@ You'd better hang onto that down here.");
            return;
        }
        player.setStatus(Action.DROPPING_GITEM);
        world.getDelayedEventHandler().add(new DelayedEvent(player, 500){

            public void run() {
                if (this.owner.isBusy() || !this.owner.getInventory().contains(item) || this.owner.getStatus() != Action.DROPPING_GITEM) {
                    this.running = false;
                    return;
                }
                if (this.owner.hasMoved()) {
                    return;
                }
                this.owner.getActionSender().sendSound("dropobject");
                this.owner.getInventory().remove(item);
                this.owner.getActionSender().sendInventory();
                world.registerItem(new Item(item.getID(), this.owner.getX(), this.owner.getY(), item.getAmount(), this.owner));
                /*
                 * QuestTrigger.ITEM_DROP has existed since RSCD but nothing ever
                 * fired it, so a quest could not notice a drop. Witch's house
                 * needs it: the rat only comes out of its hole when a piece of
                 * cheese is on the floor. Fired after the item is really gone
                 * and really on the ground, so the quest sees the finished act
                 * and not an intention -- and unlike the object triggers it does
                 * not consume anything, because dropping has already happened.
                 *
                 * triggerDrop rather than triggerEntity: watching a drop is not
                 * the same claim as owning the item, and an item claimed with
                 * associateItem() stops being edible.
                 */
                this.owner.getQuestManager().triggerDrop(item);
                this.running = false;
            }
        });
    }
}

