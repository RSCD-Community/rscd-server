
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
import org.rscdaemon.server.util.DataConversions;

/**
 * The Gnome Bar, second floor of the Grand Tree. Blurberry (534) owns it; the
 * Blurberry barman (580) runs the shop counter and is handled separately in
 * npchandler/GnomeShops.java.
 *
 * Blurberry had nothing wired to him at all before this. He offers a part
 * time job, hands over the gnome cocktail guide (851), auditions the player
 * with five cocktails in a fixed order, and after that hands out repeatable
 * orders for gold and Cooking experience. The mixing itself is
 * model/GnomeCooking.java; this class only asks for drinks and pays for them.
 *
 *     stage -1/0  never spoken to
 *     stage 1     took the job, has the cocktail guide
 *     stage 2..6  auditioning -- see AUDITION, one entry per stage
 *     stage 7     passed the audition, no order outstanding
 *     stage 8+    ORDERS[stage - 8] is outstanding
 *
 * Every line of dialogue below is verbatim from Transcript:Blurberry,
 * including its misspellings ("if your looking for a cocktail", "i dont know
 * what that is", "hi, are you readv make your first cocktail?").
 *
 * Two things are ours, invented rather than recovered. The
 * first is that each audition handout includes a cocktail glass: Blurberry
 * hands over "a cocktail shaker, a glass and a knife" once, at the start, and
 * the transcript never says he gives another, but he keeps the glass every
 * time he takes a drink, so without one per handout the audition dead-ends at
 * the second cocktail. The second is the blurberry special's ingredient list,
 * which the transcript summarises as "here's your ingredients" without
 * enumerating it -- the list below is the recipe's own requirements.
 */
public class GnomeBar extends Quest {

    public final static int UID = Quests.GNOME_BAR;

    private static final int BLURBERRY = 534;

    private static final int COINS = 10;
    private static final int COOKING = 7;

    private static final int COCKTAIL_GUIDE = 851, COCKTAIL_GLASS = 833, SHAKER = 834, KNIFE = 13;
    private static final int LEMON = 855, ORANGE = 857, LIME = 863, PINEAPPLE = 861;
    private static final int VODKA = 869, GIN = 870, BRANDY = 876, WHISKY = 868;
    private static final int MILK = 22, CREAM = 871, EQUA_LEAVES = 873, DWELLBERRIES = 765;
    private static final int CHOCOLATE_BAR = 337, CHOCOLATE_DUST = 772;

    private static final int FRUIT_BLAST = 866, DRUNK_DRAGON = 872, SGG = 874;
    private static final int CHOC_SATURDAY = 875, BLURBERRY_SPECIAL = 877;
    private static final int WIZARD_BLIZZARD = 878, PINEAPPLE_PUNCH = 879;

    /* ------------------------------------------------------------ the audition */

    /**
     * One of the five drinks Blurberry asks for in turn. The three text
     * fields are the ask, the nag when the player comes back empty handed,
     * and the reaction on delivery -- all held together so the transcript can
     * be read straight down the table.
     */
    private static final class Step {
        final int drink;
        final int coins;
        final int exp;
        final int[] handout;
        final String[] ask;
        final String[] nag;
        final String[] taken;

        Step(int drink, int coins, int exp, int[] handout,
                String[] ask, String[] nag, String[] taken) {
            this.drink = drink;
            this.coins = coins;
            this.exp = exp;
            this.handout = handout;
            this.ask = ask;
            this.nag = nag;
            this.taken = taken;
        }
    }

    private static final Step[] AUDITION = new Step[] {

        new Step(FRUIT_BLAST, 0, 0,
            new int[] { LEMON, LEMON, ORANGE, PINEAPPLE, SHAKER, COCKTAIL_GLASS, KNIFE },
            new String[] {
                "ok then, to start with make me a fruit blast",
                "here, you'll need these ingredients",
                "but I'm afraid i can't give you any more if you mess up" },
            new String[] {
                "so where's my fruit blast",
                "i don't know what you have there but it's no fruit blast" },
            new String[] {
                "hmmm... not bad, not bad at all" }),

        new Step(DRUNK_DRAGON, 1, 40,
            new int[] { VODKA, GIN, DWELLBERRIES, PINEAPPLE, CREAM, COCKTAIL_GLASS, KNIFE },
            new String[] {
                "now can you make me a drunk dragon",
                "here's what you need",
                "i'm afraid i won't be able to give you anymore if you make a mistake though",
                "let me know when it's done" },
            new String[] {
                "hello again traveller",
                "how did you do?",
                "i dont know what that is but it's no drunk dragon" },
            new String[] {
                "woooo, that's some good stuff",
                "i can sell that",
                "there you go, your share of the profit" }),

        new Step(SGG, 1, 40,
            new int[] { LIME, LIME, LIME, LIME, VODKA, EQUA_LEAVES, COCKTAIL_GLASS },
            new String[] {
                "okay then now i need an s g g",
                "a short green guy, and don't bring me a gnome",
                "here's all you need" },
            new String[] {
                "so have you got my s g g?",
                "i dont know what that is but it's no s g g" },
            new String[] {
                "hmmm, not bad, not bad at all",
                "i can sell that",
                "there you go, that's your share" }),

        new Step(CHOC_SATURDAY, 0, 40,
            new int[] { WHISKY, MILK, EQUA_LEAVES, CHOCOLATE_BAR, CREAM, CHOCOLATE_DUST,
                        COCKTAIL_GLASS },
            new String[] {
                "you doing quite well, i'm impressed",
                "ok let's try a chocolate saturday, i love them",
                "here's your ingredients" },
            new String[] {
                "hello, how did it go with the choc saturday",
                "ok, it's one choc saturday i need",
                "well let me know when you're done" },
            new String[] {
                "that's blurberry-tastic",
                "you're quite a bartender" }),

        new Step(BLURBERRY_SPECIAL, 0, 0,
            new int[] { VODKA, GIN, BRANDY, LEMON, LEMON, LEMON, ORANGE, ORANGE, LIME,
                        EQUA_LEAVES, COCKTAIL_GLASS, KNIFE },
            new String[] {
                "okay ,lets test you once more",
                "try and make me a blurberry special",
                "then we'll see if you have what it takes",
                "here's your ingredients" },
            new String[] {
                "so how did you do",
                "I need one blurberry special",
                "well let me know when you're done" },
            new String[] {
                "well i never, incredible",
                "not many manage to get that right, but this is perfect" }),
    };

    /* -------------------------------------------------------------- the orders */

    /**
     * A repeatable order. The wording is how Blurberry reads it out, which is
     * not always how the item is named -- "an s.g.g.", "two choc saturdays".
     *
     * The three the transcript records are first, in the order it records
     * them; the last three come from the minigame's own wiki page, which
     * lists more orders than any one session observed. Where the two sources
     * disagree the transcript wins: it puts the first order's pay at 170 gold
     * where the table says 179, and 170 is what the recorded session was
     * actually handed.
     */
    private static final class Order {
        final int[] items;
        final int[] amounts;
        final int coins;
        final int exp;
        final String[] wording;

        Order(int coins, int exp, int[] items, int[] amounts, String... wording) {
            this.coins = coins;
            this.exp = exp;
            this.items = items;
            this.amounts = amounts;
            this.wording = wording;
        }
    }

    private static final Order[] ORDERS = new Order[] {

        new Order(170, 135,
            new int[] { WIZARD_BLIZZARD, PINEAPPLE_PUNCH, BLURBERRY_SPECIAL, FRUIT_BLAST },
            new int[] { 1, 1, 1, 2 },
            "ok, i need one wizard blizzard,one pineapple punch..",
            "..one blurberry special and two fruit blasts"),

        new Order(70, 105,
            new int[] { CHOC_SATURDAY }, new int[] { 2 },
            "ok, i need two choc saturdays please"),

        new Order(150, 90,
            new int[] { WIZARD_BLIZZARD, SGG }, new int[] { 2, 1 },
            "ok, i need two wizard blizzards and an s.g.g."),

        new Order(120, 90,
            new int[] { SGG, BLURBERRY_SPECIAL }, new int[] { 2, 1 },
            "ok, i need two s.g.g.'s and a blurberry special"),

        new Order(10, 60,
            new int[] { FRUIT_BLAST }, new int[] { 1 },
            "ok, i just need one fruit blast"),

        new Order(100, 90,
            new int[] { PINEAPPLE_PUNCH, CHOC_SATURDAY, DRUNK_DRAGON }, new int[] { 1, 1, 1 },
            "ok, i need one pineapple punch, one choc saturday..",
            "..and one drunk dragon"),
    };

    private static final int FIRST_AUDITION_STAGE = 2;
    private static final int PASSED = 7;
    private static final int FIRST_ORDER_STAGE = 8;

    public GnomeBar(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Gnome Bar");
        setFinalStage(Integer.MIN_VALUE); // a job, not a quest -- never completes

        /* No 2003 manual page survives for this minigame; description is ours. */
        describe("Blurberry needs bar staff. Pass his five-cocktail audition and he will keep you in part time work, mixing drink orders for gold and cooking experience.");
        setStartPoint("The Gnome Bar, second floor of the Grand Tree");
        setSpeakTo("Blurberry");
        rewardOther("Gold and cooking experience for every cocktail order filled");

        associateNpc(BLURBERRY);
    }

    public void completeQuest() {
    }

    private int progress() {
        int s = getStage();
        return s < 0 ? 0 : s;
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() != BLURBERRY) {
            return;
        }
        int stage = progress();
        if (stage == 0) {
            offerJob(npc);
        } else if (stage == 1) {
            startAudition(npc);
        } else if (stage < PASSED) {
            audition(npc, AUDITION[stage - FIRST_AUDITION_STAGE], stage);
        } else if (stage == PASSED) {
            offerOrder(npc);
        } else {
            deliverOrder(npc, ORDERS[stage - FIRST_ORDER_STAGE]);
        }
    }

    /* ------------------------------------------------------------------ steps */

    private void offerJob(Npc npc) {
        new Conversation(owner, npc)
            .player("hello")
            .npc("well hello there traveller")
            .npc("if your looking for a cocktail the barman will happily make you one")
            .player("he looks pretty busy")
            .npc("I know,i just cant find any skilled staff")
            .npc("I don't suppose your looking for some part time work?")
            .npc("the pay isn't great but it's a good way to meet people")
            .options(new Choice("no thanks i prefer to stay this side of the bar",
                                "ok then i'll give it a go") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        return;
                    }
                    c.npc("excellent");
                    c.npc("it's not an easy job, ill have to test you first");
                    c.npc("i'm sure you'll be great though");
                    c.npc("here, take this cocktail guide");
                    c.give(new InvItem(COCKTAIL_GUIDE, 1));
                    c.message("blurberry gives you a cocktail guide");
                    c.npc("the book tells you how to make all the cocktails we serve");
                    c.npc("I'll tell you what i need and you can make them");
                    c.player("sounds easy enough");
                    c.npc("take a look at the book and then come and talk to me");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            setStage(1);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * The first cocktail is asked for the moment the player comes back --
     * "talking to blurberry again, even without reading" is how the
     * transcript labels it, so the book is never actually checked.
     */
    private void startAudition(Npc npc) {
        Conversation c = new Conversation(owner, npc);
        c.player("hello blurberry")
         .npc("hi, are you readv make your first cocktail?")
         .player("absolutely");
        ask(c, AUDITION[0], FIRST_AUDITION_STAGE);
        c.start();
    }

    private void ask(Conversation c, final Step step, final int stage) {
        for (int i = 0; i < step.ask.length; i++) {
            c.npc(step.ask[i]);
        }
        for (int i = 0; i < step.handout.length; i++) {
            c.give(new InvItem(step.handout[i], 1));
        }
        c.message("blurberry gives you the ingredients");
        c.npc("let me know when you're done");
        c.then(new Effect() {
            public void run(Conversation c) {
                setStage(stage);
            }
        });
    }

    private void audition(Npc npc, final Step step, final int stage) {
        Conversation c = new Conversation(owner, npc);
        if (owner.getInventory().countId(step.drink) < 1) {
            for (int i = 0; i < step.nag.length; i++) {
                c.npc(step.nag[i]);
            }
            c.start();
            return;
        }
        c.player("here you go");
        c.take(step.drink, 1);
        c.message("you give blurberry the " + itemName(step.drink));
        c.message("he takes a sip");
        for (int i = 0; i < step.taken.length; i++) {
            c.npc(step.taken[i]);
        }
        if (step.coins > 0) {
            c.give(new InvItem(COINS, step.coins));
            c.message("blurberry gives you " + step.coins
                    + (step.coins == 1 ? " gold coin" : " gold coins"));
        }
        if (step.exp > 0) {
            final int exp = step.exp;
            c.then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().incExp(COOKING, exp, true);
                    c.getPlayer().getActionSender().sendStat(COOKING);
                }
            });
        }
        int next = stage + 1;
        if (next < PASSED) {
            ask(c, AUDITION[next - FIRST_AUDITION_STAGE], next);
        } else {
            c.npc("It would be an honour to have you on the team");
            c.player("thanks");
            c.npc("now if you ever want to make some money");
            c.npc("or want to improve your cooking skills just come and see me");
            c.npc("I'll tell you what drinks we need, and if you can, you make them");
            c.player("what about ingredients?");
            c.npc("I'm afraid i can't give you anymore for free");
            c.npc("but you can buy them from heckel funch the grocer");
            c.npc("I'll always pay you more for the cocktail than you paid for the ingredients");
            c.npc("and it's a great way to learn how to prepare food and drink");
            c.then(new Effect() {
                public void run(Conversation c) {
                    setStage(PASSED);
                }
            });
        }
        c.start();
    }

    /* ----------------------------------------------------------- the orders */

    private void offerOrder(Npc npc) {
        new Conversation(owner, npc)
            .player("hello again blurberry")
            .npc("well hello traveller")
            .npc("i'm quite busy as usual, any chance you could help")
            .options(new Choice("I'm quite busy myself, sorry", "ok then, what do you need") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("that's ok, come back when you're free");
                        return;
                    }
                    final int pick = DataConversions.random(0, ORDERS.length - 1);
                    Order order = ORDERS[pick];
                    for (int i = 0; i < order.wording.length; i++) {
                        c.npc(order.wording[i]);
                    }
                    c.player("i'll do my best");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            setStage(FIRST_ORDER_STAGE + pick);
                        }
                    });
                }
            })
            .start();
    }

    private void deliverOrder(Npc npc, final Order order) {
        Conversation c = new Conversation(owner, npc);
        c.player("hi").npc("have you made the order?");
        if (!holdingOrder(order)) {
            c.player("not yet");
            for (int i = 0; i < order.wording.length; i++) {
                c.npc(order.wording[i]);
            }
            c.npc("let me know when you're done");
            c.start();
            return;
        }
        c.player("here you go");
        for (int i = 0; i < order.items.length; i++) {
            c.take(order.items[i], order.amounts[i]);
        }
        c.message("you give blurberry the cocktails");
        c.npc("that's excellent, here's your share of the profit");
        c.give(new InvItem(COINS, order.coins));
        c.message("blurberry gives you " + order.coins + " gold coins");
        final int exp = order.exp;
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().incExp(COOKING, exp, true);
                c.getPlayer().getActionSender().sendStat(COOKING);
                setStage(PASSED);
            }
        });
        c.npc("could you make me another order");
        c.options(new Choice("I'm quite busy myself, sorry", "ok then, what do you need") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("that's ok, come back when you're free");
                    return;
                }
                final int pick = DataConversions.random(0, ORDERS.length - 1);
                Order next = ORDERS[pick];
                for (int i = 0; i < next.wording.length; i++) {
                    c.npc(next.wording[i]);
                }
                c.player("i'll do my best");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        setStage(FIRST_ORDER_STAGE + pick);
                    }
                });
            }
        });
        c.start();
    }

    private boolean holdingOrder(Order order) {
        for (int i = 0; i < order.items.length; i++) {
            if (owner.getInventory().countId(order.items[i]) < order.amounts[i]) {
                return false;
            }
        }
        return true;
    }

    private static String itemName(int id) {
        return org.rscdaemon.server.entityhandling.EntityHandler.getItemDef(id).getName();
    }
}
