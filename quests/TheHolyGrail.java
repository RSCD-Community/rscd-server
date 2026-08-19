import org.rscdaemon.server.event.SingleEvent;
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
 * The Holy Grail. Released 23 July 2002, written by Paul Gower.
 *
 * The sequel to Merlin's crystal, and only open to a knight of the round table.
 * Merlin points at a holy island, a crone on it points at six stone heads, the
 * heads point at a plot of stone north-west of Brimhaven, and a whistle blown on
 * that plot crosses into the fisher king's realm. The realm is dying because its
 * king is; putting his son on the throne restores it, and only then can the
 * grail be lifted off the table on the castle's top floor.
 *
 *     Merlin 393       (462,1387)   Camelot second floor, next to the library
 *     Crone 394        (411,560)    Entrana        High priest 395 (406,562)
 *     Black Knight titan 401 (413,11)              brother Galahad 403 (585,460)
 *     Sir Percival 411 no spawn -- he is inside the sacks
 *     Fisher king 412  (419,34)     maiden 413     Fisherman 414 (392,29)
 *     King Percival 415 (516,35)    unhappy peasant 416   happy peasant 417
 *
 *     sacks 408   (328,446)    "prod"   Goblin Village, western hut
 *     door 116    (202,2438)   "Open"   Draynor Manor top floor, chest room
 *     stone head 406           six of them, scenery only, nothing to click
 *
 *     magic whistle 738 "blow"     Cup of tea 739      Holy table napkin 742
 *     bell 743 "ring"              magic golden feather 745 "blow on"
 *     Holy grail 746
 *
 * The realm is two copies of the same ground, laid out side by side: the barren
 * one at x 384-431 and the restored one exactly ninety-six tiles west of it, at
 * x 480-527. Every object in the one has a twin in the other, which is how the
 * grass and the castle doorway appear the moment Percival takes the throne --
 * the whistle simply starts landing in the other copy. Both castles run over
 * three floors, with the grail on the top one at (418,1924) and (514,1924).
 *
 * The barren castle has no way in. Asking the fisherman about it puts a bell on
 * the ground outside the front wall, and ringing that bell is what a maiden
 * answers. The restored castle has an ordinary doorway, object 63 at (510,37),
 * so the bell stops mattering.
 *
 * Deviations:
 *
 *  - The titan's rule that the killing blow must be Excalibur is enforced via
 *    refusesKill, the same hook Chronozon uses in Family crest: his health can
 *    be worn down with anything, but the blow that would kill him is refused
 *    unless Excalibur is wielded, and the fight simply continues. In 2002 he
 *    was also a wall across the path; the realm's terrain is missing from
 *    Landscape.rscd, so he cannot be that here. His dialogue is Jagex's and
 *    unchanged.
 *
 *  - The whole realm is walkable void for the same reason: the landscape archive
 *    has ground overlay 250 and no walls at all across x 384-527, y 0-47, in
 *    RSCD and in the Ignis Isle original both. Castle walls do not block, so the
 *    bell is a convenience rather than the only way in. Recovering the terrain
 *    means going back to Jagex's own cache, which is not this file's decision.
 *
 *  - Galahad offers the napkin once the crone has named the fisher king's realm.
 *    The recorded transcript has the option appear in a state of its own without
 *    saying what opens it, and "I seek an item from the realm of the fisher
 *    king" is not a thing a player can say before hearing that the realm exists.
 *
 *  - Sir Percival's line about having the means to get there is skipped when the
 *    player is carrying no whistle, and he says so; the real refusal went
 *    unrecorded. The messages for blowing the whistle, and King Arthur taking
 *    the grail off the player at the end, are unrecorded too and written fresh.
 *
 *  - King Arthur is not associated here. He starts and ends this quest and
 *    Merlin's crystal both, so he lives in src/ as KingArthur.java and drives
 *    the two of them through the names published below.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TheHolyGrail extends Quest {

    public final static int UID = Quests.THE_HOLY_GRAIL;

    /*
     * Merlin here is npc 393, the one at his desk on Camelot's second floor
     * (462,1387) -- not 287, the Merlin's crystal tower Merlin, which that
     * quest owns. Same name, two npcs; this quest originally associated 287
     * and the library Merlin ignored everybody.
     */
    private static final int MERLIN = 393, CRONE = 394, HIGH_PRIEST = 395;
    private static final int TITAN = 401, GALAHAD = 403, SIR_PERCIVAL = 411;
    private static final int FISHER_KING = 412, MAIDEN = 413, FISHERMAN = 414;
    private static final int KING_PERCIVAL = 415;
    private static final int UNHAPPY_PEASANT = 416, HAPPY_PEASANT = 417;

    private static final int SACKS = 408, SACKS_X = 328, SACKS_Y = 446;

    /*
     * The door into the chest room on Draynor Manor's top floor -- the room
     * WEST of Oddenstein's lab, with chest 17 at (202,2441). The guide's
     * "next to Ernest and Professor Oddenstein" means the room beside
     * theirs, not their own; the whistles were spawning in the lab at
     * (212,2441) for a while because of that misreading.
     */
    private static final int MANOR_DOOR = 116, DOOR_X = 202, DOOR_Y = 2438;
    private static final int MANOR_DOOR_OPEN = 11;

    private static final int EXCALIBUR = 606;
    private static final int WHISTLE = 738, TEA = 739, NAPKIN = 742;
    private static final int BELL = 743, FEATHER = 745, GRAIL = 746;

    /** Draynor Manor's top floor, in the chest room door 116 shuts off. */
    private static final int WHISTLE_X = 204, WHISTLE_Y = 2440;

    /**
     * The plot of stone north-west of Brimhaven, as its own tiles. It is the one
     * patch of ground overlay 1 out there and the six stone heads all face it;
     * the candle stand at (490,651) stands in the middle of it.
     */
    private static final int[][] PLOT = {
        {490, 650}, {491, 650},
        {489, 651}, {490, 651}, {491, 651}, {492, 651},
        {489, 652}, {490, 652}, {491, 652}, {492, 652},
        {490, 653}, {491, 653}
    };

    /** Where the whistle puts a player down again, clear of the candles. */
    private static final int PLOT_X = 491, PLOT_Y = 652;

    /** The realm, both copies of it, on all three floors. */
    private static final int REALM_WEST = 527, REALM_EAST = 384;

    /** Ninety-six tiles between the barren copy and the restored one. */
    private static final int RESTORED = 96;

    /* Where the whistle lands you, user-verified against the real game. */
    private static final int ARRIVAL_X = 393, ARRIVAL_Y = 19;
    private static final int BELL_X = 413, BELL_Y = 37;
    private static final int CASTLE_X = 415, CASTLE_Y = 37;

    private static final int PRAYER = 5, DEFENSE = 1;

    private static final int STARTED = 1;
    private static final int MERLIN_TOLD = 2;
    private static final int CRONE_TOLD = 4;
    private static final int NAPKIN_GIVEN = 8;
    private static final int SEEKING_SON = 16;
    private static final int FEATHER_GIVEN = 32;
    private static final int HEIR_SENT = 64;
    private static final int FINISHED = 128;

    public TheHolyGrail(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("The Holy Grail");
        setFinalStage(FINISHED);
        associateNpc(MERLIN);
        associateNpc(CRONE);
        associateNpc(HIGH_PRIEST);
        associateNpc(TITAN);
        associateNpc(GALAHAD);
        associateNpc(SIR_PERCIVAL);
        associateNpc(FISHER_KING);
        associateNpc(MAIDEN);
        associateNpc(FISHERMAN);
        associateNpc(KING_PERCIVAL);
        associateNpc(UNHAPPY_PEASANT);
        associateNpc(HAPPY_PEASANT);
        associateObject(SACKS, SACKS_X, SACKS_Y);
        associateDoor(MANOR_DOOR, DOOR_X, DOOR_Y);
        associateItem(WHISTLE);
        associateItem(BELL);
        associateItem(FEATHER);
        associateItem(GRAIL);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("King arthur is sending out his knights on a quest for the famous holy grail. If you are a knight of the round table go to king arthur for further orders.");
        setStartPoint("Camelot Castle");
        setSpeakTo("King Arthur");
        setMissionLength("Long");
        /* KingArthur.java only offers this quest once Merlin's crystal is done. */
        requireQuest(Quests.MERLINS_CRYSTAL);
        require("Must defeat a lvl-146 black knight titan");
        rewardExp(PRAYER, 250, 250);
        rewardExp(DEFENSE, 300, 300);
    }

    public void completeQuest() {
        grantRewards();
        take(GRAIL, 1);
        getOwner().getActionSender().sendMessage(
            "@gre@Well done you have completed The Holy Grail quest");
    }

    /** The questions KingArthur puts to this quest. */
    public boolean reached(String key) {
        if ("started".equals(key)) {
            return questStarted();
        }
        return "seeking-percival".equals(key) && has(SEEKING_SON);
    }

    /** And the other half of the same conversation. */
    public void note(String key) {
        if ("quest-accepted".equals(key) && !questStarted()) {
            set(STARTED);
        } else if ("given-feather".equals(key)) {
            set(FEATHER_GIVEN);
        } else if ("grail-returned".equals(key) && !completed()) {
            setStage(FINISHED);
        }
    }

    /**
     * The grail sits on the fisher king's table for the whole quest and is not
     * really there to be taken. Both refusals are Jagex's own wording.
     */
    public boolean refusesPickup(InvItem item) {
        if (item.getID() != GRAIL) {
            return false;
        }
        if (holding(GRAIL)) {
            say("You feel getting taking more than one holy grail might be greedy");
            return true;
        }
        if (!restored()) {
            say("You feel that the grail shouldn't be moved");
            say("You must complete some task here before you are worthy");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private boolean open() {
        return questStarted() && !completed();
    }

    /** Percival on the throne outlives the quest -- the realm stays green. */
    private boolean restored() {
        return has(HEIR_SENT) || completed();
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    private void take(int id, int amount) {
        Player p = getOwner();
        p.getInventory().remove(id, amount);
        p.getActionSender().sendInventory();
    }

    private void say(String message) {
        getOwner().getActionSender().sendMessage(message);
    }

    private Npc nearby(int id, int range) {
        Player p = getOwner();
        return world.getNpc(id, p.getX() - range, p.getX() + range,
                                p.getY() - range, p.getY() + range);
    }

    private Npc summon(int id, int x, int y, int life) {
        final Npc npc = new Npc(id, x, y, x - 1, x + 1, y - 1, y + 1);
        npc.setRespawn(false);
        world.registerNpc(npc);
        world.getDelayedEventHandler().add(new SingleEvent(null, life){
            public void action() {
                world.unregisterNpc(npc);
            }
        });
        return npc;
    }

    private void dismiss(final Npc npc) {
        world.getDelayedEventHandler().add(new SingleEvent(null, 3000){
            public void action() {
                world.unregisterNpc(npc);
            }
        });
    }

    /** Anywhere in either copy of the realm, on any of the three floors. */
    private boolean inRealm() {
        Player p = getOwner();
        if (p.getX() < REALM_EAST || p.getX() > REALM_WEST) {
            return false;
        }
        int y = p.getY() % 944;
        return y < 48;
    }

    private boolean onPlot() {
        Player p = getOwner();
        for (int[] tile : PLOT) {
            if (tile[0] == p.getX() && tile[1] == p.getY()) {
                return true;
            }
        }
        return false;
    }

    /** Which copy of the realm this player's whistle opens on. */
    private int side() {
        return restored() ? RESTORED : 0;
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            Npc npc = (Npc) entity;
            switch (npc.getID()) {
                case MERLIN:          merlin(npc);      return;
                case HIGH_PRIEST:     highPriest(npc);  return;
                case CRONE:           crone(npc);       return;
                case GALAHAD:         galahad(npc);     return;
                case TITAN:           titan(npc);       return;
                case FISHERMAN:       fisherman(npc);   return;
                case FISHER_KING:     fisherKing(npc);  return;
                case MAIDEN:          maiden(npc);      return;
                case KING_PERCIVAL:   kingPercival(npc);return;
                case SIR_PERCIVAL:    percival(npc);    return;
                case UNHAPPY_PEASANT: peasant(npc, false); return;
                case HAPPY_PEASANT:   peasant(npc, true);  return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger != QuestTrigger.ITEM_COMMAND) {
                return;
            }
            switch (((InvItem) entity).getID()) {
                case WHISTLE: blowWhistle(); return;
                case BELL:    ringBell();    return;
                case FEATHER: blowFeather(); return;
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1) {
            manorDoor(object);
        } else if (trigger == QuestTrigger.OBJECT_ACT2) {
            // The sacks' commands are WalkTo/prod, so prod is the second action.
            prodSacks();
        }
    }

    // -------------------------------------------------------------- Camelot --

    /**
     * Merlin has no more idea where the grail is than anyone else, and says so
     * at length. He repeats the whole speech every time, which is Jagex's.
     */
    private void merlin(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("hello I'm working on a new spell")
                .npc("To turn people into hedgehogs")
                .start();
            return;
        }
        if (!questStarted()) {
            return;
        }
        Conversation c = new Conversation(p, npc);
        merlinAdvice(c);
        c.start();
    }

    private void merlinAdvice(Conversation c) {
        c.player("Hello King Arthur has sent me on a quest for the holy grail")
         .player("He thought you could offer some assistance")
         .npc("Ah yes the holy grail")
         .npc("That is a powerful artifact indeed")
         .npc("Returning it here would help Camelot a lot")
         .npc("Due to its nature the holy grail is likely to reside in a holy place")
         .player("Any suggestions?")
         .npc("I believe there is a holy island somewhere not far away")
         .npc("I'm not entirely sure")
         .npc("I spent too long inside that crystal")
         .npc("Anyway go and talk to someone over there")
         .npc("I suppose you could also try speaking to Sir Galahad")
         .npc("He returned from the quest many years after everyone else")
         .npc("He seems to know something about it")
         .npc("but he can only speak about those experiences cryptically")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(MERLIN_TOLD);
             }
         });
        merlinChoices(c);
    }

    /* Asking after Galahad comes back to the same menu, not to the whole
       speech again. */
    private void merlinChoices(Conversation c) {
        c.options(new Choice("Thankyou for the advice",
                             "Where can I find Sir Galahad") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Galahad now lives a life of religious contemplation")
                      .npc("He lives somewhere west of McGrubors Wood");
                     merlinChoices(c);
                 }
             }
         });
    }

    // -------------------------------------------------------------- Entrana --

    /**
     * The high priest does not care about the grail. The crone standing behind
     * him does, and interrupts -- so her whole part of the quest is spoken here
     * rather than by talking to her.
     */
    private void highPriest(Npc npc) {
        Player p = getOwner();
        Npc her = nearby(CRONE, 12);
        if (!has(MERLIN_TOLD) || has(CRONE_TOLD) || her == null) {
            new Conversation(p, npc)
                .npc("Many greetings welcome to our fair island")
                .npc("enjoy your stay hear")
                .npc("May it be spiritually uplifting")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .npc("Many greetings welcome to our fair island")
            .player("Hello, I am in search of the holy grail")
            .npc("The object of which you speak did once pass through holy entrana")
            .npc("I know not where it is now")
            .npc("Nor do I really care")
            .npc(her, "Wait!")
            .npc(her, "Did you say the grail?")
            .npc(her, "You are a grail knight yes?")
            .npc(her, "Well you'd better hurry, a fisher king is in pain")
            .player("Well I would but I don't know where I am going")
            .npc(her, "Go to where the six heads face")
            .npc(her, "blow the whistle and away you go")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(CRONE_TOLD);
                }
            });
        croneMenu(c, her);
        c.start();
    }

    private void croneMenu(Conversation c, final Npc her) {
        c.options(new Choice("What are the four heads?",
                             "What's a fisher king?",
                             "What do you mean by the whistle?",
                             "Ok I will go searching") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc(her, "The six stone heads have appeared just recently in the world")
                     .npc(her, "They all face the point of realm crossing")
                     .npc(her, "Find where two of the heads face")
                     .npc(her, "And you should be able to pinpoint where it is")
                     .options(new Choice("The point of realm crossing",
                                         "What's a fisher king?",
                                         "What do you mean by the whistle?",
                                         "Ok I will go searching") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc(her, "The realm of the fisher king is not quite of this reality")
                                  .npc(her, "It is of a reality very close to ours though")
                                  .npc(her, "Where it's easiest to cross that is a point of realm crossing");
                                 return;
                             }
                             if (option == 1) {
                                 c.npc(her, "The fisher king is the owner and slave of the grail");
                                 return;
                             }
                             if (option == 2) {
                                 croneWhistle(c, her);
                             }
                         }
                     });
                    return;
                }
                if (option == 1) {
                    c.npc(her, "The fisher king is the owner and slave of the grail");
                    return;
                }
                if (option == 2) {
                    croneWhistle(c, her);
                }
            }
        }.says(0, "What are the six heads?"));
    }

    private void croneWhistle(Conversation c, final Npc her) {
        c.npc(her, "You don't know about the whistles yet?")
         .npc(her, "the whistles are easy")
         .npc(her, "You will need one to get to and from the fisher king's realm")
         .npc(her, "they reside in a haunted manor house in Misthalin")
         .npc(her, "though you may not perceive them unless you carry something")
         .npc(her, "From the realm of the fisher king");
        croneMenu(c, her);
    }

    /**
     * The crone has nothing of her own to say. Everything she knows she says
     * over the high priest's shoulder, and she is claimed here only so that
     * talking to her does not fall through to the ordinary npc handling.
     */
    private void crone(Npc npc) {
    }

    // ------------------------------------------- brother Galahad, and the tea --

    private void galahad(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc)
                .npc("would you like a cup of tea?")
                .npc("I'll just put the kettle on")
                .player("I returned the holy grail to camelot")
                .npc("I'm impressed")
                .npc("That's something I was never able to do");
            tea(c);
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .npc("Welcome to my home")
            .npc("Its rare for me to have guests")
            .npc("would you like a cup of tea?")
            .npc("I'll just put the kettle on");
        if (!open()) {
            c.options(new Choice("Are you any relation to Sir Galahad",
                                 "do you get lonely here on your own?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        whoHeIs(c);
                    } else {
                        lonely(c);
                    }
                    tea(c);
                }
            }.says(1, "Do you get lonely out here on your own?"));
            c.start();
            return;
        }
        if (has(CRONE_TOLD) && !has(NAPKIN_GIVEN)) {
            c.options(new Choice("I seek an item from the realm of the fisher king",
                                 "I'm on a quest to find the holy grail",
                                 "Are you any relation to Sir Galahad",
                                 "do you get lonely here on your own?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("when i left there")
                         .npc("I took this small cloth from the table as a keepsake")
                         .player("I don't suppose I could borrow that?")
                         .player("it could come in useful on my quest")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(NAPKIN_GIVEN);
                                 give(NAPKIN, 1);
                                 say("Brother Galahad gives you a holy table napkin");
                             }
                         });
                        return;
                    }
                    if (option == 1) {
                        onQuest(c);
                        return;
                    }
                    if (option == 2) {
                        whoHeIs(c);
                        return;
                    }
                    lonely(c);
                    tea(c);
                }
            }.says(3, "Do you get lonely out here on your own?"));
            c.start();
            return;
        }
        c.options(new Choice("I'm on a quest to find the holy grail",
                             "Are you any relation to Sir Galahad",
                             "do you get lonely here on your own?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    onQuest(c);
                    return;
                }
                if (option == 1) {
                    whoHeIs(c);
                    return;
                }
                lonely(c);
                tea(c);
            }
        }.says(2, "Do you get lonely out here on your own?"));
        c.start();
    }

    private void whoHeIs(Conversation c) {
        c.npc("I am Sir Galahad")
         .npc("Though I've given up being a knight for now")
         .npc("I am now live as a solitary monk")
         .npc("I prefer to be known as brother rather than sir now");
    }

    private void lonely(Conversation c) {
        c.npc("Sometimes I do yes")
         .npc("Still not many people to share my solidarity with")
         .npc("Most the religious men around here are worshippers od Saradomin");
    }

    /** He will only speak about the castle cryptically, as Merlin warned. */
    private void onQuest(Conversation c) {
        c.npc("Ah the grail yes")
         .npc("that did fill be with wonder")
         .npc("Oh, that I could have stayed forever")
         .npc("The spear, the food, the people")
         .options(new Choice("So how can I find it?",
                             "What are you talking about?") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("I did not find it through looking")
                      .npc("though admidtedly I looked long and hard")
                      .npc("Eventually it found me");
                     return;
                 }
                 c.npc("The grail castle")
                  .npc("It's hard to describe with words")
                  .npc("It mostly felt like a dream")
                  .options(new Choice("So how can I find it?",
                                      "Why did you leave?",
                                      "Why didn't you bring the grail with you?") {
                      public void picked(int option, Conversation c) {
                          if (option == 0) {
                              c.npc("I did not find it through looking")
                               .npc("though admidtedly I looked long and hard")
                               .npc("Eventually it found me");
                              return;
                          }
                          if (option == 1) {
                              c.npc("apparently the time is getting close")
                               .npc("When the world will need Arthur and his knights of the round table again")
                               .npc("And that includes me")
                               .npc("leaving was tough for me")
                               .npc("I took this small cloth from the table as a keepsake");
                              return;
                          }
                          c.npc("I'm not sure")
                           .npc("Because it seemed to be needed in the grail castle");
                          tea(c);
                          c.options(new Choice("well I'd better be going then") {
                              public void picked(int option, Conversation c) {
                                  tea(c);
                                  c.npc("If you do come across any particularily difficult obstacles on your quest")
                                   .npc("Do not hesitate to ask my advice")
                                   .npc("I know more about the realm of the grail than many")
                                   .npc("I have a feeling you may need to come back and speak to me anyway");
                              }
                          });
                      }
                  });
             }
         });
    }

    /** The kettle he put on at the start of every conversation. */
    private void tea(Conversation c) {
        c.npc("Half a moment your cup of tea is ready")
         .then(new Effect() {
             public void run(Conversation c) {
                 give(TEA, 1);
             }
         });
    }

    // ------------------------------------------------------ Draynor Manor --

    /**
     * The door on the manor's top floor. The whistles are behind it and are only
     * there for a player carrying something out of the fisher king's realm --
     * the crone's "you may not perceive them" -- which is what the napkin is
     * for. Two of them appear, as they did in 2002.
     *
     * Claiming a door takes it away from WallObjectAction outright, so the
     * ordinary opening is done here as well.
     */
    private void manorDoor(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), MANOR_DOOR_OPEN,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        int x = door.getX(), y = door.getY();
        if (door.getDirection() == 0) {
            p.teleport(x, p.getY() >= y ? y - 1 : y, false);
        } else {
            p.teleport(p.getX() >= x ? x - 1 : x, y, false);
        }
        say("You go through the door");
        if (!open() || !holding(NAPKIN) || holding(WHISTLE)) {
            return;
        }
        world.registerItem(new Item(WHISTLE, WHISTLE_X, WHISTLE_Y, 1, p));
        world.registerItem(new Item(WHISTLE, WHISTLE_X, WHISTLE_Y, 1, p));
    }

    // --------------------------------------------------------- the whistle --

    private void blowWhistle() {
        Player p = getOwner();
        if (inRealm()) {
            say("You blow the whistle");
            say("The realm fades out around you");
            p.teleport(PLOT_X, PLOT_Y, false);
            return;
        }
        if (!onPlot()) {
            say("Nothing interesting happens");
            return;
        }
        say("You blow the whistle");
        say("@yel@You are somewhere else entirely");
        p.teleport(ARRIVAL_X + side(), ARRIVAL_Y, false);
    }

    // ------------------------------------------------------------- the realm --

    private void titan(Npc npc) {
        final Player p = getOwner();
        new Conversation(p, npc)
            .npc("I am the black knight titan")
            .npc("You must pass through me before you can continue in this realm")
            .options(new Choice("Ok, have at ye oh evil knight",
                                "Actually I think I'll run away") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            c.stop();
                            c.getNpc().attackPlayer(p);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * The fisherman is the first of the fisher king's clues. Asking him how to
     * get in puts a bell on the grass outside the castle's front wall -- there
     * is no door on that side until Percival is king.
     */
    private void fisherman(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Hi - I don't get many visitors here")
            .options(new Choice("How's the fishing?",
                                "Any idea how to get into the castle?",
                                "Yes well this place is a dump") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Not amazing")
                         .npc("Not many fish can live in this gungey stuff")
                         .npc("I remember when this was a pleasant river")
                         .npc("Teaming with every soft of fish");
                        return;
                    }
                    if (option == 1) {
                        c.npc("why thats easy")
                         .npc("just ring one of the bells outside")
                         .player("I didn't see any bells")
                         .npc("You must be blind then")
                         .npc("There's always bells there when I go to the castle")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 dropBell();
                             }
                         });
                        return;
                    }
                    c.npc("This place used to be very beautiful")
                     .npc("However as our king grows old and weak")
                     .npc("the land seems to be dying too");
                }
            })
            .start();
    }

    private void dropBell() {
        Player p = getOwner();
        if (holding(BELL)) {
            return;
        }
        world.registerItem(new Item(BELL, BELL_X + side(), BELL_Y, 1, p));
    }

    private void ringBell() {
        Player p = getOwner();
        if (!inRealm()) {
            say("Ting a ling a ling");
            return;
        }
        say("Ting a ling a ling");
        say("One of the maidens lets you in");
        p.teleport(CASTLE_X + side(), CASTLE_Y, false);
    }

    /* Both lines are Jagex's, from the replay footage: the maidens never
       actually converse -- one answers the bell, and that is all. */
    private void maiden(Npc npc) {
        say("She has a far away look in her eyes");
        say("The maiden does not appear interested in talking");
    }

    private void peasant(Npc npc, boolean happy) {
        Conversation c = new Conversation(getOwner(), npc);
        if (happy) {
            c.npc("Oh happy day")
             .npc("suddenly our crops are growing again")
             .npc("It'll be a bumper harvest this year");
        } else {
            c.npc("Woe is me")
             .npc("Our crops are all failing")
             .npc("How shall I feed myself this winter?");
        }
        c.start();
    }

    // ------------------------------------------------------- the fisher king --

    /**
     * The fisher king sets SEEKING_SON on the spot, and that bit alone is
     * enough for King Arthur to hand over the golden feather (see
     * KingArthur.grailProgress's "seeking-percival") without Arthur ever
     * having offered the quest. Reaching this npc still takes a whistle, but
     * a dropped one is a shared ground spawn and not proof the quest was
     * ever accepted -- so unlike Merlin and the crone, who both already gate
     * on quest state, the fisher king has to as well.
     */
    private void fisherKing(Npc npc) {
        if (!questStarted()) {
            return;
        }
        Conversation c = new Conversation(getOwner(), npc)
            .npc("Ah you got inside at last")
            .npc("You spent all that time fumbling around outside")
            .npc("I thought you'd never make it here");
        kingMenu(c, false);
        c.start();
    }

    private void kingMenu(Conversation c, final boolean mayLookAround) {
        String[] options = mayLookAround
            ? new String[] { "How did you know what I have been doing?",
                             "I seek the holy grail",
                             "You don't look too well",
                             "Do you mind if I have a look around?" }
            : new String[] { "How did you know what I have been doing?",
                             "I seek the holy grail",
                             "You don't look too well" };
        c.options(new Choice(options) {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Oh I can see what is happening in my realm")
                     .npc("I have sent clues to help you get here")
                     .npc("Such as the fisherman")
                     .npc("And the crone");
                    kingMenu(c, true);
                    return;
                }
                if (option == 1) {
                    c.npc("Ah excellent, a knight come to seek the holy grail")
                     .npc("Maybe now our land can be restored to it's former glory")
                     .npc("At the moment the grail cannot be removed from the castle")
                     .npc("legend has it a questing knight will one day")
                     .npc("Work out how to restore our land")
                     .npc("then he will claim the grail as his prize")
                     .player("Any ideas how I can restore the land?")
                     .npc("None at all");
                    kingMenu(c, mayLookAround);
                    return;
                }
                if (option == 2) {
                    c.npc("Nope I don't feel so good either")
                     .npc("I fear my life is running short")
                     .npc("Alas my son and heir is not here")
                     .npc("I am waiting for my son to return to this castle")
                     .npc("If you could find my son that would be a great weight off my shoulders")
                     .player("Who is your son?")
                     .npc("He is known as Percival")
                     .npc("I believe he is a knight of the round table")
                     .player("I shall go and see if I can find him")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(SEEKING_SON);
                         }
                     });
                    return;
                }
                c.npc("No not at all, be my guest");
            }
        });
    }

    private void kingPercival(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("You missed all the excitement")
            .npc("I got here and agreed to take over duties as king here")
            .npc("Then before my eyes the most miraculous changes occurred here")
            .npc("Grass and trees were growing outside before our eyes")
            .npc("Thankyou very much for showing me the way home")
            .start();
    }

    // ---------------------------------------------------------- Sir Percival --

    /**
     * The feather points at the sacks, and only until Percival is out of them.
     * Its exact wording went unrecorded, so it is written as a bearing; "nothing
     * interesting happens" afterwards is Jagex's.
     */
    private void blowFeather() {
        Player p = getOwner();
        if (has(HEIR_SENT) || !has(FEATHER_GIVEN)) {
            say("nothing interesting happens");
            return;
        }
        int dx = SACKS_X - p.getX(), dy = SACKS_Y - p.getY();
        String way = "";
        if (dy < -4) {
            way = "north";
        } else if (dy > 4) {
            way = "south";
        }
        if (dx < -4) {
            way = way + "east";
        } else if (dx > 4) {
            way = way + "west";
        }
        if (way.length() == 0) {
            say("The feather spins around and points straight down");
            return;
        }
        say("The feather points " + way);
    }

    /**
     * The killing blow must come from Excalibur. Asked at the chokepoint in
     * Npc.killedBy exactly like Chronozon in Family crest, so it covers melee,
     * ranged and magic alike: anything can wear him down, but he sits at the
     * brink and the fight goes on until the last hit lands from Excalibur.
     */
    public boolean refusesKill(Npc npc) {
        if (npc.getID() != TITAN) {
            return false;
        }
        for (InvItem item : getOwner().getInventory().getItems()) {
            if (item.getID() == EXCALIBUR && item.isWielded()) {
                return false;
            }
        }
        say("The black knight titan heals himself");
        return true;
    }

    /**
     * Goblins put a knight of the round table in a sack and forgot about him.
     * Nothing happens here until his father has actually asked after him, which
     * is the wiki's warning about the quest stalling.
     */
    private void prodSacks() {
        if (!has(SEEKING_SON) || has(HEIR_SENT)) {
            say("nothing interesting happens");
            return;
        }
        Npc him = nearby(SIR_PERCIVAL, 4);
        if (him == null) {
            him = summon(SIR_PERCIVAL, SACKS_X, SACKS_Y + 1, 120000);
        }
        percival(him);
    }

    private void percival(final Npc npc) {
        Player p = getOwner();
        // He is only ever in the world because prodSacks() summoned him, and
        // that already checks SEEKING_SON -- but once summoned he is visible
        // to every player nearby, not only the one who freed him, so a
        // second player's own quest state has to be checked here too.
        if (!has(SEEKING_SON) || has(HEIR_SENT)) {
            return;
        }
        new Conversation(p, npc)
            .npc("Wow thankyou")
            .npc("I could hardly breathe in there")
            .options(new Choice("How did you end up in a sack?",
                                "Come with me, I shall make you a king",
                                "Your father wishes to speak to you") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("It's a little embarrassing really")
                         .npc("After going on a long and challenging quest")
                         .npc("to retrieve the boots of arkaneeses")
                         .npc("defeating many powerful enemies on the way")
                         .npc("I fell into a goblin trap")
                         .npc("I've been kept as a slave here for the last 3 months")
                         .npc("a day or so ago, they decided it was a fun game")
                         .npc("To put me in this sack")
                         .npc("Then they forgot about me")
                         .npc("I'm now very hungry and my bones feel very stiff");
                        return;
                    }
                    if (option == 1) {
                        c.npc("What are you talking about?")
                         .npc("The king of where?")
                         .player("Your father is apparently someone called the fisher king")
                         .player("He is dying and wishes you to be his heir");
                    } else {
                        c.npc("My father? you have spoken to him recently?")
                         .player("He is dying and wishes you to be his heir");
                    }
                    c.npc("I have been told that before")
                     .npc("I have not been able to find that castle again though");
                    if (!holding(WHISTLE)) {
                        c.player("I have no way of showing you either");
                        return;
                    }
                    c.player("Well I do have the means to get us there - a magic whistle")
                     .npc("Ok I will see you there then")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             take(WHISTLE, 1);
                             set(HEIR_SENT);
                             dismiss(npc);
                         }
                     });
                }
            })
            .start();
    }
}
