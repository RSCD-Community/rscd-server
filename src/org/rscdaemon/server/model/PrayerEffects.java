package org.rscdaemon.server.model;

import java.util.ArrayList;

import org.rscdaemon.server.util.DataConversions;

/**
 * The two beyond-vanilla prayers with an in-combat effect of their own.
 * The boost trios live in Formulae's multiplier ladder and the protect
 * prayers in the combat events; these two don't fit either shape, so they
 * get their own home instead of being pasted into every damage site.
 *
 * Smite (21) drains a quarter of every hit's damage from the victim's
 * prayer points, and Retribution (20) deals one last hit back at whoever
 * lands the killing blow. Both are player-vs-player only: npcs have no
 * prayer pool to drain, and a monster taking retribution damage after its
 * killer's fight event already ended would fight nobody.
 */
public final class PrayerEffects {

    public static final int RETRIBUTION = 20;
    public static final int SMITE = 21;

    private static final int PRAYER = 5;

    private PrayerEffects() {
    }

    /**
     * Call from every damage site (melee, ranged, magic) after the hit has
     * landed, with the attacker whose prayers matter and the raw damage
     * dealt. Quarter-damage drain, floored at zero; hitting the floor turns
     * the victim's prayers off exactly the way the drain event does, so
     * being smited dry reads the same as praying yourself dry.
     */
    public static void applySmite(Mob attacker, Mob victim, int damage) {
        if (damage <= 0 || !attacker.isPrayerActivated(SMITE)) {
            return;
        }
        if (!(attacker instanceof Player) || !(victim instanceof Player)) {
            return;
        }
        Player target = (Player) victim;
        int drain = damage / 4;
        if (drain <= 0 || target.getCurStat(PRAYER) <= 0) {
            return;
        }
        int newPrayer = Math.max(0, target.getCurStat(PRAYER) - drain);
        target.setCurStat(PRAYER, newPrayer);
        target.getActionSender().sendStat(PRAYER);
        if (newPrayer == 0) {
            for (int x = 0; x < org.rscdaemon.server.entityhandling.EntityHandler.prayerCount(); ++x) {
                if (!target.isPrayerActivated(x)) continue;
                target.removePrayerDrain(x);
                target.setPrayer(x, false);
            }
            target.getActionSender().sendMessage("@gry@ You have run out of prayer points. Return to a church to recharge");
            target.getActionSender().sendPrayers();
        }
    }

    /**
     * Call from Player.killedBy BEFORE the prayer-reset loop -- killedBy
     * turns every prayer off on the way out, so reading Retribution's state
     * any later always sees false. The hit is applied FightEvent-style so
     * everyone watching sees it, and it can kill: the dead player's own
     * prayers are already being reset, so two mutual Retributions can't
     * ping-pong forever.
     */
    public static void applyRetribution(Player dead, Mob killer) {
        if (!dead.isPrayerActivated(RETRIBUTION)) {
            return;
        }
        if (!(killer instanceof Player) || killer == dead) {
            return;
        }
        Player target = (Player) killer;
        int damage = DataConversions.random(0, dead.getMaxStat(PRAYER) / 4);
        if (damage <= 0) {
            return;
        }
        target.getActionSender().sendMessage("@pnk@ " + dead.getUsername() + "'s retribution strikes you!");
        target.setLastDamage(damage);
        int newHp = target.getHits() - damage;
        target.setHits(newHp);
        ArrayList<Player> playersToInform = new ArrayList<Player>();
        playersToInform.addAll(dead.getViewArea().getPlayersInView());
        playersToInform.addAll(target.getViewArea().getPlayersInView());
        for (Player p : playersToInform) {
            p.informOfModifiedHits(target);
        }
        target.getActionSender().sendStat(3);
        if (newHp <= 0) {
            target.killedBy(dead, false);
        }
    }
}
