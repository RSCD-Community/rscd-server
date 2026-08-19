/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.util.Formulae;

public class Entity {
    /*
     * Kept because other classes read Entity.world, but nothing INSIDE Entity
     * may use it. See setLocation.
     */
    public static final World world = World.getWorld();
    protected Point location;
    protected int id;
    protected int index;

    /*
     * World.getWorld(), not the static field above, and that is load-bearing.
     *
     * The field is assigned by Entity's static initialiser, and a static final
     * is not visible until that initialiser RETURNS. World.getWorld() loads the
     * world, which constructs GameObjects, which extend Entity and call this
     * method -- so when Entity is the class that starts the chain, this ran
     * while Entity.<clinit> was still in flight and read a null field:
     *
     *   NullPointerException: ... because "Entity.world" is null
     *       at Entity.setLocation(Entity.java:21)
     *       at GameObject.<init>(GameObject.java:25)
     *       at WorldLoader.loadWorld(WorldLoader.java:49)
     *       at World.getWorld(World.java:61)
     *       at Entity.<clinit>(Entity.java:15)
     *
     * getWorld() assigns its singleton before it loads anything, so calling it
     * here returns the half-built World that is doing the loading -- which is
     * exactly the one these objects belong in.
     *
     * The running server only ever avoided this because some other class
     * happened to touch World first. That was an accident of load order, not a
     * guarantee, and it broke the moment a harness reached an Entity subclass
     * first.
     */
    public void setLocation(Point p) {
        World.getWorld().setLocation(this, this.location, p);
        this.location = p;
    }

    public final int getID() {
        return this.id;
    }

    public final void setID(int newid) {
        this.id = newid;
    }

    public final int getIndex() {
        return this.index;
    }

    public final void setIndex(int newIndex) {
        this.index = newIndex;
    }

    public final Point getLocation() {
        return this.location;
    }

    public final int getX() {
        return this.location.getX();
    }

    public final int getY() {
        return this.location.getY();
    }

    public final boolean withinRange(Entity e, int radius) {
        return this.withinRange(e.getLocation(), radius);
    }

    public final boolean withinRange(Point p, int radius) {
        int xDiff = Math.abs(this.location.getX() - p.getX());
        int yDiff = Math.abs(this.location.getY() - p.getY());
        return xDiff <= radius && yDiff <= radius;
    }

    private boolean isBlocking(Entity e, int x, int y, int bit) {
        return this.isMapBlocking(e, x, y, (byte)bit) || this.isObjectBlocking(e, x, y, (byte)bit);
    }

    private boolean isMapBlocking(Entity e, int x, int y, byte bit) {
        byte val = World.getWorld().getTileValue((int)x, (int)y).mapValue;
        if ((val & bit) != 0) {
            return true;
        }
        if ((val & 0x10) != 0) {
            return true;
        }
        if ((val & 0x20) != 0) {
            return true;
        }
        return (val & 0x40) != 0 && (e instanceof Npc || e instanceof Player || e instanceof Item && !((Item)e).isOn(x, y) || e instanceof GameObject && !((GameObject)e).isOn(x, y));
    }

    private boolean isObjectBlocking(Entity e, int x, int y, byte bit) {
        byte val = World.getWorld().getTileValue((int)x, (int)y).objectValue;
        if ((val & bit) != 0 && !Formulae.doorAtFacing(e, x, y, Formulae.bitToDoorDir(bit)) && !Formulae.objectAtFacing(e, x, y, Formulae.bitToObjectDir(bit))) {
            return true;
        }
        if ((val & 0x10) != 0 && !Formulae.doorAtFacing(e, x, y, 2) && !Formulae.objectAtFacing(e, x, y, 3)) {
            return true;
        }
        if ((val & 0x20) != 0 && !Formulae.doorAtFacing(e, x, y, 3) && !Formulae.objectAtFacing(e, x, y, 1)) {
            return true;
        }
        return (val & 0x40) != 0 && (e instanceof Npc || e instanceof Player || e instanceof Item && !((Item)e).isOn(x, y) || e instanceof GameObject && !((GameObject)e).isOn(x, y));
    }

    public final boolean nextTo(Entity e) {
        int[] currentCoords = new int[]{this.getX(), this.getY()};
        while (currentCoords[0] != e.getX() || currentCoords[1] != e.getY()) {
            /*
             * Reaching any tile of a multi-tile object's footprint is
             * reaching the object -- the walk may never arrive at the
             * anchor tile when it sits on the far side of a wall the
             * footprint pokes through. Legends Guild top-floor stairs
             * (42:516,2423): the anchor is in the north room behind the
             * y2423/2424 dividing wall while the climb-up from below
             * lands you at (516,2426) in the south room, so "go down"
             * was swallowed with no output. Stepping onto the footprint
             * is only possible through unblocked edges (isBlocking
             * forgives just the target's own tiles), so this stays
             * wall-aware.
             */
            if (e instanceof GameObject && ((GameObject)e).isOn(currentCoords[0], currentCoords[1])) {
                return true;
            }
            if ((currentCoords = this.nextStep(currentCoords[0], currentCoords[1], e)) != null) continue;
            return false;
        }
        return true;
    }

    /**
     * Whether an unobstructed path exists from here to within radius tiles
     * of e -- the wall/door check ranged attacks and spells need and
     * withinRange cannot give, since withinRange is pure Chebyshev distance
     * with no notion of a wall between the two tiles. Confirmed missing
     * 2026-08-08: standing outside Elvarg's room with a closed door and a
     * solid wall between them, a successful cast still landed and her
     * breath (Formulae.elvargSpellBreath, unwired from any adjacency
     * requirement on purpose) killed the caster.
     *
     * Reuses nextStep -- the same greedy wall/door-bit walk nextTo already
     * trusts for melee adjacency (isBlocking, Formulae.doorAtFacing,
     * Formulae.objectAtFacing) -- but stops as soon as the walk would place
     * us within radius rather than requiring exact coincidence, since
     * ranged and magic do not need to stand next to the target the way
     * melee does.
     */
    public final boolean canReach(Entity e, int radius) {
        int curX = this.getX();
        int curY = this.getY();
        while (Math.abs(curX - e.getX()) > radius || Math.abs(curY - e.getY()) > radius) {
            int[] next = this.nextStep(curX, curY, e);
            if (next == null) {
                return false;
            }
            curX = next[0];
            curY = next[1];
        }
        return true;
    }

    public int[] nextStep(int myX, int myY, Entity e) {
        if (myX == e.getX() && myY == e.getY()) {
            return new int[]{myX, myY};
        }
        int newX = myX;
        int newY = myY;
        boolean myXBlocked = false;
        boolean myYBlocked = false;
        boolean newXBlocked = false;
        boolean newYBlocked = false;
        if (myX > e.getX()) {
            myXBlocked = this.isBlocking(e, myX - 1, myY, 8);
            newX = myX - 1;
        } else if (myX < e.getX()) {
            myXBlocked = this.isBlocking(e, myX + 1, myY, 2);
            newX = myX + 1;
        }
        if (myY > e.getY()) {
            myYBlocked = this.isBlocking(e, myX, myY - 1, 4);
            newY = myY - 1;
        } else if (myY < e.getY()) {
            myYBlocked = this.isBlocking(e, myX, myY + 1, 1);
            newY = myY + 1;
        }
        if (myXBlocked && myYBlocked || myXBlocked && myY == newY || myYBlocked && myX == newX) {
            return null;
        }
        if (newX > myX) {
            newXBlocked = this.isBlocking(e, newX, newY, 2);
        } else if (newX < myX) {
            newXBlocked = this.isBlocking(e, newX, newY, 8);
        }
        if (newY > myY) {
            newYBlocked = this.isBlocking(e, newX, newY, 1);
        } else if (newY < myY) {
            newYBlocked = this.isBlocking(e, newX, newY, 4);
        }
        if (newXBlocked && newYBlocked || newXBlocked && myY == newY || myYBlocked && myX == newX) {
            return null;
        }
        if (myXBlocked && newXBlocked || myYBlocked && newYBlocked) {
            return null;
        }
        return new int[]{newX, newY};
    }
}

