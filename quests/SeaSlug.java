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
 * Sea slug. Released 9 September 2002, written by Thomas Woode.
 *
 * Caroline's husband and son went out to the fishing platform a week ago and
 * nobody has heard from them since. The platform hauled up a net full of sea
 * slugs five days ago and the slugs have been riding the fishermen ever since.
 *
 * Four places, and the only way between them is Holgart's rowboat:
 *
 *     mainland   Caroline 455 (521,619), Holgart 456 (516,612),
 *                his rowboat 454 at (513,614)
 *     platform   Holgart 457 (493,615), bailey 460 (483,614),
 *                Platform Fisherman 462/463/464, ladder 458 (494,618)
 *     upper deck Kennith 461 (484,1560), fishing crane 453 (493,1558),
 *                loose panel, door 124 (487,1558)
 *     island     Holgart 458 (512,638), kent 459 (510,636), and the
 *                Damaged Rowboat 455 he was washed up in
 *
 * The three Holgarts are one man: 456 is him on shore, 457 waiting at the
 * platform and 458 standing on the island. Each answers for where he is, so the
 * boat rides are dialogue rather than scenery -- both rowboats are WalkTo-only
 * and always were.
 *
 * The three fishermen are one man too, in three shirts. 462 is yellow
 * (0xffcc00), 463 purple (0xbb00dd) and 464 grey (0xcccccc), which is how the
 * transcripts label them and how their lines were told apart.
 *
 * Getting there needs swamp paste, which nothing in this server could make.
 * Swamp tar out of the Lumbridge swamp mixed with flour is now handled in
 * InvUseOnItem, and item 784 has a cooking entry that warms it into 785. Both
 * are ordinary world content rather than quest content -- swamp paste is used
 * by three later quests as well -- so neither lives in here.
 *
 * Deviations:
 *
 *  - The crane does not turn. Jagex gave it three ids for three rotations
 *    (453, 459, 460) but only 453 was ever placed, so operating it lowers
 *    Kennith and otherwise just swings.
 *
 *  - Nothing physically moves Kennith. He is an NpcLoc like any other and
 *    unregistering him would take him from every other player on the platform,
 *    so the crane hands him to Holgart in the message log and the quest
 *    remembers it. Same reasoning as the sheep in Sheep herder.
 *
 *  - The fishermen throw the player back down the ladder rather than doing it
 *    with a real fight, and the fall costs a fifth of their hits. Jagex's
 *    version knocked off a flat amount; the fraction is a guess and it will
 *    never take the last hitpoint.
 *
 * Dialogue is Jagex's, from the recorded transcripts. The Holgart and Kent
 * transcripts survive without speaker labels, so who says which line was read
 * back off the alternation and the sense; everything else is labelled.
 */
public class SeaSlug extends Quest {

    public final static int UID = Quests.SEA_SLUG;

    private static final int CAROLINE = 455;
    private static final int HOLGART_SHORE = 456;
    private static final int HOLGART_PLATFORM = 457;
    private static final int HOLGART_ISLAND = 458;
    private static final int KENT = 459, BAILEY = 460, KENNITH = 461;
    private static final int YELLOW = 462, PURPLE = 463, GREY = 464;

    private static final int LADDER = 458, LADDER_X = 494, LADDER_Y = 618;
    private static final int CRANE = 453, CRANE_X = 493, CRANE_Y = 1558;
    private static final int PANEL = 124;

    private static final int TORCH = 773, LIT_TORCH = 774;
    private static final int DAMP_STICKS = 776, DRY_STICKS = 777;
    private static final int GLASS = 778, PEARLS = 779, PASTE = 785;

    private static final int SHORE_X = 516, SHORE_Y = 613;
    private static final int PLATFORM_X = 493, PLATFORM_Y = 616;
    private static final int ISLAND_X = 512, ISLAND_Y = 637;
    private static final int FLOOR = 944;

    private static final int FIREMAKING = 11, FIREMAKING_LEVEL = 30;
    private static final int FISHING = 10, HITS = 3;

    private static final int STARTED = 1;
    private static final int PASTED = 2;    /* the rowboat is patched */
    private static final int MET_BOY = 4;
    private static final int MET_KENT = 8;
    private static final int LIT = 16;      /* a torch has been lit at least once */
    private static final int BROKEN = 32;   /* the loose panel is open */
    private static final int RESCUED = 64;
    private static final int FINISHED = 127;

    public SeaSlug(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Sea Slug");
        setFinalStage(FINISHED);
        associateNpc(CAROLINE);
        associateNpc(HOLGART_SHORE);
        associateNpc(HOLGART_PLATFORM);
        associateNpc(HOLGART_ISLAND);
        associateNpc(KENT);
        associateNpc(BAILEY);
        associateNpc(KENNITH);
        associateNpc(YELLOW);
        associateNpc(PURPLE);
        associateNpc(GREY);
        /* Ladder 458 and crane 453 stand nowhere else in the world, so the id
           is the placement. Door 124 likewise: its one wall is this one, and
           the object of the same number in the Taverley dungeon comes out of
           GameObjectDef, which is a different table. */
        associateObject(LADDER);
        associateObject(CRANE);
        associateDoor(PANEL);
        associateItem(DAMP_STICKS);
        associateItem(GLASS);
        associateItem(DRY_STICKS);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Something strange is happening on the fishing platform. Missing fishermen and the presence of dozens of strange sea creatures gives cause for concern. Investigate the platform, discover the truth before it's too late.");
        setStartPoint("East of Ardounge");
        setSpeakTo("Caroline");
        setMissionLength("Short");
        /* Enforced in rub(): the torch cannot be lit below this level. */
        requireLevel(FIREMAKING, FIREMAKING_LEVEL);
        rewardExp(FISHING, 175, 200);
        rewardOther("Oyster pearls from Caroline");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Sea slug quest");
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    /** Queue a boat ride as the last thing a conversation does. */
    private Conversation sail(Conversation c, final int x, final int y) {
        return c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(x, y, false);
            }
        });
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger != QuestTrigger.NPC_TALK && trigger != QuestTrigger.ITEM_ON_NPC) {
                return;
            }
            switch (npc.getID()) {
                case CAROLINE:      caroline(npc); return;
                case HOLGART_SHORE: holgartShore(npc, used); return;
                case HOLGART_PLATFORM: holgartPlatform(npc); return;
                case HOLGART_ISLAND:   holgartIsland(npc); return;
                case KENT:    kent(npc); return;
                case BAILEY:  bailey(npc); return;
                case KENNITH: kennith(npc); return;
                default:      fisherman(npc); return;
            }
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1) {
                panel();
            } else if (trigger == QuestTrigger.OBJECT_ACT1 && object.getID() == LADDER) {
                ladder();
            } else if (trigger == QuestTrigger.OBJECT_ACT1) {
                crane();
            }
            return;
        }
        if (entity instanceof InvItem) {
            InvItem item = (InvItem) entity;
            if (trigger == QuestTrigger.ITEM_COMMAND && item.getID() == DRY_STICKS) {
                rub();
            } else if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                dry(item, used);
            }
        }
    }

    // ------------------------------------------------------------ Caroline --

    private void caroline(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("hello traveler")
                .npc("how are you?")
                .player("not bad thanks, yourself?")
                .npc("i'm good")
                .npc("busy as always looking after kent and kennith but no complaints")
                .start();
            return;
        }
        if (has(RESCUED)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("brave adventurer you've returned")
                .npc("kennith told me about the strange going ons on the platform")
                .player("i had no idea it was so serious")
                .npc("i could have lost my son and my husband if it wasn't for you")
                .player("we found kent stranded on a island")
                .npc("yes, holgart told me and sent a rescue party out")
                .npc("kent's back at home now, resting with kennith")
                .npc("i don't think he'll be doing any fishing for a while")
                .npc("here, take these oyster pearls as a reward")
                .give(new InvItem(PEARLS, 1))
                .npc("they're worth a fair bit")
                .npc("and can be used to make lethal crossbow bolts")
                .player("thanks")
                .npc("thank you")
                .npc("take care of yourself adventurer")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(FINISHED);
                    }
                })
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello caroline")
                .npc("brave adventurer have you any news about my son and his father?")
                .player("i'm working on it now caroline")
                .npc("please bring them back safe and sound")
                .player("i'll do my best")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("is there any chance you could help me?")
            .player("what's wrong?")
            .npc("it's my husband, he works on a fishing platform")
            .npc("once a month he takes our son kennith out with him")
            .npc("they usually write to me regularly but i've heard nothing all week")
            .npc("it's very strange")
            .player("maybe the post was lost!")
            .npc("maybe, but no one's heard from the other fishermen on the platform")
            .npc("their families are becoming quite concerned")
            .npc("is there any chance you could visit the platform and find out what's going on?")
            .options(new Choice("i suppose so, how do i get there?", "i'm sorry i'm too busy") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("thats a shame")
                         .player("bye")
                         .npc("bye");
                        return;
                    }
                    c.npc("that's very good of you traveller")
                     .npc("my friend holgart will take you there")
                     .player("okay i'll go and see if they're ok")
                     .npc("i will reward you for your time")
                     .npc("and it'll give me great piece of mind")
                     .npc("to know kennith and my husband kent are safe")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(STARTED);
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------- Holgart --

    /**
     * The ride out. Offered once the boat is patched, and worded the way Jagex
     * worded it: land lover before the quest is over, m'hearty after.
     */
    private void offerRide(Conversation c) {
        c.options(new Choice("okay, lets do it", "i'll come back later") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("okay then")
                     .npc("i'll wait here for you");
                    return;
                }
                c.npc("hold on tight");
                sail(c, PLATFORM_X, PLATFORM_Y);
            }
        });
    }

    private void holgartShore(Npc npc, InvItem used) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("well hello m'laddy")
                .npc("beautiful day isn't it")
                .player("not bad i suppose")
                .npc("just smell that sea air... beautiful")
                .player("hmm...lovely!")
                .start();
            return;
        }
        if (completed()) {
            Conversation c = new Conversation(p, npc)
                .player("hello again holgart")
                .npc("well hello again m'hearty")
                .npc("your land loving legs getting bored?")
                .npc("fancy some cold and wet underfoot?")
                .player("pardon")
                .npc("fancy going out to sea?");
            offerRide(c);
            c.start();
            return;
        }
        if (has(PASTED)) {
            Conversation c = new Conversation(p, npc)
                .player("hello holgart")
                .npc("hello again land lover")
                .npc("there's some strange going's on, on that platform i tell you")
                .options(new Choice("will you take me there?", "i'm keeping away from there") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            c.npc("fair enough m'hearty");
                            return;
                        }
                        c.npc("of course m'hearty")
                         .npc("if that's what you want");
                        sail(c, PLATFORM_X, PLATFORM_Y);
                    }
                }.says(0, "will you take me back there?"));
            c.start();
            return;
        }
        if (holding(PASTE)) {
            Conversation c = new Conversation(p, npc)
                .player("hello holgart")
                .npc("hello m'hearty")
                .npc("did you manage to make some swamp paste?")
                .player("yes i have some here")
                .take(PASTE, 1)
                .npc("superb, this looks great")
                .npc("that's done the job, now we can go")
                .npc("jump aboard")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(PASTED);
                    }
                });
            offerRide(c);
            c.start();
            return;
        }
        /* Whether he has explained the paste yet is worth a different opening
           and nothing else, so it rides on a session flag rather than on a
           stage bit -- forgetting it over a logout costs the player one repeat
           of a speech they have already read. */
        if (p.getFlag("slug.asked") > 0) {
            new Conversation(p, npc)
                .player("hello holgart")
                .npc("hello m'hearty")
                .npc("did you manage to make some swamp paste?")
                .player("i'm afraid not")
                .npc("to make it you need swamp tar mixed with flour heated over a fire")
                .npc("the only supply of swamp tar is in the swamps below lumbridge")
                .npc("i can't fix the row boat without it")
                .player("ok, i'll try to find some")
                .start();
            return;
        }
        p.setFlag("slug.asked", 1);
        new Conversation(p, npc)
            .player("hello")
            .npc("hello m'hearty")
            .player("i would like a ride on your boat to the fishing platform")
            .npc("i'm afraid it isn't sea worthy, it's full of holes")
            .npc("to fill the holes i'll need some swamp paste")
            .player("swamp paste?")
            .npc("yes, swamp tar mixed with flour heated over a fire")
            .player("where can i find swamp tar?")
            .npc("unfortunately the only supply of swamp tar is in the swamps below lumbridge")
            .npc("it's too far for an old man like me to travel")
            .npc("if you can make me some swamp paste i will give you a ride on my boat")
            .player("i'll see what i can do")
            .start();
    }

    private void holgartPlatform(Npc npc) {
        Player p = getOwner();
        if (has(RESCUED) && !completed()) {
            Conversation c = new Conversation(p, npc)
                .npc("did you get the kid back to shore?")
                .player("yes, he's safe and sound with his parents")
                .npc("your turn to return to land now adventurer")
                .player("looking forward to it");
            sail(c, SHORE_X, SHORE_Y);
            c.start();
            return;
        }
        if (has(MET_BOY) && !has(MET_KENT)) {
            Conversation c = new Conversation(p, npc)
                .player("holgart, something strange is going on here")
                .npc("you're telling me")
                .npc("none of the sailors seem to remember who i am")
                .player("apparently kenniths father left for help a couple of days ago")
                .npc("that's a worry, no ones heard from him on shore")
                .npc("come on, we better go look for him");
            sail(c, ISLAND_X, ISLAND_Y);
            c.start();
            return;
        }
        new Conversation(p, npc)
            .player("hey holgart")
            .npc("have you had enough of this place yet?")
            .npc("it's scaring me")
            .options(new Choice("no, i'm going to stay a while", "okay, lets go back") {
                public void picked(int option, Conversation c) {
                    // Option 0 is staying: the ride home is option 1. This was
                    // inverted, so "okay, lets go back" answered and never sailed.
                    if (option == 0) {
                        c.npc("okay, you're the boss");
                        return;
                    }
                    c.npc("okay m'hearty jump on");
                    sail(c, SHORE_X, SHORE_Y);
                }
            })
            .start();
    }

    private void holgartIsland(Npc npc) {
        Player p = getOwner();
        if (has(MET_KENT)) {
            Conversation c = new Conversation(p, npc)
                .player("we had better get back to the platform")
                .player("and see what's going on")
                .npc("you're right")
                .npc("it all sounds pretty creepy");
            sail(c, PLATFORM_X, PLATFORM_Y);
            c.start();
            return;
        }
        new Conversation(p, npc)
            .player("where are we?")
            .npc("someway of mainland still")
            .npc("you better see if old matey's okay")
            .start();
    }

    // ---------------------------------------------------------------- Kent --

    private void kent(Npc npc) {
        Player p = getOwner();
        if (has(MET_KENT)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("oh my")
                .npc("i must get back to shore")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("oh thank Saradomin")
            .npc("i thought i would be left out here forever")
            .player("your wife sent me out to find you and your boy")
            .player("kennith's fine he's on the platform")
            .npc("i knew the row boat wasn't sea worthy")
            .npc("i couldn't risk bringing him along but you must get him of that platform")
            .player("what's going on on there?")
            .npc("five days ago we pulled in huge catch")
            .npc("as well as fish we caught small slug like sea creatures, hundreds of them")
            .npc("that's when the fishermen began to act strange")
            .npc("it was the sea slugs, they attach themselves to your body")
            .npc("and somehow take over the mind of the carrier")
            .npc("i told Kennith to hide until i returned but i was washed up here")
            .npc("please go back and get my boy")
            .npc("you can send help for me later")
            .npc("traveler wait!")
            .npc("a few more minutes and that thing would have full control you body")
            .player("yuck..thanks kent")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(MET_KENT);
                }
            })
            .start();
    }

    // -------------------------------------------------------------- Bailey --

    private void bailey(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello bailey")
                .npc("well hello again traveler")
                .npc("what brings you back out here")
                .player("just looking around")
                .npc("well don't go touching any of those blasted slugs")
                .start();
            return;
        }
        if (has(RESCUED)) {
            new Conversation(p, npc)
                .npc("hello again")
                .npc("i saw you managed to get kennith of the platform")
                .npc("well done, he wasn't safe around these slugs")
                .player("are you going to come back with us?")
                .npc("no, these fishermen are my friends")
                .npc("i'm sure they can be saved")
                .npc("i'm going to stay and try to get rid of all these slugs")
                .player("you're braver than most")
                .player("take care of yourself bailey")
                .npc("you to traveler")
                .start();
            return;
        }
        if (!has(MET_KENT)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("well hello there")
                .npc("what are you doing here?")
                .player("i'm trying to find out what happened to a boy named kennith")
                .npc("oh, you mean kent's son")
                .npc("he's around somewhere, probably hiding")
                .player("hiding from what?")
                .npc("haven't you seen all those things out there?")
                .player("the sea slugs?")
                .npc("ever since we pulled up that haul something strange has been going on")
                .npc("the fishermen spend all day pulling in hauls of fish")
                .npc("only to throw back the fish and keep those nasty sea slugs")
                .npc("what am i supposed to do with those")
                .npc("i haven't figured out how to kill one yet")
                .npc("if i put them near the stove they squirm and jump away")
                .player("i doubt they would taste too good")
                .start();
            return;
        }
        if (holding(LIT_TORCH)) {
            new Conversation(p, npc)
                .player("i've managed to light the torch")
                .npc("well done traveler")
                .npc("you better get kennith out of here soon")
                .npc("the fishermen are becoming stranger by the minute")
                .npc("and they keep pulling up those blasted slugs")
                .start();
            return;
        }
        if (holding(TORCH)) {
            if (has(LIT)) {
                return;   /* "Nothing occurs." */
            }
            new Conversation(p, npc)
                .player("i better figure a way to light this torch")
                .start();
            return;
        }
        if (has(LIT)) {
            new Conversation(p, npc)
                .player("i've managed to lose my torch")
                .npc("that was silly, fortunately i have another")
                .npc("here, take it")
                .give(new InvItem(TORCH, 1))
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("oh thank god it's you")
            .npc("they've all gone mad i tell you")
            .npc("one of the fishermen tried to throw me into the sea")
            .player("they're all being controlled by the sea slugs")
            .npc("i figured as much")
            .player("i need to get kennith of this platform but i can't get past the fishermen")
            .npc("the sea slugs are scared of heat")
            .npc("i figured that out when i tried to cook them")
            .npc("here")
            .npc("i doubt the fishermen will come near you if you can get this torch to light")
            .give(new InvItem(TORCH, 1))
            .npc("the only problem is all the wood and flint is damp")
            .npc("i can't light a thing")
            .player("i better figure a way to light this torch")
            .start();
    }

    // ------------------------------------------------------------- Kennith --

    private void kennith(Npc npc) {
        Player p = getOwner();
        if (has(RESCUED) || completed()) {
            return;
        }
        if (!has(MET_BOY)) {
            new Conversation(p, npc)
                .player("are you okay young one?")
                .npc("no i want my daddy")
                .player("Where is your father?")
                .npc("he went to get help days ago")
                .npc("the nasty fisher men tried to throw me and daddy into the sea")
                .npc("so he told me to hide in here")
                .player("that's good advice")
                .player("you stay here and i'll go try and find your father")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(MET_BOY);
                    }
                })
                .start();
            return;
        }
        if (!has(MET_KENT)) {
            new Conversation(p, npc)
                .player("are you okay?")
                .npc("i want to see daddy")
                .player("i'm working on it")
                .start();
            return;
        }
        if (!has(BROKEN)) {
            new Conversation(p, npc)
                .player("hello kennith")
                .player("are you okay?")
                .npc("no i want my daddy")
                .player("you'll be able to see him soon")
                .player("first we need to get you back to land")
                .player("come with me to the boat")
                .npc("no")
                .player("what, why not?")
                .npc("i'm scared of those nasty sea slugs")
                .npc("i won't go near them")
                .player("okay, you wait here and i'll figure another way to get you out")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("kennith i've made an opening in the wall")
            .player("you can come out there")
            .npc("are their any sea slugs on the other side?")
            .player("not one")
            .npc("how will i get down stairs")
            .player("i'll figure that out in a moment")
            .npc("okay, when you have i'll come out")
            .start();
    }

    // ----------------------------------------------------------- fishermen --

    private void fisherman(Npc npc) {
        Player p = getOwner();
        if (npc.getID() == YELLOW) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("must find family")
                .player("what?")
                .npc("soon we'll all be together")
                .player("are you okay?")
                .npc("must find family")
                .npc("they're all under the blue")
                .npc("deep deep under the blue")
                .player("ermm..i'll leave you to it then")
                .start();
            return;
        }
        /* The purple one says "leave of face the deep blue" and the grey one
           "leave or face the deep blue". Jagex's typo, kept. */
        new Conversation(p, npc)
            .player("hello")
            .npc("keep away human")
            .npc(npc.getID() == PURPLE ? "leave of face the deep blue"
                                       : "leave or face the deep blue")
            .player("pardon?")
            .npc("you'll all end up in the blue")
            .npc("deep deep under the blue")
            .start();
    }

    // -------------------------------------------------------------- torch --

    /**
     * Broken glass on the damp sticks. The glass focuses the sun onto them --
     * it is not consumed, and the platform's one pane can dry every set of
     * sticks a player carries back.
     */
    private void dry(InvItem first, InvItem second) {
        Player p = getOwner();
        int a = first.getID(), b = second == null ? -1 : second.getID();
        if (!((a == GLASS && b == DAMP_STICKS) || (a == DAMP_STICKS && b == GLASS))) {
            /* Every pairing among the three claimed items lands here -- damp
               sticks on dry ones included, which would otherwise read as glass
               on damp sticks and hand out a free set. */
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(DAMP_STICKS, 1);
        p.getInventory().add(new InvItem(DRY_STICKS, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You use the glass to focus the sun on the sticks");
        p.getActionSender().sendMessage("The sticks dry out");
    }

    /** "rub together" on the dry sticks. */
    private void rub() {
        Player p = getOwner();
        if (p.getCurStat(FIREMAKING) < FIREMAKING_LEVEL) {
            p.getActionSender().sendMessage("@gry@ You need a firemaking level of "
                + FIREMAKING_LEVEL + " to light these");
            return;
        }
        if (!holding(TORCH)) {
            if (holding(LIT_TORCH)) {
                p.getActionSender().sendMessage("Your torch is already lit");
            } else {
                p.getActionSender().sendMessage("You rub the sticks together and they smoulder");
                p.getActionSender().sendMessage("You have nothing to light with them");
            }
            return;
        }
        p.getInventory().remove(DRY_STICKS, 1);
        p.getInventory().remove(TORCH, 1);
        p.getInventory().add(new InvItem(LIT_TORCH, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You rub the sticks together");
        p.getActionSender().sendMessage("@gre@The torch catches light");
        set(LIT);
    }

    // ------------------------------------------------------------- scenery --

    private void ladder() {
        Player p = getOwner();
        if (has(MET_KENT) && !has(RESCUED) && !holding(LIT_TORCH)) {
            p.getActionSender().sendMessage("The fishermen at the top of the ladder see you coming");
            p.getActionSender().sendMessage("@red@They throw you back down");
            int damage = Math.max(1, p.getCurStat(HITS) / 5);
            p.setCurStat(HITS, Math.max(1, p.getCurStat(HITS) - damage));
            p.getActionSender().sendStat(HITS);
            return;
        }
        p.teleport(p.getX(), p.getY() + FLOOR, false);
    }

    private void panel() {
        Player p = getOwner();
        if (has(BROKEN)) {
            p.getActionSender().sendMessage("The panel is already broken open");
            return;
        }
        if (!has(MET_KENT)) {
            p.getActionSender().sendMessage("You have no reason to break the platform apart");
            return;
        }
        p.getActionSender().sendMessage("You kick the worn panel");
        p.getActionSender().sendMessage("@gre@It splinters and leaves an opening in the wall");
        set(BROKEN);
    }

    private void crane() {
        Player p = getOwner();
        if (!has(BROKEN) || has(RESCUED)) {
            p.getActionSender().sendMessage("You operate the crane");
            p.getActionSender().sendMessage("The arm swings out over the water");
            return;
        }
        p.getActionSender().sendMessage("You operate the crane");
        p.getActionSender().sendMessage("@yel@jump on kennith!");
        p.getActionSender().sendMessage("@gre@Kennith climbs out through the broken panel");
        p.getActionSender().sendMessage("@gre@The crane lowers him down to Holgart's boat");
        set(RESCUED);
    }
}
