/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.util.Formulae;

public class Point {
    protected int x;
    protected int y;

    public static Point location(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Point may not contain non negative values x:" + x + " y:" + y);
        }
        return new Point(x, y);
    }

    protected Point() {
    }

    private Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int wildernessLevel() {
        if (this.inBounds(395, 3560, 438, 3600)) {
            return 123;
        }
        int wild = 2203 - (this.y + (1776 - 944 * Formulae.getHeight(this)));
        if (this.x + 2304 >= 2640) {
            wild = -50;
        }
        if (wild > 0) {
            return 1 + wild / 6;
        }
        return 0;
    }

    public boolean inWilderness() {
        return this.wildernessLevel() > 0;
    }

    public boolean inModRoom() {
        return this.inBounds(528, 743, 548, 766) || this.inBounds(531, 3578, 550, 3610);
    }

    public boolean inArena() {
        return this.inBounds(218, 120, 238, 140);
    }

    public boolean inEden() {
        return this.inBounds(486, 3284, 504, 3302);
    }

    /*
     * Thordur's Black Hole -- a pocket of empty void with no landscape data
     * at all. Two named spots a few tiles apart, not one: a banned player and
     * a visiting player (who bought a Disk of Returning from Thordur) land in
     * different spots so they can see and talk to each other but cannot walk
     * close enough to trade -- see BlackHole memory.
     *
     * It used to be at y 4290-4294, which is not a place. The world is four
     * 944-tile planes, so y stops at 3775; every tile of the old box was
     * outside it, World.getTile returned null for all of them, and the
     * teleport threw an NPE that killed the game thread and took the whole
     * world down with it. Nobody had ever been here.
     *
     * Now it is on plane 2 -- the second-storey plane, which above the
     * Dwarven Mine is nothing at all. Sector h2x54y54 and all eight of its
     * neighbours are byte-for-byte blank in Landscape.rscd, and there is no
     * npc, object or ground item within 32 tiles in any direction, so this is
     * genuinely the middle of nowhere rather than somewhere that merely looks
     * empty. Walking out is refused outright in WalkRequest, because blank
     * landscape blocks nothing by itself.
     */
    public boolean inBlackHole() {
        return this.inBounds(304, 2718, 322, 2734);
    }

    /**
     * The Mage Arena itself -- the walled enclosure on the surface, entered
     * through the barrier at (228,119) or by Kolodion's own teleport to
     * (229,130). Casting a god spell in here is what counts toward that
     * spell's permanent 100-cast unlock -- see GodCharges.java and
     * MageArena.java.
     *
     * This used to name Kolodion's cave (432,3360 to 469,3384) instead, which
     * is a different room two planes down: the one with the banker, the rune
     * seller and Kolodion standing about talking. Nothing in it can be cast
     * at. All six battle mages are at (226-230, 127-133), which the old box
     * excluded entirely, so charges accrued only where there was no target and
     * could never be earned where the targets actually are. The bounds below
     * are the same enclosure inArena() names, widened by one tile to the north
     * so the barrier's own inside step at (228,120) counts as arrival.
     */
    public boolean inMageArena() {
        return this.inBounds(218, 119, 238, 140);
    }

    /**
     * The party hall in Seers' Village -- the designated drop-party venue, and
     * the one place on the map where telekinetic grab does not work.
     *
     * Two boxes, not one, and they are the same room: the world is four planes
     * stacked 944 tiles apart, so the upper storey of a ground-floor box at
     * y 464-471 is the same footprint at y 1408-1415. Warding only the ground
     * floor would leave the balcony above it open, and a drop party is exactly
     * the situation where somebody would find that out.
     *
     * The box is the whole building including its walls, deliberately. The ward
     * is checked against the item as well as the caster (see SpellHandler case
     * 16), so standing outside and reaching in over the wall is refused for the
     * item's sake even though the caster is stood on a legal tile. That is the
     * entire point of it: a party is only fair if the floor cannot be swept
     * from the street.
     *
     * The two Party Hall Guards at (494,462) and (497,462) stand just south of
     * this box, outside their own ward, and explain it if asked -- see
     * PartyHall.java.
     */
    /**
     * The whole of Karamja island, Brimhaven and Shilo included -- the box is
     * OpenRSC's, which uses it for exactly what we use it for: rum does not
     * survive teleporting off the island. Anywhere on the mainland the rum is
     * legal cargo and teleports fine.
     */
    public boolean inKaramja() {
        return this.inBounds(323, 644, 679, 908);
    }

    public boolean inPartyHall() {
        return this.inBounds(490, 464, 500, 471) || this.inBounds(490, 1408, 500, 1415);
    }

    public final int getY() {
        return this.y;
    }

    public final int getX() {
        return this.x;
    }

    public final boolean equals(Object o) {
        if (o instanceof Point) {
            return this.x == ((Point)o).x && this.y == ((Point)o).y;
        }
        return false;
    }

    public int hashCode() {
        return this.x << 16 | this.y;
    }

    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }

    public String getDescription() {
        if (this.inModRoom()) {
            return "Mod Room";
        }
        int wild = this.wildernessLevel();
        if (wild > 0) {
            return "Wilderness lvl-" + wild;
        }
        return "Unknown";
    }

    public boolean inBounds(int x1, int y1, int x2, int y2) {
        return this.x >= x1 && this.x <= x2 && this.y >= y1 && this.y <= y2;
    }
}

