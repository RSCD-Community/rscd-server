import org.rscdaemon.server.event.SingleEvent;
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
 * Fight Arena. Released 23 July 2002, written by Paul Gower.
 *
 * Lady Servil is sitting beside a broken cart north-east of General Khazard's
 * arena, and her husband and son are in his cells waiting to die in it. Getting
 * to them means stealing a guard's helmet and chainmail out of a cupboard,
 * walking into the jail dressed as one of them, and drinking the northern guard
 * under the table for his keys. Freeing the boy blows the disguise, and the
 * only way out is through four fights.
 *
 *     Lady servil 372  (575,679)    the broken cart, north-east
 *     Guard 373        (604,717)    south-east jail, bronze helmet
 *     Guard 374        (621,701)    northern jail, iron helmet -- the drunk
 *     Guard 376/385                 mace guards, all over the town
 *     Jeremy Servil 377 (605,716)   the south-east cell
 *     Jeremy Servil 377 (613,709)   and again, watching the arena
 *     Justin Servil 378 (613,707)   in the arena, losing
 *     fightslave joe 379   (619,701)  fightslave kelvin 380 (619,707)
 *     local 381                     six of them around the town
 *     Khazard Bartender 382 (591,718)
 *     General Khazard 383  (615,1661) his own floor, above the arena
 *     hengrad 387      (609,715)    the cell Khazard throws you into
 *
 *     guardscupboard 381/382  (607,683) and (600,1623)
 *     Door 113  (621,699) (603,717)   the two jail doors -- armour only
 *     gate 371  (621,700) (621,707)   joe's and kelvin's cells
 *     gate 371  (604,716)             Jeremy's cell
 *     Door 114  (615,715) (619,711)   the waiting area
 *     Door 115  (615,711)             the arena itself
 *
 * Deviations:
 *
 *  - Jagex's transcripts record a guard saying "damn thieves, that was good
 *    armour" to a player wearing the stolen set, without saying which guard.
 *    Both named guards stand inside a jail, and both of their inside-the-jail
 *    scripts are written out separately, so that line is given to the
 *    south-east guard before the disguise has been used on the northern one.
 *
 *  - The mace guards attack when talked to only once Bouncer is dead, which is
 *    what their transcript says. The wiki claims they always attack; the
 *    transcript is the more specific of the two and wins.
 *
 *  - Khazard is fetched in for his three speeches and sent away again, rather
 *    than walking down from his own floor. The one already standing up there is
 *    untouched, and killing him is optional either way.
 *
 *  - Door 115 faces 2, a diagonal, and the ordinary crossing arithmetic only
 *    understands 0 and 1, so the arena side and the waiting side are named
 *    outright.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class FightArena extends Quest {

    public final static int UID = Quests.FIGHT_ARENA;

    private static final int LADY = 372;
    private static final int GUARD_SE = 373, GUARD_N = 374, GUARD_SPARE = 375;
    private static final int GUARD_MACE1 = 376, GUARD_MACE2 = 385;
    private static final int JEREMY = 377, JUSTIN = 378;
    private static final int JOE = 379, KELVIN = 380;
    private static final int LOCAL = 381, BARTENDER = 382;
    private static final int KHAZARD = 383, OGRE = 384, SCORPION = 386;
    private static final int HENGRAD = 387, BOUNCER = 388;

    private static final int CUPBOARD_SHUT = 381, CUPBOARD_OPEN = 382;
    private static final int JAIL_DOOR = 113;
    private static final int WAIT_DOOR = 114, ARENA_DOOR = 115;
    private static final int CELL_GATE = 371;
    private static final int JOE_X = 621, JOE_Y = 700;
    private static final int KELVIN_X = 621, KELVIN_Y = 707;
    private static final int JEREMY_X = 604, JEREMY_Y = 716;

    private static final int COINS = 10, BEER = 193;
    private static final int HELMET = 733, CHAINMAIL = 734;
    private static final int BREW = 735, KEYS = 736;

    private static final int ATTACK = 0;
    /** Slot 17 is Thieving. Formulae called it "quest" until task 38. */
    private static final int THIEVING = 17;

    private static final int ARENA_X = 615, ARENA_Y = 709;
    private static final int WAIT_X = 615, WAIT_Y = 712;
    private static final int CELL_X = 609, CELL_Y = 715;

    private static final int STARTED = 1;
    private static final int MET_GUARD = 2;
    private static final int LECTURED = 4;
    private static final int GOT_KEYS = 8;
    private static final int FREED = 16;
    private static final int OGRE_DEAD = 32;
    private static final int MET_HENGRAD = 64;
    private static final int SCORPION_DEAD = 128;
    private static final int BOUNCER_DEAD = 256;
    private static final int FINISHED = 512;

    public FightArena(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Fight arena");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The prosperous Servil family have been abducted by the infamous General Khazard. He plans to have the family battle for his entertainment in the fight arena. Can you rescue the Servil's before the tyrant has these innocent (not to mention wealthy) civillians slain.");
        setStartPoint("North of Fight Arena");
        setSpeakTo("Lady Servil");
        setMissionLength("Medium");
        rewardItem(COINS, 1000);
        rewardExp(ATTACK, 175, 200);
        rewardExp(THIEVING, 175, 200);

        associateNpc(LADY);
        associateNpc(GUARD_SE);
        associateNpc(GUARD_N);
        associateNpc(GUARD_SPARE);
        associateNpc(GUARD_MACE1);
        associateNpc(GUARD_MACE2);
        associateNpc(JEREMY);
        associateNpc(JUSTIN);
        associateNpc(JOE);
        associateNpc(KELVIN);
        associateNpc(LOCAL);
        associateNpc(BARTENDER);
        associateNpc(KHAZARD);
        associateNpc(OGRE);
        associateNpc(SCORPION);
        associateNpc(HENGRAD);
        associateNpc(BOUNCER);
        associateObject(CUPBOARD_SHUT);
        associateObject(CUPBOARD_OPEN);
        associateObject(CELL_GATE, JOE_X, JOE_Y);
        associateObject(CELL_GATE, KELVIN_X, KELVIN_Y);
        associateObject(CELL_GATE, JEREMY_X, JEREMY_Y);
        associateDoor(JAIL_DOOR);
        associateDoor(WAIT_DOOR);
        associateDoor(ARENA_DOOR);
        /* Only so that drinking it can be refused, as it was in 2002. */
        associateItem(BREW);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Fight Arena quest");
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

    /** The disguise only works while it is actually on. */
    private boolean disguised() {
        return getOwner().getInventory().wielding(HELMET)
            && getOwner().getInventory().wielding(CHAINMAIL);
    }

    private Npc nearby(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id) {
                return n;
            }
        }
        return null;
    }

    /**
     * Put a monster next to the player and set it on them. Everything that
     * fights in the arena is spawned for one bout and cleared away after ten
     * minutes in case the player walked out mid-fight.
     */
    private Npc unleash(int id) {
        final Player p = getOwner();
        int x = p.getX(), y = p.getY();
        final Npc beast = new Npc(id, x, y + 1, x - 5, x + 5, y - 5, y + 5);
        beast.setRespawn(false);
        world.registerNpc(beast);
        beast.attackPlayer(p);
        clearLater(beast, 600000);
        return beast;
    }

    private void clearLater(final Npc npc, int ms) {
        final int id = npc.getID();
        world.getDelayedEventHandler().add(new SingleEvent(null, ms){
            public void action() {
                if (npc.getID() == id) {
                    world.unregisterNpc(npc);
                }
            }
        });
    }

    /** Khazard walks on for a speech and off again. */
    private Npc summonKhazard() {
        Player p = getOwner();
        int x = p.getX(), y = p.getY();
        Npc him = new Npc(KHAZARD, x, y + 1, x - 6, x + 6, y - 6, y + 6);
        him.setRespawn(false);
        world.registerNpc(him);
        // Backstop for every walk-on: the scene's own unregister only runs
        // if its conversation reaches the end, and an interrupted scene was
        // leaving spare generals standing around the arena for good.
        clearLater(him, 600000);
        return him;
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

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
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
                case LADY:       lady(npc); return;
                case GUARD_SE:
                case GUARD_N:
                case GUARD_SPARE:  guard(npc); return;
                case GUARD_MACE1:
                case GUARD_MACE2:  maceGuard(npc); return;
                case JEREMY:     jeremy(npc); return;
                case JUSTIN:     justin(npc); return;
                case JOE:
                case KELVIN:     fightslave(npc); return;
                case LOCAL:      local(npc); return;
                case BARTENDER:  bartender(npc); return;
                case HENGRAD:    hengrad(npc); return;
                case KHAZARD:    khazard(npc); return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                /* The brew has a drink option that never did anything. */
                getOwner().getActionSender().sendMessage("Nothing interesting happens");
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1) {
            switch (object.getID()) {
                case JAIL_DOOR:  jailDoor(object); return;
                case WAIT_DOOR:  waitDoor(object); return;
                case ARENA_DOOR: arenaDoor(); return;
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            switch (object.getID()) {
                case CUPBOARD_SHUT: openCupboard(object); return;
                case CUPBOARD_OPEN: searchCupboard(); return;
                case CELL_GATE:     cellGate(object); return;
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2 && object.getID() == CUPBOARD_OPEN) {
            shutCupboard(object);
        }
    }

    private void killed(Npc npc) {
        switch (npc.getID()) {
            case OGRE:     ogreDown(); return;
            case SCORPION: scorpionDown(); return;
            case BOUNCER:  bouncerDown(); return;
        }
    }

    // ----------------------------------------------------------- Lady Servil --

    private void lady(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("Hello lady Servil")
                .npc("oh hello my dear")
                .npc("my husband and son are resting")
                .npc("while i wait for the cart fixer")
                .player("hope he's not too long")
                .npc("thanks again for everything")
                .start();
            return;
        }
        if (has(BOUNCER_DEAD)) {
            new Conversation(p, npc)
                .player("Lady Servil")
                .npc("you're alive, i thought Khazard's men took you")
                .npc("My son and husband are safe and recovering at home")
                .npc("without you they would certainly be dead")
                .npc("I am truly grateful for your service")
                .npc("all i can offer in return is material wealth")
                .npc("please take these coins and enjoy")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(FINISHED);
                    }
                })
                .start();
            return;
        }
        if (has(FREED)) {
            new Conversation(p, npc)
                .player("Lady Servil, i've freed your son")
                .player("but he has returned to the arena to try and help your husband")
                .npc("oh no, they won't stand a chance")
                .npc("please go back and help")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello Lady Servil")
                .npc("Brave traveller, please..bring back my family")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hi there, looks like you're in some trouble")
            .npc("oh, i wish this broken cart was my only problem")
            .npc("sob.. i've got to find my family.. sob")
            .options(new Choice("can i help you?", "I hope you can, good luck") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("sob..sob");
                        return;
                    }
                    c.npc("sob.. would you? please?")
                     .npc("i'm Lady Servil, my husband's Sir Servil")
                     .npc("we were travelling north with my son")
                     .npc("when we were ambushed by general Khazard's men")
                     .player("general Khazard? i haven't heard of him")
                     .npc("he's been after me ever since i")
                     .npc("declined his hand in marriage")
                     .npc("now he's kidnapped my husband and son")
                     .npc("to fight slaves in his")
                     .npc("battle arena, to the south of here")
                     .npc("i hate to think what he'll do to them")
                     .npc("he's a sick, twisted man")
                     .player("I'll try my best to return your family")
                     .npc("please do, i'm a wealthy woman")
                     .npc("and can reward you handsomely")
                     .npc("i'll be waiting for you here")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(STARTED);
                         }
                     });
                }
            })
            .start();
    }

    // ---------------------------------------------------------- the cupboard --

    private void openCupboard(GameObject cupboard) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(cupboard.getLocation(), CUPBOARD_OPEN,
            cupboard.getDirection(), cupboard.getType()));
    }

    private void shutCupboard(GameObject cupboard) {
        Player p = getOwner();
        p.getActionSender().sendSound("closedoor");
        world.registerGameObject(new GameObject(cupboard.getLocation(), CUPBOARD_SHUT,
            cupboard.getDirection(), cupboard.getType()));
    }

    private void searchCupboard() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the cupboard");
        boolean took = false;
        if (!holding(HELMET)) {
            p.getActionSender().sendMessage("you find a khazard helmet");
            give(HELMET, 1);
            took = true;
        }
        if (!holding(CHAINMAIL)) {
            p.getActionSender().sendMessage("you find a khazard chainmail");
            give(CHAINMAIL, 1);
            took = true;
        }
        if (!took) {
            p.getActionSender().sendMessage("you find nothing else of interest");
        }
    }

    // --------------------------------------------------------------- doors --

    private void jailDoor(GameObject door) {
        Player p = getOwner();
        if (disguised()) {
            walkThrough(door);
            return;
        }
        Npc guard = nearby(GUARD_SE);
        if (guard == null) {
            guard = nearby(GUARD_N);
        }
        if (guard == null) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        new Conversation(p, guard)
            .npc("you there! halt!")
            .npc("this is General Khazard's private lodgings")
            .npc("what's your business here?")
            .player("I'm looking for the 'Servil' prisoners")
            .npc("wait until tomorrow, then you can")
            .npc("see them butchered in the arena haha")
            .npc("Now OUT and don't come back!")
            .start();
    }

    private void waitDoor(GameObject door) {
        Player p = getOwner();
        if (has(FREED)) {
            walkThrough(door);
            return;
        }
        Npc guard = nearby(GUARD_MACE2);
        if (guard == null) {
            guard = nearby(GUARD_MACE1);
        }
        if (guard == null) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        new Conversation(p, guard)
            .npc("and where do you think you're going?")
            .npc("only General Khazard decides who fights in the arena")
            .npc("so get out of here")
            .start();
    }

    /**
     * The arena door. Going in for the first time is the start of the first
     * fight; after that it is simply a door, which is how a player who has had
     * enough of Khazard gets out.
     */
    private void arenaDoor() {
        final Player p = getOwner();
        if (!has(FREED)) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        if (p.getY() <= ARENA_Y + 1) {
            p.teleport(WAIT_X, WAIT_Y, false);
            return;
        }
        p.teleport(ARENA_X, ARENA_Y, false);
        if (has(OGRE_DEAD)) {
            return;
        }
        Npc boy = nearby(JEREMY);
        Conversation c = boy == null ? new Conversation(p, null) : new Conversation(p, boy);
        if (boy != null) {
            c.player("Jeremy where's your father?")
             .npc("Quick, help him! that beast will kill him")
             .npc("He can't fight! he's too old!")
             .npc("Please help him!");
        } else {
            c.message("an ogre lumbers into the arena");
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                c.stop();
                unleash(OGRE);
            }
        }).start();
    }

    // ---------------------------------------------------------- the cells --

    private void cellGate(GameObject gate) {
        Player p = getOwner();
        if (gate.getX() != JEREMY_X || gate.getY() != JEREMY_Y) {
            p.getActionSender().sendMessage("The gate is locked");
            return;
        }
        Npc boy = nearby(JEREMY);
        if (boy == null || has(FREED) || completed()) {
            p.getActionSender().sendMessage("The gate is locked");
            return;
        }
        if (!holding(KEYS)) {
            new Conversation(p, boy)
                .npc("I'm Jeremy Servil")
                .npc("Please sir, don't hurt me")
                .player("I'm here to help")
                .player("Where do they keep the keys?")
                .npc("The guard keeps them.. always")
                .start();
            return;
        }
        new Conversation(p, boy)
            .player("Jeremy, look, I have the cell keys")
            .npc("Wow! Please help me")
            .player("ok, keep quiet")
            .npc("Set me free then we can find dad")
            .player("There you go, now we need to find your father")
            .npc("I overheard a guard talking")
            .npc("I think they've taken him to the arena")
            .player("OK we'd better hurry")
            .npc("I'll run ahead")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(FREED);
                    Npc guard = nearby(GUARD_SE);
                    if (guard != null) {
                        c.getPlayer().informOfNpcMessage(
                            new org.rscdaemon.server.model.ChatMessage(
                                guard, "What are you doing?", c.getPlayer()));
                    }
                    c.getPlayer().getActionSender().sendMessage("Guard: It's an imposter!");
                }
            })
            .start();
    }

    // --------------------------------------------------------------- talk --

    private void jeremy(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            return;
        }
        if (has(FREED)) {
            new Conversation(p, npc)
                .player("Jeremy where's your father?")
                .npc("Quick, help him! that beast will kill him")
                .npc("He can't fight! he's too old!")
                .npc("Please help him!")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("I'm Jeremy Servil")
            .npc("Please sir, don't hurt me")
            .player("I'm here to help")
            .player("Where do they keep the keys?")
            .npc("The guard keeps them.. always")
            .start();
    }

    private void justin(Npc npc) {
        Player p = getOwner();
        if (completed() || !has(OGRE_DEAD)) {
            return;
        }
        new Conversation(p, npc)
            .npc("You saved my life and my son's")
            .npc("I am eternally in your debt brave traveller")
            .start();
    }

    private void fightslave(Npc npc) {
        Player p = getOwner();
        if (disguised()) {
            new Conversation(p, npc)
                .player("are you ok?")
                .npc("spare me your fake pity")
                .npc("I spit on Khazard's grave and all who do his bidding")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("are you ok?")
            .npc("you're not safe here traveller")
            .npc("leave while you still can")
            .start();
    }

    private void local(Npc npc) {
        Player p = getOwner();
        if (disguised()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("please, i haven't done anything")
                .player("what?")
                .npc("i love General Khazard, please believe me")
                .start();
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello stranger are you new to these parts?")
                .npc("you look lost")
                .npc("i suppose you're here for the fight arena?")
                .npc("there are some rich folk fighting tomorrow")
                .npc("should be entertaining")
                .start();
            return;
        }
        if (completed()) {
            new Conversation(p, npc)
                .npc("hello stranger")
                .npc("Khazard's got some great fights lined up this week")
                .npc("i can't wait")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("hello stranger are you new to these parts?")
            .player("i suppose i am")
            .npc("what's your business?")
            .player("just visiting friends in the cells")
            .npc("visiting, that's funny")
            .npc("only khazard guards are allowed to see prisoners")
            .npc("so unless you know where to get some khazard armour")
            .npc("you won't be visiting anyone")
            .start();
    }

    private void hengrad(Npc npc) {
        final Player p = getOwner();
        if (!has(OGRE_DEAD) || has(MET_HENGRAD) || completed()) {
            return;
        }
        new Conversation(p, npc)
            .player("Are you ok stranger?")
            .npc("I'm fine thanks, my name's Hengrad")
            .npc("So khazard got his hands on you too?")
            .player("I'm afraid so")
            .npc("If you're lucky you may last as long as me")
            .player("How long have you been here?")
            .npc("I've been in khazard's prisons ever since i can remember")
            .npc("I was a child when his men kidnapped me")
            .npc("My whole life has been spent killing and fighting")
            .npc("All in the hope that one day I'll escape")
            .player("Don't give up")
            .npc("Thanks friend..wait..sshh,the guard is coming")
            .npc("He'll be taking one of us to the arena")
            .npc("Looks like it's you,good luck friend")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(MET_HENGRAD);
                    c.stop();
                    p.teleport(ARENA_X, ARENA_Y, false);
                    unleash(SCORPION);
                }
            })
            .start();
    }

    private void khazard(Npc npc) {
        Player p = getOwner();
        if (!has(BOUNCER_DEAD)) {
            return;
        }
        final Npc him = npc;
        new Conversation(p, npc)
            .player("i thought i was rid of you")
            .npc("you might not believe it young one")
            .npc("but you can't kill what's already dead")
            .npc("die, foul smelling creature")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    him.attackPlayer(c.getPlayer());
                }
            })
            .start();
    }

    // -------------------------------------------------------------- guards --

    private void guard(Npc npc) {
        Player p = getOwner();
        final Npc him = npc;
        if (has(BOUNCER_DEAD) || completed()) {
            if (npc.getID() == GUARD_SE) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("hello, hope you're keeping busy?")
                    .player("of course")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("less chat and more work")
                    .npc("i can't stand lazy guards")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("Hello")
                .npc("Who goes there?")
                .player("..er .. i'm..")
                .npc("I don't know you")
                .npc("Get out of my house strange")
                .start();
            return;
        }
        if (!disguised()) {
            new Conversation(p, npc)
                .npc("this area is restricted, leave now")
                .npc("OUT and don't come back!")
                .start();
            return;
        }
        if (has(FREED)) {
            new Conversation(p, npc)
                .npc("What are you doing?")
                .npc("It's an imposter!")
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.stop();
                        him.attackPlayer(c.getPlayer());
                    }
                })
                .start();
            return;
        }
        if (npc.getID() != GUARD_N) {
            southEastGuard(npc);
            return;
        }
        northGuard(npc);
    }

    private void southEastGuard(Npc npc) {
        Player p = getOwner();
        if (has(MET_GUARD)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("i hope you're keeping busy?")
                .player("of course")
                .npc("General Khazard doesn't tolerate the lazy")
                .npc("if you're not keeping busy")
                .npc("i'll practice my combat skills on your hide")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("damn thieves, that was good armour")
            .npc("did you see anyone around here?")
            .player("me? no, no one")
            .npc("hmmmmm")
            .start();
    }

    private void northGuard(Npc npc) {
        Player p = getOwner();
        if (has(GOT_KEYS)) {
            if (holding(KEYS)) {
                new Conversation(p, npc)
                    .npc("please, let me rest")
                    .start();
                return;
            }
            new Conversation(p, npc)
                .player("i've lost the keys")
                .npc("what?! you're foolish..")
                .npc("hiccup.. and i'm drunk")
                .npc("here, i've got another set")
                .give(new InvItem(KEYS, 1))
                .start();
            return;
        }
        if (!has(MET_GUARD)) {
            new Conversation(p, npc)
                .player("long live General Khazard")
                .npc("erm.. yes.. quite right")
                .npc("have you come to laugh at the fight slaves?")
                .npc("i used to really enjoy it")
                .npc("but after a while they become quite boring")
                .npc("now i just want a decent drink")
                .npc("mind you, too much khali brew and i'll fall asleep")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(MET_GUARD);
                    }
                })
                .start();
            return;
        }
        if (!has(LECTURED)) {
            new Conversation(p, npc)
                .player("long live General Khazard")
                .npc("erm.. yes.. soldier")
                .npc("i take it you're new")
                .player("you could say that")
                .npc("Khazard died two hundred years ago")
                .npc("however his dark spirit remains")
                .npc("in the form of the undead maniac...General Khazard")
                .npc("remember he is your master, always watching")
                .npc("you got that, newbie?")
                .player("undead, maniac, master, got it - loud and clear")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(LECTURED);
                    }
                })
                .start();
            return;
        }
        if (!holding(BREW)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("bored, bored, bored")
                .npc("you would think the slaves would be more entertaining")
                .npc("selfish.. the lot of 'em")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello again")
            .npc("bored, bored, bored")
            .npc("you would think the slaves would be more entertaining")
            .npc("selfish.. the lot of 'em")
            .player("do you still fancy a drink?")
            .npc("I really shouldn't... ok then, just the one")
            .take(BREW, 1)
            .npc("this stuff looks good")
            .npc("blimey this stuff is pretty good")
            .npc("it's not too strong is it?")
            .player("no, not at all, you'll be fine")
            .npc("that is some gooood stuff")
            .npc("yeah... woooh... yeah")
            .player("are you alright?")
            .npc("yeesshh, ooohh, 'hiccup'")
            .npc("maybe i should relax for a while....")
            .player("good idea, i'll look after the prisoners")
            .npc("ok then, here, 'hiccup',")
            .npc("take these keys")
            .give(new InvItem(KEYS, 1))
            .npc("any trouble you give 'em a good beating")
            .player("no problem, i'll keep them in line")
            .npc("zzzzz zzzzz zzzzz")
            .message("To unlock the gate, left click on it")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(GOT_KEYS);
                }
            })
            .start();
    }

    private void maceGuard(Npc npc) {
        Player p = getOwner();
        final Npc him = npc;
        if (has(BOUNCER_DEAD) || completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("you're the outsider who killed bouncer")
                .npc("die traitor!")
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.stop();
                        him.attackPlayer(c.getPlayer());
                    }
                })
                .start();
            return;
        }
        if (disguised()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("can i help you stranger?")
                .npc("oh.. you're a guard as well")
                .npc("that's ok then")
                .npc("we don't like outsiders around here")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("i don't know you stranger")
            .npc("get of our land")
            .start();
    }

    // ------------------------------------------------------------ the bar --

    private void bartender(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .player("Hello")
            .npc("Hello, what can i get you? we have all sorts of brew");
        if (has(MET_GUARD) && !completed()) {
            c.options(new Choice("I'll have a beer please",
                                 "I'd like a khali brew please",
                                 "Got any news?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        pour(c);
                    } else if (option == 1) {
                        c.npc("There you go")
                         .npc("No charge")
                         .give(new InvItem(BREW, 1));
                    } else {
                        news(c);
                    }
                }
            });
        } else {
            c.options(new Choice("I'll have a beer please", "Got any news?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        pour(c);
                    } else {
                        news(c);
                    }
                }
            });
        }
        c.start();
    }

    private void pour(Conversation c) {
        if (!holding(COINS)) {
            c.npc("You don't have enough coins for that");
            return;
        }
        c.npc("There you go, that's one gold coin")
         .take(COINS, 1)
         .give(new InvItem(BEER, 1));
    }

    private void news(Conversation c) {
        c.npc("Well have you seen the famous khazard fight arena?")
         .npc("I've seen some grand battles in my time..")
         .npc("Ogres, goblins, even dragons, they all come to fight")
         .npc("The poor slaves of general khazard");
    }

    // ------------------------------------------------------------- the bouts --

    private void ogreDown() {
        final Player p = getOwner();
        if (has(OGRE_DEAD) || !has(FREED)) {
            return;
        }
        set(OGRE_DEAD);
        final Npc him = summonKhazard();
        new Conversation(p, him)
            .npc("Haha, well done, well done that was rather entertaining")
            .npc("I'm the great General Khazard")
            .npc("And the two men you just saved are my property")
            .player("They belong to no one")
            .npc("I suppose we could find some arrangement")
            .npc("for their freedom... hmmmm")
            .player("What do you mean?")
            .npc("I'll let them go but you must stay and fight for me")
            .npc("You'll make me double the gold if you manage to last a few fights")
            .npc("Guards! take him away!")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    p.teleport(CELL_X, CELL_Y, false);
                    world.unregisterNpc(him);
                }
            })
            .start();
    }

    private void scorpionDown() {
        final Player p = getOwner();
        if (has(SCORPION_DEAD) || !has(MET_HENGRAD)) {
            return;
        }
        set(SCORPION_DEAD);
        final Npc him = summonKhazard();
        new Conversation(p, him)
            .npc("Not bad, not bad at all")
            .npc("I think you need a tougher challenge")
            .npc("Time for my puppy")
            .npc("Guards, guards bring on bouncer")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    world.unregisterNpc(him);
                    unleash(BOUNCER);
                }
            })
            .start();
    }

    private void bouncerDown() {
        Player p = getOwner();
        if (has(BOUNCER_DEAD) || !has(SCORPION_DEAD)) {
            return;
        }
        set(BOUNCER_DEAD);
        final Npc him = summonKhazard();
        clearLater(him, 600000);
        new Conversation(p, him)
            .npc("nooooo! bouncer, how dare you?")
            .npc("you've taken the life of my only friend!")
            .npc("now you'll suffer traveller, prepare to meet your maker")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    him.attackPlayer(c.getPlayer());
                }
            })
            .start();
    }
}
