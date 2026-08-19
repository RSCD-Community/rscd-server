import org.rscdaemon.server.event.DelayedEvent;
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
 * Gertrude's cat. Released 28 July 2003, written by Thomas Woode.
 *
 * Gertrude has lost Fluffs. One of her sons will say where she went for a
 * hundred coins, and Fluffs herself is sulking on the top floor of the
 * abandoned lumber mill north-east of Varrock, where she refuses to be picked
 * up until she has been given milk, then a sardine seasoned with doogle
 * leaves, and finally her two kittens.
 *
 * Fluffs is not an npc. She is item 1093, "Cat", whose examine reads "it's
 * fluffs", lying on the ground at (58,2327) -- the mill's top floor. That is
 * why this quest wanted a trigger nothing had needed before: ITEM_ON_GROUND_ITEM,
 * which is how the milk, the sardine and the kittens reach her. Trying to
 * carry her off is an ordinary ITEM_PICKUP, and she scratches.
 *
 *     Gertrude    npc 714, (161,513), her house west of Varrock
 *     Shilop      npc 715, (126,504), Varrock market
 *     Wilough     npc 781, (123,506), Varrock market
 *     low fence   door 199, (51,438), the only way into the mill yard
 *     the crate   object 1040, (64,445) -- the kittens are in it
 *     Fluffs      item 1093, (58,2327), up both ladders from (60,439)
 *     doogle      item 1100, five spawns behind Gertrude's house
 *
 * Both the fence and the crate are safe to claim outright: each has exactly one
 * placement in the whole world. The crate is worth remarking on -- there are
 * eighteen crates of id 1039 and seventeen barrels of 1041 scattered through
 * the same yard and exactly one of id 1040, which is Jagex saying which crate
 * the kittens are in without having to say it.
 *
 * The low fence has to be claimed for a duller reason: its one command is
 * "search", and WallObjectAction only opens doors whose command is "open", so
 * without this the mill yard cannot be entered at all.
 *
 * Two deviations:
 *
 *  - Shilop and Wilough are interchangeable and each says the whole thing, as
 *    in the real game. The transcript for Wilough is Shilop's with the name
 *    changed and two lines left unchanged; those two are given to whoever is
 *    speaking rather than left saying "Shilop".
 *
 *  - Fluffs stays on the ground when she is picked up and refuses -- the
 *    pickup has already happened by the time a quest hears about it, so she is
 *    taken back off the player rather than never leaving the floor. Her ground
 *    spawn respawns on its own timer, so she cannot be lost or duplicated.
 *
 * The kitten itself lives here too, because the reward is a pet, not a
 * trinket. A once-a-minute sweep (one event for the whole server, armed by
 * whichever player's copy of this quest loads first) ages every kitten being
 * carried: it grows into a cat (item 1119, "looks like a healthy one" -- the
 * one tradeable cat in the vanilla defs, which is Jagex saying that is the
 * one the West Ardougne civilians take) after two hours in the pack, gets
 * hungry unless fed, lonely unless stroked, and runs away for good if either
 * is ignored past its final warning. Feeding is any raw fish, the seasoned
 * sardine, or milk; stroking or a ball of wool is attention. A banked or
 * dropped kitten is paused, not neglected -- the clock only runs while it is
 * carried. Progress rides quest vars, so it survives a logout.
 *
 * Kitten deviations, and why:
 *
 *  - The wiki says cooked fish work too. Claiming an item takes its inventory
 *    command outright, and the cooked fish all have "Eat" -- claiming Shrimp
 *    for the kitten would stop every player in the game eating shrimp. Raw
 *    fish, the seasoned sardine and milk have no command and are safe to
 *    claim, so the kitten eats its fish raw.
 *
 *  - "miaow!" and "you hear a loud meow your kitten is really hungry" are the
 *    recorded warnings. The attention warning, the run-away line and the
 *    growing-up line have no surviving transcript, and the exact timings were
 *    never recorded anywhere; the wordings and the two-hour/forgiving-warning
 *    schedule here are ours.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class GertrudesCat extends Quest {

    public final static int UID = Quests.GERTRUDES_CAT;

    private static final int GERTRUDE = 714, SHILOP = 715, WILOUGH = 781;

    /*
     * The four Civillians (the in-game spelling) of north-west West Ardougne,
     * npcs 795-798, added 2026-08-15: they shipped with this quest on 28 July
     * 2003 but postdate our cache snapshot, so their defs are rebuilt from the
     * wiki (one level 18, one old man, one with apron, one with skirt, all
     * complaining about the rat epidemic) and their dialogue is ours -- no
     * transcript survives. They trade a grown cat for 25 death runes; a
     * kitten or the witch's sleepy cat only gets talk, as recorded.
     */
    private static final int CIV_A = 795, CIV_B = 796, CIV_C = 797, CIV_D = 798;
    private static final int DEATH_RUNE = 38;
    private static final int WITCHS_CAT = 1003;
    /* The two level-2 rats, for the recorded cat-pounce. */
    private static final int RAT = 29, RAT2 = 241;

    private static final int STARTED   = 1;   /* Gertrude has asked */
    private static final int PAID      = 2;   /* the boy has been paid */
    private static final int WATERED   = 3;   /* Fluffs has had her milk */
    private static final int FED       = 4;   /* and her sardine */
    private static final int REUNITED  = 5;   /* and her kittens; she ran home */
    private static final int FINISHED  = 6;

    private static final int FENCE = 199;
    private static final int FENCE_X = 51, FENCE_Y = 438;
    private static final int CRATE = 1040;
    private static final int CRATE_X = 64, CRATE_Y = 445;
    private static final int OPEN_DOOR = 11;

    private static final int FLUFFS = 1093;
    private static final int DOOGLE = 1100, RAW_SARDINE = 354, SEASONED = 1094;
    private static final int MILK = 22, BUCKET = 21;
    private static final int KITTENS = 1095, KITTEN = 1096, CAT = 1119;
    private static final int COINS = 10, PRICE = 100;
    private static final int CHOCOLATE_CAKE = 332, STEW = 346;

    private static final int COOKING = 7, HITS = 3;

    /* What the kitten eats: the raw fish and the seasoned sardine. None of
     * these has an inventory command, so claiming them costs nothing. */
    private static final int[] KITTEN_FOOD = { 349, 351, 354, 356, 358, 366, SEASONED };
    private static final int WOOL = 207;

    /* Kitten state, in persisted quest vars. */
    private static final int VAR_GROWTH = 0, VAR_HUNGER = 1, VAR_LONELY = 2;

    /* The schedule, in minutes of being carried (the sweep runs once a
     * minute). Warnings repeat so a missed line cannot cost the kitten. */
    private static final int GROWN = 120;
    private static final int HUNGRY = 20, STARVING = 30, STARVED = 40;
    private static final int LONELY = 30, ABANDONED = 45;

    /** One sweep for the whole server; quest classes load once and are shared. */
    private static boolean tickerArmed = false;

    public GertrudesCat(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Gertrude's cat");
        setFinalStage(FINISHED);
        associateNpc(GERTRUDE);
        associateNpc(SHILOP);
        associateNpc(WILOUGH);
        associateNpc(CIV_A);
        associateNpc(CIV_B);
        associateNpc(CIV_C);
        associateNpc(CIV_D);
        /* Claimed for ITEM_ON_NPC (the cat pounce); rats have nothing to say,
         * so owning their talk costs nothing. */
        associateNpc(RAT);
        associateNpc(RAT2);
        associateDoor(FENCE);
        associateObject(CRATE);
        associateItem(FLUFFS);
        /* Both halves, so that leaves on a sardine reaches ITEM_ON_ITEM. */
        associateItem(DOOGLE);
        associateItem(RAW_SARDINE);
        /* The kitten and everything used on it, for the same reason. */
        associateItem(KITTEN);
        associateItem(MILK);
        associateItem(WOOL);
        for (int i = 0; i < KITTEN_FOOD.length; i++) {
            associateItem(KITTEN_FOOD[i]);
        }
        armTicker();

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("Gertrude's cat Fluffs has gone missing; find out where she went and coax her home again.");
        setStartPoint("Gertrude's house west of Varrock");
        setSpeakTo("Gertrude");
        rewardItem(KITTEN, 1);
        rewardItem(CHOCOLATE_CAKE, 1);
        rewardItem(STEW, 1);
        rewardExp(COOKING, 175, 45);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed Gertrude's cat");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_TALK) {
                if (npc.getID() == GERTRUDE) {
                    talkToGertrude(npc);
                } else if (npc.getID() == SHILOP || npc.getID() == WILOUGH) {
                    talkToBoy(npc);
                } else if (npc.getID() != RAT && npc.getID() != RAT2) {
                    civilian(npc);
                }
            } else if (trigger == QuestTrigger.ITEM_ON_NPC) {
                usedOnNpc(npc, used);
            }
        } else if (entity instanceof Item) {
            if (trigger == QuestTrigger.ITEM_ON_GROUND_ITEM) {
                giveToFluffs((Item) entity, used);
            }
        } else if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                if (((InvItem) entity).getID() == KITTEN) {
                    stroke();
                } else {
                    /* Fluffs. Her one command is "Stroke", and claiming her took it. */
                    getOwner().getActionSender().sendMessage("The cat purrs");
                }
            } else if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                pair((InvItem) entity, used);
            }
        } else if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
                fence(object);
            } else {
                crate(object);
            }
        }
    }

    private boolean past(int stage) {
        return questStarted() && getStage() >= stage;
    }

    // ------------------------------------------------------------ Fluffs --

    /**
     * Picking her up. She scratches until she has had everything she wants,
     * and the message says which thing she is still waiting for. Answered
     * before the item moves, so she never leaves the floor -- she used to be
     * taken and then confiscated, which despawned her until the ground
     * respawn timer brought her back.
     */
    public boolean refusesPickup(InvItem item) {
        Player p = getOwner();
        if (item.getID() != FLUFFS) {
            return false;
        }
        if (!past(PAID)) {
            p.getActionSender().sendMessage("The cat scratches you and runs off");
            scratch();
            return true;
        }
        p.getActionSender().sendMessage("The cat scratches you");
        scratch();
        if (!past(WATERED)) {
            p.getActionSender().sendMessage("She looks thirsty");
        } else if (!past(FED)) {
            p.getActionSender().sendMessage("She looks hungry");
        } else {
            p.getActionSender().sendMessage("She won't leave without something");
        }
        return true;
    }

    private void scratch() {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(1, p.getCurStat(HITS) - 2));
        p.getActionSender().sendStat(HITS);
    }

    /** Milk, then a seasoned sardine, then her kittens, in that order. */
    private void giveToFluffs(Item fluffs, InvItem used) {
        Player p = getOwner();
        if (used == null || fluffs.getID() != FLUFFS) {
            return;
        }
        if (!past(PAID)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (used.getID() == MILK && !past(WATERED)) {
            p.getInventory().remove(MILK, 1);
            p.getInventory().add(new InvItem(BUCKET, 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You pour the milk into a bowl");
            p.getActionSender().sendMessage("The cat laps up the milk");
            setStage(WATERED);
            return;
        }
        if (used.getID() == SEASONED && past(WATERED) && !past(FED)) {
            p.getInventory().remove(SEASONED, 1);
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("The cat wolfs down the sardine");
            p.getActionSender().sendMessage("She meows loudly");
            p.getActionSender().sendMessage("@gre@You hear kittens meowing in the distance");
            setStage(FED);
            return;
        }
        if (used.getID() == KITTENS && past(FED) && !past(REUNITED)) {
            p.getInventory().remove(KITTENS, 1);
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("The kittens run to their mother");
            p.getActionSender().sendMessage("@gre@Fluffs and her kittens run off home");
            world.unregisterItem(fluffs);
            setStage(REUNITED);
            return;
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }

    /**
     * A pair of claimed items used together: the seasoning, or something for
     * the kitten. Every pair of ids this quest claims arrives here and
     * nowhere else, so anything unrecognised still has to be answered.
     */
    private void pair(InvItem first, InvItem second) {
        Player p = getOwner();
        if (second == null) {
            return;
        }
        int a = first.getID(), b = second.getID();
        if ((a == DOOGLE && b == RAW_SARDINE) || (a == RAW_SARDINE && b == DOOGLE)) {
            p.getInventory().remove(DOOGLE, 1);
            p.getInventory().remove(RAW_SARDINE, 1);
            p.getInventory().add(new InvItem(SEASONED, 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You season the sardine with doogle leaves");
            return;
        }
        if (a == KITTEN || b == KITTEN) {
            nurse(a == KITTEN ? b : a);
            return;
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }

    // ----------------------------------------------------------- the kitten --

    /**
     * The whole-server sweep. Every online player's copy of this quest gets a
     * tick() once a minute; the event has no owner, so no logout removes it.
     */
    private void armTicker() {
        synchronized (GertrudesCat.class) {
            if (tickerArmed) {
                return;
            }
            tickerArmed = true;
            world.getDelayedEventHandler().add(new DelayedEvent(null, 60000) {
                public void run() {
                    for (Player p : world.getPlayers()) {
                        Quest q = p.getQuestManager().getQuest(UID);
                        if (q instanceof GertrudesCat) {
                            ((GertrudesCat) q).tick();
                        }
                    }
                }
            });
        }
    }

    /** One minute of carrying a kitten. Not carrying one pauses everything. */
    private void tick() {
        Player p = getOwner();
        if (p.getInventory() == null || p.getInventory().countId(KITTEN) < 1) {
            return;
        }
        int hunger = getVar(VAR_HUNGER, 0) + 1;
        int lonely = getVar(VAR_LONELY, 0) + 1;
        if (hunger >= STARVED || lonely >= ABANDONED) {
            runAway();
            return;
        }
        int growth = getVar(VAR_GROWTH, 0) + 1;
        if (growth >= GROWN) {
            growUp();
            return;
        }
        setVar(VAR_GROWTH, growth);
        setVar(VAR_HUNGER, hunger);
        setVar(VAR_LONELY, lonely);
        if (hunger >= STARVING) {
            if ((hunger - STARVING) % 3 == 0) {
                p.getActionSender().sendMessage("@ora@you hear a loud meow your kitten is really hungry");
            }
        } else if (hunger >= HUNGRY && (hunger - HUNGRY) % 5 == 0) {
            p.getActionSender().sendMessage("miaow!");
        }
        if (lonely >= LONELY && (lonely - LONELY) % 5 == 0) {
            p.getActionSender().sendMessage("your kitten wants attention");
        }
    }

    /** Two hours carried: one kitten becomes the healthy, tradeable cat. */
    private void growUp() {
        Player p = getOwner();
        p.getInventory().remove(KITTEN, 1);
        p.getInventory().add(new InvItem(CAT, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@gre@your kitten has grown into a healthy cat");
        forgetKitten();
    }

    /** Neglected past the final warning. Gone for good, as in the real game. */
    private void runAway() {
        Player p = getOwner();
        p.getInventory().remove(KITTEN, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@red@your kitten has run away");
        forgetKitten();
    }

    /** A carried spare, if any, starts fresh: only one kitten grows at a time. */
    private void forgetKitten() {
        clearVar(VAR_GROWTH);
        clearVar(VAR_HUNGER);
        clearVar(VAR_LONELY);
    }

    /** Its one command, "stroke". Attention. */
    private void stroke() {
        getOwner().getActionSender().sendMessage("The kitten purrs");
        clearVar(VAR_LONELY);
    }

    /**
     * An item used on a claimed npc: the cat on a rat, or anything on a
     * civilian. The recorded pounce lines are verbatim from the wiki,
     * apostrophe and all.
     */
    private void usedOnNpc(Npc npc, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        if (npc.getID() == RAT || npc.getID() == RAT2) {
            if (used.getID() != CAT) {
                p.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                return;
            }
            p.getActionSender().sendMessage("the cat pounces on the rat...");
            p.getActionSender().sendMessage("...and quickly gobbles it up");
            p.getActionSender().sendMessage("it returns to your satchel licking it's paws");
            npc.remove();
            return;
        }
        if (used.getID() == CAT) {
            tradeCat(npc);
        } else {
            p.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
        }
    }

    // ------------------------------------------------------- the civilians --

    /** A Civillian of West Ardougne. Wording is ours; see the header. */
    private void civilian(Npc npc) {
        Player p = getOwner();
        if (p.getInventory().countId(CAT) > 0) {
            tradeCat(npc);
            return;
        }
        if (p.getInventory().countId(KITTEN) > 0) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh, for a moment i thought you'd brought us a cat")
                .npc("that kitten of yours is no match for these rats")
                .npc("come back when it's fully grown")
                .start();
            return;
        }
        if (p.getInventory().countId(WITCHS_CAT) > 0) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("is that cat of yours asleep?")
                .npc("it's no use to us, the rats would walk right over it")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("can't stop to talk, the rats are everywhere")
            .npc("they eat what little food we have left")
            .player("you could do with a cat")
            .npc("if you find one we'd pay well for it")
            .start();
    }

    /** The trade: a grown cat for 25 death runes. */
    private void tradeCat(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("is that a cat you have with you?")
            .player("yes, a fine rat catcher")
            .npc("we're overrun with rats here")
            .npc("i'll give you 25 death runes for it")
            .options(new Choice("it's a deal", "no, i'm keeping my cat") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("please reconsider, we've nothing left for the rats to eat but us");
                        return;
                    }
                    if (c.getPlayer().getInventory().countId(CAT) < 1) {
                        return;
                    }
                    c.take(CAT, 1)
                     .give(new InvItem(DEATH_RUNE, 25))
                     .npc("thank you, the rats don't stand a chance now");
                }
            })
            .start();
    }

    /** Something used on the kitten: food, milk, or the ball of wool. */
    private void nurse(int other) {
        Player p = getOwner();
        if (p.getInventory().countId(KITTEN) < 1) {
            return;
        }
        if (other == MILK) {
            p.getInventory().remove(MILK, 1);
            p.getInventory().add(new InvItem(BUCKET, 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("The kitten laps up the milk");
            clearVar(VAR_HUNGER);
            return;
        }
        if (other == WOOL) {
            /* Played with, not eaten: the wool survives. */
            p.getActionSender().sendMessage("The kitten playfully chases the ball of wool");
            clearVar(VAR_LONELY);
            return;
        }
        for (int i = 0; i < KITTEN_FOOD.length; i++) {
            if (KITTEN_FOOD[i] == other) {
                p.getInventory().remove(other, 1);
                p.getActionSender().sendInventory();
                p.getActionSender().sendMessage("The kitten gobbles up the fish");
                clearVar(VAR_HUNGER);
                return;
            }
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }

    // ------------------------------------------------------ the mill yard --

    private void fence(GameObject door) {
        Player p = getOwner();
        if (door.getX() != FENCE_X || door.getY() != FENCE_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!past(PAID)) {
            p.getActionSender().sendMessage("You have no reason to go in there");
            return;
        }
        p.getActionSender().sendMessage("You search the fence");
        p.getActionSender().sendMessage("You find a gap you can squeeze through");
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        /* Faces east/west, so it stands between x-1 and x. */
        p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(),
            door.getY(), false);
    }

    private void crate(GameObject crate) {
        Player p = getOwner();
        if (crate.getX() != CRATE_X || crate.getY() != CRATE_Y) {
            p.getActionSender().sendMessage("You search the crate but find nothing");
            return;
        }
        p.getActionSender().sendMessage("You search the crate");
        if (!past(FED) || past(REUNITED)) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (p.getInventory().countId(KITTENS) > 0) {
            p.getActionSender().sendMessage("You already have the kittens");
            return;
        }
        p.getInventory().add(new InvItem(KITTENS, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@gre@You find two kittens hiding inside");
    }

    // ------------------------------------------------------------ the boys --

    /**
     * Shilop and Wilough. The two transcripts are the same words, so both
     * brothers are handled here and either of them can be the one who is paid.
     */
    private void talkToBoy(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello youngster")
                .npc("i don't talk to strange old people")
                .start();
            return;
        }
        if (past(WATERED)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("you think you're tough do you?")
                .player("pardon?")
                .npc("i can beat anyone up")
                .player("really")
                .start();
            return;
        }
        if (past(PAID)) {
            new Conversation(p, npc)
                .player("where did you say you saw fluffs?")
                .npc("weren't you listerning?, i saw the flee bag...")
                .npc("...in the old lumber mill just north east of here")
                .npc("just walk past the jolly boar inn and you should find it")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("hello there, i've been looking for you")
         .npc("i didn't mean to take it!, i just forgot to pay")
         .player("what?...i'm trying to help your mum find fluffs")
         .npc("ohh..., well, in that case i might be able to help")
         .npc("fluffs followed me to my secret play area..")
         .npc("i haven't seen him since")
         .player("and where is this play area?")
         .npc("if i told you that, it wouldn't be a secret")
         .options(new Choice("tell me sonny, or i will hurt you",
                             "what will make you tell me?",
                             "well, never mind, fluffs' loss") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("w..w..what? y..you wouldn't, a young lad like me")
                      .npc("i'd have you behind bars before nightfall");
                 } else if (option == 2) {
                     c.npc("i'm sure my mum will get over it");
                 } else {
                     haggle(c);
                 }
             }
         })
         .start();
    }

    private void haggle(Conversation c) {
        c.npc("well...now you ask, i am a bit short on cash")
         .player("how much?")
         .npc("100 coins should cover it")
         .player("100 coins!, why should i pay you?")
         .npc("you shouldn't, but i won't help otherwise")
         .npc("i never liked that cat any way, so what do you say?")
         .options(new Choice("ok then, i'll pay", "i'm not paying you a penny") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("ok then, i find another way to make money");
                     return;
                 }
                 if (c.getPlayer().getInventory().countId(COINS) < PRICE) {
                     c.player("but i'll have to get some money first")
                      .npc("i'll be waiting");
                     return;
                 }
                 c.player("there you go, now where did you see fluffs?")
                  .take(COINS, PRICE)
                  .npc("i play at an abandoned lumber mill to the north..")
                  .npc("just beyond the jolly boar inn...")
                  .npc("i saw fluffs running around in there")
                  .player("anything else?")
                  .npc("well, you'll have to find a broken fence to get in")
                  .npc("i'm sure you can manage that")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(PAID);
                      }
                  });
             }
         });
    }

    // ------------------------------------------------------------ Gertrude --

    private void talkToGertrude(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            afterwards(npc);
            return;
        }
        if (getStage() == REUNITED) {
            new Conversation(p, npc)
                .player("hello gertrude")
                .player("fluffs ran off with her two kittens")
                .npc("you're back , thank you, thank you")
                .npc("fluffs just came back, i think she was just upset...")
                .npc("...as she couldn't find her kittens")
                .npc("if you hadn't found her kittens they'd have died out there")
                .player("that's ok, i like to do my bit")
                .npc("i don't know how to thank you")
                .npc("I have no real material possessions..but i do have kittens")
                .npc("..i can only really look after one")
                .player("well, if it needs a home")
                .npc("i would sell it to my cousin in west ardounge..")
                .npc("i hear there's a rat epidemic there..but it's too far")
                .npc("here you go, look after her and thank you again")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(FINISHED);
                    }
                })
                .start();
            return;
        }
        if (getStage() == FED) {
            new Conversation(p, npc)
                .player("hi")
                .npc("hey traveller, did fluffs eat the sardines?")
                .player("yeah, she loved them, but she still won't leave")
                .npc("well that is strange, there must be a reason!")
                .start();
            return;
        }
        if (getStage() == WATERED) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("hello, how's it going?, any luck?")
                .player("yes, i've found fluffs")
                .npc("well well, you are clever, did you bring her back?")
                .player("well, that's the thing, she refuses to leave")
                .npc("oh dear, oh dear, maybe she's just hungry")
                .npc("she loves doogle sardines but i'm all out")
                .player("doogle sardines?")
                .npc("yes, raw sardines seasoned with doogle leaves")
                .npc("unfortunatly i've used all my doogle leaves")
                .npc("but you may find some in the woods out back")
                .start();
            return;
        }
        if (getStage() == PAID) {
            new Conversation(p, npc)
                .player("hello gertrude")
                .npc("hello again, did you manage to find shilop?")
                .npc("i can't keep an eye on him for the life of me")
                .player("he does seem quite a handfull")
                .npc("you have no idea!.... did he help at all?")
                .player("i think so, i'm just going to look now")
                .npc("thanks again adventurer")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello gertrude")
                .npc("have you seen my poor fluffs?")
                .player("i'm afraid not")
                .npc("what about shilop?")
                .player("no sign of him either")
                .npc("hmmm...strange, he should be at the market")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("hello, are you ok?")
         .npc("do i look ok?...those kids drive me crazy")
         .npc("...i'm sorry, it's just, ive lost her")
         .player("lost who?")
         .npc("fluffs, poor fluffs, she never hurt anyone")
         .player("who's fluffs")
         .npc("my beloved feline friend fluffs")
         .npc("she's been purring by my side for almost a decade")
         .npc("please, could you go search for her...")
         .npc("...while i look over the kids?");
        offer(c);
        c.start();
    }

    /**
     * Her offer. "what's in it for me?" loops back to it, which the transcript
     * marks with "previous"; rebuilding the menu inside picked() is how that is
     * done here.
     */
    private void offer(Conversation c) {
        c.options(new Choice("well, i suppose i could",
                             "what's in it for me?",
                             "sorry, i'm too busy to play pet rescue") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("really?, thank you so much")
                     .npc("i really have no idea where she could be")
                     .npc("i think my sons, shilop and Wilough, saw the cat last")
                     .npc("they'll be out in the market place")
                     .player("alright then, i'll see what i can do")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(STARTED);
                         }
                     });
                } else if (option == 1) {
                    c.npc("i'm sorry, i'm too poor to pay you anything")
                     .npc("the best i could offer is a warm meal")
                     .npc("so, can you help?");
                    offer(c);
                } else {
                    c.npc("well, ok then, i'll have to find someone else");
                }
            }
        });
    }

    /**
     * Afterwards she sells kittens, at Shilop's suggestion, and only to
     * somebody who has lost the one she gave them.
     */
    private void afterwards(Npc npc) {
        Player p = getOwner();
        if (p.getInventory().countId(KITTEN) > 0) {
            new Conversation(p, npc)
                .player("hello again gertrude")
                .npc("well hello adventurer, how are you?")
                .player("pretty good thanks, yourself?")
                .npc("same old, running after shilob most of the time")
                .player("never mind, i'm sure he'll calm down with age")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("well hello adventurer, how are you?")
            .player("i'm ok, but i lost my kitten")
            .npc("that is a shame..as it goes fluffs just had more")
            .npc("i'm selling them at 100 coins each...")
            .npc("...it was shilop's idea")
            .player("!")
            .npc("would you like one")
            .options(new Choice("yes please", "no thanks, i've paid that boy enough already") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    if (c.getPlayer().getInventory().countId(COINS) < PRICE) {
                        c.player("oops, looks like i'm a bit short")
                         .player("i'll have to come back later");
                        return;
                    }
                    c.npc("ok then, here you go")
                     .take(COINS, PRICE)
                     .give(new InvItem(KITTEN, 1))
                     .player("thanks");
                }
            })
            .start();
    }
}
