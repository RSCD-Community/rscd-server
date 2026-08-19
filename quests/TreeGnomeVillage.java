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
 * Tree Gnome Village. Released 23 July 2002, written by Thomas Woode.
 *
 * General Khazard's men have taken one of the three orbs that keep the tree
 * gnomes' spirit tree, and them, safe. The quest is a small war: carry logs to
 * the gnome line, find three scouts scattered across the battlefield, put their
 * three numbers into a siege engine, walk in through the hole it makes, and
 * then come back and do it all again for a warlord who has taken the other two.
 *
 *     bolren            npc 400, (656,695), the village, beside the spirit tree
 *     elkoy             npc 396, (625,675), the mouth of the hedge maze
 *     commander montai  npc 408, (656,662), the gnome side of the river
 *     tracker 1         npc 404, (653,627)
 *     tracker 2         npc 405, (649,629)
 *     tracker 3         npc 406, (668,638)
 *     Khazard commander npc 428, (659,630), inside the stronghold
 *     khazard warlord   npc 410, (673,595), north of the battlefield
 *     local gnome       npc 399, six spawns, village and treetop
 *     kalron            npc 402, six spawns, all inside the maze
 *
 * The third bystander, gnome troop 409 with its 31 spawns on the battlefield,
 * is NOT here -- its three lines are a random table rather than a staged one,
 * so it belongs to RandomChat. That reading is argued out in full under
 * RandomChat.GNOME_TROOP, because it goes against the transcript's own layout
 * and against OpenRSC.
 *
 *     Logs               item 14, six of them
 *     orb of protection  item 740, from the chest upstairs
 *     orbs of protection item 741, from the warlord
 *     Gnome Emerald Amulet of protection  item 744, the reward
 *
 *     Ballista           object 388, (657,663)
 *     wall               object 393, (658,632), three tiles wide
 *     khazard Chest      objects 410 shut / 409 open, (662,634) upstairs
 *     spirit tree        object 390, (660,695)
 *     young spirit Tree  object 391, (628,629) and (160,453)
 *
 * The ballista wants three numbers, and the three trackers hold one each:
 * tracker 1 gives the height outright, tracker 2 the y, and tracker 3 -- who
 * has lost his mind and answers in riddles -- the x. They are 4, 5 and 3, and
 * they are fixed, the same for every player, which is why the numbers can be
 * constants here rather than rolled.
 *
 * Deviations:
 *
 *  - The spirit tree's lines are sent as messages rather than as speech. Speech
 *    in this server belongs to an npc and the tree is scenery, so there is no
 *    mouth to hang a chat bubble on; vanilla had the same problem and almost
 *    certainly solved it the same way.
 *
 *  - Choosing a ballista coordinate echoes "coord 4" as the player saying it,
 *    because that is what Conversation.options() does with every menu. Harmless
 *    and consistent with the rest of the dialogue, but it is not what a machine
 *    would do.
 *
 *  - The largespear does not vanish for twenty seconds after a shot. It is
 *    cosmetic, it needs the object taken out of the world and put back rather
 *    than swapped, and the wiki records that vanilla did not check for it
 *    anyway -- the ballista fires whether the spear is there or not.
 *
 *  - The stronghold spirit tree, object 661 at (703,486), is not claimed here.
 *    Vanilla gates that one on Grand tree rather than on this quest, so it
 *    belongs to quest 39 when that is written; it should check this quest too,
 *    since the old tree offers the stronghold as a destination and a player who
 *    takes it needs a way home.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TreeGnomeVillage extends Quest {

    public final static int UID = Quests.TREE_GNOME_VILLAGE;

    // ----------------------------------------------------------------- ids --

    private static final int BOLREN = 400;
    private static final int ELKOY = 396;
    private static final int MONTAI = 408;
    private static final int TRACKER_1 = 404, TRACKER_2 = 405, TRACKER_3 = 406;
    private static final int COMMANDER = 428;
    private static final int WARLORD = 410;
    private static final int LOCAL_GNOME = 399;
    private static final int KALRON = 402;

    private static final int LOGS = 14, LOGS_NEEDED = 6;
    private static final int ORB = 740;
    private static final int ORBS = 741;
    private static final int AMULET = 744;

    private static final int ATTACK = 0;

    // -------------------------------------------------------------- scenery --

    private static final int BALLISTA = 388, BALLISTA_X = 657, BALLISTA_Y = 663;
    private static final int WALL = 393, WALL_X = 658, WALL_Y = 632;

    /**
     * "Fence with loose pannels" (Jagex's spelling), door 101. Two panels on
     * the village's own fence at y 705, and a third on the battlefield fence
     * at (540,445). Pushing through is open to everyone at any stage -- the
     * fence is the maze-skip into the village, not a quest gate.
     */
    private static final int FENCE = 101;
    private static final int FENCE_Y = 705;
    private static final int FENCE_FIELD_X = 540, FENCE_FIELD_Y = 445;
    private static final int CHEST_SHUT = 410, CHEST_OPEN = 409;
    private static final int CHEST_X = 662, CHEST_Y = 1578;
    private static final int SPIRIT_TREE = 390, SPIRIT_TREE_X = 660, SPIRIT_TREE_Y = 695;
    private static final int YOUNG_TREE = 391;
    private static final int YOUNG_BATTLEFIELD_X = 628, YOUNG_BATTLEFIELD_Y = 629;
    private static final int YOUNG_VARROCK_X = 160, YOUNG_VARROCK_Y = 453;

    // ------------------------------------------------------------ landings --

    /** Where elkoy leaves you: a few paces from bolren, inside the hedges. */
    private static final int VILLAGE_X = 654, VILLAGE_Y = 695;

    /** Where bolren's assistant leaves you: beside elkoy, at the maze mouth. */
    private static final int MAZE_MOUTH_X = 625, MAZE_MOUTH_Y = 676;

    private static final int TREE_VILLAGE_X = 659, TREE_VILLAGE_Y = 696;
    private static final int TREE_BATTLEFIELD_X = 628, TREE_BATTLEFIELD_Y = 630;
    private static final int TREE_VARROCK_X = 160, TREE_VARROCK_Y = 454;
    private static final int TREE_STRONGHOLD_X = 703, TREE_STRONGHOLD_Y = 488;

    // -------------------------------------------------------- the ballista --

    private static final String[] COORDS = { "coord 1", "coord 2", "coord 3", "coord 4", "coord 5" };

    private static final int TRUE_HEIGHT = 4, TRUE_X = 3, TRUE_Y = 5;

    // -------------------------------------------------------------- stages --

    private static final int STARTED = 1;        /* bolren asked for the orb */
    private static final int WOOD_ASKED = 2;     /* montai asked for six logs */
    private static final int WOOD_GIVEN = 4;     /* the ballista is repaired */
    private static final int TRACKING = 8;       /* agreed to find the trackers */
    private static final int WALL_DOWN = 16;     /* a direct hit on the stronghold */
    private static final int GOT_ORB = 32;       /* the chest has been searched */
    private static final int ORB_DELIVERED = 64; /* bolren told you the rest were taken */
    private static final int WARLORD_DEAD = 128;
    private static final int DONE = 256;

    private static final int FINISHED = STARTED | WOOD_ASKED | WOOD_GIVEN | TRACKING
        | WALL_DOWN | GOT_ORB | ORB_DELIVERED | WARLORD_DEAD | DONE;

    public TreeGnomeVillage(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Tree Gnome Village");
        setFinalStage(FINISHED);

        associateNpc(BOLREN);
        associateNpc(ELKOY);
        associateNpc(MONTAI);
        associateNpc(TRACKER_1);
        associateNpc(TRACKER_2);
        associateNpc(TRACKER_3);
        associateNpc(COMMANDER);
        associateNpc(WARLORD);
        associateNpc(LOCAL_GNOME);
        associateNpc(KALRON);

        associateDoor(FENCE, 633, FENCE_Y);
        associateDoor(FENCE, 634, FENCE_Y);
        associateDoor(FENCE, FENCE_FIELD_X, FENCE_FIELD_Y);

        associateObject(BALLISTA, BALLISTA_X, BALLISTA_Y);
        associateObject(WALL, WALL_X, WALL_Y);
        // Both halves of the chest at the one tile. The world ships it open,
        // which is a fault of its own -- see the report -- but claiming the shut
        // id as well means the quest works either way and "close" has somewhere
        // to go.
        associateObject(CHEST_SHUT, CHEST_X, CHEST_Y);
        associateObject(CHEST_OPEN, CHEST_X, CHEST_Y);
        associateObject(SPIRIT_TREE, SPIRIT_TREE_X, SPIRIT_TREE_Y);
        associateObject(YOUNG_TREE, YOUNG_BATTLEFIELD_X, YOUNG_BATTLEFIELD_Y);
        associateObject(YOUNG_TREE, YOUNG_VARROCK_X, YOUNG_VARROCK_Y);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The tree gnomes are in trouble. General Khazard's forces are hunting them to extinction. Find you way through the hedge maze to the gnomes secret treetop village. Then help the gnomes fight Khazard and retrieve the orbs of protection.");
        setStartPoint("Centre of maze");
        setSpeakTo("Bolren");
        setMissionLength("Long");
        require("Must defeat a Level 100 warlord");
        rewardExp(ATTACK, 200, 225);
        // Bolren hands the amulet over himself in the closing ceremony, and
        // replaces it whenever it is lost, so it stays in his dialogue.
        rewardOther("A Gnome Emerald Amulet of protection");
        rewardOther("Use of the spirit trees");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("@gre@Well done you have completed the treequest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        if (!questStarted() && bit != STARTED) {
            return;
        }
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private void say(String line) {
        getOwner().getActionSender().sendMessage(line);
    }

    private boolean holds(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    /**
     * Take several of something that does not stack.
     *
     * Inventory.remove(id, amount) treats the amount as a stack size, so on six
     * separate logs it removes one and reports success. Six calls is what is
     * actually meant.
     */
    private void takeAll(int id, int amount) {
        Player p = getOwner();
        for (int i = 0; i < amount; i++) {
            p.getInventory().remove(id, 1);
        }
        p.getActionSender().sendInventory();
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    /** The nearest live npc of this id, or null. Used for the scenes scenery starts. */
    private Npc nearby(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id && n.getHits() > 0) {
                return n;
            }
        }
        return null;
    }

    private void swap(GameObject object, int newId) {
        world.registerGameObject(new GameObject(object.getLocation(), newId,
            object.getDirection(), object.getType()));
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            scenery(trigger, (GameObject) entity);
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == WARLORD) {
                warlordFell(npc);
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case BOLREN: bolren(npc); break;
            case ELKOY: elkoy(npc); break;
            case MONTAI: montai(npc); break;
            case TRACKER_1: tracker1(npc); break;
            case TRACKER_2: tracker2(npc); break;
            case TRACKER_3: tracker3(npc); break;
            case WARLORD: warlord(npc); break;
            case COMMANDER: say("The Khazard commander does not appear interested in talking"); break;
            case LOCAL_GNOME: localGnome(npc); break;
            case KALRON: kalron(npc); break;
            default: break;
        }
    }

    // -------------------------------------------------------------- bolren --

    private void bolren(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            bolrenAfterwards(npc);
            return;
        }
        if (!questStarted()) {
            bolrenOpening(npc);
            return;
        }
        if (has(WARLORD_DEAD)) {
            bolrenEnding(npc);
            return;
        }
        if (has(ORB_DELIVERED)) {
            new Conversation(p, npc)
                .player("hello bolren")
                .npc("the orbs are gone")
                .npc("taken north of the battlefield by a khazard warlord")
                .npc("we're all doomed")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            bolrenCheckpoint(npc);
            return;
        }
        if (has(WOOD_ASKED)) {
            new Conversation(p, npc)
                .player("hello bolren")
                .npc("the orb is being held at the battlefield")
                .npc("to the north of the maze")
                .npc("above the khazard fight arena")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello bolren")
            .npc("hello traveller, we must retrieve the orb")
            .npc("it's being held by khazard troops")
            .npc("to the west of the maze")
            .npc("above the khazard fight arena")
            .player("ok i'll try my best")
            .start();
    }

    private void bolrenOpening(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello")
            .npc("well hello stranger")
            .npc("my name's bolren, i'm the king of the tree gnomes")
            .npc("i'm surprised you made it in")
            .npc("maybe i made the maze too easy")
            .player("maybe")
            .npc("i'm afraid i have more serious concerns at the moment")
            .npc("very serious")
            .options(new Choice("I'll leave you to it then", "Can i help at all?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("ok take care");
                        return;
                    }
                    c.npc("i'm glad you asked")
                     .npc("the truth is my people are in grave danger")
                     .npc("we have always been protected by the spirit tree")
                     .npc("no creature dark of heart can harm us")
                     .npc("while its three orbs are in place.")
                     .npc("We are not a violent race")
                     .npc("but we fight when we must")
                     .npc("many gnomes have fallen")
                     .npc("battling the dark forces of khazard to the north")
                     .npc("we became desperate")
                     .npc("so we took one orb of protection to the battlefield")
                     .npc("it was a foolish move")
                     .npc("khazard troops siezed the orb")
                     .npc("and now we are completely defenseless")
                     .player("how can i help?")
                     .npc("you would be a huge benefit on the battlefield")
                     .npc("if you would go there and try and retrieve the orb")
                     .npc("my people and i will be forever grateful")
                     .options(new Choice("I would be glad to help",
                                         "I'm sorry but i won't be involved") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                c.npc("ok then, travel safe");
                                return;
                            }
                            c.npc("thank you")
                             .npc("the battlefield is to the north of the maze")
                             .npc("commander montai will inform you of their current situation")
                             .npc("that's if he's still alive")
                             .npc("my assistant shall guide you out")
                             .npc("try your best to return the orb")
                             .npc("good luck friend")
                             .then(new Effect() {
                                 public void run(Conversation c) {
                                     set(STARTED);
                                 }
                             })
                             .message("A gnome guides you out of the maze")
                             .then(guideOut());
                        }
                    });
                }
            }.says(0, "i'll leave you too it then"))
            .start();
    }

    /** The middle of the quest: the orb arrives too late for the other two. */
    private void bolrenCheckpoint(Npc npc) {
        Player p = getOwner();
        if (!holds(ORB)) {
            new Conversation(p, npc)
                .player("king bolren are you ok?")
                .npc("do you have the orb?")
                .player("no, i'm afraid not")
                .npc("please, we must have the orb")
                .npc("if we are to survive")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("king bolren are you ok?")
            .player("i have the orb")
            .npc("thank you traveller, but it's too late")
            .npc("we're all doomed")
            .npc("oh my the misery, the horror")
            .player("what happened?")
            .npc("they came in the night")
            .npc("i don't how many, enough")
            .player("who?")
            .npc("khazard troops")
            .npc("they slaughtered anyone who got in their way")
            .npc("women, children, my wife")
            .player("i'm sorry")
            .npc("they took the other orbs")
            .npc("now we're defenseless")
            .player("where did they take them?")
            .npc("they headed north of the")
            .npc("battlefields to the dead valleys")
            .npc("a warlord carries the orbs")
            .options(new Choice("I will find the warlord and bring back the orbs",
                                "I'm sorry but i can't help") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("i understand, this isn't your battle");
                        return;
                    }
                    c.npc("you are brave")
                     .npc("but this task will be tough even for you,")
                     .npc("i wish you the best of luck traveller")
                     .npc("once again you are our only hope")
                     .npc("i will safeguard this orb")
                     .npc("and pray for your safe return")
                     .npc("my assistant will guide you out")
                     .take(ORB, 1)
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(ORB_DELIVERED);
                         }
                     })
                     .message("A gnome guides you out of the maze")
                     .then(guideOut());
                }
            })
            .start();
    }

    private void bolrenEnding(Npc npc) {
        Player p = getOwner();
        if (!holds(ORBS)) {
            new Conversation(p, npc)
                .player("bolren, i have returned")
                .npc("you made it back")
                .npc("do you have the orbs?")
                .player("no, i'm afraid not")
                .npc("please, we must have the orbs")
                .npc("if we are to survive")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("bolren, i have returned")
            .npc("you made it back")
            .npc("do you have the orbs?")
            .player("i have them here")
            .npc("hooray, you're amazing")
            .npc("i didn't think it was possible")
            .npc("but you've saved us")
            .npc("once the orbs are replaced we will be safe once more")
            .npc("come with me and we shall begin the ceremony")
            .player("what now?")
            .npc("the spirit tree has looked over us for centuries")
            .npc("now we must pay our respects")
            .take(ORBS, 1)
            .message("bolren takes the orbs")
            .message("the gnomes begin to chant")
            .message("Su tana, en tania")
            .message("They continue to chant")
            .message("As the king gnome climbs the tree")
            .message("placing the two Orbs at the peak of the spirit tree")
            .npc("now at last my people are safe once more")
            .npc("and can live in peace")
            .player("i'm pleased i could help")
            .npc("you are modest brave traveller")
            .npc("please for your efforts take this amulet")
            .npc("it's made from the same sacred stone as the orbs of protection")
            .npc("it will help keep you safe on your journeys")
            .give(new InvItem(AMULET, 1))
            .player("thank you king bolren")
            .npc("the tree has many other powers")
            .npc("some of which i cannot reveal")
            .npc("however as a friend of the gnome")
            .npc("people you can now use the tree's")
            .npc("magic to teleport to other trees")
            .npc("grown from related seeds")
            .then(new Effect() {
                public void run(Conversation c) {
                    setStage(FINISHED);
                }
            })
            .start();
    }

    /**
     * Afterwards he will replace the amulet, as many times as it is lost.
     *
     * Jagex let this be drop-tricked and it is the only source of the best
     * armour amulet in the game, so it is left exactly as it was.
     */
    private void bolrenAfterwards(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .player("hello again bolren")
            .npc("well hello, it's good to see you again");
        if (holds(AMULET) || p.getInventory().wielding(AMULET)) {
            c.player("good to see you").start();
            return;
        }
        c.player("i've lost my amulet")
         .npc("oh dear")
         .npc("here take another")
         .give(new InvItem(AMULET, 1))
         .start();
    }

    private Effect guideOut() {
        return new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(MAZE_MOUTH_X, MAZE_MOUTH_Y, false);
            }
        };
    }

    // --------------------------------------------------------------- elkoy --

    /**
     * The maze's doorman. Walking it is possible and he is the shortcut, which
     * is why every one of his conversations after the first ends with the offer.
     */
    private void elkoy(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("hello, welcome to our maze")
                .npc("i'm elkoy the tree gnome")
                .player("i haven't heard of your sort")
                .npc("there's not many of us left")
                .npc("once you could find tree gnomes")
                .npc("anywhere in the world, now we hide")
                .npc("in small groups to avoid capture")
                .player("capture by whom?")
                .npc("tree gnomes have been hunted")
                .npc("for so called 'fun' since i")
                .npc("can remember, our main threat")
                .npc("nowadays are General Khazard's troops")
                .npc("they know no mercy, but are also")
                .npc("very dense, they'll never find")
                .npc("their way through our maze")
                .npc("have fun")
                .start();
            return;
        }
        if (completed()) {
            offerGuide(new Conversation(p, npc)
                .player("hello little man")
                .npc("hi there, hope life")
                .npc("is treating you well"));
            return;
        }
        if (has(WARLORD_DEAD)) {
            offerGuide(new Conversation(p, npc)
                .player("hello elkoy")
                .npc("you truly are a hero")
                .player("thanks")
                .npc("you saved us by")
                .npc("returning the orbs of")
                .npc("protection, i'm humbled")
                .npc("and wish you well"));
            return;
        }
        if (has(ORB_DELIVERED)) {
            offerGuide(new Conversation(p, npc)
                .player("hello elkoy")
                .npc("did you hear? khazard's men")
                .npc("have pillaged the village!")
                .npc("they slaughtered many")
                .npc("and took the other orbs")
                .npc("in an attempt to lead us")
                .npc("all out of the maze")
                .npc("when will the misery end?"));
            return;
        }
        if (has(GOT_ORB)) {
            Conversation c = new Conversation(p, npc)
                .player("hello elkoy")
                .npc("you're back! and the orb?");
            if (!holds(ORB)) {
                c.player("no, i'm afraid not")
                 .npc("please, we must have the orb")
                 .npc("if we are to survive")
                 .start();
                return;
            }
            offerGuide(c.player("i have it here")
                .npc("you're our saviour")
                .npc("please return it to the village and we are all saved"));
            return;
        }
        if (has(WOOD_ASKED)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("you must retrieve the orb")
                .npc("or the gnome village is doomed")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello elkoy")
            .npc("oh my, oh my")
            .player("what's wrong?")
            .npc("the orb, they have the orb")
            .npc("we're doomed")
            .start();
    }

    private void offerGuide(Conversation c) {
        c.npc("would you like me to show")
         .npc("you the way to the village?")
         .options(new Choice("Yes please", "No thanks Elkoy") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("ok then take care");
                    return;
                }
                c.npc("ok then follow me")
                 .message("elkoy leads you to the gnome village")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().teleport(VILLAGE_X, VILLAGE_Y, false);
                     }
                 });
            }
        })
        .start();
    }

    // -------------------------------------------------------------- montai --

    private void montai(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("i can't talk now")
                .npc("can't you see we're trying to win a battle here?")
                .npc("if we can't hold back khazard's men")
                .npc("we're all doomed")
                .start();
            return;
        }
        if (has(ORB_DELIVERED) || completed()) {
            new Conversation(p, npc)
                .player("hello montai, how are you?")
                .npc("i'm ok, this battle is going")
                .npc("to take longer to win than i expected")
                .npc("the khazard troops won't give up even without the orb")
                .player("hang in there")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            new Conversation(p, npc)
                .player("i have the orb of protection")
                .npc("incredible, for a human")
                .npc("you really are something")
                .player("thanks... i think!")
                .npc("I'll stay here with my troops")
                .npc("and try and hold khazard's men back")
                .npc("you return the orb to the gnome village")
                .npc("go as quick as you can")
                .npc("the village is still unprotected")
                .start();
            return;
        }
        if (has(WALL_DOWN)) {
            new Conversation(p, npc)
                .player("i've breeched the stronghold")
                .npc("i saw, that was a beautiful sight")
                .npc("the khazard troops didn't know what hit them")
                .npc("now is the time to retrieve the orb")
                .npc("it's all in your hands")
                .npc("i'll be praying for you")
                .start();
            return;
        }
        if (has(TRACKING)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello warrior we need the coordinates")
                .npc("for a direct hit from the ballista")
                .npc("once you have a direct hit you will be able")
                .npc("to enter the stronghold and retrieve the orb")
                .start();
            return;
        }
        if (has(WOOD_GIVEN)) {
            montaiTrackers(npc);
            return;
        }
        if (has(WOOD_ASKED)) {
            montaiWood(npc);
            return;
        }
        montaiOpening(npc);
    }

    private void montaiOpening(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("hello traveller")
            .npc("are you here to help or just to watch?")
            .player("I've been sent by king Bolren")
            .player("to retrieve the orb of protection")
            .npc("excellent we need all the help we can get")
            .npc("i'm commander montai")
            .npc("the orb is in the khazard stronghold to the north")
            .npc("but until we weaken their defences")
            .npc("we can't get close")
            .player("what can i do?")
            .npc("first we need to strengthen our own defences")
            .npc("we desperately need wood to make more battlements")
            .npc("six loads of logs should do it")
            .npc("once the battlements are gone it's all over")
            .options(new Choice("Ok, i'll gather some wood",
                                "Sorry i no longer want to be involved") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("that's a shame we could")
                         .npc("have done with your help");
                        return;
                    }
                    c.npc("please be as quick as you can")
                     .npc("i don't know how much longer we can hold out")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(WOOD_ASKED);
                         }
                     });
                }
            })
            .start();
    }

    private void montaiWood(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .player("hello")
            .npc("hello again, we're still desperate for wood soldier");
        if (count(LOGS) < LOGS_NEEDED) {
            c.npc("we need at least six loads of logs")
             .player("i'll see what i can do")
             .npc("thankyou")
             .start();
            return;
        }
        c.player("i have some here")
         .message("you give some wood to the commander")
         .then(new Effect() {
             public void run(Conversation c) {
                 takeAll(LOGS, LOGS_NEEDED);
                 set(WOOD_GIVEN);
             }
         })
         .npc("that's excellent now we can make more defensive battlements")
         .npc("give me a moment to organise the troops")
         .npc("and then come speak to me")
         .npc("i'll inform you of our next phase of attack")
         .start();
    }

    private void montaiTrackers(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("how are you doing montai?")
            .npc("we're hanging in there soldier")
            .npc("for the next phase of the attack")
            .npc("we need to breech their stronghold")
            .npc("the ballista can break through the stronghold wall")
            .npc("and then we can advance and seize back the orb")
            .player("so what's the problem?")
            .npc("from this distance we can't get an accurate shot away")
            .npc("we need the correct coordinates of the stronghold")
            .npc("for a direct hit")
            .npc("i've sent out three tracker gnomes to gather them")
            .player("have they returned?")
            .npc("i'm afraid not and we're running out of time")
            .npc("I need you to go into the heart of the battlefield")
            .npc("find the trackers and bring back the coordinates.")
            .npc("Do you think you can do it?")
            .options(new Choice("I'll try my best", "No, i've had enough of your battle") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("i understand, this isn't your fight");
                        return;
                    }
                    c.npc("thankyou, you're braver than most")
                     .npc("i don't know how long i will be able to hold out")
                     .npc("once you have the coordinates")
                     .npc("come back and fire the ballista")
                     .npc("right into those monsters")
                     .npc("if you can retrieve the orb and bring safety back to my people")
                     .npc("none of the blood spilled on this field will be in vain")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(TRACKING);
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------ trackers --

    private void tracker1(Npc npc) {
        Player p = getOwner();
        if (has(ORB_DELIVERED) || completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("when will this battle end?")
                .npc("i feel like i've been fighting forever")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            new Conversation(p, npc)
                .player("how are you tracker?")
                .npc("now we have the globe i'm much better")
                .npc("they won't stand a chance without it")
                .start();
            return;
        }
        if (has(WALL_DOWN)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("well done, you've broken down there defenses")
                .npc("this battle must be ours")
                .start();
            return;
        }
        if (has(WOOD_GIVEN)) {
            new Conversation(p, npc)
                .player("do you know the coordinates")
                .player("of the khazard stronghold?")
                .npc("i managed to get one although it wasn't easy")
                .npc("the height coordinate is " + TRUE_HEIGHT)
                .player("well done")
                .npc("the other two tracker gnomes")
                .npc("should have the other coordinates")
                .npc("if they're still alive")
                .player("ok, take care")
                .start();
            return;
        }
        if (has(WOOD_ASKED)) {
            new Conversation(p, npc)
                .player("hi there")
                .npc("we're trying to hold them back")
                .npc("but without more wood we won't be able to last long")
                .player("hang in there little man")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("i can't talk now")
            .npc("can't you see we're trying to win a battle here?")
            .start();
    }

    private void tracker2(Npc npc) {
        Player p = getOwner();
        if (has(ORB_DELIVERED) || completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("when will this battle end?")
                .npc("i feel like i've been locked up my whole life")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            new Conversation(p, npc)
                .player("how are you tracker?")
                .npc("now we have the globe 'm much better")
                .npc("soon my comrades will come and free me")
                .start();
            return;
        }
        if (has(WALL_DOWN)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("well done you've broken down there defenses")
                .npc("this battle must be ours")
                .start();
            return;
        }
        if (has(WOOD_GIVEN)) {
            new Conversation(p, npc)
                .message("The gnome looks beaten and weak")
                .npc("they caught me spying on the stronghold..")
                .npc("they beat and tortured me")
                .npc("but i didn't crack, i told them nothing")
                .npc("they can't break me")
                .player("i'm sorry little man")
                .npc("don't be, i have the position of the stronghold")
                .npc("the y coordinate is " + TRUE_Y)
                .player("well done")
                .npc("now leave before they find you and all is lost")
                .player("hang in there")
                .npc("go")
                .start();
            return;
        }
        if (has(WOOD_ASKED)) {
            new Conversation(p, npc)
                .player("hi there")
                .npc("the battle is far from over")
                .npc("if you have a pure heart you will help us win")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("i can't talk now")
            .npc("if the guards catch me i'll be dead gnome meat")
            .start();
    }

    /**
     * The third scout has been broken by the war and gives his number as a
     * riddle: "more than me, less than our feet" -- more than one, fewer than
     * four -- and then "more than we", which settles it at three.
     */
    private void tracker3(Npc npc) {
        Player p = getOwner();
        if (has(ORB_DELIVERED) || completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("i feel dizzy, where am i?")
                .npc("oh dear, oh dear i need some rest")
                .player("I think you do")
                .start();
            return;
        }
        if (has(WALL_DOWN)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("don't talk to me, you can't see me")
                .npc("no one can just the demons")
                .message("The poor gnome has gone mad")
                .start();
            return;
        }
        if (has(WOOD_GIVEN)) {
            new Conversation(p, npc)
                .player("are you ok?")
                .message("The gnome looks dilerious")
                .npc("ok? who's ok? not me")
                .npc("hee hee")
                .player("what's wrong?")
                .npc("you can't see me, no one can")
                .npc("monsters, demons, they're all around me")
                .player("what do you mean?")
                .npc("they're dancing, all of them hee hee")
                .message("He's clearly lost the plot")
                .player("do you have the x coordinate for the khazard stronghold?")
                .npc("who holds the stronghold?")
                .player("what?")
                .npc("more than me")
                .npc("less than our feet")
                .player("you're mad")
                .npc("more than we")
                .npc("and khazard's men are beat")
                .message("The toll of war has affected his mind")
                .player("i'll pray for you little man")
                .npc("all day we pray in the hay")
                .npc("hee hee")
                .message("The poor gnome has gone mad")
                .start();
            return;
        }
        if (has(WOOD_ASKED)) {
            new Conversation(p, npc)
                .player("hi there")
                .npc("i can't stand this war")
                .npc("the misery, the pain, it's driving me crazy")
                .npc("when will it end?")
                .message("He doesn't seem to be dealing with the battle very well")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("i can't talk now")
            .npc("can't you see we're trying to win a battle here?")
            .start();
    }

    // ------------------------------------------------------------- warlord --

    /**
     * Every one of his conversations ends in a fight, so every one of them ends
     * the same way: stop the dialogue first, because attackPlayer() will not
     * touch a player the dialogue still has marked busy.
     */
    private void warlord(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (has(WARLORD_DEAD)) {
            c.player("i thought i killed you?")
             .npc("fool.. warriors blessed by khazard don't die")
             .npc("you can't kill that which is already dead")
             .npc("however i can kill you");
        } else if (has(ORB_DELIVERED)) {
            c.player("you there, stop!")
             .npc("go back to your pesky little green friends")
             .player("i've come for the orbs")
             .npc("you're out of your depth traveller")
             .npc("these orbs are part of a much larger picture")
             .player("they're stolen goods")
             .player("now give them here")
             .npc("hee hee you really think you stand a chance?")
             .npc("i'll crush you!");
        } else if (has(GOT_ORB)) {
            c.player("hello there")
             .npc("you think you're so clever")
             .npc("you know nothing!")
             .player("what?")
             .npc("i'll crush you and those pesky little green men!");
        } else {
            c.player("hello, how are you?")
             .npc("don't speak to me you insignificant wretch!")
             .npc("die, in the name of khazard!");
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                c.stop();
                npc.attackPlayer(c.getPlayer());
            }
        }).start();
    }

    /**
     * He does not stay dead -- he says so himself the next time -- but he does
     * hand over what he is carrying.
     *
     * The orbs go on the ground rather than into the pack. Vanilla's message
     * says satchel, but the orbs were a drop: players safespotted him from
     * inside West Ardougne and picked them up with telekinetic grab, which only
     * works on something lying on a tile.
     */
    private void warlordFell(Npc npc) {
        if (!has(ORB_DELIVERED)) {
            return;
        }
        set(WARLORD_DEAD);
        say("As he falls to the ground...");
        say("A ghostly vapour floats upwards from his battle worn armour");
        say("Out of sight, you hear a shrill scream in the still air of the valley");
        if (holds(ORBS)) {
            return;
        }
        say("You search his satchel and find the orbs of protection");
        world.registerItem(new Item(ORBS, npc.getX(), npc.getY(), 1, getOwner()));
    }

    // --------------------------------------------------- the village crowd --

    /*
     * Local gnome 399 and Kalron 402 are the two bystanders of the quest: six
     * spawns each, no part in it, and a line for every stage of it. They are
     * here rather than in RandomChat because they are staged -- what they say
     * only makes sense at the point it is said. The third bystander, gnome
     * troop 409, is not staged and lives in RandomChat; the argument for the
     * split is written out under RandomChat.GNOME_TROOP.
     *
     * Both transcripts are cut into five sections and both use the same five
     * cutting points, which map onto our stage bits exactly:
     *
     *   "before the quest, and during it before the orb"  ->  !GOT_ORB
     *   "after getting / after searching the chest"       ->  GOT_ORB
     *   "after bolren tells you the orbs were stolen"     ->  ORB_DELIVERED
     *   "after defeating the khazard warlord"             ->  WARLORD_DEAD
     *   "after the quest"                                 ->  completed()
     *
     * Read bottom-up, most-advanced first, so each test only has to name the
     * bit it needs rather than the bit it needs and every bit after it.
     */

    /**
     * The village idiot, and the only npc in the quest with nothing to say
     * about it. He sings.
     *
     * Two spawns of him are at y 1636 and 1639 -- plane 1, the treetop village
     * itself -- and four are on the ground below, so he is met both before and
     * after the climb.
     */
    private void localGnome(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("you're the best")
                .player("thanks")
                .npc("well, i'm better")
                .npc("hee hee")
                .start();
            return;
        }
        if (has(WARLORD_DEAD)) {
            new Conversation(p, npc)
                .player("hello gnome")
                .npc("soon we're gonna have the sacred ceremony")
                .npc("and boy am i going to party")
                .npc("lock up your daughters")
                .npc("hee hee")
                .start();
            return;
        }
        if (has(ORB_DELIVERED)) {
            new Conversation(p, npc)
                .player("hi")
                .npc("must save the orbs and kill the khazard warlord")
                .npc("that will be fun")
                .npc("hee hee")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            new Conversation(p, npc)
                .player("hello little man")
                .npc("little man stronger than big man")
                .npc("hee hee")
                .npc("lardi dee, lardi da")
                .message("Cheeky little gnome")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("lardi dee, lardi da")
            .player("are you alright?")
            .npc("hee hee, lardi da, lardi dee")
            .message("The gnome appears to be singing")
            .start();
    }

    /**
     * Lost in the hedge maze, permanently. All six of his spawns are inside it,
     * between (629,716) and (662,679), which is the joke: he helped build the
     * thing and he cannot get out of it.
     *
     * Two things here are Jagex's and are kept as recorded rather than tidied:
     *
     *  - "the village has been" stops mid-sentence. The transcript marks it sic,
     *    which is the transcriber saying "yes, it really ended there" -- so it
     *    is a line Jagex shipped broken, not a line someone failed to copy. The
     *    difference matters: a transcriber's slip gets corrected, an author's
     *    does not, and only one of those was ever seen by a player.
     *
     *  - The last conversation opens on Kalron, with no greeting from the
     *    player, and with a question that answers nothing that was asked. After
     *    the quest he has been found, and he is still suspicious of you.
     */
    private void kalron(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("are you trying to be funny?")
                .player("no")
                .npc("hmmm")
                .start();
            return;
        }
        if (has(WARLORD_DEAD)) {
            new Conversation(p, npc)
                .player("hello little man")
                .npc("hello i hope they come out and find me soon,")
                .npc("it's getting cold")
                .start();
            return;
        }
        if (has(ORB_DELIVERED)) {
            new Conversation(p, npc)
                .player("hello, how are you?")
                .npc("oh my i'll never find my way back")
                .npc("before khazard's men come and hunt me down")
                .start();
            return;
        }
        if (has(GOT_ORB)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh my, oh my")
                .npc("the village has been")
                .npc("and i'm still lost")
                .npc("oh dear")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("gotta find a way out")
            .npc("we built this maze for protection")
            .npc("but i can't get used to it")
            .npc("i'm always getting lost")
            .start();
    }

    // ------------------------------------------------------------- scenery --

    private void scenery(QuestTrigger trigger, GameObject object) {
        if (object.getID() == FENCE) {
            if (trigger == QuestTrigger.DOOR_ACT1) {
                looseFence(object);
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            if (object.getID() == CHEST_OPEN) {
                say("You close the chest");
                swap(object, CHEST_SHUT);
            } else {
                // Claiming an object takes its second command away from
                // ObjectAction as well as its first, so the claim answers both.
                say("Nothing interesting happens");
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case BALLISTA: fireBallista(); break;
            case WALL: climbWall(object); break;
            case CHEST_SHUT:
                say("You open the chest");
                swap(object, CHEST_OPEN);
                break;
            case CHEST_OPEN: searchChest(); break;
            case SPIRIT_TREE: oldTree(); break;
            case YOUNG_TREE: youngTree(); break;
            default: break;
        }
    }

    /**
     * The loose panels swing aside for anyone; the message is OpenRSC's,
     * witnessed not copied. The wall sits on the tile's low edge, so the two
     * sides of a direction-0 fence are y and y-1, and of the direction-1 one
     * x and x-1 -- the same arithmetic as Underground Pass's spider railings.
     */
    private void looseFence(GameObject fence) {
        Player p = getOwner();
        say("You push your way through the fence");
        if (fence.getY() == FENCE_Y) {
            int y = p.getY() == FENCE_Y ? FENCE_Y - 1 : FENCE_Y;
            p.teleport(fence.getX(), y, false);
        } else {
            int x = p.getX() == FENCE_FIELD_X ? FENCE_FIELD_X - 1 : FENCE_FIELD_X;
            p.teleport(x, FENCE_FIELD_Y, false);
        }
    }

    /**
     * The siege engine: three numbers, and only 4, 3, 5 hits anything.
     *
     * The menus nest because the second question cannot be asked until the
     * first is answered. Each answer is kept in a local so the branch below it
     * can read it -- an anonymous class can only close over something final.
     */
    private void fireBallista() {
        if (!has(WOOD_GIVEN)) {
            say("The ballista is damaged");
            say("It cannot be used until the gnomes have finished their repairs");
            return;
        }
        if (has(WALL_DOWN)) {
            say("The ballista has been damaged, it is out of use");
            return;
        }
        new Conversation(getOwner(), null)
            .message("To fire the ballista you Must first set the coordinates")
            .message("Set the height coordinate to")
            .options(new Choice(COORDS) {
                public void picked(int option, Conversation c) {
                    final int height = option + 1;
                    c.message("Set the x coordinate to")
                     .options(new Choice(COORDS) {
                        public void picked(int option, Conversation c) {
                            final int x = option + 1;
                            c.message("Set the y coordinate to")
                             .options(new Choice(COORDS) {
                                public void picked(int option, Conversation c) {
                                    shoot(c, height, x, option + 1);
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    private void shoot(Conversation c, int height, int x, int y) {
        c.message("You fire the ballista")
         .message("The huge spear flies through the air");
        if (height != TRUE_HEIGHT || x != TRUE_X || y != TRUE_Y) {
            c.message("Straight over the khazard stronghold")
             .message("Into the valleys behond")
             .message("You've missed the target");
            return;
        }
        c.message("And screams down directly into the Khazard stronghold")
         .message("A deafening crash echoes over the battlefield")
         .message("The front entrance is reduced to rubble")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(WALL_DOWN);
             }
         });
    }

    /**
     * The hole the ballista made, three tiles wide at (658-660, 632).
     *
     * The wall stays a blocking object either way -- what the spear did was
     * make it climbable, not make it go away -- so crossing it is a step to the
     * other side in y, in whichever direction the player is facing it from.
     */
    private void climbWall(GameObject wall) {
        Player p = getOwner();
        if (!has(WALL_DOWN)) {
            say("The wall is damaged");
            say("But not enough to climb through");
            return;
        }
        boolean goingIn = p.getY() > wall.getY();
        if (has(GOT_ORB)) {
            say("The wall is reduced to");
            say("Rubble, you manage to climb over");
        } else {
            say("The wall is reduced to rubble");
            say("You manage to climb over");
        }
        p.teleport(p.getX(), goingIn ? wall.getY() - 1 : wall.getY() + 1, false);
        if (!goingIn || has(GOT_ORB)) {
            return;
        }
        // He notices the hole in his wall, once, on the way in.
        Npc commander = nearby(COMMANDER);
        if (commander != null) {
            new Conversation(p, commander)
                .npc("what?! how did you manage to get in here?")
                .player("i've come for the orb")
                .npc("i'll never let you take it!")
                .start();
        }
    }

    /**
     * The test is what is in the pack, not what the quest remembers, which is
     * Jagex's and is what made the orb drop-trickable. Left alone.
     */
    private void searchChest() {
        if (holds(ORB)) {
            say("You search the chest, but find nothing");
            return;
        }
        say("You search the chest");
        say("And find the orb of protection");
        give(ORB, 1);
        set(GOT_ORB);
    }

    // -------------------------------------------------------- spirit trees --

    private void oldTree() {
        if (!completed()) {
            say("The tree doesn't feel like talking");
            return;
        }
        new Conversation(getOwner(), null)
            .message("The tree talks in an old tired voice...")
            .message("You friend of gnome people, you friend of mine")
            .message("Would you like me to take you somewhere?")
            .options(new Choice("Where can i go?", "No thanks old tree") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.message("The tree talks again..")
                     .message("You can travel to the trees")
                     .message("Which are related to myself")
                     .options(new Choice("Battlefield of Khazard",
                                         "Forest north of Varrock",
                                         "the gnome stronghold") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                travel(c, TREE_BATTLEFIELD_X, TREE_BATTLEFIELD_Y);
                            } else if (option == 1) {
                                travel(c, TREE_VARROCK_X, TREE_VARROCK_Y);
                            } else {
                                travel(c, TREE_STRONGHOLD_X, TREE_STRONGHOLD_Y);
                            }
                        }
                    });
                }
            })
            .start();
    }

    /** The saplings only know one road, and it leads home. */
    private void youngTree() {
        if (!completed()) {
            say("The tree doesn't feel like talking");
            return;
        }
        new Conversation(getOwner(), null)
            .message("The young spirit tree talks..")
            .message("Hello gnome friend")
            .message("Would you like to travel to the home of the tree gnome")
            .options(new Choice("Yes please", "No thank you I'm not interested") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        travel(c, TREE_VILLAGE_X, TREE_VILLAGE_Y);
                    }
                }
            }.says(1, "No thank you"))
            .start();
    }

    private void travel(Conversation c, final int x, final int y) {
        c.message("You place your hands on the dry tough bark of the spirit tree")
         .message("and feel a surge of energy run through your veins")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.getPlayer().teleport(x, y, false);
             }
         });
    }
}
