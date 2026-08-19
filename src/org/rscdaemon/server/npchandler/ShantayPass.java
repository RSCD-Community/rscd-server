package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;

/**
 * The Shantay Pass -- the gate in the wall south of Al-Kharid, and the four
 * npcs who work it.
 *
 * Everything but the shop and the gate itself was missing. Shantay (549) was
 * registered to ShopKeeper, so the only thing he could do was sell; his two
 * assistants (720) and the four guards (717 roaming, 719 standing on the gate)
 * were spawned with nothing wired to them at all, and the gate let anybody
 * holding a pass walk south without ever taking it. This is the rest of it:
 *
 *   - the guard on the gate, who reads you the desert poster, takes your pass
 *     and hands you the disclaimer that says none of it is Shantay's fault;
 *   - the roaming guards, who are too hot and too busy to talk to you;
 *   - Shantay and his assistants, who explain the place, sell the kit for it,
 *     and have you thrown in the cell if you tell them you are an outlaw;
 *   - that cell, the five gold piece fine, and the alternative -- a jail in
 *     Port Sarim.
 *
 * Dialogue is Jagex's, from the classic.runescape.wiki transcript pages and
 * 2018 replay evidence. Where a line here is ours rather than recovered it
 * says so on the line.
 *
 * Known gaps, attested but unbuilt: the assistants' scolding of a player who
 * took the Port Sarim option and came back ("You should be in jail!") needs a
 * per-player flag this server has nowhere to keep, and the guards' "Right,
 * time for dinner!" despawn line needs a spawn schedule npcs do not have.
 *
 * The gate and the cell door live where the rest of their kind live -- see
 * ObjectAction.handleShantayGate and WallObjectAction's case 176 -- and call
 * into this class for the parts that are dialogue.
 */
public class ShantayPass implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int SHANTAY = 549;
    private static final int ASSISTANT = 720;
    private static final int GUARD_ROAMING = 717;
    private static final int GUARD_STANDING = 719;

    public static final int PASS = 1030;
    public static final int DISCLAIMER = 1099;
    /** The kebab recipe Shantay drops out of his pocket. */
    private static final int RECIPE = 1120;
    private static final int COINS = 10;

    /** What the pass costs, and what the fine costs. Jagex charged both. */
    private static final int FINE = 5;

    /**
     * The cell is a two-by-four room east of the pass buildings, walled on
     * every side in the landscape with one way in: door 176, the only Jail
     * Door in the game, on its west wall at (66,729). (67,729) is where the
     * guards put you, (65,729) is the tile you are let out onto.
     */
    public static final int CELL_MIN_X = 66, CELL_MAX_X = 67;
    public static final int CELL_MIN_Y = 727, CELL_MAX_Y = 730;
    public static final int CELL_X = 67, CELL_Y = 729;
    public static final int CELL_OUT_X = 65, CELL_OUT_Y = 729;

    /**
     * The Port Sarim jail, which is a real cell on our map -- (281,665) is
     * inside it and its door is an ordinary one, so being sent there is a long
     * walk home rather than a place a player can be stranded.
     */
    private static final int PORT_SARIM_X = 281, PORT_SARIM_Y = 665;

    /** Where the gate drops you on the desert side. */
    public static final int THROUGH_X = 62, THROUGH_Y = 734;

    public void handleNpc(final Npc npc, Player player) throws Exception {
        if (npc.getID() == GUARD_STANDING) {
            standingGuard(npc, player);
            return;
        }
        if (npc.getID() == GUARD_ROAMING) {
            roamingGuard(npc, player);
            return;
        }
        passOffice(npc, player);
    }

    // ------------------------------------------------------------- guards --

    /**
     * The guard standing on the gate. He is the gate: asking him to be let
     * into the desert runs exactly the crossing that clicking the gate runs.
     */
    private void standingGuard(final Npc npc, Player player) {
        Conversation c = new Conversation(player, npc)
            .npc("Hello there!")
            .npc("What can I do for you?");
        c.options(new Choice("I'd like to go into the desert please.", "Nothing thanks.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Ok then, have a nice day.");
                    return;
                }
                c.npc("Of course!");
                cross(c);
            }
        });
        c.start();
    }

    /**
     * A guard on his rounds. He will not talk to you and says why, and the two
     * lines after it explain the mood rather than the man.
     */
    private void roamingGuard(final Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Go talk to Shantay or one of his assistants.")
            .npc("I'm on duty and I don't have time to talk to the likes of you!")
            .message("The guard seems quite bad tempered,")
            .message("probably from having to wear heavy armour in this intense heat.")
            .start();
    }

    // --------------------------------------------------- Shantay and staff --

    /** Shantay himself, or one of his two assistants. */
    private void passOffice(final Npc npc, Player player) {
        final boolean isShantay = npc.getID() == SHANTAY;

        if (isShantay) {
            /*
             * One talk in twenty-five, the kebab recipe falls out of his
             * pocket. It is the only source of the Scrumpled piece of paper in
             * the game. It lands on the floor where he is standing, as a drop
             * to the player who was talking to him.
             */
            if (DataConversions.random(0, 24) == 0) {
                world.registerItem(new Item(RECIPE, npc.getX(), npc.getY(), 1, player));
            }
        }

        Conversation c = new Conversation(player, npc);
        c.npc(isShantay ? "Hello Effendi, I am Shantay."
                        : "Hello Effendi, I am a Shantay Pass Assistant.");
        if (player.getInventory().countId(DISCLAIMER) <= 0) {
            c.npc("I see you're new!");
            c.npc("Make sure you read the poster before going into the desert.");
        }
        if (isShantay && !player.getQuestManager().completed(Quests.TOURIST_TRAP)
                && player.getQuestManager().stageOf(Quests.TOURIST_TRAP) <= 0) {
            /* The hook into Tourist trap: Irena is the woman who starts it,
               and she stands just the other side of the gate. Said only until
               the player has gone and spoken to her. */
            c.npc("There is a heartbroken Mother just past the gates and in the Desert.");
            c.npc("Her name is Irena and she mourns her lost Daughter. Such a shame.");
        }

        c.options(new Choice("What is this place?",
                             "Can I see what you have to sell please?",
                             "I must be going.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Absolutely Effendi!");
                    openShop(c, npc);
                    return;
                }
                if (option == 2) {
                    c.npc("So long...");
                    return;
                }
                explainPass(c, npc, isShantay);
            }
        });
        c.start();
    }

    /**
     * "What is this place?" -- and the question back, which is the one that
     * decides whether you are sold a waterskin or thrown in a cell.
     */
    private void explainPass(Conversation c, final Npc npc, final boolean isShantay) {
        c.npc("This is the pass of Shantay.");
        if (isShantay) {
            c.npc("I guard this area with my men.");
            c.npc("I am responsible for keeping this pass open and repaired.");
            c.npc("My men and I prevent outlaws from getting out of the desert.");
            c.npc("And we stop the inexperienced from a dry death in the sands.");
        } else {
            c.npc("Mr Shantay guards this area with his men.");
            c.npc("He is responsible for keeping this pass open and repaired.");
            c.npc("He and his men prevent outlaws from getting out of the desert.");
            c.npc("And he stops the inexperienced from a dry death in the sands.");
        }
        c.npc("Which would you say you were?");
        c.options(new Choice("I am definitely an outlaw, prepare to die!",
                             "I am a little inexperienced.",
                             "Er, neither, I'm an adventurer.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    arrest(c, npc, isShantay);
                    return;
                }
                if (option == 1) {
                    c.npc("Can I recommend that you purchase a full waterskin and a knife!");
                    c.npc("These items will no doubt save your life...");
                    c.npc("A waterskin will keep water from evaporating in the desert.");
                    c.npc("And a keen woodsman with a knife can extract the juice from a cactus.");
                    c.npc("Before you go into the desert, it's advisable to wear desert clothes.");
                    c.npc("It's very hot in the desert and you'll surely cook if you wear armour.");
                    c.npc("To keep the pass open and bandit free, we charge a small toll of five gold pieces.");
                    c.npc("You can buy a desert pass from me, just ask me to open the shop.");
                    c.npc("You can also use our free banking services by clicking on the chest.");
                    shopOrLeave(c, npc);
                    return;
                }
                c.npc("Great, I have just the thing for the desert adventurer.");
                c.npc("I sell desert clothes which will keep you cool in the heat of the desert.");
                c.npc("I also sell waterskins so that you won't die in the desert.");
                c.npc("A waterskin and a knife help you survive from the juice of a cactus.");
                c.npc("Use the chest to store your items, we'll take them to the bank.");
                c.npc("It's hot in the desert, you'll bake in all that armour.");
                c.npc("To keep the pass open we ask for 5 gold pieces.");
                c.npc("and we give you a Shantay Pass, just ask to see what I sell to buy one.");
                c.options(new Choice("Can I see what you have to sell please?",
                                     "I must be going.",
                                     "Why do I have to pay to go into the desert?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Absolutely Effendi!");
                            openShop(c, npc);
                            return;
                        }
                        if (option == 1) {
                            c.npc("So long...");
                            return;
                        }
                        if (isShantay) {
                            c.message("Shantay opens his arms wide as if too embrace you.");
                            c.npc("Effendi, you insult me!");
                            c.npc("I am not interested in making a profit from you!");
                        } else {
                            c.message("The Assistant opens his arms wide as if too embrace you.");
                            c.npc("Effendi, you insult me!");
                            c.npc("We are not interested in making a profit from you!");
                        }
                        c.npc("I merely seek to cover my expenses in keeping this pass open.");
                        c.npc("There is repair work to carry out and also the mens wages to consider.");
                        c.npc("For the paltry sum of 5 Gold pieces, I think we offer a great service.");
                        shopOrLeave(c, npc);
                    }
                });
            }
        });
    }

    /** The two-option ending both of the long sales pitches come to. */
    private void shopOrLeave(Conversation c, final Npc npc) {
        c.options(new Choice("Can I see what you have to sell please?", "I must be going.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Absolutely Effendi!");
                    openShop(c, npc);
                    return;
                }
                c.npc("So long...");
            }
        });
    }

    /**
     * Hand the player over to the shop screen. Same as SiegfriedErkle: the
     * conversation holds the player busy and has to be closed first, and the
     * stock is in Shops.xml against this corner of the map like every other
     * shop's.
     */
    private void openShop(Conversation c, final Npc npc) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                c.stop();
                Shop shop = world.getShop(npc);
                if (shop == null) {
                    return;
                }
                p.setAccessingShop(shop);
                p.getActionSender().showShop(shop);
            }
        });
    }

    // --------------------------------------------------------------- jail --

    /**
     * Claim to be an outlaw and the guards take you at your word.
     *
     * Shantay is not attackable in the npc definitions -- his own line about
     * outlaws is the only way into the cell, so there is no attack path to
     * catch here.
     */
    private void arrest(Conversation c, final Npc npc, final boolean isShantay) {
        c.npc("Ha, very funny.....");
        c.npc("Guards arrest him!");
        c.message("The guards arrest you and place you in the jail.");
        /* Deliberate fix, not recovered behaviour: the wiki's transcript
           notes that in 2018 the arrest message fired but the player was
           never physically moved, and that the fine was never actually
           collected even when the money was withdrawn. Both were plainly
           broken -- an arrest that moves nobody and a fine nobody pays --
           so this build moves the player and takes the coins. */
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(CELL_X, CELL_Y, false);
            }
        });
        c.npc("You'll have to stay in there until you pay the fine of five gold pieces.");
        c.npc("Do you want to pay now?");
        c.options(new Choice("Yes, Ok.", "No thanks, you're not having my money.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    payFine(c, npc, isShantay);
                    return;
                }
                c.npc("You have a choice.");
                c.npc("You can either pay five gold pieces or...");
                c.npc("You can be transported to a maximum security prison in Port Sarim.");
                c.npc("Will you pay the five gold pieces?");
                c.options(new Choice("Yes, Ok.", "No, do your worst!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            payFine(c, npc, isShantay);
                            return;
                        }
                        c.npc("You are to be transported to a maximum security prison in Port Sarim.");
                        c.npc("I hope you've learnt an important lesson from this.");
                        toPortSarim(c);
                    }
                });
            }
        });
    }

    /**
     * Pay up, or explain why you cannot.
     *
     * The purse is read while the branch is being built, which is a moment
     * after the answer and cannot change in between -- the player is locked in
     * a cell with the conversation still running.
     */
    private static void payFine(Conversation c, final Npc npc, final boolean isShantay) {
        c.npc("Good, I see that you have come to your senses.");
        if (c.getPlayer().getInventory().countId(COINS) >= FINE) {
            c.message("You hand over five gold pieces to Shantay.");
            c.take(COINS, FINE);
            c.npc("Great Effendi, now please try to keep the peace.");
            /* The assistant's line is verbatim; the Shantay variant is our
               inference by analogy -- the wiki marks his paid-fine branch as
               missing from the record. */
            c.message(isShantay ? "Shantay unlocks the door to the cell."
                                : "The assistant unlocks the door to the cell.");
            c.then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(CELL_OUT_X, CELL_OUT_Y, false);
                }
            });
            return;
        }
        c.npc("You don't have that kind of cash on you I see.");
        c.npc("But perhaps you have some in your bank?");
        c.npc("You can transfer some money from your bank and pay the fine.");
        c.npc("or you will be sent to a maximum security prison in Port Sarim.");
        c.npc("Which is it going to be?");
        c.options(new Choice("I'll pay the fine.", "I'm not paying the fine!") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Very well, I grow tired of you, you'll be taken to a new jail in Port Sarim.");
                    toPortSarim(c);
                    return;
                }
                c.npc("Ok then..., you'll need access to your bank.");
                /* Ours, not recovered: the cell door is where the fine is
                   actually handed over once the money is in your hand, so say
                   so rather than leave the player holding coins in a locked
                   room wondering what to click. */
                c.message("Take out five gold pieces and the guard will open the cell.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player p = c.getPlayer();
                        c.stop();
                        p.setAccessingBank(true);
                        p.getActionSender().showBank();
                    }
                });
            }
        }.says(1, "No thanks, you're not having my money."));
    }

    private static void toPortSarim(Conversation c) {
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(PORT_SARIM_X, PORT_SARIM_Y, false);
            }
        });
    }

    /** Is this player inside the cell? */
    public static boolean inCell(Player player) {
        return player.getX() >= CELL_MIN_X && player.getX() <= CELL_MAX_X
            && player.getY() >= CELL_MIN_Y && player.getY() <= CELL_MAX_Y;
    }

    /**
     * The cell door, clicked from inside.
     *
     * The recovered sequence -- the Shantay transcript's "Trying to open the
     * door" section: the door refuses, Shantay saunters over, and the same
     * fine loop the arrest ends in runs again. That is what keeps the cell
     * from being a trap: a player who logs out in the cell comes back to it
     * with no conversation at all, and the door rebuilds it. Both outcomes
     * are the ones Shantay offers -- five gold pieces, or the jail in Port
     * Sarim -- so the door adds a place to choose, not a third answer.
     */
    public static void payAtCellDoor(Player player) {
        player.getActionSender().sendMessage("This door is locked.");
        Npc warden = world.getNpc(SHANTAY, 55, 70, 720, 740);
        if (warden == null) {
            warden = world.getNpc(ASSISTANT, 55, 70, 720, 740);
        }
        if (warden == null) {
            /* Ours: both spawns are fixed, so this answers a world that has
               lost them rather than anything Jagex wrote. The fine still
               works so the cell still is not a trap. */
            if (player.getInventory().countId(COINS) >= FINE) {
                player.getInventory().remove(COINS, FINE);
                player.getActionSender().sendInventory();
                player.getActionSender().sendMessage("@pnk@ You pay the fine of five gold pieces");
                player.teleport(CELL_OUT_X, CELL_OUT_Y, false);
            } else {
                player.getActionSender().sendMessage("@gry@ There is nobody around to pay the fine to");
            }
            return;
        }
        final boolean isShantay = warden.getID() == SHANTAY;
        final Npc npc = warden;
        Conversation c = new Conversation(player, warden);
        /* The Shantay wording is the attested one; the assistant variant is
           ours by analogy with his other lines. */
        c.message(isShantay ? "Shantay saunters over to talk with you."
                            : "The assistant saunters over to talk with you.");
        c.npc("If you want to be let out, you have to pay a fine of five gold.");
        c.npc("Do you want to pay now?");
        c.options(new Choice("Yes, Ok.", "No thanks, you're not having my money.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    payFine(c, npc, isShantay);
                    return;
                }
                c.npc("You have a choice.");
                c.npc("You can either pay five gold pieces or...");
                c.npc("You can be transported to a maximum security prison in Port Sarim.");
                c.npc("Will you pay the five gold pieces?");
                c.options(new Choice("Yes, Ok.", "No, do your worst!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            payFine(c, npc, isShantay);
                            return;
                        }
                        c.npc("You are to be transported to a maximum security prison in Port Sarim.");
                        c.npc("I hope you've learnt an important lesson from this.");
                        toPortSarim(c);
                    }
                });
            }
        });
        c.start();
    }

    // --------------------------------------------------------------- gate --

    /**
     * Go through the gate, south into the desert.
     *
     * Called both by clicking the gate and by asking the guard standing on it
     * to let you through, because in the recovered sequence they are the same
     * thing: the poster, the second thoughts it is meant to give you, and then
     * the guard, who takes the pass off you and gives you the disclaimer.
     *
     * The pass is consumed. It is a toll, not a season ticket -- five gold
     * pieces a crossing is the whole of what Shantay charges for keeping the
     * pass open, and it is why his shop stocks twenty of them.
     */
    public static void cross(Player player, final Npc guard) {
        if (guard == null) {
            /* Ours: the standing guard is a fixed spawn and should always be
               there, so this is the answer to a world that has lost him
               rather than anything Jagex wrote. */
            player.getActionSender().sendMessage("@gry@ There is nobody on the gate to check your pass");
            return;
        }
        Conversation c = new Conversation(player, guard);
        cross(c);
        c.start();
    }

    /** The crossing, as steps on a conversation that is already running. */
    private static void cross(Conversation c) {
        Player player = c.getPlayer();
        if (player.getInventory().countId(DISCLAIMER) <= 0) {
            poster(c);
            c.message("That seems pretty scary! Are you sure you want to go through?");
            c.options(new Choice("Yes, that poster doesn't scare me!",
                                 "No, I'm having serious second thoughts now.") {
                public void picked(int option, Conversation c) {
                    goThrough(c, option == 0);
                }
            }.says(0, "I'd like to go into the desert please."));
            return;
        }
        c.message("A poster on the wall says exactly the same as the disclaimer");
        c.message("Are you sure you want to go through?");
        c.options(new Choice("Yeah, I'm not scared!",
                             "No, I'm having serious second thoughts now.") {
            public void picked(int option, Conversation c) {
                goThrough(c, option == 0);
            }
        });
    }

    private static void goThrough(Conversation c, boolean sure) {
        if (!sure) {
            c.message("You decide that your visit to the desert can be postponed..");
            c.message("Perhaps indefinitely!");
            return;
        }
        Player player = c.getPlayer();
        if (player.getInventory().countId(PASS) <= 0) {
            c.message("A guard stops you on your way out of the gate");
            c.npc("You'll need a Shantay pass to go through the gate into the desert.");
            c.npc("See Shantay, he'll sell you one for a very reasonable price.");
            return;
        }
        c.npc("Can I see your Shantay Desert Pass please.");
        c.player("Sure, here you go!");
        c.message("You hand over a Shantay Pass.");
        c.take(PASS, 1);
        if (player.getInventory().countId(DISCLAIMER) <= 0) {
            c.npc("Here, have a disclaimer...");
            c.npc("It means that Shantay isn't responsible if you die in the desert.");
            c.message("The guard gives you a disclaimer.");
            c.give(new InvItem(DISCLAIMER, 1));
        }
        c.message("You go through the gate.");
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(THROUGH_X, THROUGH_Y, false);
            }
        });
    }

    /** The poster on the wall by the gateway, as conversation steps. */
    private static void poster(Conversation c) {
        c.message("There is a large poster on the wall near the gateway. It reads..");
        c.message("@gre@The Desert is a VERY Dangerous place...do not enter if you are scared of dying.");
        c.message("@gre@Beware of high temperatures, sand storms, robbers, and slavers...");
        c.message("@gre@No responsibility is taken by Shantay ");
        c.message("@gre@If anything bad should happen to you in any circumstances whatsoever.");
    }

    /** Looking at the gate, which is looking at the poster nailed to it. */
    public static void lookAtGate(Player player) {
        new Conversation(player, null)
            .message("You look at the huge Stone Gate.")
            .message("On the gate is a large poster, it reads.")
            .message("@gre@The Desert is a VERY Dangerous place...do not enter if you are scared of dying.")
            .message("@gre@Beware of high temperatures, sand storms, robbers, and slavers...")
            .message("@gre@No responsibility is taken by Shantay ")
            .message("@gre@If anything bad should happen to you in any circumstances whatsoever.")
            .message("Despite this warning lots of people seem to pass through the gate.")
            .start();
    }

    // -------------------------------------------------------- the papers --

    /**
     * The disclaimer's "read" command, which nothing answered before. The
     * window, its wording, its blank lines and its red/green alternation are
     * all verbatim from 2003 footage of the read.
     */
    public static void readDisclaimer(Player player) {
        player.getActionSender().sendAlert(
            "@red@*** Shantay Disclaimer ***% %"
            + "@gre@The Desert is a VERY Dangerous place.% %"
            + "@red@Do not enter if you're scared of dying.% %"
            + "@gre@Beware of high temperatures, sand storms, and slavers.% %"
            + "@red@No responsibility is taken by Shantay% %"
            + "@gre@If anything bad happens to you under any circumstances.", true);
    }

    /**
     * The scrumpled piece of paper out of Shantay's pocket, which is the
     * ugthanki kebab recipe.
     */
    public static void readRecipe(Player player) {
        player.getActionSender().sendMessage("@yel@*** Delicious Ugthanki Kebab ***");
        player.getActionSender().sendMessage("Ingredients: Cooked Ugthanki meat, Flour, Water, Onion, Tomato.");
        player.getActionSender().sendMessage("The Ugthanki meat should be nicely grilled.");
        player.getActionSender().sendMessage("Next take the flour and water and make some Pitta Bread.");
        player.getActionSender().sendMessage("You'll need a range to do this.");
        player.getActionSender().sendMessage("Take an onion and chop it into a bowl.");
        player.getActionSender().sendMessage("Take a tomato and chop it into the onion mixture.");
        player.getActionSender().sendMessage("Chop the meat into the Onion and Tomato mixture.");
        player.getActionSender().sendMessage("Finally fill the pitta bread with the Ugthanki, Onion and Tomato mixture");
        player.getActionSender().sendMessage("to make your delicious Ugthanki Kebab.");
    }
}
