import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Jungle potion. Released 23 October 2002, written by Tytn Hays.
 *
 * Trufitus Shakaya of Tai Bwo Wannai needs five jungle herbs to brew a potion
 * that will let him ask his gods what is to become of his village. He gives
 * out one clue at a time and takes one herb at a time, so the quest is five
 * identical rounds: he names a herb, you search the right thing for it, you
 * identify what you found, you bring it back.
 *
 * That "one at a time" is the quest's only real rule and it is enforced here:
 * searching a plant before Trufitus has named its herb finds nothing, because
 * that is what the real game did.
 *
 *     round  herb            item      grows on
 *     0      Snake Weed      815 -> 816  Jungle Vine    564, (470..471, 794..795)
 *     1      Ardrigal        817 -> 818  PalmTree       553, six of them at (394..400, 756..763)
 *     2      Sito Foil       819 -> 820  Scorched Earth 554, (444..447, 778..780)
 *     3      Volencia Moss   821 -> 822  Rocks          555, (412,794)
 *     4      Rogues Purse    823 -> 824  Cavern wall    door 151, the cave walls
 *
 * All five of those ids belong to this quest and nothing else: Jungle Vine,
 * PalmTree 553, Scorched Earth, the moss Rocks 555 and the Cavern wall exist
 * only in and under Tai Bwo Wannai, so any of their placements will do. The
 * moss rock is still claimed by placement with associateObject(id, x, y) to
 * keep the claim exact.
 *
 * Trufitus is npc 517 at (436,751). Zadimus, the other speaker in this quest's
 * transcript, belongs to Shilo village and is not touched.
 *
 * Two deviations:
 *
 *  - The five unidentified herbs carry the command "Identify" and are not in
 *    ItemUnIdentHerbDef, which only knows the ten ordinary herbs 444 to 453.
 *    So this quest claims items 815 to 823 and answers "Identify" itself. It
 *    keeps answering after the quest is over, which is right: a herb picked up
 *    afterwards still has to be identifiable.
 *  - The moss rock 555 is Mine/Search and the cavern wall is WalkTo/search;
 *    both clicks of each are accepted, and the message calls it a search
 *    either way, which is the wiki's word too.
 *
 *  - The moss rock needed no inference after all: Jagex shipped a dedicated
 *    def for it -- 555 "Rocks", Mine / Search, "A moss covered rock" -- with
 *    exactly one placement in the world, (412,794). An earlier version of
 *    this file guessed the rune rock 210 at (428,819) in the mine instead,
 *    which left the real rock answering "Nothing interesting happens".
 *
 * Requires Druidic ritual, which is where herblaw comes from.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class JunglePotion extends Quest {

    public final static int UID = Quests.JUNGLE_POTION;

    private static final int TRUFITUS = 517;

    /** Stage n+1 means "looking for herb n"; stage 6 is done. */
    private static final int FINISHED = 6;

    private static final String[] HERB_NAMES = {
        "Snake Weed", "Ardrigal", "Sito Foil", "Volencia Moss", "Rogues Purse"
    };
    private static final int[] UNIDENTIFIED = { 815, 817, 819, 821, 823 };
    private static final int[] IDENTIFIED = { 816, 818, 820, 822, 824 };
    private static final int CAVERN_WALL = 151;
    private static final int[] GROWS_ON = { 564, 553, 554, 555, CAVERN_WALL };

    /**
     * The moss rock 555 has exactly one placement in the world, but it is
     * claimed by placement to keep the claim exact.
     *
     * Rogues Purse comes off the cave's own walls: DoorDef 151 "Cavern wall",
     * WalkTo / search, "It looks as if it is covered in some fungus". An
     * earlier version of this file guessed two Fungus 205 placements instead,
     * which left every cavern wall answering "Nothing interesting happens".
     * The name "Cavern wall" exists nowhere outside this cave, so the whole
     * id is claimed.
     */
    private static final int ROCK = 555;
    private static final int[][] ROCK_AT = { { 412, 794 } };

    private static final int HERBLAW = 15; /* skill index */

    public JunglePotion(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Jungle potion");
        setFinalStage(FINISHED);
        associateNpc(TRUFITUS);
        for (int i = 0; i < UNIDENTIFIED.length; i++) {
            associateItem(UNIDENTIFIED[i]);
        }
        /* Vine, palm and scorched earth grow nowhere in the world but here. */
        // @share object 564 with LegendsQuest
        // @share object 553 with LegendsQuest
        // Snake Weed and Ardrigal are also the two halves of the Gujuo potion,
        // and Legend's quest can be reached without ever meeting Trufitus. Both
        // quests answer the search; whichever of them is currently asking for
        // that herb spawns it, and the other only says there is nothing there.
        associateObject(564);
        associateObject(553);
        associateObject(554);
        for (int i = 0; i < ROCK_AT.length; i++) {
            associateObject(ROCK, ROCK_AT[i][0], ROCK_AT[i][1]);
        }
        associateDoor(CAVERN_WALL);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Trufitus Shakaya of the Taie Bwo Wannai Village requires that you collect five special jungle herbs for a potion so he can commune with his Gods.");
        setStartPoint("Deep in Karamja jungle");
        setSpeakTo("Trufitus");
        setMissionLength("Short");
        require("Must fight Level 58 Ogres");
        rewardExp(HERBLAW, 400, 125);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Jungle potion quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            if (trigger == QuestTrigger.NPC_TALK) {
                talkToTrufitus((Npc) entity);
            }
        } else if (entity instanceof GameObject) {
            search((GameObject) entity);
        } else if (entity instanceof InvItem && trigger == QuestTrigger.ITEM_COMMAND) {
            identify((InvItem) entity);
        }
    }

    // ----------------------------------------------------------- searching --

    /**
     * Search whatever the current clue points at.
     *
     * The herb lands on the ground rather than in the pack, which is what the
     * real game did and which the wiki is explicit about: "each herb appears on
     * the ground and will need to be picked up manually". Nothing checks
     * whether one is already lying there. The quest has no fail state -- the
     * wiki records that searching again simply yields another herb -- and a
     * player who walks off without picking one up has to be able to come back
     * for it.
     */
    private void search(GameObject object) {
        Player p = getOwner();
        int round = getStage() - 1;
        if (!questStarted() || completed() || object.getID() != GROWS_ON[round]) {
            /*
             * A claimed placement that is not the one Trufitus has named, or is
             * the right one too early. You cannot gather a herb before he tells
             * you about it, so there is nothing here yet.
             */
            p.getActionSender().sendMessage("You search but find nothing of interest");
            return;
        }
        /* On the searched thing's own tile -- except the cavern wall, whose
           tile is the wall itself: that herb lands where the player stands. */
        int hx = object.getID() == CAVERN_WALL ? p.getX() : object.getX();
        int hy = object.getID() == CAVERN_WALL ? p.getY() : object.getY();
        world.registerItem(new Item(UNIDENTIFIED[round], hx, hy, 1, p));
        p.getActionSender().sendMessage("You find a herb");
    }

    /**
     * "Identify". Which herb an unidentified jungle herb turns out to be is
     * fixed -- there is no chance in it and no level requirement, because
     * Trufitus has already told you what you are looking for.
     */
    private void identify(InvItem item) {
        Player p = getOwner();
        for (int i = 0; i < UNIDENTIFIED.length; i++) {
            if (item.getID() != UNIDENTIFIED[i]) {
                continue;
            }
            p.getInventory().remove(UNIDENTIFIED[i], 1);
            p.getInventory().add(new InvItem(IDENTIFIED[i], 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You identify the herb as " + HERB_NAMES[i]);
            return;
        }
    }

    // ---------------------------------------------------------- Trufitus --

    private void talkToTrufitus(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            /*
             * Shilo village claims Trufitus too, and the dispatcher runs every
             * claiming quest at once. Shilo stays silent until it has started
             * (its own trufitus() says so); this is the mirror image -- once
             * it has, all of Trufitus's talk is Shilo's business and this
             * quest's thanks-for-the-herbs speech retires, or the two
             * conversations interleave line by line.
             */
            Quest shilo = p.getQuestManager().getQuest(Quests.SHILO_VILLAGE);
            if (shilo != null && shilo.getStage() > 0) {
                return;
            }
            new Conversation(p, npc)
                .npc("My greatest respects Bwana")
                .npc("I have communed with the gods")
                .npc("and the future looks good for my people")
                .npc("We are happy now that the gods are not angry with us")
                .npc("With some blessings we will be safe here.")
                .start();
            return;
        }
        if (questStarted()) {
            handIn(npc);
            return;
        }
        offerQuest(npc);
    }

    /**
     * His opening. Three of the four openings loop back to the same offer,
     * which the transcript marks with "above" and "below"; picked() rebuilding
     * the menu is how that is done here.
     */
    private void offerQuest(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Greetings Bwana,")
         .npc("I am Trufitus Shakaya of the")
         .npc("Taie Bwo Wannai Village.")
         .npc("Welcome to our humble settlement.");
        opening(c);
        c.start();
    }

    private void opening(Conversation c) {
        c.options(new Choice("What does Bwana mean?",
                             "Taie Bwo Wannai? What does that mean?",
                             "It's a nice village, where is everyone?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Gracious sir, it means 'friend'")
                     .npc("And friends come in peace")
                     .npc("I assume that you come in peace?")
                     .options(new Choice("Yes, of course I do!",
                                         "What does a warrior like me know about peace?") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("When you grow weary of violence")
                                  .npc("and seek a more enlightened path")
                                  .npc("please pay me a visit")
                                  .npc("as I may have a proposal for you")
                                  .npc("Now I need to attend to the plight")
                                  .npc("of my people, please excuse me");
                                 return;
                             }
                             c.npc("Well, that is good news")
                              .npc("as I may have a proposition for you")
                              .options(new Choice("A proposition eh, sounds interesting!",
                                                  "I am sorry, but I am very busy") {
                                  public void picked(int option, Conversation c) {
                                      if (option != 0) {
                                          farewell(c);
                                          return;
                                      }
                                      c.npc("I hoped that you would think so.")
                                       .npc("My people are afraid to stay in the village.")
                                       .npc("They have returned to the jungle")
                                       .npc("I need to commune with the gods")
                                       .npc("to see what fate befalls us")
                                       .npc("you could help me by collecting")
                                       .npc("some herbs that I need.");
                                      explain(c);
                                  }
                              });
                         }
                     });
                } else if (option == 1) {
                    c.npc("It means 'small clearing in the jungle'")
                     .npc("But now it is the name of our village.");
                    opening(c);
                } else {
                    c.npc("My people are afraid to stay in the village")
                     .npc("They have returned to the jungle")
                     .npc("I need to commune with my gods")
                     .npc("to see what fate befalls us")
                     .npc("You may be able to help with this")
                     .options(new Choice("Me, how can I help?",
                                         "I am sorry, but I don't have time for that.") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 explain(c);
                             } else {
                                 farewell(c);
                             }
                         }
                     }.says(1, "I am very sorry, but I don't have time for that."));
                }
            }
        }.says(2, "It seems like a nice village, where is everyone?"));
    }

    private void explain(Conversation c) {
        c.npc("I need to make a special brew")
         .npc("A potion that helps me to commune with the gods.")
         .npc("For this potion, I need very")
         .npc("special herbs that are only found in")
         .npc("deep jungle")
         .npc("I can guide you only so far as the")
         .npc("herbs are not easy to find")
         .npc("With some luck, you will find each herb in turn")
         .npc("and bring it to me. I will give you")
         .npc("details of where to find the next herb.")
         .npc("In return I will give you training in Herblaw")
         .options(new Choice("It sounds like just the challenge for me.",
                             "Hmm, sounds difficult, I don't know if I am ready for the challenge") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("Very well then Bwana")
                      .npc("maybe you will return to me invigorated")
                      .npc("and ready to take up the challenge one day ?");
                     return;
                 }
                 c.player("And it would make a nice break from killing things !")
                  .npc("That is excellent then Bwana!")
                  .npc("The first herb you need to gather is called")
                  .npc("'Snake Weed'")
                  .npc("It grows near vines in an area to the south west")
                  .npc("where the ground turns soft and water kisses your feet.")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(1);
                      }
                  });
             }
         });
    }

    private void farewell(Conversation c) {
        c.npc("Very well then Bwana")
         .npc("may your journeys bring you much joy")
         .npc("maybe you will pass this way again")
         .npc("and you will then take up my proposal,")
         .npc("but for now")
         .npc("fare thee well");
    }

    // ------------------------------------------------------- handing in --

    private void handIn(Npc npc) {
        Player p = getOwner();
        final int round = getStage() - 1;
        Conversation c = new Conversation(p, npc);
        ask(c, round);
        if (p.getInventory().countId(IDENTIFIED[round]) <= 0) {
            /*
             * Claiming to have it when you do not is a real branch in the
             * transcript and it deserves to survive: he does not believe you.
             */
            c.options(new Choice("Of Course!", "Not yet, sorry.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Please don't try to deceive me!");
                    }
                    nag(c, round);
                }
            });
            c.start();
            return;
        }
        c.player("Of Course!")
         .take(IDENTIFIED[round], 1)
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(round + 2);
             }
         });
        thanks(c, round);
        c.start();
    }

    private void ask(Conversation c, int round) {
        switch (round) {
        case 0:
            c.npc("Hello Bwana, do you have the Snake Weed?");
            break;
        case 1:
            c.npc("Hello again, have you been able to get the Ardrigal ?");
            break;
        case 2:
            c.npc("Greetings Bwana")
             .npc("have you been successful in getting Sito Foil?");
            break;
        case 3:
            c.npc("Greetings Bwana")
             .npc("Do you have the 'Volencia Moss' ?");
            break;
        default:
            c.npc("Have you found 'Rogues Purse' ?");
        }
    }

    private void nag(Conversation c, int round) {
        switch (round) {
        case 0:
            c.npc("I really need that Snake Weed if I am to make this potion");
            break;
        case 1:
            c.npc("I still require Ardrigal,")
             .npc("this potion will remain incomplete without it.");
            break;
        case 2:
            c.npc("I still require Sito Foil, every herb is vital.");
            break;
        case 3:
            c.npc("I know it is difficult to find, but I do need Volencia Moss")
             .npc("After that herb, you only have one more to find.");
            break;
        default:
            c.npc("Rogues Purse is the last herb")
             .npc("for the potion and possibly the most")
             .npc("difficult to find but I do need it.");
        }
    }

    /** Each thank-you carries the next clue, which is how the quest advances. */
    private void thanks(Conversation c, int round) {
        switch (round) {
        case 0:
            c.npc("Great, you have the 'Snake Weed'")
             .npc("Ok, the next herb is called, 'Ardrigal'")
             .npc("it is related to the palm and grows")
             .npc("to the East in its brother's shady profusion.")
             .npc("Many thanks for the 'Snake Weed'");
            break;
        case 1:
            c.npc("Ah, I see you have found the 'Ardrigal'")
             .npc("you are doing well Bwana, the next")
             .npc("herb is called, 'Sito Foil' and grows best")
             .npc("where the ground has been blackened")
             .npc("by the living flame.");
            break;
        case 2:
            c.npc("Well done Bwana, just two more herbs")
             .npc("to collect. The next herb is called, 'Volencia Moss'")
             .npc("And it clings to rocks for it's existence")
             .npc("It is difficult to see, so you must search for it well.");
            break;
        case 3:
            c.npc("Ah, Volencia Moss, beautiful!")
             .npc("One final herb and the potion will")
             .npc("be complete. This is the most difficult to")
             .npc("find as it inhabits the darkness of the")
             .npc("underground. It is called 'Rogues Purse'")
             .npc("And is found in the darkest place on the Island")
             .npc("A secret entrance to the caverns is set into")
             .npc("The Northern cliffs of this land")
             .npc("Take care Bwana as it may be very dangerous");
            break;
        default:
            c.npc("Most excellent Bwana!")
             .npc("You have returned all the herbs to me")
             .npc("and I can now finish the preparations")
             .npc("for the potion and thankfully divine with the gods.")
             .npc("Many blessings on you!")
             .npc("I must now prepare")
             .npc("please excuse me while I make")
             .npc("the arrangements");
        }
    }
}
