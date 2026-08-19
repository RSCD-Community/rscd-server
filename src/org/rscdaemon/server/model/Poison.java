package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.util.DataConversions;

/**
 * Poison.
 *
 * RSCDaemon never had any of this. The items were all in the definitions --
 * seven poisoned daggers, six poisoned spears, seven poisoned knives, six
 * poisoned darts, six poisoned arrows, the bolts, the weapon poison and both
 * cure potions -- and Herblaw would even make the potions, but drinking one
 * did nothing, a poisoned weapon was just a weapon, and no monster in the game
 * could poison anybody. RSCD's own source left a comment where the potions
 * should have gone: "HANDLE WINE+ CURE POISON AND ANTIDOTE AND ZAMAROCK
 * POTIONS". This is that.
 *
 * The mechanic, from classic.runescape.wiki's Poison (effect) page, which is
 * the only surviving description of it -- poison is entirely server-side, so
 * unlike sprites and stats there is nothing in the Jagex cache to check it
 * against:
 *
 *   - Poison affects players, never monsters. A poisoned weapon is therefore
 *     only worth anything against another player.
 *   - It hurts once every 20 seconds, in or out of combat.
 *   - "The player will be damaged five times with one value, which then
 *     decreases by one, hitting five times with the lower value, and so on."
 *     So strength 6 is 5 hits of 6, 5 of 5, 5 of 4 ... 5 of 1, then it stops:
 *     30 hits and 105 damage in ten minutes if nothing is done about it.
 *   - Being poisoned again resets it to the new strength.
 *   - Curing is death, a cure poison potion, or a poison antidote. Both
 *     potions also grant immunity for a while afterwards.
 *   - The chance is rolled when combat is first entered and never again, so a
 *     long fight cannot poison you part way through. Monsters roll differently
 *     depending on who started it.
 *   - Ranging a poisonous monster can poison you too.
 *   - Paralyze Monster does not stop it.
 *   - There is no message and no special hit splat. The player sees an
 *     ordinary red splat and has to work out what is happening, which is what
 *     made antipoison worth carrying. Nothing is invented here to soften that.
 *
 * The five poisonous monsters and their strengths are the five the wiki marks
 * "poisonous = Yes", and no others: every other spider and scorpion in the
 * game is marked No, including the ones whose examine text says otherwise
 * (Jungle Spiders "suggest that they are able to poison the player" and
 * cannot). Only the scorpion and the spider have recorded chances; the other
 * three are given the same 1/6 and 1/3, which is the only pair of numbers the
 * game is known to use.
 */
public final class Poison {

    /** Poison Scorpion, Poison Spider, Tribesman, Jungle Savage, Dungeon spider. */
    private static final int[][] MONSTERS = {
        /* npc id, first-hit strength */
        { 271, 3 },     /* Poison Scorpion  -- "poison starts at 3 damage per round" */
        { 292, 6 },     /* Poison Spider    -- "poison starts at 6 damage per round" */
        { 421, 6 },     /* Tribesman        -- "their poison starts at 6 damage per round" */
        { 776, 6 },     /* Jungle Savage    -- "can inflict poison on the player" */
        { 656, 3 },     /* Dungeon spider   -- "A nasty poisonous arachnid" */
    };

    /**
     * "1/6 chance if the player attacks, 1/3 if the scorpion attacks" -- the
     * same pair is recorded for the spider, and nothing else is recorded at
     * all, so the other three monsters use it as well.
     */
    private static final int CHANCE_PLAYER_STARTED = 6;
    private static final int CHANCE_MONSTER_STARTED = 3;

    /** "damage around once every 20 seconds (~30 game ticks)". */
    private static final int ROUND_MS = 20000;

    /** "damaged five times with one value, which then decreases by one". */
    private static final int HITS_PER_STRENGTH = 5;

    /**
     * A poisoned weapon's first hit.
     *
     * "Poison damage starts at 4 for daggers", cited to a 2010 PKing guide.
     * That is the only figure recorded for any poisoned weapon, and there is
     * nothing to say the spears, knives, darts, arrows or bolts differ, so
     * they all use it.
     */
    public static final int WEAPON_STRENGTH = 4;

    /** Sinister chest: "poisons the player with poison damage starting at 6". */
    public static final int SINISTER_CHEST_STRENGTH = 6;

    /**
     * Clean weapon -> poisoned weapon, and how many one dose of weapon poison
     * treats.
     *
     * "Only certain categories of weapons may be poisoned: daggers, arrows,
     * spears, throwing knives, and darts", and the counts are the wiki's:
     * "One Dagger / One Spear / One Throwing Knife / Five Arrows / Five Bolts
     * / Six Throwing Darts", the bolts figure cited to a 2001 replay.
     *
     * Every pair here is a pair that exists in the item definitions and
     * nowhere else -- there is no poisoned black spear, dart or arrow because
     * Jagex never made one, and no poisoned ice arrow or pearl bolt either.
     */
    private static final int[][] WEAPONS = {
        /* clean, poisoned, doses treated per potion */
        {   28,  559, 1 },   /* Iron dagger              */
        {   62,  560, 1 },   /* bronze dagger            */
        {   63,  561, 1 },   /* Steel dagger             */
        {   64,  562, 1 },   /* Mithril dagger           */
        {  396,  563, 1 },   /* rune dagger              */
        {   65,  564, 1 },   /* Adamantite dagger        */
        {  423,  565, 1 },   /* Black dagger             */

        {  827, 1135, 1 },   /* Bronze Spear             */
        { 1088, 1136, 1 },   /* Iron Spear               */
        { 1089, 1137, 1 },   /* Steel Spear              */
        { 1090, 1138, 1 },   /* Mithril Spear            */
        { 1091, 1139, 1 },   /* Adamantite Spear         */
        { 1092, 1140, 1 },   /* Rune Spear               */

        { 1076, 1128, 1 },   /* Bronze throwing knife    */
        { 1075, 1129, 1 },   /* Iron throwing knife      */
        { 1077, 1130, 1 },   /* Steel throwing knife     */
        { 1078, 1131, 1 },   /* Mithril throwing knife   */
        { 1081, 1132, 1 },   /* Black throwing knife     */
        { 1079, 1133, 1 },   /* Adamantite throwing knife*/
        { 1080, 1134, 1 },   /* Rune throwing knife      */

        {   11,  574, 5 },   /* Bronze Arrows            */
        {  638,  639, 5 },   /* Iron Arrows              */
        {  640,  641, 5 },   /* Steel Arrows             */
        {  642,  643, 5 },   /* Mithril Arrows           */
        {  644,  645, 5 },   /* Adamantite Arrows        */
        {  646,  647, 5 },   /* Rune Arrows              */

        {  190,  592, 5 },   /* Crossbow bolts           */

        { 1013, 1122, 6 },   /* Bronze Throwing Dart     */
        { 1015, 1123, 6 },   /* Iron Throwing Dart       */
        { 1024, 1124, 6 },   /* Steel Throwing Dart      */
        { 1068, 1125, 6 },   /* Mithril Throwing Dart    */
        { 1069, 1126, 6 },   /* Adamantite Throwing Dart */
        { 1070, 1127, 6 },   /* Rune Throwing Dart       */
    };

    /** The weapon poison potion itself. One use, no doses. */
    public static final int WEAPON_POISON = 572;

    private Poison() {
    }

    /** The poisoned form of this weapon and how many a dose treats, or null. */
    public static int[] poisonedForm(int itemId) {
        for (int i = 0; i < WEAPONS.length; i++) {
            if (WEAPONS[i][0] == itemId) {
                return new int[] { WEAPONS[i][1], WEAPONS[i][2] };
            }
        }
        return null;
    }

    /** True if this item id is one of the poisoned weapons. */
    public static boolean isPoisonedWeapon(int itemId) {
        for (int i = 0; i < WEAPONS.length; i++) {
            if (WEAPONS[i][1] == itemId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Poison between two players, rolled as they join.
     *
     * "Players are unable to poison one another when Duelling but may poison
     * when they are PKing", so a duel is silently skipped. Both sides are
     * checked, because both are in the fight and either may be carrying a
     * treated weapon.
     *
     * The wiki records the strength -- 4, from a PKing guide -- but no chance
     * at all, for any poisoned weapon. Rather than invent a number, the weapon
     * is treated the way the ordinary poisonous monster is: rolled once on
     * engagement, at the same 1/6 the game uses when the player is the one who
     * attacked. That is a fit, not a recorded figure, and it is the only
     * invented number in this file.
     */
    public static void onPlayerEngage(Player attacker, Player defender) {
        if (attacker.isDueling() || defender.isDueling()) {
            return;
        }
        rollWeapon(attacker, defender);
        rollWeapon(defender, attacker);
    }

    private static void rollWeapon(Player armed, Player target) {
        boolean carrying = false;
        for (InvItem item : armed.getInventory().getItems()) {
            if (item.isWielded() && isPoisonedWeapon(item.getID())) {
                carrying = true;
                break;
            }
        }
        if (!carrying) {
            return;
        }
        if (DataConversions.random(0, CHANCE_PLAYER_STARTED - 1) != 0) {
            return;
        }
        infect(target, WEAPON_STRENGTH);
    }

    /** The strength this npc poisons at, or 0 if it is not poisonous. */
    public static int strengthOf(int npcId) {
        for (int i = 0; i < MONSTERS.length; i++) {
            if (MONSTERS[i][0] == npcId) {
                return MONSTERS[i][1];
            }
        }
        return 0;
    }

    public static boolean isPoisonous(int npcId) {
        return strengthOf(npcId) > 0;
    }

    /**
     * Roll for poison at the moment combat is joined.
     *
     * Called from both sides of the fight -- AttackHandler when the player
     * swings first and Npc.attack when the monster does -- and from RangeEvent,
     * because "it is possible to be poisoned by ranging a poisonous monster".
     * Nowhere else: the roll happens once, on engagement, and a fight that has
     * already started cannot poison anybody.
     */
    public static void onEngage(Npc npc, Player target, boolean npcInitiated) {
        int strength = strengthOf(npc.getID());
        if (strength == 0) {
            return;
        }
        int oneIn = npcInitiated ? CHANCE_MONSTER_STARTED : CHANCE_PLAYER_STARTED;
        if (DataConversions.random(0, oneIn - 1) != 0) {
            return;
        }
        infect(target, strength);
    }

    /**
     * Start poison, or restart it at a new strength.
     *
     * Immunity from a potion stops it outright, and a strength of 0 means the
     * caller decided there was nothing to apply.
     */
    public static void infect(Player p, int strength) {
        if (strength <= 0 || p.isImmuneToPoison()) {
            return;
        }
        p.setPoison(strength, 0);
        start(p);
    }

    /**
     * Put the ticking event back after a login, if the player logged out
     * poisoned. Safe to call for anybody; it does nothing when clean.
     */
    public static void resume(Player p) {
        if (p.getPoisonStrength() > 0) {
            start(p);
        }
    }

    private static void start(Player p) {
        for (DelayedEvent e : new ArrayList<DelayedEvent>(
                World.getWorld().getDelayedEventHandler().getEvents())) {
            if (e instanceof PoisonEvent && e.belongsTo(p)) {
                /* Already ticking. The strength has been reset underneath it,
                   which is what "This process can be reset by getting poisoned
                   again" means, but the clock keeps running: a player cannot
                   push the next hit away by stepping back into the scorpions. */
                return;
            }
        }
        World.getWorld().getDelayedEventHandler().add(new PoisonEvent(p));
    }

    /**
     * Cure, and hold poison off for a while.
     *
     * Cure poison protects "for about 2:30-5:30 minutes" and the antidote "for
     * about 6-12:30 minutes"; both windows are rolled per drink between those
     * bounds. Death cures with no immunity at all, which is the zero case.
     */
    public static void cure(Player p, int immuneMinMs, int immuneMaxMs) {
        p.setPoison(0, 0);
        if (immuneMaxMs > immuneMinMs) {
            p.setPoisonImmuneUntil(System.currentTimeMillis()
                    + DataConversions.random(immuneMinMs, immuneMaxMs));
        }
    }

    /** Death. "Poison can be cured by dying" -- and buys no immunity. */
    public static void cureOnDeath(Player p) {
        p.setPoison(0, 0);
        p.setPoisonImmuneUntil(0L);
    }

    /**
     * One poison hit every twenty seconds.
     *
     * The event outlives any one fight and keeps running while the player
     * walks, banks or stands still, because poison does. It removes itself
     * when the poison runs out, when it is cured, or when the player goes.
     */
    private static class PoisonEvent extends DelayedEvent {

        PoisonEvent(Player owner) {
            super(owner, ROUND_MS);
        }

        public void run() {
            Player p = this.owner;
            if (!p.loggedIn()) {
                this.stop();
                return;
            }
            int strength = p.getPoisonStrength();
            if (strength <= 0) {
                this.stop();
                return;
            }
            hit(p, strength);
            if (p.getPoisonStrength() <= 0) {
                /* Cured by the hit killing them, or by a potion drunk between
                   rounds. Either way there is nothing left to tick. */
                this.stop();
                return;
            }
            int done = p.getPoisonHits() + 1;
            if (done >= HITS_PER_STRENGTH) {
                /* Five hits at this value; the next five are one weaker, and
                   at zero the poison is finished. */
                p.setPoison(strength - 1, 0);
                if (p.getPoisonStrength() <= 0) {
                    this.stop();
                }
            } else {
                p.setPoison(strength, done);
            }
        }

        private void hit(Player p, int damage) {
            p.setLastDamage(damage);
            int newHp = p.getHits() - damage;
            p.setHits(newHp);

            List<Player> toInform = new ArrayList<Player>();
            toInform.addAll(p.getViewArea().getPlayersInView());
            for (Player viewer : toInform) {
                viewer.informOfModifiedHits(p);
            }
            p.getActionSender().sendStat(3);

            if (newHp <= 0) {
                /* Poison kills. There is no killer to credit -- killedBy takes
                   the null and skips the "You have defeated" branch and the
                   kill log, which is right: nobody defeated them. */
                p.killedBy(null, false);
            }
        }
    }
}
