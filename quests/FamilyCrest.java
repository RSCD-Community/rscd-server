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
 * Family crest. Released 9 April 2002, written by Paul Gower and Ladykilljoy.
 *
 * Dimintheis of the Fitzharmon family lost his estate to the crown because his
 * three sons rode off to war with the family crest and then fell out over it.
 * Each son has a third and none of them will part with it for nothing.
 *
 *     Dimintheis      npc 309, (84,523), the hut by the east Varrock bank
 *     chef (Caleb)    npc 310, (433,483), a house by the Catherby fishing spots
 *     gem trader      npc 308, (81,663), Al Kharid
 *     man (Avan)      npc 307, (72,600), the Al Kharid mine
 *     Boot the Dwarf  npc 313, (311,3348), the west end of the Dwarven mine
 *     Wizard (Johnathon) npc 314, (84,1387), upstairs in the Jolly Boar Inn
 *     Chronozon       npc 315, (232,3248), the wilderness end of Edgeville dungeon
 *
 *     Crest fragment  item 695 Caleb's, 696 Avan's, 697 Johnathon's
 *     Family crest    item 694, the three of them put together
 *     gold            item 690 perfect ore, 691 perfect bar
 *     Ruby ring       item 692, Ruby necklace item 693, both from the perfect bar
 *     Steel gauntlets item 698, and the three enchantments 699, 700, 701
 *
 * The three rewards are mutually exclusive and Jagex made them so by handing
 * over one item that each son can turn into one of three others. Nothing here
 * has to enforce "only once": the enchantment consumes item 698 and the sons
 * only offer while the player is holding it.
 *
 * Deviations:
 *
 *  - The gauntlets surviving death is in Player.killedBy, not here. It is a
 *    property of the item -- a player who never touches this quest can still
 *    inherit a pair from a friend -- and the death path has no hook a quest
 *    could hang off anyway.
 *
 *  - Smelting the perfect ore and crafting the perfect jewellery are ordinary
 *    smithing and crafting, added to ItemSmeltingDef and ItemCraftingDef rather
 *    than reimplemented here. The rocks in the Zanash dungeon are likewise an
 *    ordinary ObjectMining entry. The wiki says Boot has to be spoken to before
 *    they can be mined; the page on Boot himself says mining works beforehand
 *    and it is Avan who will not take the jewellery until Boot has been found.
 *    This follows Boot's page, so the gate is in Avan's dialogue below.
 *
 *  - The four blast spells are counted per Chronozon, in {@link #blasts}, and
 *    the count is thrown away when a different Chronozon turns up -- which is
 *    what a respawn produces. Not persisted: an unfinished fight does not
 *    survive a restart, and neither did it in 2002.
 *
 *  - Chronozon's messages are from recorded footage of the original fight:
 *    "chronozon weakens" when a blast lands, "Chronozon regenerates" when he
 *    survives a killing blow, and nothing in particular when he dies.
 *
 *  - The gem trader has no NpcHandler, so claiming him for this quest means
 *    this quest has to open his shop. That is done in {@link #openShop}, which
 *    is ShopKeeper's ending grafted onto the end of a Conversation.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class FamilyCrest extends Quest {

    public final static int UID = Quests.FAMILY_CREST;

    private static final int DIMINTHEIS = 309, CHEF = 310, GEM_TRADER = 308;
    private static final int AVAN = 307, BOOT = 313, WIZARD = 314, CHRONOZON = 315;

    private static final int CALEB_PIECE = 695, AVAN_PIECE = 696, WIZARD_PIECE = 697;
    private static final int CREST = 694;
    private static final int GAUNTLETS = 698;
    private static final int GOLDSMITHING = 699, COOKING = 700, CHAOS = 701;
    private static final int RUBY_RING = 692, RUBY_NECKLACE = 693;

    /** Cooked shrimp, salmon, tuna, swordfish and bass, for Caleb's salad. */
    private static final int[] SALAD = { 350, 357, 367, 370, 555 };

    /** Cure poison potion, three doses down to one, and the vial left over. */
    private static final int[] CURE_POISON = { 566, 567, 568 };
    private static final int EMPTY_VIAL = 465;

    /** Wind, Water, Earth and Fire blast. The four elemental spells of death. */
    private static final int[] BLASTS = { 20, 23, 27, 32 };
    private static final int ALL_BLASTS = 15;

    private static final int STARTED = 1;
    private static final int MET_CALEB = 2;       /* asked for the fish */
    private static final int GOT_CALEB = 4;       /* has been given 695 */
    private static final int ASKED_TRADER = 8;    /* the gem trader named Avan */
    private static final int MET_AVAN = 16;       /* asked for the jewellery */
    private static final int ASKED_BOOT = 32;     /* Boot named the dungeon */
    private static final int GOT_AVAN = 64;       /* has been given 696 */
    private static final int MET_WIZARD = 128;    /* found Johnathon poisoned */
    private static final int CURED = 256;         /* and cured him */
    private static final int GOT_WIZARD = 512;    /* Chronozon dropped 697 */
    private static final int DONE = 1024;
    private static final int FINISHED = 2047;

    // ---------------------------------------------------------------- levers --

    /** The dungeon's way out. Climbing it resets this player's levers. */
    private static final int EXIT_LADDER = 5;
    private static final int EXIT_LADDER_X = 523, EXIT_LADDER_Y = 3439;

    /** The three levers of the Pillars of Zanash dungeon, east of Ardougne. */
    private static final int[] LEVERS = { 316, 317, 318 };
    private static final int[][] LEVER_AT = {
        { 511, 3415 },  /* by the south wall of the north room */
        { 508, 3443 },  /* inside the south room */
        { 511, 3410 }   /* on the north wall of the north room, by the ogre */
    };

    /**
     * The four doors the levers throw: id, x, y, direction.
     *
     * Direction 0 blocks north to south between (x,y) and (x,y-1); direction 1
     * blocks east to west between (x,y) and (x-1,y).
     */
    private static final int[][] DOORS = {
        { 88, 509, 3441, 0 },  /* the eastern of the south room's two doors */
        { 90, 512, 3441, 0 },  /* the western one -- the way out at the end */
        { 91, 508, 3427, 1 },  /* the cage, with the hellhound and the gold */
        { 92, 510, 3415, 0 }   /* the north room's south door */
    };

    /**
     * Which doors each lever throws, by index into DOORS and LEVERS.
     *
     * Jagex published the route and not the wiring, the same as Ernest, so this
     * is reconstructed. Walk the recorded route through from a shut dungeon --
     * north lever, south room lever, north lever, south wall lever, north lever,
     * south room lever -- and every step lands:
     *
     *   318            -> 88 opens; the south room can be entered.
     *   317            -> 92 opens; leave the way you came in.
     *   318            -> 88 shuts.
     *   316            -> 90 and 91 open.
     *   318            -> 88 opens again.
     *   317            -> 92 shuts; leave by 90, the most western door,
     *                     and the cage door 91 is standing open.
     *
     * It is the only assignment that satisfies the route: 317 cannot be on 90,
     * or its last pull would shut the door the player is told to leave by, and
     * nothing but 316 is left to open the cage.
     */
    private static final int[][] LEVER_DOORS = {
        { 1, 2 },  /* 316 -> 90 and 91 */
        { 3 },     /* 317 -> 92 */
        { 0 }      /* 318 -> 88 */
    };

    /**
     * Which doors this player's levers have unlocked -- a bitmask in
     * persistent var slot 0, bit i for DOORS[i]. Per-player, the same model
     * as Ernest's maze: the doors stay registered (and visibly shut) for
     * everyone, always; the mask only decides whether Open lets this player
     * swing through. Persisted so a logout mid-dungeon cannot trap anyone.
     */
    private static final int VAR_DOORS = 0;

    /** Which levers are currently down, bit i for LEVERS[i]. Inspect reads it. */
    private static final int VAR_LEVERS = 1;

    private boolean doorOpen(int index) {
        return (getVar(VAR_DOORS, 0) & (1 << index)) != 0;
    }

    // ------------------------------------------------------------ per-quest --

    /** The Chronozon the blasts below were counted against. */
    private Npc blastedNpc = null;

    /** Which of BLASTS have drawn blood from him. Not persisted. */
    private int blasts = 0;

    public FamilyCrest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Family crest");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The Fitzharmon family crest has gone missing, and the family honour has been lost. Can you find the crest and return it to Dimintheis in Varrock? There are 3 different rewards available, but you can only choose one, so choose carefully!");
        setStartPoint("East Varrock");
        setSpeakTo("Dimintheis");
        setMissionLength("Long");
        requireLevel(14, 40);   /* mining -- the perfect gold ore */
        requireLevel(13, 40);   /* smithing -- smelting the perfect bar */
        requireLevel(12, 40);   /* crafting -- the ruby ring and necklace */
        requireLevel(6, 59);    /* magic -- the four elemental blast spells */
        require("Defeat a lvl 121 Demon");
        rewardOther("Steel gauntlets, which you keep even on death; one of Dimintheis' three sons will grant them one power of your choice: goldsmithing, cooking or chaos");
        /* RuneHQ and derived/quest_rewards.json both list this too. Declared
         * for the manual only: nothing in this server gates the hellhound
         * dungeon east of Ardougne on quest completion, so it was always
         * open and stays that way. */
        rewardOther("Access to the hellhound dungeon east of Ardougne");

        associateNpc(DIMINTHEIS);
        associateNpc(CHEF);
        associateNpc(GEM_TRADER);
        associateNpc(AVAN);
        associateNpc(BOOT);
        associateNpc(WIZARD);
        associateNpc(CHRONOZON);
        for (int lever : LEVERS) {
            associateObject(lever);
        }
        /* The doors are ordinary Open/Examine doors, and without a claim the
         * generic handler opens them for anyone who clicks -- the levers were
         * decoration. Claimed by placement so the world's other doors on
         * these ids stay ordinary. */
        for (int[] door : DOORS) {
            associateDoor(door[0], door[1], door[2]);
        }
        associateObject(EXIT_LADDER, EXIT_LADDER_X, EXIT_LADDER_Y);
        // Only so that the three fragments find each other.
        associateItem(CALEB_PIECE);
        associateItem(AVAN_PIECE);
        associateItem(WIZARD_PIECE);
    }

    public void completeQuest() {
        /* Verbatim from recorded footage of the original completion. */
        getOwner().getActionSender().sendMessage("Well done you have completed the family crest quest");
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        /* An unstarted quest's stage is -1, and -1 | anything is still -1 --
         * the first set() must overwrite, not OR. */
        setStage(questStarted() ? getStage() | bit : bit);
    }

    /**
     * Published progress, asked for by the silk trader.
     *
     * Jagex put the Avan clue on more than one Al Kharid merchant, but this
     * quest only ever associated the gem trader, so the silk trader's copy of
     * it lives in SilkTrade.java -- an npc handler beats quest dispatch, and
     * claiming npc 71 here would take the silk stall away from everyone not on
     * the quest. He asks these two questions to know whether the clue is in
     * its window: Caleb's piece taken, Avan not yet found.
     */
    public boolean reached(String key) {
        if ("got-caleb".equals(key)) {
            return has(GOT_CALEB);
        }
        if ("met-avan".equals(key)) {
            return has(MET_AVAN);
        }
        return false;
    }

    /** Whether the player still has the piece a son gave them, loose or joined. */
    private boolean holds(int fragment) {
        Player p = getOwner();
        return p.getInventory().countId(fragment) > 0 || p.getInventory().countId(CREST) > 0;
    }

    private boolean holdsGauntlets() {
        return getOwner().getInventory().countId(GAUNTLETS) > 0;
    }

    // ------------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
                int door = doorIndex(object);
                if (door >= 0 && trigger == QuestTrigger.DOOR_ACT1) {
                    zanashDoor(object, door);
                }
                return;
            }
            if (object.getID() == EXIT_LADDER && object.getX() == EXIT_LADDER_X
                    && object.getY() == EXIT_LADDER_Y) {
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    climbOut(object);
                }
                return;
            }
            int lever = leverIndex(object);
            if (lever < 0) {
                return;
            }
            if (trigger == QuestTrigger.OBJECT_ACT1) {
                pullLever(lever);
            } else if (trigger == QuestTrigger.OBJECT_ACT2) {
                inspectLever(lever);
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                joinCrest();
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == CHRONOZON) {
                chronozonDied(npc);
            }
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_NPC) {
            if (npc.getID() == WIZARD) {
                cureJohnathon(npc, used);
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case DIMINTHEIS: dimintheis(npc); break;
            case CHEF: caleb(npc); break;
            case GEM_TRADER: gemTrader(npc); break;
            case AVAN: avan(npc); break;
            case BOOT: boot(npc); break;
            case WIZARD: johnathon(npc); break;
            default: break;
        }
    }

    // ----------------------------------------------------------- Dimintheis --

    private void dimintheis(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Thankyou for saving our family honour")
                .npc("We will never forget you")
                .start();
            return;
        }
        if (questStarted()) {
            if (p.getInventory().countId(CREST) > 0) {
                handOver(npc, true);
                return;
            }
            if (p.getInventory().countId(CALEB_PIECE) > 0
                    && p.getInventory().countId(AVAN_PIECE) > 0
                    && p.getInventory().countId(WIZARD_PIECE) > 0) {
                handOver(npc, false);
                return;
            }
            new Conversation(p, npc)
                .npc("How are you doing finding the crest")
                .player("I don't have it yet")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hello, my name is Dimintheis")
            .npc("of the noble family of Fitzharmon")
            .options(new Choice("Why would a nobleman live in a little hut like this?",
                                "You're rich?, can I have some money?",
                                "Hi, I am bold adventurer") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Lousy beggar")
                         .npc("There's to many of your sort about these days")
                         .npc("If I gave money to each of you who asked")
                         .npc("I'd be living on the streets myself");
                        return;
                    }
                    if (option == 0) {
                        c.npc("The king has taken my estate from me")
                         .npc("Until I can show him my family crest");
                    } else {
                        c.npc("An adventurer hmm?")
                         .npc("I may have an adventure for you")
                         .npc("I desperately need my family crest returning to me");
                    }
                    c.options(new Choice(option == 0 ? "Why would he do that?"
                                                     : "Why are you so desperate for it?",
                                         "So where is this crest?") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                tradition(c);
                                c.player("so where is this crest?");
                            }
                            sons(c);
                        }
                    });
                }
            /*
             * The literal "player: " on both lines is Jagex's own -- someone
             * pasted the speaker label into the dialogue text and it shipped
             * that way. Transcript:Dimintheis marks each line {{sic}}. It
             * reads like a bug because it is one, but it is theirs.
             */
            }.says(1, "player: You're rich then?", "player: Can I have some money?"))
            .start();
    }

    private void tradition(Conversation c) {
        c.npc("We have this tradition in the Varrocian arostocracy")
         .npc("Each noble family has an ancient crest")
         .npc("This represents the honour and lineage of the family")
         .npc("If you are to lose this crest, the family's estate is given to the crown")
         .npc("until the crest is returned")
         .npc("In times past when there was much infighting between the various families")
         .npc("Capturing a family's crest meant you capture their land");
    }

    private void sons(Conversation c) {
        c.npc("Well my 3 sons took it with them many years ago")
         .npc("When they rode out to fight in the war")
         .npc("Against the undead necromancer and his army")
         .npc("I didn't hear from them for many years and mourned them dead")
         .npc("However recently I heard word that my son Caleb is alive")
         .npc("trying to earn his fortune")
         .npc("As a great chef, far away in the lands beyond white wold mountain")
         .options(new Choice("Ok I will help you",
                             "I'm not interested in that adventure right now") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     return;
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         set(STARTED);
                     }
                 })
                  .npc("I thank you greatly")
                  .npc("If you find Caleb send him my love");
             }
         });
    }

    /** The reward scene. The crest goes in whole or in three pieces. */
    private void handOver(Npc npc, final boolean whole) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("I have retrieved your crest")
            .then(new Effect() {
                public void run(Conversation c) {
                    Player p = c.getPlayer();
                    if (whole) {
                        p.getInventory().remove(CREST, 1);
                    } else {
                        p.getInventory().remove(CALEB_PIECE, 1);
                        p.getInventory().remove(AVAN_PIECE, 1);
                        p.getInventory().remove(WIZARD_PIECE, 1);
                    }
                    p.getActionSender().sendInventory();
                }
            })
            .message(whole ? "You give the crest to Dimintheis"
                           : "You give the parts of the crest to Dimintheis")
            .npc("Thankyou for your kindness")
            .npc("I cannot express my gratitude enough")
            .npc("You truly are a great hero")
            /* The quest completes here, mid-conversation -- the recorded
             * footage shows the quest point arriving between "great hero"
             * and "How can i reward you", before the gauntlets. The
             * lowercase i's on the next line are Jagex's own. */
            .then(new Effect() {
                public void run(Conversation c) {
                    set(DONE);
                }
            })
            .npc("How can i reward you i wonder?")
            .npc("I suppose these gauntlets would make a good reward")
            .npc("If you die you will always retain these gauntlets")
            .give(new InvItem(GAUNTLETS, 1))
            .message("Dimintheis gives you a pair of gauntlets")
            .npc("These gautlets can be granted extra powers")
            .npc("Take them to one of my boys, they can each do something to them")
            .npc("Though they can only receive one of the three powers")
            .start();
    }

    // ------------------------------------------------------- Caleb, the chef --

    private void caleb(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc)
                .npc("I hear you have brought the completed crest to my father")
                .npc("Impressive work I must say");
            if (holdsGauntlets()) {
                c.player("My Father says you can improve these gauntlets for me")
                 .npc("Yes that is true")
                 .npc("I can change them to gauntlets of cooking")
                 .npc("Wearing them means you will burn your lobsters, swordfish and shark less")
                 .options(new Choice("Yes please do that for me",
                                     "I'll see what your brothers have to offer first") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Ok suit yourself");
                             return;
                         }
                         c.message("Caleb holds the gauntlets and closes his eyes")
                          .message("Caleb concentrates")
                          .take(GAUNTLETS, 1)
                          .give(new InvItem(COOKING, 1))
                          .message("Caleb hands the gauntlets to you");
                     }
                 });
            }
            c.start();
            return;
        }
        if (has(MET_AVAN)) {
            if (holds(CALEB_PIECE)) {
                new Conversation(p, npc)
                    .npc("How are you doing getting the rest of the crest?")
                    .player("I am still working on it")
                    .npc("Well good luck in your quest")
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("How are you doing getting the rest of the crest?")
                .options(new Choice("I am still working on it",
                                    "I have lost the piece you gave me") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Well good luck in your quest");
                            return;
                        }
                        c.npc("Ah well here is another one")
                         .give(new InvItem(CALEB_PIECE, 1));
                    }
                })
                .start();
            return;
        }
        if (has(GOT_CALEB)) {
            new Conversation(p, npc)
                .player("Where did you say I could find Avan?")
                .npc("He said he was a living in a town in the desert")
                .npc("Ask around the desert any you may find him")
                .start();
            return;
        }
        if (has(MET_CALEB)) {
            if (hasSalad()) {
                new Conversation(p, npc)
                    .npc("How is the fish collecting going?")
                    .player("Yes I have all of that now")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            Player p = c.getPlayer();
                            for (int fish : SALAD) {
                                p.getInventory().remove(fish, 1);
                            }
                            p.getInventory().add(new InvItem(CALEB_PIECE, 1));
                            p.getActionSender().sendInventory();
                            set(GOT_CALEB);
                        }
                    })
                    .message("You give all of the fish to Caleb")
                    .message("Caleb gives you his piece of the crest")
                    .options(new Choice("Err what happened to the rest of it?",
                                        "Thankyou very much") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                return;
                            }
                            splitTheCrest(c);
                            c.player("So do you know where I could find any of your brothers?")
                             .npc("Well we haven't really kept in touch")
                             .npc("What with all falling out over the crest")
                             .npc("I did hear from my brother Avan about a year ago though")
                             .npc("He said he was a living in a town in the desert")
                             .npc("Ask around the desert and you may find him")
                             .npc("My brother has very expensive tastes")
                             .npc("He may not give up the crest easily");
                        }
                    })
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("How is the fish collecting going?")
                .player("I haven't got all the fish yet")
                .npc("Remember I want cooked swordfish, bass, tuna, salmon and shrimp")
                .start();
            return;
        }
        String[] options = questStarted()
            ? new String[] { "Are you Caleb Fitzharmon?",
                             "Nothing, I will be on my way",
                             "I see you are a chef, will you cook me anything?" }
            : new String[] { "Nothing, I will be on my way",
                             "I see you are a chef, will you cook me anything?" };
        final int offset = questStarted() ? 1 : 0;
        new Conversation(p, npc)
            .npc("Who are you? What are you after?")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 0 && offset == 1) {
                        askCaleb(c);
                        return;
                    }
                    if (option - offset == 1) {
                        c.npc("I would, but I am very busy")
                         .npc("Trying to prepare my special fish salad")
                         .npc("Which I hope will significantly increase my renown as a master chef");
                    }
                }
            }.says(offset + 1, "I see you are a chef", "Will you cook me anything?"))
            .start();
    }

    private void askCaleb(Conversation c) {
        c.npc("I am he, who might you be?")
         .player("I have been sent by your father")
         .player("He wants me to retrieve the Fitzharmon family crest")
         .npc("Ah, yes hmm well I do have a big of it yes")
         .options(new Choice("Err what happened to the rest of crest?",
                             "So can I have your bit?") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     splitTheCrest(c);
                     c.player("So can I have your bit?");
                 }
                 c.npc("Well I am the oldest son, by rights it is mine")
                  .player("It's not a lot of use to you without the rest of it though")
                  .npc("Well true")
                  .npc("So I'll tell you what I'll do")
                  .npc("I am struggling to complete my seafood salad")
                  .npc("I don't seem to be able to get hold of the ingredients I need")
                  .npc("Help me and I'll help you")
                  .player("What are you missing exactly?")
                  .npc("I need cooked swordfish,bass,tuna,salmon and shrimp")
                  .options(new Choice("Ok I will get those",
                                      "Why don't you just give me the crest?") {
                      public void picked(int option, Conversation c) {
                          if (option != 0) {
                              c.npc("No I don't want to just give it away");
                          }
                          c.then(new Effect() {
                              public void run(Conversation c) {
                                  set(MET_CALEB);
                              }
                          });
                      }
                  });
             }
         });
    }

    /** The story all three brothers tell about how the crest came apart. */
    private void splitTheCrest(Conversation c) {
        c.npc("Well we had a bit of a fight over it")
         .npc("We all wanted to be the heir of our fathers lands")
         .npc("we each ended up with a piece of the crest")
         .npc("none of us wanted to give their piece of the crest up to any of the others")
         .npc("And none of us wanted to face our father")
         .npc("coming home without a complete crest");
    }

    private boolean hasSalad() {
        Player p = getOwner();
        for (int fish : SALAD) {
            if (p.getInventory().countId(fish) < 1) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------- gem trader --

    private void gemTrader(final Npc npc) {
        Player p = getOwner();
        boolean clue = has(GOT_CALEB) && !has(MET_AVAN);
        String[] options = clue
            ? new String[] { "Yes please", "No thankyou",
                             "I'm in search of a man named adam fitzharmon" }
            : new String[] { "Yes please", "No thankyou" };
        new Conversation(p, npc)
            .npc("good day to you madam/sir")
            .npc("Would you be interested in buying some gems?")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        openShop(c, npc);
                        return;
                    }
                    if (option != 2) {
                        return;
                    }
                    c.npc("Firzharmon eh?")
                     .npc("Thats the name of a Varrocian noble family if I'm not mistaken")
                     .npc("I have seen a man of that persuasion about the place as of late")
                     .npc("Wearing a poncey yellow cape")
                     .npc("Come to my store, said he was after jewelry made from the perfect gold")
                     .npc("He's round about the desert still, looking for the perfect gold")
                     .npc("He'll be somewhere where he might get some gold I'd wager")
                     .npc("He might even be desperate enough to brave the scorpions")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(ASKED_TRADER);
                         }
                     });
                }
            })
            .start();
    }

    /**
     * Hand the player over to the shop screen.
     *
     * The conversation has to be closed first: it holds the player busy and the
     * npc blocked for as long as it is running, and the shop screen is not a
     * conversation step. This is the tail of ShopKeeper.handleNpc().
     */
    private void openShop(Conversation c, final Npc npc) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                c.stop();
                Shop shop = world.getShop(npc);
                if (shop == null) {
                    return;
                }
                p.setAccessingShop(shop);
                p.getActionSender().showShop(shop);
            }
        });
    }

    // ---------------------------------------------------------- Avan, "man" --

    private void avan(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc)
                .npc("I have heard word from my father")
                .npc("Thankyou for helping to restore our family honour");
            if (holdsGauntlets()) {
                c.player("Your father said that you could improve these Gauntlets in some way for me")
                 .npc("Indeed I can")
                 .npc("In my quest to find the perfect gold I learned a lot")
                 .npc("I can make it so when you're wearing these")
                 .npc("You gain more experience when smithing gold")
                 .options(new Choice("That sounds good, improve them for me",
                                     "I think I'll check my other options with your brothers") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Ok if you insist on getting help from the likes of them");
                             return;
                         }
                         c.message("Avan takes out a little hammer")
                          .message("He starts pounding on the gauntlets")
                          .take(GAUNTLETS, 1)
                          .give(new InvItem(GOLDSMITHING, 1))
                          .message("Avan hands the gauntlets to you");
                     }
                 }.says(0, "That sounds good, enchant them for me"));
            }
            c.start();
            return;
        }
        if (has(CURED)) {
            if (holds(AVAN_PIECE)) {
                new Conversation(p, npc)
                    .npc("How are you doing getting the rest of the crest?")
                    .player("I am still working on it")
                    .npc("Well good luck in your quest")
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("How are you doing getting the rest of the crest?")
                .options(new Choice("I am still working on it",
                                    "I have lost the piece you gave me") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Well good luck in your quest");
                            return;
                        }
                        c.npc("Ah well here is another one")
                         .give(new InvItem(AVAN_PIECE, 1));
                    }
                })
                .start();
            return;
        }
        if (has(GOT_AVAN)) {
            new Conversation(p, npc)
                .player("Where did you say I could find Johnathon again?")
                .npc("I heard my brother Johnathon is now a young mage")
                .npc("He is hunting some demon in the wilderness")
                .npc("But he's not doing a very good job of it")
                .npc("He spends most his time recovering in an inn")
                .npc("on the edge of the wilderness")
                .start();
            return;
        }
        if (has(MET_AVAN)) {
            if (has(ASKED_BOOT)
                    && p.getInventory().countId(RUBY_RING) > 0
                    && p.getInventory().countId(RUBY_NECKLACE) > 0) {
                new Conversation(p, npc)
                    .npc("So how are you doing getting the jewellry?")
                    .player("I have it")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            Player p = c.getPlayer();
                            p.getInventory().remove(RUBY_RING, 1);
                            p.getInventory().remove(RUBY_NECKLACE, 1);
                            p.getInventory().add(new InvItem(AVAN_PIECE, 1));
                            p.getActionSender().sendInventory();
                            set(GOT_AVAN);
                        }
                    })
                    .message("You give the pieces")
                    .npc("These are brilliant")
                    .npc("These are a fine piece of work")
                    .npc("Such marvelous gold to")
                    .npc("I suppose you will be after the last piece of crest now")
                    .npc("I heard my brother Johnathon is now a young mage")
                    .npc("He is hunting some demon in the wilderness")
                    .npc("But he's not doing a very good job of it")
                    .npc("He spends most his time recovering in an inn")
                    .npc("on the edge of the wilderness")
                    .start();
                return;
            }
            if (has(ASKED_BOOT)) {
                new Conversation(p, npc)
                    .npc("So how are you doing getting the jewellry?")
                    .player("I have spoken to boot about the perfect gold")
                    .player("I haven't bought you your jewellry yet though")
                    .npc("Remember I want a gold ring with a red stone in")
                    .npc("And a necklace to match")
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("So how are you getting the jewellry?")
                .player("I'm still after that perfect gold")
                .npc("Well I have been looking for such gold for a while")
                .npc("My latest lead was a dwarf named Boot")
                .npc("Though he has gone back to his home in the mountain now")
                .start();
            return;
        }
        if (!has(ASKED_TRADER)) {
            new Conversation(p, npc)
                .npc("Can't you see I'm busy?")
                .start();
            return;
        }
        new Conversation(p, npc)
            .options(new Choice("Why are you hanging around in a scorpion pit?",
                                "I'm looking for a man named Avan") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("It's a good place to find gold");
                        return;
                    }
                    c.npc("I'm called Avan yes")
                     .player("You have part of a crest")
                     .player("I have been sent to fetch it")
                     .npc("Is one of my good for nothing brothers after it again?")
                     .player("no your father would like it back")
                     .npc("Oh Dad wants it this time")
                     .npc("Well I'll tell you what I'll do")
                     .npc("I'm trying to obtain the perfect jewellty")
                     .npc("There is a lady I am trying to impress")
                     .npc("What I want is a gold ring with a red stone in")
                     .npc("And a necklace to match")
                     .npc("Not just any gold mind you")
                     .npc("The gold in these rocks doesn't seem to be of the best quality")
                     .npc("I want as good a quality as you can get")
                     .player("Any ideas where I can find that?")
                     .npc("Well I have been looking for such gold for a while")
                     .npc("My latest lead was a dwarf named Boot")
                     .npc("Though he has gone back to his home in the mountain now")
                     .player("Ok I will try to get what you are after")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(MET_AVAN);
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------- the dwarf --

    private void boot(Npc npc) {
        Player p = getOwner();
        boolean asking = has(MET_AVAN) && !has(GOT_AVAN);
        String[] options = asking
            ? new String[] { "Hello I'm in search of very high quality gold",
                             "Hello short person", "Why are you called boot?" }
            : new String[] { "Hello short person", "Why are you called boot?" };
        final int offset = asking ? 1 : 0;
        new Conversation(p, npc)
            .npc("Hello tall person")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 0 && offset == 1) {
                        c.npc("Hmm well the best gold I know of")
                         .npc("is east of the great city of Ardougne")
                         .npc("In some certain rocks underground there")
                         .npc("Its not the easiest of rocks to get to though I've heard")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(ASKED_BOOT);
                             }
                         });
                        return;
                    }
                    if (option - offset == 1) {
                        c.npc("Because when I was a very young dwarf")
                         .npc("I used to sleep in a large boot");
                    }
                }
            })
            .start();
    }

    // ------------------------------------------------- Johnathon, the wizard --

    private void johnathon(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc).npc("Hello again");
            if (holdsGauntlets()) {
                c.player("Your father tells me, you can improve these gauntlets a bit")
                 .npc("He would be right")
                 .npc("Though I didn't get good enough at the death spells to defeat chronozon")
                 .npc("I am pretty good at the chaos spells")
                 .npc("I can enchant your gauntlets so that your bolt spells are more effective")
                 .options(new Choice("That sounds good to me",
                                     "I shall see what options your brothers can offer me first") {
                     public void picked(int option, Conversation c) {
                         if (option != 0) {
                             c.npc("Boring crafting and cooking enhacements knowing them");
                             return;
                         }
                         c.message("Johnathon waves his staff")
                          .take(GAUNTLETS, 1)
                          .give(new InvItem(CHAOS, 1))
                          .message("The gauntlets sparkle and shimmer");
                     }
                 });
            } else {
                c.npc("My family now considers you a hero");
            }
            c.start();
            return;
        }
        if (has(GOT_WIZARD) && holds(WIZARD_PIECE)) {
            new Conversation(p, npc)
                .player("I have your part of the crest now")
                .npc("Well done take it to my father")
                .start();
            return;
        }
        if (has(CURED)) {
            new Conversation(p, npc)
                .npc("I'm trying to kill the demon chronozon you mentioned")
                .options(new Choice("So is this Chronozon hard to defeat?",
                                    "Where can I find Chronozon?",
                                    "Wish me luck") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            howToKill(c);
                        } else if (option == 1) {
                            c.npc("He is in the wilderness, somewhere below the obelisk of air");
                        } else {
                            c.npc("Good luck");
                        }
                    }
                })
                .start();
            return;
        }
        if (!has(GOT_AVAN)) {
            new Conversation(p, npc)
                .npc("I am so very tired, leave me to rest")
                .start();
            return;
        }
        if (has(MET_WIZARD)) {
            new Conversation(p, npc)
                .npc("Arrgh what has that spider done to me")
                .npc("I feel so ill, I can hardly think")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("Greetings, are you Johnathon Fitzharmon?")
            .npc("That is I")
            .player("I seek your fragment of the Fitzharmon family crest")
            .npc("The poison it is too much")
            .npc("arrgh my head is all of a spin")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(MET_WIZARD);
                }
            })
            .start();
    }

    private void howToKill(Conversation c) {
        c.npc("Well you will need to be a good mage")
         .npc("And I don't seem to be able to manage it")
         .npc("He will need to be hit by the 4 elemental spells of death")
         .npc("Before he can be defeated");
    }

    private void cureJohnathon(Npc npc, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        int dose = -1;
        for (int i = 0; i < CURE_POISON.length; i++) {
            if (CURE_POISON[i] == used.getID()) {
                dose = i;
            }
        }
        if (dose < 0) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!has(GOT_AVAN) || has(CURED)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        final int left = dose + 1 < CURE_POISON.length ? CURE_POISON[dose + 1] : EMPTY_VIAL;
        final int drunk = used.getID();
        new Conversation(p, npc)
            .then(new Effect() {
                public void run(Conversation c) {
                    Player p = c.getPlayer();
                    p.getInventory().remove(drunk, 1);
                    p.getInventory().add(new InvItem(left, 1));
                    p.getActionSender().sendInventory();
                    // MET_WIZARD as well: the cure can be poured down him before
                    // a word has been exchanged, and the quest must still be able
                    // to reach its final stage.
                    set(MET_WIZARD | CURED);
                }
            })
            .message("You feed your potion to Johnathon")
            .npc("Wow I'm feeling a lot better now")
            .npc("Thankyou, what can I do for you?")
            .player("I'm after your part of the fitzharmon family crest")
            .npc("Ooh I don't think I have that anymore")
            .npc("I have been trying to slay chronozon the blood demon")
            .npc("and I think I dropped a lot of my things near him when he drove me away")
            .npc("He will have it now")
            .options(new Choice("So is this Chronozon hard to defeat?",
                                "Where can I find Chronozon?",
                                "So how did you end up getting poisoned",
                                "I will be on my way now") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        howToKill(c);
                    } else if (option == 1) {
                        c.npc("He is in the wilderness, somewhere below the obelisk of air");
                    } else if (option == 2) {
                        c.npc("There are spiders towards the entrance to Chronozon's cave")
                         .npc("I must have taken a nip from one of them");
                    }
                }
            })
            .start();
    }

    // ------------------------------------------------------------ Chronozon --

    /**
     * Count a blast that drew blood.
     *
     * A cast that rolled a nought does not count -- Johnathon says Chronozon
     * has to be "hit by" all four, and the wiki records the rule as successfully
     * damaging him once with each. A different Npc object means the old demon
     * died or respawned and this is a fresh fight.
     */
    public void spellCast(Npc npc, int spellId, int damage) {
        if (npc.getID() != CHRONOZON || damage < 1) {
            return;
        }
        if (npc != this.blastedNpc) {
            this.blastedNpc = npc;
            this.blasts = 0;
        }
        for (int i = 0; i < BLASTS.length; i++) {
            if (BLASTS[i] == spellId && (this.blasts & 1 << i) == 0) {
                this.blasts |= 1 << i;
                getOwner().getActionSender().sendMessage("chronozon weakens");
            }
        }
    }

    /**
     * Keep Chronozon alive until all four blasts have landed.
     *
     * Asked at the one chokepoint in Npc.killedBy, so it covers melee, ranged
     * and magic together: a player who blasts him three times and then finishes
     * him with a sword is stopped just the same. "Chronozon regenerates" is
     * Jagex's own line, from recorded footage of the fight.
     */
    public boolean refusesKill(Npc npc) {
        if (npc.getID() != CHRONOZON) {
            return false;
        }
        if (npc == this.blastedNpc && this.blasts == ALL_BLASTS) {
            return false;
        }
        getOwner().getActionSender().sendMessage("Chronozon regenerates");
        return true;
    }

    /** He is carrying Johnathon's fragment and drops it where he falls. */
    private void chronozonDied(Npc npc) {
        Player p = getOwner();
        this.blastedNpc = null;
        this.blasts = 0;
        if (!has(CURED) || holds(WIZARD_PIECE)) {
            return;
        }
        set(GOT_WIZARD);
        world.registerItem(new Item(WIZARD_PIECE, npc.getX(), npc.getY(), 1, p));
    }

    // -------------------------------------------------------------- the crest --

    /** Three fragments in one hand make the crest. Two do not. */
    private void joinCrest() {
        Player p = getOwner();
        if (p.getInventory().countId(CALEB_PIECE) < 1
                || p.getInventory().countId(AVAN_PIECE) < 1
                || p.getInventory().countId(WIZARD_PIECE) < 1) {
            p.getActionSender().sendMessage("You need all three pieces of the crest");
            return;
        }
        p.getInventory().remove(CALEB_PIECE, 1);
        p.getInventory().remove(AVAN_PIECE, 1);
        p.getInventory().remove(WIZARD_PIECE, 1);
        p.getInventory().add(new InvItem(CREST, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You fit the three pieces of the crest together");
    }

    // ----------------------------------------- the Pillars of Zanash levers --

    /** Which of the three levers this is, or -1 for anything else. */
    private static int leverIndex(GameObject object) {
        for (int i = 0; i < LEVERS.length; i++) {
            if (object.getID() == LEVERS[i]
                    && object.getX() == LEVER_AT[i][0]
                    && object.getY() == LEVER_AT[i][1]) {
                return i;
            }
        }
        return -1;
    }

    /* The pull and inspect messages are the witness's, which sourced the
     * refusals to the wiki: an unstarted player gets "nothing interesting
     * happens" from both commands. */

    private void pullLever(int lever) {
        Player p = getOwner();
        if (!questStarted()) {
            p.getActionSender().sendMessage("nothing interesting happens");
            return;
        }
        int levers = getVar(VAR_LEVERS, 0) ^ (1 << lever);
        setVar(VAR_LEVERS, levers);
        int mask = getVar(VAR_DOORS, 0);
        for (int door : LEVER_DOORS[lever]) {
            mask ^= 1 << door;
        }
        setVar(VAR_DOORS, mask);
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("You pull the lever "
            + ((levers & 1 << lever) != 0 ? "down" : "up"));
        p.getActionSender().sendMessage("you hear a clunk");
    }

    private void inspectLever(int lever) {
        Player p = getOwner();
        if (!questStarted()) {
            p.getActionSender().sendMessage("nothing interesting happens");
            return;
        }
        p.getActionSender().sendMessage("The lever is "
            + ((getVar(VAR_LEVERS, 0) & 1 << lever) != 0 ? "down" : "up"));
    }

    /**
     * The way out. Leaving the dungeon resets this player's levers and doors
     * to the shut state the route is written from -- the puzzle starts over
     * on every visit. The climb itself is the generic ladder teleport.
     */
    private void climbOut(GameObject ladder) {
        Player p = getOwner();
        setVar(VAR_DOORS, 0);
        setVar(VAR_LEVERS, 0);
        p.teleport(p.getX(), Formulae.getNewY(p.getY(), true), false);
    }

    /** Which of the four lever-thrown doors this is, by id AND tile, or -1. */
    private static int doorIndex(GameObject door) {
        for (int i = 0; i < DOORS.length; ++i) {
            int[] d = DOORS[i];
            if (door.getID() == d[0] && door.getX() == d[1] && door.getY() == d[2]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open clicked on a lever-thrown door. Unlocked for this player: the door
     * swings for a moment and steps them across, the same swap-and-restore
     * every other door uses. Locked: it will not move by hand.
     */
    private void zanashDoor(GameObject door, int index) {
        Player p = getOwner();
        if (!doorOpen(index)) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        // Swing, not vanish: show the open doorframe (the same 2<->1 swap
        // ordinary doors use) until the closed door respawns.
        world.registerGameObject(new GameObject(door.getLocation(), 1, door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        int[] d = DOORS[index];
        if (d[3] == 1) {
            // Blocks east-west between (x,y) and (x-1,y).
            p.teleport(p.getX() >= d[1] ? d[1] - 1 : d[1], d[2], false);
        } else {
            // Blocks north-south between (x,y) and (x,y-1).
            p.teleport(d[1], p.getY() >= d[2] ? d[2] - 1 : d[2], false);
        }
    }
}
