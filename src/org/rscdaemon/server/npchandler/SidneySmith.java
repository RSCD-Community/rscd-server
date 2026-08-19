package org.rscdaemon.server.npchandler;

import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Sidney Smith, certification clerk, in the building north west of the Yanille
 * bank. Added with Legends' Quest in August 2003, and the only certer for the
 * six things nobody else would take: the four super potions, dragon bones and
 * limpwurt roots.
 *
 * She is not a stall certer and shares nothing with {@link Certer} but the
 * five-for-one rate. Her menus are paged, her wording is her own, and she
 * takes an item or a certificate used directly on her as a shortcut past the
 * conversation. Built 2026-08-04 from Transcript:Sidney Smith.
 *
 * RSCD had her wired into the generic stall certer with four potions and the
 * greeting "Welcome to my potion exchange stall", which she never said, and
 * without dragon bones or limpwurt roots at all. Those two were RSCD's only
 * copies of certificates 1270 and 1271, and both had been put on Bonzo, the
 * Fishing Contest organiser in Hemenster, where the wiki and the certificates'
 * own examine text ("Each certificate exchangable at Yanille for 5 ...") both
 * say Yanille.
 *
 * Two of the six rows below are marked RECONSTRUCTED. The transcript records
 * every line for the four potions but stops at {{tmissing}} for dragon bones
 * and limpwurt roots, apart from the limpwurt lines recovered from the
 * use-on-her section. Those gaps are filled by following the potions' own
 * pattern, and are ours rather than passed off as recovered.
 */
public class SidneySmith implements NpcHandler {

    /**
     * One certifiable good. The strings are separate fields because Sidney's
     * lines do not agree with each other about what the same item is called:
     * she asks about "Prayer Restore Potions", refuses over "Prayer potions",
     * and signs off on "Prayer Potion certificates".
     */
    private static final class Good {
        final int itemID;
        final int certID;
        /** Menu label, certificating side: "* Prayer Restore Potion *". */
        final String goodsLabel;
        /** Menu label, redeeming side: "* Restore Prayer Potion Certificates *". */
        final String certLabel;
        /** "You don't have any ... to certificate." */
        final String shortOf;
        /** "How many ... would you like to certificate?" */
        final String counted;
        /** "Ok, that's your ... certificated." */
        final String certificated;
        /** "Sorry, but you don't have any" / "... to change." */
        final String certShortOf;
        /** "How many ... certificates do you want to change?" */
        final String certCounted;
        /** "Ok, that's your ... certificates done." */
        final String certDone;

        Good(int itemID, int certID, String goodsLabel, String certLabel, String shortOf,
             String counted, String certificated, String certShortOf, String certCounted,
             String certDone) {
            this.itemID = itemID;
            this.certID = certID;
            this.goodsLabel = goodsLabel;
            this.certLabel = certLabel;
            this.shortOf = shortOf;
            this.counted = counted;
            this.certificated = certificated;
            this.certShortOf = certShortOf;
            this.certCounted = certCounted;
            this.certDone = certDone;
        }
    }

    /** Menu 1 is the first four, menu 2 the last two, exactly as she pages them. */
    private static final Good[] GOODS = {
        new Good(483, 1272, "* Prayer Restore Potion *", "* Restore Prayer Potion Certificates *",
                 "Prayer potions", "Prayer Restore Potions", "Prayer Restore potions",
                 "Prayer Restore Potion Certificates", "Prayer Restore Potion", "Prayer Potion"),
        new Good(486, 1273, "* Super Attack Potion *", "* Super Attack Potion Certificates *",
                 "Super Attack potions", "Super Attack Potions", "Super Attack potions",
                 "Super attack Potion Certificates", "Super Attack Potion", "Super Attack Potion"),
        new Good(495, 1274, "* Super Defense Potion *", "* Super Defense Potion Certificates *",
                 "Super Defense potions", "Super Defense Potions", "Super Defense potions",
                 "Super Defense Potion Certificates", "Super Defense Potion", "Super Defense Potion"),
        new Good(492, 1275, "* Super Strength Potion *", "* Super Strength Potion Certificates *",
                 "Super Strength potions", "Super Strength Potions", "Super Strength potions",
                 "Super Strength Potion Certificates", "Super Strength Potion", "Super Strength Potion"),
        // RECONSTRUCTED: the transcript records the two menu labels and the
        // "Dragon Bones Certificates to change." refusal, and nothing else.
        new Good(814, 1270, "* Dragon Bones *", "* Dragon Bones Certificates *",
                 "Dragon Bones", "Dragon Bones", "Dragon Bones",
                 "Dragon Bones Certificates", "Dragon Bones", "Dragon Bones"),
        // RECONSTRUCTED in part: everything here but "Limpwurt Roots" and
        // "Limpwurt Root" comes from the use-on-her section of the transcript.
        new Good(220, 1271, "* Limpwurt Root *", "* Limpwurt Root Certificates *",
                 "Limpwurt Roots", "Limpwurt Roots", "Limpwurt Roots",
                 "Limpwurt Root Certificates", "Limpwurt Root", "Limpwurt Root"),
    };

    private static final int PAGE = 4;
    private static final String TO_PAGE_2 = "-*- Menu 2 -*-";
    private static final String TO_PAGE_1 = "-*- Menu 1 -*-";

    /** "Sidney writes you out ..." */
    private static final String[] WRITTEN = {
        "Sidney writes you out a certificate.",
        "Sidney writes you out two certificates.",
        "Sidney writes you out three certificates.",
        "Sidney writes you out four certificates.",
        "Sidney writes you out five certificates.",
    };

    private static final String[] GOODS_AMOUNTS = {"Five", "Ten", "Fifteen", "Twenty", "Twenty Five"};

    private static final String[] CERT_AMOUNTS = {
        "One Certificate please", "Two Certificates Please", "Three Certificates Please.",
        "Four Certificates Please", "Five Certificates Please.",
    };

    // ------------------------------------------------------------- talking --

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .npc("Hello, I'm Sidney Smith, the certification Clerk.")
            .npc("How can I help you ?")
            .options(mainMenu())
            .start();
    }

    private static Choice mainMenu() {
        return new Choice("I'd like to certificate some goods please.",
                          "I'd like to change some certificates for goods please.",
                          "What is certification ?",
                          "Which goods do you certificate ?") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: certify(c); break;
                    case 1: redeem(c); break;
                    case 2: whatIsCertification(c); break;
                    default: whichGoods(c); break;
                }
            }
        };
    }

    // ---------------------------------------------------- goods -> certs --

    private static void certify(Conversation c) {
        Player player = c.getPlayer();
        boolean any = false;
        for (Good good : GOODS) {
            if (player.getInventory().countId(good.itemID) >= 5) {
                any = true;
                break;
            }
        }

        if (!any) {
            c.npc("Sorry, but you either don't have enough items for me to certificate.")
             .npc("or you don't have the right type of items for me to certificate.")
             .options(new Choice("Which goods do you certificate?",
                                 "How many items do you need to make a certificate.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        whichGoods(c);
                    } else {
                        howMany(c);
                    }
                }
            });
            return;
        }

        certifyPage(c, 0);
    }

    private static void certifyPage(Conversation c, final int page) {
        c.picker(new Choice(page == 0 ? labels(0, PAGE, true, TO_PAGE_2)
                                      : labels(PAGE, GOODS.length, true, TO_PAGE_1)) {
            public void picked(int option, Conversation c) {
                int first = page == 0 ? 0 : PAGE;
                int count = (page == 0 ? PAGE : GOODS.length - PAGE);
                if (option >= count) {
                    certifyPage(c, page == 0 ? 1 : 0);
                    return;
                }
                certifyGood(c, GOODS[first + option]);
            }
        });
    }

    private static void certifyGood(Conversation c, final Good good) {
        final int held = c.getPlayer().getInventory().countId(good.itemID);
        if (held < 5) {
            c.npc("You don't have any " + good.shortOf + " to certificate.")
             .npc("Which goods would you like to certificate?");
            certifyPage(c, 0);
            return;
        }

        c.npc("How many " + good.counted + " would you like to certificate?")
         .picker(new Choice(amounts("None", GOODS_AMOUNTS, held / 5)) {
            public void picked(final int option, Conversation c) {
                if (option == 0) {
                    c.message("You decide not to change any items.");
                    return;
                }
                final int certs = option;   // "Five" is one certificate
                c.then(new Effect() {
                    public void run(Conversation c) {
                        writeOut(c.getPlayer(), good, certs);
                    }
                })
                 .message(WRITTEN[certs - 1])
                 .npc("Ok, that's your " + good.certificated + " certificated.")
                 .player("Ok thanks.");
            }
        });
    }

    private static void writeOut(Player player, Good good, int certs) {
        int goods = certs * 5;
        if (player.getInventory().countId(good.itemID) < goods) {
            return;
        }
        // Nothing she certifies is stackable, so remove(id, amount) would only
        // ever take one; it has to go one at a time.
        for (int i = 0; i < goods; ++i) {
            if (player.getInventory().remove(good.itemID, 1) < 0) {
                return;
            }
        }
        player.getInventory().add(new InvItem(good.certID, certs));
        player.getActionSender().sendInventory();
    }

    // ---------------------------------------------------- certs -> goods --

    private static void redeem(Conversation c) {
        Player player = c.getPlayer();
        boolean any = false;
        for (Good good : GOODS) {
            if (player.getInventory().countId(good.certID) > 0) {
                any = true;
                break;
            }
        }

        if (!any) {
            c.npc("Sorry, but you don't have any certificates that I can change.")
             .npc("I can only change the following certificates into goods.")
             .npc("Dragon Bone Certificates,")
             .npc("Limpwurt Root Certificates,")
             .npc("Prayer Potion Certificates,")
             .npc("Super Attack Potion Certificates,")
             .npc("Super Defense Potion Certificates,")
             .npc("and Super Strength Potion Certificates.");
            return;
        }

        c.npc("Ok then, which certificates would you like to change?");
        redeemPage(c, 0);
    }

    private static void redeemPage(Conversation c, final int page) {
        c.picker(new Choice(page == 0 ? labels(0, PAGE, false, TO_PAGE_2)
                                      : labels(PAGE, GOODS.length, false, TO_PAGE_1)) {
            public void picked(int option, Conversation c) {
                int first = page == 0 ? 0 : PAGE;
                int count = (page == 0 ? PAGE : GOODS.length - PAGE);
                if (option >= count) {
                    redeemPage(c, page == 0 ? 1 : 0);
                    return;
                }
                redeemGood(c, GOODS[first + option]);
            }
        });
    }

    private static void redeemGood(Conversation c, final Good good) {
        final int held = c.getPlayer().getInventory().countId(good.certID);
        if (held < 1) {
            c.npc("Sorry, but you don't have any")
             .npc(good.certShortOf + " to change.");
            return;
        }

        c.npc("How many " + good.certCounted + " certificates do you want to change?")
         .picker(new Choice(amounts("None thanks.", CERT_AMOUNTS, held)) {
            public void picked(final int option, Conversation c) {
                if (option == 0) {
                    c.player("None thanks.").npc("Ok, suit yourself.");
                    return;
                }
                final int certs = option;
                c.then(new Effect() {
                    public void run(Conversation c) {
                        handBack(c.getPlayer(), good, certs);
                    }
                })
                 .npc("Ok, that's your " + good.certDone + " certificates done.")
                 .player("Ok thanks.");
            }
        });
    }

    private static void handBack(Player player, Good good, int certs) {
        if (player.getInventory().countId(good.certID) < certs) {
            return;
        }
        if (player.getInventory().remove(good.certID, certs) < 0) {
            return;
        }
        for (int i = 0; i < certs * 5; ++i) {
            player.getInventory().add(new InvItem(good.itemID, 1));
        }
        player.getActionSender().sendInventory();
    }

    // ------------------------------------------------------ what she says --

    private static void whatIsCertification(Conversation c) {
        c.npc("It's quite easy really..")
         .npc("You swap some goods for certificates which are easier to store.")
         .npc("I specialise in certificating very rare items.")
         .npc("The kinds of items only Legendary Runescape citizens will own.")
         .options(new Choice("I'd like to certificate some goods please.",
                             "I'd like to change some certificates for goods please.",
                             "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    certify(c);
                } else if (option == 1) {
                    redeem(c);
                }
            }
        });
    }

    private static void whichGoods(Conversation c) {
        c.npc("Well, I can certificate the following items.")
         .npc("Prayer Restore Potion,")
         .npc("Super Attack Potion,")
         .npc("Super Defense Potion,")
         .npc("Super Strength Potion,")
         .npc("Dragon Bones,")
         .npc("and Limpwurt Root.")
         .options(new Choice("How many items do you need to make a certificate.",
                             "I'd like to certificate some goods please.",
                             "I'd like to change some certificates for goods please.",
                             "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    howMany(c);
                } else if (option == 1) {
                    certify(c);
                } else if (option == 2) {
                    redeem(c);
                }
            }
        });
    }

    private static void howMany(Conversation c) {
        c.npc("Well, you need at the least five items to make a certificate.")
         .npc("We'll turn any five items into one certificate.")
         .npc("It makes storage and transportation much easier.")
         .options(new Choice("Which goods do you certificate?", "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    whichGoods(c);
                }
            }
        });
    }

    // ----------------------------------------------------- use-on shortcut --

    /**
     * An item or a certificate used on her, which the transcript records as a
     * way straight to the quantity question. Returns false when she has
     * nothing to do with what was handed over, so the caller can give the
     * usual "Nothing interesting happens."
     */
    public static boolean use(Player player, Npc sidney, InvItem item) {
        for (Good good : GOODS) {
            if (item.getID() == good.itemID) {
                Conversation c = new Conversation(player, sidney);
                certifyGood(c, good);
                c.start();
                return true;
            }
            if (item.getID() == good.certID) {
                Conversation c = new Conversation(player, sidney);
                redeemGood(c, good);
                c.start();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ helpers --

    /** The labels for one page, plus the link to the other one. */
    private static String[] labels(int from, int to, boolean certifying, String flip) {
        String[] out = new String[to - from + 1];
        for (int i = from; i < to; ++i) {
            out[i - from] = certifying ? GOODS[i].goodsLabel : GOODS[i].certLabel;
        }
        out[out.length - 1] = flip;
        return out;
    }

    /**
     * A quantity menu cut off at what the player is actually holding, which is
     * how the transcript shows it: someone with one certificate is offered
     * "None thanks." and "One Certificate please" and nothing further.
     */
    private static String[] amounts(String none, String[] steps, int available) {
        List<String> out = new ArrayList<String>(steps.length + 1);
        out.add(none);
        for (int i = 0; i < steps.length && i < available; ++i) {
            out.add(steps[i]);
        }
        return out.toArray(new String[out.size()]);
    }
}
