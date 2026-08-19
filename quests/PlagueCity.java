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
 * Plague city. Released 27 August 2002, written by Paul Gower and Thomas Woode.
 * Episode one of the multi-part quest, and the reason a wall runs down the
 * middle of Ardougne.
 *
 * Edmond's daughter Elena crossed into West Ardougne to help the plague victims
 * three weeks ago and has not come back. There is no legal way over the wall,
 * so the way in is under it:
 *
 *     Edmond 437     (620,581) his garden, and (625,3417) down in the sewer
 *     Alrena 450     (615,581), her cupboard 452 at (614,579)
 *     Dug up soil 447 (620,581) -- four buckets of water, then a spade
 *     Pile of mud 448 (620,3414) climbs back out
 *     large Sewer pipe 449 (636,3422) -- rope, then Edmond pulls the grill
 *
 * and out into West Ardougne:
 *
 *     Jethick 443    (634,589), with a book to return
 *     Rehnison door 122 (645,569); Ted 446, Martha 447, Milli 449 upstairs
 *     abandoned house door 123 (637,606), guarded, then stairs 42 (637,608)
 *     Clerk 452 (651,585), office door 121 (648,585), Bravek 454 (646,586)
 *     Head mourner 469 (629,595)
 *     barrel 456 (639,3440) hides the little key; gate 457 (637,3447)
 *     Elena 465 (637,3449)
 *
 * The hangover cure is not in here. Chocolate dust ground from a bar, stirred
 * into a bucket of milk and finished with snape grass is a fixed recipe with no
 * quest item in it, so it went into InvUseOnItem where every other recipe
 * lives; grinding a chocolate bar went into doGrind alongside the unicorn horn.
 * Neither existed before. Reading the scruffy note (item 781) does live here,
 * because the note is Bravek's and only this quest ever hands it out.
 *
 * Deviations:
 *
 *  - The gasmask is not enforced. Jagex killed anyone who walked around West
 *    Ardougne without it, which needs something watching every player's tile
 *    every tick; this server has no such thing and inventing one for a single
 *    quest would be the wrong place to put it. Alrena still makes the mask,
 *    and still hides a spare in her cupboard.
 *
 *  - The mourners say nothing. There are eight mourner ids scattered across
 *    both Ardougnes and they are wanted by Biohazard, Watchtower and
 *    Underground pass as well as by this quest -- an npc that belongs to two
 *    quests can live in neither, so their dialogue belongs in an NpcHandler
 *    that reads quest stages, the way Aggie and Ned do. The one mourner
 *    interaction the quest cannot do without, the guard on the plague house,
 *    is answered by the door itself.
 *
 *  - Elena speaks through her cell bars. Jagex played her plea when the player
 *    tried the gate; here it is on Elena, because a conversation needs an npc
 *    to hang the speech on and the gate handler has no way to find her.
 *
 *  - Ardougne teleport is gated on reading the magic scroll Edmond hands over,
 *    which is where Jagex put it -- finishing the quest is not enough, and the
 *    server's own refusal message says otherwise. That wording is Jagex's too
 *    and is kept. Reading a second scroll does nothing but destroy it, and
 *    Edmond hands out replacements for as long as you keep asking.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class PlagueCity extends Quest {

    public final static int UID = Quests.PLAGUE_CITY;

    private static final int EDMOND = 437, JETHICK = 443;
    private static final int TED = 446, MARTHA = 447, MILLI = 449;
    private static final int ALRENA = 450, CLERK = 452, BRAVEK = 454;
    private static final int ELENA = 465, HEAD_MOURNER = 469;

    private static final int SOIL = 447, SOIL_X = 620, SOIL_Y = 581;
    /**
     * The pile of mud under Edmond's garden, and where climbing it puts you.
     *
     * Not the tile below the soil: the garden's south fence runs along
     * y=581/582 and its east fence along x=620/621, so (620,582) is on the far
     * side of both and the climb came up in the street rather than the garden.
     * (619,580) is inside, a step from the hole.
     */
    private static final int MUD = 448, MUD_EXIT_X = 619, MUD_EXIT_Y = 580;
    private static final int PIPE = 449;
    private static final int CUPBOARD = 452;
    private static final int BARREL = 456;
    private static final int CELL_GATE = 457;

    private static final int OFFICE_DOOR = 121, OFFICE_X = 648, OFFICE_Y = 585;
    private static final int REHNISON_DOOR = 122, REHNISON_X = 645, REHNISON_Y = 569;
    private static final int HOUSE_DOOR = 123, HOUSE_X = 637, HOUSE_Y = 606;

    private static final int WATER = 50, BUCKET = 21, SPADE = 211, ROPE = 237;
    private static final int SCROLL = 752, BERRIES = 765, GASMASK = 766;
    private static final int PICTURE = 767, BOOK = 768, CURE = 771;
    private static final int WARRANT = 775, KEY = 780, NOTE_ITEM = 781;

    /* Beside the Pile of mud that climbs back out, not inside the wall above
     * it: (620,3413) is solid, and dropping into the sewer there left the
     * player with no tile to stand on. */
    private static final int SEWER_X = 621, SEWER_Y = 3414;
    private static final int WEST_X = 635, WEST_Y = 590;
    private static final int MINING = 14;

    private static final int STARTED = 1;
    private static final int MASK = 2;
    /* 3, 4 and 5 are one, two and three buckets of water. */
    private static final int SOAKED = 6;
    private static final int SEWER = 7;
    private static final int ROPED = 8;
    private static final int GRILL = 9;
    private static final int BOOK_GIVEN = 10;
    private static final int INSIDE = 11;
    private static final int PARENTS = 12;
    private static final int TOLD = 13;
    private static final int REFUSED = 14;
    private static final int OFFICE = 15;
    private static final int NOTE = 16;
    private static final int CURED = 17;
    private static final int WARRANTED = 18;
    private static final int FREED = 19;
    private static final int FINISHED = 20;
    /* One past the end, and the only stage that can be reached after the quest
       is over: the scroll is handed out at FINISHED, so reading it necessarily
       comes later. It is a second final stage rather than a working stage,
       because completed() is exact equality and the quest must not un-complete
       itself the moment the player reads their reward. */
    private static final int SCROLL_READ = 21;

    public PlagueCity(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Plague City");
        setFinalStage(FINISHED);
        addFinalStage(SCROLL_READ);
        associateItem(SCROLL);
        associateItem(NOTE_ITEM);
        associateNpc(EDMOND);
        associateNpc(JETHICK);
        associateNpc(TED);
        associateNpc(MARTHA);
        associateNpc(MILLI);
        associateNpc(ALRENA);
        associateNpc(CLERK);
        associateNpc(BRAVEK);
        associateNpc(ELENA);
        associateNpc(HEAD_MOURNER);
        /* Each of these stands in exactly one place in the world, so the id is
           the placement and there is nothing to hand back. */
        associateObject(SOIL);
        associateObject(MUD);
        associateObject(PIPE);
        associateObject(CUPBOARD);
        associateObject(BARREL);
        associateObject(CELL_GATE);
        associateDoor(OFFICE_DOOR);
        associateDoor(REHNISON_DOOR);
        /* Door 123 is an ordinary West Ardougne front door and stands twice.
           Only the plague house is ours. */
        associateDoor(HOUSE_DOOR, HOUSE_X, HOUSE_Y);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Epsiode 1 of our multipart quest. A shadow of disease has overcast Ardounge. Edmond's daugher Elena has gone missing in West Ardougne whilst trying to help the plague victims there. See if you can find out what's going on.");
        setStartPoint("East Ardounge");
        setSpeakTo("Edmond");
        setMissionLength("Ongoing");
        rewardExp(MINING, 175, 75);
        rewardOther("A magic scroll from Edmond; reading it unlocks the Ardougne teleport spell");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Plague city quest");
    }

    private boolean at(int stage) {
        return getStage() == stage;
    }

    private boolean past(int stage) {
        return questStarted() && getStage() >= stage;
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private void walkThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        if (door.getDirection() == 0) {
            p.teleport(door.getX(), p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
        } else {
            p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(), door.getY(), false);
        }
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    /**
     * The questions other code is allowed to ask this quest.
     *
     * "ardougne-teleport" is SpellHandler's: the teleport is learnt from the
     * scroll, not from the quest, so finishing Plague city is necessary and not
     * enough.
     *
     * The other five are the mourner outside Edmond's house, whose whole
     * conversation changes six times as the quest goes on. He belongs to no
     * quest -- Biohazard and Underground pass want the mourners too -- so he
     * lives in an NpcHandler and asks by name. See npchandler/Mourners.
     */
    public boolean reached(String key) {
        if ("ardougne-teleport".equals(key)) {
            return getStage() >= SCROLL_READ;
        }
        if ("started".equals(key)) {
            return past(STARTED);
        }
        if ("gasmask".equals(key)) {
            return past(MASK);
        }
        if ("soil-softened".equals(key)) {
            return past(SOAKED);
        }
        if ("in-sewer".equals(key)) {
            return past(SEWER);
        }
        if ("grill-removed".equals(key)) {
            return past(GRILL);
        }
        return false;
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                if (((InvItem) entity).getID() == SCROLL) {
                    readScroll();
                } else if (((InvItem) entity).getID() == NOTE_ITEM) {
                    readNote();
                }
            }
            return;
        }
        if (entity instanceof Npc) {
            if (trigger != QuestTrigger.NPC_TALK && trigger != QuestTrigger.ITEM_ON_NPC) {
                return;
            }
            Npc npc = (Npc) entity;
            switch (npc.getID()) {
                case EDMOND:  edmond(npc); return;
                case ALRENA:  alrena(npc); return;
                case JETHICK: jethick(npc); return;
                case TED:
                case MARTHA:  rehnison(npc); return;
                case MILLI:   milli(npc); return;
                case CLERK:   clerk(npc); return;
                case BRAVEK:  bravek(npc); return;
                case ELENA:   elena(npc); return;
                default:      headMourner(npc); return;
            }
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.ITEM_ON_DOOR) {
            switch (object.getID()) {
                case OFFICE_DOOR:   officeDoor(object); return;
                case REHNISON_DOOR: rehnisonDoor(object); return;
                default:            houseDoor(object); return;
            }
        }
        switch (object.getID()) {
            case SOIL: {
                if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
                    soil(used);
                }
                return;
            }
            case MUD: {
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    getOwner().teleport(MUD_EXIT_X, MUD_EXIT_Y, false);
                }
                return;
            }
            case PIPE:     pipe(trigger, used); return;
            case CUPBOARD: cupboard(trigger); return;
            case BARREL:   barrel(trigger); return;
            case CELL_GATE: gate(object); return;
        }
    }

    // -------------------------------------------------------------- Edmond --

    private void edmond(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Ah hello again")
                .npc("And thank you again")
                .options(new Choice("Do you have any more of those scrolls?", "No problem") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("yes here you go")
                             .give(new InvItem(SCROLL, 1));
                        }
                    }
                })
                .start();
            return;
        }
        if (at(FREED)) {
            new Conversation(p, npc)
                .npc("Thank you thank you")
                .npc("Elena beat you back by minutes")
                .npc("now I said I'd give you a reward")
                .npc("What can I give you as a reward I wonder?")
                .npc("Here take this magic scroll")
                .give(new InvItem(SCROLL, 1))
                .npc("I have little use for it, but it may help you")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(FINISHED);
                    }
                })
                .start();
            return;
        }
        if (past(GRILL)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("Have you found Elena yet?")
                .player("Not yet, it's big city over there")
                .npc("I hope it's not to late")
                .start();
            return;
        }
        if (at(ROPED)) {
            new Conversation(p, npc)
                .player("I've tied the other end of this rope to the grill")
                .npc("that's done the job")
                .npc("Remember always wear the gasmask")
                .npc("otherwise you'll die over there for certain")
                .npc("and please bring my elena back safe and sound")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(GRILL);
                    }
                })
                .start();
            return;
        }
        if (at(SEWER)) {
            new Conversation(p, npc)
                .player("Edmond, I can't get through to west ardougne")
                .player("there's an iron grill blocking my way")
                .player("i can't pull it off alone")
                .npc("if you get some rope you could tie it to the grill")
                .npc("then we could both pull it from here")
                .start();
            return;
        }
        if (at(SOAKED)) {
            new Conversation(p, npc)
                .player("I've soaked the soil with water")
                .npc("that's great it should be soft enough to dig through now")
                .start();
            return;
        }
        if (past(MASK)) {
            new Conversation(p, npc)
                .player("hi Edmond, I've got the gasmask now")
                .npc("good stuff now for the digging")
                .npc("beneath are the ardougne sewers")
                .npc("there you'll find access to west ardougne")
                .npc("the problem is the soil is rock hard")
                .npc("you'll need to pour on some buckets of water to soften it up")
                .npc("I'll keep an eye out for the mourners")
                .start();
            return;
        }
        if (questStarted()) {
            if (holding(BERRIES)) {
                new Conversation(p, npc)
                    .player("hello Edmond")
                    .npc("have you got the dwellberries?")
                    .player("yes i have some here")
                    .npc("take them to my wife alrena")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello Edmond")
                    .npc("have you got the dwellberries?")
                    .player("sorry I'm afraid not")
                    .npc("you'll probably find them in mcgrubor's wood to the north")
                    .start();
            }
            return;
        }
        new Conversation(p, npc)
            .player("hello old man")
            .player("what's wrong?")
            .npc("I've got to find my daughter")
            .npc("i pray that she's still alive")
            .options(new Choice("what's happened to her?", "Well, good luck finding her") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("elena's a missionary and a healer")
                     .npc("three weeks ago she managed to cross the ardougne wall")
                     .npc("no one's allowed to cross the wall in case they spread the plague")
                     .npc("but after hearing the screams of suffering she felt she had to help")
                     .npc("she said she'd be gone for a few days but we've heard nothing since");
                    ask(c);
                }
            })
            .start();
    }

    /** The three-way menu Edmond's opening lands on, offered again after the aside. */
    private void ask(Conversation c) {
        c.options(new Choice("can i help find her?", "Tell me more about the plague",
                             "I'm sorry i have to go") {
            public void picked(int option, Conversation c) {
                if (option == 2) {
                    c.npc("ok then goodbye");
                    return;
                }
                if (option == 1) {
                    c.npc("The mourners can tell you more than me")
                     .npc("they're the only ones allowed to cross the border")
                     .npc("I do know the plague is a horrible way to go")
                     .npc("that's why elena felt she had to go help");
                    ask(c);
                    return;
                }
                c.npc("really, would you?")
                 .npc("I've been working on a plan to get over the wall")
                 .npc("but I'm too old and tired to carry it through")
                 .npc("if you're going over the first thing you'll need is protection from the plague")
                 .npc("My wife made a special gasmask for elena")
                 .npc("with dwellberries rubbed into it")
                 .npc("Dwellberries help repel the virus")
                 .npc("We need some more though")
                 .player("Where can I find these Dwellberries?")
                 .npc("the only place i know is mcgrubor's wood to the north")
                 .player("ok I'll go get some")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(STARTED);
                     }
                 });
            }
        });
    }

    // -------------------------------------------------------------- Alrena --

    private void alrena(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Thank you for rescuing my daughter")
                .npc("Elena has told me of your bravery")
                .npc("In entering a house that could have been plague infected")
                .npc("I can't thank you enough")
                .start();
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello madam")
                .npc("oh hello there")
                .player("are you ok?")
                .npc("not too bad")
                .npc("I've just got some troubles on my mind")
                .start();
            return;
        }
        if (at(STARTED)) {
            if (holding(BERRIES)) {
                new Conversation(p, npc)
                    .player("hello, Edmond has asked me to help find your daughter")
                    .npc("yes he told me")
                    .npc("I've begun making your special gas mask")
                    .npc("but i need some dwellberries to finish it")
                    .player("yes I've got some here")
                    .take(BERRIES, 1)
                    .npc("there we go all done")
                    .give(new InvItem(GASMASK, 1))
                    .npc("while in west ardougne you must wear this at all times")
                    .npc("or you'll never make it back")
                    .npc("while you two are digging I'll make a spare mask")
                    .npc("I'll hide it in the cupboard incase the mourners come in")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            setStage(MASK);
                        }
                    })
                    .start();
                return;
            }
            new Conversation(p, npc)
                .player("hello, Edmond has asked me to help find your daughter")
                .npc("yes he told me")
                .npc("I've begun making your special gas mask")
                .npc("but i need some dwellberries to finish it")
                .player("I'll try to get some")
                .npc("the best place to look is in mcgrubor's wood to the north")
                .start();
            return;
        }
        if (past(GRILL)) {
            new Conversation(p, npc)
                .player("hello alrena")
                .npc("hello, any word on elena?")
                .player("not yet I'm afraid")
                .start();
            return;
        }
        if (past(SEWER)) {
            new Conversation(p, npc)
                .player("hello alrena")
                .npc("Hi, have you managed to get through to west ardougne?")
                .player("not yet, but i should be going through soon")
                .npc("make sure you wear your mask while you are over there")
                .npc("i can't think of a worse way to die")
                .start();
            return;
        }
        if (at(SOAKED)) {
            new Conversation(p, npc)
                .player("hello again alrena")
                .npc("how's the tunnel going?")
                .player("I'm getting there")
                .npc("one of the mourners has been sniffing around")
                .npc("asking questions about you and Edmond")
                .npc("you should keep an eye out for him")
                .player("ok, thanks alrena")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello alrena")
            .npc("hello darling")
            .npc("how's that tunnel coming along?")
            .player("we're getting there")
            .npc("well I'm sure you're quicker than Edmond")
            .player("i just need to soften the soil and then we'll start digging")
            .npc("if you lose your protective clothing I've made a spare set")
            .npc("they're hidden in the cupboard incase the mourners come in")
            .start();
    }

    private void cupboard(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!past(MASK)) {
            p.getActionSender().sendMessage("You search the cupboard");
            p.getActionSender().sendMessage("It is empty");
            return;
        }
        if (holding(GASMASK)) {
            p.getActionSender().sendMessage("You search the cupboard");
            p.getActionSender().sendMessage("You already have a gasmask");
            return;
        }
        p.getActionSender().sendMessage("You search the cupboard");
        p.getActionSender().sendMessage("@gre@You find the spare gasmask Alrena hid there");
        p.getInventory().add(new InvItem(GASMASK, 1));
        p.getActionSender().sendInventory();
    }

    // --------------------------------------------------------- the scroll --

    /**
     * Reading the magic scroll, which is the whole of Ardougne teleport's
     * unlock. The scroll is destroyed either way; a second one says nothing but
     * the last line, which is Jagex's and reads as the scroll having nothing
     * left to teach.
     */
    /**
     * The scruffy note, which is the recipe and the only place the player is
     * told it.
     *
     * The note carries "read" in the item table and nothing claimed it, so the
     * command fell through to the generic handler and answered "Nothing
     * interesting happens" -- Bravek handed over the recipe and there was no
     * way to find out what it said.
     *
     * The misspellings are Jagex's and are the joke: Bravek wrote it down with
     * a hangover, and the player has to work out that "bncket of nnlk" is a
     * bucket of milk. Transcribed exactly, so do not tidy them. It is not
     * consumed by reading -- unlike the reward scroll, this one is a recipe you
     * may want to look at twice.
     *
     * A Conversation and not eight sendMessage calls, and every line prefixed
     * @que@. Sent straight, all eight arrive in the same tick: the first lines
     * scroll off the bottom of the chat box before they can be read, and they
     * land in the general history where they are mixed in with combat and chat.
     * A Conversation paces them at the usual line delay, and @que@ is Jagex's
     * own routing -- handleServerMessage sends a @que@ line to the quest
     * history tab, which is where a recipe the player will want to check twice
     * belongs.
     */
    private void readNote() {
        new Conversation(getOwner(), null)
            .message("@que@The handwriting on this note is very scruffy")
            .message("@que@as far as you can make out it says")
            .message("@que@Got a bncket of nnlk")
            .message("@que@Tlen qrind sorne lhoculate")
            .message("@que@vnith a pestal and rnortar")
            .message("@que@ald the grourd dlocolate to tho milt")
            .message("@que@fnales add 5cme snape gras5")
            .message("@que@you guess it really says something slightly different")
            .start();
    }

    private void readScroll() {
        Player p = getOwner();
        if (getStage() == FINISHED) {
            p.getActionSender().sendMessage("You memorise what is written on the scroll");
            p.getActionSender().sendMessage("You can now cast the Ardougne teleport spell");
            p.getActionSender().sendMessage("Provided you have the required runes and magic level");
            setStage(SCROLL_READ);
        }
        p.getInventory().remove(SCROLL, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("The scroll crumbles to dust");
    }

    // ------------------------------------------------------------ the dig --

    private void soil(InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        if (used.getID() == WATER) {
            if (!past(MASK)) {
                p.getActionSender().sendMessage("You have no reason to water Edmond's garden");
                return;
            }
            if (past(SOAKED)) {
                p.getActionSender().sendMessage("The soil is soft enough already");
                return;
            }
            p.getInventory().remove(WATER, 1);
            p.getInventory().add(new InvItem(BUCKET, 1));
            p.getActionSender().sendInventory();
            setStage(getStage() + 1);
            if (at(SOAKED)) {
                p.getActionSender().sendMessage("You pour the water onto the soil");
                p.getActionSender().sendMessage("@gre@The soil is now soft enough to dig");
            } else {
                p.getActionSender().sendMessage("You pour the water onto the soil");
                p.getActionSender().sendMessage("The soil is still rather hard");
            }
            return;
        }
        if (used.getID() != SPADE) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!past(SOAKED)) {
            p.getActionSender().sendMessage("The soil is far too hard to dig");
            p.getActionSender().sendMessage("You need to soften it with water first");
            return;
        }
        /* Biohazard opens with the mourners having "discovered the tunnel and
           filled it in" (Edmond's line, quoted in that quest), and Elena's
           whole wall-crossing errand exists because of it -- so the dig stops
           working the moment that quest is underway, and never comes back:
           Edmond stays "working on another tunnel" forever. Between the two
           quests the tunnel is the legitimate way back into west Ardougne. */
        if (p.getQuestManager().stageOf(Quests.BIOHAZARD) > 0) {
            p.getActionSender().sendMessage("You dig into the soft soil");
            p.getActionSender().sendMessage("The tunnel beneath has been filled in");
            return;
        }
        if (at(SOAKED)) {
            p.getActionSender().sendMessage("You dig through the soft soil");
            p.getActionSender().sendMessage("@gre@The ground gives way and you drop into the sewers");
            setStage(SEWER);
        } else {
            p.getActionSender().sendMessage("You climb down into the hole");
        }
        p.teleport(SEWER_X, SEWER_Y, false);
    }

    private void pipe(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != ROPE) {
                p.getActionSender().sendMessage("Nothing interesting happens");
                return;
            }
            if (past(GRILL)) {
                p.getActionSender().sendMessage("The grill is already off");
                return;
            }
            if (!at(SEWER)) {
                p.getActionSender().sendMessage("You have no reason to do that");
                return;
            }
            p.getInventory().remove(ROPE, 1);
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You tie the rope around the iron grill");
            p.getActionSender().sendMessage("@gre@Edmond should be able to help you pull now");
            setStage(ROPED);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!past(GRILL)) {
            p.getActionSender().sendMessage("An iron grill is bolted across the pipe");
            if (at(ROPED)) {
                p.getActionSender().sendMessage("You should go and tell Edmond about the rope");
            }
            return;
        }
        p.getActionSender().sendMessage("You crawl through the pipe");
        p.teleport(WEST_X, WEST_Y, false);
    }

    // ------------------------------------------------------------- Jethick --

    private void jethick(Npc npc) {
        Player p = getOwner();
        if (past(BOOK_GIVEN)) {
            new Conversation(p, npc)
                .npc("Hello I don't recognise you")
                .npc("We don't get many newcomers around here")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hello I don't recognise you")
            .npc("We don't get many newcomers around here")
            .options(new Choice("Hi I'm looking for a woman from east Ardougne",
                                "So who's in charge here?") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Well King tyras has wandered off in to the west kingdom")
                         .npc("He doesn't care about the mess he's left here")
                         .npc("The city warder Bravek is in charge at the moment")
                         .npc("He's not much better");
                        return;
                    }
                    c.npc("East Ardougnian women are easier to find in east Ardougne")
                     .npc("Not many would come to west ardougne to find one")
                     .npc("Any particular woman you have in mind?")
                     .player("Yes a lady called Elena")
                     .npc("What does she look like?");
                    if (!holding(PICTURE)) {
                        c.player("Um brown hair, in her twenties")
                         .npc("Hmm that doesn't narrow it down a huge amount")
                         .npc("I'll need to know more than that");
                        return;
                    }
                    c.npc("Ah yes I recognise her")
                     .npc("She was over here to help aid plague victims")
                     .npc("I think she is staying over with the Rehnison family")
                     .npc("They live in the small timbered building at the far north side of town")
                     .npc("I've not seen her around here in a while mind you")
                     .npc("I don't suppose you could run me a little errand?")
                     .npc("While you are over there")
                     .npc("I borrowed this book from them")
                     .npc("can you return it?")
                     .give(new InvItem(BOOK, 1))
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(BOOK_GIVEN);
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------ Rehnison --

    private void rehnisonDoor(GameObject door) {
        Player p = getOwner();
        if (past(INSIDE)) {
            walkThrough(door);
            return;
        }
        if (!holding(BOOK)) {
            p.getActionSender().sendMessage("Ted Rehnison: Go away we don't want any");
            return;
        }
        p.getActionSender().sendMessage("Ted Rehnison: Go away we don't want any");
        p.getActionSender().sendMessage("You say: I have come to return a book from Jethick");
        p.getActionSender().sendMessage("Ted Rehnison: Ok I guess you can come in then");
        p.getInventory().remove(BOOK, 1);
        p.getActionSender().sendInventory();
        setStage(INSIDE);
        walkThrough(door);
    }

    private void rehnison(Npc npc) {
        Player p = getOwner();
        if (past(FREED)) {
            new Conversation(p, npc)
                .npc("Any luck with finding Elena yet?")
                .player("Yes she is safe at home now")
                .npc("That's good to hear she helped us a lot")
                .start();
            return;
        }
        if (past(PARENTS)) {
            new Conversation(p, npc)
                .npc("Any luck with finding Elena yet?")
                .player("Not yet")
                .npc("I wish you luck she did a lot for us")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc)
            .player("Hi I hear a woman called Elena is staying here")
            .npc("Yes she was staying here")
            .npc("but slightly over a week ago she was getting ready to go back")
            .npc("However she never managed to leave")
            .npc("My daughter Milli was playing near the west wall")
            .npc("When she saw some shadowy figures jump out and grab her")
            .npc("Milli is upstairs if you wish to speak to her");
        if (at(INSIDE)) {
            c.then(new Effect() {
                public void run(Conversation c) {
                    setStage(PARENTS);
                }
            });
        }
        c.start();
    }

    private void milli(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Have you found Elena yet?")
                .player("Yes she's safe at home")
                .npc("I hope she comes and visits sometime")
                .player("Maybe")
                .start();
            return;
        }
        if (!past(PARENTS)) {
            return;   /* She will not speak to a stranger her parents have not vouched for. */
        }
        if (past(TOLD)) {
            new Conversation(p, npc)
                .npc("Have you found Elena yet?")
                .player("No I am still looking")
                .npc("I hope you find her")
                .npc("She was nice")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("Hello")
            .player("Your parents say you saw what happened to Elena")
            .npc("sniff")
            .npc("Yes I was near the south east corner")
            .npc("When I saw Elena walking by")
            .npc("I was about to run to greet her")
            .npc("when some men jumped out")
            .npc("Shoved a sack over her head")
            .npc("and dragged her into a building")
            .player("Which building?")
            .npc("It was the mossy windowless building")
            .npc("In that south east corner of west Ardougne")
            .then(new Effect() {
                public void run(Conversation c) {
                    setStage(TOLD);
                }
            })
            .start();
    }

    // -------------------------------------------------------- plague house --

    private void houseDoor(GameObject door) {
        Player p = getOwner();
        if (past(FREED) || (past(WARRANTED) && holding(WARRANT))) {
            if (at(WARRANTED)) {
                p.getActionSender().sendMessage("Mourner: I'd stand away from there");
                p.getActionSender().sendMessage("Mourner: That black cross means that house has been touched by the plague");
                p.getActionSender().sendMessage("You say: I have a warrant from Bravek to enter here");
                p.getActionSender().sendMessage("Mourner: this is highly irregular");
                p.getActionSender().sendMessage("Mourner: Please wait while I speak to the head mourner");
            }
            walkThrough(door);
            return;
        }
        p.getActionSender().sendMessage("Mourner: I'd stand away from there");
        p.getActionSender().sendMessage("Mourner: That black cross means that house has been touched by the plague");
        if (!past(TOLD)) {
            return;
        }
        p.getActionSender().sendMessage("You say: But I think a kidnap victim is in here");
        p.getActionSender().sendMessage("Mourner: Sounds unlikely");
        p.getActionSender().sendMessage("Mourner: Even kidnappers wouldn't go in there");
        p.getActionSender().sendMessage("Mourner: even if someone is in there");
        p.getActionSender().sendMessage("Mourner: They're probably dead by now");
        p.getActionSender().sendMessage("You say: I want to check anyway");
        p.getActionSender().sendMessage("Mourner: You don't have clearance to go in there");
        p.getActionSender().sendMessage("You say: How do I get clearance?");
        p.getActionSender().sendMessage("Mourner: Well you'd need to apply to the head mourner");
        p.getActionSender().sendMessage("Mourner: Or I suppose Bravek the city warder");
        p.getActionSender().sendMessage("Mourner: I wouldn't get your hopes up though");
        if (at(TOLD)) {
            setStage(REFUSED);
        }
    }

    private void headMourner(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("hmm how did you did get over here?")
            .npc("You're not one of this rabble")
            .npc("Ah well you'll have to stay")
            .npc("Can't risk you going back now");
        if (past(REFUSED) && !completed()) {
            c.options(new Choice("I need clearance to enter a plague house",
                                 "So what's a mourner?",
                                 "I've not got the plague though") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        whatIsAMourner(c);
                        return;
                    }
                    if (option == 2) {
                        notGotThePlague(c);
                        return;
                    }
                    c.player("It's in the southeast corner of west ardougne")
                     .npc("You must be nuts, absolutely not")
                     .options(new Choice("There's a kidnap victim inside",
                                         "I've got a gasmask though",
                                         "Yes I'm utterly crazy") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc("Well they're as good as dead already then")
                                  .npc("No point trying to save them");
                             } else if (option == 1) {
                                 c.npc("It's not regulation")
                                  .npc("Anyway you're not properly trained to deal with the plague")
                                  .player("How do I get trained")
                                  .npc("It requires a strict 18 months of training")
                                  .player("I don't have that sort of time");
                             } else {
                                 c.npc("You waste my time")
                                  .npc("I have much work to do");
                             }
                         }
                     });
                }
            });
            c.start();
            return;
        }
        /* Elena is off the list once she is home, which is the only difference
           between the head mourner before the quest and after it. */
        if (completed()) {
            c.options(new Choice("So what's a mourner?", "I've not got the plague though") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        whatIsAMourner(c);
                    } else {
                        notGotThePlague(c);
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("So what's a mourner?", "I've not got the plague though",
                             "I'm looking for a woman named Elena") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    whatIsAMourner(c);
                } else if (option == 1) {
                    notGotThePlague(c);
                } else {
                    c.npc("ah yes I've heard of her")
                     .npc("A missionary I believe")
                     .npc("She must be mad coming over here voluntarily")
                     .npc("I hear rumours she has probably caught the plague now")
                     .npc("Very tragic stupid waste of life");
                }
            }
        });
        c.start();
    }

    private void whatIsAMourner(Conversation c) {
        c.npc("We're working for King Luthas of East ardougne")
         .npc("Trying to contain the accursed plague sweeping west Ardougne")
         .npc("We also do our best to ease these peoples suffering")
         .npc("We're nicknamed mourners")
         .npc("because we spend a lot of time at plague victims funerals")
         .npc("no one else is allowed to risk the funerals")
         .npc("It's a demanding job")
         .npc("And we get little thanks from the people here");
    }

    private void notGotThePlague(Conversation c) {
        c.npc("Can't risk you being a carrier")
         .npc("that protective clothing you have")
         .npc("isn't regulation issue")
         .npc("It won't meet safety standards");
    }

    // --------------------------------------------------------- civic office --

    private void clerk(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("Hello welcome to the civic office of west Ardougne")
            .npc("How can I help you?");
        if (at(REFUSED)) {
            c.options(new Choice("I need permission to enter a plague house",
                                 "Who is through that door?",
                                 "I'm just looking thanks") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        throughThatDoor(c);
                        return;
                    }
                    if (option == 2) {
                        return;
                    }
                    c.npc("Rather you than me")
                     .npc("Well the mourners normally deal with that stuff")
                     .npc("You should speak to them")
                     .npc("Their headquarters are right near the city gate")
                     .options(new Choice("This is urgent though",
                                         "Surely you don't let them run everything for you?",
                                         "I'll try asking them then") {
                         public void picked(int option, Conversation c) {
                             if (option == 2) {
                                 return;
                             }
                             if (option == 1) {
                                 c.npc("Well they do know what they're doing there")
                                  .npc("If they did start doing something badly")
                                  .npc("Bravek the city warder")
                                  .npc("would have the power to override")
                                  .npc("I can't see that happening though");
                             }
                             c.player("Someone's been kidnapped")
                              .player("and is being held in a plague house")
                              .npc("I'll see what I can do I suppose")
                              .npc("Mr Bravek there's a man here who really needs to speak to you")
                              .message("@yel@Bravek: I suppose they can come in then")
                              .message("@yel@Bravek: If they keep it short")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      setStage(OFFICE);
                                  }
                              });
                         }
                     });
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("Who is through that door?", "I'm just looking thanks") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    throughThatDoor(c);
                }
            }
        });
        c.start();
    }

    private void throughThatDoor(Conversation c) {
        c.npc("The city warder Bravek is in there")
         .player("Can i go in?")
         .npc("He has asked not to be disturbed");
    }

    private void officeDoor(GameObject door) {
        Player p = getOwner();
        if (past(OFFICE)) {
            walkThrough(door);
            return;
        }
        p.getActionSender().sendMessage("Bravek: Go away,I'm busy");
        p.getActionSender().sendMessage("Bravek: I'm");
        p.getActionSender().sendMessage("Bravek: um");
        p.getActionSender().sendMessage("Bravek: In a meeting");
    }

    private void bravek(Npc npc) {
        Player p = getOwner();
        if (past(FREED)) {
            new Conversation(p, npc)
                .npc("thanks again for the hangover cure")
                .player("Not a problem, happy to help out")
                .npc("I'm just having a little bit of whisky")
                .npc("then I'll feel really good")
                .start();
            return;
        }
        if (past(CURED)) {
            warrantTalk(npc, false);
            return;
        }
        if (at(NOTE)) {
            if (!holding(CURE)) {
                new Conversation(p, npc)
                    .npc("uurgh")
                    .npc("My head still hurts too much to think straight")
                    .npc("Oh for one of Trudi's hangover cures")
                    .start();
                return;
            }
            warrantTalk(npc, true);
            return;
        }
        new Conversation(p, npc)
            .npc("My head hurts")
            .npc("I'll speak to you another day")
            .options(new Choice("This is really important though", "Ok goodbye") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("I can't possibly speak to you with my head spinning like this")
                     .npc("I went a bit heavy on the drink again last night")
                     .npc("curse my herbalist")
                     .npc("she made the best hang over cures")
                     .npc("Darn inconvenient of her catching the plague")
                     .options(new Choice("Do you know what is in the cure?",
                                         "You shouldn't drink so much then",
                                         "Ok goodbye") {
                         public void picked(int option, Conversation c) {
                             if (option == 2) {
                                 return;
                             }
                             if (option == 1) {
                                 c.npc("Well positions of responsibility are hard")
                                  .npc("I need something to take my mind off things")
                                  .npc("especially with the problems this place has");
                             }
                             c.npc("Hmm let me think")
                              .npc("ouch - thinking not clever")
                              .npc("Ah here, she did scribble it down for me")
                              .give(new InvItem(NOTE_ITEM, 1))
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      setStage(NOTE);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    /**
     * The warrant. Offered after the cure has gone down; the option that gets
     * it is the one that tells him the mourners have already said no.
     */
    private void warrantTalk(Npc npc, boolean drinking) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (drinking) {
            c.npc("uurgh")
             .npc("My head still hurts too much to think straight")
             .npc("Oh for one of Trudi's hangover cures")
             .player("Try this")
             .take(CURE, 1)
             .npc("grruurgh")
             .npc("Ooh that's much better")
             .npc("thanks that's the clearest my head has felt in a month")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(CURED);
                 }
             });
        } else {
            c.npc("thanks again for the hangover cure");
        }
        c.npc("Ah now what was it you wanted me to do for you?")
         .player("I need to rescue a kidnap victim called Elena")
         .player("She's being held in a plague house I need permission to enter")
         .npc("Well the mourners deal with that sort of thing")
         .options(new Choice("They won't listen to me",
                             "Is that all anyone says around here",
                             "Ok I'll go speak to them") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     return;
                 }
                 if (option == 1) {
                     c.npc("Well they know best about plague issues")
                      .npc("Nope I don't wish to take a deep interest in plagues")
                      .npc("That stuff is too scary for me")
                      .npc("But delegating is the only way to lead")
                      .npc("I delegate all plague issues to the mourners");
                     return;
                 }
                 c.player("They say I'm not properly equipped to go in the house")
                  .player("Though I do have a very effective gas mask")
                  .npc("hmm well I guess they're not taking the issue of a kidnap seriously enough")
                  .npc("They do go a bit far sometimes")
                  .npc("I've heard of Elena, she has helped us a lot")
                  .npc("Ok I'll give you this warrant to enter the house")
                  .give(new InvItem(WARRANT, 1))
                  .then(new Effect() {
                      public void run(Conversation c) {
                          if (getStage() < WARRANTED) {
                              setStage(WARRANTED);
                          }
                      }
                  });
             }
         });
        c.start();
    }

    // ------------------------------------------------------------- the cell --

    private void barrel(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        p.getActionSender().sendMessage("You search the barrel");
        if (past(FREED) || holding(KEY)) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        p.getActionSender().sendMessage("@gre@You find a little key stashed inside");
        p.getInventory().add(new InvItem(KEY, 1));
        p.getActionSender().sendInventory();
    }

    /**
     * The cell gate. Locked until the little key from the barrel opens it, and
     * an ordinary way in and out of the cell for good afterwards.
     *
     * It used to answer "The gate is already open" and do nothing else once
     * unlocked, which left Elena unreachable: she is at (637,3449), the wall
     * along y=3447/3448 is the only thing between her and the rest of the
     * dungeon, and this gate is its only gap. Unlocking a gate you can then
     * never walk through is no better than never unlocking it.
     */
    private void gate(GameObject gate) {
        Player p = getOwner();
        if (past(FREED)) {
            stepThroughGate(gate);
            return;
        }
        if (!holding(KEY)) {
            p.getActionSender().sendMessage("The gate is locked");
            p.getActionSender().sendMessage("Elena: Hey get me out of here please");
            return;
        }
        p.getInventory().remove(KEY, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You unlock the gate with the little key");
        p.getActionSender().sendMessage("@gre@Elena is free");
        setStage(FREED);
        stepThroughGate(gate);
    }

    /**
     * Step across the cell gate, whichever side of it the player is stood on.
     *
     * The gate is one tile wide at (637,3447) and the wall it sits in separates
     * y 3447 from y 3448, so the crossing is those two tiles and nothing else.
     * A teleport rather than a walk, the same as every other door in this file.
     */
    private void stepThroughGate(GameObject gate) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        p.teleport(gate.getX(), p.getY() > gate.getY() ? gate.getY() : gate.getY() + 1, false);
    }

    private void elena(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            return;
        }
        if (past(FREED)) {
            new Conversation(p, npc)
                .player("Hi, you're free to go")
                .player("Your kidnappers don't seem to be about right now")
                .npc("Thank you, Being kidnapped was so inconvenient")
                .npc("I was on my way back to East Ardougne with some samples")
                .npc("I want to see if I can diagnose a cure for this plague")
                .player("Well you can leave via the manhole cover near the gate")
                .npc("If you go and see my father")
                .npc("I'll make sure he adequately rewards you")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hey get me out of here please")
            .player("I would do but I don't have a key")
            .npc("I think there may be one around here somewhere")
            .npc("I'm sure I saw them stashing it somewhere")
            .options(new Choice("Have you caught the plague?", "Ok I will look for it") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("No, I have none of the symptoms")
                         .player("Strange I was told this house was plague infected")
                         .npc("I suppose that was a cover up by the kidnappers");
                    }
                }
            })
            .start();
    }
}
