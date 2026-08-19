package org.rscdaemon.server.entityhandling.defs.extras;

/**
 * An Agility obstacle that is a door rather than scenery, keyed by DoorDef id.
 *
 * Only the two low walls at the end of the Barbarian Outpost course are here.
 * They are doors because that is what the landscape made them: a low wall is a
 * wall you can see over, and Classic drew it with the door table. A door does
 * not need its ends written down the way a rope swing does -- it stands
 * between two adjacent tiles and the player steps across it -- so this def
 * carries only the level, the payout and the course bookkeeping.
 *
 * Keyed by DoorDef id, which is its own numbering: never pass a GameObjectDef
 * id to this. Experience is in quarters for the same reason it is in
 * {@link ObjectAgilityDef}.
 */
public class DoorAgilityDef {
    private int requiredLvl;
    /** Experience on success, times four. */
    private int quarterExp;
    /** Course this obstacle belongs to. */
    private int courseId;
    /** Position of this obstacle in its course, counting from zero. */
    private int stage;
    /** Lap bonus paid when this obstacle finishes a lap, times four. */
    private int lapQuarterExp;
    /** Message shown while crossing, or null for the generic one. */
    private String message;

    public int getReqLevel() {
        return this.requiredLvl;
    }

    /** The wiki's figure, rounded half up. */
    public int getExp() {
        return (this.quarterExp + 2) / 4;
    }

    public int getLapExp() {
        return (this.lapQuarterExp + 2) / 4;
    }

    public boolean hasLapBonus() {
        return this.lapQuarterExp > 0;
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
}
