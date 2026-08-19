import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.Quests;

/**
 * How many times each god spell has been cast inside the Mage Arena.
 *
 * Not a quest. The same reasoning as GangMembership: a Quest is the only
 * thing this server persists per player besides skills and inventory, and
 * quest classes compile into the default package, so nothing under src/ can
 * name one -- SpellHandler, which needs to read and increment these counts
 * every time a god spell is cast, can only reach a quest by uid through
 * QuestManager.note(uid, key) / .reached(uid, key). So the three counts live
 * here, packed into one persisted stage int, addressed by string key rather
 * than by class.
 *
 * Real RSC: casting a god spell (Claws of Guthix, Saradomin strike, Flames
 * of Zamorak) OUTSIDE the Mage Arena requires having already cast that same
 * spell 100 times INSIDE it. The unlock is permanent per spell once reached
 * and is never re-earned or decayed -- unrelated to Kolodion's shapeshift
 * gauntlet (see MageArena.java), which only unlocks which god's spell/cape a
 * player can pursue in the first place, not the casts themselves.
 *
 * Each counter needs 0-100, which fits in 7 bits (0-127); three of them plus
 * a spare bit come to 22 of the stage int's 32, comfortably clear of
 * Quest.NEVER (Integer.MIN_VALUE) so completed() can never accidentally
 * trigger on a packed value.
 */
public class GodCharges extends Quest {

    public final static int UID = Quests.GOD_CHARGES;

    private static final int NEVER = Integer.MIN_VALUE;
    private static final int CAP = 100;
    private static final int MASK = 0x7F; // 7 bits, 0-127

    private static final int GUTHIX_SHIFT = 0, SARADOMIN_SHIFT = 7, ZAMORAK_SHIFT = 14;

    public GodCharges(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("God spell charges");
        setFinalStage(NEVER);
        // Nothing associated. This record owns no npc, object or item.
    }

    public void completeQuest() {
    }

    private int shiftFor(String god) {
        if ("guthix".equals(god)) return GUTHIX_SHIFT;
        if ("saradomin".equals(god)) return SARADOMIN_SHIFT;
        if ("zamorak".equals(god)) return ZAMORAK_SHIFT;
        return -1;
    }

    /**
     * getStage() is -1, not 0, for a quest never started (Player.getQuestStage's
     * own doc: "-1 if this player has never started it") -- as all bits set,
     * every packed field would read back as its maximum, meaning every new
     * player would start already charged on all three gods. Treated as a
     * fresh zero-everything baseline instead, here, rather than in every
     * caller.
     */
    private int stageOrZero() {
        int s = getStage();
        return s == -1 ? 0 : s;
    }

    private int countOf(String god) {
        int shift = shiftFor(god);
        if (shift < 0) {
            return 0;
        }
        return (stageOrZero() & (MASK << shift)) >>> shift;
    }

    /**
     * "<god>-cast": one more successful cast of that god's spell inside the
     * arena. Saturates at 100 -- there is no reward for going past it, and
     * capping here means the 7-bit field can never overflow into its
     * neighbour.
     *
     * "<god>-charged": read-only elsewhere; nothing else can note() its way
     * to instant-unlock. Unknown keys are silently ignored, same contract
     * as the base class's reached().
     */
    public void note(String key) {
        if (!key.endsWith("-cast")) {
            return;
        }
        String god = key.substring(0, key.length() - "-cast".length());
        int shift = shiftFor(god);
        if (shift < 0) {
            return;
        }
        int current = countOf(god);
        if (current >= CAP) {
            return;
        }
        int cleared = stageOrZero() & ~(MASK << shift);
        setStage(cleared | ((current + 1) << shift));
    }

    /** "<god>-charged": true once 100 in-arena casts of that spell have landed. */
    public boolean reached(String key) {
        if (!key.endsWith("-charged")) {
            return false;
        }
        String god = key.substring(0, key.length() - "-charged".length());
        return countOf(god) >= CAP;
    }
}
