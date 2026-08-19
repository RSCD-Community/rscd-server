package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.BarCrawlCard;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Every bartender in the game.
 *
 * Ten npcs, one per inn, and not one of them was registered anywhere before
 * this -- talking to any of them did nothing at all. The drinks themselves
 * were never the gap: Beer, Asgarnian Ale, the Mind Bomb, Dwarven Stout,
 * Whisky, Grog, Dragon bitter and Greenmans ale have had working "drink"
 * cases in InvActionHandler all along. There was simply no way to obtain
 * them, since none of the ten inns is a Shop either.
 *
 *     12   Blue Moon Inn        Varrock, (120,522)
 *     44   Jolly Boar Inn       north-east of Varrock, (85,454)
 *     142  Barmaid              the Rising Sun, Falador, (319,549) and upstairs
 *     150  The Rusty Anchor     Port Sarim, (255,626)
 *     279  Dead Man's Chest     Brimhaven, (451,705)
 *     306  Forester's Arms      Seers' Village, (524,451)
 *     340  Flying Horse Inn     East Ardougne, (619,587)
 *     382  Khazard Bartender    Port Khazard, (591,718)
 *     520  Dancing Donkey Inn   Varrock, (94,526)
 *     529  Ye Olde Dragon Inn   Yanille, (629,766)
 *
 * Six of the ten also sign the Alfred Grimhand barcrawl card. That branch is
 * offered only to a player who is on the crawl and carrying the card, which
 * is how the transcripts mark it ("cond=If the player is doing the
 * barcrawl"). The state and the drink effects are model/BarCrawlCard.java;
 * the guard, the gate and the card itself are quests/BarCrawl.java.
 *
 * Every line below is verbatim from the wiki's transcripts, misspellings
 * included -- "Yohoho me hearty", "Thanksh very mush", "signiture",
 * "thankyou", "I'll try the meat pie", the Dancing Donkey's all-lowercase
 * chatter. Nothing here is written from scratch. The one line used somewhere
 * it was not recorded is the Khazard bartender's refusal: his transcript
 * shows no insufficient-funds branch at all, so he gets {@link #BROKE}, the
 * stock sentence eight of the other nine answer with word for word.
 */
public class Bartenders implements NpcHandler {

    private static final int BLUE_MOON = 12;
    private static final int JOLLY_BOAR = 44;
    private static final int BARMAID = 142;
    private static final int RUSTY_ANCHOR = 150;
    private static final int DEAD_MANS_CHEST = 279;
    private static final int FORESTERS_ARMS = 306;
    private static final int FLYING_HORSE = 340;
    private static final int KHAZARD = 382;
    private static final int DANCING_DONKEY = 520;
    private static final int YE_OLDE_DRAGON = 529;

    private static final int BEER = 193;
    private static final int ASGARNIAN_ALE = 267;
    private static final int MIND_BOMB = 268;
    private static final int DWARVEN_STOUT = 269;
    private static final int KARAMJA_RUM = 318;
    private static final int MEAT_PIE = 259;
    private static final int STEW = 346;
    private static final int GROG = 598;
    private static final int KHALI_BREW = 735;
    private static final int DRAGON_BITTER = 829;
    private static final int GREENMANS_ALE = 830;

    /** The stock refusal. Eight of the ten transcripts use it word for word. */
    private static final String BROKE = "Oh dear. I don't seem to have enough money";

    /* The skills each barcrawl drink drains, in Formulae.statArray order.
       Hits is never in these lists -- BarCrawlCard.drink takes it separately. */
    private static final int[] GUTROT = { 0, 1, 2, 13 };
    private static final int[] OLDE_SUSPICIOUSE = { 0, 1, 14, 12, 6, 2, 13 };
    private static final int[] HAND_OF_DEATH = { 0, 1, 4, 10 };
    private static final int[] SUPERGROG = { 0, 1, 7, 15, 5 };
    private static final int[] LIVERBANE = { 0, 1, 9, 11, 8 };

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case BLUE_MOON:        blueMoon(npc, player);       return;
            case JOLLY_BOAR:       jollyBoar(npc, player);      return;
            case BARMAID:          barmaid(npc, player);        return;
            case RUSTY_ANCHOR:     rustyAnchor(npc, player);    return;
            case DEAD_MANS_CHEST:  deadMansChest(npc, player);  return;
            case FORESTERS_ARMS:   forestersArms(npc, player);  return;
            case FLYING_HORSE:     flyingHorse(npc, player);    return;
            /* Unreachable, and meant to be. 382 is deliberately absent from
               this handler's <ids> block in NpcHandlers.xml because a handler
               beats a quest for NPC_TALK dispatch, and quests/FightArena.java
               associates him and reimplements this whole menu -- it has to,
               because his khali brew is gated on quest state. Registering him
               here would silently take him back off the quest. The branch is
               kept because it is the transcript, and because deleting it is
               how someone later "discovers" the gap and re-adds the id. */
            case KHAZARD:          khazard(npc, player);        return;
            case DANCING_DONKEY:   dancingDonkey(npc, player);  return;
            case YE_OLDE_DRAGON:   yeOldeDragon(npc, player);   return;
        }
    }

    // ------------------------------------------------------------ helpers --

    /**
     * Sell one thing. The price is checked at the moment the option is picked
     * rather than when the menu is built, which is the same order the
     * transcripts run in: the bartender names the price first and only then
     * does the player discover they cannot pay.
     */
    private static void sell(Conversation c, int price, int item, String bought) {
        if (c.getPlayer().getInventory().countId(10) < price) {
            c.player(BROKE);
            return;
        }
        c.take(10, price);
        c.give(new InvItem(item));
        c.message(bought);
    }

    /**
     * The barcrawl option itself, once the bartender has named their price.
     *
     * @return false if the player could not pay, so a caller with a parting
     *         line of its own knows not to add it.
     */
    private static boolean serveCrawl(Conversation c, final int bar, final int price,
            final boolean charge, final int[] stats, String[] lines, String broke) {
        if (!BarCrawlCard.canAfford(c.getPlayer(), price)) {
            c.player(broke);
            return false;
        }
        for (int i = 0; i < lines.length; i++) {
            c.message(lines[i]);
        }
        c.then(new Effect() {
            public void run(Conversation conv) {
                Player p = conv.getPlayer();
                BarCrawlCard.pay(p, price, charge);
                if (stats != null) {
                    BarCrawlCard.drink(p, stats);
                }
                BarCrawlCard.sign(p, bar);
            }
        });
        return true;
    }

    // -------------------------------------------------------------- inns --

    /* Varrock, the one that knows it is in a computer game. */
    private void blueMoon(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc).npc("What can I do yer for?");
        if (BarCrawlCard.wants(player, BarCrawlCard.BLUE_MOON)) {
            c.options(new Choice("A glass of your finest ale please",
                    "Can you recommend anywhere an adventurer might make his fortune?",
                    "Do you know where I can get some good equipment?",
                    "I'm doing Alfred Grimhand's barcrawl") {
                public void picked(int option, Conversation c) {
                    blueMoonPicked(option, c);
                }
            });
        } else {
            c.options(new Choice("A glass of your finest ale please",
                    "Can you recommend anywhere an adventurer might make his fortune?",
                    "Do you know where I can get some good equipment?") {
                public void picked(int option, Conversation c) {
                    blueMoonPicked(option, c);
                }
            });
        }
        c.start();
    }

    private void blueMoonPicked(int option, Conversation c) {
        switch (option) {
            case 0:
                c.npc("No problemo");
                c.npc("That'll be 2 coins");
                sell(c, 2, BEER, "You buy a pint of beer");
                return;
            case 1:
                c.npc("Ooh I don't know if I should be giving away information");
                c.npc("Makes the computer game too easy");
                c.options(new Choice("Oh ah well",
                        "Computer game? What are you talking about?",
                        "Just a small clue?") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("This world around us..");
                            c.npc("is all a computer game..");
                            c.npc("called Runescape");
                            c.player("Nope, still don't understand what you are talking about");
                            c.player("What's a computer?");
                            c.npc("It's a sort of magic box thing,");
                            c.npc("which can do all sorts of different things");
                            c.player("I give up");
                            c.player("You're obviously completely mad!");
                        } else if (option == 2) {
                            c.npc("Go and talk to the bartender at the Jolly Boar Inn");
                            c.npc("He doesn't seem to mind giving away clues");
                        }
                    }
                }.says(1, "Computer game?", "What are you talking about?"));
                return;
            case 2:
                c.npc("Well, there's the sword shop across the road,");
                c.npc("or there's also all sorts of shops up around the market");
                return;
            default:
                c.npc("Oh no not another of you guys");
                c.npc("These barbarian barcrawls cause too much damage to my bar");
                c.npc("You're going to have to pay 50 gold for the Uncle Humphrey's gutrot");
                if (serveCrawl(c, BarCrawlCard.BLUE_MOON, 50, true, GUTROT,
                        new String[] {
                            "You buy some gutrot",
                            "You drink the gutrot",
                            "your insides feel terrible",
                            "The bartender signs your card" },
                        "I don't have 50 coins")) {
                    c.player("Blearrgh");
                }
        }
    }

    /*
     * Two of the Jolly Boar's options are not what the player then says. The
     * double space in the beer line is Jagex's -- Transcript:Bartender (Jolly
     * Boar Inn) marks it {{sic}}, along with a trailing space that renders as
     * nothing and is not reproduced here. No other bar in the game words its
     * beer option this way, so neither of these travels.
     */
    private static final String JOLLY_BOAR_BEER = "I'll have a pint of  beer please";
    private static final String JOLLY_BOAR_HINTS = "Any hints on where I can go adventuring?";

    /* North-east of Varrock. The one who does give away clues. */
    private void jollyBoar(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc).npc("Yes please?");
        if (BarCrawlCard.wants(player, BarCrawlCard.JOLLY_BOAR)) {
            c.options(new Choice("I'll have a beer please",
                    "Any hints where I can go adventuring?",
                    "Heard any good gossip?",
                    "I'm doing Alfred Grimhand's barcrawl") {
                public void picked(int option, Conversation c) {
                    jollyBoarPicked(option, c);
                }
            }.says(0, JOLLY_BOAR_BEER).says(1, JOLLY_BOAR_HINTS));
        } else {
            c.options(new Choice("I'll have a beer please",
                    "Any hints where I can go adventuring?",
                    "Heard any good gossip?") {
                public void picked(int option, Conversation c) {
                    jollyBoarPicked(option, c);
                }
            }.says(0, JOLLY_BOAR_BEER).says(1, JOLLY_BOAR_HINTS));
        }
        c.start();
    }

    private void jollyBoarPicked(int option, Conversation c) {
        switch (option) {
            case 0:
                c.npc("Ok, that'll be two coins");
                sell(c, 2, BEER, "You buy a pint of beer");
                return;
            case 1:
                c.npc("It's funny you should say that");
                c.npc("An adventurer passed through here, the other day,");
                c.npc("claiming to have found a dungeon full of treasure,");
                c.npc("guarded by vicious skeletal warriors");
                c.npc("He said he found the entrance in a ruined town");
                c.npc("deep in the woods to the west of here, behind the palace");
                c.npc("Now how much faith you put in that story is up to you,");
                c.npc("but it probably wouldn't do any harm to have a look");
                c.player("Thanks");
                c.player("I may try that at some point");
                return;
            case 2:
                c.npc("I'm not that well up on the gossip out here");
                c.npc("I've heard that the bartender in the Blue Moon Inn has gone a little crazy");
                c.npc("He keeps claiming he is part of something called a computer game");
                c.npc("What that means, I don't know");
                c.npc("That's probably old news by now though");
                return;
            default:
                c.npc("Ah, there seems to be a fair few doing that one these days");
                c.npc("My supply of Olde Suspiciouse is starting to run low");
                c.npc("It'll cost you 10 coins");
                if (serveCrawl(c, BarCrawlCard.JOLLY_BOAR, 10, true, OLDE_SUSPICIOUSE,
                        new String[] {
                            "You buy a pint of Olde Suspiciouse",
                            "You gulp it down",
                            "Your head is spinning",
                            "The bartender signs your card" },
                        "I don't have 10 coins right now")) {
                    c.player("Thanksh very mush");
                }
        }
    }

    /* The Rising Sun, Falador. Two spawns, ground floor and upstairs. */
    private void barmaid(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        if (BarCrawlCard.wants(player, BarCrawlCard.RISING_SUN)) {
            c.options(new Choice("Hi what ales are you serving",
                    "I'm doing Alfred Grimhand's barcrawl") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        barmaidMenu(c);
                        return;
                    }
                    c.npc("Hehe this'll be fun");
                    c.npc("You'll be after our off the menu hand of death cocktail then");
                    c.npc("Lots of expensive parts to the cocktail though");
                    c.npc("So it will cost you 70 coins");
                    serveCrawl(c, BarCrawlCard.RISING_SUN, 70, true, HAND_OF_DEATH,
                        new String[] {
                            "You buy a hand of death cocktail",
                            "You drink the cocktail",
                            "You stumble around the room",
                            "The barmaid giggles",
                            "The barmaid signs your card" },
                        "I don't have that much money on me");
                }
            });
        } else {
            c.player("Hi, what ales are you serving?");
            barmaidMenu(c);
        }
        c.start();
    }

    private void barmaidMenu(Conversation c) {
        c.npc("Well you can either have a nice Asgarnian Ale or a Wizards Mind bomb");
        c.npc("Or a Dwarven Stout");
        c.options(new Choice("One Asgarnian Ale please",
                "I'll try the mind bomb",
                "Can I have a Dwarven Stout?",
                "I don't feel like any of those") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("That'll be two gold");
                        sell(c, 2, ASGARNIAN_ALE, "You buy an Asgarnian Ale");
                        return;
                    case 1:
                        c.npc("That'll be two gold");
                        sell(c, 2, MIND_BOMB, "You buy a pint of Wizard's Mind Bomb");
                        return;
                    case 2:
                        c.npc("That'll be three gold");
                        sell(c, 3, DWARVEN_STOUT, "You buy a pint of Dwarven Stout");
                }
            }
        });
    }

    /*
     * Port Sarim. The goblin-armour rumour is the hook Goblin Diplomacy hangs
     * off, and it changes once that quest is done.
     */
    private void rustyAnchor(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        final boolean afterGoblins = player.getQuestManager().completed(Quests.GOBLIN_DIPLOMACY);
        if (BarCrawlCard.wants(player, BarCrawlCard.RUSTY_ANCHOR)) {
            c.options(new Choice("Could i buy a beer please?",
                    afterGoblins ? "Have you heard any more rumours in here?"
                                 : "Not very busy in here today is it?",
                    "I'm doing Alfred Grimhand's barcrawl") {
                public void picked(int option, Conversation c) {
                    rustyAnchorPicked(option, c, afterGoblins);
                }
            });
        } else {
            c.options(new Choice("Could i buy a beer please?",
                    afterGoblins ? "Have you heard any more rumours in here?"
                                 : "Not very busy in here today is it?") {
                public void picked(int option, Conversation c) {
                    rustyAnchorPicked(option, c, afterGoblins);
                }
            });
        }
        c.start();
    }

    private void rustyAnchorPicked(int option, Conversation c, boolean afterGoblins) {
        switch (option) {
            case 0:
                c.npc("Sure that will be 2 gold coins please");
                if (c.getPlayer().getInventory().countId(10) < 2) {
                    c.message("You dont have enough coins for the beer");
                    return;
                }
                c.player("Ok here you go thanks");
                c.take(10, 2);
                c.give(new InvItem(BEER));
                c.message("you buy a pint of beer");
                return;
            case 1:
                if (afterGoblins) {
                    c.npc("No it hasn't been very busy lately");
                    return;
                }
                c.npc("No it was earlier");
                c.npc("There was a guy in here saying the goblins up by the mountain are arguing again");
                c.npc("Of all things about the colour of their armour.");
                c.npc("Knowing the goblins, it could easily turn into a full blown war");
                c.npc("Which wouldn't be good");
                c.npc("Goblin wars make such a mess of the countryside");
                c.player("Well if I have time I'll see if I can go and knock some sense into them");
                /*
                 * This npc is claimed here, not by GoblinDiplomacy -- see the
                 * comment on this id in NpcHandlers.xml -- so the quest never
                 * sees this conversation. Reporting the rumour by name is the
                 * established route for exactly that situation (Quest.note(),
                 * used the same way by SpellHandler for GodCharges and by
                 * PlayerAppearanceUpdater for Tutorial Island): the quest
                 * decides for itself whether hearing it means anything.
                 */
                c.then(new Effect() {
                    public void run(Conversation conv) {
                        conv.getPlayer().getQuestManager().note(Quests.GOBLIN_DIPLOMACY, "heard-rumour");
                    }
                });
                return;
            default:
                c.npc("Are you sure you look a bit skinny for that");
                c.player("Just give me whatever drink I need to drink here");
                c.npc("Ok one black skull ale coming up, 8 coins please");
                /*
                 * charge=false and stats=null. The black skull ale is the one
                 * drink on the crawl that costs nothing and does nothing: the
                 * eight coins are checked and then never taken (two recorded
                 * replays on classic.runescape.wiki), and the table gives it
                 * no stat drain and no Hits loss, only hiccups.
                 */
                serveCrawl(c, BarCrawlCard.RUSTY_ANCHOR, 8, false, null,
                    new String[] {
                        "You buy a black skull ale",
                        "You drink your black skull ale",
                        "Your vision blurs",
                        "The bartender signs your card" },
                    "I don't have 8 coins with me");
        }
    }

    /* Brimhaven. */
    private void deadMansChest(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc)
            .npc("Yohoho me hearty what would you like to drink?");
        if (BarCrawlCard.wants(player, BarCrawlCard.DEAD_MANS_CHEST)) {
            c.options(new Choice("Nothing thankyou",
                    "A pint of Grog please",
                    "A bottle of rum please",
                    "I'm doing Alfred Grimhand's barcrawl") {
                public void picked(int option, Conversation c) {
                    deadMansChestPicked(option, c);
                }
            });
        } else {
            c.options(new Choice("Nothing thankyou",
                    "A pint of Grog please",
                    "A bottle of rum please") {
                public void picked(int option, Conversation c) {
                    deadMansChestPicked(option, c);
                }
            });
        }
        c.start();
    }

    private void deadMansChestPicked(int option, Conversation c) {
        switch (option) {
            case 1:
                c.npc("One grog coming right up");
                c.npc("That'll be 3 gold");
                sell(c, 3, GROG, "You buy a pint of Grog");
                return;
            case 2:
                c.npc("That'll be 27 gold");
                sell(c, 27, KARAMJA_RUM, "You buy a bottle of rum");
                return;
            case 3:
                c.npc("Haha time to be breaking out the old supergrog");
                c.npc("That'll be 15 coins please");
                serveCrawl(c, BarCrawlCard.DEAD_MANS_CHEST, 15, true, SUPERGROG,
                    new String[] {
                        "The bartender serves you a glass of strange thick dark liquid",
                        "You wince and drink it",
                        "You stagger backwards",
                        "You think you see 2 bartenders signing 2 barcrawl cards" },
                    "Sorry I don't have 15 coins");
        }
    }

    /* Seers' Village. The only inn that serves food. */
    private void forestersArms(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc)
            .npc("Good morning, what would you like?");
        if (BarCrawlCard.wants(player, BarCrawlCard.FORESTERS_ARMS)) {
            c.options(new Choice("What do you have?",
                    "Beer please",
                    "I'm doing Alfred Grimhand's barcrawl",
                    "I don't really want anything thanks") {
                public void picked(int option, Conversation c) {
                    forestersArmsPicked(option, c);
                }
            });
        } else {
            c.options(new Choice("What do you have?",
                    "Beer please",
                    "I don't really want anything thanks") {
                public void picked(int option, Conversation c) {
                    forestersArmsPicked(option, c);
                }
            });
        }
        c.start();
    }

    private void forestersArmsPicked(int option, Conversation c) {
        // Option 2 is the barcrawl when it is offered and the brush-off when
        // it is not; the brush-off is always last either way.
        if (option == 2 && !BarCrawlCard.wants(c.getPlayer(), BarCrawlCard.FORESTERS_ARMS)) {
            option = 3;
        }
        switch (option) {
            case 0:
                c.npc("Well we have beer");
                c.npc("Or if you want some food, we have our home made stew and meat pies");
                c.options(new Choice("Beer please",
                        "I'll try the meat pie",
                        "Could I have some stew please",
                        "I don't really want anything thanks") {
                    public void picked(int option, Conversation c) {
                        switch (option) {
                            case 0:
                                forestersBeer(c);
                                return;
                            case 1:
                                c.npc("Ok, that'll be 16 gold");
                                sell(c, 16, MEAT_PIE, "You buy a nice hot meat pie");
                                return;
                            case 2:
                                c.npc("A bowl of stew, that'll be 20 gold please");
                                sell(c, 20, STEW, "You buy a bowl of home made stew");
                        }
                    }
                });
                return;
            case 1:
                forestersBeer(c);
                return;
            case 2:
                c.npc("Oh you're a barbarian then");
                c.npc("Now which of these was the barrels contained the liverbane ale?");
                c.npc("That'll be 18 coins please");
                serveCrawl(c, BarCrawlCard.FORESTERS_ARMS, 18, true, LIVERBANE,
                    new String[] {
                        "The bartender gives you a glass of liverbane ale",
                        "You gulp it down",
                        "The room seems to be swaying",
                        "The bartender scrawls his signiture on your card" },
                    "Sorry I don't have 18 coins");
        }
        /* case 3, "I don't really want anything thanks", is the echoed option
           and nothing follows it. */
    }

    private static void forestersBeer(Conversation c) {
        c.npc("one beer coming up");
        c.npc("Ok, that'll be two coins");
        sell(c, 2, BEER, "You buy a pint of beer");
    }

    /* East Ardougne. Serves exactly one thing. */
    private void flyingHorse(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Would you like to buy a drink?")
            .player("What do you serve?")
            .npc("Beer")
            .options(new Choice("I'll have a beer then", "I'll not have anything then") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("Ok, that'll be two coins");
                    sell(c, 2, BEER, "You buy a pint of beer");
                }
            })
            .start();
    }

    /*
     * Port Khazard. The khali brew is off the menu until the player has taken
     * up the Fight Arena, and it is free -- the only free drink in the game.
     */
    private void khazard(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc)
            .player("Hello")
            .npc("Hello, what can i get you? we have all sorts of brew");
        if (player.getQuestManager().stageOf(Quests.FIGHT_ARENA) > 0) {
            c.options(new Choice("I'll have a beer please",
                    "I'd like a khali brew please",
                    "Got any news?") {
                public void picked(int option, Conversation c) {
                    khazardPicked(option, c);
                }
            });
        } else {
            c.options(new Choice("I'll have a beer please", "Got any news?") {
                public void picked(int option, Conversation c) {
                    khazardPicked(option == 0 ? 0 : 2, c);
                }
            });
        }
        c.start();
    }

    private void khazardPicked(int option, Conversation c) {
        switch (option) {
            case 0:
                c.npc("There you go, that's one gold coin");
                sell(c, 1, BEER, "You buy a pint of beer");
                return;
            case 1:
                c.npc("There you go");
                c.npc("No charge");
                c.give(new InvItem(KHALI_BREW));
                return;
            default:
                c.npc("Well have you seen the famous khazard fight arena?");
                c.npc("I've seen some grand battles in my time..");
                c.npc("Ogres, goblins, even dragons, they all come to fight");
                c.npc("The poor slaves of general khazard");
        }
    }

    /* Varrock again, east of the square. All in lower case, as recorded. */
    private void dancingDonkey(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello")
            .npc("good day to you, brave adventurer")
            .npc("can i get you a refreshing beer")
            .options(new Choice("yes please", "no thanks", "how much?") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:
                            dancingDonkeyBeer(c);
                            return;
                        case 1:
                            c.npc("let me know if you change your mind");
                            return;
                        default:
                            c.npc("two gold pieces a pint");
                            c.npc("so, what do you say?");
                            c.options(new Choice("yes please", "no thanks") {
                                public void picked(int option, Conversation c) {
                                    if (option == 0) {
                                        dancingDonkeyBeer(c);
                                    } else {
                                        c.npc("let me know if you change your mind");
                                    }
                                }
                            });
                    }
                }
            })
            .start();
    }

    private static void dancingDonkeyBeer(Conversation c) {
        c.npc("ok then, that's two gold coins please");
        if (c.getPlayer().getInventory().countId(10) < 2) {
            c.message("you don't have enough gold");
            return;
        }
        c.message("you give two coins to the barman");
        c.take(10, 2);
        c.message("he gives you a cold beer");
        c.give(new InvItem(BEER));
        c.npc("cheers");
        c.player("cheers");
    }

    /* Yanille. */
    private void yeOldeDragon(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("What can I get you?")
            .player("What's on the menu?")
            .npc("Dragon bitter and Greenmans ale")
            .options(new Choice("I'll give it a miss I think",
                    "I'll try the dragon bitter",
                    "Can I have some greenman's ale?") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:
                            c.npc("Come back when you're a little thirstier");
                            return;
                        case 1:
                            c.npc("Ok, that'll be two coins");
                            sell(c, 2, DRAGON_BITTER, "You buy a pint of dragon bitter");
                            return;
                        default:
                            c.npc("Ok, that'll be ten coins");
                            sell(c, 10, GREENMANS_ALE, "You buy a pint of ale");
                    }
                }
            }.says(0, "I'll give it a miss"))
            .start();
    }
}
