package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Conversation;

/**
 * The four Gnome Trainers who work the Gnome Stronghold agility course. Nine
 * npcs between them and every one of them was silent, on the one course in the
 * game a player is meant to run laps of.
 *
 * Transcript:Gnome Trainer, ordinary Jagex banner. Twenty-two recorded lines
 * across four fixed conversations -- these are not random tables. Each trainer
 * says one thing, always, which is why this is a switch and not a roll.
 *
 * ------------------------------------------------------- which is which ----
 *
 * The transcript has SIX headings for FOUR trainers, because two of the four
 * were recorded twice under different names. Building six tables would be
 * wrong; the duplicates are byte-identical, not variants:
 *
 *   "The one near the log"  ==  "at the start of the course or by the end
 *                               side of the pipe"          (8 lines)  -> 576
 *   "Pickpocketable trainers"  ==  "after climbing the last net"
 *                                                          (7 lines)  -> 579
 *
 * The mapping is settled by course geometry, and the coordinates are worth
 * writing down because they are what makes it certain rather than plausible.
 * Course 1 in defs/extras/ObjectAgility.xml.gz is this course, seven stages:
 *
 *   stage 0  log     (692,495) -> (692,499)
 *   stage 1  net     (692,502) -> (692,1450)      [1450 = plane 1]
 *   stage 2  tower   (693,1451) -> (692,2395)     [2395 = plane 2]
 *   stage 3  rope    (689,2395) -> (688,2395)
 *   stage 4  down    (684,2396) -> (684,508)
 *   stage 5  net     (683,503) -> (683,501)
 *   stage 6  pipe    (683,498) -> (683,494)
 *
 * against the nine trainer spawns in locs/NpcLoc.xml.gz:
 *
 *   576  (696,492) (682,492)   log start, and one tile off the pipe exit
 *   577  (689,504) (696,502)   either side of the first net
 *   578  (690,1450) (694,1451) on the plane-1 platform the net lands on
 *   579  (681,500) (685,500) (687,502)   around the last net and the pipe
 *
 * 576 having exactly two spawns, one at each of the two places its heading
 * names, is the confirmation.
 *
 * AND THERE IS A SECOND, INDEPENDENT CONFIRMATION for 579, which is the one
 * that would otherwise rest on geometry alone. The transcript titles that table
 * "Pickpocketable trainers". Of the four trainer ids, exactly one has an entry
 * in defs/extras/NpcPickpocket.xml.gz: 579. A heading about thieving and a
 * thieving table, from two files that know nothing about each other, naming the
 * same npc.
 *
 * ------------------------------------------------------- what is missing ----
 *
 * Five more recorded spoken lines are NOT here, and they are not an oversight.
 * They happen DURING an obstacle. Four are the trainer shouting -- "move it,
 * move it, move it" as you climb the net, "that's it, straight up, no messing
 * around" on the tower, "my granny can move faster than you" at the last net,
 * and "that's the way, well done" out of the pipe -- and the fifth is the
 * player's "ooof" on dropping off the watchtower. They belong to the obstacle,
 * not to talking to an npc, and ObjectAgilityDef has no npc field and no way to
 * speak. Wiring them would be a framework change, not a dialogue addition, so
 * they are logged and left for that decision.
 *
 * (Four, counted off the transcript, not six. Saying six would have put a wrong
 * number in front of a decision that has not been made yet.)
 *
 * The same section of the transcript shows our obstacle messages are shorter
 * than Jagex's -- ours is the single string "You walk across the log" where the
 * recording has "you stand on the slippery log" then "and walk across" -- and
 * that stage 2 is a tree in the transcript and a tower in our data. Both are
 * held for the same reason: <message> is one string per obstacle.
 *
 * ---------------------------------------------------------------- sic ------
 *
 * Five, all shipped as recorded: "agilty", "obstical", "im" with no apostrophe,
 * "say's" with a wrong one, and the spacing in "go, go ,go ,go".
 */
public class GnomeTrainers implements NpcHandler {

    public static final World world = World.getWorld();

    /** Log start and pipe exit. Two spawns, one at each. */
    public static final int TRAINER_LOG = 576;
    /** Beside the first net, ground level. */
    public static final int TRAINER_NET = 577;
    /** On the platform the first net lands on, plane 1. */
    public static final int TRAINER_PLATFORM = 578;
    /** Last net and pipe. The only pickpocketable one. */
    public static final int TRAINER_LAST_NET = 579;

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case TRAINER_LOG:
                trainerLog(npc, player);
                return;
            case TRAINER_NET:
                trainerNet(npc, player);
                return;
            case TRAINER_PLATFORM:
                trainerPlatform(npc, player);
                return;
            case TRAINER_LAST_NET:
                trainerLastNet(npc, player);
                return;
        }
    }

    /**
     * 576 -- the course explained. The only one of the four that answers a
     * question rather than barking, which fits: he is the one you meet first.
     */
    private void trainerLog(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello, what is this place?")
            .npc("this my friend, is where we train")
            .npc("it improves our agility, an essential skill")
            .player("looks easy enough")
            .npc("if you complete the course...")
            .npc("from the slippery log to the end")
            .npc("your agilty will increase much faster..")
            .npc(".. than repeating one obstical")
            .start();
    }

    /** 577 -- beside the first net. */
    private void trainerNet(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello")
            .npc("this isn't a granny's tea party")
            .npc("let's see some sweat human")
            .npc("go, go ,go ,go")
            .start();
    }

    /**
     * 578 -- on the platform. The only one of the four that does NOT open with
     * a greeting: the player's first line is "this is fun", said to a trainer
     * you have just climbed a net to reach. Recorded that way, kept that way.
     */
    private void trainerPlatform(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("this is fun")
            .npc("this is training soldier")
            .npc("if you want fun, go make some cocktails")
            .start();
    }

    /** 579 -- last net and pipe, and the one you can pickpocket. */
    private void trainerLastNet(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello")
            .npc("hi")
            .player("how are you?")
            .npc("im amazed by how much you humans chat")
            .npc("the sign say's training area...")
            .npc("..not pointless conversation area")
            .npc("now move it soldier")
            .start();
    }
}
