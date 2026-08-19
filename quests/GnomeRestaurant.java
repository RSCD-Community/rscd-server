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
 * Giannes tree gnome cuisine, second floor of the Grand Tree. Aluft Gianne
 * (536) owns it; his Gnome waiters (581) run the counter and are handled
 * separately in npchandler/GnomeShops.java.
 *
 * The same shape as quests/GnomeBar.java and for the same reasons -- Aluft
 * had nothing wired to him before this either. He offers a cook's position,
 * hands over the gianne cook book (899), auditions the player with five
 * dishes in a fixed order, and then hands out repeatable orders for gold and
 * Cooking experience. The cooking itself is model/GnomeCooking.java.
 *
 *     stage -1/0  never spoken to
 *     stage 1     took the job, has the cook book
 *     stage 2..6  auditioning -- see AUDITION, one entry per stage
 *     stage 7     passed the audition, no order outstanding
 *     stage 8+    ORDERS[stage - 8] is outstanding
 *
 * Dialogue is verbatim from Transcript:Aluft Gianne, misspellings included
 * ("eat green, eat gnome cruisine", "i'm afraid all are toads legs are served
 * fresh", "no luck so for").
 *
 * Two things are ours, invented rather than recovered. The
 * worm hole handout is one: the transcript says only "here's everything else
 * you'll need" without listing it, so the list below is what the recipe
 * needs minus the worms he tells the player to catch. The other is the
 * experience figures. The minigame's wiki page gives them in quarter points
 * -- 106.25, 168.75, 137.5 -- because that is how the real server stored
 * experience; this one stores whole points, the same as every cooking and
 * fishing figure in the def data, so each is rounded to the nearest whole.
 */
public class GnomeRestaurant extends Quest {

    public final static int UID = Quests.GNOME_RESTAURANT;

    private static final int ALUFT_GIANNE = 536;

    private static final int COINS = 10;
    private static final int COOKING = 7;

    private static final int COOK_BOOK = 899, GIANNE_DOUGH = 881;
    private static final int TOMATO = 320, CHEESE = 319, ONION = 241;
    private static final int EQUA_LEAVES = 873, GNOME_SPICE = 898;
    private static final int CHOCOLATE_BAR = 337, CHOCOLATE_DUST = 772, CREAM = 871;

    private static final int CHEESE_TOM_BATTA = 901, TOAD_BATTA = 902, WORM_BATTA = 904;
    private static final int FRUIT_BATTA = 905, VEG_BATTA = 906;
    private static final int CHOC_BOMB = 907, VEGBALL = 908, WORM_HOLE = 909, TANGLED_TOADS = 910;
    private static final int CHOC_CRUNCHIES = 911, WORM_CRUNCHIES = 912;
    private static final int TOAD_CRUNCHIES = 913, SPICE_CRUNCHIES = 914;
    private static final int TOAD_LEGS = 896;

    /* ------------------------------------------------------------ the audition */

    /**
     * One dish of the audition.
     *
     * The line arrays are speaker-tagged, because every one of these blocks
     * runs back and forth between Aluft and the player and a plain array of
     * npc lines cannot say that. The tags:
     *
     *     "P:"  the player says the rest of it
     *     "M:"  a grey narration line
     *     "*"   hand the ingredients over here, silently
     *     none  Aluft says it
     *
     * Before this the greeting, the excuse, the hand-over line and the closing
     * line were all one hardcoded wording shared by all five dishes -- "hi
     * aluft" / "here you go" / "let me know how you get on" -- and the player's
     * side of each exchange was missing entirely. Aluft has a different opening
     * for every dish and makes a running joke of the player calling him "mr
     * gianne", which is the thing that most obviously went.
     *
     * @param ask      asking for the dish, through to handing the ingredients
     *                 over and sending the player off
     * @param greet    the player's hello and Aluft's "how did you get on?"
     * @param nag      the not-done-yet branch, starting with the player's excuse
     * @param handOver the player's one line when they do have it
     * @param taken    the two narration lines and Aluft's verdict
     */
    private static final class Step {
        final int dish;
        final int[] handout;
        final String[] ask;
        final String[] greet;
        final String[] nag;
        final String handOver;
        final String[] taken;

        Step(int dish, int[] handout, String[] ask, String[] greet, String[] nag,
             String handOver, String[] taken) {
            this.dish = dish;
            this.handout = handout;
            this.ask = ask;
            this.greet = greet;
            this.nag = nag;
            this.handOver = handOver;
            this.taken = taken;
        }
    }

    private static final Step[] AUDITION = new Step[] {

        new Step(CHEESE_TOM_BATTA,
            new int[] { TOMATO, CHEESE, EQUA_LEAVES, GIANNE_DOUGH },
            new String[] {
                "but we'll start with something simple",
                "can you make me a cheese and tomato gnome batta",
                "here's what you need",
                "*",
                "M:aluft gives you one tomato, some cheese...",
                "M:...some equa leaves and some plain dough",
                "P:thanks",
                "Let me know how you get on" },
            new String[] {
                "P:hi mr gianne",
                "call me aluft",
                "P:ok",
                "so how did you get on?" },
            new String[] {
                "P:erm.. not quite done yet",
                "ok, let me know when you are",
                "i need one cheese and tomato batta" },
            "no problem, it was easy",
            new String[] {
                "M:you give aluft the gnome batta",
                "M:he takes a bite",
                "not bad...not bad at all" }),

        new Step(CHOC_BOMB,
            new int[] { CHOCOLATE_BAR, CHOCOLATE_BAR, CHOCOLATE_BAR, CHOCOLATE_BAR,
                        EQUA_LEAVES, CHOCOLATE_DUST, GIANNE_DOUGH, CREAM, CREAM },
            new String[] {
                "ok now for something a little harder",
                "try and make me a choc bomb.. they're my favorite",
                "here's what you need",
                "*",
                "M:aluft gives you four bars of chocolate",
                "M:some equa leaves, some chocolate dust...",
                "M:...some gianne dough and some cream",
                "P:ok aluft, i'll be back soon",
                "good stuff" },
            new String[] {
                "P:hi aluft",
                "hello there, how did you get on" },
            new String[] {
                "P:i haven't made it yet",
                "just follow the instructions carefully",
                "i need one choc bomb" },
            "here you go",
            new String[] {
                "M:you give aluft the choc bomb",
                "M:he takes a bite",
                "yes, yes, yes, that's superb",
                "i'm really impressed",
                "P:i'm glad" }),

        new Step(TOAD_BATTA,
            new int[] { GIANNE_DOUGH, EQUA_LEAVES, GNOME_SPICE },
            new String[] {
                "ok then, now can you make me a toad batta",
                "here's what you need",
                "*",
                "M:mr gianne gives you some dough, some equaleaves...",
                "M:...and some gnome spice",
                // "all are toads legs" is his, not a typo of ours.
                "i'm afraid all are toads legs are served fresh",
                "P:nice!",
                "so you'll need to go to the swamp on ground level",
                "and catch a toad",
                "let me know when the batta's ready" },
            new String[] {
                "P:hi mr gianne",
                "aluft",
                "P:sorry, aluft",
                "so where's my toad batta?" },
            new String[] {
                "P:i'm not done yet",
                "ok, quick as you can though",
                "P:no problem" },
            "here you go, easy",
            new String[] {
                "M:you give mr gianne the toad batta",
                "M:he takes a bite",
                "ooh, that's some good toad",
                "very nice" }),

        new Step(WORM_HOLE,
            new int[] { GIANNE_DOUGH, ONION, ONION, GNOME_SPICE, EQUA_LEAVES },
            new String[] {
                "let's see if you can make a worm hole",
                // Run together as one word by the player and not by Aluft.
                "P:a wormhole?",
                "yes, it's in the cooking guide i gave you",
                "you'll have to get the worms from the swamp",
                "but here's everything else you'll need",
                /* The only one of the five with no narration for the handout.
                   The transcript records none, so none is printed. */
                "*",
                "let me know when your done" },
            new String[] {
                "P:hello again aluft",
                "hello traveller, how did you do?" },
            new String[] {
                "P:i'm not done yet",
                "ok, quick as you can though",
                "i need one worm hole",
                "P:no problem" },
            "here, see what you think",
            new String[] {
                "M:you give mr gianne the worm hole",
                "M:he takes a bite",
                "hmm, that's actually really good" }),

        new Step(TOAD_CRUNCHIES,
            new int[] { GIANNE_DOUGH, EQUA_LEAVES },
            new String[] {
                "how about you make me some toad crunchies for desert",
                "then i'll decide whether i can take you on",
                "P:toad crunchies?",
                "that's right, here's all you need",
                "except the toad",
                "*",
                "M:mr gianne gives you some gianne dough and some equa leaves",
                "let me know when your done" },
            /* The wiki records the player's hello here as 'hi aluft"', with a
               stray quotation mark it marks {{sic}}. A trailing quote is what a
               transcription slip looks like, not what a chat line looks like,
               so it is dropped rather than reproduced. */
            new String[] {
                "P:hi aluft",
                "hello, how are you getting on?" },
            new String[] {
                "P:no luck so for",
                "ok then but don't take too long",
                "i need one toad crunchie" },
            "here, try it",
            new String[] {
                "M:you give mr gianne the toad crunchie",
                "M:he takes a bite",
                "well for a human you certainly can cook",
                "i'd love to have you on the team" }),
    };

    /* -------------------------------------------------------------- the orders */

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

    /**
     * The nine orders the minigame's wiki page records. Only the second is in
     * the transcript, and its wording is Aluft's own; the other eight are read
     * out in the same shape.
     */
    private static final Order[] ORDERS = new Order[] {

        new Order(75, 169,
            new int[] { CHOC_BOMB, CHOC_CRUNCHIES, TOAD_CRUNCHIES }, new int[] { 1, 2, 2 },
            "ok, i need a choc bomb, two choc crunchies and two toad crunchies"),

        new Order(45, 106,
            new int[] { WORM_BATTA, TOAD_BATTA, VEG_BATTA }, new int[] { 2, 1, 1 },
            "ok, i need two worm battas, a toad batta and a veg batta"),

        new Order(30, 75,
            new int[] { CHOC_CRUNCHIES }, new int[] { 2 },
            "ok, i just need two choc crunchies"),

        new Order(45, 106,
            new int[] { CHOC_BOMB, CHOC_CRUNCHIES }, new int[] { 1, 2 },
            "ok, i need a choc bomb and two choc crunchies"),

        new Order(45, 106,
            new int[] { VEG_BATTA, WORM_HOLE }, new int[] { 2, 1 },
            "ok, i need two veg battas and a worm hole"),

        new Order(45, 106,
            new int[] { VEGBALL, TOAD_LEGS, WORM_HOLE }, new int[] { 1, 1, 1 },
            "ok, i need a veg ball, some toads legs and a worm hole"),

        new Order(60, 138,
            new int[] { CHEESE_TOM_BATTA, VEGBALL, WORM_CRUNCHIES }, new int[] { 1, 1, 2 },
            "ok, i need a cheese and tomato batta, a veg ball..",
            "..and two worm crunchies"),

        new Order(45, 106,
            new int[] { SPICE_CRUNCHIES, FRUIT_BATTA, CHOC_BOMB, VEGBALL },
            new int[] { 2, 1, 1, 1 },
            "ok, i need two spice crunchies, a fruit batta..",
            "..a choc bomb and a veg ball"),

        new Order(45, 106,
            new int[] { TANGLED_TOADS, WORM_CRUNCHIES }, new int[] { 1, 2 },
            "ok, i need a tangled toads legs and two worm crunchies"),
    };

    private static final int FIRST_AUDITION_STAGE = 2;
    private static final int PASSED = 7;
    private static final int FIRST_ORDER_STAGE = 8;

    public GnomeRestaurant(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Gnome Restaurant");
        setFinalStage(Integer.MIN_VALUE); // a job, not a quest -- never completes

        /* No 2003 manual page survives for this minigame; description is ours. */
        describe("Aluft Gianne has a cook's position open at his tree gnome restaurant. Prove yourself on five gnome dishes and he will pay you for every meal order you cook after that.");
        setStartPoint("Giannes tree gnome cuisine, second floor of the Grand Tree");
        setSpeakTo("Aluft Gianne");
        rewardOther("Gold and cooking experience for every meal order cooked");

        associateNpc(ALUFT_GIANNE);
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
        if (npc.getID() != ALUFT_GIANNE) {
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
            .npc("well hello there,you hungry..")
            .npc("you come to the right place")
            .npc("eat green, eat gnome cruisine")
            .npc("my waiter will be glad to take your order")
            .player("thanks")
            .npc("on the other hand if you looking for some work")
            .npc("i have a cook's position available")
            .options(new Choice("no thanks i'm no cook", "ok i'll give it a go") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("in that case please, eat and enjoy");
                        return;
                    }
                    c.npc("well that's great");
                    c.npc("of course i'll have to see what you're like first");
                    c.npc("here, have a look at our menu");
                    c.give(new InvItem(COOK_BOOK, 1));
                    c.message("Aluft gives you a cook book");
                    c.npc("when you've had a look come back...");
                    c.npc("... and i'll let you prepare a few dishes");
                    c.player("good stuff");
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
     * "Talking to him whether you have read the book or not" -- the first dish
     * is asked for on the next hello, the book is never checked.
     */
    private void startAudition(Npc npc) {
        Conversation c = new Conversation(owner, npc);
        c.player("hi mr gianne")
         .npc("hello my good friend")
         .npc("what did you think")
         .player("I'm not too sure about toads legs")
         .npc("they're a gnome delicacy, you'll love them");
        ask(c, AUDITION[0], FIRST_AUDITION_STAGE);
        c.start();
    }

    /**
     * Play a speaker-tagged block. See {@link Step} for what the tags mean; the
     * "*" entry is the only one that is not a line at all, and it is where the
     * ingredients change hands.
     */
    private void say(Conversation c, String[] lines, Step step) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if ("*".equals(line)) {
                for (int j = 0; j < step.handout.length; j++) {
                    c.give(new InvItem(step.handout[j], 1));
                }
            } else if (line.startsWith("P:")) {
                c.player(line.substring(2));
            } else if (line.startsWith("M:")) {
                c.message(line.substring(2));
            } else {
                c.npc(line);
            }
        }
    }

    private void ask(Conversation c, final Step step, final int stage) {
        say(c, step.ask, step);
        c.then(new Effect() {
            public void run(Conversation c) {
                setStage(stage);
            }
        });
    }

    private void audition(Npc npc, final Step step, final int stage) {
        Conversation c = new Conversation(owner, npc);
        say(c, step.greet, step);
        if (owner.getInventory().countId(step.dish) < 1) {
            say(c, step.nag, step);
            c.start();
            return;
        }
        c.player(step.handOver);
        c.take(step.dish, 1);
        say(c, step.taken, step);
        int next = stage + 1;
        if (next < PASSED) {
            ask(c, AUDITION[next - FIRST_AUDITION_STAGE], next);
        } else {
            c.npc("if you ever want to make some money");
            c.npc("or want to improve your cooking skills just come and see me");
            c.npc("i'll tell you what meals i need, and if you can, you make them");
            c.player("what about ingredients?");
            c.npc("well you know where to find toads and worms");
            c.npc("you can buy the rest from hudo glenfad the grocer");
            c.npc("i'll always pay you much more for the meal than you paid for the ingredients");
            c.npc("and it's a great way to improve your cooking skills");
            c.then(new Effect() {
                public void run(Conversation c) {
                    setStage(PASSED);
                }
            });
        }
        c.start();
    }

    /* ----------------------------------------------------------- the orders */

    /**
     * The refusal's menu entry is not recoverable. The wiki records it twice --
     * once on this offer and once on the repeat below -- and misspells his name
     * a different way each time, "sorry alufy" and "sorry alfut". They cannot
     * both be Jagex's, and nothing says which one is the transcription slip. The
     * spoken line is "sorry aluft" in both places, which is the only wording two
     * independent records agree on, so it is the one used for the label too.
     */
    private void offerOrder(Npc npc) {
        new Conversation(owner, npc)
            .player("hello again aluft")
            .npc("well hello there traveller")
            .npc("have you come to help me out?")
            .options(new Choice("sorry aluft, i'm too busy", "i would be glad to help") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("no worries, let me know when you're free");
                        return;
                    }
                    final int pick = DataConversions.random(0, ORDERS.length - 1);
                    Order order = ORDERS[pick];
                    for (int i = 0; i < order.wording.length; i++) {
                        c.npc(order.wording[i]);
                    }
                    c.player("no problem");
                    c.npc("good stuff");
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
        c.player("hi aluft").npc("hello again, are the dishes ready?");
        if (!holdingOrder(order)) {
            c.player("i'm not done yet");
            for (int i = 0; i < order.wording.length; i++) {
                c.npc(order.wording[i]);
            }
            c.npc("don't take too long");
            c.npc("it's a full house tonight");
            c.start();
            return;
        }
        c.player("here you go aluft");
        for (int i = 0; i < order.items.length; i++) {
            c.take(order.items[i], order.amounts[i]);
        }
        c.message("you give aluft the dishes");
        c.npc("they look great, well done");
        c.npc("here's your share of the profit");
        c.give(new InvItem(COINS, order.coins));
        c.message("mr gianne gives you " + order.coins + " gold coins");
        final int exp = order.exp;
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().incExp(COOKING, exp, true);
                c.getPlayer().getActionSender().sendStat(COOKING);
                setStage(PASSED);
            }
        });
        c.npc("can you stay and make another dish?");
        c.options(new Choice("sorry aluft, i'm too busy", "i would be glad to help") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("no worries, let me know when you're free");
                    return;
                }
                final int pick = DataConversions.random(0, ORDERS.length - 1);
                Order next = ORDERS[pick];
                for (int i = 0; i < next.wording.length; i++) {
                    c.npc(next.wording[i]);
                }
                c.player("no problem");
                c.npc("good stuff");
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
}
