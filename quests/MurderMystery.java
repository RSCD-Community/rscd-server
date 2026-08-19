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
import org.rscdaemon.server.util.DataConversions;

import java.util.ArrayList;

/**
 * Murder Mystery. Released 9 June 2003, written by James B.
 *
 * Lord Sinclair is dead in the study of his mansion north of Seers' Village,
 * apparently stabbed, and the guards outside have no idea what to do about it.
 * He was in fact poisoned: the knife on the floor is too flimsy to have killed
 * anyone and the pot beside it smells of wine and Peter Potter's Patented
 * Multi Purpose Poison. One of his six children did it, and which one is
 * decided per player when the quest is taken on.
 *
 * Three separate lines of evidence have to meet before the guards will act:
 *
 *   - the thread caught on the study window, which matches the murderer's
 *     trousers and rules out the servants, who all wear black;
 *   - the guard dog behind the gate, which barks at strangers and did not bark
 *     that night, which rules out an intruder;
 *   - a fingerprint lifted off the knife with flour and flypaper and matched
 *     against prints lifted the same way off six pieces of silver, one taken
 *     from each child's barrel.
 *
 * The print names the murderer. Every child bought a bottle of poison from the
 * salesman on the Camelot road the week before and each has an innocent use for
 * it; asking them and then going to look shows five jobs done and one not, and
 * the one who never used their poison is the one who fed it to their father.
 *
 *     Guard 747 (495,418) (488,411) (482,402) (496,399)   Guard Dog 748 (482,385)
 *     Anna 751 (496,389)      Bob 752 (485,385)      Carol 753 (488,1329)
 *     David 754 (485,403)     Elizabeth 755 (495,1329)   Frank 756 (495,385)
 *     Donovan 741 (487,1336)  Pierre 742 (481,389)   Hobbes 743 (494,390)
 *     Louisa 744 (498,386)    Mary 745 (495,1334)    Stanford 746 (501,388)
 *     Poison Salesman 763 (490,452)
 *
 *     Window 205    (484,389)   "Investigate"  a wall object -- the thread
 *     gate 1140     (482,389)   "Investigate"  the guard dog's pen
 *     barrels 1132  (498,390)   1133 (498,391)          Anna, Bob
 *             1134  (484,1331)  1135 (484,1332)         Carol, David
 *             1136  (498,1331)  1137 (498,1334)         Elizabeth, Frank
 *     Flour Barrel 1138 (496,385) "Take From"   the kitchen
 *     sacks 1139    (500,385) (501,385) "investigate"   the garden shed
 *
 *     Compost Heap 1126 (500,392)   Anna's alibi
 *     beehive 1127      (500,406)   Bob's
 *     Drain 1128        (493,394)   Carol's
 *     web 1129          (491,1337)  David's
 *     fountain 1130     (484,399)   Elizabeth's
 *     Sinclair Crest 1131 (486,394) Frank's
 *
 * The whole of this world data already matched Jagex's, placement for
 * placement, with one exception: Louisa The Cook (npc 744) had no spawn at all,
 * so she has been put back in the kitchen beside the flour barrel. Everything
 * else here is claimed, not moved.
 *
 * Deviations:
 *
 *  - The gossiping Man on the road IS implemented, as of 2026-08-07. This entry
 *    used to say he was not, and both of its reasons were false. It is kept
 *    rather than deleted because how it survived is the useful part.
 *
 *    It said: "Jagex gave his lines to npc 11, the generic Man, and there is no
 *    way to claim one Man without claiming every Man in RuneScape. His dialogue
 *    is hints only -- fingerprints, the dog, the salesman -- all of which the
 *    quest tells you anyway."
 *
 *    He is not npc 11. He is npc 750, examine "A thirsty looking man", with six
 *    spawns of his own on the road south of the mansion. Claiming him claims
 *    nothing else, exactly as claiming Straven 24 claims no other Man.
 *
 *    And the quest does NOT tell you anyway. Fingerprinting is the one mechanic
 *    here that nothing else explains, and his hint table is its only source.
 *    Until today this quest shipped a puzzle with its instructions missing.
 *
 *    Nobody checked the id. The sentence was plausible, it was written down, and
 *    it then justified leaving out the largest single piece of the quest for
 *    months. The Mining Guild gate was the same shape: a confident note about a
 *    number that nobody had gone and looked at.
 *
 *  - The messages for dusting an item with flour, lifting a print with
 *    flypaper, and comparing two prints were never recorded, so they are
 *    written here. Everything spoken by an npc, and every message from the
 *    scenery, is Jagex's from the transcripts.
 *
 *  - A pot of flour dusts one item and is spent, leaving the empty pot. The
 *    barrel is in the kitchen and the mansion is small, so this is seven short
 *    walks rather than a wall.
 *
 *  - Elizabeth's line at the fountain -- "I hate mosquitos, they're so
 *    annoying" -- is printed as a message rather than spoken by the player,
 *    because scenery has no npc to hold a conversation against.
 *
 *  - Duplicate murder weapons and murder scene pots are refused on pickup. The
 *    real game also refused them to a player who had one banked; this only
 *    looks at the inventory.
 */
public class MurderMystery extends Quest {

    public final static int UID = Quests.MURDER_MYSTERY;

    // ---------------------------------------------------------------- cast --

    private static final int GUARD = 747;

    /** The six children, in the order every table in this file uses. */
    private static final int ANNA = 751, BOB = 752, CAROL = 753;
    private static final int DAVID = 754, ELIZABETH = 755, FRANK = 756;
    private static final int[] SUSPECT = { ANNA, BOB, CAROL, DAVID, ELIZABETH, FRANK };
    private static final String[] SUSPECT_NAME =
        { "Anna", "Bob", "Carol", "David", "Elizabeth", "Frank" };
    /** "It must have been Anna who killed her father". */
    private static final String[] SUSPECT_HIS =
        { "her", "his", "her", "his", "her", "his" };
    /** "We will hold her here under house arrest". */
    private static final String[] SUSPECT_HIM =
        { "her", "him", "her", "him", "her", "him" };

    /**
     * Where each suspect's poison alibi falls down. There are TWO of these and
     * they are NOT the same table -- Elizabeth and Frank are worded differently
     * in the two scenes, and the transcript is consistent about it across every
     * suspect block, so this is a difference in the shipped strings and not a
     * transcription slip. Collapsing them into one array would silently rewrite
     * two of the twelve recorded lines.
     *
     * ACCUSING is the mid-quest "I have proof X is lying about the poison"
     * branch; PROVING is the final accusation.
     */
    private static final String[] ACCUSING_PLACE = {
        "the compost heap", "the beehive", "the drain",
        "the spiders nest", "the mosquitos at the fountain",
        "the tarnished family crest"
    };
    private static final String[] PROVING_PLACE = {
        "the compost heap", "the beehive", "the drain",
        "the spiders nest", "the fountain", "the Sinclair Crest"
    };

    /** The six servants, in their own order. */
    private static final int DONOVAN = 741, PIERRE = 742, HOBBES = 743;
    private static final int LOUISA = 744, MARY = 745, STANFORD = 746;
    private static final int[] SERVANT = { DONOVAN, PIERRE, HOBBES, LOUISA, MARY, STANFORD };

    private static final int SALESMAN = 763;

    /**
     * The gossiping Man on the road south of the mansion. Six spawns of his own
     * at (498,418), (492,411), (486,423), (493,414), (486,417) and (496,416).
     *
     * He is npc 750, "A thirsty looking man", and he is NOT the generic Man --
     * see the deviation note in the header, which said he was for months and was
     * wrong about it.
     */
    private static final int MAN = 750;

    // ------------------------------------------------------------- scenery --

    private static final int COMPOST = 1126, BEEHIVE = 1127, DRAIN = 1128;
    private static final int WEB = 1129, FOUNTAIN = 1130, CREST = 1131;
    /** One per suspect, in suspect order. */
    private static final int[] ALIBI = { COMPOST, BEEHIVE, DRAIN, WEB, FOUNTAIN, CREST };

    private static final int FIRST_BARREL = 1132;   /* .. 1137, in suspect order */
    private static final int FLOUR_BARREL = 1138;
    private static final int SACKS = 1139;
    private static final int GATE = 1140;

    private static final int WINDOW = 205, WINDOW_X = 484, WINDOW_Y = 389;

    // --------------------------------------------------------------- items --

    private static final int COINS = 10;
    private static final int POT = 135, FLOUR = 136;

    private static final int FIRST_SILVER = 1194;   /* .. 1199, in suspect order */
    private static final int FIRST_DUSTED = 1224;   /* .. 1229, the same six floured */
    private static final int FIRST_PRINT = 1207;    /* .. 1212, their fingerprints */

    /** Red, green, blue -- the three threads Jagex drew. */
    private static final int[] THREAD = { 1200, 1201, 1202 };
    /**
     * Whose trousers are which colour, from the npc definitions: Anna and David
     * green, Bob and Carol red, Elizabeth and Frank blue. The thread on the
     * window is the murderer's, so it narrows six suspects to two.
     */
    private static final int[] TROUSERS = { 1, 0, 0, 1, 2, 2 };

    private static final int FLYPAPER = 1203;
    private static final int SCENE_POT = 1204;
    private static final int DAGGER = 1205;
    private static final int DUSTED_DAGGER = 1230;
    private static final int MURDER_PRINT = 1206;
    private static final int UNKNOWN_PRINT = 1223;

    private static final int CRAFTING = 12;
    private static final int REWARD_COINS = 2000;

    // -------------------------------------------------------------- stages --

    private static final int STARTED = 1;
    /** Bits 1..3 hold the murderer as 1..6; zero means the quest never started. */
    private static final int CULPRIT_MASK = 14, CULPRIT_SHIFT = 1;
    private static final int THREAD_TAKEN = 16;
    private static final int DOG = 32;
    private static final int SALES = 64;
    private static final int PRINT = 128;
    private static final int LIED = 256;
    /** Bits 9..14: which suspects have been asked about their poison. */
    private static final int ASKED_SHIFT = 9;
    /**
     * Bit 15, alone. completed() is exact equality, so finishing has to wipe
     * the murderer and everything else -- which is safe, because once the
     * guards have him nothing ever asks who it was again.
     */
    private static final int FINISHED = 32768;

    public MurderMystery(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Murder Mystery");
        setFinalStage(FINISHED);

        for (int npc : SERVANT) {
            associateNpc(npc);
        }
        for (int npc : SUSPECT) {
            associateNpc(npc);
        }
        associateNpc(GUARD);
        associateNpc(SALESMAN);
        associateNpc(MAN);

        /* 1126 .. 1140: all fifteen exist only at the Sinclair mansion. */
        for (int object = COMPOST; object <= GATE; object++) {
            associateObject(object);
        }
        associateDoor(WINDOW, WINDOW_X, WINDOW_Y);

        /* Both halves of every pair, so ITEM_ON_ITEM reaches us. */
        associateItem(FLOUR);
        associateItem(FLYPAPER);
        associateItem(UNKNOWN_PRINT);
        for (int i = 0; i < SUSPECT.length; i++) {
            associateItem(FIRST_SILVER + i);
            associateItem(FIRST_DUSTED + i);
            associateItem(FIRST_PRINT + i);
        }
        associateItem(DUSTED_DAGGER);
        /* Also the two spawns in the study, for the pickup messages. */
        associateItem(DAGGER);
        associateItem(SCENE_POT);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("Lord Sinclair lies murdered in his mansion north of Seers' Village; gather the evidence that proves which of his six children poisoned him.");
        setStartPoint("The Sinclair mansion north of Seers' Village");
        setSpeakTo("Guard");
        rewardItem(COINS, REWARD_COINS);
        rewardOther("Crafting experience of level x 37.5 + 187.5");
    }

    public void completeQuest() {
        Player p = getOwner();
        grantRewards();
        /* Level * 37.5 + 187.5, which is what the wiki's rewards section says
         * and what the RuneHQ guide's "some crafting exp" is worth. This paid
         * four times that until 2026-08-01 -- 150 and 750, the same numbers
         * with the halves cleared by multiplying through -- which at level 50
         * was 8,250 crafting instead of 2,062.
         *
         * The halves are real; two other quests have them. It is written as one
         * division so the truncation lands once, at the end: Level 1 gives 225
         * exactly, and only odd levels lose the half. The Restless Ghost does
         * the same thing for Level * 62.5 + 500.
         *
         * Stays imperative: rewardExp() takes whole base and per-level figures
         * and cannot say 37.5, so it is declared with rewardOther above. */
        p.incExp(CRAFTING, (p.getMaxStat(CRAFTING) * 75 + 375) / 2, false);
        /* Recorded text. This said "Well done you have solved the Murder
         * Mystery" until 2026-08-07, which was a paraphrase nobody had a source
         * for; the transcript's arrest sequence ends with this line instead.
         *
         * Two lines that sit either side of it in the transcript are NOT built,
         * on purpose, and they are framework gaps rather than quest gaps:
         *   "You haved gained 3 quest points!"  -- nothing in the tree announces
         *      quest points at all. Points are computed on read from the set of
         *      completed quests (Player.getQuestPoints) and never printed. Adding
         *      a hand-written one here would make this the only quest in fifty-
         *      seven that announces them.
         *   "You received 2000 gold!"           -- likewise, no quest anywhere
         *      announces a reward item; grantRewards() adds them silently.
         * Both belong in Quest, for every quest at once, or nowhere. The
         * level-up line between them needs nothing: incExp prints it. */
        p.getActionSender().sendMessage(
            "Well done.You have completed the Murder Mystery Quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage(questStarted() ? getStage() | bit : bit);
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

    private void say(String line) {
        getOwner().getActionSender().sendMessage(line);
    }

    private void say(String[] lines) {
        for (String line : lines) {
            say(line);
        }
    }

    /**
     * Queue a block of transcript. A line beginning "P:" is the player, so a
     * whole exchange can sit in one array beside the lines it belongs with.
     */
    private void lines(Conversation c, String[] block) {
        for (String line : block) {
            if (line.startsWith("P:")) {
                c.player(line.substring(2));
            } else {
                c.npc(line);
            }
        }
    }

    private void permission() {
        say("You need the guards permission to do that");
    }

    private boolean open() {
        return questStarted() && !completed();
    }

    /** Which of the six did it, or -1 before the quest is taken on. */
    private int culprit() {
        if (!questStarted()) {
            return -1;
        }
        return ((getStage() & CULPRIT_MASK) >> CULPRIT_SHIFT) - 1;
    }

    private boolean asked(int suspect) {
        return has(1 << (ASKED_SHIFT + suspect));
    }

    /** Everything the guards need before they will make an arrest. */
    private boolean solved() {
        return has(THREAD_TAKEN) && has(DOG) && has(LIED) && holding(MURDER_PRINT);
    }

    private int indexOf(int[] table, int value) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] == value) {
                return i;
            }
        }
        return -1;
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
            int id = npc.getID();
            if (id == GUARD) {
                guard(npc);
                return;
            }
            if (id == SALESMAN) {
                salesman(npc);
                return;
            }
            if (id == MAN) {
                manOnTheRoad(npc);
                return;
            }
            int i = indexOf(SUSPECT, id);
            if (i >= 0) {
                suspect(npc, i);
                return;
            }
            i = indexOf(SERVANT, id);
            if (i >= 0) {
                servant(npc, i);
            }
            return;
        }
        if (entity instanceof InvItem) {
            InvItem item = (InvItem) entity;
            if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                combine(item, used);
                return;
            }
            if (trigger == QuestTrigger.ITEM_PICKUP) {
                pickup(item);
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1) {
            window();
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (object.getID() == FLOUR_BARREL) {
                flourBarrel(used);
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        int id = object.getID();
        if (id >= FIRST_BARREL && id < FIRST_BARREL + SUSPECT.length) {
            barrel(id - FIRST_BARREL);
            return;
        }
        int i = indexOf(ALIBI, id);
        if (i >= 0) {
            alibi(i);
            return;
        }
        switch (id) {
            case FLOUR_BARREL: flourBarrel(null); return;
            case SACKS:        sacks();           return;
            case GATE:         gate();            return;
        }
    }

    // --------------------------------------------------------- the evidence --

    /** The study window. The thread on the nail is the murderer's. */
    private void window() {
        if (!open()) {
            permission();
            return;
        }
        int guilty = culprit();
        if (guilty < 0) {
            return;
        }
        int thread = THREAD[TROUSERS[guilty]];
        say("Some thread seems to have been caught");
        say("on a loose nail on the window");
        if (holding(thread)) {
            say("You have already taken the thread");
            return;
        }
        if (has(THREAD_TAKEN)) {
            say("Lucky for you theres some thread left");
            say("You should be less careless in future");
        } else {
            say("You take the thread");
            set(THREAD_TAKEN);
        }
        give(thread, 1);
    }

    /** The dog's pen. It barks at strangers, and it did not bark that night. */
    private void gate() {
        if (!open()) {
            permission();
            return;
        }
        say("As you approach the gate the Guard Dog starts barking loudly at you");
        say("There is no way an intruder could have committed the murder");
        say("It must have been someone the dog knew to get past it quietly");
        set(DOG);
    }

    private static final String[] BARREL_TAKE = {
        "You take Annas Silver Necklace",
        "You take Bobs silver cup",
        "You take Carols silver bottle",
        "You take Davids silver book",
        "You take Elizabeths silver needle",
        "You take franks silver pot"
    };

    private static final String[] BARREL_ALREADY = {
        "You already have Annas Necklace",
        "You already have Bobs cup",
        "You already have Carols bottle",
        "You already have Davids book",
        "You already have Elizabeths needle",
        "You already have franks pot"
    };

    private void barrel(int suspect) {
        if (!open()) {
            permission();
            return;
        }
        say("Theres something shiny hidden at the bottom");
        if (holding(FIRST_SILVER + suspect) || holding(FIRST_DUSTED + suspect)) {
            say(BARREL_ALREADY[suspect]);
            return;
        }
        say(BARREL_TAKE[suspect]);
        give(FIRST_SILVER + suspect, 1);
    }

    /** The gardener's shed. Seven sheets are needed and the sack never empties. */
    private void sacks() {
        if (!open()) {
            permission();
            return;
        }
        new Conversation(getOwner(), null)
            .message("Theres some flypaper in there.")
            .message("Do you take it?")
            .options(new Choice("Yes, it might be useful",
                                "No, I don't see any need for it") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.message("you leave the paper in the sack");
                        return;
                    }
                    c.message("You take a piece of fly paper")
                     .message("There is still plenty of fly paper left")
                     .give(new InvItem(FLYPAPER, 1));
                }
            })
            .start();
    }

    /**
     * The kitchen's flour barrel. Not part of the quest -- it is the only place
     * outside a windmill that fills a pot -- so it is not gated on the guards.
     */
    private void flourBarrel(InvItem used) {
        if (used != null && used.getID() == SCENE_POT) {
            say("You probably shouldn't use evidence from a crime");
            say("scene to keep flour in...");
            return;
        }
        if (used != null && used.getID() != POT) {
            say("Nothing interesting happens");
            return;
        }
        if (!holding(POT)) {
            say("You need an empty pot to take any flour");
            return;
        }
        take(POT, 1);
        give(FLOUR, 1);
        say("You fill the pot with flour");
    }

    // ---------------------------------------------------------- the alibis --

    private static final String[][] ALIBI_NEUTRAL = {
        { "Its a heap of Compost" },
        { "Its a very old beehive" },
        { "Its the drains from the kitchen" },
        { "It looks like a Spiders Nest of some kind" },
        { "A fountain with large numbers of insects around the base" },
        { "The Sinclair Family Crest is hung up here" }
    };

    private static final String[][] ALIBI_INNOCENT = {
        { "There is a faint smell of poison behind the smell of the compost" },
        { "The hive is empty. There are a few dead bees and",
          "a faint smell of poison" },
        { "The drain seems to have been recently cleaned",
          "You can still smell the faint aroma of poison" },
        { "A faint smell of poison and a few dead spiders",
          "is all that remains of the spiders nest" },
        { "There are a lot of dead mosquitos around",
          "the base of the fountain. A faint smell of",
          "poison is in the air, but the water seems clean" },
        { "The sinclair family crest",
          "its shiny and freshly polished",
          "And has a slight smell of poison" }
    };

    private static final String[][] ALIBI_GUILTY = {
        { "The compost is teeming with maggots",
          "Somebody should really do something about it",
          "Its certainly clear nobodies used poison here." },
        { "The beehive buzzes with activity",
          "These bees definitely don't seem poisoned at all" },
        { "The drain is totally blocked",
          "It really stinks. No, it *Really* smells bad.",
          "Its certainly clear nobodies cleaned it recently." },
        { "There is a spiders nest here",
          "You estimate there must be at least a few hundred spiders ready to hatch",
          "Its certainly clear nobodies used poison here." },
        { "The fountain is swarming with mosquitos",
          "Theres a nest of them underneath the fountain",
          "I hate mosquitos, they're so annoying",
          "Its certainly clear nobodies used poison here." },
        { "It looks like the Sinclair Family Crest",
          "but it is very dirty.",
          "you can barely make it out under all of the grime",
          "Its certainly clear nobodies cleaned it recently." }
    };

    /**
     * The thing a suspect said they bought the poison for. Until the salesman
     * has been found and that suspect has been asked, there is nothing to see:
     * the player has no reason yet to be looking for traces of poison.
     */
    private void alibi(int suspect) {
        if (!open()) {
            permission();
            return;
        }
        if (!has(SALES) || !asked(suspect)) {
            say(ALIBI_NEUTRAL[suspect]);
            return;
        }
        if (culprit() != suspect) {
            say(ALIBI_INNOCENT[suspect]);
            return;
        }
        say(ALIBI_GUILTY[suspect]);
        set(LIED);
    }

    // ------------------------------------------------------ fingerprinting --

    private void pickup(InvItem item) {
        if (item.getID() == DAGGER) {
            say("This knife doesn't seem sturdy enough to have killed Lord Sinclair");
            if (getOwner().getInventory().countId(DAGGER) > 1) {
                say("You already have the murderweapon");
                take(DAGGER, 1);
            }
            return;
        }
        if (item.getID() != SCENE_POT) {
            return;
        }
        say("It seems like Lord Sinclair was drinking from this before he died");
        if (getOwner().getInventory().countId(SCENE_POT) > 1) {
            say("You already have the sickly smelling pot");
            take(SCENE_POT, 1);
        }
    }

    /**
     * Flour onto silver, flypaper onto flour, one print against another. Six
     * pieces of silver and the murder weapon go the same way, so the dagger
     * rides along as a seventh entry with no owner.
     */
    private void combine(InvItem first, InvItem second) {
        if (first == null || second == null || !open()) {
            return;
        }
        int a = first.getID(), b = second.getID();

        int item = silverIndex(a);
        if (item >= 0 && b == FLOUR) {
            dust(item);
            return;
        }
        item = silverIndex(b);
        if (item >= 0 && a == FLOUR) {
            dust(item);
            return;
        }

        item = dustedIndex(a);
        if (item >= 0 && b == FLYPAPER) {
            lift(item);
            return;
        }
        item = dustedIndex(b);
        if (item >= 0 && a == FLYPAPER) {
            lift(item);
            return;
        }

        item = printIndex(a);
        if (item >= 0 && b == UNKNOWN_PRINT) {
            compare(item);
            return;
        }
        item = printIndex(b);
        if (item >= 0 && a == UNKNOWN_PRINT) {
            compare(item);
        }
    }

    /** 0..5 for the children's silver, 6 for the murder weapon. */
    private int silverIndex(int id) {
        if (id == DAGGER) {
            return SUSPECT.length;
        }
        return id >= FIRST_SILVER && id < FIRST_SILVER + SUSPECT.length
            ? id - FIRST_SILVER : -1;
    }

    private int dustedIndex(int id) {
        if (id == DUSTED_DAGGER) {
            return SUSPECT.length;
        }
        return id >= FIRST_DUSTED && id < FIRST_DUSTED + SUSPECT.length
            ? id - FIRST_DUSTED : -1;
    }

    private int printIndex(int id) {
        return id >= FIRST_PRINT && id < FIRST_PRINT + SUSPECT.length
            ? id - FIRST_PRINT : -1;
    }

    private int silverId(int item) {
        return item == SUSPECT.length ? DAGGER : FIRST_SILVER + item;
    }

    private int dustedId(int item) {
        return item == SUSPECT.length ? DUSTED_DAGGER : FIRST_DUSTED + item;
    }

    private int printId(int item) {
        return item == SUSPECT.length ? UNKNOWN_PRINT : FIRST_PRINT + item;
    }

    private String silverName(int item) {
        return item == SUSPECT.length ? "dagger" : SUSPECT_NAME[item] + "'s silver";
    }

    private void dust(int item) {
        if (!holding(FLOUR) || !holding(silverId(item))) {
            return;
        }
        take(FLOUR, 1);
        give(POT, 1);
        take(silverId(item), 1);
        give(dustedId(item), 1);
        say("You dust the " + silverName(item) + " with the flour");
        say("A fingerprint stands out clearly in the powder");
    }

    private void lift(int item) {
        if (!holding(FLYPAPER) || !holding(dustedId(item))) {
            return;
        }
        take(FLYPAPER, 1);
        take(dustedId(item), 1);
        give(silverId(item), 1);
        give(printId(item), 1);
        say("You press the flypaper down onto the " + silverName(item));
        say("and peel the fingerprint off with it");
    }

    private void compare(int suspect) {
        if (!holding(UNKNOWN_PRINT) || !holding(printId(suspect))) {
            return;
        }
        say("You hold the two prints up against each other");
        if (culprit() != suspect) {
            say("They are nothing like each other");
            return;
        }
        say("Every line and whorl matches");
        say("@gre@" + SUSPECT_NAME[suspect] + " handled the murder weapon");
        take(UNKNOWN_PRINT, 1);
        take(printId(suspect), 1);
        give(MURDER_PRINT, 1);
        set(PRINT);
    }

    // ----------------------------------------------------------- the guard --

    private void guard(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Excellent work on solving the murder")
                .npc("All of the guards I know are very impressed")
                .npc("And don't worry, we have the murderer under guard")
                .npc("until they can be taken to trial")
                .start();
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("What's going on here?")
                .npc("Oh, its terrible.")
                .npc("Lord Sinclair has been murdered")
                .npc("And we don't have any clues as to")
                .npc("who or why. We're totally baffled")
                .npc("If you can help us")
                .npc("we will be very grateful")
                .options(new Choice("Sure, I'll help",
                                    "You should do your own dirty work") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            c.npc("get lost then, this is private property.")
                             .npc("...unless you'd like to be taken for questioning yourself");
                            return;
                        }
                        c.npc("thanks a lot!")
                         .player("What should I be doing to help?");
                        briefing(c);
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                begin();
                            }
                        });
                    }
                })
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        guardMenu(c);
        c.start();
    }

    /** Pick the murderer. Six players in a room will be chasing six answers. */
    private void begin() {
        int guilty = (int) (Math.random() * SUSPECT.length);
        setStage(STARTED | ((guilty + 1) << CULPRIT_SHIFT));
    }

    private void briefing(Conversation c) {
        c.npc("Look around and investigate who might be responsible")
         .npc("the sarge said every murder leaves clues to who done it")
         .npc("but frankly we're out of our depth here");
    }

    private void guardMenu(Conversation c) {
        c.options(new Choice("What should I be doing to help again?",
                             "How did Lord Sinclair die?",
                             "I know who did it!") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    briefing(c);
                    return;
                }
                if (option == 1) {
                    c.npc("well its all very mysterious.")
                     .npc("Mary the maid found the body in the study next to his bedroom")
                     .npc("on the east wing of the ground floor, the door was found locked,")
                     .npc("from the inside, and he seemed to have been stabbed")
                     .npc("but there was an odd smell in the room. Frankly, I'm stumped");
                    return;
                }
                accusationMenu(c);
            }
        });
    }

    /* What the player can say once they claim to know. */
    private static final int SAY_SOLVED = 0, SAY_SERVANTS = 1, SAY_POISON = 2;
    private static final int SAY_PRINTS = 3, SAY_INTRUDER = 4, SAY_BUTLER = 5;
    private static final int SAY_STAFF = 6, SAY_FAMILY = 7;

    /**
     * "I know who did it!" -- every piece of evidence the player is actually
     * carrying gets its own line, in front of the four ways of guessing.
     */
    private void accusationMenu(Conversation c) {
        ArrayList<String> options = new ArrayList<String>();
        final ArrayList<Integer> kinds = new ArrayList<Integer>();
        if (solved()) {
            options.add("I have conclusive Proof who the killer was");
            kinds.add(SAY_SOLVED);
        }
        if (has(THREAD_TAKEN)) {
            options.add("I have proof that it wasn't any of the servants");
            kinds.add(SAY_SERVANTS);
        }
        if (has(LIED)) {
            options.add("I have proof that " + SUSPECT_NAME[culprit()]
                + " is lying about the poison");
            kinds.add(SAY_POISON);
        }
        if (holding(MURDER_PRINT)) {
            options.add("I have the fingerprints of the culprit");
            kinds.add(SAY_PRINTS);
        }
        if (kinds.isEmpty()) {
            c.npc("Really? That was quick work! Who?");
        }
        options.add("It was an intruder!");
        kinds.add(SAY_INTRUDER);
        options.add("the butler did it!");
        kinds.add(SAY_BUTLER);
        options.add("It was one of the servants");
        kinds.add(SAY_STAFF);
        options.add("It was one of his family");
        kinds.add(SAY_FAMILY);

        c.options(new Choice(options.toArray(new String[options.size()])) {
            public void picked(int option, Conversation c) {
                switch (kinds.get(option).intValue()) {
                    case SAY_SOLVED:   arrest(c);            return;
                    case SAY_SERVANTS: servantsRuledOut(c);  return;
                    case SAY_POISON:   poisonProof(c);       return;
                    case SAY_PRINTS:   printProof(c);        return;
                    case SAY_INTRUDER: intruder(c);          return;
                    case SAY_BUTLER:   butler(c);            return;
                    case SAY_STAFF:    nameMenu(c, false);   return;
                    case SAY_FAMILY:   nameMenu(c, true);    return;
                }
            }
        });
    }

    // ------------------------------------------------- the man on the road --

    /**
     * Npc 750, the gossip on the road, and the single largest thing this quest
     * was missing: 128 lines, none of which existed anywhere in the server.
     *
     * He is not flavour. Dialogue 4 of his hint table is the ONLY place in the
     * game that explains fingerprinting -- unique marks on hands, fine powder on
     * a shiny metal surface, sticky paper to lift the print. Every piece of that
     * mechanic was already implemented and nothing told the player it existed.
     * The guard says as much in printProof(): "I've never heard of such a
     * technique for finding criminals before". He has never heard of it because
     * the player was supposed to have learned it out here, from this man.
     *
     * Without him the quest is a puzzle with its instructions deleted, and the
     * only way through is to guess that flour plus flypaper plus dagger is a
     * thing you can do.
     */
    private void manOnTheRoad(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .npc("Theres some kind of commotion up at the Sinclair place")
                .npc("I hear. Not surprising all things considered")
                .start();
            return;
        }
        if (getStage() == FINISHED) {
            new Conversation(p, npc)
                .npc("I heard you solved the murder")
                .npc("Was I of any help to you at all?")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("I'm investigating the murder up at the Sinclair place")
            .npc("Murder is it?")
            .npc("Well, I'm not very surprised...")
            .options(new Choice("What can you tell me about the Sinclairs?",
                                "Who do you think was responsible?",
                                "Why do the Sinclairs live so far from town?",
                                "I think the butler did it",
                                "I am so confused about who did it") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0: aboutTheSinclairs(c); return;
                        case 1: whoWasResponsible(c);  return;
                        case 2: whySoFarFromTown(c);   return;
                        case 3: butlerTheory(c);       return;
                        default: hints(c);
                    }
                }
            })
            .start();
    }

    private void aboutTheSinclairs(Conversation c) {
        c.npc("Well, what do you want to know?")
         .options(new Choice("Tell me about Lord Sinclair",
                             "what can you tell me about his sons?",
                             "what can you tell me about his daughters?") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: lordSinclair(c); return;
                    case 1: children(c, SONS, "His sons eh? They all have their own skeletons",
                                     "In their cupboards. You'll have to be more specific.",
                                     "Who are you interested in exactly?");
                            return;
                    default: children(c, DAUGHTERS,
                                     "His daughters eh? They're all nasty pieces of work",
                                     "which of them specifically did you want to know about?",
                                     null);
                }
            }
        });
    }

    /** Indices into SUSPECT_NAME, in the order each menu lists them. */
    private static final int[] SONS = { 1, 3, 5 };        // Bob, David, Frank
    private static final int[] DAUGHTERS = { 0, 2, 4 };   // Anna, Carol, Elizabeth

    /**
     * The sons' menu opens with three lines and the daughters' with two, so the
     * third is passed as null rather than given the two menus separate methods
     * that would otherwise be identical.
     */
    private void children(Conversation c, final int[] who,
                          String first, String second, String third) {
        c.npc(first).npc(second);
        if (third != null) {
            c.npc(third);
        }
        c.options(new Choice("Tell me about " + SUSPECT_NAME[who[0]],
                             "Tell me about " + SUSPECT_NAME[who[1]],
                             "Tell me about " + SUSPECT_NAME[who[2]]) {
            public void picked(int option, Conversation c) {
                lines(c, GOSSIP[who[option]]);
            }
        });
    }

    private void lordSinclair(Conversation c) {
        c.npc("Old Lord Sinclair was a great man with a lot of")
         .npc("respect in these parts. More than his worthless")
         .npc("children have anyway")
         .player("His children? They have something to gain by his death?")
         .npc("yes. you could say that. not that im one to gossip");
    }

    /**
     * One block per suspect, in the file's standard suspect order, so
     * GOSSIP[i] lines up with SUSPECT_NAME[i] and nothing has to be looked up.
     *
     * Sic throughout and all of it deliberate: "thats", "Hes"/"hes", "Fathers",
     * "its", "devestated", and "frank" in lower case in the middle of his own
     * paragraph. The wiki marks every one.
     */
    private static final String[][] GOSSIP = {
        /* Anna */ {
            "Anna... ah yes...",
            "Anna has 2 great loves:",
            "Sewing and Gardening. But one thing",
            "she has kept secret is that once had",
            "an affair with Stanford the gardener",
            "and tried to get him fired when they broke up",
            "by killing all the flowers in the garden",
            "if her father ever found out she had done that",
            "He would be so furious he would probably disown her"
        },
        /* Bob */ {
            "Bob is an odd character indeed...",
            "I'm not one to gossip, but I heard",
            "Bob is addicted to Tea. He can't make it through the day",
            "Without having at least 20 cups!",
            "You might not think thats a big thing,",
            "But he has spent thousands of gold to feed his habit",
            "At one point he stole a lot of silverware from the kitchen",
            "and pawned it just so he could afford to buy his daily",
            "tea allowance. If his father ever found out, he would",
            "be in so much trouble... he might even get disowned"
        },
        /* Carol */ {
            "Oh Carol... she is such a fool",
            "You didn't hear it from me, but I heard",
            "a while ago she was conned out for a lot of money",
            "by a travelling salesman who sold her a box full",
            "of beans by telling her they were magic. But they weren't",
            "She sold some rare books from the library to cover her debts",
            "But her father would be incredibly annoyed",
            "If he ever found out - he might even throw her out of the house"
        },
        /* David */ {
            "David... oh David...",
            "not many people know this, but David really",
            "has an anger problem. Hes always screaming and shouting",
            "at the household servants when hes angry, and they live",
            "in a state of fear, always walking on eggshells around him",
            "but none of them have the courage to talk to his father about",
            "his behaviour. If they did Lord Sinclair would almost certainly",
            "kick him out of the house, as some of the servants have",
            "been there longer than he has, and he definitely",
            "has no right to treat them like he does... but",
            "I'm not one to gossip about people."
        },
        /* Elizabeth */ {
            "Elizabeth? Elizabeth has a strange problem",
            "She cannot help herself, but is always stealing small",
            "objects - its pretty sad that she is rich enough to afford",
            "to buy things, but would rather steal them instead.",
            "Now, I don't want to spread stories, but I heard",
            "She even stole a silver needle from her father that",
            "had great sentimental value for him. He was devestated when",
            "it was lost, and cried for a week thinking he had lost it",
            "If he ever found out that it was her who had stolen it",
            "He would go absolutely mental, maybe even disowning her"
        },
        /* Frank */ {
            "I'm not one to talk ill of people behind their back",
            "but frank is a real piece of work. He is an absolutely",
            "terrible gambler... he can't pass 2 dogs in the street",
            "without putting a bet on which one will bark first",
            "He has already squandered all of his allowance, and I heard",
            "he had stolen a number of paintings of his Fathers to sell",
            "to try and cover his debts, but he still owes a lot of",
            "people a lot of money. If his Father ever found out, he would",
            "stop his income, and then he would be in serious trouble"
        }
    };

    private void whoWasResponsible(Conversation c) {
        c.npc("well, I guess it could have been an intruder")
         .npc("but with that big guard dog of theirs")
         .npc("I seriously doubt it.")
         .npc("I suspect it was someone closer to home...")
         .npc("Especially as I heard that that poison salesman")
         .npc("in the seers village made a big sale to one")
         .npc("of the family the other day.");
    }

    private void whySoFarFromTown(Conversation c) {
        c.npc("Well, they used to live in the big castle")
         .npc("but old Lord Sinclair gave it up so that those")
         .npc("strange knights could live there instead")
         .npc("So the king built him a new house to the North")
         .npc("Its more cramped than his old place, but he seemed to like it")
         .npc("his children were furious at him for doing it though");
    }

    private void butlerTheory(Conversation c) {
        c.npc("And I think you've been reading too many")
         .npc("cheap detective novels")
         .npc("Hobbes is kind of uptight, but his loyalty")
         .npc("to Old Lord Sinclair is beyond question");
    }

    /**
     * Five hints, drawn at random. HINT[3] is the fingerprint tutorial.
     *
     * That it is one roll in five is Jagex's design, not ours, and it is shipped
     * as recorded -- {{trandom}} carries no weighting anywhere on the page, the
     * same finding as the Man and Barbarian tables. Worth stating plainly
     * though: a player who needs the tutorial has a one-in-five chance per ask
     * of being given it. Making the tutorial guaranteed would be a DESIGN
     * CHANGE, not a restoration, and it is not made here.
     *
     * Hint 1 places the poison salesman "in town" while the "Who do you think
     * was responsible?" branch places him "in the seers village". Both are npc
     * 763 and both are recorded. Two speakers phrasing it differently is not an
     * inconsistency to tidy.
     *
     * "I don't think he has any stock left now though..." is flavour explaining
     * why you cannot simply buy the poison yourself. Reading a stock mechanic
     * into one line would be inventing one.
     */
    private void hints(Conversation c) {
        c.player("think you could give me any hints?");
        lines(c, HINT[DataConversions.random(0, HINT.length - 1)]);
    }

    private static final String[][] HINT = {
        {
            "well, I dont know if its related",
            "But I heard from that Poison Salesman in town",
            "That he sold some poison to one of the family the other day",
            "I don't think he has any stock left now though..."
        },
        {
            "Well I don't know how much help this is",
            "but I heard that their guard dog will bark loudly at anyone",
            "it doesn't recognise",
            "maybe you should find out if anyone heard anything suspicious?"
        },
        {
            "Well, this might be of some help to you",
            "My father was in the guards when he was younger",
            "and he always said that there isn't a crime that can't be",
            "solved through careful examination of the crime scene",
            "and all surrounding areas"
        },
        {   /* The fingerprint tutorial. Nothing else teaches this. */
            "I don't know how much help this is to you",
            "but my dad was in the guard once",
            "and he told me that the marks on your hands",
            "Are totally unique. He called them 'finger prints'",
            "He said you can find them easily on any shiny metallic surface",
            "By using a fine powder to mark out where the marks are",
            "and then using some sticky paper to lift the print from the object",
            "I bet if you could find a way to get everyones 'finger prints'",
            "you could solve the crime pretty easily"
        },
        {
            "My father used to be in the guard.",
            "He always wrote himself notes on a piece of paper",
            "so he could keep track of information easily.",
            "Maybe you should try that?",
            "Don't forget to thank me if I help you solve the case!"
        }
    };

    private void intruder(Conversation c) {
        c.npc("Thats what we were thinking too.")
         .npc("That someone broke in, to steal something")
         .npc("was discovered by Lord Sinclair, stabbed him and ran.")
         .npc("Its odd that apparently nothing was stolen though.")
         .npc("Find out something has been stolen, and the case is closed")
         .npc("But the murdered man was a friend of the king")
         .npc("and its more than my jobs worth not to investigate fully");
    }

    private void butler(Conversation c) {
        c.npc("I hope you have proof to that effect.")
         .npc("we have to arrest someone for this and it seems to me that")
         .npc("only the actual murderer would gain by falsely accusing someone")
         .npc("although having said that")
         .npc("the butler is kind of shifty looking...");
    }

    private void noProof(Conversation c) {
        c.message("You tell the guard who you suspect of the crime")
         .npc("Great work, show me the evidence")
         .npc("and we'll take them to the dungeons")
         .npc("you *DO* have evidence of their crime, right?")
         .player("uh....")
         .npc("tch. You wouldn't last a day in the guards")
         .npc("with sloppy thinking like that.")
         .npc("come see me when you have some proof of your accusations");
    }

    /** Servants or family, then women or men, then a name. */
    private void nameMenu(Conversation c, final boolean family) {
        c.npc("Oh really? Which one?")
         .options(new Choice("It was one of the women", "It was one of the men") {
            public void picked(int option, Conversation c) {
                c.npc("Oh really? Which one?");
                if (family) {
                    familyNames(c, option == 0);
                } else {
                    servantNames(c, option == 0);
                }
            }
        });
    }

    private void familyNames(Conversation c, boolean women) {
        Choice choice = women
            ? new Choice("I know it was Anna",
                         "I am so sure it was Carol",
                         "Ill bet you anything it was Elizabeth") {
                public void picked(int option, Conversation c) {
                    noProof(c);
                }
              }
            : new Choice("I'm certain it was Bob",
                         "It was David. No doubt about it.",
                         "If it wasn't Frank I'll eat my shoes") {
                public void picked(int option, Conversation c) {
                    noProof(c);
                }
              };
        c.options(choice);
    }

    /**
     * The men's list cannot use options(), because one of its four entries does
     * not say its own name. Accusing Hobbes speaks "the butler did it!" -- the
     * short line, not the long menu label -- and options() would print the label
     * instead. So the men go through picker(), which prints nothing on its own,
     * and each branch speaks its line explicitly. The three that do echo their
     * label are re-spoken verbatim, so nothing changes for them.
     *
     * The women's list has no such divergence and stays on options().
     */
    private void servantNames(Conversation c, boolean women) {
        if (women) {
            c.options(new Choice("it was so obviously Louisa The Cook",
                                 "It must have been Mary The Maid") {
                public void picked(int option, Conversation c) {
                    noProof(c);
                }
            });
            return;
        }
        final String[] spoken = {
            "it can only be Donovan the Handyman",
            "Pierre the Dog Handler. No question.",
            "the butler did it!",
            "you must know it was Stanford The Gardener"
        };
        c.picker(new Choice("it can only be Donovan the Handyman",
                            "Pierre the Dog Handler. No question.",
                            "Hobbes the Butler. the butler *always* did it",
                            "you must know it was Stanford The Gardener") {
            public void picked(int option, Conversation c) {
                c.player(spoken[option]);
                if (option == 2) {
                    butler(c);
                    return;
                }
                noProof(c);
            }
        });
    }

    private void servantsRuledOut(Conversation c) {
        c.message("you show the guard the thread you found on the window")
         .player("All the servants dress in black so")
         .player("it couldn't have been one of them")
         .npc("Thats some good work there. I guess it wasn't a servant.")
         .npc("You still havent proved who did do it though");
    }

    /**
     * The option label carries the spoken line here. Vanilla's menu entry is the
     * generic "I have proof one of the family lied about the poison" but the line
     * the player actually speaks names the suspect, and options() echoes the
     * label -- so the label is built per-suspect above and the echo produces the
     * recorded sentence. One line printed, and it is the right one.
     */
    private void poisonProof(Conversation c) {
        c.npc("Oh really? How did you get that?")
         .message("you tell the guard about " + ACCUSING_PLACE[culprit()])
         .npc("Hmm. thats some good detective work there.")
         .npc("We need more evidence before we can close the case though")
         .npc("Keep up the good work");
    }

    /**
     * Note the apostrophe. Here it is "Anna's Fingerprints", correctly punctuated
     * and with a capital F; in arrest() below the same possessive is written
     * "Annas fingerprints" with no apostrophe and a lower-case f. Both are as
     * recorded. Jagex was inconsistent between the two scenes and we reproduce
     * the inconsistency rather than tidying it, because a tidy-up here is
     * indistinguishable from a transcription error later.
     */
    private void printProof(Conversation c) {
        c.player("I have " + SUSPECT_NAME[culprit()] + "'s Fingerprints here.")
         .player("You can see for yourself they match the")
         .player("Fingerprints on the murder weapon exactly")
         .message("You show the guard the finger prints evidence")
         .npc("...")
         .npc("I'm impressed. How on earth did you think")
         .npc("of something like that? I've never heard")
         .npc("of such a technique for finding criminals before")
         .npc("This will come in very handy in the future")
         .npc("But we can't arrest someone on just this.")
         .npc("I'm afraid you'll still need to find more evidence")
         .npc("Before we can close this case completely");
    }

    private void arrest(Conversation c) {
        final int guilty = culprit();
        c.npc("You do? thats excellent work. Lets hear it then")
         .player("I don't think it was an intruder, and I don't think Lord")
         .player("Sinclair was killed by being stabbed.")
         .npc("hmmm? really? why not?")
         .player("nobody heard the guard dog barking, which it would have if")
         .player("it had been an intruder who was responsible.")
         .player("nobody heard any signs of a struggle either.")
         .player("I think the knife was there to throw suspicion away from the real culprit.")
         .npc("Yes, that makes sense. But who did do it then?")
         /* The three pieces of evidence are laid out one at a time and the guard
          * concedes a little more after each, which is what those three "not
          * quite enough" lines are answering. Without the messages between them
          * he reads as stalling for no reason.
          *
          * "Annas" and "Annas fingerprints" have no apostrophe. That is {sic} on
          * the wiki and deliberate here -- see printProof() above, which spells
          * the same possessive correctly, in the same quest. */
         .message("You prove to the guard the thread matches "
                  + SUSPECT_NAME[guilty] + "s clothes")
         .npc("Yes, I'd have to agree with that... but we need more evidence")
         .message("You prove to the guard " + SUSPECT_NAME[guilty]
                  + " did not use poison on " + PROVING_PLACE[guilty])
         .npc("Excellent work - have you considered a career as a detective?")
         .npc("But i'm afraid its still not quite enough...")
         .message("You match " + SUSPECT_NAME[guilty]
                  + "s fingerprints with those on the dagger")
         .message("Found in the body of Lord Sinclair")
         .npc("...")
         .npc("Yes. theres no doubt about it.")
         .npc("It must have been " + SUSPECT_NAME[guilty] + " who killed "
              + SUSPECT_HIS[guilty] + " father")
         .npc("All of the guards must congratulate you on your")
         .npc("Excellent work in helping us to solve this case")
         .npc("We don't have many murders here in RuneScape")
         .npc("And i'm afraid we wouldn't have been able to solve it")
         .npc("by ourselves. We will hold " + SUSPECT_HIM[guilty] + " here under house arrest")
         .npc("Until such time as we can bring " + SUSPECT_HIM[guilty] + " to trial")
         .npc("You have our gratitude, and I'm sure the rest of the")
         .npc("families as well, in helping to apprehend the murderer")
         .npc("I'll just take the evidence from you now")
         .message("You hand over all the evidence")
         /* The completion beat sits BETWEEN the two guard lines, not after both.
          * setStage(FINISHED) runs completeQuest(), which pays the crafting exp
          * (and so prints the level-up line) and prints the completion line --
          * and vanilla prints all of that before "Please accept this reward",
          * because the reward is the coins, and the coins land last. */
         .then(new Effect() {
             public void run(Conversation c) {
                 seizeEvidence();
                 setStage(FINISHED);
             }
         })
         .npc("Please accept this reward from the family!");
    }

    /** "I'll just take the evidence from you now" -- and he means all of it. */
    private void seizeEvidence() {
        Player p = getOwner();
        int[] loose = { MURDER_PRINT, UNKNOWN_PRINT, FLYPAPER, SCENE_POT,
                        DAGGER, DUSTED_DAGGER, THREAD[0], THREAD[1], THREAD[2] };
        for (int id : loose) {
            p.getInventory().remove(id, p.getInventory().countId(id));
        }
        for (int i = 0; i < SUSPECT.length; i++) {
            int[] set = { FIRST_SILVER + i, FIRST_DUSTED + i, FIRST_PRINT + i };
            for (int id : set) {
                p.getInventory().remove(id, p.getInventory().countId(id));
            }
        }
        p.getActionSender().sendInventory();
    }

    // -------------------------------------------------------- the children --

    private static final String[][] SUSPECT_GREETING = {
        { "Oh really? what do you want to know then?" },
        { "I suppose I had better talk to you then." },
        { "Well, ask what you want to know then" },
        { "And? Make this quick, I have better things to",
          "do than be interrogated by halfwits all day" },
        { "What's so important you need to bother me with then?" },
        { "Good for you. Now what do you want?",
          "And can you spare me any money? I'm a little short..." }
    };

    private static final String[][] SUSPECT_BLAME = {
        { "It was clearly an intruder.",
          "P:Well, I don't think it was",
          "It was one of our lazy servants then" },
        { "I don't really care as long as noone things its me",
          "Maybe that strange poison seller who headed towards seers village." },
        { "I don't know. I think its very convenient",
          "that you have arrived here so soon after it happened.",
          "Maybe it was you" },
        { "I don't really know or care",
          "Frankly, the old man deserved to die",
          "There was a suspicious red headed man who came",
          "to the house the other day selling poison now I",
          "think about it. Last I saw he was headed towards",
          "the tavern in the Seers village." },
        { "Could have been anyone. The old man was an",
          "idiot. Hes been asking for it for years." },
        { "I don't know.",
          "You don't know how long it takes an inheritance",
          "to come through do you? I could really use that",
          "money pretty soon..." }
    };

    private static final String[][] SUSPECT_ALIBI = {
        { "in the library. Noone else was there so",
          "You'll just have to take my word for it" },
        { "I was walking by myself in the garden.",
          "P:And can anyone vouch for that?",
          "No. But I was." },
        { "Why? Are you accusing me of something?",
          "You seem to have a very high opinion of yourself",
          "I was in my room if you must know, alone." },
        { "that is none of your business.",
          "Are we finished now, or are you just going",
          "to stand there irritating me with your",
          "idiotic questions all day?" },
        { "I was out",
          "P:Care to be any more specific?",
          "not really. I don't have to justify myself to the likes of you.",
          "I know the king personally you know. Now are we finished here?" },
        { "I don't know, somewhere around here probably.",
          "Could you spare me a few coins?",
          "I'll be able to pay you double tomorrow",
          "its just theres this poker night tonight in town..." }
    };

    /** What they say about a thread that is not their colour. */
    private static final String[][] SUSPECT_THREAD_NO = {
        { "Not really, no. Thread is fairly common" },
        { "Its some thread. great clue. No, really." },
        { "Its some thread. Sorry, do you have a point here?",
          "Or do you just enjoy wasting peoples time?" },
        { "No. Can I go yet? your face irritates me." },
        { "Its some thread. You're not very good",
          "at this whole investigation thing are you?" },
        { "It looks like thread to me, but I'm not exactly",
          "an expert. Is it worth something?",
          "Can I have it? Actually, can you spare me a few gold?" }
    };

    /** And what they say when it is. */
    private static final String[][] SUSPECT_THREAD_YES = {
        { "Its some Green thread. Its not exactly uncommon is it?",
          "My trousers are made of the same material" },
        { "Its some red thread. I suppose you think",
          "thats some kind of clue? It looks like",
          "the material my trousers are made of" },
        { "Its some red thread... it kind of looks like the",
          "Same material as my trousers. But obviously its not." },
        { "Its some Green thread, like my trousers are made of.",
          "Are you finished? I'm not sure which I dislike more",
          "about you, your face or your general bad odour" },
        { "Looks like a Blue thread to me.",
          "If you can't work that out for yourself I",
          "don't hold much hope of you solving this crime",
          "P:It looks a lot like the material your trousers",
          "P:are made of doesn't it?",
          "I suppose it does. So what?" },
        { "it kind of looks like the same material as",
          "my trousers are made of... same colour anyway",
          "think its worth anything? Can I have it? Or just some money?" }
    };

    private static final String[][] SUSPECT_POISON = {
        { "That useless Gardener Standford has let his",
          "Compost heap fester. Its an eyesore to the garden",
          "So I bought some poison from a travelling salesman",
          "So that I could kill off some of the wildlife living in it" },
        { "what's it you to you anyway?",
          "If you absolutely must know, we had a problem",
          "with the beehive in the garden, and as all of our",
          "servants are so pathetically useless, I decided",
          "I would deal with it myself. So I did." },
        { "I don't see what on earth it has to",
          "do with you, but the drain outside was",
          "blocked, and as nobody else here has the",
          "intelligence to even unblock a simple drain",
          "I felt I had to do it myself" },
        { "There was a nest of spiders upstairs between the",
          "Two Servant quarters. Obviously I had to kill them before",
          "our pathetic servants whined at my father some more",
          "Honestly, its like they expected to be treated like royalty",
          "If I had my way I would fire the whole workshy lot of them" },
        { "there was a nest of mosquitos under the fountain",
          "in the garden, which I killed with poison the other day.",
          "You can see for yourself if you're capable",
          "of managing that, which I somehow doubt",
          "P:I hate mosquitos",
          "Doesn't everyone?" },
        { "Would you like to buy some? I'm kind of strapped",
          "for cash right now, I'll sell it to you cheap, its hardly",
          "been used at all, I just used a bit to clean that family",
          "crest outside up a bit. Do you think I can get much money",
          "For the family crest, actually? Its cleaned up a bit now" }
    };

    /**
     * Before the quest the children have nothing to say to a stranger in their
     * garden, which is what the transcripts record: the dialogue simply ends.
     */
    private void suspect(Npc npc, final int i) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Apparently you aren't as stupid as you look")
                .start();
            return;
        }
        if (!questStarted()) {
            return;
        }
        Conversation c = new Conversation(p, npc)
            .player("I'm here to help the guards with their investigation");
        lines(c, SUSPECT_GREETING[i]);
        suspectMenu(c, i);
        c.start();
    }

    private void suspectMenu(Conversation c, final int i) {
        ArrayList<String> options = new ArrayList<String>();
        options.add("Who do you think was responsible?");
        options.add("Where were you when the murder happened?");
        final boolean thread = holding(THREAD[0]) || holding(THREAD[1])
            || holding(THREAD[2]) || has(THREAD_TAKEN);
        if (thread) {
            options.add("Do you recognise this thread?");
        }
        if (has(SALES)) {
            options.add("Why did you buy poison the other day?");
        }
        final int threadOption = thread ? 2 : -1;
        final int poisonOption = has(SALES) ? options.size() - 1 : -1;

        c.options(new Choice(options.toArray(new String[options.size()])) {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    lines(c, SUSPECT_BLAME[i]);
                    return;
                }
                if (option == 1) {
                    lines(c, SUSPECT_ALIBI[i]);
                    return;
                }
                if (option == threadOption) {
                    int guilty = culprit();
                    if (guilty >= 0 && TROUSERS[i] == TROUSERS[guilty]) {
                        lines(c, SUSPECT_THREAD_YES[i]);
                    } else {
                        lines(c, SUSPECT_THREAD_NO[i]);
                    }
                    return;
                }
                if (option == poisonOption) {
                    lines(c, SUSPECT_POISON[i]);
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            set(1 << (ASKED_SHIFT + i));
                        }
                    });
                }
            }
        });
    }

    // -------------------------------------------------------- the servants --

    private static final String[][] SERVANT_BEFORE = {
        { "I have no interest in talking to gawkers" },
        { "The Guards told me not to talk to anyone" },
        { "This is private property! Please leave!" },
        { "I'm far too upset to talk to random people right now" },
        {},
        { "Have you no shame? we are all grieving at the moment" }
    };

    private static final String[][] SERVANT_BLAME = {
        { "Oh... I really couldn't say.",
          "I wouldn't really want to point any fingers at anybody",
          "If I had to make a guess I'd have to say it was probably",
          "Bob though. I saw him arguing with Lord Sinclair about",
          "some missing silverware from the kitchen",
          "It was a very heated argument." },
        { "honestly? I think it was Carol.",
          "I saw here in a huge argument with Lord Sinclair",
          "in the library the other day. It was something",
          "to do with stolen books. She definitely seemed",
          "upset enough to have done it afterwards" },
        { "Well, in my considered opinion it must be",
          "David. The man is nothing more than a bully",
          "And I happen to know that the poor Lord Sinclair",
          "and David had a massive argument about the way",
          "he treats the staff in the living room the",
          "other day. I did not intend to overhear their conversation",
          "But they were shouting so loudly I could not help but",
          "Overhear it. David definitely used the words",
          "I am going to kill you!' as well",
          "I think he should be the prime suspect.",
          "He has a nasty temper that one." },
        { "Elizabeth.",
          "Her father confronted her about her",
          "constant petty thieving, and was",
          "devestated to find she had stolen a silver",
          "needle which meant a lot to him.",
          "You could hear their argument from Lumbridge!" },
        { "Oh I don't know...",
          "Frank was acting kind of funny...",
          "After that big argument him and the Lord",
          "had the other day by the beehive... so",
          "I guess maybe him... but its really scary",
          "to think someone here might have been responsible.",
          "I actually hope it was a burglar" },
        { "It was Anna. She is seriously unbalanced.",
          "She trashed the garden once then tried to blame it on me!",
          "I bet it was her. Its just the kind of thing she'd do",
          "She really hates me and was arguing with Lord Sinclair",
          "about trashing the garden a few days ago." }
    };

    private static final String[][] SERVANT_ALIBI = {
        { "Me? I was sound asleep here in the servants",
          "Quarters. Its very hard work as a handyman",
          "around here, theres always something to do" },
        { "I was in town at the inn. When I got back",
          "The house was swarming with guards who told",
          "me what had happened. Sorry." },
        { "I was assisting the cook with the evening meal",
          "I gave Mary His Lordships dinner, and sent her",
          "to take it to him, then heard the scream as she",
          "I found the body." },
        { "I was right here Hobbes and Mary.",
          "You can't suspect me surely!" },
        { "I was with hobbes and Louisa in the kitchen",
          "helping to prepare Lord Sinclair's meal, and then",
          "when I took it to his study...",
          "I saw... oh, it was horrible... he... was..." },
        { "Right here, by my little shed.",
          "Its very cosy to sit and think in" }
    };

    private static final String[][] SERVANT_NOISES = {
        { "Hmmm.... No, I didn't, but I sleep very soundly at night.",
          "P:So you didn't hear any sounds of a struggle or any",
          "P:barking from the guard dog next to his study window?",
          "Now you mention it, no. it is odd I didn't hear anything",
          "like that. But I do sleep very soundly as I said and",
          "wouldn't necessarily have heard it if there was any such noise" },
        { "well, like what?",
          "P:Any sounds of a struggle with Lord Sinclair?",
          "No, I don't remember hearing anything like that.",
          "P:How about the guard dog barking at all?",
          "I hear him bark all the time.",
          "its one of his favorite things to do.",
          "I can't say I did the night of the murder though",
          "As I wasn't close enough to hear either way" },
        { "how do you mean suspicious?",
          "P:Any sounds of a struggle with Lord Sinclair?",
          "No, I definitely didn't hear anything like that.",
          "P:How about the guard dog barking at all?",
          "You know, now you come to mention it",
          "I don't believe I did. I suppose that is",
          "Proof enough that it could not have been an",
          "intruder who is responsible." },
        { "suspicious? what do you mean suspicious?",
          "P:Any sounds of a struggle with an intruder for an example?",
          "No, I'm sure I don't recall any such thing.",
          "P:How about the guard dog barking at an intruder?",
          "No, I didn't.",
          "If you don't have anything else to ask can",
          "You go and leave me alone now? I have a lot",
          "of cooking to do for this evening." },
        { "I don't really remember hearing anything out of the ordinary",
          "P:no sounds of a struggle then?",
          "No, I don't really remember hearing anything like that.",
          "P:How about the guard dog barking?",
          "Oh that horrible dog is always barking at nothing",
          "but I don't think I did..." },
        { "Not that I remember.",
          "P:So no sounds of a struggle between Lord Sinclair and an intruder?",
          "Not to the best of my recollection",
          "P:How about the guard dog barking?",
          "Not that I can recall" }
    };

    private static final String[][] SERVANT_POISON = {
        { "Well, I do know Frank bought some poison",
          "recently to clean the family crest thats outside",
          "Its very old and rusty, and I couldn't clean it",
          "myself, so he said he would buy some cleaner and",
          "clean it himself. He probably just got some from that",
          "Poison Salesman who came to the door the other day",
          "you'd really have to ask him though" },
        { "Well, I know David said he was",
          "going to do something about the spider nests thats",
          "between the two servant quarters upstairs",
          "He made a big deal about it to Mary the Maid, calling",
          "her useless and incompetent. I felt sorry",
          "for her actually.",
          "you'd really have to ask him though." },
        { "Well, I do know that Elizabeth was extremely",
          "annoyed by the mosquito nest under the fountain",
          "in the garden, and was going to do something about",
          "it. I suspect any poison she bought would have been",
          "to get rid of it. A Good job too,",
          "I hate mosquitos.",
          "P:Yeah, so do I",
          "you'd really have to ask her though." },
        { "I told Carol to buy some from that strange",
          "poison salesman and clean the drains before they",
          "began to smell any worse. She was the one who",
          "blocked them in the first place with a load",
          "of beans that she bought for some reason.",
          "There were far too many to eat, and they",
          "were almost rotten when she bought them anyway",
          "you'd really have to ask her though." },
        { "I overheard Anna saying to Stanford",
          "that if he didn't do something about the",
          "state of his compost heap, she was going to.",
          "She really doesn't get on well with Stanford",
          "I really have no idea why",
          "you'd really have to ask her though." },
        { "Well, Bob mentioned to me the other day",
          "he wanted to get rid of the bees in that hive",
          "over there. I think I saw him buying poison",
          "from that poison salesman the other day",
          "I assume it was to sort out those bees",
          "you'd really have to ask him though." }
    };

    private void servant(Npc npc, final int i) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Thank you for all your help in solving the murder")
                .start();
            return;
        }
        if (!questStarted()) {
            if (SERVANT_BEFORE[i].length == 0) {
                return;
            }
            Conversation c = new Conversation(p, npc);
            lines(c, SERVANT_BEFORE[i]);
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .player("I'm here to help the guards with their investigation")
            .npc("How can I help?");
        servantMenu(c, i);
        c.start();
    }

    private void servantMenu(Conversation c, final int i) {
        ArrayList<String> options = new ArrayList<String>();
        options.add("Who do you think is responsible?");
        options.add("Where were you at the time of the murder?");
        options.add("Did you hear any suspicious noises at all?");
        if (has(SALES)) {
            options.add("Do you know why so much poison was bought recently?");
        }
        c.options(new Choice(options.toArray(new String[options.size()])) {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: lines(c, SERVANT_BLAME[i]);  return;
                    case 1: lines(c, SERVANT_ALIBI[i]);  return;
                    case 2: lines(c, SERVANT_NOISES[i]); return;
                    case 3: lines(c, SERVANT_POISON[i]); return;
                }
            }
        });
    }

    // -------------------------------------------------- Peter Potter, Esq. --

    private void salesman(Npc npc) {
        Player p = getOwner();
        if (!questStarted() || completed()) {
            new Conversation(p, npc)
                .player("Hi.")
                .npc("I'm afraid I'm all sold out of poison at the moment.")
                .npc("People know a bargain when they see it!")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .player("I'm investigating the murder at the Sinclair house.")
            .npc("There was a murder at the Sinclair House???")
            .npc("Thats terrible! And I was only there the other day too")
            .npc("They bought the last of my Patented Multi Purpose Poison!");
        ArrayList<String> options = new ArrayList<String>();
        options.add("Patented Multi Purpose Poison?");
        options.add("Who did you sell Poison to at the house?");
        options.add("Can I buy some Poison?");
        final boolean pot = holding(SCENE_POT);
        if (pot) {
            options.add("I have this pot I found at the murder scene...");
        }
        c.options(new Choice(options.toArray(new String[options.size()])) {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: pitch(c);     return;
                    case 1: whoBought(c); return;
                    case 2: outOfStock(c); return;
                    case 3: theSmell(c);  return;
                }
            }
        });
        c.start();
    }

    private void pitch(Conversation c) {
        c.npc("Aaaaah... a miracle of modern apothecarys, this exclusive")
         .npc("concoction has been tested on all known forms of life")
         .npc("and been proven to kill them all in varying dilutions")
         .npc("from cockroaches to king dragons")
         .npc("so incredibly versatile, it can be used as pest")
         .npc("control, a cleansing agent, drain cleaner, metal polish")
         .npc("and washed whiter than white, all with our uniquely")
         .npc("fragrant concoction that is immediately recognisable")
         .npc("across the land as Peter Potters Patented Poison potion")
         .message("The salesman stops for breath")
         .npc("I'd love to sell you some but I've sold out recently")
         .npc("Thats just how good it is! Three hundred and Twenty")
         .npc("Eight people in this area alone cannot be wrong!")
         .npc("Nine out of Ten poisoners prefer it in controlled tests!")
         .npc("Can I help you with anything else?")
         .npc("Perhaps I can take your name and add it to our mailing list")
         .npc("Of poison users? We will only send you information related to")
         .npc("the use of poison and other Peter Potter Products")
         .player("uh... no, its ok");
    }

    private void whoBought(Conversation c) {
        c.npc("Well, Peter Potters Patented Multi Purpose Poison")
         .npc("is a product of such obvious quality that I am")
         .npc("glad to say I managed to sell a bottle to each of the")
         .npc("Sinclars - Anna, Bob, Carol, David, Elizabeth and Frank")
         .npc("all bought a bottle - in fact they bought the last of my supplies")
         .npc("Maybe I can take your name and address, and I will")
         .npc("personally come and visit you when stocks return?")
         .player("uh... no, its ok")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(SALES);
             }
         });
    }

    private void outOfStock(Conversation c) {
        c.npc("I'm afraid I am totally out of stock at the moment")
         .npc("After my successful trip to the Sinclair's House the other day")
         .npc("but don't worry, our factories are working overtime")
         .npc("to produce Peter Potters Patented Multi Purpose Poison")
         .npc("possibly the finest multi purpose poison and cleaner yet")
         .npc("available to the general market. And its unique fragrance")
         .npc("makes it the number one choice for cleaners, and exterminators")
         .npc("the whole country over");
    }

    private void theSmell(Conversation c) {
        c.npc("hmmm... yes, that smells exactly like my")
         .npc("Patented Multi Purpose Poison, but I don't see how it could be")
         .npc("It quite clearly says on the label of all bottles")
         .npc("not to be taken internally - extremely poisonous")
         .player("Perhaps someone else put it in his wine?");
    }
}
