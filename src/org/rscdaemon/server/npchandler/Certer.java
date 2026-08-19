package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.CerterDef;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The ten certificate exchange stalls: Giles, Miles and Niles in Draynor
 * market, Seth in Brimhaven, Owen in Catherby, Orven and Padik in the Fishing
 * Guild, jinno and Watto in Zanaris, and chuck in East Ardougne. Yanille's
 * Sidney Smith is a certer too but shares none of this; see
 * {@link SidneySmith}.
 *
 * Rewritten 2026-08-04 against Transcript:Giles, :Miles, :Niles, :Owen and
 * :Chuck, which agree line for line with one another. Three things changed:
 *
 * <ul>
 * <li>Every message is now Jagex's, down to the lowercase "what sort of ...",
 *     "You exchange your ore for certificates", and the option labels "One",
 *     "two", "Three", "four", "five". The wiki marks that capitalisation
 *     {@literal {{sic}}}, so it is the original's and not a transcription
 *     slip.</li>
 * <li>The sixth options, "All to bank" and "All from bank", are gone. They
 *     were RSCD's own: the vanilla menus offer one to five certificates and
 *     five to twenty-five goods and nothing else. They also amounted to
 *     remote banking from any certer in the game, which is a change to how
 *     the world plays and not a convenience.</li>
 * <li>The wording comes from the def's own fields rather than from one plural
 *     glued onto {@code type}; see {@link CerterDef}.</li>
 * </ul>
 *
 * Certificates stack and the goods do not, so the bag is always the binding
 * limit going that way: twenty-five items need twenty-five free slots.
 * {@code Inventory.add} drops the overflow at the player's feet with a message
 * rather than destroying it, the same as everywhere else in the server.
 */
public class Certer implements NpcHandler {

    /** "How many certificates do you wish to trade in?" */
    private static final String[] CERT_AMOUNTS = {"One", "two", "Three", "four", "five"};

    /** "How many ores do you wish to trade in?" */
    private static final String[] GOODS_AMOUNTS = {"five", "ten", "Fifteen", "Twenty", "Twentyfive"};

    public void handleNpc(Npc npc, Player player) throws Exception {
        final CerterDef def = EntityHandler.getCerterDef(npc.getID());
        if (def == null) {
            return;
        }

        new Conversation(player, npc)
            .npc("Welcome to my " + def.getType() + " exchange stall")
            // picker, not options: the third entry is the one place in the
            // game where the menu label and the line the player actually
            // speaks are different words -- "stall" in the list, "store" out
            // loud -- so the echo cannot produce it and each branch says its
            // own line below.
            .picker(new Choice("I have some certificates to trade in",
                               "I have some " + def.getGoods() + " to trade in",
                               "What is " + def.getArticle() + " " + def.getType() + " exchange stall?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        certificatesToGoods(c, def);
                    } else if (option == 1) {
                        goodsToCertificates(c, def);
                    } else {
                        explain(c, def);
                    }
                }
            })
            .start();
    }

    /**
     * "What is an ore exchange stall?" -- the third option, which this server
     * did not have at all. Seven lines, identical in structure across
     * Transcript:Giles, :Miles, :Niles, :Owen and :Chuck with only the noun
     * changing, so five independent captures agree on every word of it.
     *
     * The player asks about a "stall" and then says "store". That is Jagex's,
     * in all five transcripts, and is not corrected here.
     */
    private static void explain(Conversation c, CerterDef def) {
        String plural = def.getPlural();
        c.player("What is " + def.getArticle() + " " + def.getType() + " exchange store?")
         .npc("You may exchange your " + plural + " here")
         .npc("For certificates which are light and easy to carry")
         .npc("You can carry many of these certificates at once unlike " + plural)
         .npc("5 " + plural + " will give you one certificate")
         .npc("You may also redeem these certificates here for " + plural + " again")
         .npc("The advantage of doing this is")
         .npc("You can trade large amounts of " + plural + " with other players quickly and safely");
    }

    /** "I have some certificates to trade in": five goods per certificate. */
    private static void certificatesToGoods(Conversation c, final CerterDef def) {
        c.player("I have some certificates to trade in")
         .message("what sort of certificate do you wish to trade in?")
         .picker(new Choice(def.getCertNames()) {
            public void picked(final int index, Conversation c) {
                c.message("How many certificates do you wish to trade in?")
                 .picker(new Choice(CERT_AMOUNTS) {
                    public void picked(final int amount, Conversation c) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                redeem(c.getPlayer(), def, index, amount + 1);
                            }
                        });
                    }
                });
            }
        });
    }

    /** "I have some ore to trade in": one certificate per five goods. */
    private static void goodsToCertificates(Conversation c, final CerterDef def) {
        c.player("I have some " + def.getGoods() + " to trade in")
         .message("what sort of " + def.getGoods() + " do you wish to trade in?")
         .picker(new Choice(def.getCertNames()) {
            public void picked(final int index, Conversation c) {
                c.message("How many " + def.getCounted() + " do you wish to trade in?")
                 .picker(new Choice(GOODS_AMOUNTS) {
                    public void picked(final int amount, Conversation c) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                issue(c.getPlayer(), def, index, amount + 1);
                            }
                        });
                    }
                });
            }
        });
    }

    /** Hand over {@code certs} certificates, take back five goods for each. */
    private static void redeem(Player player, CerterDef def, int index, int certs) {
        int certID = def.getCertID(index);
        int itemID = def.getItemID(index);
        if (certID < 0) {
            return;
        }

        if (player.getInventory().countId(certID) < certs) {
            player.getActionSender().sendMessage("You don't have that many certificates");
            return;
        }
        if (player.getInventory().remove(certID, certs) < 0) {
            return;
        }
        // One item per bag slot: nothing certifiable stacks, so this cannot be
        // a single add() of certs * 5.
        for (int i = 0; i < certs * 5; ++i) {
            player.getInventory().add(new InvItem(itemID, 1));
        }
        player.getActionSender().sendMessage("You exchange your certificates for " + def.getGoods());
        player.getActionSender().sendInventory();
    }

    /** Take {@code certs * 5} goods, hand back that many certificates. */
    private static void issue(Player player, CerterDef def, int index, int certs) {
        int certID = def.getCertID(index);
        int itemID = def.getItemID(index);
        if (certID < 0) {
            return;
        }

        int goods = certs * 5;
        if (player.getInventory().countId(itemID) < goods) {
            player.getActionSender().sendMessage("You don't have that " + def.getShortfall());
            return;
        }
        // remove(id, amount) only honours the amount for a stackable item, and
        // nothing certifiable is stackable, so this has to go one at a time.
        for (int i = 0; i < goods; ++i) {
            if (player.getInventory().remove(itemID, 1) < 0) {
                break;
            }
        }
        player.getInventory().add(new InvItem(certID, certs));
        player.getActionSender().sendMessage("You exchange your " + def.getGoods() + " for certificates");
        player.getActionSender().sendInventory();
    }
}
