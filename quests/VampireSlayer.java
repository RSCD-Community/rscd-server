import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Vampire slayer.
 *
 * Morgan of Draynor village asks for help against the vampire in the manor
 * cellar and sends the player to Dr Harlow, a retired vampire hunter who now
 * drinks in the Jolly Boar inn. Buy him a beer and he hands over a stake and the
 * three things worth knowing: the killing blow must be struck with the stake, a
 * hammer is needed to drive it in, and garlic weakens the Count.
 *
 * Three quest points and attack experience of Level x 150 + 325.
 *
 * The Count does not stand in the cellar waiting. He has no spawn at all -- the
 * only trace of him in the world files is his coffin, object 136, at (204,3380)
 * in the cellar under Draynor Manor, and searching that is what lets him out. So
 * this quest owns both halves of the coffin, open (136) and shut (135). RSCD has
 * a second one of these at (668,3281) that vanilla never placed; it is left
 * alone but answered for, since claiming an object id claims every instance.
 *
 * One deliberate departure from vanilla, and it is worth stating plainly. In the
 * real game a vampire beaten without a stake never dies: he regenerates on the
 * spot and the fight simply carries on. Here the engine settles a death inside
 * Npc.killedBy() -- unregistering the npc and awarding the experience -- before
 * any quest is told about it, and there is no way to refuse a kill after the
 * fact without reaching into all three combat paths. So the Count does die, and
 * is immediately put back where he fell at full health with a message saying he
 * regenerated. The player keeps the combat experience for the round. He drops
 * only bones, so nothing is duplicated by this.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class VampireSlayer extends Quest {

    public final static int UID = Quests.VAMPIRE_SLAYER;

    private static final int STARTED = 1;
    /** Dr Harlow has explained the stake and handed one over. */
    private static final int HAS_STAKE = 2;
    private static final int FINISHED = 3;

    private static final int MORGAN = 97;
    private static final int HARLOW = 98;
    private static final int COUNT_DRAYNOR = 96;

    private static final int COFFIN_CLOSED = 135;
    private static final int COFFIN_OPEN = 136;

    /**
     * The cellar under Draynor Manor. Underground is the surface square plus
     * three floors of 944, so (204,3380) is (204,548) -- the manor itself.
     */
    private static final int COFFIN_X = 204;
    private static final int COFFIN_Y = 3380;

    /**
     * Where the Count lands, which is deliberately NOT the coffin's own tile.
     *
     * He used to be released onto (204,3380), standing in the coffin that let
     * him out. A coffin is a blocking scenery object, and an npc sharing a
     * tile with one cannot be reached or clicked -- the player watched a
     * vampire they could neither talk to nor attack until he happened to wander
     * off it. World.registerNpc does not catch this: its guard is
     * isBlockedGround, which reads the landscape's own blocking bit and knows
     * nothing about objects standing on top of it.
     *
     * (205,3382) and the roam box around it are Jagex's, and both
     * reimplementations agree on them exactly.
     */
    private static final int COUNT_X = 205;
    private static final int COUNT_Y = 3382;
    private static final int COUNT_MIN_X = 202;
    private static final int COUNT_MAX_X = 207;
    private static final int COUNT_MIN_Y = 3379;
    private static final int COUNT_MAX_Y = 3385;

    private static final int BEER = 193;
    private static final int STAKE = 217;
    private static final int HAMMER = 168;
    private static final int GARLIC = 218;

    /** How much of the Count's 35 hitpoints carrying garlic costs him. */
    private static final int GARLIC_PENALTY = 10;

    /**
     * What garlic takes off the Count's attack/strength, and separately off his
     * defence, as a fraction of base. Defence is the one that matters: 65 down
     * to 39 is the difference between a level-3 landing a blow and not.
     *
     * These proportions are the one part of this that is NOT verified. Both
     * reimplementations apply the drain at the same moment and print the same
     * message, but they disagree on the amount -- Core-Framework uses 10/10/40
     * and says in a comment that it is an approximation open to correction,
     * rsc-server uses a flat 25 across all three. Core-Framework's is taken
     * here because singling defence out matches what the effect is for. If the
     * real figures ever turn up, this is the only place to change.
     */
    private static final double GARLIC_DRAIN = 0.10;
    private static final double GARLIC_DEFENCE_DRAIN = 0.40;

    /* Formulae.statArray order, which is what Npc.getBaseStat/drain use. */
    private static final int STAT_ATTACK = 0;
    private static final int STAT_DEFENCE = 1;
    private static final int STAT_STRENGTH = 2;

    /**
     * Whether the Count now in the world has already been weakened by garlic.
     * Reset when he is released, since that builds a new Npc; without it,
     * walking away and re-engaging would drain him again every time.
     */
    private boolean garlicApplied = false;

    private static final int ATTACK = 0; /* skill index */

    public VampireSlayer(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Vampire slayer");
        setFinalStage(FINISHED);
        associateNpc(MORGAN);
        associateNpc(HARLOW);
        associateNpc(COUNT_DRAYNOR);
        associateObject(COFFIN_CLOSED);
        associateObject(COFFIN_OPEN);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The people of Draynor village live in constant terror. Their numbers are dwindling, all due to the foul creature lurking in the manor to the north known as a vampire.");
        setStartPoint("Draynor village");
        setSpeakTo("Morgan");
        setMissionLength("Medium");
        require("Able to defeat a vampire");
        rewardExp(ATTACK, 325, 150);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Vampire slayer quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == COUNT_DRAYNOR) {
                    killedCount(npc);
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            if (npc.getID() == MORGAN) {
                talkToMorgan(npc);
            } else if (npc.getID() == HARLOW) {
                talkToHarlow(npc);
            } else if (npc.getID() == COUNT_DRAYNOR) {
                // He is not here for conversation.
                new Conversation(getOwner(), npc).npc("Get out of my house!").start();
            }
            return;
        }
        if (entity instanceof GameObject) {
            useCoffin(trigger, (GameObject) entity);
        }
    }

    // ------------------------------------------------------------- morgan --

    private void talkToMorgan(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("How are you doing with your quest?")
             .player("I have slain the foul creature")
             .npc("Thank you, thank you")
             .npc("You will always be a hero in our village")
             .start();
            return;
        }

        if (questStarted()) {
            c.npc("How are you doing with your quest?")
             .player("I'm working on it still")
             .npc("Please hurry")
             .npc("Every day we live in fear of lives")
             .npc("That we will be the vampires next victim")
             .start();
            return;
        }

        c.npc("Please please help us, bold hero")
         .player("What's the problem?")
         .npc("Our little village has been dreadfully ravaged by an evil vampire")
         .npc("There's hardly any of us left")
         .npc("We need someone to get rid of him once and for good")
         .options(new Choice("Ok I'm up for an adventure",
                             "No. vampires are scary") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("I don't blame you");
                     return;
                 }
                 c.npc("I think first you should seek help")
                  .npc("I have a friend who is a retired vampire hunter")
                  .npc("Called Dr Harlow")
                  .npc("He may be able to give you some tips")
                  .npc("He can normally be found in the Jolly boar inn these days")
                  .npc("He's a bit of an old soak")
                  .npc("Mention his old friend Morgan")
                  .npc("I'm sure he wouldn't want his old friend to be killed by a vampire")
                  .player("I'll look him up then")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  });
             }
         });
        c.start();
    }

    // ---------------------------------------------------------- dr harlow --

    private void talkToHarlow(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Buy me a drrink pleassh");

        // Outside the quest he is simply a drunk who wants a beer. The Morgan
        // branch only exists between accepting the quest and being given the
        // stake; before and after that, both here and in the transcript, the
        // conversation is the same two lines.
        final boolean asking = questStarted() && getStage() == STARTED;

        if (!asking) {
            c.options(new Choice("No you've had enough", "Ok mate") {
                public void picked(int option, Conversation c) {
                    if (option != 1) {
                        return;
                    }
                    if (!buyDrink(c)) {
                        c.player("I'll just go buy one");
                        return;
                    }
                    c.npc("Cheersh matey");
                }
            });
            c.start();
            return;
        }

        /* Option order is Jagex's, and it is not the convenient one: the
           refusal comes first and the quest line last. Worth leaving alone
           even though it puts the only option that advances anything at the
           bottom -- "Ok mate" here is a decoy that costs a beer and returns
           nothing, which is exactly how the real one behaved. */
        c.options(new Choice("No you've had enough", "Ok mate", "Morgan needs your help") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    return;
                }
                if (option == 1) {
                    if (!buyDrink(c)) {
                        c.player("I'll just go and buy one");
                        return;
                    }
                    c.npc("Cheersh matey");
                    return;
                }
                c.npc("Morgan you shhay?")
                 .player("His village is being terrorised by a vampire")
                 .player("He wanted me to ask you how i should go about stopping it")
                 .npc("Buy me a beer then i'll teash you what you need to know")
                 .options(new Choice("Ok mate", "But this is your friend Morgan we're talking about") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Buy ush a drink anyway");
                             return;
                         }
                         if (!buyDrink(c)) {
                             c.player("I'll just go and buy one");
                             return;
                         }
                         c.npc("Cheersh matey")
                          .player("So tell me how to kill vampires then")
                          .npc("Yesh yesh vampires I was very good at killing em once")
                          .message("Dr Harlow appears to sober up slightly")
                          .npc("Well you're gonna to kill it with a stake")
                          .npc("Otherwishe he'll just regenerate")
                          .npc("Yes your killing blow must be done with a stake")
                          .npc("I jusht happen to have one on me")
                          .give(new org.rscdaemon.server.model.InvItem(STAKE, 1))
                          .message("Dr Harlow hands you a stake")
                          .npc("You'll need a hammer to hand to drive it in properly as well")
                          .npc("One last thing")
                          .npc("It's wise to carry garlic with you")
                          .npc("Vampires are weakened somewhat if they can smell garlic")
                          .npc("Dunno where you'd find that though")
                          .npc("Remember even then a vampire is a dangeroush foe")
                          .player("Thank you very much")
                          .then(new Effect() {
                              public void run(Conversation c) {
                                  setStage(HAS_STAKE);
                              }
                          });
                     }
                 });
            }
        });
        c.start();
    }

    /**
     * Hand over a beer if there is one. False means the player has none, which
     * is the branch where they say they will go and buy one.
     *
     * The message is Jagex's and it is not decoration. Only the beer bought
     * inside the Morgan branch buys anything; the "Ok mate" at the top of the
     * conversation takes one and gives back a "Cheersh matey", which means a
     * player who picks it expecting to advance the quest watches beer after
     * beer disappear with nothing on screen to say where they went. Without
     * this line the inventory is the only evidence, and it was missing.
     */
    private boolean buyDrink(Conversation c) {
        Player p = c.getPlayer();
        if (p.getInventory().countId(BEER) < 1) {
            return false;
        }
        p.getInventory().remove(BEER, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You give a beer to Dr Harlow");
        return true;
    }

    // ------------------------------------------------------------- coffin --

    private void useCoffin(QuestTrigger trigger, GameObject coffin) {
        Player p = getOwner();
        if (coffin.getID() == COFFIN_CLOSED) {
            open(coffin, COFFIN_OPEN);
            return;
        }
        // The open coffin: click one searches it, click two shuts it again.
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            open(coffin, COFFIN_CLOSED);
            return;
        }
        if (coffin.getX() != COFFIN_X || coffin.getY() != COFFIN_Y) {
            // Some other coffin. Claiming an object id claims every one of them
            // on the map, so this quest has to answer for the rest as well.
            p.getActionSender().sendMessage("You search the coffin but find nothing");
            return;
        }
        if (world.getNpc(COUNT_DRAYNOR, coffin.getX() - 4, coffin.getX() + 4,
                         coffin.getY() - 4, coffin.getY() + 4) != null) {
            p.getActionSender().sendMessage("The coffin is empty");
            return;
        }
        p.getActionSender().sendMessage("A vampire jumps out of the coffin");
        releaseCount(COUNT_X, COUNT_Y);
    }

    private void open(GameObject coffin, int becomes) {
        world.registerGameObject(new GameObject(coffin.getLocation(), becomes,
                                                coffin.getDirection(), coffin.getType()));
        getOwner().getActionSender().sendSound(becomes == COFFIN_OPEN ? "opendoor" : "closedoor");
    }

    /**
     * Put the Count in the world.
     *
     * He never respawns on his own: he has no NpcLoc, and one left to respawn
     * would stand in the cellar for every player who came after, quest or no
     * quest. Whoever opens the coffin is the reason he is there.
     */
    private void releaseCount(int x, int y) {
        Npc count = new Npc(COUNT_DRAYNOR, x, y,
                            COUNT_MIN_X, COUNT_MAX_X, COUNT_MIN_Y, COUNT_MAX_Y);
        count.setRespawn(false);
        garlicApplied = false;
        world.registerNpc(count);
    }

    /**
     * Garlic, which is asked about on arrival and not before.
     *
     * This used to be settled when the coffin was opened: whoever was carrying
     * garlic at that moment got a Count with ten fewer hitpoints, and anyone
     * who fetched the garlic afterwards got nothing for it, with no way to tell
     * -- which is exactly backwards from what Dr Harlow says the clove is for
     * ("vampires are weakened somewhat if they can smell garlic"). Carrying it
     * into the fight is the whole mechanic, so the fight is when to ask.
     *
     * Both reimplementations do it here and print this same line. Nothing is
     * "used" on the vampire in any of them -- garlic has no target action, it
     * only ever has to be in the bag.
     */
    public boolean refusesAttack(Npc npc) {
        if (npc.getID() == COUNT_DRAYNOR) {
            applyGarlic(npc);
        }
        return false;
    }

    private void applyGarlic(Npc count) {
        Player p = getOwner();
        if (garlicApplied || p.getInventory().countId(GARLIC) < 1) {
            return;
        }
        garlicApplied = true;
        count.drain(STAT_ATTACK,   (int) (count.getBaseStat(STAT_ATTACK)   * GARLIC_DRAIN));
        count.drain(STAT_STRENGTH, (int) (count.getBaseStat(STAT_STRENGTH) * GARLIC_DRAIN));
        count.drain(STAT_DEFENCE,  (int) (count.getBaseStat(STAT_DEFENCE)  * GARLIC_DEFENCE_DRAIN));
        if (count.getHits() > GARLIC_PENALTY) {
            count.setHits(count.getHits() - GARLIC_PENALTY);
        }
        p.getActionSender().sendMessage("The vampire appears to weaken");
    }

    // -------------------------------------------------------------- fight --

    private void killedCount(Npc count) {
        Player p = getOwner();
        boolean staked = p.getInventory().wielding(STAKE)
                      && p.getInventory().countId(HAMMER) > 0;
        if (!staked) {
            p.getActionSender().sendMessage("The vampire is beaten, but without a stake through his heart");
            p.getActionSender().sendMessage("he regenerates and rises again");
            releaseCount(count.getX(), count.getY());
            return;
        }
        p.getActionSender().sendMessage("You drive the stake through the vampire's heart");
        if (questStarted() && !completed()) {
            setStage(getFinalStage());
        }
    }
}
