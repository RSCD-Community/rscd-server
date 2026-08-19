/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Path;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.states.CombatState;

public class WalkRequest
implements PacketHandler {
    public static final World world = World.getWorld();

    /** Is any fight or duel event in the scheduler ticking for this player? */
    private static boolean fightEventExists(Player player) {
        for (org.rscdaemon.server.event.DelayedEvent event : world.getDelayedEventHandler().getEvents()) {
            if (event instanceof org.rscdaemon.server.event.FightEvent) {
                org.rscdaemon.server.event.FightEvent f = (org.rscdaemon.server.event.FightEvent)event;
                if (f.getOwner().equals(player) || f.getAffectedMob().equals(player)) {
                    return true;
                }
                continue;
            }
            if (event instanceof org.rscdaemon.server.event.DuelEvent) {
                org.rscdaemon.server.event.DuelEvent d = (org.rscdaemon.server.event.DuelEvent)event;
                if (d.getOwner().equals(player) || d.getAffectedPlayer().equals(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Enabled aggressive block sorting
     */
    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.inCombat()) {
            if (pID != 132) {
                return;
            }
            Mob opponent = player.getOpponent();
            if (opponent == null) {
                player.setSuspiciousPlayer(true);
                return;
            }
            /* Seen live 2026-08-17: a player in combat stance against a
               Death wing with no FightEvent ticking -- no rounds, no damage,
               and hitsMade stuck below 3, so the retreat gate held them
               there for good. If the fight has had ample time to start and
               no event exists for this player, the state is stale: free both
               sides and let the walk proceed. The log line is deliberate --
               the next occurrence should say who and against what. */
            if (System.currentTimeMillis() - player.getCombatTimer() > 5000L
                    && !WalkRequest.fightEventExists(player)) {
                org.rscdaemon.server.util.Logger.error("Stale combat freed: "
                        + player.getUsername() + " vs "
                        + (opponent instanceof org.rscdaemon.server.model.Npc
                                ? "npc " + ((org.rscdaemon.server.model.Npc)opponent).getID()
                                : "mob") + " at " + player.getLocation());
                player.resetCombat(CombatState.ERROR);
                opponent.resetCombat(CombatState.ERROR);
            } else {
            if (opponent.getHitsMade() < 3) {
                player.getActionSender().sendMessage("@gry@ You cannot retreat in the first 3 rounds of battle.");
                return;
            }
            if (player.isDueling() && player.getDuelSetting(0)) {
                player.getActionSender().sendMessage("@gry@ Running has been disabled in this duel.");
                return;
            }
            player.resetCombat(CombatState.RUNNING);
            opponent.resetCombat(CombatState.WAITING);
            }
        } else if (player.isBusy()) {
            return;
        }
        /*
         * The Black Hole has no floor, no walls and no way out but the disk.
         * It used to sit outside the world entirely, where the engine made it
         * immobile for free; now that it is on real tiles with blank
         * landscape -- and blank landscape blocks nothing -- the rule has to
         * be stated. Refusing the whole request rather than the step keeps a
         * banned player from inching out one waypoint at a time.
         */
        if (player.getLocation().inBlackHole()) {
            player.getActionSender().sendMessage("@gry@ There is nothing to walk on.");
            return;
        }
        player.resetAll();
        /* Seen live 2026-08-17: a 132 with no body at all, which made
           readShort throw out of the handler. Nothing useful can be read
           from it; the combat retreat above has already been honoured. */
        if (p.remaining() < 4) {
            return;
        }
        short startX = p.readShort();
        short startY = p.readShort();
        int numWaypoints = p.remaining() / 2;
        byte[] waypointXoffsets = new byte[numWaypoints];
        byte[] waypointYoffsets = new byte[numWaypoints];
        int x = 0;
        while (true) {
            if (x >= numWaypoints) {
                Path path = new Path((int)startX, (int)startY, waypointXoffsets, waypointYoffsets);
                player.setStatus(Action.IDLE);
                player.setPath(path);
                return;
            }
            waypointXoffsets[x] = p.readByte();
            waypointYoffsets[x] = p.readByte();
            ++x;
        }
    }
}

