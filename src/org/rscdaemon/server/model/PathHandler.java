/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Path;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.TileValue;
import org.rscdaemon.server.model.World;

/**
 * Walks a mob along its path, one tile per engine tick.
 *
 * The server does no pathfinding of its own: for players the CLIENT finds
 * the route and sends it as a start tile plus waypoint offsets (the walk
 * packets), and NPC wandering picks single adjacent tiles. This class only
 * replays that route -- step one tile toward the current waypoint each tick,
 * advance to the next waypoint on arrival -- while re-checking the collision
 * map at every step, because the world may have changed since the client
 * planned the route (a door closed, an object appeared). Any blocked step
 * abandons the whole path, which is exactly what the original did: walk up
 * to a now-shut door and you simply stop.
 */
public class PathHandler {
    private Path path;
    /* -1 means still heading for the path's start tile; 0..length-1 is the
       waypoint currently being walked toward. */
    private int curWaypoint;
    private Mob mob;
    private static final World world = World.getWorld();

    public PathHandler(Mob m) {
        this.mob = m;
        this.resetPath();
    }

    public void setPath(int startX, int startY, byte[] waypointXoffsets, byte[] waypointYoffsets) {
        this.setPath(new Path(startX, startY, waypointXoffsets, waypointYoffsets));
    }

    public void setPath(Path path) {
        this.curWaypoint = -1;
        this.path = path;
    }

    public void updatePosition() {
        if (!this.finishedPath()) {
            this.setNextPosition();
        }
    }

    protected void resetPath() {
        this.path = null;
        this.curWaypoint = -1;
    }

    protected void setNextPosition() {
        int[] newCoords = new int[]{-1, -1};
        if (this.curWaypoint == -1) {
            if (this.atStart()) {
                this.curWaypoint = 0;
            } else {
                newCoords = this.getNextCoords(this.mob.getX(), this.path.getStartX(), this.mob.getY(), this.path.getStartY());
            }
        }
        if (this.curWaypoint > -1) {
            if (this.atWaypoint(this.curWaypoint)) {
                ++this.curWaypoint;
            }
            if (this.curWaypoint < this.path.length()) {
                newCoords = this.getNextCoords(this.mob.getX(), this.path.getWaypointX(this.curWaypoint), this.mob.getY(), this.path.getWaypointY(this.curWaypoint));
            } else {
                this.resetPath();
            }
        }
        if (newCoords[0] > -1 && newCoords[1] > -1) {
            this.mob.setLocation(Point.location(newCoords[0], newCoords[1]));
        }
    }

    /* A tile blocks entry from a given side if either its landscape byte or
       its placed-object byte says so -- walls contribute the per-side bits
       (1, 2, 4, 8, one per edge, which is why every caller passes the bit
       for the edge being crossed), while 0x10/0x20/0x40 mark the tile
       impassable from every direction (water, solid scenery, out of
       bounds). */
    private boolean isBlocking(int x, int y, int bit) {
        TileValue t = world.getTileValue(x, y);
        return this.isBlocking(t.mapValue, (byte)bit) || this.isBlocking(t.objectValue, (byte)bit);
    }

    private boolean isBlocking(byte val, byte bit) {
        if ((val & bit) != 0) {
            return true;
        }
        if ((val & 0x10) != 0) {
            return true;
        }
        if ((val & 0x20) != 0) {
            return true;
        }
        return (val & 0x40) != 0;
    }

    /**
     * One diagonal-capable step from (startX,startY) toward the waypoint:
     * the x axis and y axis each move a tile if they are short of the
     * target and the edge being crossed is open. The three grouped checks
     * afterwards reject the cases where a diagonal would cut a corner --
     * leaving through a blocked edge, arriving through a blocked edge, or
     * squeezing between two walls -- and rejection cancels the path
     * entirely rather than routing around, as the original did.
     */
    protected int[] getNextCoords(int startX, int destX, int startY, int destY) {
        try {
            int[] coords = new int[]{startX, startY};
            boolean myXBlocked = false;
            boolean myYBlocked = false;
            boolean newXBlocked = false;
            boolean newYBlocked = false;
            if (startX > destX) {
                myXBlocked = this.isBlocking(startX - 1, startY, 8);
                coords[0] = startX - 1;
            } else if (startX < destX) {
                myXBlocked = this.isBlocking(startX + 1, startY, 2);
                coords[0] = startX + 1;
            }
            if (startY > destY) {
                myYBlocked = this.isBlocking(startX, startY - 1, 4);
                coords[1] = startY - 1;
            } else if (startY < destY) {
                myYBlocked = this.isBlocking(startX, startY + 1, 1);
                coords[1] = startY + 1;
            }
            if (myXBlocked && myYBlocked || myXBlocked && startY == destY || myYBlocked && startX == destX) {
                return this.cancelCoords();
            }
            if (coords[0] > startX) {
                newXBlocked = this.isBlocking(coords[0], coords[1], 2);
            } else if (coords[0] < startX) {
                newXBlocked = this.isBlocking(coords[0], coords[1], 8);
            }
            if (coords[1] > startY) {
                newYBlocked = this.isBlocking(coords[0], coords[1], 1);
            } else if (coords[1] < startY) {
                newYBlocked = this.isBlocking(coords[0], coords[1], 4);
            }
            if (newXBlocked && newYBlocked || newXBlocked && startY == coords[1] || myYBlocked && startX == coords[0]) {
                return this.cancelCoords();
            }
            if (myXBlocked && newXBlocked || myYBlocked && newYBlocked) {
                return this.cancelCoords();
            }
            return coords;
        }
        catch (Exception e) {
            return this.cancelCoords();
        }
    }

    private int[] cancelCoords() {
        this.resetPath();
        return new int[]{-1, -1};
    }

    public boolean finishedPath() {
        if (this.path == null) {
            return true;
        }
        if (this.path.length() > 0) {
            return this.atWaypoint(this.path.length() - 1);
        }
        return this.atStart();
    }

    protected boolean atWaypoint(int waypoint) {
        return this.path.getWaypointX(waypoint) == this.mob.getX() && this.path.getWaypointY(waypoint) == this.mob.getY();
    }

    protected boolean atStart() {
        return this.mob.getX() == this.path.getStartX() && this.mob.getY() == this.path.getStartY();
    }
}

