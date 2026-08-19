import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Multicannon;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;

/**
 * Dwarf cannon. Released 27 May 2003, written by Thomas Woode.
 *
 * The dwarven black guard hold the mines north of the fishing guild and are
 * losing them to goblins coming up out of the southern forest. Five favours,
 * each one asked as though it were the last:
 *
 *     Dwarf commander 771 (605,467) -- six railings
 *     broken railings, wall ids 181..186, one placement each:
 *         181 (603,473)  182 (609,478)  183 (616,474)
 *         184 (622,477)  185 (625,476)  186 (624,462)
 *     Watch tower 980 (617,490), ladder (616,492), dwarf remains 1046 upstairs
 *     Goblin foot prints 1164 lead to the cave at (576,523)
 *     crates 986 x41 and 987 x1 (620,3313) -- 987 is Lollk's
 *     multicannon 994 (601,468) in the shed, and the tool kit 1055
 *     Dwarf Cannon engineer 770 (275,493) -- notes 1056 and mould 1057
 *
 * The stage is a bit set rather than a ladder, because the six railings are
 * fixed in any order and the four cannon parts likewise. Bit 0 is the quest
 * itself, bits 1-6 the railings, bits 11-14 the cannon parts, and the single
 * bits in between are the errands. Every bit set is the finish, so the order
 * within each group does not matter and none of it can be done twice.
 *
 * Deviations:
 *
 *  - Lollk is untied but never appears. Npc 695 exists in the definitions and
 *    has dialogue, but Jagex placed him nowhere -- he was spawned by the crate
 *    at the moment it was searched. Nothing in this server spawns an npc into
 *    the world from a quest, so his lines are spoken from the crate instead and
 *    he is described as running for home. His NPC_TALK is still wired up, so if
 *    a spawn is ever added he behaves correctly.
 *
 *  - The dwarf remains can be picked up twice. Jagex answered a second pickup
 *    with "carrying one 'dwarfs remains' is bad enough"; item pickup here is
 *    not something a quest can refuse, only observe.
 *
 *  - Nulodion's notes are read into the chat box. The real item opened a
 *    parchment interface; the server cannot open one, so the three lines are
 *    printed instead.
 *
 *  - After the quest the engineer sells the cannon, as Jagex had him do: the
 *    whole thing for 750,000 through the dialogue below, or the four parts,
 *    the mould and the manual over the counter from his shop. The shop is a
 *    location box round his hut in Shops.xml; the dialogue opens it, since
 *    ShopKeeper cannot ask whether a quest is finished. The cannon itself is
 *    in Multicannon.java -- assembly, firing and the replacement he owes a
 *    player whose cannon was lost.
 *
 *  - The engineer's door is not locked before the quest, so he can be spoken to
 *    early; he gives nothing away.
 *
 *  - The cannon repair messages are written here rather than recorded. Every
 *    other line in this class is Jagex's, from the transcripts; the four
 *    parts -- pipe, barrel, axle and shaft -- are named by the walkthrough but
 *    the wording of the repair itself was never written down.
 */
public class DwarfCannon extends Quest {

    public final static int UID = Quests.DWARF_CANNON;

    private static final int COMMANDER = 771, ENGINEER = 770, LOLLK_NPC = 695;

    /** The six broken railings, in bit order. Each id stands in one place. */
    private static final int[] BROKEN_RAILINGS = { 181, 182, 183, 184, 185, 186 };
    /** The sound stretch of the same fence. */
    private static final int GOOD_RAILING = 193;

    private static final int CRATE = 986, LOLLK_CRATE = 987;
    private static final int CANNON = 994, CANNON_X = 601, CANNON_Y = 468;

    /**
     * The two ends of the goblin cave, and the reason they need naming here at
     * all.
     *
     * Every other way in or out of this quest's map is a ladder, and ObjectAction
     * carries a generic handler for those: anything whose command is "climb-up"
     * or "climb-down" is moved a floor by coordModifier, which is why the watch
     * tower (981 up, 985 down) has always worked without a line of quest code.
     *
     * These two are not ladders. 982's command is "enter" and 983's is "climb",
     * neither of which the generic handler matches, and nothing else claimed
     * them -- so both were live objects with a working right-click menu and no
     * effect whatsoever. The cave could not be entered, which stops the quest
     * dead at the point the footprints send you there, and the mudpile that
     * climbs back out did nothing either, so reaching the crates by any other
     * means would have left the player walled in underground.
     *
     * Each id stands in exactly one place, and the destinations are Jagex's.
     */
    private static final int CAVE = 982, CAVE_X = 576, CAVE_Y = 523;
    private static final int CAVE_TO_X = 578, CAVE_TO_Y = 3356;
    private static final int MUDPILE = 983, MUDPILE_X = 577, MUDPILE_Y = 3355;
    private static final int MUDPILE_TO_X = 578, MUDPILE_TO_Y = 521;

    private static final int RAILING = 1042, REMAINS = 1046, TOOL_KIT = 1055;
    private static final int NOTES = 1056, MOULD = 1057;

    private static final String[] PARTS = { "pipe", "barrel", "axle", "shaft" };

    private static final int CRAFTING = 12, SMITHING = 13, HITS = 3;

    private static final int STARTED = 1;
    private static final int RAILS_ALL = 126;      /* bits 1-6 */
    private static final int TOWER = 128;
    private static final int SHOWN_REMAINS = 256;
    private static final int FOUND_LOLLK = 512;
    private static final int GOT_TOOLS = 1024;
    private static final int PARTS_ALL = 30720;    /* bits 11-14 */
    private static final int CANNON_WORKS = 32768;
    private static final int GOT_NOTES = 65536;
    private static final int FINISHED = 131071;

    public DwarfCannon(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Dwarf Cannon");
        setFinalStage(FINISHED);
        associateNpc(COMMANDER);
        associateNpc(ENGINEER);
        associateNpc(LOLLK_NPC);
        for (int i = 0; i < BROKEN_RAILINGS.length; i++) {
            associateDoor(BROKEN_RAILINGS[i]);
        }
        /* The intact stretch too, so searching it says what Jagex said rather
           than nothing. Every placement of 193 is in this one fence. */
        associateDoor(GOOD_RAILING);
        /* Both crate ids stand only in the goblin cave. */
        associateObject(CRATE);
        associateObject(LOLLK_CRATE);
        /* The way in and the way back out. Both are single placements, but
           they are named by position anyway: it is the pairing that matters
           and a bare id would not show it. */
        associateObject(CAVE, CAVE_X, CAVE_Y);
        associateObject(MUDPILE, MUDPILE_X, MUDPILE_Y);
        /* Object 994 also stands twice at the black guard base as scenery. */
        associateObject(CANNON, CANNON_X, CANNON_Y);
        associateItem(NOTES);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("The dwarven black guard are losing their mines to goblin raids; mend their defences, find the missing dwarf child, and get their broken multicannon firing again.");
        setStartPoint("The black guard mines north of the fishing guild");
        setSpeakTo("Dwarf commander");
        rewardExp(CRAFTING, 250, 50);
        rewardOther("The right to buy a dwarf multicannon and ammunition from the Dwarf Cannon engineer");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Dwarf cannon quest");
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

    private boolean railsDone() {
        return has(RAILS_ALL);
    }

    private boolean partsDone() {
        return has(PARTS_ALL);
    }

    private void hurt(int damage) {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(1, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
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
            switch (((Npc) entity).getID()) {
                case COMMANDER: commander((Npc) entity); return;
                case ENGINEER:  engineer((Npc) entity); return;
                default:        lollk((Npc) entity); return;
            }
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND && ((InvItem) entity).getID() == NOTES) {
                readNotes();
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT2) {
            railing(object);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case CANNON:      cannon(); return;
            case LOLLK_CRATE: lollkCrate(); return;
            case CRATE:       emptyCrate(); return;
            case CAVE:        enterCave(); return;
            case MUDPILE:     leaveCave(); return;
        }
    }

    /**
     * Not gated on quest progress, deliberately. The cave is an ordinary hole
     * in the ground that happens to have goblins in it; nothing in the quest
     * says it is sealed, and the reference implementation lets anyone in. What
     * the quest gates is the crate inside, which is where it belongs.
     */
    private void enterCave() {
        getOwner().getActionSender().sendMessage("you cautiously enter the cave");
        getOwner().teleport(CAVE_TO_X, CAVE_TO_Y, false);
    }

    private void leaveCave() {
        getOwner().getActionSender().sendMessage("you climb the mudpile");
        getOwner().teleport(MUDPILE_TO_X, MUDPILE_TO_Y, false);
    }

    // ---------------------------------------------------------- the fence --

    private void railing(GameObject wall) {
        Player p = getOwner();
        int bit = 0;
        for (int i = 0; i < BROKEN_RAILINGS.length; i++) {
            if (BROKEN_RAILINGS[i] == wall.getID()) {
                bit = 2 << i;
            }
        }
        p.getActionSender().sendMessage("you search the railing");
        if (bit == 0 || !questStarted()) {
            p.getActionSender().sendMessage("but find nothing of interest");
            return;
        }
        if (has(bit)) {
            p.getActionSender().sendMessage("you have already fixed this railing");
            return;
        }
        p.getActionSender().sendMessage("one railing is broken and needs to be replaced");
        if (!holding(RAILING)) {
            p.getActionSender().sendMessage("but you have nothing to replace it with");
            return;
        }
        final int fixed = bit;
        new Conversation(p, null)
            .options(new Choice("try to replace the railing", "leave it be") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.message("you attempt to replace the missing railing");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            replace(fixed);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * One attempt. Crafting decides it; the transcript has the player cut
     * themselves on a failure but keep the railing, which is why six are enough
     * for six gaps however badly it goes.
     */
    private void replace(int bit) {
        Player p = getOwner();
        if (!holding(RAILING)) {
            return;
        }
        if (DataConversions.random(1, 100) > 40 + p.getCurStat(CRAFTING)) {
            p.getActionSender().sendMessage("but you fail and cut yourself trying");
            hurt(3);
            return;
        }
        p.getInventory().remove(RAILING, 1);
        p.getActionSender().sendInventory();
        set(bit);
        p.getActionSender().sendMessage("you replace the railing with no problems");
        if (railsDone()) {
            p.getActionSender().sendMessage("@gre@That was the last of the broken railings");
        }
    }

    // -------------------------------------------------------- goblin cave --

    private void emptyCrate() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the crate");
        p.getActionSender().sendMessage("but it's empty");
    }

    private void lollkCrate() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the crate");
        if (!has(SHOWN_REMAINS) || has(FOUND_LOLLK)) {
            p.getActionSender().sendMessage("but it's empty");
            return;
        }
        p.getActionSender().sendMessage("inside you see a dwarf child tied up");
        p.getActionSender().sendMessage("you untie the child");
        p.getActionSender().sendMessage("Lollk: thank the heavens, you saved me");
        p.getActionSender().sendMessage("Lollk: i thought i'd be goblin lunch for sure");
        p.getActionSender().sendMessage("Lollk: i think so, i'd better run of home");
        p.getActionSender().sendMessage("@gre@The dwarf child scrambles out and runs for the tunnel");
        set(FOUND_LOLLK);
    }

    private void lollk(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("thank the heavens, you saved me")
            .npc("i thought i'd be goblin lunch for sure")
            .player("are you ok?")
            .npc("i think so, i'd better run of home")
            .player("that's right , you get going, i'll catch up")
            .npc("thanks again brave adventurer")
            .start();
    }

    // -------------------------------------------------------- the cannon --

    private void cannon() {
        Player p = getOwner();
        if (!has(GOT_TOOLS)) {
            p.getActionSender().sendMessage("you inspect the cannon");
            p.getActionSender().sendMessage("it's in pieces, and none of your business");
            return;
        }
        if (partsDone()) {
            p.getActionSender().sendMessage("you inspect the cannon");
            p.getActionSender().sendMessage("it seems to be in working order");
            return;
        }
        if (!holding(TOOL_KIT)) {
            p.getActionSender().sendMessage("you inspect the cannon");
            p.getActionSender().sendMessage("you need the tool kit to do anything about it");
            return;
        }
        int index = 0;
        while (has(2048 << index)) {
            index++;
        }
        p.getActionSender().sendMessage("you inspect the cannon");
        p.getActionSender().sendMessage("the " + PARTS[index] + " is damaged");
        p.getActionSender().sendMessage("you set to work with the tool kit");
        int chance = 25 + (((p.getCurStat(CRAFTING) + p.getCurStat(SMITHING)) * 55) / 100);
        if (DataConversions.random(1, 100) > chance) {
            p.getActionSender().sendMessage("but you can't get it to fit");
            return;
        }
        set(2048 << index);
        p.getActionSender().sendMessage("you repair the " + PARTS[index]);
        if (partsDone()) {
            p.getActionSender().sendMessage("@gre@The cannon looks to be in working order");
        }
    }

    private void readNotes() {
        Player p = getOwner();
        p.getActionSender().sendMessage("the note reads....");
        p.getActionSender().sendMessage("Ammo for the dwarf multi cannon must be made from steel bars");
        p.getActionSender().sendMessage("The bars must be heated in a furnace and used with the cannon ball mould.");
        p.getActionSender().sendMessage("Due to the cannon ball's extreame weight only so many can be carried before one must rest");
    }

    // ----------------------------------------------------- Dwarf commander --

    private void commander(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("well, hello there, how you doing?")
                .player("not bad, yourself?")
                .npc("i'm great, the goblins can't get close with this cannon blasting at them")
                .start();
            return;
        }
        if (!questStarted()) {
            offer(npc);
            return;
        }
        if (!railsDone()) {
            railProgress(npc);
            return;
        }
        if (!has(TOWER)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello again traveller")
                .npc("how are you doing with those railings?")
                .player("i'm getting there")
                .npc("the goblins seemed to have stopped getting in")
                .npc("i think you've done the job")
                .player("good stuff")
                .npc("could you do me one more favour?")
                .npc("i need you to go check up on a guard")
                .npc("he should be in the black guard watch tower just to the south of here")
                .npc("he should have reported in by now")
                .player("ok, i'll see what i can find out")
                .npc("thanks traveller")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(TOWER);
                    }
                })
                .start();
            return;
        }
        if (!has(SHOWN_REMAINS)) {
            remains(npc);
            return;
        }
        if (!has(FOUND_LOLLK)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("traveller have you managed to find the goblins base?")
                .player("not yet i'm afraid, but i'll keep looking")
                .start();
            return;
        }
        if (!has(GOT_TOOLS)) {
            toolKit(npc);
            return;
        }
        if (!partsDone()) {
            new Conversation(p, npc)
                .npc("how are doing in there bold adventurer?")
                .npc("we've been trying our best with that thing")
                .npc("but i just haven't got the patience")
                .player("it's not an easy job, but i'm getting there")
                .npc("good stuff, let me know if you have any luck")
                .npc("if we manage to get that thing working...")
                .npc("those goblins will be know trouble at all")
                .start();
            return;
        }
        if (!has(CANNON_WORKS)) {
            fixed(npc);
            return;
        }
        if (!has(GOT_NOTES)) {
            new Conversation(p, npc)
                .player("hi again")
                .npc("hello traveller")
                .npc("any word from the Cannon engineer?")
                .player("not yet")
                .npc("the black guard camp is just south of the ice mountain")
                .npc("the quicker we can get some ammo for this thing..")
                .npc(".. the quicker those goblins will leave us be")
                .player("i'll get to it")
                .start();
            return;
        }
        if (!holding(NOTES) || !holding(MOULD)) {
            new Conversation(p, npc)
                .player("hi")
                .npc("hello traveller, any word from the Cannon engineer?")
                .player("yes, i have spoken to him")
                .player("he gave me some items to give you...")
                .player("but i seem to have lost something")
                .npc("if you could go back and get another, i'd appreciate it")
                .player("ok then")
                .start();
            return;
        }
        handover(npc);
    }

    private void offer(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello")
            .npc("hello traveller, i'm pleased to see you")
            .npc("we were hoping to find an extra pair of hands")
            .npc("that's if you don't mind helping?")
            .player("why, what's wrong?")
            .npc("as part of the dwarven black guard..")
            .npc("...it is our duty to protect these mines")
            .npc("but we just don't have the man power")
            .npc("could you help?")
            .options(new Choice("yeah, i'd love to help", "i'm sorry, i'm too busy mining") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("ok then, we'll have find someone else");
                        return;
                    }
                    c.npc("thankyou, we have no time to waste")
                     .npc("the goblins have been attacking from the forests to the south")
                     .npc("they manage to get through the broken railings")
                     .npc("could you please replace them with these new ones")
                     .player("sounds easy enough")
                     .message("the Dwarf commander gives you six railings")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(STARTED);
                             for (int i = 0; i < 6; i++) {
                                 c.getPlayer().getInventory().add(new InvItem(RAILING, 1));
                             }
                             c.getPlayer().getActionSender().sendInventory();
                         }
                     })
                     .npc("let me know once you've fixed the railings")
                     .player("ok , commander");
                }
            })
            .start();
    }

    private void railProgress(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .player("hello")
            .npc("hello again traveller")
            .npc("how are you doing with those railings?")
            .player("i'm getting there")
            .npc("the goblins are still getting in")
            .npc("so there must still be some broken railings")
            .player("don't worry, i'll find them soon enough");
        if (!holding(RAILING)) {
            c.player("but i'm out of railings")
             .npc("ok, we've got plenty")
             .message("the Dwarf commander gives you another railing")
             .give(new InvItem(RAILING, 1));
        }
        c.start();
    }

    private void remains(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .player("hello")
            .npc("have you been to the watch tower yet?")
            .player("yes, i went up but there was no one")
            .npc("that's strange, gilob never leaves his post");
        if (!holding(REMAINS)) {
            c.npc("his son was also with him, its too strange")
             .npc("can you return and look for clues?")
             .player("ok then");
            c.start();
            return;
        }
        c.player("i may have some bad news for you commander")
         .message("you show the Dwarf commander the remains")
         .take(REMAINS, 1)
         .npc("what's this?, oh no , it can't be!")
         .player("i'm sorry, it looks like the goblins got him")
         .npc("noooo... those..those animals")
         .npc("but where's gilobs son?, he was also there")
         .player("the goblins must have taken him")
         .npc("please traveller, seek out the goblins base..")
         .npc("...and return the lad to us")
         .npc("they must sleep somewhere!")
         .player("ok, i'll see if i can find their hide out")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(SHOWN_REMAINS);
             }
         })
         .start();
    }

    private void toolKit(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello, has lollk returned yet?")
            .npc("he has, and i thank you from the bottom of my heart..")
            .npc("...with out you he'd be goblin barbecue")
            .player("always a pleasure to help")
            .npc("in that case i have one more favour to ask you")
            .npc("as you've seen, our defences are too weak against those goblins")
            .npc("the black guard have sent us a cannon to help the situation")
            .player("sounds good")
            .npc("unfortunatly we're having trouble fixing the thing")
            .npc("the cannon is stored in our shed")
            .npc("if you could fix it, it would be a great help")
            .options(new Choice("ok, i'll see what i can do", "sorry, i've done enough for today") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("fair enough, take care traveller");
                        return;
                    }
                    c.npc("that's great,you'll need this")
                     .message("the Dwarf commander gives you a tool kit")
                     .give(new InvItem(TOOL_KIT, 1))
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(GOT_TOOLS);
                         }
                     })
                     .npc("let me know how you get on");
                }
            })
            .start();
    }

    private void fixed(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello again")
            .npc("hello there traveller, how's things?")
            .player("well, i think i've done it, take a look")
            .npc("really!")
            .message("the Dwarf commander pops into the shed to take a closer look")
            .npc("well i don't believe it, it seems to be in working order")
            .player("not bad for an adventurer")
            .npc("not bad at all, your effort is appreciated my friend")
            .npc("now, if i could only figure what the thing uses as ammo")
            .npc("the black guard forgot to send instructions")
            .npc("i know i said that was the last favour..but..")
            .player("what now?")
            .npc("i can't leave this post, could you go to the black guard..")
            .npc("..base and find out what this thing actually shoots?")
            .options(new Choice("ok then, just for you", "sorry, i've really done enough") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("fair enough");
                        return;
                    }
                    c.npc("you're a good adventurer, we were lucky to find you")
                     .npc("the base is located just south of the ice mountain")
                     .npc("you'll need to speak to the dwarf Cannon engineer")
                     .npc("he's the weapons development chief for the black guard")
                     .npc("so if anyone knows how to fire that thing, it'll be him")
                     .player("ok, i'll see what i can do")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(CANNON_WORKS);
                         }
                     });
                }
            })
            .start();
    }

    private void handover(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hi")
            .npc("hello traveller, any word from the Cannon engineer?")
            .player("yes, i have spoken to him")
            .player("he gave me these to give to you")
            .message("you hand the Dwarf commander the mould and the notes")
            .take(NOTES, 1)
            .take(MOULD, 1)
            .npc("aah, of course, we make the ammo")
            .npc("this is great, now we will be able to defend ourselves")
            .npc("i don't know how to thank you")
            .player("you could give me a cannon")
            .npc("hah, you'd be lucky, those things are worth a fortune")
            .npc("hmmm, now i think about it the Cannon engineer may be able to help")
            .npc("he controls production of the cannons")
            .npc("he won't be able to give you one")
            .npc("but for the right price, i'm sure he'll sell one to you")
            .player("hmmm, sounds interesting")
            .npc("take care of yourself traveller, and thanks again")
            .player("you take care too")
            .then(new Effect() {
                public void run(Conversation c) {
                    setStage(FINISHED);
                }
            })
            .start();
    }

    // ------------------------------------------------ Dwarf Cannon engineer --

    private void engineer(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            afterwards(npc);
            return;
        }
        if (!has(CANNON_WORKS)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("can i help you?")
                .player("no, i'm just looking around")
                .npc("well don't touch anything, this is black guard property")
                .start();
            return;
        }
        if (!has(GOT_NOTES)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("can i help you?")
                .player("the Dwarf commander sent me, he's having trouble with his cannon")
                .npc("of course, we forgot to send the ammo mould")
                .player("it fires a mould?")
                .npc("don't be silly, the ammo's made by using a mould")
                .npc("here, take these to him, the instructions explain everthing")
                .player("that's great, thanks")
                .npc("thank you adventurer, the dwarf black guard will remember this")
                .message("the Cannon engineer gives you some notes and a mould")
                .give(new InvItem(NOTES, 1))
                .give(new InvItem(MOULD, 1))
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(GOT_NOTES);
                    }
                })
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc).player("hello again");
        if (!holding(NOTES)) {
            c.player("i've lost the notes")
             .npc("here take these")
             .message("the Cannon engineer gives you some more notes")
             .give(new InvItem(NOTES, 1));
        }
        if (!holding(MOULD)) {
            c.player("i've lost the cannon ball mould")
             .npc("deary me, you are trouble")
             .npc("here take this one")
             .message("the Cannon engineer gives you another mould")
             .give(new InvItem(MOULD, 1));
        }
        c.npc("so has the commander figured out how to work the cannon?")
         .player("not yet, but i'm sure he will")
         .npc("if you can get those items to him it'll help")
         .start();
    }

    /** The whole cannon, over the counter, and what it costs. */
    private static final int COINS = 10, CANNON_PRICE = 750000;

    /** Open Nulodion's Cannon Parts, which stands on the engineer's own tile. */
    private void openShop(final Npc npc) {
        Player p = getOwner();
        Shop shop = World.getWorld().getShop(npc);
        if (shop == null) {
            return;
        }
        p.setAccessingShop(shop);
        p.getActionSender().showShop(shop);
    }

    private void afterwards(final Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello")
            .npc("hello traveller, how's things?")
            .player("not bad thanks, yourself?")
            .npc("i'm good, just working hard as usual")
            .options(new Choice("i was hoping you might sell me a cannon?",
                                "i want to know more about the cannon?",
                                "i've lost my cannon",
                                "well, take care of yourself then") {
                public void picked(int option, Conversation c) {
                    if (option == 3) {
                        return;
                    }
                    if (option == 1) {
                        c.npc("there's only so much i can tell you adventurer")
                         .npc("we've been working on this little beauty for some time now")
                         .player("is it effective?")
                         .npc("in short bursts it's very effective, the most destructive weapon to date")
                         .npc("the cannon automatically targets monsters close by")
                         .npc("you just have to make the ammo and let rip");
                        return;
                    }
                    if (option == 2) {
                        lostIt(c);
                        return;
                    }
                    sellIt(c, npc);
                }
            })
            .start();
    }

    /**
     * The sale.
     *
     * Four parts, a mould and a manual for 750,000, or the counter for anyone
     * who wants one piece at a time. Both endings are Jagex's, and the third
     * option -- ammo and instructions -- opens the same counter, because the
     * mould and the manual are on it.
     */
    private void sellIt(Conversation c, final Npc npc) {
        c.npc("hmmm")
         .npc("i shouldn't really, but as you helped us so much")
         .npc("well, i could sort something out")
         .npc("i'll warn you though, they don't come cheap")
         .player("how much?")
         .npc("for the full set up.. 750 000 coins")
         .npc("or i can sell you the seperate parts for 200 000 each")
         .player("that's not cheap")
         .options(new Choice("ok, i'll take a cannon please",
                             "can i look at the seperate parts please",
                             "have you any ammo or instructions to sell?",
                             "sorry, that's too much for me") {
            public void picked(int option, Conversation c) {
                if (option == 3) {
                    c.npc("fair enough, it's too much for most of us");
                    return;
                }
                if (option == 1) {
                    c.npc("of course!").then(new Effect() {
                        public void run(Conversation c) {
                            openShop(npc);
                        }
                    });
                    return;
                }
                if (option == 2) {
                    c.npc("yes, of course").then(new Effect() {
                        public void run(Conversation c) {
                            openShop(npc);
                        }
                    });
                    return;
                }
                c.npc("ok then, but keep it quiet..")
                 .npc("this thing's top secret");
                Player p = getOwner();
                if (p.getInventory().countId(COINS) < CANNON_PRICE) {
                    c.player("oops, i don't have enough money")
                     .npc("sorry, i can't go any lower than that");
                    return;
                }
                /* Six items out and the coins in, so the room has to be there
                   before anything moves -- the four parts, the mould and the
                   manual, less the coin slot the payment empties. */
                if (Inventory.MAX_SIZE - p.getInventory().size() < 5) {
                    c.npc("you'll need to make some room first");
                    return;
                }
                c.take(COINS, CANNON_PRICE)
                 .message("you give the Cannon engineer 750 000 coins")
                 .message("he gives you the four parts that make the cannon")
                 .message("a ammo mould and an instruction manual")
                 .give(new InvItem(Multicannon.BASE, 1))
                 .give(new InvItem(Multicannon.STAND, 1))
                 .give(new InvItem(Multicannon.BARRELS, 1))
                 .give(new InvItem(Multicannon.FURNACE, 1))
                 .give(new InvItem(Multicannon.AMMO_MOULD, 1))
                 .give(new InvItem(Multicannon.MANUAL, 1))
                 .npc("there you go, you be carefull with that thing")
                 .player("will do, take care mate")
                 .npc("take care adventurer");
            }
         });
    }

    /**
     * The warranty claim.
     *
     * "i'm only allowed to replace cannons... that were stolen in action" is
     * the whole rule, and the server knows which those are: a cannon that was
     * standing when its owner was taken out of the world, rather than one they
     * dropped or sold. Multicannon keeps that mark; it does not survive a
     * restart, and a player owed one across a restart has to buy again.
     */
    private void lostIt(Conversation c) {
        Player p = getOwner();
        c.player("that's unfortunate...but don't worry, i can sort you out");
        if (!Multicannon.isOwedReplacement(p)) {
            c.npc("oh dear, i'm only allowed to replace cannons...")
             .npc("...that were stolen in action")
             .npc("i'm sorry but you'll have to buy a new set");
            return;
        }
        if (Inventory.MAX_SIZE - p.getInventory().size() < 4) {
            c.npc("you'll need to make some room first");
            return;
        }
        c.message("the dwarf gives you a new cannon")
         .give(new InvItem(Multicannon.BASE, 1))
         .give(new InvItem(Multicannon.STAND, 1))
         .give(new InvItem(Multicannon.BARRELS, 1))
         .give(new InvItem(Multicannon.FURNACE, 1))
         .then(new Effect() {
            public void run(Conversation c) {
                Multicannon.replacementGiven(getOwner());
            }
         })
         .npc("keep that quite or i'll be in real trouble")
         .player("thanks alot")
         .npc("no worries");
    }
}
