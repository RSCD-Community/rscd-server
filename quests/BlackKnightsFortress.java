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
 * Black knight's fortress, 6 April 2001. Jagex's own quest list puts it first.
 *
 * Sir Amik Varze, leader of the white knights, wants the black knights' secret
 * weapon found and destroyed. It turns out to be an invincibility potion a witch
 * has spent five years brewing, and the last thing it needs is a cabbage grown
 * in the magic soil of Draynor Manor. Any other cabbage dropped into it wrecks
 * it, which is the whole of the sabotage.
 *
 * Three quest points and 2500 coins. No experience. It is the first quest Jagex
 * gated behind quest points -- twelve of them -- so it cannot be the first one a
 * player does.
 *
 * Two cabbages exist and the quest turns on telling them apart: 18 is the
 * ordinary one that grows everywhere, and 228 is the Draynor Manor one, which
 * only grows on the nine tiles at (229..231, 548..550). Feeding the witch the
 * right cabbage helps her, so the quest refuses it.
 *
 * Getting in needs a guard's uniform worn, not merely carried: a medium bronze
 * helmet and an iron chain mail body. Both are ordinary shop armour.
 *
 * The two doors of the ground-floor meeting hall have ids of their own, 39 at
 * (275,439) and 40 at (278,443). The warning about the meeting -- "I wouldn't
 * go in there if I woz you" -- stays in the mouth of the guard standing outside
 * the room, where the recorded transcript puts it. But the doors are not
 * ordinary: the knights meant it when they said they'd kill anyone who came in.
 * Walking in through the west door sets the nearest black knight on the player,
 * and the south door will not let anyone slip out of the room quietly -- if no
 * knight is already on them, one attacks instead of the door opening. That is
 * how the room plays in the original: crossed at a run, under attack the whole
 * way, out the far side or dead.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class BlackKnightsFortress extends Quest {

    public final static int UID = Quests.BLACK_KNIGHTS_FORTRESS;

    /** Sir Amik has explained the job. */
    private static final int STARTED = 1;
    /** The plan has been overheard at the grill. */
    private static final int HEARD_PLAN = 2;
    /** A cabbage has gone down the hole and the potion is ruined. */
    private static final int SABOTAGED = 3;
    private static final int FINISHED = 4;

    private static final int SIR_AMIK_VARZE = 110;
    /** "Guard -- He's here to guard this fortress". Only the fortress ones. */
    private static final int GUARD = 100;

    /** The east entrance, at (271,441). The only door of its id in the world. */
    private static final int ENTRANCE_DOOR = 38;
    private static final int ENTRANCE_DOOR_X = 271;
    private static final int ENTRANCE_DOOR_Y = 441;
    private static final int OPEN_DOOR = 11;

    /** The meeting hall's own doors -- see the class comment. */
    private static final int MEETING_DOOR_WEST = 39;
    private static final int MEETING_DOOR_WEST_X = 275;
    private static final int MEETING_DOOR_WEST_Y = 439;
    private static final int MEETING_DOOR_SOUTH = 40;
    private static final int MEETING_DOOR_SOUTH_X = 278;
    private static final int MEETING_DOOR_SOUTH_Y = 443;

    /** The three black knights in the meeting hall, and their room. */
    private static final int BLACK_KNIGHT = 66;
    private static final int HALL_MIN_X = 275;
    private static final int HALL_MAX_X = 283;
    private static final int HALL_MIN_Y = 433;
    private static final int HALL_MAX_Y = 443;

    /** The grill on the first floor, at (275,1377). Its first command is "Listen". */
    private static final int GRILL = 148;
    private static final int GRILL_X = 275;
    private static final int GRILL_Y = 1377;

    /** The hole on the top floor, at (278,2323), above the witch's cauldron. */
    private static final int HOLE = 154;
    private static final int HOLE_X = 278;
    private static final int HOLE_Y = 2323;

    private static final int COINS = 10;
    private static final int IRON_CHAIN_BODY = 7;
    private static final int CABBAGE = 18;
    private static final int MEDIUM_BRONZE_HELMET = 104;
    /** The Draynor Manor cabbage -- the one the witch actually wants. */
    private static final int DRAYNOR_CABBAGE = 228;

    private static final int QUEST_POINTS_NEEDED = 12;
    private static final int REWARD = 2500;

    public BlackKnightsFortress(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Black knight's fortress");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The black knights are up to no good. You are hired by the white knights to spy on them and uncover their evil scheme.");
        setStartPoint("White knight's castle, falador");
        setSpeakTo("Sir Amik Varze");
        setMissionLength("Medium");
        require("12 quest points");
        require("Able to defeat lvl-46 knights");
        rewardItem(COINS, REWARD);

        associateNpc(SIR_AMIK_VARZE);
        associateNpc(GUARD);
        associateObject(GRILL);
        associateObject(HOLE);
        associateDoor(ENTRANCE_DOOR);
        associateDoor(MEETING_DOOR_WEST);
        associateDoor(MEETING_DOOR_SOUTH);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Black knight's fortress quest");
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger == QuestTrigger.NPC_TALK && entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (npc.getID() == SIR_AMIK_VARZE) {
                talkToAmik(npc);
            } else if (npc.getID() == GUARD) {
                talkToGuard(npc);
            }
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 && object.getID() == ENTRANCE_DOOR) {
                enterFortress(object);
            } else if (trigger == QuestTrigger.DOOR_ACT2 && object.getID() == ENTRANCE_DOOR) {
                getOwner().getActionSender().sendMessage("The door is shut");
            } else if (trigger == QuestTrigger.DOOR_ACT1 && object.getID() == MEETING_DOOR_WEST) {
                meetingHallWestDoor(object);
            } else if (trigger == QuestTrigger.DOOR_ACT1 && object.getID() == MEETING_DOOR_SOUTH) {
                meetingHallSouthDoor(object);
            } else if (trigger == QuestTrigger.OBJECT_ACT1 && object.getID() == GRILL) {
                listenAtGrill(object);
            }
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject
                && ((GameObject) entity).getID() == HOLE) {
            dropDownHole((GameObject) entity, used);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // ------------------------------------------------------- sir amik varze --

    private void talkToAmik(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.player("Hello Sir Amik")
             .npc("Hello friend")
             .start();
            return;
        }

        if (getStage() == SABOTAGED) {
            c.player("I have ruined the black knight's invincibilty potion.")
             .player("That should put a stop to your problem.")
             .npc("Yes we have just received a message from the black knights.")
             .npc("Saying they withdraw their demands.")
             .npc("Which confirms your story")
             .player("You said you were going to pay me")
             .npc("Yes that's right")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(FINISHED);
                 }
             })
             .start();
            return;
        }

        if (questStarted()) {
            c.npc("How's the mission going?");
            if (getStage() >= HEARD_PLAN) {
                c.player("I've found out what the black knight's secret weapon is.")
                 .player("It's a potion of invincibility.")
                 .npc("That is bad news.");
            } else {
                c.player("I haven't managed to find what the secret weapon is yet.");
            }
            c.start();
            return;
        }

        c.npc("I am the leader of the white knights of Falador")
         .npc("Why do you seek my audience?")
         .options(new Choice("I seek a quest",
                             "I don't I'm just looking around") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Ok, don't break anything");
                     return;
                 }
                 if (c.getPlayer().getQuestPoints() < QUEST_POINTS_NEEDED) {
                     c.npc("Well i do have a task, but it is very dangerous")
                      .npc("and it's critical to us that no mistakes are made")
                      .npc("I couldn't possibly let an unexperienced quester like yourself go");
                     return;
                 }
                 c.npc("Well I need some spy work doing")
                  .npc("It's quite dangerous")
                  .npc("You will need to go into the Black Knight's fortress")
                  .options(new Choice("I laugh in the face of danger",
                                      "I go and cower in a corner at the first sign of danger") {
                      public void picked(int option, Conversation c) {
                          if (option == 0) {
                              c.npc("Well that's good")
                               .npc("Don't get too overconfident though");
                              theJob(c);
                              return;
                          }
                          c.npc("Err")
                           .npc("Well")
                           .npc("spy work does involve a little hiding in corners I suppose")
                           .options(new Choice("Oh I suppose I'll give it a go then",
                                               "No I'm not convinced") {
                               public void picked(int option, Conversation c) {
                                   if (option == 0) {
                                       theJob(c);
                                   }
                               }
                           });
                      }
                  });
             }
         });
        c.start();
    }

    private void theJob(Conversation c) {
        c.npc("You've come along just right actually")
         .npc("All of my knights are known to the black knights already")
         .npc("Subtlety isn't exactly our strong point")
         .player("So what needs doing?")
         .npc("Well the black knights have started making strange threats to us")
         .npc("Demanding large amounts of money and land")
         .npc("And threataning to invade Falador if we don't pay")
         .npc("Now normally this wouldn't be a problem")
         .npc("But they claim to have a powerful new secret weapon")
         .npc("What I want you to do is to get inside their fortress")
         .npc("Find out what their secret weapon is")
         .npc("And then sabotage it")
         .npc("You will be well paid")
         .player("OK I'll give it a try")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(STARTED);
             }
         });
    }

    // ------------------------------------------------------------- guards --

    /**
     * The fortress guards, of which there are three groups and two dialogues.
     *
     * The pair that roam (272..274, 437..442) stand outside the black knights'
     * meeting room on the ground floor, and are the ones who warn the player off
     * it. Everybody else -- the four on the entrance side, (266..270, 438..445),
     * and the pair on the first floor at (272..275, 1382..1385) -- gives the
     * "you can't come in here" challenge.
     *
     * The two ground-floor groups are told apart by x, since the meeting room is
     * east of the wall at 271 and the entrance is west of it. Testing y instead
     * would pick out the first floor, which is neither group.
     */
    private void talkToGuard(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (npc.getY() < 944 && npc.getX() >= 271) {
            c.npc("I wouldn't go in there if I woz you")
             .npc("Those black knights are in an important meeting")
             .npc("They said they'd kill anyone who went in there")
             .options(new Choice("Ok I won't", "I don't care I'm going in anyway") {
                 public void picked(int option, Conversation c) {
                 }
             });
            c.start();
            return;
        }

        c.npc("Heh you can't come in here")
         .npc("This is a high security military installation")
         .options(new Choice("Yes but I work here", "So who does it belong to?", "Oh sorry") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("This fortress belongs to the order of black knights known as the Kinshra");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Don't let it happen again");
                     return;
                 }
                 c.npc("Well this is the guards entrance")
                  .npc("And I might be new here")
                  .npc("But I can tell you're not a guard")
                  .npc("You're not even wearing proper guards uniform")
                  .options(new Choice("So what is this uniform?", "Pleaasse let me in") {
                      public void picked(int option, Conversation c) {
                          if (option == 0) {
                              c.npc("Well you can see me wearing it")
                               .npc("It's iron chain mail and a medium bronze helmet");
                              return;
                          }
                          c.npc("Go away, you're getting annoying");
                      }
                  });
             }
         });
        c.start();
    }

    // ----------------------------------------------------------- entrance --

    /** Worn, not carried -- the guards can see what the player has on. */
    private boolean inUniform() {
        return getOwner().getInventory().wielding(MEDIUM_BRONZE_HELMET)
            && getOwner().getInventory().wielding(IRON_CHAIN_BODY);
    }

    private void enterFortress(GameObject door) {
        Player p = getOwner();
        if (door.getX() != ENTRANCE_DOOR_X || door.getY() != ENTRANCE_DOOR_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!inUniform()) {
            /* Ours. The guard's own lines are on him, in talkToGuard(); a door
               that is simply refused needs to say why without starting a
               conversation with an npc who may not be standing there. */
            p.getActionSender().sendMessage("A guard blocks your way");
            p.getActionSender().sendMessage("You're not wearing proper guards uniform");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        // The door faces east-west, so it stands between x-1 and x.
        p.teleport(p.getX() < ENTRANCE_DOOR_X ? ENTRANCE_DOOR_X : ENTRANCE_DOOR_X - 1,
            ENTRANCE_DOOR_Y, false);
    }

    // ------------------------------------------------------ meeting hall --

    /** The nearest black knight still on his feet in the meeting hall. */
    private Npc hallKnight() {
        return world.getNpc(BLACK_KNIGHT, HALL_MIN_X, HALL_MAX_X, HALL_MIN_Y, HALL_MAX_Y);
    }

    /**
     * The west door. Opens both ways, but stepping into the hall through it
     * sets the nearest knight on the player -- they did say they'd kill anyone
     * who went in.
     */
    private void meetingHallWestDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != MEETING_DOOR_WEST_X || door.getY() != MEETING_DOOR_WEST_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        boolean entering = p.getX() < MEETING_DOOR_WEST_X;
        swingThrough(door);
        if (entering) {
            Npc knight = hallKnight();
            if (knight != null && !knight.isBusy()) {
                knight.attackPlayer(p);
            }
        }
    }

    /**
     * The south door, out the far side. Nobody strolls out of the meeting
     * quietly: if no knight is already on the player, one attacks instead of
     * the door opening, so the room is crossed fighting or at a run. Coming in
     * from the south side is just a door.
     */
    private void meetingHallSouthDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != MEETING_DOOR_SOUTH_X || door.getY() != MEETING_DOOR_SOUTH_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        boolean leaving = p.getY() < MEETING_DOOR_SOUTH_Y;
        if (leaving && !p.inCombat()) {
            Npc knight = hallKnight();
            if (knight != null && !knight.isBusy()) {
                knight.attackPlayer(p);
                return;
            }
        }
        swingThrough(door);
    }

    /**
     * Swing a hall door open and step through, on either axis: direction 0
     * stands between (x,y) and (x,y-1), direction 1 between (x,y) and (x-1,y).
     */
    private void swingThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        if (door.getDirection() == 0) {
            p.teleport(door.getX(),
                p.getY() < door.getY() ? door.getY() : door.getY() - 1, false);
        } else {
            p.teleport(p.getX() < door.getX() ? door.getX() : door.getX() - 1,
                door.getY(), false);
        }
    }

    // -------------------------------------------------------------- grill --

    /**
     * Listening at the grill.
     *
     * The three of them are behind a sealed wall and only Greldo is actually
     * spawned in there, so the scene is played into the chat box with the
     * speakers named, in chat-yellow as the real client showed it, rather than
     * as speech bubbles over npcs the player cannot see.
     */
    private void listenAtGrill(GameObject grill) {
        Player p = getOwner();
        if (grill.getX() != GRILL_X || grill.getY() != GRILL_Y
                || !questStarted() || getStage() >= HEARD_PLAN) {
            p.getActionSender().sendMessage("I can't hear much right now");
            return;
        }
        new Conversation(p, null)
            .message("@yel@Black Knight: So how's the secret weapon coming along?")
            .message("@yel@Witch: The invincibility potion is almost ready")
            .message("@yel@Witch: It's taken me five years but it's almost ready")
            .message("@yel@Witch: Greldo the Goblin here")
            .message("@yel@Witch: Is just going to fetch the last ingredient for me")
            .message("@yel@Witch: It's a specially grown cabbage")
            .message("@yel@Witch: Grown by my cousin Helda who lives in Draynor Manor")
            .message("@yel@Witch: The soil there is slightly magical")
            .message("@yel@Witch: And it gives the cabbages slight magic properties")
            .message("@yel@Witch: Not to mention the trees")
            .message("@yel@Witch: Now remember Greldo only a Draynor Manor cabbage will do")
            .message("@yel@Witch: Don't get lazy and bring any old cabbage")
            .message("@yel@Witch: That would entirely wreck the potion")
            .message("@yel@Greldo: Yeth Mithreth")
            .then(new Effect() {
                public void run(Conversation c) {
                    setStage(HEARD_PLAN);
                }
            })
            .start();
    }

    // --------------------------------------------------------------- hole --

    private void dropDownHole(GameObject hole, InvItem used) {
        Player p = getOwner();
        if (hole.getX() != HOLE_X || hole.getY() != HOLE_Y || used == null
                || (used.getID() != CABBAGE && used.getID() != DRAYNOR_CABBAGE)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!questStarted() || completed()) {
            sayMessage("Why would I want to do that?");
            return;
        }
        if (used.getID() == DRAYNOR_CABBAGE) {
            p.getActionSender().sendMessage("This is the wrong sort of cabbage!");
            p.getActionSender().sendMessage("You are meant to be hindering the witch.");
            p.getActionSender().sendMessage("Not helping her.");
            return;
        }
        p.getInventory().remove(CABBAGE, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You drop a cabbage down the hole.");
        p.getActionSender().sendMessage("The cabbage lands in the cauldron below.");
        p.getActionSender().sendMessage("The mixture in the cauldron starts to froth and bubble.");
        p.getActionSender().sendMessage("You hear the witch groan in dismay.");
        if (getStage() < SABOTAGED) {
            setStage(SABOTAGED);
        }
    }
}
