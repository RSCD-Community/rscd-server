package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Thordur, the dwarf who runs the Black Hole Experience.
 *
 * Real RSC history: for 10gp Thordur sold a Disk of Returning, letting a
 * curious player descend a ladder into his mock "Black Hole" -- a pocket of
 * emptiness dressed up as the punishment cell moderators once teleported
 * rule-breakers into. Spinning the disk while inside was the only way out.
 * The feature was pulled in early 2002 after players tricked each other into
 * dropping the disk while trapped, leaving them stuck until a moderator
 * fished them out -- the disk itself was left in the game, permanently
 * unobtainable, and became one of RSC's most infamous "useless items."
 *
 * RSCD never built any of this -- the NPCDef entries for Thordur (175) and
 * the Disk of Returning (387, already carrying its "spin" command) sat
 * unused. Built now, for real, with the actual exploit fixed at the root:
 * the disk cannot be dropped while inside the Black Hole (see DropHandler),
 * so nobody can be trapped there against their will the way that exploit
 * worked. It also now doubles as the real destination for ::ban -- see
 * CommandHandler -- so a banned player and a curious visitor can meet inside
 * it, a few tiles apart, without being able to trade.
 */
public class ThordurHandler implements NpcHandler {

    private static final World world = World.getWorld();

    private static final int COINS = 10;
    private static final int DISK_PRICE = 10;
    private static final int DISK = 387;

    public static final int THORDUR_X = 308, THORDUR_Y = 3348;
    /* Inside the hole. Both spots must satisfy Point.inBlackHole -- see the
       comment there for why they are where they are, and for the crash that
       came of the pair they used to be. */
    public static final int VISITOR_X = 316, VISITOR_Y = 2726;
    public static final int BANNED_X = 310, BANNED_Y = 2726;

    /*
     * The dialogue below is Transcript:Thordur, verbatim. An earlier pass
     * wrote this conversation from scratch on the belief that no record of
     * it survived; the transcript covers the whole tree, so all of that
     * invented text is gone. Jagex's own capitalisation is kept as found,
     * including "No Thankyou" as one word and the "Mind numbing boredom"
     * option that capitalises a word its own spoken line does not.
     *
     * Thordur opens unprompted and gives the same greeting whether or not
     * you already own a disk -- there is no "you've been here before"
     * branch in the real dialogue, so there isn't one here.
     *
     * THE ONE SUBSTITUTION, marked OURS at both call sites: Jagex's Thordur
     * tells you to climb down the ladder, and the ladder was its own piece
     * of scenery with its own dialogue. Ladder id 199 has exactly one
     * placement in our entire map, at (51,438), nowhere near Thordur at
     * (308,3348) -- expected, because the hole was removed from the Dwarven
     * Mine on 26 February 2002 and our landscape is 2018-era. So the
     * descent is offered as a dialogue option instead. Adding a 199
     * GameObjectLoc at the hole would let the authentic ladder flow work
     * and this substitution be deleted; that is a map edit, so it is the
     * user's call rather than something to slip in here.
     */
    public void handleNpc(Npc npc, Player player) throws Exception {
        Conversation c = new Conversation(player, npc);
        c.npc("Would you like to go on the black hole experience");
        c.options(new Choice("Yes please", "No Thankyou", "What's the black hole experience?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    offer(c);
                    return;
                }
                if (option == 1) {
                    /* The wiki marks this branch missing -- nobody recorded
                       what Thordur says to a refusal here. Ending without a
                       reply is wrong, but inventing one would be worse and
                       harder to spot later. */
                    return;
                }
                c.npc("Experience the mind numbing boredom")
                 .npc("As experienced by runescape's criminals and rulebreakers")
                 .npc("But with the comfort of being able to leave at any time")
                 .options(new Choice("Yes please", "No thanks, I don't enjoy Mind numbing boredom") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             offer(c);
                             return;
                         }
                         c.npc("Me neither")
                          .npc("I'm not well suited for this job");
                     }
                 }.says(1, "No thanks", "I don't enjoy mind numbing boredom"));
            }
        });
        c.start();
    }

    /** The price quote and the second confirm. Buying is two steps in the
        real dialogue, not one. */
    private static void offer(Conversation c) {
        c.npc("Ok it will cost you " + DISK_PRICE + " gold coins")
         .npc("For which you get to enter the hole")
         .npc("And get a magic disk which lets you escape again")
         .options(new Choice("Yes that sounds good", "Oh I'm not paying money for it") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     return;
                 }
                 Player p = c.getPlayer();
                 if (p.getInventory().countId(COINS) < DISK_PRICE) {
                     // OURS -- nothing attested for turning up short.
                     c.npc("Come back when you have the coin");
                     return;
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         Player p = c.getPlayer();
                         p.getInventory().remove(COINS, DISK_PRICE);
                         p.getInventory().add(new InvItem(DISK, 1));
                         p.getActionSender().sendInventory();
                     }
                 });
                 c.message("You pay the dwarf ten coins and get given a disk")
                  .npc("Ok you may climb down the ladder to the black hole")
                  .npc("Have fun");
                 descend(c);
             }
         /* The transcript records the spoken line with a stray closing quote
            ("I'm not paying money for it"") -- a wiki transcription slip
            rather than a Jagex sic, so it is dropped. */
         }.says(1, "I'm not paying money for it"));
    }

    /**
     * OURS -- stands in for the missing ladder scenery, see the class note.
     * Everything spoken above this point is Jagex's; this option is not.
     */
    private static void descend(Conversation c) {
        c.options(new Choice("Climb down into the black hole", "Not just yet") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                c.message("You climb down the ladder into the darkness");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(VISITOR_X, VISITOR_Y, false);
                    }
                });
            }
        });
    }
}
