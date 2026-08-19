/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.NPCDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemDropDef;
import org.rscdaemon.server.entityhandling.locs.NPCLoc;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.FightEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Path;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class Npc
extends Mob {
    private static final World world = World.getWorld();
    private NPCLoc loc;
    private NPCDef def;
    /** Curse-spell drains: attack, defence, strength. See drain(int, int). */
    private final int[] drained = new int[3];
    private int curHits;
    private DelayedEvent timeout = null;
    private Player blocker = null;
    private boolean shouldRespawn = true;

    /** Who this npc is chasing down after being shot/spelled from outside melee range -- see retaliate(). */
    private Player chaseTarget = null;
    private DelayedEvent chaseEvent = null;

    /**
     * Set the first time Player.updateViewedNpcs finds this npc removed but
     * still listed on an ActiveTile -- the state behind the unkillable 0-hit
     * npcs. That skip is what stops it being player-visible, which also means
     * it would stop being reportable; this makes it say so exactly once
     * instead, per npc, rather than once per player per tick forever.
     */
    private boolean reportedStranded = false;

    /** True on the first call only, so the caller can log a stranding once. */
    public boolean reportStranded() {
        if (this.reportedStranded) {
            return false;
        }
        this.reportedStranded = true;
        return true;
    }

    public void setRespawn(boolean respawn) {
        this.shouldRespawn = respawn;
    }

    public void blockedBy(Player player) {
        this.blocker = player;
        player.setNpc(this);
        this.setBusy(true);
        this.timeout = new DelayedEvent(null, 15000){

            public void run() {
                Npc.this.unblock();
                this.running = false;
            }
        };
        world.getDelayedEventHandler().add(this.timeout);
    }

    /**
     * The player this npc is currently held by, or null if nobody is talking to
     * it. Only a conversation sets this, so it answers "is somebody keeping it
     * busy" rather than the broader isBusy(), which combat sets as well. The
     * knight's sword needs the distinction: the cupboard opens while a second
     * player is talking to Sir Vyvin, and only then.
     */
    public Player getBlocker() {
        return this.blocker;
    }

    public void unblock() {
        if (this.blocker != null) {
            this.blocker.setNpc(null);
            this.blocker = null;
        }
        if (this.timeout == null) {
            return;
        }
        this.setBusy(false);
        this.timeout.stop();
        this.timeout = null;
    }

    public Npc(NPCLoc loc) {
        this.def = EntityHandler.getNpcDef(loc.getId());
        this.curHits = this.def.getHits();
        this.loc = loc;
        super.setID(loc.getId());
        super.setLocation(Point.location(loc.startX(), loc.startY()), true);
        super.setCombatLevel(Formulae.getCombatLevel(this.def.getAtt(), this.def.getDef(), this.def.getStr(), this.def.getHits(), 0, 0, 0));
    }

    public Npc(int id, int startX, int startY, int minX, int maxX, int minY, int maxY) {
        this(new NPCLoc(id, startX, startY, minX, maxX, minY, maxY));
    }

    public void remove() {
        if (!this.removed && this.shouldRespawn && this.def.respawnTime() > 0) {
            world.getDelayedEventHandler().add(new DelayedEvent(null, this.def.respawnTime() * 1000){

                public void run() {
                    world.registerNpc(new Npc(Npc.this.loc));
                    this.running = false;
                }
            });
        }
        this.removed = true;
    }

    public void killedBy(Mob mob, boolean stake) {
        /*
         * A second lethal hit can genuinely still be in flight when the
         * first one lands -- two blows queued back to back, or autocast
         * resending against the same wire index the instant this npc's
         * respawn hands it to a new instance. Without this guard the second
         * call re-runs unregisterNpc/remove on an npc already gone, doubles
         * the loot drop, and -- see EntityList.remove's own comment -- can
         * null out whatever *new* npc has since reused this one's freed
         * index, leaving that one stuck on screen and unattackable.
         */
        if (this.removed) {
            return;
        }
        Mob opponent;
        if (mob instanceof Player) {
            // Asked before anything else happens, and the only place it is
            // asked: melee, ranged and magic all arrive here once the blow has
            // landed, so a quest that has to keep an npc alive -- Chronozon
            // until all four blasts have hit him -- only has to say so once.
            // The reprieve is one hit point and the fight carries on; the quest
            // has already told the player why.
            Player killer = (Player)mob;
            if (killer.getQuestManager().refusesKill(this)) {
                this.setHits(1);
                for (Player p : this.getViewArea().getPlayersInView()) {
                    p.informOfModifiedHits(this);
                }
                return;
            }
        }
        if (mob instanceof Player) {
            Player player = (Player)mob;
            player.getActionSender().sendSound("victory");
        }
        if ((opponent = super.getOpponent()) != null) {
            opponent.resetCombat(CombatState.WON);
        }
        this.resetCombat(CombatState.LOST);
        world.unregisterNpc(this);
        this.remove();
        Player owner = mob instanceof Player ? (Player)mob : null;
        if (owner != null) {
            // Before the drops, so a quest that wants to substitute or suppress
            // one has somewhere to stand. Fired for the killer only: a quest is
            // a property of one player's progress, not of the npc.
            owner.getQuestManager().triggerEntity(QuestTrigger.NPC_KILLED, this);
        }
        ItemDropDef[] drops = this.def.getDrops();
        int total = 0;
        for (ItemDropDef drop : drops) {
            total += drop.getWeight();
        }
        int hit = DataConversions.random(0, total);
        total = 0;
        for (ItemDropDef drop : drops) {
            if (drop.getWeight() == 0) {
                world.registerItem(new Item(drop.getID(), this.getX(), this.getY(), drop.getAmount(), owner));
                continue;
            }
            if (hit >= total && hit < total + drop.getWeight()) {
                /*
                 * A negative id is the "nothing" outcome. Before this, a
                 * table had no way to express one at all: hit=random(0,total)
                 * is inclusive, so exactly one value (hit==total) fell through
                 * every range unmatched -- the roll's only possible "nothing",
                 * at a fixed ~1/(total+1) chance regardless of how the real
                 * weights were split. Authentic RSC's Man has a documented 25%
                 * chance of no drop at all; there was no way to write that down
                 * with weights alone, since inflating total to make that slice
                 * bigger would have inflated every OTHER item's rarity by the
                 * same factor. A dedicated negative-id entry is weighted like
                 * any other outcome and drops nothing when it wins.
                 */
                if (drop.getID() == -2) {
                    /*
                     * -2 is the Rare Drop Table sentinel: this monster has a
                     * documented chance of rolling the shared gem/dragon-item/
                     * cert table instead of anything on its own list. See
                     * RareDropTable for the table itself. Its roll has the
                     * same shape as this one -- random(0, total) inclusive --
                     * so it carries the same built-in 1-in-(total+1) empty
                     * outcome, plus any negative-id rows of its own.
                     */
                    RareDropTable.roll(this.getX(), this.getY(), owner);
                } else if (drop.getID() >= 0) {
                    world.registerItem(new Item(drop.getID(), this.getX(), this.getY(), drop.getAmount(), owner));
                }
                break;
            }
            total += drop.getWeight();
        }
    }

    public int getCombatStyle() {
        return 0;
    }

    public int getWeaponPowerPoints() {
        return 1;
    }

    public int getWeaponAimPoints() {
        return 1;
    }

    public int getArmourPoints() {
        return 1;
    }

    public int getAttack() {
        return Math.max(0, this.def.getAtt() - this.drained[0]);
    }

    public int getDefense() {
        return Math.max(0, this.def.getDef() - this.drained[1]);
    }

    public int getStrength() {
        return Math.max(0, this.def.getStr() - this.drained[2]);
    }

    /**
     * The base level of one of the three combat stats a curse spell can lower.
     * Indexed the way Formulae.statArray is: 0 attack, 1 defence, 2 strength.
     */
    public int getBaseStat(int stat) {
        switch (stat) {
            case 0: {
                return this.def.getAtt();
            }
            case 1: {
                return this.def.getDef();
            }
            case 2: {
                return this.def.getStr();
            }
        }
        return 0;
    }

    /**
     * Whether Silverlight has already taken its bite out of this demon. The
     * effect fires once at the start of the first fight and never again, so
     * re-engaging cannot stack it; see Formulae.applySilverlight. Not saved
     * anywhere, and it does not need to be -- killing the npc builds a new one.
     */
    private boolean silverlightWeakened = false;

    public boolean isSilverlightWeakened() {
        return this.silverlightWeakened;
    }

    public void setSilverlightWeakened(boolean weakened) {
        this.silverlightWeakened = weakened;
    }

    /**
     * How far below its base level a curse spell has already pushed a stat.
     * Confuse and its family refuse to stack, so this is what they ask.
     */
    public int getDrain(int stat) {
        return stat >= 0 && stat < this.drained.length ? this.drained[stat] : 0;
    }

    /**
     * An npc has no current-level table of its own -- its combat stats come
     * straight off the shared definition -- so a drain is held here and taken
     * off in the getters above. It lasts as long as this npc does: killing it
     * builds a fresh Npc from the same NPCLoc, which starts undrained.
     */
    public void drain(int stat, int amount) {
        if (stat >= 0 && stat < this.drained.length && amount > 0) {
            this.drained[stat] = this.drained[stat] + amount;
        }
    }

    public int getHits() {
        return this.curHits;
    }

    public void setHits(int lvl) {
        if (lvl <= 0) {
            lvl = 0;
        }
        this.curHits = lvl;
    }

    private Player findVictim() {
        long now = System.currentTimeMillis();
        ActiveTile[][] tiles = this.getViewArea().getViewedArea(2, 2, 2, 2);
        for (int x = 0; x < tiles.length; ++x) {
            for (int y = 0; y < tiles[x].length; ++y) {
                ActiveTile t = tiles[x][y];
                if (t == null) continue;
                for (Player p : t.getPlayers()) {
                    if (p.isBusy() || now - p.getCombatTimer() < (long)(p.getCombatState() == CombatState.RUNNING || p.getCombatState() == CombatState.WAITING ? 3000 : 500) || !p.nextTo(this) || !p.getLocation().inBounds(this.loc.minX - 4, this.loc.minY - 4, this.loc.maxX + 4, this.loc.maxY + 4) || !this.getLocation().inWilderness() && p.getCombatLevel() >= this.getCombatLevel() * 2 + 1) continue;
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Set this npc on a player, as an aggressive one does when it spots them.
     *
     * Lifted out of updatePosition() so that something other than the wander
     * loop can start a fight. Quests need it: the phoenix gang's weaponsmaster
     * is not an aggressive npc, he attacks only the player who has just picked
     * up one of the crossbows he is guarding.
     *
     * Does nothing if either side is already busy, so it cannot pull a player
     * out of a fight they are already in.
     */
    public void attackPlayer(Player victim) {
        if (victim == null || this.isBusy() || victim.isBusy()) {
            return;
        }
        this.resetPath();
        victim.resetPath();
        victim.resetAll();
        victim.setStatus(Action.FIGHTING_MOB);
        victim.getActionSender().sendMessage("You are under attack!");
        this.setLocation(victim.getLocation(), true);
        for (Player p : this.getViewArea().getPlayersInView()) {
            p.removeWatchedNpc(this);
        }
        victim.setBusy(true);
        victim.setSprite(9);
        victim.setOpponent(this);
        victim.setCombatTimer();
        this.setBusy(true);
        this.setSprite(8);
        this.setOpponent(victim);
        this.setCombatTimer();
        org.rscdaemon.server.util.Formulae.applyDragonBreath(this, victim, true);
        /* The monster started it, so it rolls at the shorter odds. */
        Poison.onEngage(this, victim, true);
        /* A demon that jumps a player who is already carrying Silverlight
           still meets it from the first blow, so the weakening applies here
           too. What the sword does not survive is being drawn mid-fight, and
           neither of these two call sites can be reached mid-fight. */
        org.rscdaemon.server.util.Formulae.applySilverlight(this, victim);
        if (victim.getHits() <= 0) {
            victim.killedBy(this, false);
            this.resetCombat(org.rscdaemon.server.states.CombatState.WON);
            victim.resetCombat(org.rscdaemon.server.states.CombatState.LOST);
            return;
        }
        FightEvent fighting = new FightEvent(victim, this, true);
        fighting.setLastRun(0L);
        world.getDelayedEventHandler().add(fighting);
    }

    private boolean isChasing() {
        return this.chaseTarget != null && this.chaseEvent != null;
    }

    private boolean isChasing(Player p) {
        return this.isChasing() && this.chaseTarget.equals(p);
    }

    private void stopChasing() {
        this.chaseTarget = null;
        if (this.chaseEvent != null) {
            this.chaseEvent.stop();
            this.chaseEvent = null;
        }
    }

    /**
     * Called whenever ranged or magic damage (or a harmful curse spell, which
     * deals none) lands on this npc from outside melee range. Real RSC made
     * any harmful attack an npc could path to provoke retaliation -- the only
     * safe way to shoot/spell something was standing behind a closed gate or
     * fence that genuinely blocked its path, not just its line of sight.
     * Melee never needed this: AttackHandler already forces the player
     * adjacent before FightEvent starts trading blows both ways. Ranged and
     * magic don't, so without this call the npc just absorbs damage forever.
     *
     * Adjacent already -- as a curse spell or a point-blank shot can leave
     * things -- engages immediately via attackPlayer(). Otherwise starts (or
     * keeps) a tile-by-tile chase toward the attacker's current position,
     * using the same collision-aware PathHandler stepping a player's own
     * setFollowing() drives (see Player.setFollowing's DelayedEvent), so a
     * closed gate stops it exactly like it would stop a player.
     *
     * A no-op if this npc is already busy (already fighting something, being
     * talked to, etc.) -- one extra arrow from a bystander should not pull it
     * off whatever it is already doing, same restraint attackPlayer() itself
     * already applies.
     */
    public void retaliate(final Player attacker) {
        if (attacker == null || this.isBusy() || this.isChasing(attacker)) {
            return;
        }
        if (attacker.nextTo(this)) {
            this.attackPlayer(attacker);
            return;
        }
        if (this.isChasing()) {
            this.stopChasing();
        }
        this.chaseTarget = attacker;
        /*
         * owner is the attacker, not this npc -- DelayedEvent.owner is typed
         * Player (every other event in this codebase is Player-owned even
         * when an npc is the one acting, e.g. FightEvent's own owner/
         * affectedMob split), so the attacker is the only Player reference
         * available to hand it. That also means logout cleans this up for
         * free: World's logout path calls removePlayersEvents(player), which
         * drops every event belongsTo() that player, this one included.
         */
        this.chaseEvent = new DelayedEvent(attacker, 500) {

            public void run() {
                if (Npc.this.isBusy() || !attacker.loggedIn()
                        || !attacker.getLocation().inBounds(Npc.this.loc.minX - 4, Npc.this.loc.minY - 4,
                                Npc.this.loc.maxX + 4, Npc.this.loc.maxY + 4)) {
                    Npc.this.stopChasing();
                    return;
                }
                if (attacker.nextTo(Npc.this)) {
                    Npc.this.stopChasing();
                    Npc.this.attackPlayer(attacker);
                    return;
                }
                if (Npc.this.finishedPath()) {
                    Npc.this.setPath(new Path(Npc.this.getX(), Npc.this.getY(), attacker.getX(), attacker.getY()));
                }
            }
        };
        world.getDelayedEventHandler().add(this.chaseEvent);
    }

    public void updatePosition() {
        long now = System.currentTimeMillis();
        Player victim = null;
        if (!this.isBusy() && this.def.isAggressive() && now - this.getCombatTimer() > 3000L && (victim = this.findVictim()) != null) {
            this.attackPlayer(victim);
        }
        if (now - this.lastMovement > 6000L) {
            this.lastMovement = now;
            if (!this.isBusy() && this.finishedPath() && DataConversions.random(0, 2) == 1) {
                super.setPath(new Path(this.getX(), this.getY(), DataConversions.random(this.loc.minX(), this.loc.maxX()), DataConversions.random(this.loc.minY(), this.loc.maxY())));
            }
        }
        super.updatePosition();
    }

    public NPCLoc getLoc() {
        return this.loc;
    }

    public NPCDef getDef() {
        return EntityHandler.getNpcDef(this.getID());
    }
}

