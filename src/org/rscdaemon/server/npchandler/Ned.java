package org.rscdaemon.server.npchandler;

import java.util.Vector;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Ned, Draynor village. Rope, and one wig.
 *
 * A handler rather than part of a quest, for the reason Aggie and Wyson are:
 * Ned belongs to two of them. He makes the wig for Prince Ali rescue and he is
 * the sailor who takes the player to Crandor in Dragon slayer, and an npc that
 * a quest associates is taken away from every other quest entirely.
 *
 * Selling rope is not quest work at all -- rope opens dungeons all over the map
 * -- so it is here unconditionally. The wig only exists while Prince Ali rescue
 * is running and the Crandor offer only while Dragon slayer is, which is how the
 * transcript records both.
 *
 * Neither branch needs to know a quest's stage numbers. Ned asks Dragon slayer
 * "ship-ready" and tells it "ned-agreed", both by name -- see Quest.reached and
 * Quest.note. The quest decides what those mean; this file never sees an integer
 * belonging to it.
 *
 * There are TWO Neds, and that resolves something this file used to apologise
 * for. The note here used to say that Jagex's Ned walks off to Port Sarim once
 * he has agreed and does the rest of his talking aboard the ship, and that we
 * could not reproduce it because an npc who moves for one player has moved for
 * everybody. That was the right reasoning and the wrong conclusion: Jagex did
 * not move him either. They shipped a second npc.
 *
 *     124  Draynor, one spawn. Rope, the wig, and the offer to sail.
 *     194  The ship, two spawns -- one on each of the two ship interiors.
 *
 * Both are named Ned and both examine as "An old sailor", so nothing separates
 * them but the id. 194 was spawned in the world and had no dialogue at all,
 * which is why the Draynor Ned appeared to be the whole of him.
 *
 * The crossing is here now. It used to be missing because boarding the ship
 * sailed it, so a player who could sail never stood in the hold to ask -- that
 * was a routing bug in the quest, since fixed, and asking Ned to cast off is
 * the whole point of putting him aboard.
 *
 * His two spawns are BOTH in the Port Sarim hold. The note here used to read
 * the northern one as the Crandor wreck and gave it the Crandor lines; that was
 * wrong. Jagex built the hold four times -- holed or sound, Ned absent or
 * aboard -- and Ned stands in the two copies he is aboard for:
 *
 *     (280,3474)  holed, Ned    back from Crandor, holed again on the rocks
 *     (279,3494)  sound, Ned    ready to sail
 *
 * There is no ship interior on Crandor at all, and no Ned spawned there, so his
 * two Crandor-wreck lines have nowhere to live. See DragonSlayer's deviations.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class Ned implements NpcHandler {

    private static final int COINS = 10;
    private static final int BALL_OF_WOOL = 207;
    private static final int ROPE = 237;
    /** "wig -- A wig made from wool". The blonde one, 244, is this one dyed. */
    private static final int WIG = 245;

    private static final int ROPE_PRICE = 15;
    private static final int WOOL_FOR_ROPE = 4;
    private static final int WOOL_FOR_WIG = 3;

    /** The on-ship Ned. Same name, same examine, different npc entirely. */
    private static final int NED_ON_SHIP = 194;

    /**
     * The first row of the sound copy of the hold. Everything at or below this
     * y is the holed copy; everything above it is the patched one.
     */
    private static final int HOLD_SOUND_Y = 3480;

    public void handleNpc(Npc npc, Player player) throws Exception {
        if (npc.getID() == NED_ON_SHIP) {
            onShip(npc, player);
            return;
        }
        talkInDraynor(npc, player);
    }

    /**
     * Ned aboard the Lumbridge Lady.
     *
     * Which scene he is in is decided by where he is standing rather than by a
     * quest stage, because there is exactly one of him in each copy of the hold
     * and the copy IS the state. Reading it off position keeps this file's
     * promise not to know Dragon slayer's integers, and it also means he still
     * says something sensible to a player who got below decks by some route the
     * quest did not anticipate.
     *
     * The two copies sit at y 3472-3474 (holed) and y 3493-3495 (sound).
     */
    private void onShip(Npc npc, Player player) {
        if (npc.getY() > HOLD_SOUND_Y) {
            readyToSail(npc, player);
            return;
        }
        holedAgain(npc, player);
    }

    /**
     * The sound hull. He will cast off, if there is anything to steer by.
     *
     * Two greetings, and the difference is whether the player has crossed
     * before: "Hello there" the first time with "So are you going to take me to
     * Crandor Island now then?", "Hello again" afterwards with "Can you take me
     * back to Crandor again?" and a shorter answer. Both end in the same
     * voyage. "lad" or "lass" by the player's own gender -- the wiki records
     * both forms.
     */
    private void readyToSail(Npc npc, Player player) {
        final QuestManager qm = player.getQuestManager();
        final boolean beenBefore = qm.reached(Quests.DRAGON_SLAYER, "sailed");

        if (beenBefore) {
            /*
             * The option label "Can you take me back to Crandor again" has no
             * question mark and the spoken line does. That is the transcript,
             * not a slip.
             */
            new Conversation(player, npc)
                .npc("Hello again " + (player.isMale() ? "lad" : "lass"))
                .picker(new Choice("Can you take me back to Crandor again",
                                   "How did you get back?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.player("Can you take me back to Crandor again?")
                             .npc("Okie Dokie");
                            castOff(c);
                        } else {
                            whale(c);
                        }
                    }
                })
                .start();
            return;
        }

        new Conversation(player, npc)
            .npc("Hello there " + (player.isMale() ? "lad" : "lass"))
            .picker(new Choice("So are you going to take me to Crandor Island now then?",
                               "So are you still up to sailing this ship?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("So are you going to take me to Crandor Island now then?")
                         .npc("Okay show me the map and we'll set sail now");
                        castOff(c);
                    } else {
                        c.player("So are you still up to sailing this ship?")
                         .npc("Well I am a tad rusty")
                         .npc("I'm sure it'll all come back to me, once I get into action")
                         .npc("I hope...");
                    }
                }
            })
            .start();
    }

    /**
     * Handing over the chart and the voyage that follows.
     *
     * Jagex takes the three pieces as readily as the joined map and records a
     * different message for each, which is why the question asked here is
     * "map-ready" and not "do you hold item 415". Without either, the scene
     * simply ends -- he has already said "show me the map", and the transcript
     * gives him nothing further to say to a player who cannot.
     *
     * The voyage itself belongs to the quest: this reports "sail" and stops.
     */
    private void castOff(Conversation c) {
        final QuestManager qm = c.getPlayer().getQuestManager();
        if (!qm.reached(Quests.DRAGON_SLAYER, "map-ready")) {
            return;
        }
        c.message(qm.reached(Quests.DRAGON_SLAYER, "map-joined")
                ? "You give the map to ned"
                : "You give the parts of the map to ned")
         .player("Here it is")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.getPlayer().getQuestManager().note(Quests.DRAGON_SLAYER, "sail");
             }
         });
    }

    /**
     * The hull holed a second time, on the rocks at Crandor, with Ned aboard.
     *
     * Reachable since the quest grew a persisted var for "sailed AND the hull
     * is broken": every crossing cracks her open again, and boarding routes
     * here until the player re-patches the hole.
     */
    private void holedAgain(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Hello again " + (player.isMale() ? "lad" : "lass"))
            .picker(new Choice("Can you take me back to Crandor again",
                               "How did you get back?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("Can you take me back to Crandor again?")
                         .npc("Well I would, but the last adventure")
                         .npc("Hasn't left this tub in the best of shapes")
                         .npc("You'll have to fix it again");
                    } else {
                        whale(c);
                    }
                }
            })
            .start();
    }

    /**
     * How he got the Lumbridge Lady home. It used to be printed on boarding the
     * wreck at Crandor, which is the wrong end of the journey -- see
     * DragonSlayer.boardAtCrandor().
     */
    private void whale(Conversation c) {
        c.player("How did you get back?")
         .npc("I got towed back by a passing friendly whale");
    }

    private void talkInDraynor(Npc npc, Player player) throws Exception {
        Conversation c = new Conversation(player, npc)
            .npc("Why hello there, me friends call me Ned")
            .npc("I was a man of the sea, but its past me now")
            .npc("Could I be making or selling you some Rope?");

        // Built rather than written out because two of the four lines are
        // conditional and the answer indices have to follow them.
        final boolean wig = wigWanted(player);
        final boolean crandor = crandorWanted(player);
        Vector<String> lines = new Vector<String>();
        lines.add("Yes, I would like some Rope");
        if (wig) {
            lines.add("Ned, could you make other things from wool?");
        }
        if (crandor) {
            lines.add("You're a sailor? Could you take me to the Isle of Crandor");
        }
        lines.add("No thanks Ned, I don't need any");

        c.options(new Choice(lines.toArray(new String[lines.size()])) {
            public void picked(int option, Conversation c) {
                int at = 0;
                if (option == at++) {
                    rope(c);
                    return;
                }
                if (wig && option == at++) {
                    wool(c);
                    return;
                }
                if (crandor && option == at++) {
                    crandor(c);
                    return;
                }
                noThanks(c);
            }
        });
        c.start();
    }

    /**
     * Whether the wig is worth talking about: Prince Ali rescue started and not
     * yet finished. The quest's own stage numbers live in the quest class, which
     * is loaded from quests/ into the default package and cannot be imported
     * from here, so this asks the only two questions that cross that line.
     */
    private boolean wigWanted(Player player) {
        QuestManager qm = player.getQuestManager();
        return qm.stageOf(Quests.PRINCE_ALI_RESCUE) > 0
            && !qm.completed(Quests.PRINCE_ALI_RESCUE);
    }

    /**
     * Whether Crandor is worth talking about: Dragon slayer started and not yet
     * finished. Same two questions the wig asks, of a different quest.
     */
    private boolean crandorWanted(Player player) {
        QuestManager qm = player.getQuestManager();
        return qm.stageOf(Quests.DRAGON_SLAYER) > 0
            && !qm.completed(Quests.DRAGON_SLAYER);
    }

    private void noThanks(Conversation c) {
        c.npc("Well, old Neddy is always here if you do")
         .npc("Tell your friends, I can always be using the business");
    }

    // --------------------------------------------------------------- rope --

    private void rope(Conversation c) {
        c.npc("Well, I can sell you some rope for " + ROPE_PRICE + " coins")
         .npc("Or I can be making you some if you gets me " + WOOL_FOR_ROPE + " balls of wool")
         .npc("I strands them together I does, makes em strong")
         .options(new Choice("Okay, please sell me some Rope",
                             "I have some balls of wool. could you make me some Rope?",
                             "I will go and get some wool",
                             "Thats a little more than I want to pay") {
             public void picked(int option, Conversation c) {
                 switch (option) {
                     case 0:
                         buyRope(c);
                         break;
                     case 1:
                         spinRope(c);
                         break;
                     case 2:
                         c.npc("Aye, you do that")
                          .npc("Remember, it takes " + WOOL_FOR_ROPE + " balls of wool to make strong rope");
                         break;
                     default:
                         c.npc("Well, if you ever need rope. thats the price. sorry")
                          .npc("An old sailor needs money for a little drop o rum.");
                         break;
                 }
             }
         });
    }

    private void buyRope(Conversation c) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                if (p.getInventory().countId(COINS) < ROPE_PRICE) {
                    p.getActionSender().sendMessage("You don't have enough coins");
                    c.stop();
                    return;
                }
                p.getInventory().remove(COINS, ROPE_PRICE);
                p.getInventory().add(new InvItem(ROPE, 1));
                p.getActionSender().sendInventory();
            }
        })
         .npc("There you go, finest rope in Runescape");
    }

    private void spinRope(Conversation c) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                if (p.getInventory().countId(BALL_OF_WOOL) < WOOL_FOR_ROPE) {
                    p.getActionSender().sendMessage("You don't have " + WOOL_FOR_ROPE
                        + " balls of wool");
                    c.stop();
                    return;
                }
                p.getInventory().remove(BALL_OF_WOOL, WOOL_FOR_ROPE);
                p.getInventory().add(new InvItem(ROPE, 1));
                p.getActionSender().sendInventory();
            }
        })
         .npc("Sure I can.");
    }

    // ------------------------------------------------------------- crandor --

    /**
     * The old sailor, asked to sail again.
     *
     * The second answer only exists once the Lumbridge Lady will float, which is
     * Dragon slayer's judgement and not this file's -- hence the question by
     * name. Agreeing is reported back the same way.
     */
    private void crandor(Conversation c) {
        final boolean ready = c.getPlayer().getQuestManager()
            .reached(Quests.DRAGON_SLAYER, "ship-ready");

        c.npc("Well I was a sailor")
         .npc("I've not been able to get work at sea these days though")
         .npc("They say I am too old")
         .npc("I miss those days")
         .npc("If you could get me a ship I would take you anywhere");

        if (!ready) {
            c.player("I will work on finding a sea worthy ship then");
            return;
        }

        c.options(new Choice("As it happens I do have a ship ready to sail",
                             "I will work on finding a sea worthy ship then") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                c.npc("That'd be grand, where is it")
                 .player("It's called the Lumbrige Lady and it's docked in Port Sarim")
                 .npc("I'll go right over there and check her out then")
                 .npc("See you over there")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().getQuestManager()
                          .note(Quests.DRAGON_SLAYER, "ned-agreed");
                     }
                 });
            }
        });
    }

    // ---------------------------------------------------------------- wig --

    private void wool(Conversation c) {
        c.npc("I am sure I can. What are you thinking of?")
         .options(new Choice("How about some sort of a wig?",
                             "Could you knit me a sweater?",
                             "Could you repair the arrow holes in the back of my shirt?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Do I look like a member of a sewing circle?")
                      .npc("Be off wi' you, I have fought monsters that would turn your hair blue")
                      .npc("I don't need to be laughed at just 'cos I am getting a bit old");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Ah yes, its a tough world these days")
                      .npc("Theres a few brave enough to attack from 10 metres away")
                      .npc("There you go, good as new")
                      .player("Thanks Ned, maybe next time they will attack me face to face");
                     return;
                 }
                 c.npc("Well... Thats an interesting thought")
                  .npc("yes, I think I could do something")
                  .npc("Give me " + WOOL_FOR_WIG + " balls of wool and I might be able to do it");
                 wig(c);
             }
         });
    }

    private void wig(Conversation c) {
        c.options(new Choice("I have that now. Please, make me a wig",
                             "great, I will get some. I think a wig would be useful",
                             "I will come back when I need you to make me one") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    return;
                }
                if (option == 2) {
                    c.npc("Well, it sounds like a challenge")
                     .npc("come to me if you need one");
                    return;
                }
                c.npc("Okay, I will have a go.")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         Player p = c.getPlayer();
                         if (p.getInventory().countId(BALL_OF_WOOL) < WOOL_FOR_WIG) {
                             p.getActionSender().sendMessage("You don't have " + WOOL_FOR_WIG
                                 + " balls of wool");
                             c.stop();
                             return;
                         }
                         p.getInventory().remove(BALL_OF_WOOL, WOOL_FOR_WIG);
                         p.getInventory().add(new InvItem(WIG, 1));
                         p.getActionSender().sendInventory();
                     }
                 })
                 .npc("Here you go, hows that for a quick effort? Not bad I think!")
                 .player("Thanks Ned, theres more to you than meets the eye");
            }
        });
    }
}
