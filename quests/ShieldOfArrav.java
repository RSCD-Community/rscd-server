import java.util.ArrayList;
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
 * Shield of Arrav, 4 January 2001 -- one of the six quests RuneScape launched
 * with, and the only one Jagex built for two players.
 *
 * The shield was stolen from the Varrock museum by the phoenix gang. Five years
 * ago the gang tore itself in half over it; the breakaway faction took one half
 * of the broken shield with them and became the black arm gang. Neither gang
 * will admit an outsider without a favour, and neither will admit anyone who has
 * already joined the other -- so one half of the shield is reachable by any one
 * player, and never both.
 *
 * That is deliberate and it is kept. Two players each join a different gang,
 * each fetch their half, trade so that one holds both, take them to the curator,
 * and split the two certificates he writes out. The king pays 600 coins on each
 * certificate, which is why he calls it "half the reward". One quest point.
 *
 * Nothing here is soloable and nothing here has been made soloable. If a
 * shortcut is ever wanted it belongs beside this, not inside it.
 *
 * Reldo belongs to two quests: he starts this one and, once The knight's sword
 * is under way, he is also the only source of the Imcando dwarves' whereabouts.
 * He is owned here, and answers the Imcando question by reading the other
 * quest's stage -- a quest may read another's progress but never write it.
 *
 * Deviations, all forced and all small:
 *
 *   - The black arm hideout's own door, Jagex's door 21, was missing from RSCD's
 *     world file and has since been restored from the landscape, at (148,533).
 *     This quest deliberately does not claim it: nothing gates it in the game
 *     either. Katrine's first line to a stranger is an answer to "What is this
 *     place?", asked from inside, so anyone may walk in and it is the gang that
 *     turns them away, not the door. Straven's door and the weapons store door
 *     are gated, and both are enforced here.
 *   - Item 47, the phoenix hq key, is not given out. It was withdrawn in the
 *     quest's own rework between 10 May and 11 June 2001 -- black arm players
 *     were trading for it and walking into the hideout -- and the transcripts
 *     that survive are of the game after that rework, where Straven hands over
 *     the weapons store key alone.
 *   - Which gang a player joined is carried in the stage number, and the stage
 *     number becomes FINISHED when the king pays: there is one persisted integer
 *     per quest and it is spent. So the gang is written down a second time, at
 *     the moment of joining, in GangMembership -- a record rather than a quest,
 *     and the only reason it exists. Nothing here reads it directly; the four
 *     side and member questions below ask the stage first and the record second.
 *   - The weaponsmaster's three behaviours all key off gang membership, and the
 *     black arm is treated exactly as a stranger in every one of them. See
 *     talkToWeaponsmaster, stealCrossbow and refusesAttack below.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class ShieldOfArrav extends Quest {

    public final static int UID = Quests.SHIELD_OF_ARRAV;

    /* Shared opening. */
    /** Reldo has been asked for a quest; the bookcase now holds the book. */
    private static final int ASKED_RELDO = 1;
    /** The book has been read. */
    private static final int READ_BOOK = 2;
    /** Reldo has named Baraek. Both gangs are now approachable. */
    private static final int STARTED = 3;

    /*
     * Between STARTED and joining a gang the player is free: they may talk to
     * both informants, hear both pitches, and even hold both gangs' errands at
     * once. Vanilla kept that state in separate flags and only committed the
     * player at one of two moments -- killing Jonny the beard on Straven's
     * errand, or handing Katrine the crossbows. The stage integer is the only
     * thing persisted per quest, so the free period is encoded in it: stage
     * EXPLORING plus a bitmask of which conversations have happened. None of
     * the bits is a commitment; the stage only leaves this range at one of the
     * two authentic points of no return.
     */
    private static final int EXPLORING = 100;
    /** Baraek has been paid and has given up the hideout's location. */
    private static final int BIT_BRIBED_BARAEK = 1;
    /** The tramp has named Katrine. */
    private static final int BIT_MET_TRAMP = 2;
    /** Straven wants Jonny the beard dead and his report brought back. */
    private static final int BIT_PHOENIX_MISSION = 4;
    /** Katrine wants two phoenix crossbows. */
    private static final int BIT_BLACKARM_MISSION = 8;
    private static final int BITS_ALL = 15;

    /* Phoenix gang. */
    /** Legacy stage from before EXPLORING: bribing Baraek used to commit. */
    private static final int BRIBED_BARAEK = 10;
    /** Jonny the beard is dead by this player's hand: phoenix, no way back. */
    private static final int KILL_JONNY = 11;
    /** A phoenix gang member, holding the weapons store key. */
    private static final int PHOENIX = 12;
    /** The left half is out of the chest. */
    private static final int PHOENIX_HALF = 13;
    /** The curator has taken the whole shield from this player. */
    private static final int PHOENIX_CERT = 14;

    /* Black arm gang. */
    /** Legacy stage from before EXPLORING: meeting the tramp used to commit. */
    private static final int MET_TRAMP = 20;
    /** Legacy stage from before EXPLORING: Katrine's errand used to commit. */
    private static final int GET_CROSSBOWS = 21;
    /** A black arm gang member. */
    private static final int BLACKARM = 22;
    /** The right half is out of the cupboard. */
    private static final int BLACKARM_HALF = 23;
    /** The curator has taken the whole shield from this player. */
    private static final int BLACKARM_CERT = 24;

    private static final int FINISHED = 31;

    /** The hidden record that remembers the gang after this quest ends. */
    private static final int GANG = Quests.FIRST_CUSTOM;

    /** Hero's quest, which sends the player back to their own leader. */
    private static final int HEROS = Quests.HEROS_QUEST;
    private static final int CANDLESTICK = 585;

    private static final int RELDO = 20;
    /** Straven, who is only ever called "Man" to your face. */
    private static final int STRAVEN = 24;
    private static final int JONNY = 25;
    private static final int BARAEK = 26;
    private static final int KATRINE = 27;
    /** The tramp outside the alley. RSCD's definition calls him Jake. */
    private static final int TRAMP = 28;
    private static final int WEAPONSMASTER = 37;
    private static final int CURATOR = 39;
    private static final int KING = 42;

    private static final int COINS = 10;
    private static final int BOOK = 30;
    private static final int STORE_KEY = 48;
    private static final int SCROLL = 49;
    private static final int SHIELD_LEFT = 53;
    private static final int SHIELD_RIGHT = 54;
    private static final int PHOENIX_CROSSBOW = 59;
    private static final int CERTIFICATE = 61;
    private static final int FUR = 146;
    private static final int GREY_WOLF_FUR = 541;

    private static final int BRIBE = 20;
    private static final int FUR_PRICE = 20;
    private static final int FUR_PRICE_HAGGLED = 18;
    private static final int FUR_BUY_PRICE = 12;
    private static final int WOLF_FUR_BUY_PRICE = 120;
    private static final int CROSSBOWS_WANTED = 2;
    private static final int REWARD = 600;

    /** The library bookcase that holds the book, and only that one. */
    private static final int BOOKCASE = 67;
    private static final int BOOKCASE_X = 132;
    private static final int BOOKCASE_Y = 455;

    /**
     * Both containers are placed in their open state, as Jagex placed them --
     * "Search, Close" rather than "Open, Examine". Closing swaps in the shut
     * model for a moment and the map puts the open one back.
     */
    private static final int CHEST = 81;
    private static final int CHEST_SHUT = 82;
    private static final int CHEST_X = 101;
    private static final int CHEST_Y = 3380;

    private static final int CUPBOARD = 85;
    private static final int CUPBOARD_SHUT = 84;
    private static final int CUPBOARD_X = 145;
    private static final int CUPBOARD_Y = 1477;

    /** Straven's door, into the phoenix hideout proper. */
    private static final int HIDEOUT_DOOR = 19;
    private static final int HIDEOUT_DOOR_X = 110;
    private static final int HIDEOUT_DOOR_Y = 3370;

    /** The weapons store door, at street level. Needs the key. */
    private static final int STORE_DOOR = 20;
    private static final int STORE_DOOR_X = 103;
    private static final int STORE_DOOR_Y = 532;

    private static final int OPEN_DOOR = 11;

    public ShieldOfArrav(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Shield of Arrav");
        setFinalStage(FINISHED);
        associateNpc(RELDO);
        associateNpc(STRAVEN);
        associateNpc(JONNY);
        associateNpc(BARAEK);
        associateNpc(KATRINE);
        associateNpc(TRAMP);
        // @share npc 39 with Digsite
        // Digsite only ever has items used on the curator -- the letter and the
        // three certificates -- and never answers NPC_TALK for him, so the two
        // claims cannot see each other's triggers.
        associateNpc(CURATOR);
        associateNpc(KING);
        // The weaponsmaster answers to gang membership in three separate ways,
        // and this quest is the only thing that knows which gang the player
        // joined. He is claimed here rather than registered as a plain
        // NpcHandler because a handler beats quest dispatch and would take all
        // three from us.
        associateNpc(WEAPONSMASTER);
        associateObject(BOOKCASE);
        associateObject(CHEST);
        associateObject(CUPBOARD);
        associateDoor(HIDEOUT_DOOR);
        associateDoor(STORE_DOOR);
        // The book, so that "read" reaches us; the crossbows, so that lifting
        // one wakes the weaponsmaster.
        associateItem(BOOK);
        associateItem(PHOENIX_CROSSBOW);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Varrocian literature tells of a valuable shield stolen from long ago the museum of Varrock by a gang of professional thieves. See if you can track down this shield and return it to the museum. You will need a friend to help you complete this quest.");
        setStartPoint("Varrock palace Library");
        setSpeakTo("Reldo");
        setMissionLength("Medium");
        require("One friend");
        rewardItem(COINS, REWARD);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Shield of Arrav quest");
    }

    // ------------------------------------------------------------ helpers --

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    /**
     * Which gang this player joined, from the record that outlives this quest.
     *
     * The stage number carries the gang only while the quest is running: 12 up
     * to 14 is a phoenix, 22 up to 24 is a black arm, and 31 is neither because
     * the king has paid and there is one integer per quest. Vanilla went on
     * caring afterwards -- Straven sets his dogs on a black arm who walks into
     * the weapons store, Katrine will not talk to a phoenix man at all, and
     * Hero's quest sends you to whichever leader is yours -- so the gang is
     * written down once, at the moment of joining, in GangMembership. These
     * four questions ask the stage first and the record second, which keeps
     * every mid-quest answer exactly what it was.
     */
    private boolean recorded(String key) {
        return getOwner().getQuestManager().reached(GANG, key);
    }

    private void record(String key) {
        getOwner().getQuestManager().note(GANG, key);
    }

    /** In the free period: started, not yet committed to either gang. */
    private boolean exploring() {
        return getStage() == STARTED
            || (getStage() >= EXPLORING && getStage() <= EXPLORING + BITS_ALL);
    }

    /** Whether this exploring-period conversation has happened. */
    private boolean noted(int bit) {
        return getStage() >= EXPLORING && (getStage() - EXPLORING & bit) != 0;
    }

    /** Remember an exploring-period conversation. Commits to nothing. */
    private void note(int bit) {
        int bits = getStage() >= EXPLORING ? getStage() - EXPLORING : 0;
        setStage(EXPLORING + (bits | bit));
    }

    /** Committed to the phoenix gang -- Jonny is dead or they are a member. */
    private boolean phoenixSide() {
        return (getStage() >= KILL_JONNY && getStage() <= PHOENIX_CERT)
            || recorded("phoenix");
    }

    private boolean phoenixMember() {
        return (getStage() >= PHOENIX && getStage() <= PHOENIX_CERT)
            || recorded("phoenix");
    }

    /** Committed to the black arm gang -- Katrine has her crossbows. */
    private boolean blackArmSide() {
        return (getStage() >= BLACKARM && getStage() <= BLACKARM_CERT)
            || recorded("black-arm");
    }

    private boolean blackArmMember() {
        return (getStage() >= BLACKARM && getStage() <= BLACKARM_CERT)
            || recorded("black-arm");
    }

    /**
     * Finished the quest before the gang record existed, so nobody knows which
     * side this player took.
     *
     * Only reachable by a character saved by an older build. Both leaders will
     * recruit such a player for Hero's quest, and whichever one does gets
     * written down, which puts the record right for good.
     */
    private boolean gangForgotten() {
        return completed() && !recorded("member");
    }

    /* ------------------------------------------------------- hero's quest --
     *
     * The master thief armband is Hero's quest's, but it is handed over by
     * whichever of these two leaders the player joined -- and both of them
     * belong here, because this quest recruited them and an npc can only have
     * one owner. So the conversation lives with the npc, as King Arthur's does,
     * and Hero's quest is asked where the player has got to and told what
     * happened. Nothing below reads or writes a stage in either direction.
     */

    /** On Hero's quest, sent to their leader, and not yet paid. */
    private boolean heroSeekingArmband() {
        return getOwner().getQuestManager().reached(HEROS, "seeking-armband");
    }

    /**
     * Holding a candlestick they took themselves.
     *
     * Jagex would not accept one handed over by a player who had already
     * finished the quest, and this is the same rule: the flag belongs to
     * whoever killed Grip or emptied the chest, which between them is both
     * halves of a genuine pair.
     */
    private boolean heroBringingCandlestick() {
        return count(CANDLESTICK) > 0
            && getOwner().getQuestManager().reached(HEROS, "stole-candlestick");
    }

    /** Has fetched their gang's half, whether or not they still hold it. */
    private boolean gotHalf() {
        return getStage() == PHOENIX_HALF || getStage() == PHOENIX_CERT
            || getStage() == BLACKARM_HALF || getStage() == BLACKARM_CERT;
    }

    // ----------------------------------------------------------- dispatch --

    /**
     * Map a save from before EXPLORING existed onto it. The old stages 10, 20
     * and 21 treated an informant's tip as a commitment; they become the
     * matching uncommitted bits. Old stage 11 stays: those players had already
     * accepted Straven's errand under rules that had committed them at 10, and
     * 11 still reads as phoenix-committed, which is no worse than they were.
     */
    private void migrate() {
        switch (getStage()) {
            case BRIBED_BARAEK: setStage(EXPLORING + BIT_BRIBED_BARAEK); break;
            case MET_TRAMP:     setStage(EXPLORING + BIT_MET_TRAMP);     break;
            case GET_CROSSBOWS: setStage(EXPLORING + BIT_MET_TRAMP + BIT_BLACKARM_MISSION); break;
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        migrate();
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == JONNY) {
                    // The report drops for anyone; the commitment is only for a
                    // player on Straven's errand. This is vanilla's phoenix
                    // point of no return -- the black arm will not have Jonny's
                    // killer, whatever else they have or have not done.
                    if (exploring() && noted(BIT_PHOENIX_MISSION)) {
                        setStage(KILL_JONNY);
                    }
                    dropReport();
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case RELDO:   talkToReldo(npc);   break;
                case BARAEK:  talkToBaraek(npc);  break;
                case TRAMP:   talkToTramp(npc);   break;
                case KATRINE: talkToKatrine(npc); break;
                case STRAVEN: talkToStraven(npc); break;
                case CURATOR: talkToCurator(npc); break;
                case KING:    talkToKing(npc);    break;
                case WEAPONSMASTER: talkToWeaponsmaster(npc); break;
                case JONNY:
                    // Jagex logged this one properly rather than leaving it to
                    // the generic "is not interested in talking".
                    getOwner().getActionSender().sendMessage("Johnny the beard is not interested in talking");
                    break;
            }
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            switch (object.getID()) {
                case BOOKCASE:
                    // The bookcase's commands are WalkTo/Search, so Search is
                    // the SECOND action, not the first as with chest/cupboard.
                    if (trigger == QuestTrigger.OBJECT_ACT2) { searchBookcase(object); }
                    break;
                case CHEST:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchChest(object); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { shut(object, CHEST_SHUT, "chest"); }
                    break;
                case CUPBOARD:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchCupboard(object); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { shut(object, CUPBOARD_SHUT, "cupboard"); }
                    break;
            }
            if (trigger == QuestTrigger.DOOR_ACT1) {
                if (object.getID() == HIDEOUT_DOOR) { openHideoutDoor(object); }
                else if (object.getID() == STORE_DOOR) { openStoreDoor(object); }
            } else if (trigger == QuestTrigger.DOOR_ACT2) {
                getOwner().getActionSender().sendMessage("The door is shut");
            }
            return;
        }
        if (entity instanceof InvItem) {
            InvItem item = (InvItem) entity;
            if (trigger == QuestTrigger.ITEM_COMMAND && item.getID() == BOOK) {
                readBook();
            } else if (trigger == QuestTrigger.ITEM_PICKUP && item.getID() == PHOENIX_CROSSBOW) {
                stealCrossbow();
            }
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_DOOR && entity instanceof GameObject
                && ((GameObject) entity).getID() == STORE_DOOR) {
            // "Use the key on the door" and "open the door" are the same act
            // here, so both land in the same place.
            openStoreDoor((GameObject) entity);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // -------------------------------------------------------------- reldo --

    private void talkToReldo(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        ArrayList<String> opts = new ArrayList<String>();
        final ArrayList<Integer> keys = new ArrayList<Integer>();

        opts.add("Hello");                          keys.add(0);
        if (getStage() == READ_BOOK) {
            opts.add("OK I've read the book");      keys.add(1);
        } else if (!questStarted()) {
            opts.add("I'm in search of a quest");   keys.add(2);
        }
        if (getOwner().getQuestManager().stageOf(Quests.THE_KNIGHTS_SWORD) >= 1) {
            opts.add("What do you know about the Imcando dwarves?");
            keys.add(3);
        }
        opts.add("Do you have anything to trade?"); keys.add(4);
        opts.add("What do you do?");                keys.add(5);

        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                switch (keys.get(option).intValue()) {
                    case 0:
                        c.npc("Hello stranger");
                        break;
                    case 1:
                        c.player("Do you know where I can find the Phoenix Gang")
                         .npc("No I don't")
                         .npc("I think I know someone who will though")
                         .npc("Talk to Baraek, the fur trader in the market place")
                         .npc("I've heard he has connections with the Phoenix Gang")
                         .player("Thanks, I'll try that")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 setStage(STARTED);
                             }
                         });
                        break;
                    case 2:
                        c.npc("I don't think there's any here")
                         .npc("Let me think actually")
                         .npc("If you look in a book")
                         .npc("called the shield of Arrav")
                         .npc("You'll find a quest in there")
                         .npc("I'm not sure where the book is mind you")
                         .npc("I'm sure it's somewhere in here")
                         .player("Thankyou")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 if (!questStarted()) {
                                     setStage(ASKED_RELDO);
                                 }
                             }
                         });
                        break;
                    case 3:
                        // The knight's sword needs this and cannot ask for it
                        // itself -- Reldo is one npc and belongs to one quest.
                        c.npc("The Imcando Dwarves, you say?")
                         .npc("They were the world's most skilled smiths about a hundred years ago")
                         .npc("They used secret knowledge")
                         .npc("Which they passed down from generation to generation")
                         .npc("Unfortunatly about a century ago the once thriving race")
                         .npc("Was wiped out during the barbarian invasions of that time")
                         .player("So are there any Imcando left at all?")
                         .npc("A few of them survived")
                         .npc("But with the bulk of their population destroyed")
                         .npc("Their numbers have dwindled even further")
                         .npc("Last I knew there were a couple living in Asgarnia")
                         .npc("Near the cliffs on the Asgarnian southern peninsula")
                         .npc("They tend to keep to themselves")
                         .npc("They don't tend to tell people that they're the descendants of the Imcando")
                         .npc("Which is why people think that the tribe has died out totally")
                         .npc("you may have more luck talking to them if you bring them some red berry pie")
                         .npc("They really like red berry pie");
                        break;
                    case 4:
                        c.npc("No, sorry. I'm not the trading type")
                         .player("ah well");
                        break;
                    default:
                        c.npc("I'm the palace librarian")
                         .player("Ah that's why you're in the library then")
                         .npc("Yes")
                         .npc("Though I might be in here even if I didn't work here")
                         .npc("I like reading");
                        break;
                }
            }
        });
        c.start();
    }

    private void searchBookcase(GameObject bookcase) {
        Player p = getOwner();
        if (bookcase.getX() != BOOKCASE_X || bookcase.getY() != BOOKCASE_Y) {
            p.getActionSender().sendMessage("You search the bookcase but find nothing of interest");
            return;
        }
        if (getStage() < ASKED_RELDO || getStage() > STARTED || count(BOOK) > 0) {
            p.getActionSender().sendMessage("You search the bookcase but find nothing of interest");
            return;
        }
        p.getInventory().add(new InvItem(BOOK, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find a book titled the shield of Arrav");
    }

    /**
     * The book. Jagex trimmed it once to fit the message log, and this is the
     * trimmed version -- the one the game shipped with.
     */
    private void readBook() {
        Player p = getOwner();
        new Conversation(p, null)
            .message("You read the book")
            .message("The shield of Arrav")
            .message("Arrav was a hero of the fourth age")
            .message("His shield hung in the museum of Varrock for 150 years")
            .message("In the year 143 of the fifth age it was stolen")
            .message("By the phoenix gang, who have since become")
            .message("The most powerful crime gang in Varrock")
            .message("The king of the time offered a reward for its return")
            .message("The reward has never been claimed")
            .then(new Effect() {
                public void run(Conversation c) {
                    if (getStage() == ASKED_RELDO) {
                        setStage(READ_BOOK);
                    }
                }
            })
            .start();
    }

    // ------------------------------------------------------------- baraek --

    private void talkToBaraek(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        ArrayList<String> opts = new ArrayList<String>();
        final ArrayList<Integer> keys = new ArrayList<Integer>();

        if (exploring() && !noted(BIT_BRIBED_BARAEK)) {
            opts.add("Can you tell me where I can find the phoenix gang?");
            keys.add(0);
        }
        opts.add("Can you sell me some furs?");     keys.add(1);
        if (count(FUR) > 0) {
            opts.add("Would you like to buy my fur?"); keys.add(2);
        }
        if (count(GREY_WOLF_FUR) > 0) {
            opts.add("Would you like to buy my grey wolf fur?"); keys.add(3);
        }
        opts.add("Hello I am in search of a quest"); keys.add(4);

        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                switch (keys.get(option).intValue()) {
                    case 0: bribeBaraek(c);   break;
                    case 1: buyFur(c);        break;
                    case 2: sellFur(c);       break;
                    case 3: sellWolfFur(c);   break;
                    default:
                        c.npc("Sorry kiddo, I'm a fur trader not a damsel in distress");
                        break;
                }
            }
        });
        c.start();
    }

    private void bribeBaraek(Conversation c) {
        c.npc("Sh Sh, not so loud")
         .npc("You don't want to get me in trouble")
         .player("So do you know where they are?")
         .npc("I may do")
         .npc("Though I don't want to get into trouble for revealing their hideout")
         .npc("Now if I was say 20 gold coins richer")
         .npc("I may happen to be more inclined to take that sort of risk")
         .options(new Choice("Okay have 20 gold coins",
                             "No I don't like things like bribery",
                             "Yes I'd like to be 20 gold coins richer too") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Heh, if you wanna deal with the phoenix gang")
                      .npc("They're involved in much worse than a bit of bribery");
                     return;
                 }
                 if (option == 2) {
                     return;
                 }
                 if (count(COINS) < BRIBE) {
                     c.player("Oops. I don't have 20 coins. Silly me.");
                     return;
                 }
                 c.take(COINS, BRIBE)
                  .npc("Cheers")
                  .npc("Ok to get to the gang hideout")
                  .npc("After entering Varrock through the south gate")
                  .npc("If you take the first turning east")
                  .npc("Somewhere along there is an alleyway to the south")
                  .npc("The door at the end of there is the entrance to the phoenix gang")
                  .npc("They're operating there under the name of the VTAM corporation")
                  .npc("Be careful")
                  .npc("The phoenix gang ain't the types to be messed with")
                  .player("Thanks")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          // A tip, not a commitment: vanilla let a player pay
                          // Baraek and still join the black arm gang.
                          if (exploring()) {
                              note(BIT_BRIBED_BARAEK);
                          }
                      }
                  });
             }
         });
    }

    private void buyFur(Conversation c) {
        c.npc("Yeah sure they're " + FUR_PRICE + " gold coins a piece")
         .options(new Choice("Yeah okay here you go",
                             FUR_PRICE + " gold coins that's an outrage",
                             "No thanks, I'll leave it") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     c.npc("It's your loss mate");
                     return;
                 }
                 if (option == 0) {
                     if (count(COINS) < FUR_PRICE) {
                         c.player("Oh dear I don't seem to have enough money")
                          .npc("Well, okay I'll go down to " + FUR_PRICE_HAGGLED);
                         haggledFur(c);
                         return;
                     }
                     c.take(COINS, FUR_PRICE).give(new InvItem(FUR, 1));
                     return;
                 }
                 c.npc("Well, okay I'll go down to " + FUR_PRICE_HAGGLED);
                 haggledFur(c);
             }
         });
    }

    private void haggledFur(Conversation c) {
        c.options(new Choice("Okay here you go", "No thanks, I'll leave it") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("It's your loss mate");
                    return;
                }
                if (count(COINS) < FUR_PRICE_HAGGLED) {
                    c.player("Oh dear I don't seem to have enough money")
                     .npc("Well I can't go any cheaper than that mate")
                     .npc("I have a family to feed")
                     .player("Ah well never mind");
                    return;
                }
                c.take(COINS, FUR_PRICE_HAGGLED).give(new InvItem(FUR, 1));
            }
        });
    }

    private void sellFur(Conversation c) {
        c.npc("Lets have a look at it")
         .npc("It's not in the best of condition")
         .npc("I guess I could give " + FUR_BUY_PRICE + " coins to take it off your hands")
         .options(new Choice("Yeah that'll do", "I think I'll keep hold of it actually") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Oh ok").npc("Didn't want it anyway");
                     return;
                 }
                 if (count(FUR) < 1) {
                     return;
                 }
                 c.take(FUR, 1).give(new InvItem(COINS, FUR_BUY_PRICE));
             }
         });
    }

    private void sellWolfFur(Conversation c) {
        c.npc("Grey wolf fur, now you're talking")
         .npc("Hmm I'll give you " + WOLF_FUR_BUY_PRICE + " per fur, does that sound fair?")
         .options(new Choice("Yep sounds fine",
                             "No I almost got my throat torn out by a wolf to get this") {
             public void picked(int option, Conversation c) {
                 if (option == 1 || count(GREY_WOLF_FUR) < 1) {
                     return;
                 }
                 c.take(GREY_WOLF_FUR, 1).give(new InvItem(COINS, WOLF_FUR_BUY_PRICE));
             }
         });
    }

    // -------------------------------------------------------------- tramp --

    private void talkToTramp(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.npc("Spare some change guv?")
         .options(new Choice("Sorry I haven't got any",
                             "Go get a job",
                             "Ok here you go",
                             "Is there anything down this alleyway?") {
             public void picked(int option, Conversation c) {
                 switch (option) {
                     case 0:
                         c.npc("Thanks anyway");
                         break;
                     case 1:
                         c.npc("You startin?");
                         break;
                     case 2:
                         if (count(COINS) > 0) {
                             c.take(COINS, 1);
                         }
                         c.npc("Thankyou, thats great")
                          .options(new Choice("No problem",
                                              "So don't I get some sort of quest hint or something now") {
                              public void picked(int option, Conversation c) {
                                  if (option == 1) {
                                      c.npc("No that's not why I'm asking for money")
                                       .npc("I just need to eat");
                                  }
                              }
                          });
                         break;
                     default:
                         alleyway(c);
                         break;
                 }
             }
         });
        c.start();
    }

    private void alleyway(Conversation c) {
        c.npc("Yes there is actually")
         .npc("A notorious gang of thieves and hoodlums")
         .npc("Called the blackarm gang");
        if (getStage() < STARTED || blackArmSide() || completed()) {
            c.player("Thanks for the warning")
             .npc("Don't worry about it");
            if (blackArmSide()) {
                c.player("Do you think they would let me join?")
                 .npc("I was under the impression you were already a member");
            }
            return;
        }
        c.options(new Choice("Thanks for the warning", "Do you think they would let me join?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Don't worry about it");
                    return;
                }
                if (phoenixSide()) {
                    // The tramp is one of Straven's, and he knows.
                    c.npc("No")
                     .npc("You're a collaborator with the phoenix gang")
                     .npc("There's no way they'll let you join")
                     .player("How did you know I was in the phoenix gang?")
                     .npc("I spend a lot of time on the streets")
                     .npc("And you hear those sorta things sometimes")
                     .options(new Choice("Any ideas how I could get in there then?", "Like who?") {
                         public void picked(int option, Conversation c) {
                             if (option == 1) {
                                 c.npc("There's plenty of other adventurers about")
                                  .npc("Besides yourself")
                                  .npc("I'm sure if you asked one of them nicely")
                                  .npc("They would help you");
                                 return;
                             }
                             c.npc("Hmm I dunno")
                              .npc("Your best bet would probably be to get someone else")
                              .npc("Someone who isn't a member of the phoenix gang")
                              .npc("To Infiltrate the ranks of the black arm gang")
                              .npc("If you find someone")
                              .npc("Tell em to come to me first")
                              .player("Ok good plan");
                         }
                     });
                    return;
                }
                c.npc("You never know")
                 .npc("You'll find a lady down there called Katrine")
                 .npc("Speak to her")
                 .npc("But don't upset her, she's pretty dangerous")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         if (exploring()) {
                             note(BIT_MET_TRAMP);
                         }
                     }
                 });
            }
        });
    }

    // ------------------------------------------------------------ katrine --

    private void talkToKatrine(Npc npc) {
        Player p = getOwner();
        final Conversation c = new Conversation(p, npc);

        if (phoenixSide()) {
            c.npc("You've got some guts coming here")
             .npc("Phoenix guy")
             .npc("Now go away")
             .npc("Or I'll make sure you 'aven't got those guts anymore")
             .start();
            return;
        }
        // gangForgotten(): a character saved before the record existed. Katrine
        // takes them as one of hers, and the first thing she is asked for
        // writes the record down, which settles it for good.
        if (blackArmMember() || gangForgotten()) {
            ArrayList<String> opts = new ArrayList<String>();
            final ArrayList<Integer> keys = new ArrayList<Integer>();
            opts.add("Who are all those people in there?"); keys.add(0);
            opts.add("Teach me to be a top class criminal"); keys.add(1);
            if (heroBringingCandlestick()) {
                opts.add("I have a candlestick now"); keys.add(3);
            } else if (heroSeekingArmband()) {
                opts.add("Is there any way I can get the rank of master thief?"); keys.add(2);
            }
            c.player("Hey")
             .npc("Hey")
             .options(new Choice(opts.toArray(new String[opts.size()])) {
                 public void picked(int option, Conversation c) {
                     switch (keys.get(option).intValue()) {
                         case 0:
                             c.npc("They're just various rogues and thieves")
                              .player("They don't say a lot")
                              .npc("Nope");
                             break;
                         case 1:
                             c.npc("Teach yourself");
                             break;
                         case 2:
                             c.npc("Master thief? We are the ambitious one aren't we?")
                              .npc("Well you're going to have do something pretty amazing")
                              .player("Anything you can suggest?")
                              .npc("Well some of the most coveted prizes in thiefdom right now")
                              .npc("Are in the pirate town of Brimhaven on Karamja")
                              .npc("The pirate leader Scarface Pete")
                              .npc("Has a pair of extremely rare valuable candlesticks")
                              .npc("His security is very good")
                              .npc("We of course have gang members in a town like Brimhaven")
                              .npc("They may be able to help you")
                              .npc("visit our hideout in the alleyway on palm street")
                              .npc("To get in you will need to tell them the word four leafed clover")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      record("joined-black-arm");
                                      getOwner().getQuestManager().note(HEROS, "told-about-armband");
                                  }
                              });
                             break;
                         default:
                             c.npc("Wow is it really it?")
                              .take(CANDLESTICK, 1)
                              .npc("This really is a fine bit of thievery")
                              .npc("Thieves have been trying to get hold of this 1 for a while")
                              .npc("You wanted to be ranked as master thief didn't you?")
                              .npc("Well I guess this just about ranks as good enough")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      record("joined-black-arm");
                                      getOwner().getQuestManager().note(HEROS, "armband-earned");
                                  }
                              });
                             break;
                     }
                 }
             });
            c.start();
            return;
        }
        if (exploring() && noted(BIT_BLACKARM_MISSION)) {
            crossbowCheck(c);
            c.start();
            return;
        }

        c.options(new Choice("What is this place?", "I'm looking for fame and riches") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("And you expect to find it up the backstreets of Varrock?");
                    return;
                }
                c.npc("It's a private business")
                 .npc("Can I help you at all?");
                if (!noted(BIT_MET_TRAMP)) {
                    c.options(new Choice("What sort of business", "I'm looking for the door out of here") {
                        public void picked(int option, Conversation c) {
                            if (option == 1) {
                                c.npc("Try the one you just came in");
                                return;
                            }
                            c.npc("A small family business")
                             .npc("We give financial advice to other companies");
                        }
                    });
                    return;
                }
                c.options(new Choice("I've heard you're the blackarm gang",
                                     "What sort of business",
                                     "I'm looking for the door out of here") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("A small family business")
                             .npc("We give financial advice to other companies");
                            return;
                        }
                        if (option == 2) {
                            c.npc("Try the one you just came in");
                            return;
                        }
                        c.npc("Who told you that?")
                         .options(new Choice("I'd rather not reveal my sources",
                                             "It was the tramp outside",
                                             "Everyone knows - its no great secret") {
                             public void picked(int option, Conversation c) {
                                 if (option == 0) {
                                     c.npc("Yes, I can understand that");
                                 } else if (option == 1) {
                                     c.npc("Is that guy still out there?")
                                      .npc("He's getting to be a nuisance")
                                      .npc("Remind me to send someone to kill him")
                                      .npc("So now you've found us");
                                 } else {
                                     c.player("It's no great secret")
                                      .npc("I thought we were safe back here")
                                      .player("Oh no, not at all")
                                      .player("It's so obvious")
                                      .player("Even the town guard have caught on")
                                      .npc("Wow we must be obvious")
                                      .npc("I guess they'll be expecting bribes again soon in that case")
                                      .npc("Thanks for the information");
                                 }
                                 c.npc("So what do you want with us?")
                                  .options(new Choice("I want to become a member of your gang",
                                                      "I want some hints for becoming a thief") {
                                      public void picked(int option, Conversation c) {
                                          if (option == 1) {
                                              c.npc("Well I'm sorry luv")
                                               .npc("I'm not giving away my secrets")
                                               .npc("Not to none black arm members anyway");
                                              return;
                                          }
                                          joinBlackArm(c);
                                      }
                                  }.says(1, "I want some hints for becomming a thief"));
                             }
                         }.says(2, "Everyone knows"));
                    }
                });
            }
        });
        c.start();
    }

    private void joinBlackArm(Conversation c) {
        c.npc("How unusual")
         .npc("Normally we recruit for our gang")
         .npc("By watching local thugs and thieves in action")
         .npc("People don't normally waltz in here")
         .npc("Saying 'hello can I play'")
         .npc("How can I be sure you can be trusted?")
         .options(new Choice("Well you can give me a try, can't you?",
                             "Well people tell me I have an honest face") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("I'm not so sure");
                 } else {
                     c.npc("How unusual someone honest wanting to join a gang of thieves")
                      .npc("Excuse me if i remain unconvinced");
                 }
                 c.npc("I think I may have a solution actually")
                  .npc("Our rival gang - the phoenix gang")
                  .npc("Has a weapons stash a little east of here")
                  .npc("We're fresh out of crossbows")
                  .npc("So if you could steal a couple of crossbows for us")
                  .npc("That would be very much appreciated")
                  .npc("Then I'll be happy to call you a black arm")
                  .options(new Choice("Ok no problem", "Sounds a little tricky got anything easier?") {
                      public void picked(int option, Conversation c) {
                          if (option == 1) {
                              c.npc("If you're not up for a little bit of danger")
                               .npc("I don't think you've got anything to offer our gang");
                              return;
                          }
                          // An errand, not a commitment: only handing over the
                          // crossbows makes this player a black arm.
                          if (exploring()) {
                              note(BIT_BLACKARM_MISSION);
                          }
                      }
                  });
             }
         });
    }

    private void crossbowCheck(Conversation c) {
        c.npc("Have you got those crossbows for me yet?");
        int held = count(PHOENIX_CROSSBOW);
        if (held >= CROSSBOWS_WANTED) {
            c.player("Yes I have")
             .take(PHOENIX_CROSSBOW, CROSSBOWS_WANTED)
             .npc("Ok you can join our gang now")
             .npc("Feel free to enter any the rooms of the ganghouse")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(BLACKARM);
                     record("joined-black-arm");
                 }
             });
            return;
        }
        if (held == 1) {
            c.player("I have one")
             .npc("I need two")
             .npc("Come back when you have them");
            return;
        }
        c.player("No I haven't found them yet")
         .npc("I need two crossbows")
         .npc("Stolen from the phoenix gang weapons stash")
         .npc("which if you head east for a bit")
         .npc("Is a building on the south side of of the road");
    }

    // ------------------------------------------------------------ straven --

    private void talkToStraven(final Npc npc) {
        Player p = getOwner();
        final Conversation c = new Conversation(p, npc);

        if (blackArmSide()) {
            c.npc("hey get away from there")
             .npc("Black arm dog")
             .then(new Effect() {
                 public void run(Conversation c) {
                     // Not straight away: the player is still marked busy for
                     // as long as the conversation is running, and attackPlayer
                     // refuses a busy target. This lands just after it ends.
                     world.getDelayedEventHandler().add(new SingleEvent(c.getPlayer(), 1000) {
                         public void action() {
                             npc.attackPlayer(this.owner);
                         }
                     });
                 }
             })
             .start();
            return;
        }
        if (phoenixMember() || completed()) {
            greetMember(c);
            c.start();
            return;
        }
        if (getStage() == KILL_JONNY || (exploring() && noted(BIT_PHOENIX_MISSION))) {
            c.npc("Hows your little mission going?");
            if (count(SCROLL) > 0) {
                c.player("I have the intelligence report")
                 .take(SCROLL, 1)
                 .npc("Lets see it then")
                 .npc("Yes this is very good")
                 .npc("Ok you can join the phoenix gang")
                 .npc("I am Straven, one of the gang leaders")
                 .player("Nice to meet you")
                 .npc("Here is a key")
                 .give(new InvItem(STORE_KEY, 1))
                 .npc("It will let you enter our weapon supply area")
                 .npc("Round the front of this building")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(PHOENIX);
                         record("joined-phoenix");
                     }
                 });
            } else {
                c.player("I haven't managed to find the report yet")
                 .npc("You need to kill Jonny the beard")
                 .npc("Who should be in the blue moon inn");
            }
            c.start();
            return;
        }

        c.player("What's through that door?")
         .npc("Heh you can't go in there")
         .npc("Only authorised personnel of the VTAM corporation are allowed beyond this point");
        ArrayList<String> opts = new ArrayList<String>();
        final ArrayList<Integer> keys = new ArrayList<Integer>();
        if (exploring() && noted(BIT_BRIBED_BARAEK)) {
            opts.add("I know who you are"); keys.add(0);
        }
        opts.add("How do I get a job with the VTAM corporation?"); keys.add(1);
        opts.add("Why not?"); keys.add(2);

        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                switch (keys.get(option).intValue()) {
                    case 0: revealSelf(c); break;
                    case 1:
                        c.npc("Get a copy of the Varrock Herald")
                         .npc("If we have any positions right now")
                         .npc("They'll be advertised in there");
                        break;
                    default:
                        c.npc("Sorry that is classified information");
                        break;
                }
            }
        });
        c.start();
    }

    private void revealSelf(Conversation c) {
        c.npc("I see")
         .npc("Carry on")
         .player("This is the headquarters of the Phoenix Gang")
         .player("The most powerful crime gang this city has seen")
         .npc("And supposing we were this crime gang")
         .npc("What would you want with us?")
         .options(new Choice("I'd like to offer you my services",
                             "I want nothing. I was just making sure you were them") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Well stop wasting my time");
                     return;
                 }
                 c.npc("You mean you'd like to join the phoenix gang?")
                  .npc("Well the phoenix gang doesn't let people join just like that")
                  .npc("You can't be too careful, you understand")
                  .npc("Generally someone has to prove their loyalty before they can join")
                  .player("How would I go about this?")
                  .npc("Let me think")
                  .npc("I have an idea")
                  .npc("A rival gang of ours")
                  .npc("Called the black arm gang")
                  .npc("Is meant to be meeting their contact from Port Sarim today")
                  .npc("In the blue moon inn")
                  .npc("the south entrance to this city")
                  .npc("The name of this contact is Jonny the beard")
                  .npc("Kill him and bring back his intelligence report")
                  .player("Ok, I'll get on it")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          // Accepting the errand commits to nothing; killing
                          // Jonny is the commitment, in the NPC_KILLED branch.
                          if (exploring()) {
                              note(BIT_PHOENIX_MISSION);
                          }
                      }
                  });
             }
         });
    }

    private void greetMember(final Conversation c) {
        c.npc("Greetings fellow gang member");
        ArrayList<String> opts = new ArrayList<String>();
        final ArrayList<Integer> keys = new ArrayList<Integer>();
        if (count(STORE_KEY) < 1) {
            opts.add("I have lost the key you gave me"); keys.add(0);
        }
        /* The menu says "treasure", the player says "treasures" -- Jagex's own
           mismatch, and the index moves with the lost-key entry above, so the
           says() slot has to be looked up rather than written as a constant. */
        final int treasureOpt = opts.size();
        opts.add("I've heard you've got some cool treasure in this place"); keys.add(1);
        opts.add("Any suggestions for where I can go thieving?"); keys.add(2);
        opts.add("Where's the Blackarm gang hideout?"); keys.add(3);
        if (heroBringingCandlestick()) {
            opts.add("I have retrieved a candlestick"); keys.add(5);
        } else if (heroSeekingArmband()) {
            opts.add("How would I go about getting a master thieves armband?"); keys.add(4);
        }

        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                switch (keys.get(option).intValue()) {
                    case 4:
                        c.npc("Ooh tricky stuff, took me years to get that rank")
                         .npc("Well what some of aspiring thieves in our gang are working on right now")
                         .npc("Is to steal some very valuable rare candlesticks")
                         .npc("From scarface Pete - the pirate leader on Karamja")
                         .npc("His security is good enough and the target valuable enough")
                         .npc("That might be enough to get you the rank")
                         .npc("Go talk to our man Alfonse the waiter in the shrimp and parrot")
                         .npc("Use the secret word gherkin to show you're one of us")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 record("joined-phoenix");
                                 getOwner().getQuestManager().note(HEROS, "told-about-armband");
                             }
                         });
                        break;
                    case 5:
                        c.npc("Hmm not a bad job")
                         .npc("Let's see it, make sure it's genuine")
                         .take(CANDLESTICK, 1)
                         .player("So is this enough to get me a master thieves armband?")
                         .npc("Hmm I dunno")
                         .npc("I suppose I'm in a generous mood today")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 record("joined-phoenix");
                                 getOwner().getQuestManager().note(HEROS, "armband-earned");
                             }
                         });
                        break;
                    case 0:
                        c.npc("You need to be more careful")
                         .npc("We don't want that falling into the wrong hands")
                         .npc("Ah well")
                         .npc("Have this spare")
                         .give(new InvItem(STORE_KEY, 1));
                        break;
                    case 1:
                        c.npc("Oh yeah, we've all stolen some stuff in our time")
                         .npc("The candlesticks down here")
                         .npc("Were quite a challenge to get out the palace")
                         .player("And the shield of Arrav")
                         .player("I heard you got that")
                         .npc("hmm")
                         .npc("That was a while ago")
                         .npc("We don't even have all the shield anymore")
                         .npc("About 5 years ago")
                         .npc("We had a massive fight in our gang")
                         .npc("The shield got broken in half during the fight")
                         .npc("Shortly after the fight")
                         .npc("Some gang members decided")
                         .npc("They didn't want to be part of our gang anymore")
                         .npc("So they split off to form their own gang")
                         .npc("The black arm gang")
                         .npc("On their way out")
                         .npc("They looted what treasures they could from us")
                         .npc("Which included one of the halves of the shield")
                         .npc("We've been rivals with the black arms ever since");
                        break;
                    case 2:
                        c.npc("You can always try the market")
                         .npc("Lots of opportunity there");
                        break;
                    default:
                        c.player("I wanna go sabotage em")
                         .npc("That would be a little tricky")
                         .npc("Their security is pretty good")
                         .npc("Not as good as ours obviously")
                         .npc("But still good")
                         .npc("If you really want to go there")
                         .npc("It is in the alleyway")
                         .npc("To the west as you come in the south gate")
                         .npc("One of our operatives is often near the alley")
                         .npc("A red haired tramp")
                         .npc("He may be able to give you some ideas")
                         .player("Thanks for the help");
                        break;
                }
            }
        }.says(treasureOpt, "I've heard you've got some cool treasures in this place"));
    }

    // -------------------------------------------------------------- jonny --

    /**
     * The intelligence report. Jagex drops it every time and left it tradeable
     * so that a stronger player can fetch it for a weaker one, which is kept:
     * the drop is not gated on the killer's own progress.
     *
     * It goes on the killer's tile rather than Jonny's, because by the time this
     * runs the npc has already been unregistered and removed.
     */
    private void dropReport() {
        Player p = getOwner();
        world.registerItem(new Item(SCROLL, p.getX(), p.getY(), 1, p));
    }

    // ------------------------------------------------------ weapons store --

    private void openStoreDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != STORE_DOOR_X || door.getY() != STORE_DOOR_Y) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        if (count(STORE_KEY) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendMessage("You unlock the door");
        walkThrough(door);
    }

    /* ------------------------------------------------- the weaponsmaster --
     *
     * He is the phoenix gang's quartermaster and he answers to one question:
     * are you one of us. Three separate behaviours turn on it -- talking to
     * him, lifting a crossbow off him, and swinging at him -- and the black arm
     * gang is treated exactly as a stranger in all three. That last part is the
     * easy one to get wrong: a black arm member is a gang member, just not his.
     *
     * A character who finished the quest before GangMembership existed has no
     * record of which side they took, so they come through here as strangers.
     * That is no worse than the old behaviour, which attacked everyone, and it
     * puts itself right the moment either leader recruits them for Hero's quest.
     *
     * Dialogue from Transcript:Weaponsmaster. The attack refusal additionally
     * carries a packet capture, cited on the article.
     */

    /** In the phoenix gang, which is the only thing he cares about. */
    private boolean fellowPhoenix() {
        return phoenixMember();
    }

    /**
     * "Hey I don't know you / You're not meant to be here", and he swings.
     * Every stranger and every black arm member gets this.
     */
    private void talkToWeaponsmaster(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (!fellowPhoenix()) {
            c.player("Hello")
             .npc("Hey I don't know you")
             .npc("You're not meant to be here")
             .then(new Effect() {
                 public void run(Conversation c) {
                     // stop() first: attackPlayer will not touch a player the
                     // dialogue still has marked busy.
                     c.stop();
                     npc.attackPlayer(c.getPlayer());
                 }
             })
             .start();
            return;
        }
        c.npc("Hello Fellow phoenix")
         .npc("What are you after?")
         .options(new Choice("I'm after a weapon or two",
                             "I'm looking for treasure") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("Sure have a look around");
                 } else {
                     c.npc("We've not got any up here")
                      .npc("Go mug someone somewhere")
                      .npc("If you want some treasure");
                 }
             }
         })
         .start();
    }

    /**
     * Somebody has lifted one of the crossbows he is standing over.
     *
     * A phoenix member is grumbled at and nothing more; anyone else is called a
     * thief and attacked. RSCD attacked everyone silently, including the
     * player's own gang, and said neither line.
     *
     * The pickup itself succeeds either way, and has to: the quest sends a
     * phoenix member here for two of them, so a refusal that actually took the
     * crossbow back would leave that half of the quest unfinishable. "He won't
     * like you messing with that" is a warning, not a lock.
     */
    private void stealCrossbow() {
        Player p = getOwner();
        final Npc guard = world.getNpc(WEAPONSMASTER, p.getX() - 6, p.getX() + 6, p.getY() - 6, p.getY() + 6);
        if (guard == null) {
            return;
        }
        if (fellowPhoenix()) {
            // Straven is the phoenix leader, so the crossbows are the boss's.
            new Conversation(p, guard)
                .npc("Hey, that's Straven's")
                .npc("He won't like you messing with that")
                .start();
            return;
        }
        new Conversation(p, guard)
            .npc("Hey thief!")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    guard.attackPlayer(c.getPlayer());
                }
            })
            .start();
    }

    /**
     * A phoenix member cannot attack the weaponsmaster at all.
     *
     * The refusal is the player's own line, spoken above their head rather than
     * by the npc -- which is how the capture records it, and it is the only
     * reason this reads as a player line for a refusal nobody asked for.
     */
    public boolean refusesAttack(Npc npc) {
        if (npc.getID() != WEAPONSMASTER || !fellowPhoenix()) {
            return false;
        }
        new Conversation(getOwner(), npc)
            .player("Nope, I'm not going to attack a fellow gang member")
            .start();
        return true;
    }

    // ----------------------------------------------------- phoenix hideout --

    private void openHideoutDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != HIDEOUT_DOOR_X || door.getY() != HIDEOUT_DOOR_Y) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        if (!phoenixMember() && !completed()) {
            // Straven turns the player away himself if he is standing there,
            // which he always is -- he is the door's only guard.
            Npc straven = world.getNpc(STRAVEN, p.getX() - 5, p.getX() + 5, p.getY() - 5, p.getY() + 5);
            if (straven != null) {
                new Conversation(p, straven)
                    .npc("Heh you can't go in there")
                    .npc("Only authorised personnel of the VTAM corporation are allowed beyond this point")
                    .start();
            } else {
                p.getActionSender().sendMessage("The door is locked");
            }
            return;
        }
        p.getActionSender().sendMessage("The door is opened for you");
        p.getActionSender().sendMessage("You go through the door");
        walkThrough(door);
    }

    private void searchChest(GameObject chest) {
        Player p = getOwner();
        if (chest.getX() != CHEST_X || chest.getY() != CHEST_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (getStage() != PHOENIX || count(SHIELD_LEFT) > 0) {
            p.getActionSender().sendMessage("You search the chest but find nothing");
            return;
        }
        p.getInventory().add(new InvItem(SHIELD_LEFT, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find half of the shield of Arrav");
        setStage(PHOENIX_HALF);
    }

    // ---------------------------------------------------- blackarm hideout --

    private void searchCupboard(GameObject cupboard) {
        Player p = getOwner();
        if (cupboard.getX() != CUPBOARD_X || cupboard.getY() != CUPBOARD_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (getStage() != BLACKARM || count(SHIELD_RIGHT) > 0) {
            p.getActionSender().sendMessage("You search the cupboard but find nothing");
            return;
        }
        p.getInventory().add(new InvItem(SHIELD_RIGHT, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find half of the shield of Arrav");
        setStage(BLACKARM_HALF);
    }

    // ------------------------------------------------------------ curator --

    private void talkToCurator(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.npc("Welcome to the museum of Varrock");

        boolean left = count(SHIELD_LEFT) > 0;
        boolean right = count(SHIELD_RIGHT) > 0;

        if (left && right && !completed()) {
            c.player("I have retrieved the shield of Arrav and I would like to claim my reward")
             .npc("The shield of Arrav?")
             .npc("Let me see that")
             .npc("This is incredible")
             .npc("That shield has been missing for about twenty five years")
             .npc("Well give me the shield")
             .npc("And I'll write you out a certificate")
             .npc("Saying you have returned the shield")
             .npc("So you can claim your reward from the king")
             .player("Can I have two certificates?")
             .player("I needed significant help from a friend to get the shield")
             .player("We'll split the reward")
             .npc("Oh ok")
             .take(SHIELD_LEFT, 1)
             .take(SHIELD_RIGHT, 1)
             .give(new InvItem(CERTIFICATE, 1))
             .give(new InvItem(CERTIFICATE, 1))
             .npc("Take these to the king")
             .npc("And he'll pay you both handsomely")
             .then(new Effect() {
                 public void run(Conversation c) {
                     if (getStage() == PHOENIX_HALF) {
                         setStage(PHOENIX_CERT);
                     } else if (getStage() == BLACKARM_HALF) {
                         setStage(BLACKARM_CERT);
                     }
                 }
             })
             .start();
            return;
        }
        if (left || right) {
            c.player("I have half the shield of Arrav here")
             .player("Can I get a reward")
             .npc("Well it might be worth a small reward")
             .npc("The entire shield would me worth much much more")
             .player("Ok I'll hang onto it")
             .player("And see if I can find the other half")
             .start();
            return;
        }

        c.options(new Choice("Have you any interesting news?",
                             "Do you know where I could find any treasure?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("No, I'm only interested in old stuff");
                    return;
                }
                c.npc("This museum is full of treasures")
                 .player("No, I meant treasures for me")
                 .npc("Any treasures this museum knows about")
                 .npc("It aquires");
            }
        });
        c.start();
    }

    // --------------------------------------------------------------- king --

    private void talkToKing(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        boolean cert = count(CERTIFICATE) > 0;
        boolean half = count(SHIELD_LEFT) > 0 || count(SHIELD_RIGHT) > 0;

        if (cert && completed()) {
            c.player("Your majesty")
             .player("I have come to claim the reward")
             .player("For the return of the shield of Arrav")
             .npc("You have already claimed the reward")
             .npc("You can't claim it twice")
             .start();
            return;
        }
        if (cert && gotHalf()) {
            // The certificate is only good in the hands of somebody who worked
            // for it -- one of the two who actually fetched a half.
            c.player("Your majesty")
             .player("I have come to claim the reward")
             .player("For the return of the shield of Arrav")
             .npc("My goodness")
             .npc("This is the claim for a reward put out by my father")
             .npc("I never thought I'd see anyone claim this reward")
             .npc("I see you are claiming half the reward")
             .npc("So that would come to " + REWARD + " gold coins")
             .take(CERTIFICATE, 1)
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(FINISHED);
                 }
             })
             .start();
            return;
        }
        if (cert) {
            c.player("Your majesty")
             .player("I have come to claim the reward")
             .player("For the return of the shield of Arrav")
             .npc("The name on this certificate isn't yours!")
             .npc("I can't give you the reward")
             .npc("Unless you do the quest yourself")
             .start();
            return;
        }
        if (half) {
            c.player("Your majesty")
             .player("I have recovered the shield of Arrav")
             .player("I would like to claim the reward")
             .npc("The shield of Arrav, eh?")
             .npc("Yes, I do recall my father putting a reward out for that")
             .npc("Very well")
             .npc("Go get the authenticity of the shield verified")
             .npc("By the curator at the museum")
             .npc("And I will grant you your reward")
             .start();
            return;
        }
        c.player("Greetings, your majesty")
         .npc("Do you have anything of import to say?")
         .player("Not really")
         .npc("You will have to excuse me then")
         .npc("I am very busy")
         .npc("I have a kingdom to run")
         .start();
    }

    // -------------------------------------------------------------- doors --

    /**
     * Open a door for a moment and step the player to the far side of it.
     *
     * A door facing 0 stands between (x,y) and (x,y-1); one facing 1 stands
     * between (x,y) and (x-1,y). Both of this quest's doors face 0, but the
     * other case is here so the next one does not have to rediscover it.
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

    /** Swing the chest or cupboard shut for a moment. The map reopens it. */
    private void shut(GameObject container, int shutID, String what) {
        Player p = getOwner();
        p.getActionSender().sendMessage("You close the " + what);
        world.registerGameObject(new GameObject(container.getLocation(), shutID,
            container.getDirection(), container.getType()));
        world.delayedSpawnObject(container.getLoc(), 3000);
    }
}
