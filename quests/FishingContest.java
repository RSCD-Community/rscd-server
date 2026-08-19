import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Fishing contest. Released 28 May 2002, written by Paul Gower.
 *
 * The mountain dwarves want a gold artifact, the artifact is first prize at the
 * Hemenster fishing competition, and dwarves cannot fish. Win it for them and
 * they open the tunnel under White Wolf mountain.
 *
 *     Mountain Dwarf       npc 355, three spawns at the two tunnel mouths
 *     Morris               npc 349, (563,493), on the competition gate
 *     Bonzo                npc 347, (566,491), running the competition
 *     Sinister stranger    npc 346, (568,489), a vampire, by the pipes
 *     Big Dave             npc 353, (570,500)
 *     Joshua               npc 354, (570,503)
 *     Grandpa Jack         npc 345, (560,485), who used to win it
 *     Forester             npc 348, three spawns on McGrubor's wood
 *
 *     competition gate     object 358, (564,492)
 *     the outflow pipes    object 350, (569..571,488)
 *     the fishing spots    objects 351 (571,495) by the oak tree,
 *                          352 (570,489) by the pipes,
 *                          353 (572,500) Big Dave's, 354 (572,503) Joshua's
 *     the red vine         object 355, six tiles at (567..569,449..454)
 *     loose fence panels   door 101, (540,445), the east fence of the wood
 *     the tunnel stairs    object 359, (426,458) and (385,466)
 *
 *     competition pass     item 719      trophy         item 720
 *     red vine worms       item 715      raw giant carp item 717
 *     garlic 218, spade 211, fishing rod 377
 *
 * The four fishing spots are ids of their own -- 351 to 354, each with exactly
 * one placement, none of them in ObjectFishing -- so they are the competition's
 * and nothing else's. Same for the pipes (350, three tiles, nowhere else) and
 * for the red vine (355, six tiles, all in McGrubor's wood; the ordinary vines
 * scattered round the world are ids 218, 219 and 220).
 *
 * Deviations:
 *
 *  - The Sinister stranger does not actually move when the garlic goes on the
 *    pipes. He is a world npc shared by everybody and one player's garlic is
 *    not everybody's, so what changes is who owns which spot for that player.
 *    Bonzo's line about taking the area by the pipes is the whole of it, which
 *    is what the player sees anyway. The transcript records two different
 *    refusals for the two spots, one for each side of that move, and both are
 *    keyed off the same per-player flag: he claims the pipes before it and the
 *    oak after it.
 *
 *  - There is no clock on the competition. Bonzo calls time when the player
 *    tells him about the carp, exactly as the transcript has it; a real timer
 *    would be a shared one on a shared npc.
 *
 *  - The gate into McGrubor's wood was a woodcutting 70 gate with an invented
 *    npc behind it. Vanilla has the Forester refuse everyone and the loose
 *    fence panel as the only way in. The panel is restored here -- its "push"
 *    command was falling through WallObjectAction and doing nothing at all --
 *    and the gate is now the Forester's again, in ObjectAction.
 *
 *  - Object 359 leads nowhere in the shipped data: it has no ObjectTelePoints
 *    entry, so both tunnel mouths were dead. The destinations below are what
 *    ObjectAction.coordModifier() would have computed for a height-3 object
 *    facing direction 4 -- one floor down and three tiles south -- which lands
 *    on the matching pair of up-stairs, object 43 at (426,3290) and (385,3298).
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class FishingContest extends Quest {

    public final static int UID = Quests.FISHING_CONTEST;

    private static final int DWARF = 355, MORRIS = 349, BONZO = 347;
    private static final int STRANGER = 346, BIG_DAVE = 353, JOSHUA = 354;
    private static final int GRANDPA_JACK = 345, FORESTER = 348;

    private static final int GATE = 358, GATE_X = 564, GATE_Y = 492;
    private static final int PIPES = 350;
    private static final int SPOT_OAK = 351, SPOT_PIPES = 352;
    private static final int SPOT_DAVE = 353, SPOT_JOSHUA = 354;
    private static final int VINE = 355;
    private static final int FENCE = 101, FENCE_X = 540, FENCE_Y = 445;
    private static final int STAIRS = 359;

    private static final int PASS = 719, TROPHY = 720, WORMS = 715;
    private static final int RAW_CARP = 717, GARLIC = 218, SPADE = 211;
    private static final int ROD = 377, COINS = 10, ENTRY_FEE = 5;

    private static final int FISHING = 10, FISHING_LEVEL = 10;

    private static final int STARTED = 1;
    private static final int SMELLY = 2;   /* garlic on the pipes */
    private static final int ENTERED = 4;  /* five gold paid */
    private static final int BY_PIPES = 8; /* moved to the good spot */
    private static final int CAUGHT = 16;  /* the giant carp is out of the water */
    private static final int WON = 32;     /* Bonzo handed over the trophy */
    private static final int TOLD = 64;    /* and the dwarves have it */
    private static final int FINISHED = 127;

    public FishingContest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Fishing contest");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The mountain Dwarves home would be an ideal way to get across White Wolf mountain safely. However the Dwarves aren't to fond of strangers. They will let you through if you can bring them a trophy. The trophy is the prize for the annual Hemenster fishing competition.");
        setStartPoint("The foot of White Wolf mountain");
        setSpeakTo("Mountain Dwarf");
        setMissionLength("Medium");
        requireLevel(FISHING, FISHING_LEVEL);
        /* The xp is a stepped table, not the base + level * rate shape --
           (level + 3) * 75, plus a flat 200 from level 24 up -- so it stays in
           completeQuest() below. */
        rewardOther("Fishing experience, on a table that steps up at fishing level 24");
        rewardOther("Use of the dwarves' tunnel under White Wolf mountain");

        associateNpc(DWARF);
        associateNpc(MORRIS);
        associateNpc(BONZO);
        associateNpc(STRANGER);
        associateNpc(BIG_DAVE);
        associateNpc(JOSHUA);
        associateNpc(GRANDPA_JACK);
        associateNpc(FORESTER);
        associateObject(GATE);
        associateObject(PIPES);
        associateObject(SPOT_OAK);
        associateObject(SPOT_PIPES);
        associateObject(SPOT_DAVE);
        associateObject(SPOT_JOSHUA);
        associateObject(VINE);
        associateObject(STAIRS);
        /* Wholesale rather than by placement, unlike Jungle potion's rock:
           "push" is not a command WallObjectAction implements, so the two
           other fences with loose panels in Ardougne do nothing today and
           there is no behaviour to hand back. They are refused by coordinate
           below. */
        associateDoor(FENCE);
    }

    public void completeQuest() {
        Player p = getOwner();
        p.getActionSender().sendMessage("Well done.You have completed the Fishing competition quest");
        /* (Level + 3) * 75, with a flat 200 on top from level 24 up. This is
         * the one quest reward the wiki records as a full ninety-row table
         * rather than a formula, which is what makes the step at 24 visible at
         * all: 23 pays 1,950 and 24 pays 2,225.
         *
         * What was here before -- Level * 250 + 1500 -- paid 4,000 to a level
         * 10 fisherman who should get 975, and it is the only reward in the
         * fifty quests that was wrong in shape rather than in a constant. */
        int level = p.getMaxStat(FISHING);
        int exp = (level + 3) * 75 + (level >= 24 ? 200 : 0);
        p.incExp(FISHING, exp, false);
        p.getActionSender().sendStat(FISHING);
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
                fence(object);
            } else {
                object(object, used);
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.ITEM_ON_NPC) {
            if (npc.getID() == STRANGER && used != null && used.getID() == GARLIC) {
                new Conversation(getOwner(), npc)
                    .npc("urrggh get zat horrible ving avay from me")
                    .npc("How do people like to eat that stuff")
                    .npc("I can't stand even to be near it for ten seconds")
                    .start();
            } else {
                getOwner().getActionSender().sendMessage("Nothing interesting happens");
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case DWARF:        dwarf(npc);        break;
            case BONZO:        bonzo(npc);        break;
            case GRANDPA_JACK: grandpaJack(npc);  break;
            case STRANGER:     stranger(npc);     break;
            case MORRIS:       morris(npc, false); break;
            case FORESTER:     forester(npc);     break;
            default:           break; /* Big Dave and Joshua do not talk */
        }
    }

    // ------------------------------------------------------------ scenery --

    private void object(GameObject object, InvItem used) {
        Player p = getOwner();
        switch (object.getID()) {
            case STAIRS:      stairs(object);        return;
            case GATE:        gate(object);          return;
            case PIPES:       pipes(used);           return;
            case VINE:        vine(used);            return;
            case SPOT_PIPES:  fishPipes();           return;
            case SPOT_OAK:    fishOak();             return;
            case SPOT_DAVE:   warnedOff(BIG_DAVE);   return;
            case SPOT_JOSHUA: warnedOff(JOSHUA);     return;
            default:          return;
        }
    }

    private void stairs(GameObject object) {
        Player p = getOwner();
        if (!completed()) {
            p.getActionSender().sendMessage("The dwarves won't let you down there");
            p.getActionSender().sendMessage("You haven't earned their trust yet");
            return;
        }
        p.teleport(object.getX(), object.getY() + 2832 + 3, false);
    }

    private void gate(GameObject object) {
        Player p = getOwner();
        if (object.getX() != GATE_X || object.getY() != GATE_Y) {
            return;
        }
        boolean leaving = p.getX() > GATE_X;
        if (leaving) {
            p.teleport(GATE_X, GATE_Y, false);
            return;
        }
        Npc morris = world.getNpc(MORRIS, GATE_X - 4, GATE_X + 4, GATE_Y - 4, GATE_Y + 4);
        if (morris == null) {
            p.teleport(GATE_X + 1, GATE_Y, false);
            return;
        }
        morris(morris, true);
    }

    private void pipes(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != GARLIC) {
            p.getActionSender().sendMessage("It's a dirty sewer pipe");
            return;
        }
        if (!questStarted() || has(SMELLY)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(GARLIC, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You rub the garlic all over the pipes");
        p.getActionSender().sendMessage("They now smell very strongly of garlic");
        set(SMELLY);
        if (!has(ENTERED) || has(BY_PIPES)) {
            return;
        }
        /*
         * Garlic after the competition has started, which is the ordering the
         * transcript is actually headed with ("When Competition has started and
         * garlic has been placed in the pipe") and the only one we did not
         * handle: the move was written into Bonzo's entry speech and nowhere
         * else, so a player who paid the 5gp first and rubbed the pipes second
         * set SMELLY, never got BY_PIPES, and had no way to get it -- the oak
         * gave no bites, the pipes were refused, and Bonzo had nothing left to
         * say. Dead end, and the more natural order of the two.
         *
         * He says the same two lines here as he does in Bonzo's version. Bonzo
         * is not standing next to the pipes, so his "you'd better go and take
         * the area by the pipes then" belongs to that telling and not this one.
         */
        Npc stranger = strangerNpc();
        if (stranger == null) {
            set(BY_PIPES);
            return;
        }
        new Conversation(p, stranger)
            .npc("Arrgh what is that ghastly smell")
            .npc("I think I will move over here instead")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(BY_PIPES);
                }
            })
            .start();
    }

    private void vine(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != SPADE) {
            p.getActionSender().sendMessage("A creepy creeper");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(WORMS, 1))) {
            p.getActionSender().sendMessage("You don't have room for that");
            return;
        }
        p.getInventory().add(new InvItem(WORMS, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You dig around in the vines");
        p.getActionSender().sendMessage("You find some red vine worms");
    }

    /** Big Dave and Joshua are quite firm about their spots. */
    private void warnedOff(int who) {
        Player p = getOwner();
        Npc npc = world.getNpc(who, 565, 576, 494, 508);
        if (npc == null) {
            p.getActionSender().sendMessage("Someone is already fishing here");
            return;
        }
        if (who == BIG_DAVE) {
            new Conversation(p, npc)
                .npc("Oi whaddya think ya doin'")
                .npc("I'm fishin' here")
                .npc("Now beat it")
                .start();
        } else {
            new Conversation(p, npc)
                .npc("This is my fishing spot")
                .npc("Ya don't wanna be fishing 'ere mate")
                .npc("Cos I'll break your knuckles")
                .start();
        }
    }

    private void fishOak() {
        Player p = getOwner();
        if (!has(ENTERED)) {
            p.getActionSender().sendMessage("You aren't in the competition");
            return;
        }
        if (has(BY_PIPES)) {
            /* The garlic has driven him over here, so the oak is his now and
               the refusal is his one-liner rather than the grey message we
               used to print. */
            Npc stranger = strangerNpc();
            if (stranger == null) {
                p.getActionSender().sendMessage("You've moved to the spot by the pipes");
                return;
            }
            new Conversation(p, stranger)
                .npc("I think you will find that is my spot")
                .start();
            return;
        }
        if (p.getInventory().countId(ROD) < 1) {
            p.getActionSender().sendMessage("You need a fishing rod to fish here");
            return;
        }
        p.getActionSender().sendMessage("You cast out your line...");
        p.getActionSender().sendMessage("You don't seem to be getting any bites here");
    }

    private void fishPipes() {
        Player p = getOwner();
        if (!has(BY_PIPES)) {
            if (has(ENTERED)) {
                /* He is still on the pipes, and he says so himself -- the four
                   lines below are the whole exchange, and the reason he gives
                   for not moving is the reason the garlic works. "The Sinister
                   stranger is fishing here" was ours. */
                Npc stranger = strangerNpc();
                if (stranger == null) {
                    p.getActionSender().sendMessage("The Sinister stranger is fishing here");
                    return;
                }
                new Conversation(p, stranger)
                    .npc("I think you will find that is my spot")
                    .player("Can't you go to another spot?")
                    .npc("I like this place")
                    .npc("I like to savour the aroma coming from these pipes")
                    .start();
            } else {
                p.getActionSender().sendMessage("You aren't in the competition");
            }
            return;
        }
        if (p.getInventory().countId(ROD) < 1) {
            p.getActionSender().sendMessage("You need a fishing rod to fish here");
            return;
        }
        if (p.getInventory().countId(WORMS) < 1) {
            p.getActionSender().sendMessage("You need some bait");
            p.getActionSender().sendMessage("Grandpa Jack might know what these fish like");
            return;
        }
        if (p.getMaxStat(FISHING) < FISHING_LEVEL) {
            p.getActionSender().sendMessage("You need a fishing level of "
                + FISHING_LEVEL + " to catch these");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(RAW_CARP, 1))) {
            p.getActionSender().sendMessage("You don't have room for that");
            return;
        }
        p.getInventory().remove(WORMS, 1);
        p.getInventory().add(new InvItem(RAW_CARP, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You cast out your line...");
        p.getActionSender().sendMessage("@gre@You catch a giant carp!");
        p.incExp(FISHING, 400, true);
        p.getActionSender().sendStat(FISHING);
        if (!has(CAUGHT)) {
            set(CAUGHT);
        }
    }

    private void fence(GameObject door) {
        Player p = getOwner();
        if (door.getX() != FENCE_X || door.getY() != FENCE_Y) {
            return; /* the two in Ardougne, which have never done anything */
        }
        p.getActionSender().sendMessage("You squeeze through the loose panels");
        /* Direction 1: the panel stands between (540,445) and (539,445), so the
           crossing is the same one openOrdinaryDoor() makes for any east-west
           wall. RSCD had this half-built -- it let players out of the wood and
           answered "You can't seem to get through" to anyone trying to get in,
           which left the red vine unreachable without woodcutting 70. */
        p.teleport(p.getX() >= FENCE_X ? FENCE_X - 1 : FENCE_X, door.getY(), false);
    }

    // ------------------------------------------------------------ talkers --

    private void dwarf(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Welcome oh great fishing champion")
                .npc("Feel free to pop by any time")
                .start();
            return;
        }
        if (questStarted()) {
            if (p.getInventory().countId(TROPHY) > 0) {
                new Conversation(p, npc)
                    .npc("Have you won yet?")
                    .player("Yes I have")
                    .npc("Well done, so where is the trophy?")
                    .player("I have it right here")
                    .take(TROPHY, 1)
                    .npc("Okay we will let you in now")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            set(TOLD);
                        }
                    })
                    .start();
                return;
            }
            if (has(WON)) {
                new Conversation(p, npc)
                    .npc("Have you won yet?")
                    .player("Yes I have")
                    .npc("Well done, so where is the trophy?")
                    .player("I don't have it with me")
                    .start();
                return;
            }
            if (p.getInventory().countId(PASS) < 1) {
                new Conversation(p, npc)
                    .npc("Have you won yet?")
                    .options(new Choice("No I need another competition pass",
                                        "No it takes preparation to win fishing competitions") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                c.npc("Hmm its a good job they sent us spares")
                                 .npc("there you go")
                                 .give(new InvItem(PASS, 1));
                            } else {
                                c.npc("Maybe that's where we are going wrong when we try fishing");
                            }
                        }
                    }.says(0, "I need another competition pass"))
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("Have you won yet?")
                .player("No not yet")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("hmmph what do you want")
            .options(new Choice("I was wondering what was down those stairs?",
                                "I was just stopping to say hello") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Hello then");
                        return;
                    }
                    c.npc("You can't go down there");
                    askWhy(c);
                }
            }.says(0, "I was just wondering what was down those stairs?"))
            .start();
    }

    private void askWhy(Conversation c) {
        c.options(new Choice("Why not?",
                             "I'm bigger than you let me by",
                             "I didn't want to anyway") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Go away")
                     .npc("You're not going to bully your way in here");
                    return;
                }
                if (option == 2) {
                    c.npc("Good");
                    return;
                }
                c.npc("This is the home of the mountain dwarves")
                 .npc("How would you like it if I wanted to take a short cut through your home")
                 .options(new Choice("If you were my friend I wouldn't mind it",
                                     "Ooh is this a short cut to somewhere?",
                                     "Oh sorry I hadn't realised it was private") {
                     public void picked(int option, Conversation c) {
                         if (option == 1) {
                             c.npc("Well it is easier to go this way")
                              .npc("Than through passes full of wolves");
                             return;
                         }
                         if (option == 2) {
                             return;
                         }
                         c.npc("Yes, but I don't even know you");
                         beFriends(c);
                     }
                 }.says(0, "If you were my friend I wouldn't mind"));
            }
        }.says(1, "I'm bigger than you", "Let me by"));
    }

    private void beFriends(Conversation c) {
        c.options(new Choice("Well lets be friends", "You're a grumpy little man aren't you") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Don't you know it");
                    return;
                }
                c.npc("I don't make friends easily")
                 .npc("People need to earn my trust first")
                 .options(new Choice("And how am I meant to do that?",
                                     "You're a grumpy little man aren't you") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Don't you know it");
                             return;
                         }
                         c.npc("My we are the persistant one aren't we")
                          .npc("Well theres a certain gold artifact we're after")
                          .npc("We dwarves are big fans of gold")
                          .npc("This artifact is the first prize at the hemenster fishing competition")
                          .npc("Fortunately we have acquired a pass to enter that competition")
                          .npc("Unfortunately Dwarves don't make good fishermen")
                          .options(new Choice("fortunately I'm alright at fishing",
                                              "I'm not much of a fisherman either") {
                              public void picked(int option, Conversation c) {
                                  if (option != 0) {
                                      c.npc("what good are you?");
                                      return;
                                  }
                                  c.npc("Okay I entrust you with our competition pass")
                                   .npc("go to Hemenster and do us proud")
                                   .give(new InvItem(PASS, 1))
                                   .then(new Effect() {
                                       public void run(Conversation c) {
                                           setStage(STARTED);
                                       }
                                   });
                              }
                          });
                     }
                 });
            }
        });
    }

    /**
     * Morris on the gate. He is the same conversation whether the player walked
     * into him or clicked the gate; only the gate version lets anybody through.
     */
    private void morris(Npc npc, boolean atGate) {
        Player p = getOwner();
        final boolean pass = p.getInventory().countId(PASS) > 0 || has(ENTERED);
        if (pass) {
            Conversation c = new Conversation(p, npc)
                .npc("competition pass please")
                .npc("Move on through");
            if (atGate) {
                c.then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(GATE_X + 1, GATE_Y, false);
                    }
                });
            }
            c.start();
            return;
        }
        new Conversation(p, npc)
            .npc("competition pass please")
            .options(new Choice("What do I need that for?", "I don't have one of them") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("This is the entrance to the Hementster fishing competition")
                     .npc("It's a high class competition")
                     .npc("Invitation only");
                }
            })
            .start();
    }

    private void bonzo(Npc npc) {
        Player p = getOwner();
        if (has(WON)) {
            new Conversation(p, npc)
                .npc("Hello champ")
                .npc("So any hints on how to fish so well")
                .player("I think I'll keep them to myself")
                .start();
            return;
        }
        if (has(CAUGHT)) {
            new Conversation(p, npc)
                .npc("so how are you doing so far?")
                // Jagex's label runs the comma into the next word, and the
                // player only speaks the first half of it.
                .options(new Choice("I have this big fish,is it enough to win?",
                                    "I think I might still be able to find a bigger fish") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            c.npc("Ok, good luck");
                            return;
                        }
                        c.npc("Well we'll just wait till time is up")
                         .npc("Okay folks times up")
                         .npc("Lets see who caught the biggest fish")
                         .npc("We have a new winner")
                         .npc("The heroic looking person")
                         .npc("who was fishing by the pipes")
                         .npc("Has caught the biggest carp")
                         .npc("I've seen since Grandpa Jack used to compete")
                         .give(new InvItem(TROPHY, 1))
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(WON);
                             }
                         });
                    }
                }.says(0, "I have this big fish"))
                .start();
            return;
        }
        if (has(ENTERED)) {
            new Conversation(p, npc)
                .npc("so how are you doing so far?")
                .player("I think I might still be able to find a bigger fish")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Roll up, roll up")
            .npc("Enter the great Hemenster fishing competition")
            .npc("only 5gp entrance fee")
            .options(new Choice("I'll give that a go then", "No thanks, I'll just watch the fun") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    Player p = c.getPlayer();
                    if (p.getInventory().countId(COINS) < ENTRY_FEE) {
                        c.player("I don't have the 5gp though")
                         .npc("No pay, no play");
                        return;
                    }
                    c.take(COINS, ENTRY_FEE)
                     .npc("Marvelous")
                     .npc("Ok we've got all the fishermen")
                     .npc("It's time to roll")
                     .npc("Ok nearly everyone is in there place already")
                     .npc("You fish in the spot by the oak tree")
                     .npc("And the Sinister stranger you fish by the pipes")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(ENTERED);
                         }
                     });
                    Npc stranger = strangerNpc();
                    if (has(SMELLY) && stranger != null) {
                        c.npc(stranger, "Arrgh what is that ghastly smell")
                         .npc(stranger, "I think I will move over here instead")
                         .npc("Hmm you'd better go and take the area by the pipes then")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(BY_PIPES);
                             }
                         });
                    }
                }
            })
            .start();
    }

    /** The stranger, wherever he happens to be standing. */
    private Npc strangerNpc() {
        return world.getNpc(STRANGER, 563, 573, 484, 494);
    }

    /**
     * The Sinister stranger. What we shipped was three invented lines -- "I am
     * just a fisherman" / "Now leave me be" -- in place of the whole tree, and
     * with it went the joke the npc exists for: he is called Vlad, he comes
     * from where the sun is not so bright, the nights are long, and when he is
     * stressed he gets a little..  thirsty. Every road through it reaches "Just
     * because I can't stand the smell of garlic ... doesn't necessarily mean
     * I'm a vampire", which is the punchline and which nothing could reach.
     *
     * Three menu entries say something other than what they read, all Jagex's:
     * the top-level "so you like fishing?" is spoken without its question mark
     * and with a capital S, "If you get thirsty you should drink something" is
     * spoken as two lines, and the fishing branch offers "Well good look with
     * the fishing" where the other branch offers the same option spelled right.
     * The wiki marks that last one {{sic}}. Both spellings are kept.
     */
    private void stranger(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("..")
            .options(new Choice("..?", "Who are you?", "so you like fishing?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("...");
                        return;
                    }
                    if (option == 2) {
                        strangerFishing(c);
                        return;
                    }
                    c.npc("My name is Vlad")
                     .npc("I come from far avay, vere the sun is not so bright")
                     .options(new Choice("You're a vampire aren't you?",
                                         "Is it nice there?") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 strangerVampire(c);
                                 return;
                             }
                             c.npc("It is vonderful")
                              .npc("the vomen are beautiful")
                              .npc("and the nights are long")
                              .options(new Choice("You're a vampire aren't you?",
                                                  "So you like fishing?",
                                                  "Well good luck with the fishing") {
                                  public void picked(int option, Conversation c) {
                                      if (option == 0) {
                                          strangerVampire(c);
                                      } else if (option == 1) {
                                          strangerFishing(c);
                                      } else {
                                          strangerGoodLuck(c);
                                      }
                                  }
                              }.says(1, "So you like fishing"));
                         }
                     });
                }
            }.says(2, "So you like fishing"));
    }

    /** The punchline. Reachable from all three of the branches below it. */
    private static void strangerVampire(Conversation c) {
        c.npc("Just because I can't stand the smell of garlic")
         .npc("and I don't like bright sunlight")
         .npc("Doesn't necessarily mean I'm a vampire");
    }

    private static void strangerGoodLuck(Conversation c) {
        c.npc("Luck has nothing to do vith it")
         .npc("It is all in the technique");
    }

    private static void strangerFishing(Conversation c) {
        c.npc("My doctor told be to take up a velaxing hobby")
         .npc("vhen I am stressed I tend to get a little..")
         .npc("..thirsty")
         .options(new Choice("You're a vampire aren't you?",
                             "If you get thirsty you should drink something",
                             "Well good look with the fishing") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     strangerVampire(c);
                 } else if (option == 1) {
                     c.npc("I think I may do that soon");
                 } else {
                     strangerGoodLuck(c);
                 }
             }
         }.says(1, "If you get thirsty", "You should drink something")
          .says(2, "Well good luck with the fishing"));
    }

    private void forester(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hey you can't come through here")
            .npc("This is private land")
            .start();
    }

    private void grandpaJack(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("Hello young man")
            .npc("Come to visit old Grandpa Jack?")
            .npc("I can tell ye stories for sure")
            .npc("I used to be the best fisherman these parts have seen");
        if (questStarted() && !completed()) {
            c.options(new Choice("Are you entering the fishing competition?",
                                 "Tell me a story then",
                                 "Sorry I don't have time now") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        story(c);
                        return;
                    }
                    if (option == 2) {
                        c.npc("sigh").npc("Young people - always in such a rush");
                        return;
                    }
                    c.npc("Ah the Hemenster fishing competition")
                     .npc("I know all about that")
                     .npc("I won that four years straight")
                     .npc("I'm to old for that lark now though")
                     .options(new Choice("I don't suppose you could give me any hints?",
                                         "That's less competition for me then") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 return;
                             }
                             c.npc("Well you sometimes get these really big fish")
                              .npc("In the water just by the outflow pipes")
                              .npc("Think they're some kind of carp")
                              .npc("try to get a spot round there")
                              .npc("The best sort of bait for them is red vine worms")
                              .npc("I used to get those from McGruber's wood, north of here")
                              .npc("dig around in the red vines up there");
                         }
                     });
                }
            });
        } else {
            c.options(new Choice("Tell me a story then", "Sorry I don't have time now") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        story(c);
                    } else {
                        c.npc("sigh").npc("Young people - always in such a rush");
                    }
                }
            });
        }
        c.start();
    }

    private void story(Conversation c) {
        c.npc("Well when I were a young man")
         .npc("We used to take fishing trips over to Catherby")
         .npc("The fishing over there - now that was something")
         .npc("Anyway we decided to do a bit of fishing with our nets")
         .npc("I wasn't having the best of days")
         .npc("Tuning up nothing but old boots and bits of seaweed")
         .npc("Then my net suddenly got really heavy")
         .npc("I pulled it up")
         .npc("To my amazement I'd caught this little chest thing")
         .npc("even more amazing was when I opened it")
         .npc("It contained a diamond the size of a radish")
         .npc("That's the best catch I've ever had!");
    }
}
