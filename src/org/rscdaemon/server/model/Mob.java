/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.DuelEvent;
import org.rscdaemon.server.event.FightEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Path;
import org.rscdaemon.server.model.PathHandler;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.ViewArea;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.Logger;

/**
 * Everything that can walk and fight -- the shared base of Player and Npc.
 *
 * A mob owns exactly the state the two have in common: a facing/animation
 * sprite, a movement path being walked, who it is fighting, the prayers lit
 * on it, a ViewArea of what it can see, and an appearance revision number.
 * Everything stat-shaped (hits, attack, defence...) is abstract because
 * players read theirs from curstats and NPCs from their definition.
 */
public abstract class Mob
extends Entity {
    /* Step direction -> facing sprite, indexed [1 + (oldX-newX)][1 + (oldY-newY)]
       by updateSprite() below. The centre cell is -1 because a step that goes
       nowhere never reaches the lookup. Sprites 0-7 are the eight facings;
       8 and 9 (never produced by walking) are the two combat stances. */
    private int[][] mobSprites = new int[][]{{3, 2, 1}, {4, -1, 0}, {5, 6, 7}};
    protected int mobSprite = 1;
    protected boolean hasMoved;
    protected int combatLevel = 3;
    protected boolean ourAppearanceChanged = true;
    /* Revision counter, bumped whenever appearance changes. Other players
       cache the last revision they were sent per mob and resend appearance
       only when the number moves -- see ClientUpdater. */
    protected int appearanceID = 0;
    /* Idle tracking: when this mob last took a step, and whether the
       stood-still warning has already been given so it is not repeated. */
    protected long lastMovement = System.currentTimeMillis();
    protected boolean warnedToMove = false;
    /* Busy gates interaction: a mob mid-fight, mid-trade or mid-menu cannot
       be engaged by anything else until whatever holds it lets go. */
    private boolean busy = false;
    private boolean spriteChanged = false;
    private Mob combatWith = null;
    private long combatTimer = 0L;
    private PathHandler pathHandler = new PathHandler(this);
    private int lastDamage = 0;
    protected ViewArea viewArea = new ViewArea(this);
    protected boolean[] activatedPrayers =
        new boolean[org.rscdaemon.server.entityhandling.EntityHandler.prayerCount()];
    private int hitsMade = 0;
    protected boolean removed = false;
    private CombatState lastCombatState = CombatState.WAITING;

    public boolean isRemoved() {
        return this.removed;
    }

    public abstract void remove();

    public int getHitsMade() {
        return this.hitsMade;
    }

    public void incHitsMade() {
        ++this.hitsMade;
    }

    public abstract int getCombatStyle();

    public abstract int getHits();

    public abstract int getAttack();

    public abstract int getDefense();

    public abstract int getStrength();

    public abstract void setHits(int var1);

    public abstract void killedBy(Mob var1, boolean var2);

    public abstract int getWeaponPowerPoints();

    public abstract int getWeaponAimPoints();

    public abstract int getArmourPoints();

    /**
     * Takes this mob out of combat whatever the reason -- a death, a
     * retreat, a won fight -- by stopping the fight or duel event that
     * involves it, clearing the combat stance and linkage, and recording
     * how the fight ended so later code can ask (a retreating opponent
     * and a dead one look the same by every other field).
     */
    public void resetCombat(CombatState state) {
        for (DelayedEvent event : world.getDelayedEventHandler().getEvents()) {
            DuelEvent dueling;
            if (event instanceof FightEvent) {
                FightEvent fighting = (FightEvent)event;
                if (!fighting.getOwner().equals(this) && !fighting.getAffectedMob().equals(this)) continue;
                fighting.stop();
                break;
            }
            if (!(event instanceof DuelEvent) || !(dueling = (DuelEvent)event).getOwner().equals(this) && !dueling.getAffectedPlayer().equals(this)) continue;
            dueling.stop();
            break;
        }
        this.setBusy(false);
        this.setSprite(4);
        this.setOpponent(null);
        this.setCombatTimer();
        this.hitsMade = 0;
        if (this instanceof Player) {
            Player player = (Player)this;
            player.setStatus(Action.IDLE);
        }
        this.lastCombatState = state;
    }

    public CombatState getCombatState() {
        return this.lastCombatState;
    }

    public boolean isPrayerActivated(int pID) {
        return this.activatedPrayers[pID];
    }

    public void setPrayer(int pID, boolean b) {
        this.activatedPrayers[pID] = b;
    }

    public ViewArea getViewArea() {
        return this.viewArea;
    }

    public int getLastDamage() {
        return this.lastDamage;
    }

    public void setLastDamage(int d) {
        this.lastDamage = d;
    }

    public boolean isBusy() {
        return this.busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public void warnToMove() {
        this.warnedToMove = true;
    }

    public boolean warnedToMove() {
        return this.warnedToMove;
    }

    public void setLastMoved() {
        this.lastMovement = System.currentTimeMillis();
    }

    public long getLastMoved() {
        return this.lastMovement;
    }

    public int getCombatLevel() {
        return this.combatLevel;
    }

    public void setCombatLevel(int level) {
        this.combatLevel = level;
        this.ourAppearanceChanged = true;
    }

    public void setAppearnceChanged(boolean b) {
        this.ourAppearanceChanged = b;
    }

    public void updateAppearanceID() {
        if (this.ourAppearanceChanged) {
            ++this.appearanceID;
        }
    }

    public int getAppearanceID() {
        return this.appearanceID;
    }

    public void setLocation(Point p) {
        this.setLocation(p, false);
    }

    /**
     * Walking turns the mob to face the way it stepped; teleporting does
     * not, which is why the flag exists -- a teleported mob keeps its old
     * facing and, more importantly, is not flagged hasMoved, so watchers
     * are told to redraw it from scratch rather than slide it one tile.
     */
    public void setLocation(Point p, boolean teleported) {
        if (!teleported) {
            this.updateSprite(p);
            this.hasMoved = true;
        }
        this.setLastMoved();
        this.warnedToMove = false;
        super.setLocation(p);
    }

    /** Faces the mob the way this step is about to take it (see mobSprites). */
    protected void updateSprite(Point newLocation) {
        try {
            int xIndex = this.getLocation().getX() - newLocation.getX() + 1;
            int yIndex = this.getLocation().getY() - newLocation.getY() + 1;
            this.setSprite(this.mobSprites[xIndex][yIndex]);
        }
        catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    public int getSprite() {
        return this.mobSprite;
    }

    public void setSprite(int x) {
        this.spriteChanged = true;
        this.mobSprite = x;
    }

    public boolean spriteChanged() {
        return this.spriteChanged;
    }

    public void resetSpriteChanged() {
        this.spriteChanged = false;
    }

    public void setOpponent(Mob opponent) {
        this.combatWith = opponent;
    }

    public void setCombatTimer() {
        this.combatTimer = System.currentTimeMillis();
    }

    public long getCombatTimer() {
        return this.combatTimer;
    }

    public Mob getOpponent() {
        return this.combatWith;
    }

    /* Sprites 8 and 9 are the two sides of the fight animation; combat is
       the stance AND an opponent, because the stance alone lingers for a
       moment after resetCombat's opponent-clearing on the other mob. */
    public boolean inCombat() {
        return (this.mobSprite == 8 || this.mobSprite == 9) && this.combatWith != null;
    }

    public boolean hasMoved() {
        return this.hasMoved;
    }

    public void resetMoved() {
        this.hasMoved = false;
    }

    public boolean finishedPath() {
        return this.pathHandler.finishedPath();
    }

    public void updatePosition() {
        this.pathHandler.updatePosition();
    }

    public void resetPath() {
        this.pathHandler.resetPath();
    }

    public void setPath(Path path) {
        this.pathHandler.setPath(path);
    }

    /**
     * Whether this mob stands on or beside any tile of the object's
     * footprint -- the "close enough to use it" test. Type 1 objects
     * (walkable scenery) count as a single tile whatever their definition
     * says, and a rotated object (any direction but north/south) swaps
     * width for height because the footprint turns with it.
     */
    public final boolean atObject(GameObject o) {
        int width;
        int height;
        int dir = o.getDirection();
        if (o.getType() == 1) {
            height = 1;
            width = 1;
        } else if (dir == 0 || dir == 4) {
            width = o.getGameObjectDef().getWidth();
            height = o.getGameObjectDef().getHeight();
        } else {
            height = o.getGameObjectDef().getWidth();
            width = o.getGameObjectDef().getHeight();
        }
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                int yDist;
                Point p = Point.location(o.getX() + x, o.getY() + y);
                int xDist = Math.abs(this.location.getX() - p.getX());
                int tDist = xDist + (yDist = Math.abs(this.location.getY() - p.getY()));
                if (tDist > 1) continue;
                return true;
            }
        }
        return false;
    }
}

