import java.util.List;

import org.rscdaemon.server.model.Entity;
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
 * Goblin diplomacy.
 *
 * The two generals of Goblin Village cannot agree whether their new armour
 * should be green or red. Offer to pick for them and they ask for orange, reject
 * it, ask for dark blue, reject that too, and settle on the light blue they were
 * wearing all along. Five quest points -- the most any free quest gives.
 *
 * It starts in Port Sarim, not Goblin Village: the bartender of The Rusty Anchor
 * mentions the argument, and until he has, the generals only tell the player to
 * go away. That is why the bartender is part of this quest and why HEARD_RUMOUR
 * exists as a stage of its own.
 *
 * The three armours are all called "Goblin Armour" and differ only by colour in
 * the definitions: 273 is cyan (what goblins drop and wear), 274 is orange and
 * 275 is dark blue. Dyeing is not done here -- it is ordinary item-on-item
 * crafting, and lives in InvUseOnItem alongside the rest.
 *
 * The dye chain runs through two npcs this quest deliberately does not claim.
 * Aggie sells the red, yellow and blue dye and Wyson sells the woad leaves blue
 * dye needs; both are also wanted by other quests, so both are NpcHandlers
 * instead. See npchandler/Aggie.java.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class GoblinDiplomacy extends Quest {

    public final static int UID = Quests.GOBLIN_DIPLOMACY;

    /** The bartender has mentioned the argument. */
    private static final int HEARD_RUMOUR = 1;
    private static final int NEED_ORANGE = 2;
    private static final int NEED_DARK_BLUE = 3;
    private static final int NEED_LIGHT_BLUE = 4;
    private static final int FINISHED = 5;

    private static final int BARTENDER = 150;
    private static final int WARTFACE = 151;
    private static final int BENTNOZE = 152;

    private static final int ARMOUR_LIGHT_BLUE = 273;
    private static final int ARMOUR_ORANGE = 274;
    private static final int ARMOUR_DARK_BLUE = 275;

    private static final int COINS = 10;
    private static final int BEER = 193;
    private static final int BEER_PRICE = 2;
    private static final int GOLD_BAR = 172;

    private static final int CRAFTING = 12; /* skill index */

    public GoblinDiplomacy(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Goblin diplomacy");
        setFinalStage(FINISHED);
        associateNpc(BARTENDER);
        associateNpc(WARTFACE);
        associateNpc(BENTNOZE);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("There's a disturbance in the goblin village. Help the goblins solve their dispute so the world doesn't have to worry about rioting goblins.");
        setStartPoint("Port Sarim");
        setSpeakTo("Barman");
        setMissionLength("Medium");
        rewardItem(GOLD_BAR, 1);
        rewardExp(CRAFTING, 125, 15);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Goblin diplomacy quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() == BARTENDER) {
            talkToBartender(npc);
        } else if (npc.getID() == WARTFACE || npc.getID() == BENTNOZE) {
            talkToGenerals(npc);
        }
    }

    /**
     * The bartender is npchandler/Bartenders.java's, not this quest's --
     * NpcHandlers.xml keeps id 150 there because that handler is also one of
     * Alfred Grimhand's six barcrawl bars, which this quest does not
     * reimplement. talkToBartender() above therefore never runs; instead
     * Bartenders.rustyAnchorPicked reports the rumour here by name, the same
     * way GodCharges and Tutorial Island hear about events that happen inside
     * someone else's npc handler.
     */
    public void note(String key) {
        if ("heard-rumour".equals(key) && !questStarted()) {
            setStage(HEARD_RUMOUR);
        }
    }

    /**
     * The general standing next to the one being spoken to.
     *
     * They bicker with each other throughout, so half the lines belong to the
     * other one. If he has wandered off or been killed his lines fall back to the
     * general in front of the player, which reads oddly but never breaks.
     */
    private Npc otherGeneral(Npc npc) {
        int wanted = npc.getID() == WARTFACE ? BENTNOZE : WARTFACE;
        List<Npc> inView = getOwner().getViewArea().getNpcsInView();
        for (Npc n : inView) {
            if (n.getID() == wanted) {
                return n;
            }
        }
        return npc;
    }

    private boolean has(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    // ---------------------------------------------------------- bartender --

    private void talkToBartender(Npc npc) {
        final boolean afterwards = completed();
        Conversation c = new Conversation(getOwner(), npc);
        c.options(new Choice("Could i buy a beer please?",
                             afterwards ? "Have you heard any more rumours in here?"
                                        : "Not very busy in here today is it") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Sure that will be 2 gold coins please")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             Player p = c.getPlayer();
                             if (p.getInventory().countId(COINS) < BEER_PRICE) {
                                 p.getActionSender().sendMessage("You don't have enough coins.");
                                 c.stop();
                                 return;
                             }
                             p.getInventory().remove(COINS, BEER_PRICE);
                             p.getInventory().add(new InvItem(BEER, 1));
                             p.getActionSender().sendInventory();
                         }
                     })
                     .player("Ok here you go thanks");
                    return;
                }
                if (afterwards) {
                    c.npc("No it hasn't been very busy lately");
                    return;
                }
                c.npc("No it was earlier")
                 .npc("There was a guy in here saying the goblins up by the mountain are arguing again")
                 .npc("Of all things about the colour of their armour.")
                 .npc("Knowing the goblins, it could easily turn into a full blown war")
                 .npc("Which wouldn't be good")
                 .npc("Goblin wars make such a mess of the countryside")
                 .player("Well if I have time I'll see if I can go and knock some sense into them")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         if (!questStarted()) {
                             setStage(HEARD_RUMOUR);
                         }
                     }
                 });
            }
        });
        c.start();
    }

    // ----------------------------------------------------------- generals --

    private void talkToGenerals(Npc npc) {
        final Npc other = otherGeneral(npc);
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Now you've solved our argument we gotta think of something else to do")
             .npc(other, "Yep, we bored now")
             .start();
            return;
        }

        // They are always mid-argument when the player walks up. Wartface (green)
        // and Bentnoze (red) each argue their own colour, whichever one the
        // player actually approached -- not whichever happened to speak first.
        if (npc.getID() == WARTFACE) {
            c.npc("green armour best")
             .npc(other, "No no Red every time");
        } else {
            c.npc("Red armour best")
             .npc(other, "No no green every time");
        }
        c.npc("go away human, we busy");

        if (!questStarted()) {
            // Nothing more until the bartender has mentioned it -- the generals
            // simply talk past anyone who has not heard the rumour.
            c.start();
            return;
        }

        switch (getStage()) {
            case HEARD_RUMOUR:
                c.options(new Choice("Do you want me to pick an armour colour for you?",
                                     "Why are you arguing about the colour of your armour?",
                                     "Wouldn't you prefer peace?") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("We decide to celebrate goblin new century")
                             .npc("By changing the colour of our armour")
                             .npc("Light blue get boring after a bit")
                             .npc("And we want change")
                             .npc("Problem is they want different change to us");
                            return;
                        }
                        if (option == 2) {
                            c.npc("Yeah peace is good as long as it is peace wearing Green armour")
                             .npc(other, "But green to much like skin!")
                             .npc(other, "Nearly make you look naked!");
                            return;
                        }
                        c.player("different to either green or red")
                         .npc("Hmm me dunno what that'd look like")
                         .npc("You'd have to bring me some, so us could decide")
                         .npc(other, "Yep bring us orange armour")
                         .npc("Yep orange might be good")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 setStage(NEED_ORANGE);
                             }
                         });
                    }
                });
                break;

            case NEED_ORANGE:
                c.npc("Oh it you");
                if (!has(ARMOUR_ORANGE)) {
                    c.npc("Have you got some orange goblin armour yet?")
                     .player("Err no")
                     .npc("Come back when you have some");
                    break;
                }
                c.player("I have some orange armour")
                 .then(take(ARMOUR_ORANGE))
                 .npc("No I don't like that much")
                 .npc(other, "It clashes with my skin colour")
                 .npc("Try bringing us dark blue armour")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(NEED_DARK_BLUE);
                     }
                 });
                break;

            case NEED_DARK_BLUE:
                c.npc("Oh it you");
                if (!has(ARMOUR_DARK_BLUE)) {
                    c.npc("Have you got some Dark Blue goblin armour yet?")
                     .player("Err no")
                     .npc("Come back when you have some");
                    break;
                }
                c.player("I have some dark blue armour")
                 .then(take(ARMOUR_DARK_BLUE))
                 .npc("Doesn't seem quite right")
                 .npc(other, "maybe if it was a bit lighter")
                 .npc("Yeah try light blue")
                 .player("I thought that was the armour you were changing from")
                 .player("But never mind, anything is worth a try")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(NEED_LIGHT_BLUE);
                     }
                 });
                break;

            default:
                if (!has(ARMOUR_LIGHT_BLUE)) {
                    c.npc("Have you got some Light Blue goblin armour yet?")
                     .player("Err no")
                     .npc("Come back when you have some");
                    break;
                }
                c.player("Ok I've got light blue armour")
                 .then(take(ARMOUR_LIGHT_BLUE))
                 .npc("That is rather nice")
                 .npc(other, "Yes I could see myself wearing somethin' like that")
                 .npc("It' a deal then")
                 .npc("Light blue it is")
                 .npc("Thank you for sorting our argument")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(getFinalStage());
                     }
                 });
                break;
        }
        c.start();
    }

    private Effect take(final int id) {
        return new Effect() {
            public void run(Conversation c) {
                c.getPlayer().getInventory().remove(id, 1);
                c.getPlayer().getActionSender().sendInventory();
            }
        };
    }
}
