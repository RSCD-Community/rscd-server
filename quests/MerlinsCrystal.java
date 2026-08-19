import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.Formulae;

/**
 * Merlin's crystal. Released 27 February 2002, written by Paul Gower.
 *
 * King Arthur has moved the whole round table to RuneScape and his wizard is
 * stuck in a lump of quartz. Getting him out means sailing to Morgan LeFaye's
 * keep in a merchant's hold, beating her son at the top of it, and then working
 * through the four things she gives up in exchange for his life: a pentagram, a
 * black candle, bat bones, and the sword the spell was bound with.
 *
 *     Sir Lancelot 273  (462,432)     Sir Gawain 274    (472,450)
 *     King Arthur  275  (474,453)     Sir Mordred 276   (460,2407) top of the keep
 *     Renegade knight 277             Arhein 280        (440,502) Catherby dock
 *     Morgan le faye 281  spawned when Mordred dies
 *     Candlemaker 282   (448,492)     lady 283          (353,523) Taverley lake
 *     lady 284, lady 285  spawned -- see below
 *     Beggar 286        spawned at Grum's door
 *     Merlin 287        (462,2336)    Thrantax 288      spawned at the pentagram
 *     Giant bat 43      (471-485, 514-524) outside the keep, drops bat bones
 *
 *     ship 292 (436,509) / 293 (436,504)   "stow away"   Catherby dock
 *     beehive 294  (475,488) (471,489) (472,484)
 *     Giant crystal 287 (461,2335)         Camelot south-east tower, top floor
 *     Altar 296    (116,366)               "Search"  Chaos Temple, level 11 wild
 *     Ladder 295   (280,634)               "Climb-Up" Grum's Jewellers
 *     Door 85      (277,632)               "Open"     Grum's Jewellers
 *     Door 2       (462,447) (464,1392)    "Open"     into the crystal's tower
 *
 * The pentagram is not scenery. It is a ground overlay -- tile texture 14 --
 * which is why it has no examine option and why it cannot be associated. There
 * is exactly one of them in the park north-east of Camelot Castle, at (448,435),
 * in the middle of a five by five stone pad, and that is the tile the ritual
 * looks for. The others in the world -- Keep LeFaye's top floor, the Mage Arena,
 * the Dark Wizards' Tower, the Observatory, the Sorcerors' Tower, the Wizards'
 * Guild, the Watchtower, and the ones beside the spirit trees -- are all further
 * from the crystal than that one, which is what Morgan asks for.
 *
 * The ship's hold is Jagex's own: a sealed two by four wooden room under the
 * keep at (456-457, 3351-3354), with a ladder at (457,3352) that climbs out onto
 * the keep's railed sea dock at (457,520). Stowing away only has to put the
 * player in it; the ordinary climb-up handler does the rest, and the arch in the
 * keep's east wall at y 519-522 lets them walk in. The front doors stay shut,
 * as they should: they are object 142, which has no open state.
 *
 * Deviations:
 *
 *  - Locking the tower is ours. Nothing recorded says either of those two doors
 *    was ever anything but an ordinary door, and in 2002 they almost certainly
 *    were not -- but Merlin 287 is a permanent spawn beside the crystal, so
 *    with them open a player who climbs the tower before doing anything at all
 *    finds the wizard standing free next to the lump of quartz he is supposedly
 *    inside. Nothing progresses, nothing breaks; the sight is the spoiler. They
 *    are shut until the ritual binds the crystal and open from then on.
 *
 *  - Sparing Sir Mordred has no mechanical effect. In 2002 Morgan appeared as
 *    the killing blow landed and the player chose whether it counted; RSCD tells
 *    a quest an npc died only once it already has, so she arrives afterwards and
 *    both answers lead to the same place. Her dialogue is unchanged.
 *
 *  - The Candlemaker will make a second black candle for a second bucket of wax.
 *    The recorded behaviour is that his dialogue reverts once he has handed one
 *    over, which leaves a player who loses the candle before the ritual with no
 *    way to finish. The wax is the real cost and it is still charged.
 *
 *  - Two of the three "lady" npcs Jagex made have no spawn anywhere, in RSCD or
 *    in the 2001 data. 284 is used for the voice in the room above Grum's and
 *    285 for the one who hands over Excalibur, which is what having three copies
 *    of a one-location npc is for.
 *
 *  - Refusing the bucket at an unrepelled beehive says "The bees will not let
 *    you near the hive", and the Candlemaker's "Not yet" option is named. Both
 *    lines went unrecorded.
 *
 *  - King Arthur is not claimed here. He is the start and the end of this quest
 *    and of The Holy Grail both, and an npc can only belong to one quest, so he
 *    lives in KingArthur.java as a handler and drives this one through the names
 *    below. Merlin is not claimed either -- nothing here needs to own him,
 *    because the crystal speaks through him when it shatters, and The Holy Grail
 *    does need him.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class MerlinsCrystal extends Quest {

    public final static int UID = Quests.MERLINS_CRYSTAL;

    private static final int LANCELOT = 273, GAWAIN = 274;
    private static final int MORDRED = 276, ARHEIN = 280, MORGAN = 281;
    private static final int CANDLEMAKER = 282;
    private static final int LADY = 283, LADY_VOICE = 284, LADY_GIFT = 285;
    private static final int BEGGAR = 286, MERLIN = 287, THRANTAX = 288;

    private static final int SHIP_BOW = 292, SHIP_STERN = 293;
    private static final int BEEHIVE = 294;
    private static final int CRYSTAL = 287, CRYSTAL_X = 461, CRYSTAL_Y = 2335;
    /* The tower's top floor exists twice on the map, mirror copies either side
       of the keep: the west one holds the crystal with Merlin walled inside
       it, the east one is the same room empty, for after he is freed. The two
       ladders between the middle and top floors are routed so a player only
       ever sees the copy their quest says is there. */
    private static final int TOWER_UP = 5, TOWER_DOWN = 6;
    private static final int TOP_WEST_X = 460, TOP_EAST_X = 476, TOP_Y = 2337;
    private static final int MID_WEST_X = 460, MID_EAST_X = 476, MID_Y = 1394;
    private static final int CHAOS_ALTAR = 296, ALTAR_X = 116, ALTAR_Y = 366;
    private static final int GRUM_LADDER = 295, LADDER_X = 280, LADDER_Y = 634;
    private static final int GRUM_DOOR = 85, DOOR_X = 277, DOOR_Y = 632;

    /**
     * The two doors into Camelot's south-west tower, the one the crystal sits
     * on top of. Both are ordinary door 2, so both are claimed by placement.
     */
    private static final int TOWER_DOOR = 2;
    private static final int HALL_DOOR_X = 462, HALL_DOOR_Y = 447;
    private static final int LANDING_DOOR_X = 464, LANDING_DOOR_Y = 1392;

    private static final int BUCKET = 21, BREAD = 138;
    private static final int BLACK_CANDLE = 600, LIT_BLACK_CANDLE = 602;
    private static final int REPELLANT = 603, BAT_BONES = 604;
    private static final int WAX_BUCKET = 605, EXCALIBUR = 606;

    /** The one pentagram in the park north-east of the castle. */
    private static final int PENTAGRAM_X = 448, PENTAGRAM_Y = 435;

    /** Inside the hold, clear of the ladder that climbs back out of it. */
    private static final int HOLD_X = 456, HOLD_Y = 3353;

    private static final int PRAYER = 5;

    /** Squirting the hive lasts until a bucket is filled from it. */
    private static final String HIVE_FLAG = "merlin_hive_repelled";

    private static final String WORDS = "Snarthon Candtrick Termanto";
    private static final String[] INCANTATIONS = {
        "Snarthtrick Candanto Termon",
        "Snarthon Candtrick Termanto",
        "Snarthanto Candon Termtrick"
    };

    private static final int STARTED = 1;
    private static final int GAWAIN_TOLD = 2;
    private static final int LANCELOT_TOLD = 4;
    private static final int RITUAL = 8;
    private static final int CANDLE_ASKED = 16;
    private static final int WORDS_FOUND = 32;
    private static final int TEST_SET = 64;
    private static final int BOUND = 128;
    private static final int FREED = 256;
    private static final int FINISHED = 512;

    public MerlinsCrystal(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Merlin's crystal");
        setFinalStage(FINISHED);
        associateNpc(LANCELOT);
        associateNpc(GAWAIN);
        associateNpc(MORDRED);
        associateNpc(ARHEIN);
        associateNpc(MORGAN);
        associateNpc(CANDLEMAKER);
        associateNpc(LADY);
        associateNpc(LADY_VOICE);
        associateNpc(LADY_GIFT);
        associateNpc(BEGGAR);
        associateNpc(THRANTAX);
        associateObject(SHIP_BOW);
        associateObject(SHIP_STERN);
        associateObject(BEEHIVE);
        associateObject(CRYSTAL, CRYSTAL_X, CRYSTAL_Y);
        associateObject(CHAOS_ALTAR, ALTAR_X, ALTAR_Y);
        associateObject(GRUM_LADDER, LADDER_X, LADDER_Y);
        associateObject(TOWER_UP, 459, 1393);
        associateObject(TOWER_UP, 477, 1393);
        associateObject(TOWER_DOWN, 459, 2337);
        associateObject(TOWER_DOWN, 477, 2337);
        associateDoor(GRUM_DOOR, DOOR_X, DOOR_Y);
        associateDoor(TOWER_DOOR, HALL_DOOR_X, HALL_DOOR_Y);
        associateDoor(TOWER_DOOR, LANDING_DOOR_X, LANDING_DOOR_Y);
        /* Watched, not owned: the bones are dropped, never handed over. */
        associateDroppedItem(BAT_BONES);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Merlin the wizard has carelessly become imprisoned inside a giant crystal. Take up king Arthur's quest to free Merlin and become one of the knights of the round table.");
        setStartPoint("Camelot castle");
        setSpeakTo("King Arthur");
        setMissionLength("Long");
        require("Fight a level 58 enemy");
        rewardOther("Knighted as a knight of the round table");
    }

    public void completeQuest() {
        getOwner().getActionSender().sendMessage(
            "@gre@Well done you have completed the Merlin's crystal quest");
    }

    /**
     * Published for KingArthur, and for the quests that start where this one
     * finishes: Hero's quest wants the knighthood, The Holy Grail wants it too.
     */
    public boolean reached(String key) {
        if ("started".equals(key)) {
            return questStarted();
        }
        if ("merlin-freed".equals(key)) {
            return has(FREED);
        }
        return "knight-of-the-round-table".equals(key) && completed();
    }

    /** The other half of the same conversation with KingArthur. */
    public void note(String key) {
        if ("quest-accepted".equals(key) && !questStarted()) {
            set(STARTED);
        } else if ("knighted".equals(key) && has(FREED)) {
            setStage(FINISHED);
        }
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

    /** Lancelot's tip survives the quest -- the ship keeps running afterwards. */
    private boolean knowsKeep() {
        return has(LANCELOT_TOLD) || completed();
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

    /** A short-lived npc that will not respawn and clears itself away. */
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

    /** The shop the npc is standing in, opened the way a shopkeeper opens it. */
    private void openShop(Npc keeper) {
        Player p = getOwner();
        Shop shop = world.getShop(keeper);
        if (shop == null) {
            return;
        }
        p.setAccessingShop(shop);
        p.getActionSender().showShop(shop);
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == MORDRED) {
                    mordredDown();
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case GAWAIN:      gawain(npc);      return;
                case LANCELOT:    lancelot(npc);    return;
                case ARHEIN:      arhein(npc);      return;
                case CANDLEMAKER: candlemaker(npc); return;
                case LADY:        lady(npc);        return;
                case MORGAN:      morgan(npc);      return;
                case BEGGAR:      beggar(npc);      return;
                case LADY_VOICE:
                case LADY_GIFT:
                case THRANTAX:    return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_DROP
                    && ((InvItem) entity).getID() == BAT_BONES) {
                bonesDropped();
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1) {
            if (object.getID() == GRUM_DOOR) {
                grumsDoor(object);
            } else {
                towerDoor(object);
            }
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            switch (object.getID()) {
                case BEEHIVE: hive(object, used); return;
                case CRYSTAL: shatter(used);      return;
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            switch (object.getID()) {
                case SHIP_BOW:
                case SHIP_STERN:   stowAway();   return;
                case GRUM_LADDER:  climbGrums(); return;
                case CHAOS_ALTAR:  recharge();   return;
                case TOWER_UP:     towerLadder(true);  return;
                case TOWER_DOWN:   towerLadder(false); return;
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2 && object.getID() == CHAOS_ALTAR) {
            searchAltar();
        }
    }

    // ----------------------------------------------------------- Camelot --

    /**
     * Gawain names Morgan LeFaye and where she lives, and once Lancelot has said
     * his piece about the deliveries both knights go back to their usual selves.
     */
    private void gawain(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc).npc("Good day to you sir");
        if (completed()) {
            c.options(new Choice("good day", "Know you of any quests sir knight?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("I think you've done the main quest we were on right now");
                    }
                }
            });
            c.start();
            return;
        }
        if (has(LANCELOT_TOLD) || !questStarted()) {
            c.options(new Choice("good day", "Know you of any quests sir knight?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("The king is the man to talk to if you want a quest");
                    }
                }
            });
            c.start();
            return;
        }
        if (has(GAWAIN_TOLD)) {
            c.options(new Choice("Any idea how to get into Morgan Le Faye's stronghold?",
                                 "Hello again") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("No you've got me stumped there");
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("Any ideas on how to get Merlin out that crystal?",
                             "Do you know how Merlin got trapped?",
                             "good day",
                             "Know you of any quests sir knight?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("I'm a little stumped myself")
                     .npc("We've tried opening it with anything and everything");
                    return;
                }
                if (option == 1) {
                    c.npc("I would guess this is the work of the evil Morgan Le Faye")
                     .player("And where can I find her?")
                     .npc("She lives in her stronghold to the south of here")
                     .npc("Guarded by some renegade knights led by Sir Morded")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(GAWAIN_TOLD);
                         }
                     })
                     .options(new Choice("Any idea how to get into Morgan Le Faye's stronghold",
                                         "Thankyou for the information") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc("No you've got me stumped there");
                             }
                         }
                     });
                    return;
                }
                if (option == 3) {
                    c.npc("The king is the man to talk to if you want a quest");
                }
            }
        });
        c.start();
    }

    private void lancelot(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("Greetings I am Sir Lancelot the greatest knight in the land")
            .npc("What do you want?");
        if (has(GAWAIN_TOLD) && !has(LANCELOT_TOLD)) {
            c.options(new Choice("Any ideas on how to get into Morgan Le Faye's stronghold?",
                                 "You're a little full of yourself aren't you?",
                                 "I seek a quest") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("That stronghold is built in a strong defensive position")
                         .npc("It's on a big rock sticking out into the sea")
                         .npc("There are two ways in that I know of, the large heavy front doors")
                         .npc("And the sea entrance, only penetrable by boat")
                         .npc("They take all their deliveries by boat")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(LANCELOT_TOLD);
                             }
                         });
                        return;
                    }
                    lancelotUsual(option - 1, c);
                }
            }.says(0, "Any ideas on how to get into Morgan Le Fayes's stronghold"));
            c.start();
            return;
        }
        if (open() && !has(GAWAIN_TOLD)) {
            c.options(new Choice("I want to get Merlin out of the crystal",
                                 "You're a little full of yourself aren't you?",
                                 "I seek a quest") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Well the knights of the round table can't manage it")
                         .npc("I can't see how a commoner like you could succeed where we have failed");
                        return;
                    }
                    lancelotUsual(option - 1, c);
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("You're a little full of yourself aren't you?",
                             "I seek a quest") {
            public void picked(int option, Conversation c) {
                lancelotUsual(option, c);
            }
        });
        c.start();
    }

    private void lancelotUsual(int option, Conversation c) {
        if (option == 0) {
            c.npc("I have every right to be proud of myself")
             .npc("My prowess in battle is world renowned");
            return;
        }
        c.npc("Leave questing to the profesionals")
         .npc("Such as myself");
    }

    // ---------------------------------------------------------- Catherby --

    /**
     * Arhein sells buckets and rope and will not give anyone a lift. Everything
     * about the fort is only offered once Lancelot has explained how the keep
     * gets its supplies; before that the player has no reason to ask.
     */
    private void arhein(final Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Hello would you like to trade")
            .options(new Choice("Yes ok", "No thankyou", "Is that your ship?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                c.stop();
                                openShop(npc);
                            }
                        });
                        return;
                    }
                    if (option == 1) {
                        return;
                    }
                    c.npc("Yes I use it to make deliver my goods up and down the coast")
                     .npc("These crates here are all ready for my next trip");
                    shipMenu(c);
                }
            })
            .start();
    }

    private void shipMenu(Conversation c) {
        if (knowsKeep()) {
            c.options(new Choice("Do you deliver to the fort just down the coast?",
                                 "Where do you deliver too?",
                                 "Are you rich then?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Yes I do have orders to deliver there from time to time")
                         .npc("I think I may have some bits and pieces for them")
                         .npc("when I leave here next actually")
                         .options(new Choice("can you drop me off on the way down please",
                                             "Aren't you worried about supplying evil knights") {
                             public void picked(int option, Conversation c) {
                                 if (option == 0) {
                                     c.npc("I don't think Sir Mordred would like that")
                                      .npc("He wants as few outsiders visiting as possible")
                                      .npc("I wouldn't want to lose his buisness");
                                     return;
                                 }
                                 c.npc("Hey you gotta take business where you can find it these days")
                                  .npc("Besides if I didn't supply them, someone else would");
                             }
                         });
                        return;
                    }
                    arheinTrade(option - 1, c);
                }
            }.says(1, "Where do you deliver to?"));
            return;
        }
        c.options(new Choice("Where do you deliver too?", "Are you rich then?") {
            public void picked(int option, Conversation c) {
                arheinTrade(option, c);
            }
        }.says(0, "Where do you deliver to?"));
    }

    private void arheinTrade(int option, Conversation c) {
        if (option == 0) {
            c.npc("Oh various places up and down the coast")
             .npc("Mostly Karamja and Port Sarim")
             .options(new Choice("I don't suppose I could get a lift anywhere?",
                                 "Well good luck with your buisness") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("I'm not quite ready to sail yet");
                     }
                 }
             }.says(1, "Well good luck with your business"));
            return;
        }
        c.npc("Business is going reasonably well")
         .npc("I wouldn't say I was the richest of merchants ever")
         .npc("But I'm doing reasonably well");
    }

    /**
     * The crates are ready and the merchant is not. Getting aboard needs him
     * looking the other way, which in 2002 meant a second player holding him in
     * conversation -- so the check is whether somebody else has him blocked.
     */
    private void stowAway() {
        Player p = getOwner();
        if (!knowsKeep()) {
            say("I have no reason to do that");
            return;
        }
        Npc merchant = world.getNpc(ARHEIN, 435, 445, 498, 512);
        if (merchant != null && (merchant.getBlocker() == null
                || merchant.getBlocker() == p)) {
            new Conversation(p, merchant).npc("Oi get away from there").start();
            return;
        }
        say("You climb into the ship's hold");
        p.teleport(HOLD_X, HOLD_Y, false);
        say("The ship sets sail");
    }

    private void candlemaker(final Npc npc) {
        Player p = getOwner();
        if (has(CANDLE_ASKED)) {
            new Conversation(p, npc)
                .npc("Have you got any wax yet?")
                .options(new Choice("Not yet", "Yes I have some now") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            return;
                        }
                        if (!holding(WAX_BUCKET)) {
                            c.npc("That's not a bucket of wax");
                            return;
                        }
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                take(WAX_BUCKET, 1);
                                give(BUCKET, 1);
                                give(BLACK_CANDLE, 1);
                                say("The candlemaker gives you a black candle");
                            }
                        });
                    }
                })
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .npc("Hi would you be interested in some of my fine candles");
        if (has(RITUAL)) {
            c.options(new Choice("Have you got any black candles?",
                                 "Yes please", "No thankyou") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Black candles hmm?")
                         .npc("It's very bad luck to make black candles")
                         .player("I can pay well for one")
                         .npc("I still dunno")
                         .npc("Tell you what, I'll supply you with a black candle")
                         .npc("If you can bring me a bucket full of wax")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(CANDLE_ASKED);
                             }
                         });
                        return;
                    }
                    if (option == 1) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                c.stop();
                                openShop(npc);
                            }
                        });
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("Yes please", "No thankyou") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            c.stop();
                            openShop(npc);
                        }
                    });
                }
            }
        });
        c.start();
    }

    /**
     * Repellant first, then the bucket. The repellant is not used up -- the bees
     * are, until the wax is taken.
     */
    private void hive(GameObject object, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        if (used.getID() == REPELLANT) {
            say("you squirt insect repellant on the beehive");
            say("You see bees leaving the hive");
            p.setFlag(HIVE_FLAG, 1);
            return;
        }
        if (used.getID() != BUCKET) {
            return;
        }
        if (p.getFlag(HIVE_FLAG) == 0) {
            say("The bees will not let you near the hive");
            return;
        }
        p.setFlag(HIVE_FLAG, 0);
        take(BUCKET, 1);
        give(WAX_BUCKET, 1);
        say("You fill the bucket with wax from the hive");
    }

    // ------------------------------------------------------- Keep LeFaye --

    /**
     * Morgan appears over her son. RSCD only tells a quest an npc died once it
     * is dead, so the offer to spare him is made too late to mean anything --
     * see the class comment.
     */
    private void mordredDown() {
        Player p = getOwner();
        if (!open() || has(RITUAL) || nearby(MORGAN, 6) != null) {
            return;
        }
        Npc her = summon(MORGAN, p.getX(), p.getY(), 120000);
        morgan(her);
    }

    private void morgan(Npc npc) {
        Player p = getOwner();
        if (has(RITUAL) || !open()) {
            new Conversation(p, npc).npc("Leave us be").start();
            return;
        }
        new Conversation(p, npc)
            .npc("Please spare my son")
            .options(new Choice("Tell me how to untrap Merlin and I might",
                                "No he deserves to die",
                                "OK then") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("You have guessed correctly that I'm responsible for that")
                     .npc("I suppose I can live with that fool Merlin being loose")
                     .npc("for the sake of my son")
                     .npc("Setting him free won't be easy though")
                     .npc("You will need to find a pentagram as close to the crystal as you can find")
                     .npc("You will need to drop some bats bones in the pentagram")
                     .npc("while holding a black candle")
                     .npc("This will summon the demon Thrantax")
                     .npc("You will need to bind him with magic words")
                     .npc("Then you will need the sword Excalibur with which the spell was bound")
                     .npc("Shatter the crystal with Excalibur")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(RITUAL);
                         }
                     });
                    morganMenu(c);
                }
            })
            .start();
    }

    private void morganMenu(Conversation c) {
        c.options(new Choice("So where can I find Excalibur?",
                             "What are the magic words?",
                             "OK I will go do all that") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("The lady of the lake has it")
                     .npc("I don't know if she will give it you though")
                     .npc("She can be rather temperamental");
                    morganMenu(c);
                    return;
                }
                if (option == 1) {
                    c.npc("You will find the magic words at the base of one of the chaos altars")
                     .npc("Which chaos altar I cannot remember");
                    morganMenu(c);
                }
            }
        }.says(2, "OK I will do all that"));
    }

    // ------------------------------------------------------ the magic words --

    private void recharge() {
        Player p = getOwner();
        p.getActionSender().sendMessage("@pnk@ You recharge at the altar.");
        p.getActionSender().sendSound("recharge");
        int maxPray = p.getMaxStat(PRAYER);
        if (p.getCurStat(PRAYER) < maxPray) {
            p.setCurStat(PRAYER, maxPray);
        }
        p.getActionSender().sendStat(PRAYER);
    }

    private void searchAltar() {
        say("You search the altar");
        if (!has(RITUAL) || has(WORDS_FOUND)) {
            say("You find nothing of interest");
            return;
        }
        say("You find some words scratched into the base of the altar");
        say("@yel@" + WORDS);
        set(WORDS_FOUND);
    }

    // ---------------------------------------------------------- Excalibur --

    private void lady(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("Good day to you " + (p.isMale() ? "sir" : "madam"));
        if (has(RITUAL) && !has(TEST_SET)) {
            c.options(new Choice("I seek the sword Excalibur",
                                 "Who are you?", "Good day") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Aye, I have that artifact in my possesion")
                         .npc("Tis very valuable and not an artifact to be given away lightly")
                         .npc("I would want to give it away only to one who is worthy and good")
                         .player("And how am I meant to prove that")
                         .npc("I will set a test for you")
                         .npc("First I need you to travel to Port Sarim")
                         .npc("Then go to the upstairs room of the jeweller's shop there")
                         .player("Ok that seems easy enough")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(TEST_SET);
                             }
                         });
                        return;
                    }
                    if (option == 1) {
                        c.npc("I am the lady of the lake");
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("Who are you?", "Good day") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("I am the lady of the lake");
                }
            }
        });
        c.start();
    }

    /** Her test is open again once the quest is over -- one loaf, one sword. */
    private boolean testRunning() {
        return has(TEST_SET) || completed();
    }

    /**
     * Step through a door, whichever side of it the player is stood on.
     *
     * Direction 0 is a wall between y-1 and y, anything else a wall between
     * x-1 and x, so the crossing is always those two tiles and nothing else.
     * A teleport rather than a walk, the same as every other door in RSCD.
     */
    private void stepThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        int x = door.getX(), y = door.getY();
        if (door.getDirection() == 0) {
            p.teleport(x, p.getY() >= y ? y - 1 : y, false);
        } else {
            p.teleport(p.getX() >= x ? x - 1 : x, y, false);
        }
    }

    /**
     * The two doors into Camelot's south-west tower.
     *
     * The tower is nothing but a way up to the crystal: a walled alcove off the
     * great hall at (459-463, 447-451) with a ladder at (460,451), the landing
     * above it at (459-462, 1392-1395), and then the crystal room. Merlin
     * himself stands beside the crystal at (462,2336) from the moment the world
     * boots -- he is an ordinary spawn, because RSC has no way to hide an npc
     * from one player and not another -- so anyone who wanders up there meets a
     * freed wizard while King Arthur is still telling them he is trapped.
     *
     * Shut until the pentagram ritual has bound the crystal, which is the first
     * moment the player has any reason to climb it, and open for good after
     * that: BOUND is the last thing set before Excalibur is swung, and
     * completed() covers it afterwards because finishing replaces the stage
     * rather than adding to it. The Holy grail sends the player back to Merlin
     * up here, so it cannot re-lock.
     */
    private void towerDoor(GameObject door) {
        if (!has(BOUND) && !completed()) {
            say("the door is locked");
            return;
        }
        stepThrough(door);
    }

    private void grumsDoor(GameObject door) {
        int x = door.getX(), y = door.getY();
        stepThrough(door);
        if (!testRunning() || nearby(BEGGAR, 6) != null) {
            return;
        }
        Npc him = summon(BEGGAR, x - 1, y, 120000);
        beggar(him);
    }

    private void beggar(final Npc npc) {
        Player p = getOwner();
        if (!testRunning()) {
            /* Transcript:Beggar has exactly one state: the Lady's test. He does
             * not exist outside it, so there is nothing recorded for him to say
             * here and nothing is invented. This branch only catches the window
             * between the test ending and the summon timing out. */
            return;
        }
        new Conversation(p, npc)
            .npc("Please sir, me and my family are starving")
            .npc("Could you possibly give me a loaf of bread?")
            .options(new Choice("Yes certainly",
                                "No I don't have any bread with me") {
                public void picked(int option, Conversation c) {
                    if (option == 1 || !holding(BREAD)) {
                        if (option == 0) {
                            c.player("Except that I don't have any bread at the moment");
                        }
                        c.npc("Well if you get some you know where to come");
                        return;
                    }
                    final Npc her = summon(LADY_GIFT, npc.getX(), npc.getY(), 30000);
                    c.npc("Thankyou very much")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             world.unregisterNpc(npc);
                         }
                     })
                     .npc(her, "Well done you have passed my test")
                     .npc(her, "Here is Excalibur, guard it well")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             take(BREAD, 1);
                             give(EXCALIBUR, 1);
                             dismiss(her);
                         }
                     });
                }
            })
            .start();
    }

    /**
     * The room the lady sent the player to. She is not in it -- her voice is,
     * and only for as long as the beggar downstairs has gone hungry.
     */
    private void climbGrums() {
        Player p = getOwner();
        p.teleport(p.getX(), Formulae.getNewY(p.getY(), true), false);
        if (!has(TEST_SET) || holding(EXCALIBUR)) {
            return;
        }
        final Npc her = summon(LADY_VOICE, p.getX(), p.getY() + 1, 30000);
        new Conversation(p, her)
            .player("Hello I am here, can I have Excalibur yet?")
            .npc("I don't think you are worthy enough")
            .npc("Come back when you are a better person")
            .then(new Effect() {
                public void run(Conversation c) {
                    dismiss(her);
                }
            })
            .start();
    }

    // ------------------------------------------------------------ the ritual --

    /**
     * Bat bones on the pentagram, with a lit black candle in hand and the words
     * already found. The bones are put straight back: in 2002 they never left
     * the inventory, which is how the demon came to be summonable sixty-four
     * times over.
     */
    private void bonesDropped() {
        Player p = getOwner();
        if (!open() || !has(WORDS_FOUND) || has(BOUND)) {
            return;
        }
        if (p.getX() != PENTAGRAM_X || p.getY() != PENTAGRAM_Y) {
            return;
        }
        if (!holding(LIT_BLACK_CANDLE)) {
            return;
        }
        for (Item lying : p.getViewArea().getItemsInView()) {
            if (lying.getID() == BAT_BONES && !lying.isRemoved()
                    && lying.isOn(PENTAGRAM_X, PENTAGRAM_Y)) {
                world.unregisterItem(lying);
                break;
            }
        }
        give(BAT_BONES, 1);
        if (nearby(THRANTAX, 4) != null) {
            return;
        }
        final Npc demon = summon(THRANTAX, PENTAGRAM_X, PENTAGRAM_Y + 1, 300000);
        new Conversation(p, demon)
            .player("Now what were those magic words?")
            .options(new Choice(INCANTATIONS) {
                public void picked(int option, Conversation c) {
                    c.npc("rarrrrgh");
                    if (option != 1) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                take(LIT_BLACK_CANDLE, 1);
                                say("Your black candle is consumed by the demon");
                                c.stop();
                                demon.attackPlayer(getOwner());
                            }
                        });
                        return;
                    }
                    c.npc("You have me in your control")
                     .npc("What do you wish of me?")
                     .npc("So that I may return to the nether regions")
                     .player("I wish to free Merlin from his giant crystal")
                     .npc("rarrrrgh")
                     .npc("It is done, you can now shatter Merlins crystal")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(BOUND);
                             dismiss(demon);
                         }
                     });
                }
            })
            .start();
    }

    /** Whether this player's tower top is the freed copy. */
    private boolean freed() {
        // Knighting rewrites the stage, so FREED alone is not enough.
        return has(FREED) || completed();
    }

    private void towerLadder(boolean up) {
        say(up ? "You climb up the ladder" : "You climb down the ladder");
        int x = freed() ? (up ? TOP_EAST_X : MID_EAST_X)
                        : (up ? TOP_WEST_X : MID_WEST_X);
        getOwner().teleport(x, up ? TOP_Y : MID_Y, false);
    }

    private void shatter(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != EXCALIBUR) {
            return;
        }
        if (!has(BOUND)) {
            say("The sword glances off the crystal");
            return;
        }
        if (has(FREED)) {
            say("The crystal is already empty");
            return;
        }
        say("You strike the crystal with Excalibur");
        say("@yel@The crystal shatters");
        set(FREED);
        /* The room the player is standing in still has the crystal in it, and
           always will. Freeing Merlin moves them to the copy that doesn't --
           where the freed Merlin stands for the thank-you. */
        p.teleport(TOP_EAST_X, TOP_Y, false);
        Npc wizard = world.getNpc(MERLIN, TOP_EAST_X - 3, TOP_EAST_X + 3,
                                          TOP_Y - 3, TOP_Y + 3);
        if (wizard == null) {
            return;
        }
        new Conversation(p, wizard)
            .npc("Thankyou thankyou")
            .npc("It's not fun being trapped in a giant crystal")
            .npc("Go speak to King Arthur, I'm sure he'll reward you")
            .start();
    }
}
