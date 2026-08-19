/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.GnomeBall;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;

public class InvUseOnPlayer
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        final Player affectedPlayer = world.getPlayer(p.readShort());
        final InvItem item = player.getInventory().get(p.readShort());
        if (affectedPlayer == null || item == null) {
            return;
        }
        player.setFollowing(affectedPlayer);
        player.setStatus(Action.USING_INVITEM_ON_PLAYER);
        if (item.getID() == GnomeBall.BALL) {
            // a throw, not a hand-over: works from range, so walk only until
            // within 4 tiles rather than adjacent
            world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedPlayer, 4){

                public void arrived() {
                    this.owner.resetPath();
                    if (!this.owner.getInventory().contains(item) || this.owner.isBusy() || this.owner.isRanging() || this.owner.getStatus() != Action.USING_INVITEM_ON_PLAYER) {
                        return;
                    }
                    this.owner.resetAll();
                    GnomeBall.throwBall(this.owner, affectedPlayer);
                }
            });
            return;
        }
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedPlayer, 1){

            public void arrived() {
                this.owner.resetPath();
                if (!this.owner.getInventory().contains(item) || !this.owner.nextTo(affectedPlayer) || this.owner.isBusy() || this.owner.isRanging() || this.owner.getStatus() != Action.USING_INVITEM_ON_PLAYER) {
                    return;
                }
                this.owner.resetAll();
                switch (item.getID()) {
                    case 575: {
                        this.owner.setBusy(true);
                        affectedPlayer.setBusy(true);
                        this.owner.resetPath();
                        affectedPlayer.resetPath();
                        Bubble crackerBubble = new Bubble(this.owner, 575);
                        for (Player p : this.owner.getViewArea().getPlayersInView()) {
                            p.informOfBubble(crackerBubble);
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You pull the cracker with " + affectedPlayer.getUsername() + "...");
                        affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is pulling a cracker with you...");
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                InvItem phat = new InvItem(DataConversions.random(576, 581));
                                if (DataConversions.random(0, 1) == 1) {
                                    this.owner.getActionSender().sendMessage("@pnk@ Out comes a " + phat.getDef().getName() + "!");
                                    affectedPlayer.getActionSender().sendMessage("@gry@ " + this.owner.getUsername() + " got the contents!");
                                    this.owner.getInventory().add(phat);
                                } else {
                                    this.owner.getActionSender().sendMessage("@gry@ " + affectedPlayer.getUsername() + " got the contents!");
                                    affectedPlayer.getActionSender().sendMessage("@pnk@ Out comes a " + phat.getDef().getName() + "!");
                                    affectedPlayer.getInventory().add(phat);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.setBusy(false);
                                affectedPlayer.setBusy(false);
                                this.owner.getActionSender().sendInventory();
                                affectedPlayer.getActionSender().sendInventory();
                            }
                        });
                        break;
                    }
                    default: {
                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                    }
                }
            }
        });
    }
}

