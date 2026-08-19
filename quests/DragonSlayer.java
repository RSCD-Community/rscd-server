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

/**
 * Dragon slayer.
 *
 * The last and largest of the free quests. Oziach will not sell rune plate to
 * anyone who has not killed Elvarg, and Elvarg is on an island nobody can find:
 * the only map of Crandor was torn in three and scattered. One piece is at the
 * bottom of Melzar's maze, one behind a riddle door in the dwarven mine, and one
 * was stolen by a goblin now sitting in the Port Sarim jail. With the map made
 * whole, a bought and patched-up ship, and an old sailor willing to steer it,
 * the island can be reached.
 *
 * Two quest points, and Defense and Strength experience of level * 300 + 650
 * each. No items -- the reward is the right to buy the armour.
 *
 * Six stages, and they are the six things the player cannot undo:
 *
 *   1 STARTED        Oziach has named Elvarg. Five npcs change what they say.
 *   2 SHIP_BOUGHT    Klarense has been paid, and the Lumbridge Lady is theirs.
 *   3 SHIP_REPAIRED  the hole in the hold is planked over.
 *   4 NED_AGREED     Ned will captain her.
 *   5 SAILED         the player has stood on Crandor.
 *   6 FINISHED       Elvarg is dead.
 *
 * Everything else is read off the inventory, the way Jagex did it: which map
 * pieces are held, whether the maze keys have been found, whether the Duke's
 * shield has been collected. None of that is progress, it is luggage.
 *
 * Ned is not here. He belongs to Prince Ali rescue as well and so lives in
 * src/ as an npc handler; he asks this quest "ship-ready" and reports back
 * "ned-agreed" through Quest.reached and Quest.note, which were added for him.
 * The handler never learns a stage number and never writes one.
 *
 * Deviations, all of them deliberate and none of them silent:
 *
 *   * The hull is repaired in one go -- three planks and twelve nails, with a
 *     hammer in hand -- rather than plank by plank. A quest persists exactly one
 *     integer and a half-planked hull is not a stage. The counts are the wiki
 *     walkthrough's; the transcript says only "a few planks, hammered in with
 *     steel nails".
 *
 *   * The ship breaks again on the crossing, as Jagex modelled: the fourth
 *     copy of the hold, (281,3472), is the Lumbridge Lady holed a second time
 *     on Crandor's rocks with Ned still aboard, and his "you'll have to fix
 *     it again" exchange lives there. "Sailed AND hull broken" cannot ride
 *     the stage integer without renumbering FINISHED, so it rides a persisted
 *     quest var instead: set by the crunch in sail(), cleared by the second
 *     repair. Boarding either gangplank routes by it.
 *
 *   * Ned has no spawn on the Crandor wreck, so the two lines he speaks there
 *     ("the ship took a nasty jar from those rocks", the sun-lotion answer)
 *     have no npc to attach to. Both Neds in the world data stand in the Port
 *     Sarim hold. Either our spawn file is missing one or Jagex reached those
 *     lines some way we have not found; guessing a spawn would put an npc on
 *     the map on the strength of a transcript heading, so it is flagged.
 *
 *   * Oziach's armour shop is opened from inside this quest rather than by the
 *     ShopKeeper npc handler, because a handler is tried before quest dispatch
 *     and registering one for Oziach would take him away from Dragon slayer
 *     entirely. The stock is his -- two rune plate mail bodies, restocking
 *     slowly -- and lives in Shops.xml with every other shop; only the opening
 *     of it is here.
 *
 *   * Elvarg breathes fire in name only. The anti dragon breath shield is real,
 *     and collected, and does nothing, because there is no dragonfire in the
 *     combat code yet.
 *
 * The maze entrance door, id 60 at (338,632), was already handled in
 * InvUseOnObject against the maze key long before this quest existed, and is
 * left there untouched. The six coloured doors inside are claimed here; every
 * placement of ids 48 to 53 in the world is in that maze, so no coordinate guard
 * is needed. The emergency exits, door 54, keep their ordinary behaviour.
 *
 * Dialogue is Jagex's, from the recorded transcripts. Wormbrain has none in any
 * source we hold, so he has none here: he is claimed only so that killing him
 * yields the map piece.
 */
public class DragonSlayer extends Quest {

    public final static int UID = Quests.DRAGON_SLAYER;

    private static final int STARTED = 1;
    private static final int SHIP_BOUGHT = 2;
    private static final int SHIP_REPAIRED = 3;
    private static final int NED_AGREED = 4;
    private static final int SAILED = 5;
    private static final int FINISHED = 6;

    /**
     * Persisted flag: the hull has been holed again on Crandor's rocks and not
     * yet re-patched. The one piece of state the stage integer cannot carry.
     */
    private static final int VAR_REHOLED = 0;

    /** The Champions' Guild will not open below this. */
    private static final int GUILD_POINTS = 32;

    private static final int DEFENSE = 1;  /* skill index */
    private static final int STRENGTH = 2;

    // ---------------------------------------------------------------- npcs --

    private static final int GUILDMASTER = 111;
    private static final int OZIACH = 187;
    private static final int ORACLE = 197;
    private static final int KLARENSE = 193;
    private static final int DUKE = 198;
    private static final int WORMBRAIN = 192;
    private static final int ELVARG = 196;

    /** Melzar's maze, one dedicated npc per coloured key, in the order met. */
    private static final int MAZE_RAT = 177;
    private static final int MAZE_GHOST = 178;
    private static final int MAZE_SKELETON = 179;
    private static final int MAZE_ZOMBIE = 180;
    private static final int MAZE_DEMON = 181;
    private static final int MELZAR = 182;

    // --------------------------------------------------------------- doors --

    private static final int DOOR_RED = 48;
    private static final int DOOR_ORANGE = 49;
    private static final int DOOR_YELLOW = 50;
    private static final int DOOR_BLUE = 51;
    private static final int DOOR_MAGENTA = 52;
    private static final int DOOR_BLACK = 53;

    /** The riddle door in the dwarven mine, its only placement in the world. */
    private static final int DOOR_ORACLE = 57;
    private static final int DOOR_ORACLE_X = 259;
    private static final int DOOR_ORACLE_Y = 3334;

    /** Karamja's volcano to Crandor's dungeon, and the way back after the wreck. */
    private static final int STRANGE_WALL = 58;

    /** Elvarg's lair. */
    private static final int LAIR_DOOR = 59;

    /** Every door in the maze opens onto its own doorframe. */
    private static final int OPEN_DOOR = 1;

    // ------------------------------------------------------------- objects --

    /** Melzar's chest, at the bottom of the maze. Shuts to 229. */
    private static final int MELZAR_CHEST = 228;
    private static final int MELZAR_CHEST_SHUT = 229;
    /** The chest behind the Oracle's door. Shuts to 231. */
    private static final int MINE_CHEST = 230;
    private static final int MINE_CHEST_SHUT = 231;

    /** The Lumbridge Lady's gangplanks, Port Sarim. */
    private static final int GANGPLANK_SARIM_W = 224;
    private static final int GANGPLANK_SARIM_E = 225;
    /** The same ship, wrecked on Crandor. */
    private static final int GANGPLANK_CRANDOR_E = 233;
    private static final int GANGPLANK_CRANDOR_W = 234;

    /**
     * The hole in the hold. Two of them exist, and BOTH are Port Sarim's.
     *
     * The note that used to sit here -- "only Port Sarim's is patchable, the
     * other is the same ship after she has been holed again on Crandor's
     * rocks" -- was a guess, and the world data contradicts it. There is no
     * ship interior on Crandor at all: the wreck there is deck-level, plane 0,
     * around (405-410,641). Both holes are in the same room twenty-two tiles
     * apart, because that room exists four times. See {@link #enterHold}.
     */
    private static final int HULL_HOLE = 226;
    private static final int HULL_HOLE_X = 258;
    private static final int HULL_HOLE_Y = 3471;
    /** The same hole in the copy of the hold that Ned stands in. */
    private static final int HULL_HOLE_NED_X = 280;
    private static final int HULL_HOLE_NED_Y = 3472;

    /**
     * The hold, which Jagex built four times.
     *
     * A game object is the same object for every player, so a hole cannot be
     * hidden from one and shown to another. Jagex's answer was to model the
     * room four times and move the player between the copies. The x axis is
     * whether Ned is aboard and the y axis is whether the hull is sound:
     *
     *     (259,3472)  holed, no Ned    she has not been patched yet
     *     (259,3493)  sound, no Ned    patched, Ned not asked yet
     *     (281,3493)  sound, Ned       Ned aboard -- the crossing starts here
     *     (281,3472)  holed, Ned       back from Crandor, holed again
     *
     * All four are plane 3 under the Port Sarim dock, and each is an exact
     * translation of the first: the ladder out sits at +1y in every copy, the
     * barrels and sacks line up, and npc 194 is spawned once in each of the two
     * eastern rooms. The fourth is not reachable yet -- see the deviations in
     * the class comment.
     */
    private static final int HOLD_X = 259;
    private static final int HOLD_Y = 3472;
    /** The eastern pair, the two copies Ned is spawned in. */
    private static final int HOLD_NED_X = 281;
    /** The northern pair, the two copies with no hole in them. */
    private static final int HOLD_SOUND_Y = 3493;

    /**
     * The ladder out, one in each copy of the hold.
     *
     * Claimed rather than left to ObjectAction's generic climb, which lands the
     * player on their own x one plane up. That happens to be the dock from the
     * first copy and is open water or dry land nowhere near the ship from the
     * other three, so wiring the copies up without claiming this would strand
     * anyone who used it.
     */
    private static final int HOLD_LADDER = 227;

    private static final int DOCK_X = 259;
    private static final int DOCK_Y = 642;
    private static final int CRANDOR_X = 408;
    private static final int CRANDOR_Y = 640;

    // --------------------------------------------------------------- items --

    private static final int COINS = 10;
    private static final int MAZE_KEY = 421;
    private static final int KEY_RED = 390;
    private static final int KEY_ORANGE = 391;
    private static final int KEY_YELLOW = 392;
    private static final int KEY_BLUE = 393;
    private static final int KEY_MAGENTA = 394;
    private static final int KEY_BLACK = 395;

    private static final int MAP = 415;
    private static final int PIECE_MAZE = 416;
    private static final int PIECE_MINE = 417;
    private static final int PIECE_GOBLIN = 418;

    private static final int PLANK = 410;
    private static final int NAILS = 419;
    private static final int HAMMER = 168;
    private static final int SHIELD = 420;

    /** The Oracle's four, in the order of her riddle. */
    private static final int MIND_BOMB = 268;
    private static final int SILK = 200;
    private static final int LOBSTER_POT = 375;
    private static final int UNFIRED_BOWL = 340;

    private static final int SHIP_PRICE = 2000;
    private static final int PLANKS_NEEDED = 3;
    /*
     * Was 30, invented -- the class comment on repairHull() openly admitted
     * "Jagex's counts are not recorded in anything we hold... these are
     * ours." The real number, per classic.runescape.wiki's own walkthrough:
     * "12 nails (made from 6 steel bars, 34 Smithing required), 3 planks."
     */
    private static final int NAILS_NEEDED = 12;

    public DragonSlayer(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Dragon slayer");
        setFinalStage(FINISHED);

        associateNpc(GUILDMASTER);
        associateNpc(OZIACH);
        associateNpc(ORACLE);
        associateNpc(KLARENSE);
        associateNpc(DUKE);
        associateNpc(WORMBRAIN);
        associateNpc(ELVARG);
        associateNpc(MAZE_RAT);
        associateNpc(MAZE_GHOST);
        associateNpc(MAZE_SKELETON);
        associateNpc(MAZE_ZOMBIE);
        associateNpc(MAZE_DEMON);
        associateNpc(MELZAR);

        associateObject(MELZAR_CHEST);
        associateObject(MINE_CHEST);
        associateObject(HULL_HOLE);
        associateObject(HOLD_LADDER);
        associateObject(GANGPLANK_SARIM_W);
        associateObject(GANGPLANK_SARIM_E);
        associateObject(GANGPLANK_CRANDOR_E);
        associateObject(GANGPLANK_CRANDOR_W);

        associateDoor(DOOR_RED);
        associateDoor(DOOR_ORANGE);
        associateDoor(DOOR_YELLOW);
        associateDoor(DOOR_BLUE);
        associateDoor(DOOR_MAGENTA);
        associateDoor(DOOR_BLACK);
        associateDoor(DOOR_ORACLE);
        associateDoor(STRANGE_WALL);
        associateDoor(LAIR_DOOR);

        // All three, so that using any two of them on each other reaches this
        // quest -- an item pair is only dispatched when one quest owns both.
        associateItem(PIECE_MAZE);
        associateItem(PIECE_MINE);
        associateItem(PIECE_GOBLIN);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Prove yourself a true hero. Kill the mighty dragon Elvarg of Crandor Island and earn the right to buy and wear the powerful rune plate mail body.");
        setStartPoint("Champion's guild");
        setSpeakTo("Guildmaster");
        setMissionLength("Long");
        require("32 quest points");
        /* The manual's other two lines; neither has a code gate. The 32 quest
           points above is the real one, in talkToGuildmaster(). */
        require("Level 33 magic");
        require("Be able to kill a lvl 110 dragon");
        rewardExp(DEFENSE, 650, 300);
        rewardExp(STRENGTH, 650, 300);
        rewardOther("The right to buy rune plate mail bodies from Oziach");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Dragon slayer quest");
    }

    // ------------------------------------------------- the named questions --

    /**
     * What Ned needs to know, and all he needs to know. None of it is a stage
     * number: he asks in words and this quest decides what the words mean.
     *
     * "map-ready" is deliberately looser than the joined map. Jagex's Ned takes
     * the three pieces as readily as the finished chart -- the transcript
     * records a different message for each -- so requiring the assembly would
     * have invented a step. "map-joined" is only there to pick between those
     * two messages.
     */
    public boolean reached(String key) {
        if ("ship-ready".equals(key)) {
            return getStage() >= SHIP_REPAIRED;
        }
        if ("map-ready".equals(key)) {
            return holdsTheMap();
        }
        if ("map-joined".equals(key)) {
            return getOwner().getInventory().countId(MAP) > 0;
        }
        if ("sailed".equals(key)) {
            return getStage() >= SAILED;
        }
        return false;
    }

    /**
     * Ned has agreed to captain the Lumbridge Lady, or is casting off.
     *
     * "ned-agreed" is only meaningful at the one stage it can follow --
     * reporting it twice, or early, changes nothing. "sail" is the crossing,
     * which is his to start and this quest's to carry out; it checks the map
     * again rather than trusting the caller, because the two are separated by
     * however long the player takes to read four lines of dialogue.
     */
    public void note(String key) {
        if ("ned-agreed".equals(key) && getStage() == SHIP_REPAIRED) {
            setStage(NED_AGREED);
            return;
        }
        if ("sail".equals(key) && getStage() >= NED_AGREED && holdsTheMap()) {
            sail();
        }
    }

    // -------------------------------------------------------------- routing --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                killed(npc);
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case GUILDMASTER: talkToGuildmaster(npc); break;
                case OZIACH:      talkToOziach(npc);      break;
                case ORACLE:      talkToOracle(npc);      break;
                case KLARENSE:    talkToKlarense(npc);    break;
                case DUKE:        talkToDuke(npc);        break;
                default: break;   // Wormbrain, Elvarg and the maze have nothing to say
            }
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1) {
                openDoor(object);
                return;
            }
            if (trigger == QuestTrigger.DOOR_ACT2) {
                getOwner().getActionSender().sendMessage("The door is shut");
                return;
            }
            switch (object.getID()) {
                case MELZAR_CHEST:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchChest(object, PIECE_MAZE); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { shut(object, MELZAR_CHEST_SHUT); }
                    break;
                case MINE_CHEST:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchChest(object, PIECE_MINE); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { shut(object, MINE_CHEST_SHUT); }
                    break;
                case GANGPLANK_SARIM_W:
                case GANGPLANK_SARIM_E:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { boardAtSarim(); }
                    break;
                case GANGPLANK_CRANDOR_E:
                case GANGPLANK_CRANDOR_W:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { boardAtCrandor(); }
                    break;
                case HULL_HOLE:
                    getOwner().getActionSender().sendMessage(
                        "There is a large hole in the ship's hull");
                    break;
                case HOLD_LADDER:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { climbOut(); }
                    break;
            }
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject
                && ((GameObject) entity).getID() == HULL_HOLE) {
            repairHull((GameObject) entity, used);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_ITEM && entity instanceof InvItem) {
            assembleMap();
            return;
        }
        triggerEntity(trigger, entity);
    }

    // --------------------------------------------------------- guildmaster --

    /**
     * The Champions' Guild doorman. He turns away anyone under 32 quest points
     * and, for everyone else, is where the trail to Oziach begins.
     *
     * The guild's own door is not claimed here; that is guild membership, not
     * this quest, and it belongs with whatever eventually owns the building.
     */
    private void talkToGuildmaster(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (p.getQuestPoints() < GUILD_POINTS) {
            c.npc("You have not proven yourself worthy to enter here yet").start();
            return;
        }

        c.options(new Choice("What is this place?",
                             "Do you know where I could get a rune plate mail body?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("This is the champion's guild")
                     .npc("Only Adventurers who have proved themselves worthy")
                     .npc("by gaining influence from quests are allowed in here")
                     .npc("As the number of quests in the world rises")
                     .npc("So will the requirements to get in here")
                     .npc("But so will the rewards");
                    return;
                }
                c.npc("I have a friend called Oziach who lives by the cliffs")
                 .npc("He has a supply of rune plate mail")
                 .npc("He may sell you some if you're lucky, he can be a little strange though");
            }
        });
        c.start();
    }

    // -------------------------------------------------------------- oziach --

    /**
     * Oziach's Body Armour Shop, two rune plate mail bodies at a time.
     *
     * Opened from inside the quest rather than by an npc handler. Shops are
     * normally served by ShopKeeper, registered against an npc id in
     * NpcHandlers.xml -- but a handler is tried before quest dispatch, so
     * registering Oziach would take him away from this quest and with him every
     * word of Dragon slayer he speaks. The shop area is in Shops.xml like any
     * other; only the door to it is here.
     */
    private void openArmourShop(Conversation c) {
        Player p = c.getPlayer();
        Shop shop = world.getShop(c.getNpc());
        if (shop == null) {
            p.getActionSender().sendMessage("@gre@Oziach has nothing to sell today");
            return;
        }
        p.setAccessingShop(shop);
        p.getActionSender().showShop(shop);
    }

    private void talkToOziach(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.player("I have slain the dragon")
             .npc("Well done")
             .options(new Choice("Can I buy a rune mail body now please?", "Thank you") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.then(new Effect() {
                             public void run(Conversation c) {
                                 openArmourShop(c);
                             }
                         });
                     }
                 }
             });
            c.start();
            return;
        }

        c.npc("Aye tiz a fair day my friend")
         .options(new Choice("Can you sell me some rune plate mail?",
                             "I'm not your friend",
                             "Yes it's a very nice day") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("I'd be surprised if your anyone's friend with that sort of manners");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Aye may the Gods walk by your side");
                     return;
                 }
                 runePlate(c);
             }
         });
        c.start();
    }

    private void runePlate(Conversation c) {
        // Nothing physically stops a player from walking straight to
        // Oziach's house without ever passing the Guildmaster -- the 32
        // quest point check has to be repeated here, or it isn't a gate
        // at all, just a suggestion.
        if (c.getPlayer().getQuestPoints() < GUILD_POINTS) {
            c.npc("I don't know what you're talking about")
             .npc("Perhaps you should prove yourself at the Champions' Guild first");
            return;
        }
        c.npc("Soo how does thee know I'ave some?")
         .options(new Choice("The guildmaster of the champion guild told me",
                             "I am a master detective") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("Well if you're worthy of his advise")
                      .npc("You must have something going for you")
                      .npc("He has been known to let some weeklin's into his guild though")
                      .npc("I don't want just any old pumpkinmush to have this armour")
                      .npc("jus cos they have a large amount of cash");
                 } else {
                     c.npc("well however you found out about it")
                      .npc("This is armour fit for a hero to be sure")
                      .npc("So you'll need to prove to me that you're a hero before you can buy some");
                 }
                 prove(c);
             }
         });
    }

    private void prove(Conversation c) {
        c.options(new Choice("So how am I meant to prove that?",
                             "That's a pity, I'm not a hero") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                c.npc("Well if you want to prove yourself")
                 .npc("You could try and defeat Elvarg the dragon of the Isle of Crandor")
                 .options(new Choice("A dragon, that sounds like fun",
                                     "I may be a champion, but I don't think I'm up to dragon killing yet") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Yes I can understand that");
                             return;
                         }
                         theDragon(c);
                     }
                 });
            }
        });
    }

    /**
     * The warning about her breath, and with it the checkpoint: from here the
     * Oracle, Klarense, the Duke, Ned and Oziach himself all change what they
     * say. The transcript marks this exact line as the moment they do.
     */
    private void theDragon(Conversation c) {
        c.npc("Elvarg really is one of the most powerful dragons")
         .npc("I really wouldn't recommend charging in without special equipment")
         .npc("Her breath is the main thing to watch out for")
         .npc("You can get fried very fast")
         .npc("Unless you have a special flameproof antidragon shield")
         .npc("It won't totally protect you")
         .npc("but it should prevent some of the damage to you")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (!questStarted()) {
                     setStage(STARTED);
                 }
             }
         });
        theHunt(c);
    }

    /**
     * Where the island is, where the pieces are, and where the shield is. Every
     * answer re-offers the menu, because in Jagex's version they all did.
     */
    private void theHunt(Conversation c) {
        c.options(new Choice("So where can I find this dragon?",
                             "Where can I get an antidragon shield?",
                             "Ok I'll try and get everything together") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("I believe the Duke of Lumbrige Castle may have one in his armoury");
                    theHunt(c);
                    return;
                }
                if (option == 2) {
                    c.npc("Fare ye well");
                    return;
                }
                c.npc("That is a problem too yes")
                 .npc("No one knows where the Isle of Crandor is located")
                 .npc("There was a map")
                 .npc("But it was torn up into three pieces")
                 .npc("Which are now scattered across Asgarnia")
                 .npc("You'll also struggle to find someone bold enough to take a ship to Crandor Island");
                thePieces(c);
            }
        });
    }

    private void thePieces(Conversation c) {
        c.options(new Choice("Where is the first piece of map?",
                             "Where is the second piece of map?",
                             "Where is the third piece of map?",
                             "Ok I'll try and get everything together") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("deep in a strange building known as Melzar's maze")
                     .npc("Located north west of Rimmington")
                     .npc("You will need this to get in")
                     .npc("This is the key to the front entrance to the maze")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             giveMazeKey();
                         }
                     });
                    thePieces(c);
                    return;
                }
                if (option == 1) {
                    c.npc("You will need to talk to the oracle on the ice mountain");
                    thePieces(c);
                    return;
                }
                if (option == 2) {
                    c.npc("That was stolen by one of the goblins from the goblin village");
                    thePieces(c);
                    return;
                }
                c.npc("Fare ye well");
            }
        });
    }

    /** One key at a time; asking again after losing it gets another. */
    private void giveMazeKey() {
        Player p = getOwner();
        if (p.getInventory().countId(MAZE_KEY) > 0) {
            return;
        }
        p.getInventory().add(new InvItem(MAZE_KEY, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("Oziach gives you a key");
    }

    // -------------------------------------------------------------- oracle --

    /**
     * The Oracle on Ice mountain. Her riddle names the four things that open the
     * door in the mine below: a wizard's mind bomb, a sheet of silk, a lobster
     * pot and an unfired bowl.
     *
     * Off quest she has eight stock answers. She is claimed by this quest, so
     * they are here too -- taking an npc means taking all of what it says, and
     * leaving them out would make her mute for everyone who is not on the quest.
     * Which one she gives is picked from the world clock, not saved.
     */
    private void talkToOracle(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (!questStarted() || completed() || getOwner().getInventory().countId(MAP) > 0
                || getOwner().getInventory().countId(PIECE_MINE) > 0) {
            wisdom(c);
            c.start();
            return;
        }

        c.picker(new Choice("I seek a piece of the map of the isle of Crondor",
                             "Can you impart your wise knowledge to me oh oracle?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("I seek a piece of the map of the isle of Crandor")
                     .npc("The map's behind a door below")
                     .npc("But entering is rather tough")
                     .npc("And this is what you need to know")
                     .npc("You must have the following stuff")
                     .npc("First a drink used by the mage")
                     .npc("Next some worm string, changed to sheet")
                     .npc("Then a small crustacean cage")
                     .npc("Last a bowl that's not seen heat");
                    return;
                }
                wisdom(c);
            }
        });
        c.start();
    }

    private static final String[] WISDOM = new String[]{
        "You must search from within to find your true destiny",
        "No crisps at the party",
        "It is cunning, almost foxlike",
        "Is it waking up time, I'm not quite sure",
        "When in Asgarnia do as the Asgarnians do",
        "The light at the end of the tunnel is the demon infested lava pit",
        "Watch out for cabbages they are green and leafy",
        "Too many cooks spoil the anchovie pizza"
    };

    private void wisdom(Conversation c) {
        int which = (int) ((System.currentTimeMillis() / 1000L) % WISDOM.length);
        c.player("Can you impart your wise knowledge to me oh oracle?")
         .npc(WISDOM[which]);
    }

    // ------------------------------------------------------------ klarense --

    private void talkToKlarense(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (getStage() >= SHIP_BOUGHT) {
            sold(c);
            c.start();
            return;
        }

        c.npc("You're interested in a trip on the Lumbridge Lady are you?")
         .npc("I admit she looks fine, but she isn't seaworthy right now");

        if (!questStarted()) {
            c.options(new Choice("Do you know when she will be seaworthy",
                                 "Ah well never mind") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("No not really")
                         .npc("Port Sarim's shipbuilders aren't very efficient")
                         .npc("So it could be quite a while");
                    }
                }
            });
            c.start();
            return;
        }

        c.options(new Choice("Would you take me to Crandor Isle when it's ready?",
                             "I don't suppose I could buy it",
                             "Do you know when she will be seaworthy",
                             "Ah well never mind") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well even if I knew how to get there")
                     .npc("I wouldn't like to risk it")
                     .npc("Especially after to goin to all the effort of fixing the old girl up");
                    return;
                }
                if (option == 1) {
                    buyShip(c);
                    return;
                }
                if (option == 2) {
                    c.npc("No not really")
                     .npc("Port Sarim's shipbuilders aren't very efficient")
                     .npc("So it could be quite a while");
                }
            }
        });
        c.start();
    }

    private void buyShip(Conversation c) {
        c.npc("I guess you could")
         .npc("I'm sure the work needed to do on it wouldn't be too expensive")
         .npc("How does " + SHIP_PRICE + " gold sound for a price?")
         .options(new Choice("Yep sounds good",
                             "I'm not paying that much for a broken boat") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("That's Ok, I didn't particularly want to sell anyway");
                     return;
                 }
                 if (c.getPlayer().getInventory().countId(COINS) < SHIP_PRICE) {
                     c.player("Except I don't have that much money on me");
                     return;
                 }
                 c.take(COINS, SHIP_PRICE)
                  .npc("Ok she's all yours")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(SHIP_BOUGHT);
                      }
                  });
             }
         });
    }

    /** Klarense once the ship is somebody else's problem. */
    private void sold(Conversation c) {
        c.options(new Choice("So would you like to sail this ship to Crandor Isle for me?",
                             "So what needs fixing on this ship?",
                             "What are you going to do now you don't have a ship?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("No not me, I'm frightened of dragons");
                    return;
                }
                if (option == 1) {
                    c.npc("Well the big gaping hole in the hold is the main problem")
                     .npc("you'll need a few planks")
                     .npc("Hammered in with steel nails");
                    return;
                }
                c.npc("Oh I'll be fine")
                 .npc("I've got work as Port Sarim's first life guard");
            }
        });
    }

    // ---------------------------------------------------------------- duke --

    private void talkToDuke(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc)
            .npc("Greetings welcome to my castle");

        final boolean wantsShield = questStarted() && !completed()
            && getOwner().getInventory().countId(SHIELD) < 1;

        if (!wantsShield) {
            c.options(new Choice("Have you any quests for me?", "Where can I find money?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        anyQuests(c);
                        return;
                    }
                    c.npc("I've heard the blacksmiths are prosperous amoung the peasantry")
                     .npc("Maybe you could try your hand at that");
                }
            });
            c.start();
            return;
        }

        c.options(new Choice("I seek a shield that will protect me from dragon breath",
                             "Have you any quests for me?",
                             "Where can I find money?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    anyQuests(c);
                    return;
                }
                if (option == 2) {
                    c.npc("I've heard the blacksmiths are prosperous amoung the peasantry")
                     .npc("Maybe you could try your hand at that");
                    return;
                }
                c.npc("A knight going on a dragon quest hmm?")
                 .npc("A most worthy cause")
                 .npc("Guard this well my friend")
                 .give(new InvItem(SHIELD, 1))
                 .message("The Duke gives you an anti dragon breath shield");
            }
        });
        c.start();
    }

    /**
     * "Have you any quests for me?" is the Duke's line, but the answer stopped
     * being "All is well for me" the day Rune mysteries arrived: the Duke's
     * strange talisman is its opening move. The branch lives here rather than
     * in RuneMysteries.java because the conversation dispatcher gives an npc
     * one owner, and the Duke has been Dragon slayer's since long before. It
     * moves the other quest along the sanctioned way -- report the event with
     * note(), let the quest decide what it means.
     */
    private void anyQuests(Conversation c) {
        if (getOwner().getQuestManager().reached(Quests.RUNE_MYSTERIES, "started")
                || getOwner().getQuestManager().completed(Quests.RUNE_MYSTERIES)) {
            c.npc("All is well for me");
            return;
        }
        c.npc("As it happens, yes")
         .npc("A workman digging our new cellar turned up this odd talisman")
         .npc("My advisors can make nothing of it")
         .npc("Take it to the head wizard at the tower south of here")
         .npc("Sedridor is his name, you'll find him in the basement")
         .give(new InvItem(1291, 1))
         .message("The Duke hands you a strange air talisman")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.getPlayer().getQuestManager().note(Quests.RUNE_MYSTERIES, "duke-sent");
             }
         });
    }

    // ---------------------------------------------------------------- kills --

    /**
     * What dies in this quest and what it leaves behind.
     *
     * Fired for the killer only and before the npc's own drop table, so the key
     * and the map piece land alongside whatever else the corpse was going to
     * give up rather than instead of it.
     */
    private void killed(Npc npc) {
        int key;
        switch (npc.getID()) {
            case ELVARG:
                // Crandor can be reached through the volcano without ever having
                // spoken to Oziach, so killing her only finishes a quest that was
                // actually running.
                if (questStarted() && !completed()) {
                    getOwner().getActionSender().sendMessage(
                        "You have slain Elvarg, the dragon of Crandor");
                    setStage(FINISHED);
                }
                return;
            case WORMBRAIN:
                // Both other pieces only come out of their chests once the
                // quest is actually running (searchChest() checks
                // questStarted()); this one has to match, or the goblin's
                // piece is the odd one out and free to anybody who kills him.
                if (questStarted() && !completed() && !holdsPiece(PIECE_GOBLIN)) {
                    drop(npc, PIECE_GOBLIN);
                }
                return;
            case MAZE_RAT:      key = KEY_RED;     break;
            case MAZE_GHOST:    key = KEY_ORANGE;  break;
            case MAZE_SKELETON: key = KEY_YELLOW;  break;
            case MAZE_ZOMBIE:   key = KEY_BLUE;    break;
            /* Melzar holds the magenta key and the demon behind him holds the
               black one, not the other way round. These two were swapped: the
               demon's own drop table hands out the black key as well, so the
               maze could be finished but the demon paid twice and Melzar paid
               with a key to a door already open. */
            case MELZAR:        key = KEY_MAGENTA; break;
            case MAZE_DEMON:    key = KEY_BLACK;   break;
            default: return;
        }
        drop(npc, key);
    }

    private void drop(Npc npc, int id) {
        world.registerItem(new Item(id, npc.getX(), npc.getY(), 1, getOwner()));
    }

    // --------------------------------------------------------------- chests --

    /**
     * A map piece from a chest, once per chest and once per lost piece.
     *
     * Which pieces have been taken is not saved -- there are three of them and
     * one integer to hold the quest -- so the test is whether the player has this
     * one anywhere on them, in the map, or behind them in a finished quest. That
     * also means a piece dropped by accident can be fetched again, which is the
     * same mercy Redbeard Frank shows over his key.
     */
    private void searchChest(GameObject chest, int piece) {
        Player p = getOwner();
        if (!questStarted() || completed() || holdsPiece(piece)) {
            p.getActionSender().sendMessage("You search the chest but find nothing");
            return;
        }
        p.getInventory().add(new InvItem(piece, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find a piece of a map inside the chest");
    }

    /** Whether this piece is already accounted for. */
    private boolean holdsPiece(int piece) {
        return getOwner().getInventory().countId(piece) > 0
            || getOwner().getInventory().countId(MAP) > 0;
    }

    /** Swing a chest shut for a moment. The map reopens it. */
    private void shut(GameObject chest, int shutID) {
        getOwner().getActionSender().sendMessage("You close the chest");
        world.registerGameObject(new GameObject(chest.getLocation(), shutID,
            chest.getDirection(), chest.getType()));
        world.delayedSpawnObject(chest.getLoc(), 3000);
    }

    // ----------------------------------------------------------------- map --

    /**
     * Three pieces into one map.
     *
     * Reached by using any piece on any other, and it takes all three or none:
     * two thirds of a map is still two thirds of a map.
     */
    private void assembleMap() {
        Player p = getOwner();
        if (p.getInventory().countId(PIECE_MAZE) < 1
                || p.getInventory().countId(PIECE_MINE) < 1
                || p.getInventory().countId(PIECE_GOBLIN) < 1) {
            p.getActionSender().sendMessage("You need all three pieces of the map");
            return;
        }
        p.getInventory().remove(PIECE_MAZE, 1);
        p.getInventory().remove(PIECE_MINE, 1);
        p.getInventory().remove(PIECE_GOBLIN, 1);
        p.getInventory().add(new InvItem(MAP, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You join the pieces of the map together");
    }

    // ---------------------------------------------------------------- ship --

    /**
     * The Port Sarim gangplank.
     *
     * Always the way down into the hold, and nothing else. It used to sail the
     * ship the moment the player was ready, which is why nobody ever met Ned:
     * the crossing is his, asked for in his own dialogue, and the gangplank has
     * no business doing it.
     */
    private void boardAtSarim() {
        Player p = getOwner();
        if (!questStarted()) {
            p.getActionSender().sendMessage("The Lumbridge Lady is Klarense's ship");
            return;
        }
        p.getActionSender().sendMessage("You climb aboard the Lumbridge Lady");
        enterHold();
    }

    /**
     * Put the player in whichever copy of the hold matches the ship's state.
     *
     * Called on boarding and again the instant the hull is patched, because the
     * patch is not a change to the hole -- it is a change of room.
     */
    private void enterHold() {
        boolean sound = getStage() >= SHIP_REPAIRED && !reholed();
        getOwner().teleport(
            getStage() >= NED_AGREED ? HOLD_NED_X : HOLD_X,
            sound ? HOLD_SOUND_Y : HOLD_Y,
            false);
    }

    /** Holed a second time on Crandor's rocks and not yet re-patched. */
    private boolean reholed() {
        return getVar(VAR_REHOLED, 0) == 1;
    }

    /** The ladder in any of the four copies. All of them come out on the dock. */
    private void climbOut() {
        getOwner().teleport(DOCK_X, DOCK_Y, false);
    }

    /** Whether Ned has anything to steer by: the map, or the three pieces. */
    private boolean holdsTheMap() {
        Player p = getOwner();
        return p.getInventory().countId(MAP) > 0
            || (p.getInventory().countId(PIECE_MAZE) > 0
                && p.getInventory().countId(PIECE_MINE) > 0
                && p.getInventory().countId(PIECE_GOBLIN) > 0);
    }

    /**
     * The crossing itself, asked for by Ned and run from here.
     *
     * Everything Ned says on either side of this is in his handler now, where
     * the transcript puts it. What is left is the voyage: five recorded
     * messages and the landing. "The ship is sailing" really is recorded TWICE
     * in a row -- Jagex spaced them out over the trip, which is why a duplicate
     * reads as a heartbeat rather than as a bug. We have no timer, so they
     * arrive together.
     *
     * "Aha we've arrived" is printed rather than spoken because by the time it
     * is said the player is standing on Crandor and Ned is not: he has no spawn
     * there. See the deviations in the class comment.
     */
    private void sail() {
        Player p = getOwner();
        p.getActionSender().sendMessage("You feel the ship begin to move");
        p.getActionSender().sendMessage("You are out at sea");
        p.getActionSender().sendMessage("The ship is sailing");
        p.getActionSender().sendMessage("The ship is sailing");
        p.getActionSender().sendMessage("You feel a crunch");
        // The crunch is the rocks: the hull is holed again, every crossing.
        setVar(VAR_REHOLED, 1);
        p.teleport(CRANDOR_X, CRANDOR_Y, false);
        p.getActionSender().sendMessage("Ned: Aha we've arrived");
        if (getStage() == NED_AGREED) {
            setStage(SAILED);
        }
    }

    /**
     * The wreck on Crandor, which still floats well enough to get home.
     *
     * This used to print "Ned: I got towed back by a passing friendly whale"
     * as you boarded. That line is real, but it is his ANSWER to "How did you
     * get back?" asked at Port Sarim after the trip -- so it was being said in
     * the wrong place, by an npc who was not there, about a journey that had
     * not happened yet. It now lives where it was recorded, in his handler.
     */
    private void boardAtCrandor() {
        Player p = getOwner();
        p.getActionSender().sendMessage("You climb aboard the Lumbridge Lady");
        // Below decks she is the same ship as at Port Sarim, holed again from
        // the landing unless she has been re-patched. The ladder out comes up
        // on the Sarim dock -- the tow home Ned credits to his friendly whale.
        enterHold();
    }

    /**
     * The hole in the hold.
     *
     * Three planks and twelve nails, hammered in in one go -- per
     * classic.runescape.wiki's walkthrough. Klarense only ever says "a few
     * planks, hammered in with steel nails" in the recorded transcript; the
     * exact counts come from the wiki, not from her line. The hammer is a
     * tool and stays.
     *
     * Both holes in the world are patchable, because both are this ship: they
     * are the same room drawn twice, once with Ned in it. The guard here used
     * to accept only the western one on the belief that the other was a Crandor
     * wreck, which the world data does not support.
     */
    private void repairHull(GameObject hole, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != PLANK) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        boolean known = (hole.getX() == HULL_HOLE_X && hole.getY() == HULL_HOLE_Y)
            || (hole.getX() == HULL_HOLE_NED_X && hole.getY() == HULL_HOLE_NED_Y);
        if (!known) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (getStage() < SHIP_BOUGHT) {
            p.getActionSender().sendMessage("This is not your ship to mend");
            return;
        }
        boolean secondRepair = getStage() >= SAILED && reholed();
        if (getStage() > SHIP_BOUGHT && !secondRepair) {
            p.getActionSender().sendMessage("The hull is already patched");
            return;
        }
        if (p.getInventory().countId(HAMMER) < 1) {
            p.getActionSender().sendMessage("You need a hammer to do that");
            return;
        }
        if (p.getInventory().countId(PLANK) < PLANKS_NEEDED) {
            p.getActionSender().sendMessage("You need " + PLANKS_NEEDED
                + " planks to cover a hole that size");
            return;
        }
        if (p.getInventory().countId(NAILS) < NAILS_NEEDED) {
            p.getActionSender().sendMessage("You need " + NAILS_NEEDED
                + " steel nails to fix the planks in place");
            return;
        }
        p.getInventory().remove(PLANK, PLANKS_NEEDED);
        p.getInventory().remove(NAILS, NAILS_NEEDED);
        p.getActionSender().sendInventory();
        p.getActionSender().sendSound("mechanical");
        p.getActionSender().sendMessage("You hammer the planks over the hole");
        p.getActionSender().sendMessage("The Lumbridge Lady is seaworthy again");
        if (secondRepair) {
            setVar(VAR_REHOLED, 0);
        } else {
            setStage(SHIP_REPAIRED);
        }
        // The hole is scenery and cannot be taken away for one player, so the
        // patched ship is a different room. State first, then move: enterHold()
        // reads it to decide which copy.
        enterHold();
    }

    // --------------------------------------------------------------- doors --

    private void openDoor(GameObject door) {
        switch (door.getID()) {
            case DOOR_RED:     keyDoor(door, KEY_RED,     "red");     break;
            case DOOR_ORANGE:  keyDoor(door, KEY_ORANGE,  "orange");  break;
            case DOOR_YELLOW:  keyDoor(door, KEY_YELLOW,  "yellow");  break;
            case DOOR_BLUE:    keyDoor(door, KEY_BLUE,    "blue");    break;
            case DOOR_MAGENTA: keyDoor(door, KEY_MAGENTA, "magenta"); break;
            case DOOR_BLACK:   keyDoor(door, KEY_BLACK,   "black");   break;
            case DOOR_ORACLE:  oracleDoor(door); break;
            case STRANGE_WALL:
                getOwner().getActionSender().sendSound("secretdoor");
                getOwner().getActionSender().sendMessage("You push the wall");
                getOwner().getActionSender().sendMessage("It swings round to reveal a passage");
                walkThrough(door);
                break;
            case LAIR_DOOR:
                walkThrough(door);
                break;
        }
    }

    /**
     * A coloured door in Melzar's maze.
     *
     * The key is kept, not spent: there are three red doors and four orange ones
     * and the maze is walked more than once. Every placement of these six ids in
     * the world is inside the maze, so there is nothing here to guard against.
     */
    private void keyDoor(GameObject door, int key, String colour) {
        Player p = getOwner();
        if (p.getInventory().countId(key) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            p.getActionSender().sendMessage("You need a " + colour + " key to open it");
            return;
        }
        walkThrough(door);
    }

    /**
     * The Oracle's riddle door, in the dwarven mine at (259,3334).
     *
     * The four answers are handed over, not shown: "Opening it will remove the
     * four items from your inventory."
     *
     * The riddle is only ever asked of somebody standing outside. The door
     * faces 1, so it stands between (259,3334) and (258,3334), and the chest
     * room is the western side - a dead end whose only way out is this door.
     * Asking for the four items again from in there would seal the player in
     * for good, since opening the door is what destroyed them.
     */
    private void oracleDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != DOOR_ORACLE_X || door.getY() != DOOR_ORACLE_Y) {
            walkThrough(door);
            return;
        }
        if (p.getX() < DOOR_ORACLE_X) {
            walkThrough(door);
            return;
        }
        if (p.getInventory().countId(MIND_BOMB) < 1
                || p.getInventory().countId(SILK) < 1
                || p.getInventory().countId(LOBSTER_POT) < 1
                || p.getInventory().countId(UNFIRED_BOWL) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            p.getActionSender().sendMessage("Something about it feels like a riddle");
            return;
        }
        p.getInventory().remove(MIND_BOMB, 1);
        p.getInventory().remove(SILK, 1);
        p.getInventory().remove(LOBSTER_POT, 1);
        p.getInventory().remove(UNFIRED_BOWL, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("The door swings open");
        walkThrough(door);
    }

    /**
     * Open a door for a moment and step the player to the far side of it.
     *
     * A door facing 0 stands between (x,y) and (x,y-1); one facing 1 stands
     * between (x,y) and (x-1,y).
     */
    private void walkThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        if (door.getDirection() == 0) {
            p.teleport(door.getX(), p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
        } else {
            p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(), door.getY(), false);
        }
    }
}
