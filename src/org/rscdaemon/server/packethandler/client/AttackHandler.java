/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.FightEvent;
import org.rscdaemon.server.event.RangeEvent;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.Action;

public class AttackHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.getX() < 86 && player.getX() > 61 && player.getY() == 129) {
            player.resetPath();
            return;
        }
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        Mob affectedMob = null;
        short serverIndex = p.readShort();
        if (pID == 57) {
            affectedMob = world.getPlayer(serverIndex);
            Player pcheck = world.getPlayer(serverIndex);
            if (pcheck.isMod() && pcheck.getLocation().inWilderness()) {
                player.resetPath();
                player.getActionSender().sendMessage("@gry@ Attacking disabled.");
                return;
            }
            if (pcheck.isAdmin() && pcheck.getLocation().inWilderness()) {
                player.resetPath();
                player.getActionSender().sendMessage("@gry@ Attacking disabled.");
                return;
            }
        } else if (pID == 73) {
            affectedMob = world.getNpc(serverIndex);
        }
        if (affectedMob == null || affectedMob.equals(player)) {
            player.resetPath();
            player.getActionSender().sendMessage("@gry@ Attacking disabled.");
            return;
        }
        if (affectedMob.getX() < 86 && affectedMob.getX() > 61 && affectedMob.getY() == 129) {
            player.resetPath();
            player.getActionSender().sendMessage("@gry@ Attacking disabled.");
            return;
        }
        player.setFollowing(affectedMob);
        player.setStatus(Action.ATTACKING_MOB);
        if (player.getRangeEquip() < 0) {
            world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedMob, 2){

                public void arrived() {
                    this.owner.resetPath();
                    if (this.owner.isBusy() || this.affectedMob.isBusy() || !this.owner.nextTo(this.affectedMob) || !this.owner.checkAttack(this.affectedMob, false) || this.owner.getStatus() != Action.ATTACKING_MOB) {
                        return;
                    }
                    this.owner.resetAll();
                    // Asked here rather than on the click so that a quest which
                    // refuses can have the npc say so face to face, and after
                    // resetAll() so that the refusal may open a dialogue --
                    // resetAll() would close one this had just started.
                    if (this.affectedMob instanceof Npc && this.owner.getQuestManager().refusesAttack((Npc)this.affectedMob)) {
                        return;
                    }
                    this.owner.setStatus(Action.FIGHTING_MOB);
                    if (this.affectedMob instanceof Player) {
                        Player affectedPlayer = (Player)this.affectedMob;
                        this.owner.setSkulledOn(affectedPlayer);
                        affectedPlayer.resetAll();
                        affectedPlayer.setStatus(Action.FIGHTING_MOB);
                        affectedPlayer.getActionSender().sendMessage("@pnk@ You are under attack!");
                    }
                    this.affectedMob.resetPath();
                    this.owner.setLocation(this.affectedMob.getLocation(), true);
                    for (Player p : this.owner.getViewArea().getPlayersInView()) {
                        p.removeWatchedPlayer(this.owner);
                    }
                    this.owner.setBusy(true);
                    this.owner.setSprite(9);
                    this.owner.setOpponent(this.affectedMob);
                    this.owner.setCombatTimer();
                    this.affectedMob.setBusy(true);
                    this.affectedMob.setSprite(8);
                    this.affectedMob.setOpponent(this.owner);
                    this.affectedMob.setCombatTimer();
                    if (this.affectedMob instanceof Npc) {
                        org.rscdaemon.server.util.Formulae.applyDragonBreath((Npc) this.affectedMob, this.owner, false);
                        /* Silverlight weakens a demon only if it was already
                           in hand when the fight opened -- this is the swing
                           that opens it. */
                        org.rscdaemon.server.util.Formulae.applySilverlight((Npc) this.affectedMob, this.owner);
                        /* "The chance to be poisoned by a monster is random,
                           and will be applied when first entering combat" --
                           here, where the player swung first, so the longer
                           odds apply. */
                        org.rscdaemon.server.model.Poison.onEngage((Npc) this.affectedMob, this.owner, false);
                        if (this.owner.getHits() <= 0) {
                            this.owner.killedBy(this.affectedMob, false);
                            this.affectedMob.resetCombat(org.rscdaemon.server.states.CombatState.WON);
                            this.owner.resetCombat(org.rscdaemon.server.states.CombatState.LOST);
                            return;
                        }
                    } else if (this.affectedMob instanceof Player) {
                        /* A poisoned weapon is only worth carrying against
                           another player -- poison does not touch monsters --
                           and both sides of the fight roll, since both are
                           swinging. */
                        org.rscdaemon.server.model.Poison.onPlayerEngage(
                                this.owner, (Player) this.affectedMob);
                    }
                    FightEvent fighting = new FightEvent(this.owner, this.affectedMob);
                    fighting.setLastRun(0L);
                    world.getDelayedEventHandler().add(fighting);
                }
            });
        } else {
            world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedMob, 5){

                public void arrived() {
                    this.owner.resetPath();
                    if (this.owner.isBusy() || !this.owner.canReach(this.affectedMob, 5) || !this.owner.checkAttack(this.affectedMob, true) || this.owner.getStatus() != Action.ATTACKING_MOB) {
                        return;
                    }
                    this.owner.resetAll();
                    // The melee branch's comment applies here too: a bow does
                    // not change whether the quest will allow the fight.
                    if (this.affectedMob instanceof Npc && this.owner.getQuestManager().refusesAttack((Npc)this.affectedMob)) {
                        return;
                    }
                    this.owner.setStatus(Action.RANGING_MOB);
                    if (this.affectedMob instanceof Player) {
                        Player affectedPlayer = (Player)this.affectedMob;
                        this.owner.setSkulledOn(affectedPlayer);
                        affectedPlayer.resetTrade();
                        if (affectedPlayer.getMenuHandler() != null) {
                            affectedPlayer.resetMenuHandler();
                        }
                        if (affectedPlayer.accessingBank()) {
                            affectedPlayer.resetBank();
                        }
                        if (affectedPlayer.accessingShop()) {
                            affectedPlayer.resetShop();
                        }
                        if (affectedPlayer.getNpc() != null) {
                            affectedPlayer.getNpc().unblock();
                            affectedPlayer.setNpc(null);
                        }
                    }
                    if (this.affectedMob.isPrayerActivated(13)) {
                        this.owner.getActionSender().sendMessage("@pnk@ Your missles have mystically been blocked.");
                        return;
                    }
                    /* "Note that it is possible to be poisoned by ranging a
                       poisonous monster." Rolled once, here, where the ranging
                       starts, and not per arrow -- poison is applied on
                       engagement whichever way the player engages. The player
                       shot first, so it is the longer odds. */
                    if (this.affectedMob instanceof Npc) {
                        org.rscdaemon.server.model.Poison.onEngage((Npc) this.affectedMob, this.owner, false);
                    } else if (this.affectedMob instanceof Player) {
                        org.rscdaemon.server.model.Poison.onPlayerEngage(
                                this.owner, (Player) this.affectedMob);
                    }
                    this.owner.setRangeEvent(new RangeEvent(this.owner, this.affectedMob));
                }
            });
        }
    }
}

