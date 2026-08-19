/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import java.util.ArrayList;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class FightEvent
extends DelayedEvent {
    private Mob affectedMob;
    private int hits;
    private int firstHit;

    /* Elvarg's own npc id -- see Formulae.applyDragonBreath's doc comment. */
    private static final int ELVARG = 196;

    /**
     * The Shadow spider's drain is a combat-start effect, not a per-round one:
     * the moment the fight begins -- whichever side started it -- the player's
     * current prayer points are halved, once. That is how the witness
     * implements it (OnCombatStartScript), and it matches the "is it a spider
     * or is it a shadow" gimmick better than a repeating tick would.
     */
    private static final int SHADOW_SPIDER = 343;
    private static final int PRAYER = 5;

    public FightEvent(Player owner, Mob affectedMob) {
        this(owner, affectedMob, false);
    }

    public FightEvent(Player owner, Mob affectedMob, boolean attacked) {
        super(owner, 1000);
        this.affectedMob = affectedMob;
        this.firstHit = attacked ? 1 : 0;
        this.hits = 0;
        if (affectedMob instanceof Npc && ((Npc) affectedMob).getID() == SHADOW_SPIDER) {
            int prayer = owner.getCurStat(PRAYER);
            if (prayer > 0) {
                owner.setCurStat(PRAYER, (int) Math.round(prayer / 2.0));
                owner.getActionSender().sendStat(PRAYER);
                owner.getActionSender().sendMessage("The spider drains your prayer");
            }
        }
    }

    public void run() {
        Player attackerPlayer;
        String combatSound;
        Mob opponent;
        Mob attacker;
        if (!this.owner.loggedIn() || this.affectedMob instanceof Player && !((Player)this.affectedMob).loggedIn()) {
            this.owner.resetCombat(CombatState.ERROR);
            this.affectedMob.resetCombat(CombatState.ERROR);
            return;
        }
        if (this.hits++ % 2 == this.firstHit) {
            attacker = this.owner;
            opponent = this.affectedMob;
        } else {
            attacker = this.affectedMob;
            opponent = this.owner;
        }
        if (opponent.getHits() <= 0) {
            attacker.resetCombat(CombatState.WON);
            opponent.resetCombat(CombatState.LOST);
            return;
        }
        attacker.incHitsMade();
        boolean elvargAttacking = attacker instanceof Npc && ((Npc) attacker).getID() == ELVARG;
        boolean paralyzed = attacker instanceof Npc && opponent.isPrayerActivated(12);
        if (paralyzed && !elvargAttacking) {
            // An ordinary npc's attack is stopped outright.
            return;
        }
        int damage;
        if (paralyzed) {
            /*
             * elvargAttacking is true here. Paralyze Monster still stops HER
             * physical attack roll exactly like any other npc's -- the breath
             * (Formulae.elvargBreathChip) is a separate, always-on effect,
             * not her sword. This used to read attacker==Elvarg as "this
             * round is the breath" and skip the block for her entire attack,
             * so a paralyzed player was taking her full attack roll (~110
             * Attack) every round on top of the breath.
             *
             * opponent is always this event's owner (a Player) here: this
             * event is Player-vs-Mob, and elvargAttacking true means
             * attacker==affectedMob, so opponent==owner.
             */
            damage = Formulae.elvargBreathChip((Player) opponent);
        } else {
            damage = Formulae.calcFightHit(attacker, opponent);
            if (elvargAttacking) {
                // On top of her normal attack roll, not instead of it -- the
                // breath is a separate, always-on effect, and now (like the
                // spell-cast case) shield-sensitive -- see Formulae.elvargBreath.
                damage += Formulae.elvargBreathChip((Player) opponent);
            }
        }
        opponent.setLastDamage(damage);
        int newHp = opponent.getHits() - damage;
        opponent.setHits(newHp);
        org.rscdaemon.server.model.PrayerEffects.applySmite(attacker, opponent, damage);
        ArrayList<Player> playersToInform = new ArrayList<Player>();
        playersToInform.addAll(opponent.getViewArea().getPlayersInView());
        playersToInform.addAll(attacker.getViewArea().getPlayersInView());
        for (Player p : playersToInform) {
            p.informOfModifiedHits(opponent);
        }
        String string = combatSound = damage > 0 ? "combat1b" : "combat1a";
        if (opponent instanceof Player) {
            Player opponentPlayer = (Player)opponent;
            opponentPlayer.getActionSender().sendStat(3);
            opponentPlayer.getActionSender().sendSound(combatSound);
        }
        if (attacker instanceof Player) {
            attackerPlayer = (Player)attacker;
            attackerPlayer.getActionSender().sendSound(combatSound);
        }
        if (newHp <= 0) {
            opponent.killedBy(attacker, false);
            if (attacker instanceof Player) {
                attackerPlayer = (Player)attacker;
                int exp = Formulae.combatExperienceQuarters(opponent);
                switch (attackerPlayer.getCombatStyle()) {
                    case 0: {
                        for (int x = 0; x < 3; ++x) {
                            attackerPlayer.incExpQuarters(x, exp, true);
                            attackerPlayer.getActionSender().sendStat(x);
                        }
                        break;
                    }
                    case 1: {
                        attackerPlayer.incExpQuarters(2, exp * 3, true);
                        attackerPlayer.getActionSender().sendStat(2);
                        break;
                    }
                    case 2: {
                        attackerPlayer.incExpQuarters(0, exp * 3, true);
                        attackerPlayer.getActionSender().sendStat(0);
                        break;
                    }
                    case 3: {
                        attackerPlayer.incExpQuarters(1, exp * 3, true);
                        attackerPlayer.getActionSender().sendStat(1);
                    }
                }
                attackerPlayer.incExpQuarters(3, exp, true);
                attackerPlayer.getActionSender().sendStat(3);
            }
            attacker.resetCombat(CombatState.WON);
            opponent.resetCombat(CombatState.LOST);
        }
    }

    public Mob getAffectedMob() {
        return this.affectedMob;
    }

    public boolean equals(Object o) {
        if (o instanceof FightEvent) {
            FightEvent e = (FightEvent)o;
            return e.belongsTo(this.owner) && e.getAffectedMob().equals(this.affectedMob);
        }
        return false;
    }
}

