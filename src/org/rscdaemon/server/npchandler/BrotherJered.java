package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;

/**
 * Brother Jered, the Monastery north of Falador. He blesses a holy symbol of
 * Saradomin, which is the only way a symbol ever gets its prayer bonus.
 *
 * He was spawned in the world with nothing wired to him at all, so the blessed
 * symbol -- item 385, prayer bonus 8 -- was unobtainable by any route. The
 * unblessed one (45) is craftable and has a prayer bonus of 0, so the whole
 * point of making it was missing.
 *
 * Three item states, and they are three different items rather than one item
 * with a flag:
 *
 *   44  "Holy Symbol of saradomin"   "This needs a string putting on it"
 *   45  "Unblessed Holy Symbol"      "This needs blessing"
 *   385 "Holy Symbol of saradomin"   "This improves my prayer"
 *
 * 44 and 385 share a name and differ only by description, so anything that
 * matches these by name rather than by id will pick the wrong one.
 *
 * There is no level requirement and no quest requirement on the blessing
 * itself. The gate is positional: 31 Prayer is needed to reach the Monastery's
 * top floor, and that check belongs to the stairs, not to Jered. Nothing here
 * asks the player for anything.
 *
 * Dialogue is Jagex's, from Transcript:Brother Jered.
 */
public class BrotherJered implements NpcHandler {

    public static final World world = World.getWorld();

    public static final int JERED = 176;

    /** Strung but not blessed -- the one he takes. */
    private static final int UNBLESSED = 45;
    /** Not yet strung. He will not take this one, and says why. */
    private static final int UNSTRUNG = 44;
    /** Blessed. Prayer bonus 8. */
    private static final int BLESSED = 385;

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .options(new Choice("What can you do to help a bold adventurer such as myself?",
                                "Praise be to Saradomin") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        offerBlessing(c);
                    } else {
                        c.npc("Yes praise he who brings life to this world");
                    }
                }
            })
            .start();
    }

    /**
     * The three outcomes of the first question, in priority order.
     *
     * The transcript lists the no-symbol case first and the unstrung case
     * second, which cannot be read as a plain if/else chain -- a player holding
     * an unstrung symbol satisfies "does not have an unblessed Holy Symbol"
     * too. The only consistent reading is that the more specific state wins, so
     * the checks run most-specific first here and produce the transcript's
     * answer for every one of the three states.
     */
    private static void offerBlessing(Conversation c) {
        Player player = c.getPlayer();
        if (player.getInventory().countId(UNBLESSED) > 0) {
            c.npc("Well I can bless that star of Saradomin you have")
             /* picker() rather than options(), purely so the two halves of this
                menu can differ. The transcript records the option as "No
                thankyou" and the player's spoken line as "No Thankyou", both
                marked sic, and that capitalisation asymmetry is exactly the
                pattern this era of Jagex writing really did have. options()
                echoes the option text verbatim as the spoken line and so cannot
                reproduce it; picker() leaves the line to us. Do not "tidy"
                these into agreement. */
             .picker(new Choice("Yes Please", "No thankyou") {
                 public void picked(int option, Conversation c) {
                     if (option != 0) {
                         c.player("No Thankyou");
                         return;
                     }
                     c.player("Yes Please")
                      // Four server messages, not speech -- the transcript
                      // marks all four {{mes}} while every line above it is
                      // marked as Jered speaking.
                      .message("You give Jered the symbol")
                      .take(UNBLESSED, 1)
                      .message("Jered closes his eyes and places his hand on the symbol")
                      .message("He softly chants")
                      .give(new InvItem(BLESSED, 1))
                      .message("Jered passes you the holy symbol");
                 }
             });
            return;
        }
        if (player.getInventory().countId(UNSTRUNG) > 0) {
            /* The transcript renders the second line as: I can bless it for
               you" -- with a stray closing quote, wiki-marked sic. Dropped, on
               the judgement that it is a transcription artefact rather than
               something Jagex shipped: it is an unmatched delimiter in the
               middle of a file whose other sic marks are all ordinary spelling
               and capitalisation slips. */
            c.npc("Well if you put a string on that holy symbol")
             .npc("I can bless it for you");
            return;
        }
        c.npc("If you have a silver star")
         .npc("Which is the holy symbol of Saradomin")
         .npc("Then I can bless it")
         .npc("Then if you are wearing it")
         .npc("It will help you when you are praying");
    }
}
