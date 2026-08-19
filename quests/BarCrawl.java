import org.rscdaemon.server.model.BarCrawlCard;
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
 * The Alfred Grimhand bar crawl.
 *
 * A rite of passage rather than a quest: drink the strongest thing on offer
 * in six of the worst bars in the game, get the card signed each time, and
 * the Barbarian guards will let you through the gate into the outpost. That
 * gate is the only way to the Barbarian Outpost Agility Course, and to the
 * second of the three scorpions in Scorpion catcher -- which is why that
 * quest's own notes said the crawl "is a mini-quest nobody has written". It
 * is written now.
 *
 * This class owns three things:
 *
 *     Barbarian guard   npc 305, two of them at (494,547)
 *     the gate          object 311 at (494,543)
 *     barcrawl card     item 668, and its "read" command
 *
 * The six bartenders who sign the card are not here -- they are ten npcs with
 * a great deal of unrelated dialogue apiece, so they live in
 * npchandler/Bartenders.java. The state both halves share, and the drink
 * effects, are model/BarCrawlCard.java, which also documents the stage
 * encoding and where the Hits figures came from.
 *
 * Two decisions worth recording:
 *
 *  - The guard's "I want some money" option is kept even though it does
 *    nothing but get the player told off. It is in the transcript, it is the
 *    only joke the guard makes, and dropping options because they have no
 *    mechanical effect is how a world stops feeling like a place.
 *
 *  - The gate answers rather than refuses. Walking into it before the crawl
 *    is done starts the same conversation talking to a guard would, which is
 *    what the wiki describes ("You may also try to enter the gate. One of the
 *    guards will come to you if you haven't proven yourself"). If no guard is
 *    alive to come over, the gate says so in the chat box instead of
 *    silently doing nothing.
 *
 * Every line below is verbatim from Transcript:Barbarian guard, misspellings
 * included -- "Oi whaddya want?", "I think I jusht about done them all, but I
 * losht count", "Not to bad", "Ello friend".
 */
public class BarCrawl extends Quest {

    public final static int UID = Quests.BAR_CRAWL;

    private static final int GUARD = 305;

    private static final int GATE = 311, GATE_X = 494, GATE_Y = 543;

    /*
     * The fence line runs north-south and the passage through the gate is
     * EAST-WEST: (494,544) is outside the gate, (493,544) is inside. Both
     * earlier versions of this file believed the crossing was north-south
     * and teleported the player along the fence instead of through it.
     */
    private static final int OUTSIDE_X = 494, INSIDE_X = 493, CROSS_Y = 544;

    public BarCrawl(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Alfred Grimhand bar crawl");
        setFinalStage(BarCrawlCard.DONE);

        /* No 2003 manual page survives for this mini-quest; description is ours. */
        describe("Drink the strongest thing on offer in six of the worst bars in the land and get your card signed in each, and the Barbarian guards will let you into their outpost.");
        setStartPoint("The Barbarian Outpost gate");
        setSpeakTo("Barbarian guard");
        rewardOther("Access to the Barbarian Outpost");

        associateNpc(GUARD);
        associateObject(GATE, GATE_X, GATE_Y);
        associateItem(BarCrawlCard.CARD);
    }

    public void completeQuest() {
        getOwner().getActionSender().sendMessage("@gre@You have completed the Alfred Grimhand barcrawl!");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger == QuestTrigger.ITEM_COMMAND) {
            BarCrawlCard.read(getOwner());
            return;
        }
        if (entity instanceof Npc && trigger == QuestTrigger.NPC_TALK) {
            guard((Npc) entity);
            return;
        }
        if (entity instanceof GameObject
                && (trigger == QuestTrigger.OBJECT_ACT1 || trigger == QuestTrigger.OBJECT_ACT2)) {
            gate((GameObject) entity);
        }
    }

    // ------------------------------------------------------------- guard --

    private void guard(Npc npc) {
        Player p = getOwner();

        if (BarCrawlCard.finished(p)) {
            new Conversation(p, npc).npc("Ello friend").start();
            return;
        }

        if (!BarCrawlCard.started(p)) {
            offerCrawl(p, npc);
            return;
        }

        /* On the crawl. What the guard says depends on the card, not the
           signatures -- he cannot read, and says so. */
        if (!BarCrawlCard.holdingCard(p)) {
            new Conversation(p, npc)
                .npc("So hows the barcrawl coming along?")
                .options(new Choice("I've lost my barcrawl card",
                        "Not to bad, my barcrawl card is in my bank now") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            c.npc("You need it with you when you are going on a barcrawl");
                            return;
                        }
                        c.npc("What are you like?");
                        c.npc("You're gonna have to start all over now");
                        c.npc("Here you go, have another barcrawl card");
                        c.then(new Effect() {
                            public void run(Conversation conv) {
                                BarCrawlCard.issue(conv.getPlayer());
                            }
                        });
                    }
                })
                .start();
            return;
        }

        if (!BarCrawlCard.allSigned(p)) {
            new Conversation(p, npc)
                .npc("So hows the barcrawl coming along?")
                .player("I haven't finished it yet")
                .npc("Well come back when you have, you lightweight")
                .start();
            return;
        }

        new Conversation(p, npc)
            .npc("So hows the barcrawl coming along?")
            .player("I think I jusht about done them all, but I losht count")
            .message("You give the card to the barbarian")
            .npc("Yep that seems fine")
            .npc("I never learned to read, but you look like you've drunk plenty")
            .npc("You can come in now")
            .then(new Effect() {
                public void run(Conversation c) {
                    BarCrawlCard.handIn(c.getPlayer());
                }
            })
            .start();
    }

    private void offerCrawl(Player p, Npc npc) {
        new Conversation(p, npc)
            .npc("Oi whaddya want?")
            .options(new Choice("I want to come through this gate", "I want some money") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Well do I look like a banker to you?");
                        return;
                    }
                    c.npc("Barbarians only");
                    c.npc("Are you a barbarian?");
                    c.npc("You don't look like one");
                    c.options(new Choice("Hmm, yep you've got me there",
                            "Looks can be deceiving, I am in fact a barbarian") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                return;
                            }
                            c.npc("If you're a barbarian you need to be able to drink like one");
                            c.npc("We barbarians like a good drink");
                            c.npc("And I have the perfect challenge for you");
                            c.npc("The Alfred Grimhand barcrawl");
                            c.npc("First done by Alfred Grimhand");
                            c.message("The guard hands you a barcrawl card");
                            c.then(new Effect() {
                                public void run(Conversation conv) {
                                    BarCrawlCard.issue(conv.getPlayer());
                                }
                            });
                            c.npc("Take that card to each of the bars named on it");
                            c.npc("The bartenders all know what it means");
                            c.npc("We're kinda well known");
                            c.npc("They'll give you their strongest drink and sign your card");
                            c.npc("When you done all that, we'll be happy to let you in");
                        }
                    });
                }
            })
            .start();
    }

    // -------------------------------------------------------------- gate --

    private void gate(GameObject object) {
        Player p = getOwner();

        /* Leaving is always free; only getting in is earned. */
        boolean inside = p.getX() < GATE_X;
        if (BarCrawlCard.finished(p) || inside) {
            open(object, inside ? OUTSIDE_X : INSIDE_X);
            return;
        }

        Npc guard = world.getNpc(GUARD, 485, 505, 538, 556);
        if (guard == null) {
            p.getActionSender().sendMessage("@gry@The gate is barred. Barbarians only.");
            return;
        }
        guard(guard);
    }

    /**
     * Swing the gate open and walk through.
     *
     * Object 181 is the open-gate model ObjectAction.doGate swaps in, and
     * delayedSpawnObject puts the real one back a second later. The teleport
     * is what actually moves the player: the gate is one tile wide and the
     * pathfinder will not route them through a closed one.
     */
    private void open(GameObject object, int toX) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(object.getLocation(), 181,
            object.getDirection(), object.getType()));
        world.delayedSpawnObject(object.getLoc(), 1000);
        p.teleport(toX, CROSS_Y, false);
    }
}
