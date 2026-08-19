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
 * Druidic ritual. Released 27 February 2002, alongside Witch's house.
 *
 * The dark wizards took the druids' stone circle south of Varrock, and putting
 * that right starts with a sacrifice to Guthix: raw meat from four animals,
 * each dipped in the Cauldron of Thunder in the halls under Taverley. Kaqemeex
 * at the stone circle sends you to Sanfew in the village; Sanfew names the
 * meats; you come back with them enchanted; Kaqemeex teaches you herblaw.
 *
 * The reward is not experience. It is the herblaw skill itself, which nobody
 * can use until this quest is done -- so this quest is also why
 * InvActionHandler and InvUseOnItem now ask QuestManager whether it is
 * finished before letting a herb be cleaned or a potion mixed.
 *
 * Ids:
 *
 *     Kaqemeex           npc 204, stone circle, (362,459)
 *     Sanfew             npc 205, Taverley,     (379,487)
 *     Cauldron of Thunder scenery 236, (369,3332) -- the only one in the world
 *     raw    503 rat 133 chicken 502 bear 504 beef
 *     enchanted 506 rat 508 chicken 505 bear 507 beef
 *
 * The two Suits of armour (npc 206) flanking the west door are this quest's
 * doormen: trying that door from outside wakes one while either stands, and
 * both must be killed to come in that way -- see guardedDoor(). Their defs are
 * neither attackable nor aggressive, which is also why the four suits on top
 * of Ardougne castle are scenery with a yellow dot, exactly as the real game
 * had them.
 *
 * One thing the real quest had that is not here, and is not invented around:
 * the dark wizards' circle south of Varrock is never actually purified --
 * Jagex never wrote that part, the quest simply ends with Kaqemeex's lesson.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class DruidicRitual extends Quest {

    public final static int UID = Quests.DRUIDIC_RITUAL;

    /** Kaqemeex has sent you to Sanfew. */
    private static final int STARTED = 1;
    /** Sanfew has named the four meats. */
    private static final int TOLD = 2;
    /** Sanfew has the enchanted meat; go back to Kaqemeex. */
    private static final int MEAT_GIVEN = 3;
    private static final int FINISHED = 4;

    private static final int KAQEMEEX = 204;
    private static final int SANFEW = 205;
    private static final int CAULDRON = 236;
    private static final int CAULDRON_X = 369, CAULDRON_Y = 3332;

    /**
     * The two Suits of armour flanking the cauldron room's west door, and the
     * door itself. The suits stand at (374,3331) and (374,3333); the door is
     * between them at (374,3332), crossing x 373/374. Trying it from outside
     * while either suit stands wakes one -- both must be down to walk in this
     * way. From inside it is just a door, and the east and south doors (64)
     * are never guarded.
     */
    private static final int SUIT_OF_ARMOUR = 206;
    private static final int GUARDED_DOOR = 63;
    private static final int DOOR_X = 374, DOOR_Y = 3332;

    /** Raw and enchanted, index for index. */
    private static final int[] RAW = { 503, 133, 502, 504 };
    private static final int[] ENCHANTED = { 506, 508, 505, 507 };

    private static final int HERBLAW = 15; /* skill index */
    private static final int REWARD_XP = 250;

    public DruidicRitual(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Druidic ritual");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("To start using the herblaw skill you will need to ask for some training from the druids. However they require some help with a ritual before they will help you.");
        setStartPoint("Stone circle north of Taverley");
        setSpeakTo("Kaqemeex");
        setMissionLength("Medium");
        rewardExp(HERBLAW, REWARD_XP, 0);
        rewardOther("Use of the herblaw skill");

        associateNpc(KAQEMEEX);
        associateNpc(SANFEW);
        associateObject(CAULDRON);
        associateDoor(GUARDED_DOOR, DOOR_X, DOOR_Y);
    }

    public void completeQuest() {
        grantRewards();
        Player p = getOwner();
        p.getActionSender().sendMessage("Well done.You have completed the Druidic ritual quest");
        p.getActionSender().sendMessage("@gre@You can now use the herblaw skill");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger == QuestTrigger.NPC_TALK && entity instanceof Npc) {
            if (((Npc) entity).getID() == KAQEMEEX) {
                talkToKaqemeex((Npc) entity);
            } else {
                talkToSanfew((Npc) entity);
            }
        } else if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (object.getID() == GUARDED_DOOR) {
                guardedDoor(object);
            } else {
                useCauldron(object, null);
            }
        }
    }

    /**
     * The west door into the cauldron room. Trying it from outside while
     * either Suit of armour still stands wakes one of them instead of opening
     * -- kill it, try again, and the second wakes; only with both down does
     * the door open. From inside it always opens: the suits guard the way in,
     * not the way out. The line and the one-at-a-time behaviour are the
     * recorded originals.
     */
    private void guardedDoor(GameObject door) {
        Player p = getOwner();
        Npc suit = world.getNpc(SUIT_OF_ARMOUR, DOOR_X, DOOR_X, DOOR_Y - 2, DOOR_Y + 2);
        if (suit != null && p.getX() > DOOR_X - 1) {
            p.getActionSender().sendMessage("Suddenly the suit of armour comes to life!");
            suit.attackPlayer(p);
            return;
        }
        p.getActionSender().sendSound("opendoor");
        p.teleport(p.getX() >= DOOR_X ? DOOR_X - 1 : DOOR_X, DOOR_Y, false);
    }

    /*
     * QuestManager always calls this three-argument form, whatever the
     * trigger, so anything it does not recognise must fall through to the
     * one above -- without that fall-through Kaqemeex and Sanfew never
     * answered a click, which they didn't.
     */
    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject) {
            useCauldron((GameObject) entity, used);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // -------------------------------------------------------- Kaqemeex --

    private void talkToKaqemeex(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Hello how is the herblaw going?")
                .options(new Choice("Very well thankyou", "I need more practice at it") {
                    public void picked(int option, Conversation c) { }
                })
                .start();
            return;
        }
        if (getStage() == MEAT_GIVEN) {
            new Conversation(p, npc)
                .npc("I've heard you were very helpful to Sanfew")
                .npc("I will teach you the herblaw you need to know now")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(getFinalStage());
                    }
                })
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("Hello again")
                .npc("You need to speak to Sanfew in the village south of here")
                .npc("To continue with your quest")
                .start();
            return;
        }
        offerQuest(npc);
    }

    /**
     * Kaqemeex's opening menu. Three of its four branches loop back to it --
     * "above" and "below" in the transcript -- which the Conversation builder
     * does by having picked() build the menu again, so the same code is the
     * branch and the return to it.
     */
    private void offerQuest(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("What brings you to our holy Monument");
        firstMenu(c);
        c.start();
    }

    private void firstMenu(Conversation c) {
        c.options(new Choice("Who are you?",
                             "I'm in search of a quest",
                             "Did you build this?",
                             "Well I'll be on my way now") {
            public void picked(int option, Conversation c) {
                switch (option) {
                case 0:
                    c.npc("We are the druids of Guthix")
                     .npc("We worship our God at our famous stone circles")
                     .options(new Choice("What about the stone circle full of dark wizards?",
                                         "So whats so good about Guthix",
                                         "Well I'll be on my way now") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 corrupted(c);
                                 acceptMenu(c);
                             } else if (option == 1) {
                                 c.npc("Guthix is very important to this world")
                                  .npc("He is the God of nature and balance")
                                  .npc("He is in the trees and he is in the rock");
                             } else {
                                 c.npc("good bye");
                             }
                         }
                     }.says(1, "So what's so good abou Guthix?"));
                    break;
                case 1:
                    c.npc("I think I may have a worthwhile quest for you actually")
                     .npc("I don't know if you are familair withe the stone circle south of Varrock");
                    corrupted(c);
                    acceptMenu(c);
                    break;
                case 2:
                    c.npc("Well I didn't build it personally")
                     .npc("Our forebearers did")
                     .npc("The first druids of Guthix built many stone circles 800 years ago")
                     .npc("Only 2 that we know of remain")
                     .npc("And this is the only 1 we can use any more");
                    firstMenu(c);
                    break;
                default:
                    c.npc("good bye");
                }
            }
        });
    }

    private void corrupted(Conversation c) {
        c.npc("That used to be our stone circle")
         .npc("Unfortunatley many years ago dark wizards cast a wicked spell on it")
         .npc("Corrupting it for their own evil purposes")
         .npc("and making it useless for us")
         .npc("We need someone who will go on a quest for us")
         .npc("to help us purify the circle of Varrock");
    }

    private void acceptMenu(Conversation c) {
        c.options(new Choice("Ok I will try and help",
                             "No that doesn't sound very interesting",
                             "So is there anything in this for me?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Ok go and speak to our Elder druid, Sanfew")
                     .npc("He lives in our village to the south of here")
                     .npc("He knows better what we need than I")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(STARTED);
                         }
                     });
                } else if (option == 1) {
                    c.npc("Well suit yourself, we'll have to find someone else");
                } else {
                    c.npc("We are skilled in the art of herblaw")
                     .npc("We can teach you some of our skill if you complete your quest");
                    acceptMenu(c);
                }
            }
        });
    }

    // ----------------------------------------------------------- Sanfew --

    private void talkToSanfew(Npc npc) {
        Player p = getOwner();
        if (getStage() >= MEAT_GIVEN) {
            new Conversation(p, npc)
                .npc("What can I do for you young 'un?")
                .options(new Choice("Is there anything else I can help with?",
                                    "Actually I don't need to speak to you") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Not at the moment")
                             .npc("I need to make some more preparations myself now");
                        }
                    }
                })
                .start();
            return;
        }
        if (getStage() == TOLD) {
            handIn(npc);
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .npc("What can I do for you young 'un?")
                .options(new Choice("I've been sent to help purify the varrock stone circle",
                                    "Actually I don't need to speak to you") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            return;
                        }
                        c.npc("Well what I'm struggling with")
                         .npc("Is the meats I needed for the sacrifice to Guthix")
                         .npc("I need the raw meat from 4 different animals")
                         .npc("Which all need to be dipped in the cauldron of thunder")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 setStage(TOLD);
                             }
                         })
                         .options(new Choice("Where can I find this cauldron",
                                             "Ok I'll do that then") {
                             public void picked(int option, Conversation c) {
                                 if (option == 0) {
                                     c.npc("It is in the mysterious underground halls")
                                      .npc("which are somewhere in the woods to the south of here");
                                 }
                             }
                         });
                    }
                })
                .start();
            return;
        }
        /* Not sent by Kaqemeex: he sends you straight back to him. */
        new Conversation(p, npc)
            .npc("What can I do for you young 'un?")
            .options(new Choice("I've heard you druids might be able to teach me herblaw",
                                "Actually I don't need to speak to you") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("You should go to speak to kaqemeex")
                     .npc("He is probably our best teacher of herblaw at the moment")
                     .npc("I believe he is at our stone circle to the north of here");
                }
            })
            .start();
    }

    private void handIn(Npc npc) {
        Player p = getOwner();
        boolean got = true;
        for (int i = 0; i < ENCHANTED.length; i++) {
            if (p.getInventory().countId(ENCHANTED[i]) <= 0) {
                got = false;
            }
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Have you got what I need yet?");
        if (!got) {
            c.player("no not yet")
             .options(new Choice("What was I meant to be doing again?", "I'll get on with it") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("I need the raw meat from 4 different animals")
                          .npc("Which all need to be dipped in the cauldron of thunder");
                     }
                 }
             });
        } else {
            c.player("Yes I have everything")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     for (int i = 0; i < ENCHANTED.length; i++) {
                         p.getInventory().remove(ENCHANTED[i], 1);
                     }
                     p.getActionSender().sendInventory();
                     setStage(MEAT_GIVEN);
                 }
             })
             .npc("thank you, that has brought us much closer to reclaiming our stone circle")
             .npc("Now go and talk to kaqemeex")
             .npc("He will show you what you need to know about herblaw");
        }
        c.start();
    }

    // -------------------------------------------------------- cauldron --

    /**
     * The Cauldron of Thunder. Claiming the scenery takes every click on it,
     * so this has to answer for a bare look as well as for a meat dipped in
     * it, and for players who have never met a druid.
     */
    private void useCauldron(GameObject cauldron, InvItem used) {
        Player p = getOwner();
        if (cauldron.getX() != CAULDRON_X || cauldron.getY() != CAULDRON_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (used == null) {
            p.getActionSender().sendMessage("You look into the cauldron");
            p.getActionSender().sendMessage("Thunder rolls somewhere a long way below you");
            return;
        }
        for (int i = 0; i < RAW.length; i++) {
            if (used.getID() != RAW[i]) {
                continue;
            }
            if (!questStarted()) {
                p.getActionSender().sendMessage("You dip the meat in the cauldron");
                p.getActionSender().sendMessage("Nothing happens");
                return;
            }
            p.getInventory().remove(RAW[i], 1);
            p.getInventory().add(new InvItem(ENCHANTED[i], 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You dip the meat in the cauldron");
            p.getActionSender().sendMessage("The meat is enchanted by the cauldron of thunder");
            return;
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }
}
