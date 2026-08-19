/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import java.util.ArrayList;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class DuelEvent
extends DelayedEvent {
    private Player affectedPlayer;
    private int hits;

    public DuelEvent(Player owner, Player affectedPlayer) {
        super(owner, 1000);
        this.affectedPlayer = affectedPlayer;
        this.hits = 0;
    }

    public void run() {
        Player opponent;
        Player attacker;
        if (!this.owner.loggedIn() || !this.affectedPlayer.loggedIn()) {
            this.owner.resetCombat(CombatState.ERROR);
            this.affectedPlayer.resetCombat(CombatState.ERROR);
            return;
        }
        if (this.hits++ % 2 == 0) {
            attacker = this.owner;
            opponent = this.affectedPlayer;
        } else {
            attacker = this.affectedPlayer;
            opponent = this.owner;
        }
        if (opponent.getHits() <= 0) {
            attacker.resetCombat(CombatState.WON);
            opponent.resetCombat(CombatState.LOST);
            return;
        }
        attacker.incHitsMade();
        attacker.setLastMoved();
        int damage = Formulae.calcFightHit(attacker, opponent);
        opponent.setLastDamage(damage);
        int newHp = opponent.getHits() - damage;
        opponent.setHits(newHp);
        ArrayList<Player> playersToInform = new ArrayList<Player>();
        playersToInform.addAll(opponent.getViewArea().getPlayersInView());
        playersToInform.addAll(attacker.getViewArea().getPlayersInView());
        for (Player p : playersToInform) {
            p.informOfModifiedHits(opponent);
        }
        String combatSound = damage > 0 ? "combat1b" : "combat1a";
        opponent.getActionSender().sendStat(3);
        opponent.getActionSender().sendSound(combatSound);
        attacker.getActionSender().sendSound(combatSound);
        if (newHp <= 0) {
            opponent.killedBy(attacker, true);
            int exp = Formulae.combatExperienceQuarters(opponent);
            switch (attacker.getCombatStyle()) {
                case 0: {
                    for (int x = 0; x < 3; ++x) {
                        attacker.incExpQuarters(x, exp, true);
                        attacker.getActionSender().sendStat(x);
                    }
                    break;
                }
                case 1: {
                    attacker.incExpQuarters(2, exp * 3, true);
                    attacker.getActionSender().sendStat(2);
                    break;
                }
                case 2: {
                    attacker.incExpQuarters(0, exp * 3, true);
                    attacker.getActionSender().sendStat(0);
                    break;
                }
                case 3: {
                    attacker.incExpQuarters(1, exp * 3, true);
                    attacker.getActionSender().sendStat(1);
                }
            }
            attacker.incExpQuarters(3, exp, true);
            attacker.getActionSender().sendStat(3);
            attacker.resetCombat(CombatState.WON);
            opponent.resetCombat(CombatState.LOST);
            attacker.resetDueling();
            opponent.resetDueling();
        }
    }

    public Player getAffectedPlayer() {
        return this.affectedPlayer;
    }

    public boolean equals(Object o) {
        if (o instanceof DuelEvent) {
            DuelEvent e = (DuelEvent)o;
            return e.belongsTo(this.owner) && e.getAffectedPlayer().equals(this.affectedPlayer);
        }
        return false;
    }
}

