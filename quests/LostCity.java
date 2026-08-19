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
import org.rscdaemon.server.util.Formulae;

/**
 * Lost City. Released 27 February 2002, written by Paul Gower.
 *
 * Four adventurers are camped in the Lumbridge swamp hunting a city they will
 * not name. Talk them round in circles for long enough and one of them lets
 * slip that a leprechaun in a nearby tree knows the way. The leprechaun sends
 * you to Entrana for a branch of the Dramen tree; the monks who run the boat
 * will not carry weapons, so the level 95 spirit guarding the tree has to be
 * fought with whatever is left. A knife turns the branch into a staff, and the
 * staff turns an empty shack in the swamp into the door to Zanaris.
 *
 *     Adventurer 207 (178,667)  a cleric      Adventurer 208 (178,667) a wizard
 *     Adventurer 209 (177,669)  a warrior     Adventurer 210 (180,669) an archer
 *     Leprechaun 211            spawned by searching the tree
 *     tree spirit 216           spawned by chopping the Dramen tree
 *
 *     Tree 237        (172,662)   "Search" -- the leprechaun's tree
 *     Dramen Tree 245 (412,3402)  "Chop"   -- Entrana dungeon
 *     Door 66         (126,686)   "Open"   -- the shack, and the way to Zanaris
 *
 * Everything else on the route already worked and is left alone: the monks run
 * the boat (EntranaMonks), the ladder at (426,548) drops into the dungeon
 * through the ordinary climb-down handler, and the Magic Door at (406,3392) is
 * an ObjectTelePoint out to the wilderness.
 *
 * Deviations:
 *
 *  - The shack door teleports from either side rather than only from outside.
 *    The landscape's walls are not objects, so the quest cannot tell inside
 *    from outside, and the shack is empty in any case. RSCD's own handling had
 *    the same property: an ObjectTelePoint at (126,686) sent anyone who opened
 *    the door to Zanaris, quest or no quest, staff or no staff. That entry is
 *    gone from both ObjectTelePoints.xml.gz and ObjectTelePoints.xml, so the
 *    gate is the only way in. Coming back out is the ladder at (128,3518),
 *    which the ordinary climb-up handler already gets right.
 *
 *  - RSCD had ten extra Adventurer (id 208) spawns scattered from Falador to
 *    Zanaris. Jagex placed that npc once, at the swamp camp. The ten were
 *    removed rather than left to recite Lost City dialogue at passers-by.
 *
 *  - The tree spirit is remembered in a session flag, not in the quest stage.
 *    It guards the tree rather than marking progress: it is fought again on a
 *    later login, and it is fought again by a player who comes back for another
 *    branch after the quest, which is what it did in 2002.
 *
 *  - Entrana's ban on weapons and armour, and the prayer drain on the way into
 *    the dungeon, are properties of the island rather than of this quest, and
 *    live with the monks.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class LostCity extends Quest {

    public final static int UID = Quests.LOST_CITY;

    private static final int CLERIC = 207, WIZARD = 208;
    private static final int WARRIOR = 209, ARCHER = 210;
    private static final int LEPRECHAUN = 211, SPIRIT = 216;

    private static final int TREE = 237, TREE_X = 172, TREE_Y = 662;
    private static final int DRAMEN_TREE = 245;
    private static final int SHACK_DOOR = 66, DOOR_X = 126, DOOR_Y = 686;

    private static final int KNIFE = 13, STAFF = 509, BRANCH = 510;

    private static final int WOODCUT = 8, CRAFTING = 12;
    private static final int CHOP_LEVEL = 36, CARVE_LEVEL = 31;

    private static final int ZANARIS_X = 126, ZANARIS_Y = 3518;

    /** The spirit is a guard, not a milestone -- see the class comment. */
    private static final String SPIRIT_FLAG = "dramen_spirit_killed";

    private static final int STARTED = 1;
    private static final int TOLD = 2;
    private static final int FINISHED = 4;

    public LostCity(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Lost city");
        setFinalStage(FINISHED);
        associateNpc(CLERIC);
        associateNpc(WIZARD);
        associateNpc(WARRIOR);
        associateNpc(ARCHER);
        associateNpc(LEPRECHAUN);
        associateNpc(SPIRIT);
        associateObject(TREE, TREE_X, TREE_Y);
        associateObject(DRAMEN_TREE);
        associateDoor(SHACK_DOOR, DOOR_X, DOOR_Y);
        /* Both halves, so knife-on-branch reaches ITEM_ON_ITEM. */
        associateItem(KNIFE);
        associateItem(BRANCH);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Legends tell of a magical lost city hidden in the swamps. Many adventurers have tried to find this city, but it is proving difficult. Can you unlock the secrets of the city of Zanaris?");
        setStartPoint("Swamp, south of Lumbridge");
        setSpeakTo("Adventurers");
        setMissionLength("Long");
        requireLevel(CRAFTING, CARVE_LEVEL);
        requireLevel(WOODCUT, CHOP_LEVEL);
        require("Must kill a level 95 spirit unarmed");
        rewardOther("Access to the lost city of Zanaris");
    }

    public void completeQuest() {
        getOwner().getActionSender().sendMessage(
            "@gre@Well done you have completed the Lost City of Zanaris quest");
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

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == SPIRIT) {
                    getOwner().setFlag(SPIRIT_FLAG, 1);
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case CLERIC:
                case WIZARD:
                case WARRIOR:
                case ARCHER:
                    adventurer(npc);
                    return;
                case LEPRECHAUN:
                    leprechaun(npc);
                    return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                carve((InvItem) entity, used);
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1) {
            shackDoor(object);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case TREE:        searchTree(); return;
            case DRAMEN_TREE: chop(object); return;
        }
    }

    // -------------------------------------------------------- the camp fire --

    private void adventurer(Npc npc) {
        Player p = getOwner();
        if (has(TOLD) || completed()) {
            new Conversation(p, npc)
                .player("thankyou for your information")
                .player("It has helped me a lot in my quest to find Zanaris")
                .npc("So what have you found out?")
                .npc("Where is Zanaris?")
                .player("I think I will keep that to myself")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("So let me get this straight")
                .player("I need to search the trees near here for a leprechaun?")
                .player("And he will tell me where Zanaris is?")
                .npc("That is what the legends and rumours are,yes")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc).npc("hello traveller");
        opening(c);
        c.start();
    }

    private void opening(Conversation c) {
        c.options(new Choice("Do you know any good adventures I can go on",
                             "What are you camped here for?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well we're on an adventure now")
                     .npc("Mind you this is our adventure")
                     .npc("We don't want to share it - find your own");
                    adventureMenu(c);
                } else {
                    c.npc("We're looking for Zanaris");
                    zanarisMenu(c, true);
                }
            }
        });
    }

    private void adventureMenu(Conversation c) {
        c.options(new Choice("Please tell me",
                             "I don't think you've found a good adventure at all") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("No");
                    return;
                }
                c.npc("We're on one of the greatest adventures i'll have you know")
                 .npc("Searching for zanaris isn't a walk in the park");
                zanarisMenu(c, true);
            }
        });
    }

    /**
     * The three questions about Zanaris. Asking what makes them think it is out
     * here loops back to the other two, so the legends question drops off the
     * second time round rather than offering itself again.
     */
    private void zanarisMenu(Conversation c, boolean legends) {
        Choice choice = legends
            ? new Choice("Who's Zanaris?", "what's Zanaris?",
                         "What makes you think it's out here?") {
                public void picked(int option, Conversation c) {
                    zanarisAnswer(option, c);
                }
              }
            : new Choice("Who's Zanaris?", "what's Zanaris?") {
                public void picked(int option, Conversation c) {
                    zanarisAnswer(option, c);
                }
              };
        c.options(choice);
    }

    private void zanarisAnswer(int option, Conversation c) {
        if (option == 0) {
            c.npc("hehe Zanaris isn't a person")
             .npc("It's a magical hidden city");
            hiddenMenu(c);
            return;
        }
        if (option == 1) {
            c.npc("I don't think we want other people competing with us to find it")
             .options(new Choice("Please tell me", "Oh well never mind") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("No");
                     }
                 }
             });
            return;
        }
        c.npc("Don't you know the legends?")
         .npc("Of the magical city, hidden in the swamp");
        zanarisMenu(c, false);
    }

    private void hiddenMenu(Conversation c) {
        c.options(new Choice("If it's hidden how are you planning to find it",
                             "There's no such thing") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well we don't want to tell others that")
                     .npc("We want all the glory of finding it for ourselves")
                     .options(new Choice("Please tell me",
                             "looks like you don't know either if you're sitting around here") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc("No");
                                 return;
                             }
                             c.npc("Of course we know")
                              .npc("We haven't worked out which tree the stupid leprechaun is in yet")
                              .npc("Oops I didn't mean to tell you that");
                             slipUp(c);
                         }
                     });
                    return;
                }
                c.npc("Well when we find which tree the leprechaun is in")
                 .npc("You can eat those words")
                 .npc("Oops I didn't mean to tell you that");
                slipUp(c);
            }
        });
    }

    /** Both ways of pushing them end in the same admission. */
    private void slipUp(Conversation c) {
        c.player("So a Leprechaun knows where Zanaris is?")
         .npc("Eerm")
         .npc("yes")
         .player("And he's in a tree somewhere around here")
         .player("thankyou very much")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(STARTED);
             }
         });
    }

    // -------------------------------------------------------- the leprechaun --

    private void searchTree() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the tree");
        Npc found = nearby(LEPRECHAUN);
        if (found != null) {
            p.getActionSender().sendMessage("the little man is still there");
            return;
        }
        p.getActionSender().sendMessage("@yel@a little man jumps out of the tree!");
        final Npc shamus = new Npc(LEPRECHAUN, TREE_X, TREE_Y + 1,
            TREE_X - 3, TREE_X + 3, TREE_Y - 2, TREE_Y + 4);
        shamus.setRespawn(false);
        world.registerNpc(shamus);
        world.getDelayedEventHandler().add(new SingleEvent(null, 120000){
            public void action() {
                if (shamus.getID() == LEPRECHAUN) {
                    world.unregisterNpc(shamus);
                }
            }
        });
    }

    private Npc nearby(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id) {
                return n;
            }
        }
        return null;
    }

    private void leprechaun(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("Ay you big elephant")
            .npc("You have caught me")
            .npc("What would you be wanting with old Shamus then?");
        if (has(TOLD) || completed()) {
            c.options(new Choice("I'm not sure", "How do I get to Zanaris again?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("I dunno, what stupid people")
                         .npc("Who go to all the trouble to catch leprechaun's")
                         .npc("When they don't even know what they want");
                        return;
                    }
                    c.npc("You need to enter the shed in the middle of the swamp")
                     .npc("While holding a dramenwood staff")
                     .npc("Made from a branch")
                     .npc("Cut from the dramen tree on the island of Entrana");
                }
            });
            c.start();
            return;
        }
        if (!questStarted()) {
            c.player("I'm not sure")
             .npc("Well you'll have to catch me again when you are")
             .start();
            return;
        }
        c.player("I want to find Zanaris")
         .npc("Zanaris?")
         .npc("You need to go in the funny little shed")
         .npc("in the middle of the swamp")
         .player("Oh I thought Zanaris was a city")
         .npc("It is")
         .options(new Choice("How does it fit in a shed then?",
                             "I've been in that shed, I didn't see a city") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("Silly person")
                      .npc("The city isn't in the shed")
                      .npc("The shed is a portal to Zanaris")
                      .player("So I just walk into the shed and end up in Zanaris?");
                 } else {
                     c.player("I didn't see a city");
                 }
                 c.npc("Oh didn't I say?")
                  .npc("You need to be carrying a Dramenwood staff")
                  .npc("Otherwise you do just end up in a shed")
                  .player("So where would I get a staff?")
                  .npc("Dramenwood staffs are crafted from branches")
                  .npc("These staffs are cut from the Dramen tree")
                  .npc("located somewhere in a cave on the island of Entrana")
                  .npc("I believe the monks of Entrana have recetnly")
                  .npc("Started running a ship from port sarim to Entrana")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          set(TOLD);
                      }
                  });
             }
         }.says(1, "I've been in that shed"))
         .start();
    }

    // ------------------------------------------------------- the Dramen tree --

    private void chop(GameObject tree) {
        final Player p = getOwner();
        if (p.getMaxStat(WOODCUT) < CHOP_LEVEL) {
            p.getActionSender().sendMessage(
                "You need a woodcutting level of " + CHOP_LEVEL + " to axe this tree.");
            return;
        }
        int axe = -1;
        for (int a : Formulae.woodcuttingAxeIDs) {
            if (p.getInventory().countId(a) > 0) {
                axe = a;
                break;
            }
        }
        if (axe < 0) {
            p.getActionSender().sendMessage("You need an axe to chop this tree down.");
            return;
        }
        if (p.getFlag(SPIRIT_FLAG) == 0) {
            guard(tree);
            return;
        }
        p.getActionSender().sendMessage("You swing your axe at the tree...");
        p.getActionSender().sendMessage("you cut a branch from the dramen tree");
        give(BRANCH, 1);
    }

    /**
     * The spirit steps out of the tree and starts on the player. Only the
     * player who woke it can be attacked by it, and it is cleared away after
     * five minutes in case the fight was abandoned.
     */
    private void guard(GameObject tree) {
        final Player p = getOwner();
        int x = tree.getX(), y = tree.getY();
        final Npc spirit = new Npc(SPIRIT, x, y + 1, x - 4, x + 4, y - 3, y + 5);
        spirit.setRespawn(false);
        world.registerNpc(spirit);
        new Conversation(p, spirit)
            .npc("Stop")
            .npc("I am the spirit of the Dramen Tree")
            .npc("You must come through me before touching that tree")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    spirit.attackPlayer(p);
                }
            })
            .start();
        world.getDelayedEventHandler().add(new SingleEvent(null, 300000){
            public void action() {
                if (spirit.getID() == SPIRIT) {
                    world.unregisterNpc(spirit);
                }
            }
        });
    }

    /** Either order -- the pair arrives the way the player used it. */
    private void carve(InvItem first, InvItem second) {
        Player p = getOwner();
        if (first == null || second == null) {
            return;
        }
        int a = first.getID(), b = second.getID();
        if (!((a == KNIFE && b == BRANCH) || (a == BRANCH && b == KNIFE))) {
            return;
        }
        if (p.getMaxStat(CRAFTING) < CARVE_LEVEL) {
            p.getActionSender().sendMessage(
                "You need a crafting level of " + CARVE_LEVEL + " to make this staff");
            return;
        }
        take(BRANCH, 1);
        p.getActionSender().sendMessage("you carve the branch into a staff");
        give(STAFF, 1);
    }

    // ------------------------------------------------------------ the shack --

    private void shackDoor(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        if (!p.getInventory().wielding(STAFF)) {
            if (door.getDirection() == 0) {
                p.teleport(door.getX(),
                    p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
            } else {
                p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(),
                    door.getY(), false);
            }
            return;
        }
        p.getActionSender().sendMessage("The world starts to shimmer");
        p.getActionSender().sendMessage("You find yourself in different surroundings");
        p.teleport(ZANARIS_X, ZANARIS_Y, false);
        if (!completed()) {
            setStage(FINISHED);
        }
    }
}
