import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Sheep herder. Released 15 August 2002, written by Thomas Woode.
 *
 * Four plague sheep have got out of West Ardougne. Councillor Halgrive wants
 * them driven into Farmer Brumty's enclosure, poisoned, and their remains
 * burnt in the cattle furnace before the whole city catches it.
 *
 *     Councillor Halgrive  npc 436, (585,603), outside the church
 *     Doctor orbon         npc 435, (578,591), inside the chapel
 *     Farmer brumty        npc 434, (595,539), his farm
 *     the sheep            npcs 430 (578,565), 431 (581,565),
 *                               432 (622,529), 433 (603,586)
 *     cattle furnace       object 444, (593,547), in the shed in the enclosure
 *     cattle prod          item 757, ground spawn at (597,543)
 *     poisoned feed        item 759, from Halgrive
 *     the clothes          items 760 jacket and 761 trousers, 100 coins from Orbon
 *     the remains          items 758, 762, 763 and 764 -- one per sheep
 *
 * Each sheep has three spawns of its own, one per tile, so each of the four
 * stands in a little row of three. Prodding one drives it into the enclosure;
 * poisoned feed puts it to sleep and leaves its remains behind; the remains go
 * in the furnace. Progress is a bit set in the stage, one bit per sheep burnt,
 * because there are four independent things to remember and stages persist
 * where flags do not.
 *
 * Whether a sheep is penned is not remembered anywhere -- it is read off the
 * sheep, which is standing either in the enclosure or out on the grass. That
 * is the honest way round: the sheep are ordinary world npcs shared by
 * everybody, exactly as they were in 2002, and one player driving a sheep in
 * is one player driving it in for everyone. The quest was disliked for that
 * and it is left alone.
 *
 * Deviations:
 *
 *  - The enclosure is taken to be the four tiles either side of (594,546),
 *    which is where a prodded sheep lands. Its real boundary is a fence in the
 *    landscape rather than an object in GameObjectLoc -- the only things in the
 *    object file are the gate at (588,540) and the shed door at (593,545) --
 *    so there is no fence for a quest to read. The gate, the furnace and the
 *    cattle prod's spawn all fall inside the box, which is as close as the
 *    data gets.
 *
 *    The gate itself is not this quest's. It is an ordinary farm gate that
 *    anyone may open, so it lives with the rest of them in ObjectAction rather
 *    than being claimed here -- but it was missing from that list and answered
 *    "Nothing interesting happens" to everybody, which put the cattle prod out
 *    of reach and made the quest unfinishable. Added there, not here.
 *
 *  - A poisoned sheep goes home rather than dying. Unregistering it would take
 *    it away from every other player until the server restarted, since these
 *    are NpcLoc spawns and not npcs this quest created. It falls asleep, its
 *    remains are handed over, and it wakes up back where it started.
 *
 *  - There is no ninety-second timer on a penned sheep. Losing it again was a
 *    punishment, and putting one in would mean a shared timer on a shared npc
 *    that some other player is also herding.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class SheepHerder extends Quest {

    public final static int UID = Quests.SHEEP_HERDER;

    private static final int HALGRIVE = 436, ORBON = 435, BRUMTY = 434;

    private static final int STARTED = 1;
    private static final int[] BURNT = { 2, 4, 8, 16 };
    private static final int PAID = 32;
    private static final int FINISHED = STARTED | 2 | 4 | 8 | 16 | PAID;
    /** Every sheep burnt and Halgrive not yet told. */
    private static final int ALL_BURNT = FINISHED - PAID;

    private static final int[] SHEEP = { 430, 431, 432, 433 };
    private static final int[][] HOME = { { 578, 565 }, { 581, 565 },
                                          { 622, 529 }, { 603, 586 } };
    private static final int[] REMAINS = { 758, 762, 763, 764 };

    private static final int PEN_X = 594, PEN_Y = 546, PEN = 4;
    private static final int FURNACE = 444;
    private static final int FURNACE_X = 593, FURNACE_Y = 547;

    private static final int PROD = 757, FEED = 759;
    private static final int JACKET = 760, TROUSERS = 761;
    private static final int COINS = 10, SUIT_PRICE = 100, REWARD = 3100;

    /** Prods needed before a sheep gives up and runs for the gate. */
    private static final int PRODS = 3;

    public SheepHerder(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Sheep herder");
        setFinalStage(FINISHED);
        associateNpc(HALGRIVE);
        associateNpc(ORBON);
        associateNpc(BRUMTY);
        for (int i = 0; i < SHEEP.length; i++) {
            associateNpc(SHEEP[i]);
        }
        associateObject(FURNACE);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Some plague infected sheep have escaped into East Ardounge. They must be found and disposed off before the whole town is infected, time is of the essence.");
        setStartPoint("East Ardounge");
        setSpeakTo("Councillor Halgrive");
        setMissionLength("Short");
        rewardItem(COINS, REWARD);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Sheep herder quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            furnace((GameObject) entity, used);
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        int sheep = indexOf(npc.getID());
        if (sheep > -1) {
            if (trigger == QuestTrigger.ITEM_ON_NPC) {
                useOnSheep(npc, sheep, used);
            } else {
                getOwner().getActionSender().sendMessage("The sheep says nothing");
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        if (npc.getID() == HALGRIVE) {
            halgrive(npc);
        } else if (npc.getID() == ORBON) {
            orbon(npc);
        } else {
            brumty(npc);
        }
    }

    private static int indexOf(int npcId) {
        for (int i = 0; i < SHEEP.length; i++) {
            if (SHEEP[i] == npcId) {
                return i;
            }
        }
        return -1;
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private boolean suited() {
        return getOwner().getInventory().wielding(JACKET)
            && getOwner().getInventory().wielding(TROUSERS);
    }

    // ------------------------------------------------------------ sheep --

    private void useOnSheep(Npc npc, int sheep, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        if (!questStarted() || completed()) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!suited()) {
            p.getActionSender().sendMessage("You aren't going near that sheep");
            p.getActionSender().sendMessage("without your protective clothing on");
            return;
        }
        if (used.getID() == PROD) {
            prod(npc, sheep);
        } else if (used.getID() == FEED) {
            poison(npc, sheep);
        } else {
            p.getActionSender().sendMessage("Nothing interesting happens");
        }
    }

    /**
     * A few prods and the sheep bolts for the enclosure. The count is kept on
     * the player rather than on the sheep: two people herding the same animal
     * each get their own patience, which is kinder than sharing one counter.
     */
    private void prod(Npc npc, int sheep) {
        Player p = getOwner();
        if (has(BURNT[sheep])) {
            p.getActionSender().sendMessage("You have already dealt with this one");
            return;
        }
        if (penned(npc)) {
            p.getActionSender().sendMessage("The sheep is already in the enclosure");
            p.getActionSender().sendMessage("Now use the poisoned feed on it");
            return;
        }
        String key = "sheep.prod." + SHEEP[sheep];
        int prods = p.getFlag(key) + 1;
        if (prods < PRODS) {
            p.setFlag(key, prods);
            p.getActionSender().sendMessage("You prod the sheep");
            p.getActionSender().sendMessage("It bleats and shuffles away");
            return;
        }
        p.setFlag(key, 0);
        p.getActionSender().sendMessage("You prod the sheep");
        p.getActionSender().sendMessage("@gre@It bolts into the enclosure");
        npc.setLocation(Point.location(PEN_X, PEN_Y), true);
    }

    private void poison(Npc npc, int sheep) {
        Player p = getOwner();
        if (has(BURNT[sheep])) {
            p.getActionSender().sendMessage("You have already dealt with this one");
            return;
        }
        if (!penned(npc)) {
            p.getActionSender().sendMessage("You should get it into the enclosure first");
            return;
        }
        if (p.getInventory().countId(REMAINS[sheep]) > 0) {
            p.getActionSender().sendMessage("You already have this sheep's remains");
            return;
        }
        p.getInventory().remove(FEED, 1);
        p.getInventory().add(new InvItem(REMAINS[sheep], 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("The sheep eats the feed");
        p.getActionSender().sendMessage("It peacefully falls asleep");
        p.getActionSender().sendMessage("You gather up the remains");
        npc.setLocation(Point.location(HOME[sheep][0], HOME[sheep][1]), true);
    }

    private boolean penned(Npc npc) {
        return Math.abs(npc.getX() - PEN_X) <= PEN
            && Math.abs(npc.getY() - PEN_Y) <= PEN;
    }

    // ---------------------------------------------------------- furnace --

    private void furnace(GameObject object, InvItem used) {
        Player p = getOwner();
        if (object.getX() != FURNACE_X || object.getY() != FURNACE_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (used == null) {
            p.getActionSender().sendMessage("The furnace roars away");
            return;
        }
        for (int i = 0; i < REMAINS.length; i++) {
            if (used.getID() != REMAINS[i]) {
                continue;
            }
            if (!suited()) {
                p.getActionSender().sendMessage("You aren't handling that");
                p.getActionSender().sendMessage("without your protective clothing on");
                return;
            }
            p.getInventory().remove(REMAINS[i], 1);
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You throw the remains into the furnace");
            p.getActionSender().sendMessage("They burn away to nothing");
            if (!has(BURNT[i])) {
                setStage((questStarted() ? getStage() : 0) | BURNT[i]);
            }
            if (getStage() == ALL_BURNT) {
                p.getActionSender().sendMessage("@gre@That was the last of them");
                p.getActionSender().sendMessage("@gre@You should tell Councillor Halgrive");
            }
            return;
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }

    // ------------------------------------------------------------ talkers --

    private void halgrive(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello again halgrive")
                .npc("well hello again traveller")
                .npc("how are you")
                .player("good thanks and yourself?")
                .npc("much better now i don't have to worry about those sheep")
                .start();
            return;
        }
        if (getStage() == ALL_BURNT) {
            new Conversation(p, npc)
                .npc("have you managed to dispose of those four sheep?")
                .player("yes i have")
                .npc("here take one hundred coins to cover the price of your protective clothing")
                .npc("and another three thousand for your efforts")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage((questStarted() ? getStage() : 0) | PAID);
                    }
                })
                .start();
            return;
        }
        if (questStarted()) {
            Conversation c = new Conversation(p, npc);
            if (!suited()) {
                c.npc("please find those four sheep as soon as you can")
                 .npc("every second counts");
                if (p.getInventory().countId(FEED) < 1) {
                    c.player("Some more sheep poison might be useful")
                     .give(new InvItem(FEED, 1));
                }
            } else {
                c.npc("have you managed to dispose of those four sheep?")
                 .player("erm not quite")
                 .npc("not quite's not good enough")
                 .npc("all four sheep must be captured, slain and their remains burnt")
                 .player("ok i'll get to it");
                if (p.getInventory().countId(FEED) < 1) {
                    c.give(new InvItem(FEED, 1));
                }
            }
            c.start();
            return;
        }
        new Conversation(p, npc)
            .player("how are you?")
            .npc("I've been better")
            .options(new Choice("What's wrong?", "that's life for you") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("a plague has spread over west ardounge")
                     .npc("apparently it's reasonably contained")
                     .npc("but four infected sheep have escaped")
                     .npc("they're roaming free in and around east ardounge")
                     .npc("the whole city could be infected in days")
                     .npc("i need someone to gather the sheep")
                     .npc("herd them into a safe enclosure")
                     .npc("then kill the sheep")
                     .npc("their remains will also need to be disposed of safely in a furnace")
                     .options(new Choice("i can do that for you", "that's not a job for me") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("fair enough, it's not nice work");
                                 return;
                             }
                             c.npc("good, the enclosure is to the north of the city")
                              .npc("On farmer Brumty's farm")
                              .npc("the four sheep should still be close to it")
                              .npc("before you go into the enclosure")
                              .npc("make sure you have protective clothing on")
                              .npc("otherwise you'll catch the plague")
                              .player("where do I get protective clothing?")
                              .npc("Doctor Orbon wears it when trying to save the infected")
                              .npc("you'll find him in the chapel")
                              .npc("take this poisoned animal feed")
                              .npc("give it to the four sheep and they'll peacefully fall asleep")
                              .give(new InvItem(FEED, 1))
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      setStage(STARTED);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    /**
     * Doctor Orbon. He sells the one suit he owns, and goes on selling it --
     * losing the clothes and having to buy another set is in the transcript.
     */
    private void orbon(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("well hello again")
                .npc("i was so relieved when i heard you disposed of the plagued sheep")
                .npc("Now the town is safe")
                .start();
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("how do you feel?")
                .npc("no heavy flu or the shivers?")
                .player("no, i'm fine")
                .npc("how about nightmares?")
                .npc("have you had any problems with really scary nightmares?")
                .player("no, not since i was young")
                .npc("good good")
                .npc("have to be carefull nowadays")
                .npc("the plague spreads faster than a common cold")
                .options(new Choice("the plague? tell me more", "ok I'll be careful") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            c.npc("you do that traveller");
                            return;
                        }
                        c.npc("the virus came from the west and is deadly")
                         .player("what are the symtoms?")
                         .npc("watch out for abnormal nightmares and strong flu symtoms")
                         .npc("when you find a thick black liquid dripping from your nose and eyes")
                         .npc("then no one can save you");
                    }
                })
                .start();
            return;
        }
        boolean carrying = p.getInventory().countId(JACKET) > 0
                        && p.getInventory().countId(TROUSERS) > 0;
        Conversation c = new Conversation(p, npc);
        if (carrying) {
            c.npc("have you managed to get rid of those sheep?")
             .player("not yet")
             .npc("you must hurry")
             .npc("they could have the whole town infected in days")
             .start();
            return;
        }
        if (p.getFlag("herder.bought") > 0) {
            c.npc("have you managed to get rid of those sheep?")
             .player("not yet")
             .npc("you must hurry")
             .npc("they could have the whole town infected in days")
             .npc("I see you don't have your protective clothing with you")
             .npc("Would you like to buy some more?")
             .npc("Same price as before");
            sell(c);
            c.start();
            return;
        }
        c.player("hi doctor")
         .player("I need to aquire some protective clothing")
         .player("so i can recapture some escaped sheep who have the plague")
         .npc("I'm afraid i only have one suit")
         .npc("Which i made to keep myself safe from infected patients")
         .npc("I could sell it to you")
         .npc("then i could make myself another")
         .npc("hmmm..i'll need at least 100 gold coins");
        sell(c);
        c.start();
    }

    private void sell(Conversation c) {
        c.options(new Choice("ok i'll take it", "sorry doc, that's too much") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                Player p = c.getPlayer();
                if (p.getInventory().countId(COINS) < SUIT_PRICE) {
                    c.player("oops, I don't have enough money")
                     .npc("that's ok, but don't go near those sheep")
                     .npc("if you can find the money i'll be waiting here");
                    return;
                }
                c.take(COINS, SUIT_PRICE)
                 .give(new InvItem(JACKET, 1))
                 .give(new InvItem(TROUSERS, 1))
                 .npc("these will keep you safe from the plague")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().setFlag("herder.bought", 1);
                     }
                 });
            }
        });
    }

    private void brumty(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello there")
                .player("i'm sorry about your sheep")
                .npc("that's ok, it had to be done")
                .npc("i just hope none of my other livestock becomes infected")
                .start();
            return;
        }
        if (!questStarted()) {
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("hello adventurer")
            .npc("be careful rounding up those sheep")
            .npc("i don't think they've wandered far")
            .npc("but if you touch them you'll become infected as well")
            .npc("there should be a cattle prod in the barn")
            .npc("you can use it to herd up the sheep")
            .start();
    }
}
