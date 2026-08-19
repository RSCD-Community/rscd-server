import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.Quests;

/**
 * Which of Varrock's two crime gangs the player joined.
 *
 * Not a quest. It is a record, kept as one because a quest is the only thing
 * this server persists per player besides skills and inventory: Player holds a
 * map of quest id to stage, the save packet writes the whole map, and ids at
 * Quests.FIRST_CUSTOM and above are ours to use. So a hidden quest with an
 * unreachable final stage is a persisted integer with a name on it, and costs
 * nothing else.
 *
 * It exists because Shield of Arrav spends its own stage on the answer. A
 * player who joins the Phoenix gang is at stage 12, one who joins the Black
 * arms is at 22 -- and both are at 31 when the king pays out, at which point
 * the gang is gone. Vanilla remembered: Straven sets his dogs on a Black arm
 * who walks into the Phoenix store afterwards, Katrine will not talk to a
 * Phoenix man at all, and Hero's quest sends you to whichever leader is yours
 * for the master thief armband. None of that can work off one integer that has
 * already been spent, so the gang is written down here as well, once, at the
 * moment of joining, and never changed again.
 *
 * Nothing outside reads a stage number: Shield of Arrav reports the joining
 * through note(), and Hero's quest asks through reached(). The stock client has
 * no row in its quest tab for id 1000 and will never be asked to draw one --
 * QuestManager.fillCompletion() skips anything past the end of Jagex's list --
 * and Quests.points(1000) is nought, so this awards nothing.
 */
public class GangMembership extends Quest {

    public final static int UID = Quests.FIRST_CUSTOM;

    /** Not a stage anything can reach, so completed() is never true. */
    private static final int NEVER = Integer.MIN_VALUE;

    private static final int PHOENIX = 1;
    private static final int BLACK_ARM = 2;

    public GangMembership(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Gang membership");
        setFinalStage(NEVER);
        // Nothing is associated. This record owns no npc, no object and no
        // item; it is only ever spoken to through note() and reached().
    }

    /** Never called: there is no final stage to arrive at. */
    public void completeQuest() {
    }

    public boolean reached(String key) {
        if ("phoenix".equals(key)) {
            return getStage() == PHOENIX;
        }
        if ("black-arm".equals(key)) {
            return getStage() == BLACK_ARM;
        }
        return "member".equals(key)
            && (getStage() == PHOENIX || getStage() == BLACK_ARM);
    }

    /**
     * Reported by Shield of Arrav when a gang takes the player in.
     *
     * The first answer sticks. Nobody has ever been in both gangs, and a
     * second joining would mean something has gone wrong somewhere upstream
     * rather than that the player changed sides.
     */
    public void note(String key) {
        if (getStage() == PHOENIX || getStage() == BLACK_ARM) {
            return;
        }
        if ("joined-phoenix".equals(key)) {
            setStage(PHOENIX);
        } else if ("joined-black-arm".equals(key)) {
            setStage(BLACK_ARM);
        }
    }
}
