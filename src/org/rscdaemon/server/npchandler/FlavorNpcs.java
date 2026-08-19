package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * A handful of standalone flavor NPCs with no quest tie, each too small on
 * its own to be worth a whole file. Each was checked against the shipped
 * client strings and every quest file before landing here: genuinely no
 * dialogue anywhere, and no quest that could own it.
 *
 *   Yohnus (622)       Shilo Village. Charges a toll for furnace access.
 *   Sigbert (573)      End of the Yanille agility dungeon. Warns about
 *                      the dangerous Salarain the Twisted.
 *   remsai (397)       Tree Gnome Village. Ambient; no documented purpose
 *                      beyond a greeting -- see the memory note on why
 *                      nothing more specific was invented for him.
 *   Billy Rehnison     Plague City, upstairs from Ted, Martha and Milli.
 *   (448)              Not part of the quest -- PlagueCity.java associates
 *                      446/447/449 but never him -- and the wiki records no
 *                      dialogue of his own, just the vanilla dismissal.
 *   Hans (5)           Lumbridge castle courtyard. The first npc most players
 *                      ever meet, and he had nothing.
 *   Fairy Lunderwin   Zanaris market. Buys cabbages at 100 coins each. She
 *   (219)             appeared in no handler list and no quest at all.
 *   Colonel Radick    West end of Yanille. Challenges you friend or foe, and
 *   (518)             attacks if you answer foe. Also unwired before this.
 *   Head chef (133)    Cooking guild, Lumbridge. The guild door already spoke
 *                      for him; talking to him inside did not.
 *   Brimstail (590)    A cave under the Tree Gnome Stronghold, (739,502) on
 *                      plane 3. Every word recorded of him is a refusal to
 *                      speak -- he is mid-chant and stays that way. He belongs
 *                      to a members' quest that is not written yet; until it
 *                      is, the refusal IS the content, and it is his own rather
 *                      than the generic one.
 */
public class FlavorNpcs implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int COINS = 10;

    public static final int YOHNUS = 622;
    public static final int SIGBERT = 573;
    public static final int REMSAI = 397;
    public static final int BILLY = 448;
    public static final int HANS = 5;
    public static final int HEAD_CHEF = 133;
    public static final int BRIMSTAIL = 590;
    public static final int CAMEL = 13;
    public static final int LUNDERWIN = 219;
    public static final int COLONEL_RADICK = 518;
    public static final int MASTER_CRAFTER = 231;

    /* Mining guild dwarves. Only 191's examine is guild-specific ("A dwarf who
       looks after the mining guild"); 94 and 699 are both "A short angry guy"
       and account for about thirty spawns between them. The transcript records
       the brush-off for the guild dwarf, and says nothing either way about the
       other two -- extending it to them is OURS, on the grounds that a mute npc
       reads as more broken than a curt one, and that the downside if we are
       wrong is two npcs declining to talk slightly more politely than they
       should. Flagged here rather than buried. */
    public static final int DWARF_GUILD = 191;
    public static final int DWARF_ANGRY_A = 94;
    public static final int DWARF_ANGRY_B = 699;

    /* Monks of Zamorak. All three share the examine "An evil cleric", so the
       examine cannot separate them -- our own NPCDef combat stats can, and they
       line up 1:1 with the wiki's three levels:
           140  18/18/22/20  level 19
           139  28/28/32/30  level 29
           293  48/48/52/40  level 47 (members)
       293 is deliberately absent below. His line was never recorded, and giving
       him 139's would turn a recoverable gap into a fabrication that nothing
       downstream could ever tell apart from recovered text. */
    public static final int MONK_OF_ZAMORAK_19 = 140;
    public static final int MONK_OF_ZAMORAK_29 = 139;

    private static final int FURNACE_TOLL = 20;

    /* Fairy Lunderwin's price, and the cabbage she pays it for.
       Jagex shipped two identical cabbage definitions, 18 and 228. 228 is the
       Draynor manor one that Black knight's fortress needs, and Zanaris is a
       long way from Draynor; she takes 18 only, so that a market stall on
       another plane cannot quietly eat a quest item. */
    private static final int CABBAGE = 18;
    private static final int CABBAGE_PRICE = 100;

    /** Skill index for Cooking, as WallObjectAction's guild door uses it. */
    private static final int COOKING = 7;
    private static final int COOKING_GUILD_LEVEL = 32;
    private static final int CHEFS_HAT = 192;

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case YOHNUS:
                yohnus(npc, player);
                return;
            case LUNDERWIN:
                lunderwin(npc, player);
                return;
            case COLONEL_RADICK:
                radick(npc, player);
                return;
            case SIGBERT:
                sigbert(npc, player);
                return;
            case REMSAI:
                new Conversation(player, npc)
                    .player("Hello there.")
                    .npc("Oh, hello. Lovely day for it, isn't it?")
                    .start();
                return;
            case BILLY:
                // Vanilla dismissal, not the generic "<name> is not
                // interested in talking" -- the wiki records him by his
                // short name rather than "Billy Rehnison".
                player.getActionSender().sendMessage("Billy is not interested in talking.");
                return;
            case HANS:
                hans(npc, player);
                return;
            case BRIMSTAIL:
                // Two messages, no speech. "he does not respond" is lowercase
                // mid-sentence in the transcript and marked sic there, so it is
                // Jagex's own and stays as it is.
                new Conversation(player, npc)
                    .player("hello")
                    .message("The gnome is chanting")
                    .message("he does not respond")
                    .start();
                return;
            case CAMEL:
                // The entire Camel transcript is this one message. Capital C
                // on "Camel", and no full stop -- Billy's line above ends in
                // one, and that is not a precedent to copy across. Two
                // different recorded strings, not one string typed twice.
                player.getActionSender().sendMessage("The Camel does not appear interested in talking");
                return;
            case DWARF_GUILD:
            case DWARF_ANGRY_A:
            case DWARF_ANGRY_B:
                player.getActionSender().sendMessage("The dwarf does not appear interested in talking");
                return;
            case MONK_OF_ZAMORAK_19:
                new Conversation(player, npc)
                    .npc("Save your speech for the altar")
                    .start();
                return;
            case MONK_OF_ZAMORAK_29:
                // The space before the question mark is the wiki's and is RSC
                // house style throughout. Do not close it up.
                new Conversation(player, npc)
                    .npc("Who are you to dare speak to the servants of Zamorak ?")
                    .start();
                return;
            case MASTER_CRAFTER:
                masterCrafter(npc, player);
                return;
            case HEAD_CHEF:
                headChef(npc, player);
                return;
        }
    }

    /**
     * Hans, Lumbridge. He opens, three options, one or two lines each, out.
     *
     * The lower-case "am i" and the shouted "HELP HELP!" are both his, verbatim
     * from Transcript:Hans, and neither is a typo of ours.
     *
     * The wiki's article says he "can be persuaded into thinking that players
     * want to kill everyone in the castle, causing him to panic", but the
     * transcript records only the line -- no flee, no alarm, no guards. So the
     * panic here is the shout and nothing more; a behaviour would be invented.
     */
    private void hans(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Hello what are you doing here?")
            .options(new Choice("I'm looking for whoever is in charge of this place",
                                "I have come to kill everyone in this castle",
                                "I don't know. I'm lost. Where am i?") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0: c.npc("Sorry, I don't know where he is right now"); break;
                        case 1: c.npc("HELP HELP!"); break;
                        default: c.npc("You are in Lumbridge Castle"); break;
                    }
                }
            })
            .start();
    }

    /**
     * The head chef, inside the cooking guild.
     *
     * He speaks and ends -- no player lines and no menu in any of the three
     * branches, which is unusual enough to be worth saying out loud so nobody
     * later "completes" it with an options list.
     *
     * Only the qualified branch is attested as a direct conversation; the
     * transcript's other two sections are labelled as attempts to enter, and
     * the guild door in WallObjectAction says those same lines. They are
     * repeated here because the unqualified states are still reachable from
     * inside -- take the hat off after walking in -- and answering with the
     * chef's own words for exactly that situation beats inventing a third
     * thing for him to say. Flagged as the one inference in this handler.
     */
    /**
     * Master Crafter, Crafting guild.
     *
     * His transcript has three blocks, and only one of them belongs here. The
     * other two are the guild's entry gates -- the crafting-40 refusal and the
     * brown-apron refusal -- and both already fire from the guild door, in
     * WallObjectAction case 68. Wiring them here as well would tell a player
     * who is standing at the door the same thing twice, and he is inside the
     * guild anyway, so the only way to be next to him is to have passed both.
     *
     * Note this is the opposite choice from {@link #headChef} above, which does
     * re-check its gates on a direct talk. The difference is deliberate rather
     * than drift: the head chef's own refusals are the only recorded source for
     * that wording, whereas the Master Crafter's are recorded as door text.
     * Neither transcript settles what happens if you somehow talk to the npc
     * from the wrong side, so this is the cheaper guess in each case.
     */
    private void masterCrafter(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Hello welcome to the Crafter's guild")
            .npc("Accomplished crafters all over the land come here")
            .npc("All to use our top notch workshops")
            .start();
    }

    private void headChef(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        if (player.getCurStat(COOKING) < COOKING_GUILD_LEVEL) {
            c.npc("Sorry. Only the finest chefs are allowed in here").start();
            return;
        }
        if (!player.getInventory().wielding(CHEFS_HAT)) {
            c.npc("Where's your chef's hat")
             .npc("You can't come in here unless you're wearing a chef's hat")
             .start();
            return;
        }
        c.npc("Hello welcome to the chef's guild")
         .npc("Only accomplished chefs and cooks are allowed in here")
         .npc("Feel free to use any of our facilities")
         .start();
    }

    private void yohnus(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.player("What's through there?")
         .npc("The furnace, but it'll cost you " + FURNACE_TOLL + " coins to use it.")
         .options(new Choice("Here you go.", "Never mind.") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("Suit yourself.");
                     return;
                 }
                 Player p = c.getPlayer();
                 if (p.getInventory().countId(COINS) < FURNACE_TOLL) {
                     c.npc("Come back when you've got the coin.");
                     return;
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         Player pl = c.getPlayer();
                         pl.getInventory().remove(COINS, FURNACE_TOLL);
                         pl.getActionSender().sendInventory();
                     }
                 });
                 c.npc("In you go, then.");
             }
         });
        c.start();
    }

    private void sigbert(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("Hello.")
            .npc("Careful further in. Salarain the Twisted lurks down there.")
            .npc("He's caught more adventurers than I care to remember.")
            .start();
    }

    /**
     * Fairy Lunderwin, in the Zanaris market.
     *
     * She takes the whole holding in one go, and the transcript is explicit
     * that the sale message and the payment repeat once per cabbage rather
     * than being summed -- so a player carrying nine of them sees nine lines.
     */
    private void lunderwin(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.npc("I am buying cabbage, we have no such thing where I come from")
         .npc("I pay hansomly for this wounderous object")
         .npc("Would 100 gold coins per cabbage be a fair price?");
        if (player.getInventory().countId(CABBAGE) < 1) {
            c.player("Alas I have no cabbages either").start();
            return;
        }
        c.options(new Choice("Yes, I will sell you all my cabbages",
                             "No, I will keep my cabbbages") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    return;
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player p = c.getPlayer();
                        int held = p.getInventory().countId(CABBAGE);
                        for (int i = 0; i < held; i++) {
                            p.getInventory().remove(CABBAGE, 1);
                            p.getInventory().add(new InvItem(COINS, CABBAGE_PRICE));
                            p.getActionSender().sendMessage("You sell a cabbage");
                        }
                        p.getActionSender().sendInventory();
                    }
                })
                 .npc("Good doing buisness with you");
            }
        }.says(1, "No, I will keep my cabbages"))
         .start();
    }

    /**
     * Colonel Radick, at the western end of Yanille.
     *
     * Answering "foe" really does start the fight. The conversation has to be
     * stopped first: {@link Npc#attackPlayer} refuses to touch a player who is
     * busy, and a player in a conversation is busy for its whole length.
     *
     * The menu says "foe" and the player says "Foe" -- the wiki marks the
     * capital {{sic}}, and both are Jagex's.
     */
    private void radick(final Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("Who goes there?")
            .npc("friend or foe?")
            .options(new Choice("Friend", "foe",
                                "Why's this town so heavily defeated?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Ok good to hear it");
                        return;
                    }
                    if (option == 1) {
                        c.npc("Oh righty").then(new Effect() {
                            public void run(Conversation c) {
                                Player p = c.getPlayer();
                                c.stop();
                                npc.attackPlayer(p);
                            }
                        });
                        return;
                    }
                    c.npc("Yanille is on the southwest border of Kandarin")
                     .npc("Beyond here you go into the feldip hills")
                     .npc("Which is major ogre teritory")
                     .npc("Our job is to defend Yanille from the ogres");
                }
            }.says(1, "Foe").says(2, "Why's this town so heavily defended?"))
            .start();
    }
}
