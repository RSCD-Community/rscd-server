package org.rscdaemon.server.entityhandling.defs.extras;

/**
 * One Agility obstacle, keyed by the scenery object id that carries it.
 *
 * An obstacle is a crossing: two tiles that a player cannot walk between,
 * joined by a rope, a log, a net or a pipe. Both ends are written down here
 * rather than derived at runtime, because the landscape only says which side
 * of a wall a tile is on -- it does not say where the rope lands.
 *
 * Experience is held as quarters. Classic quotes agility experience in halves
 * (7.5 for a gnome obstacle, 22.5 for the Yanille ledge, 12.5 for a net) and
 * the server's experience counter is a whole number, so the wiki figure is
 * stored multiplied by four and rounded back on the way out. This is the same
 * convention Thieving uses and it exists for the same reason: the halves are
 * Jagex's, the rounding is ours, and keeping them apart means the data file
 * can be checked against the wiki without arithmetic.
 */
public class ObjectAgilityDef {
    /** Agility level the obstacle asks for. */
    private int requiredLvl;
    /** Experience on success, times four. */
    private int quarterExp;
    /** Experience on a failed attempt, times four. Usually zero. */
    private int failQuarterExp;
    /** One end of the crossing. */
    private int x1, y1;
    /** The other end. */
    private int x2, y2;
    /** True when the crossing only works from (x1,y1) towards (x2,y2). */
    private boolean oneWay;
    /** True when the obstacle can drop the player. */
    private boolean canFail;
    /**
     * Where a failed crossing puts the player, or (0,0) to leave them where
     * they were.
     *
     * Classic drops a failed climber into whatever is underneath -- a spiked
     * pit, water, the floor of a cavern. For most of the map that landing spot
     * was never recorded, so nearly every obstacle leaves this at zero and
     * takes the damage on the spot.
     * The Underground Pass is the exception: the wiki says plainly that a
     * failed stone bridge drops you to the dungeon floor, and the dungeon floor
     * is a place with coordinates.
     */
    private int failX, failY;
    /**
     * Level at which failure stops entirely. Zero means the default, which is
     * twenty-five levels above the requirement -- the span the Yanille ledge
     * is recorded as having (40 to 65).
     */
    private int failStopLvl;
    /** True when the obstacle still pays out at 100% fatigue. */
    private boolean ignoresFatigue;
    /** Course this obstacle belongs to, or zero for a standalone shortcut. */
    private int courseId;
    /** Position of this obstacle in its course, counting from zero. */
    private int stage;
    /** Lap bonus paid when this obstacle finishes a lap, times four. */
    private int lapQuarterExp;
    /** Message shown while crossing, or null for the generic one. */
    private String message;
    /**
     * Item the crossing cannot be made without, or zero.
     *
     * Classic has a handful of obstacles that are not a question of skill at
     * all: a wall grill too high to reach, a stalactite over a spiked pit. You
     * do not climb those, you tie a rope to them. The rope is not consumed --
     * the recovered stalagmite transcript has the player untying it afterwards
     * and putting it back in the satchel -- so this is a check and not a cost.
     */
    private int requiredItem;
    /** What the obstacle says when {@link #requiredItem} is missing. */
    private String refusal;

    public int getReqLevel() {
        return this.requiredLvl;
    }

    /** The wiki's figure, rounded half up. */
    public int getExp() {
        return (this.quarterExp + 2) / 4;
    }

    public int getFailExp() {
        return (this.failQuarterExp + 2) / 4;
    }

    public int getLapExp() {
        return (this.lapQuarterExp + 2) / 4;
    }

    public boolean hasLapBonus() {
        return this.lapQuarterExp > 0;
    }

    public int getX1() {
        return this.x1;
    }

    public int getY1() {
        return this.y1;
    }

    public int getX2() {
        return this.x2;
    }

    public int getY2() {
        return this.y2;
    }

    public boolean isOneWay() {
        return this.oneWay;
    }

    public boolean canFail() {
        return this.canFail;
    }

    /** True when a failed crossing moves the player rather than just hurting them. */
    public boolean hasFallPoint() {
        return this.failX > 0 && this.failY > 0;
    }

    public int getFailX() {
        return this.failX;
    }

    public int getFailY() {
        return this.failY;
    }

    /** The level at which this obstacle stops throwing the player. */
    public int getFailStopLvl() {
        return this.failStopLvl > 0 ? this.failStopLvl : this.requiredLvl + 25;
    }

    public boolean ignoresFatigue() {
        return this.ignoresFatigue;
    }

    public int getCourseId() {
        return this.courseId;
    }

    public int getStage() {
        return this.stage;
    }

    public String getMessage() {
        return this.message;
    }

    public int getRequiredItem() {
        return this.requiredItem;
    }

    public String getRefusal() {
        return this.refusal != null ? this.refusal : "You can't do that";
    }
}
