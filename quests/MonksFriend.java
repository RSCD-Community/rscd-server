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
 * Monk's friend. Released 28 May 2002, written by Thomas Woode.
 *
 * Brother Omad in the Kandarin monastery has not slept in a week because
 * Brother Androe's son has lost his blanket to thieves. Get it back, and the
 * quest turns into its second half: the boy's first birthday party needs wine,
 * the monk who went for the wine is drunk in the forest, and his cart is
 * broken.
 *
 * Ids:
 *
 *     Brother Omad    npc 350, monastery, (580,665)
 *     Brother Cedric  npc 357, forest,    (617,638)
 *     Blanket         item 716, a ground spawn in the thieves' sewer at (619,3500)
 *     Bucket          item 21, a ground spawn beside Cedric at (616,640)
 *     Bucket of water item 50
 *     Logs            item 14
 *     Law-rune        item 42, eight of them
 *     broken cart     scenery 360 at (617,639) -- one of four in the world
 *
 * Nothing here claims the blanket, the bucket or the well: the blanket is
 * already lying in the sewer where Jagex left it, the bucket is already lying
 * beside Cedric, and filling a bucket at a well is ordinary behaviour that
 * this quest has no business taking over. All the quest owns is the two monks.
 *
 * One deviation. Cedric says he will mend the cart and the cart at (617,639)
 * stays broken, because scenery is shared: swapping it for a whole one would
 * mend it for every player on the server, including the ones who have not
 * started. Jagex could get away with that in a single-player-facing script and
 * we cannot, so the cart is mended in the dialogue only.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class MonksFriend extends Quest {

    public final static int UID = Quests.MONKS_FRIEND;

    /** Looking for the blanket. */
    private static final int STARTED = 1;
    /** Blanket returned; Omad is asleep and the party is being planned. */
    private static final int BLANKET_BACK = 2;
    /** Agreed to go and find Cedric. */
    private static final int FIND_CEDRIC = 3;
    /** Cedric is sober and wants wood. */
    private static final int SOBER = 4;
    /** The cart is mended; go and tell Omad. */
    private static final int CART_FIXED = 5;
    private static final int FINISHED = 6;

    private static final int OMAD = 350;
    private static final int CEDRIC = 357;

    private static final int BLANKET = 716;
    private static final int BUCKET_OF_WATER = 50;
    private static final int EMPTY_BUCKET = 21;
    private static final int LOGS = 14;
    private static final int LAW_RUNE = 42;
    private static final int LAW_RUNES = 8;

    private static final int WOODCUT = 8; /* skill index */

    public MonksFriend(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Monk's friend");
        setFinalStage(FINISHED);
        associateNpc(OMAD);
        associateNpc(CEDRIC);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("A monk's child has had their blanket stolen. Find the thieves' den and return the blanket, then help Brother Omad organise the drinks for the child's birthday party.");
        setStartPoint("Monastery south of Ardounge");
        setSpeakTo("Brother Omad");
        setMissionLength("Short");
        rewardItem(LAW_RUNE, LAW_RUNES);
        rewardExp(WOODCUT, 125, 125);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Monk's friend quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() == OMAD) {
            talkToOmad(npc);
        } else {
            talkToCedric(npc);
        }
    }

    /*
     * QuestManager always calls this three-argument form, whatever the
     * trigger, so anything it does not recognise must fall through to the
     * one above -- without that fall-through Omad and Cedric never answered
     * a click, which they didn't.
     */
    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_NPC && entity instanceof Npc
                && ((Npc) entity).getID() == CEDRIC
                && used != null && used.getID() == BUCKET_OF_WATER) {
            giveWater((Npc) entity);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // --------------------------------------------------------------- Omad --

    private void talkToOmad(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Dum dee do la la")
                .npc("Hiccup")
                .npc("That's good wine")
                .start();
            return;
        }
        if (getStage() == CART_FIXED) {
            new Conversation(p, npc)
                .player("Hi Omad, Brother Cedric is on his way")
                .npc("good,good,good")
                .npc("now we can party")
                .npc("I have little to repay you with")
                .npc("but please, take these runestones")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(getFinalStage());
                    }
                })
                .start();
            return;
        }
        if (getStage() >= FIND_CEDRIC) {
            new Conversation(p, npc)
                .player("Hi there!")
                .npc("oh my, I need a drink")
                .npc("where is that brother Cedric")
                .start();
            return;
        }
        if (getStage() == BLANKET_BACK) {
            party(npc);
            return;
        }
        if (questStarted()) {
            blanketBack(npc);
            return;
        }
        offerQuest(npc);
    }

    private void offerQuest(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello there")
            .npc("...yawn...oh, hello...yawn..")
            .npc("I'm sorry, I'm just so tired..")
            .npc("I haven't slept in a week")
            .npc("It's driving me mad")
            .options(new Choice("Why can't you sleep, what's wrong?",
                                "Sorry, I'm too busy to hear your problems") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("It's the brother Androe's son")
                     .npc("with his constant waaaaaah..waaaaaaaaah")
                     .npc("Androe said it's natural, but it's just annoying")
                     .player("I suppose that's what kids do")
                     .npc("he was fine, up until last week")
                     .npc("thieves broke in")
                     .npc("They stole his favourite sleeping blanket")
                     .npc("now he won't rest until it's returned")
                     .npc("..and that means neither can I!")
                     .options(new Choice("can I help at all?",
                                         "I'm sorry to hear that, I hope you find his blanket") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 return;
                             }
                             c.npc("please do, we are peaceful men")
                              .npc("but you could recover the blanket from the thieves")
                              .player("where are they?")
                              .npc("they hide in a secret cave in the forest")
                              .npc("..it's hidden under a ring of stones")
                              .npc("please, bring back the blanket")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      setStage(STARTED);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    private void blanketBack(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.player("Hello")
         .npc("...yawn...oh, hello again...yawn..")
         .npc("..please tell me you have the blanket");
        if (p.getInventory().countId(BLANKET) <= 0) {
            c.player("I'm afraid not")
             .npc("I need some sleep");
        } else {
            c.player("Yes I returned it from the clutches of the evil thieves")
             .take(BLANKET, 1)
             .npc("Really, that's excellent, well done")
             .npc("that should cheer up Androe's son")
             .npc("and maybe I will be able to get some rest")
             .npc("..yawn..i'm off to bed, farewell brave traveller.")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(BLANKET_BACK);
                 }
             });
        }
        c.start();
    }

    /**
     * The second half of the quest. Two of the three replies agree to go and
     * look for Cedric -- the transcript marks both as checkpoints -- so both
     * of them move the quest on and only "I've no time for that" does not.
     */
    private void party(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("Hello, how are you")
            .npc("much better now i'm sleeping well")
            .npc("now I can organise the party")
            .player("what party?")
            .npc("Androe's son's birthday party")
            .npc("he's going to be one year old")
            .player("that's sweet")
            .npc("it's also a great excuse for a drink")
            .npc("now we just need brother Cedric to return")
            .npc("with the wine")
            .options(new Choice("who's brother Cedric?", "enjoy it, i'll see you soon") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("Cedric lives here too")
                     .npc("we sent him out three days ago")
                     .npc("to collect wine, but he didn't return")
                     .npc("he most probably got drunk")
                     .npc("and lost in the forest")
                     .npc("I don't suppose you could look for him?")
                     .npc("then we can really party")
                     .options(new Choice("where should I look?",
                                         "can I come?",
                                         "I've no time for that, sorry") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc("oh, he won't be far")
                                  .npc("probably out in the forest");
                             } else if (option == 1) {
                                 c.npc("of course,")
                                  .npc("but we need the wine first");
                             } else {
                                 return;
                             }
                             c.then(new Effect() {
                                 public void run(Conversation c) {
                                     setStage(FIND_CEDRIC);
                                 }
                             });
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------- Cedric --

    private void talkToCedric(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Brother Oman sends you his thanks")
                .npc("He won't be in a fit state to thank you in person any more")
                .start();
            return;
        }
        if (getStage() == CART_FIXED) {
            new Conversation(p, npc)
                .player("Hello Cedric")
                .npc("hi, i'm almost done here")
                .npc("go tell Omad that I..")
                .npc("..won't be long")
                .start();
            return;
        }
        if (getStage() == SOBER) {
            wantsWood(npc);
            return;
        }
        if (getStage() == FIND_CEDRIC) {
            drunk(npc);
            return;
        }
        /*
         * Before the quest reaches him he is just a drunk in the woods, and
         * that is all he has to say -- to a player who has never met Omad and
         * to one who is still hunting the blanket alike.
         */
        new Conversation(p, npc)
            .player("Hello")
            .npc("honey,money,woman,wine..")
            .player("Are you ok?")
            .npc("yesshh...hic up...beautiful..")
            .player("take care old monk")
            .npc("la..di..da..hic..up..")
            .start();
    }

    /**
     * The first meeting asks for water; every meeting after it says the same
     * thing more briefly, which is how the transcript has it.
     */
    private void drunk(Npc npc) {
        Player p = getOwner();
        if (p.getFlag("monk.asked") != 0) {
            new Conversation(p, npc)
                .player("Are you okay?")
                .npc("...hic up..oh my head..")
                .npc("..I need some water.")
                .start();
            return;
        }
        p.setFlag("monk.asked", 1);
        new Conversation(p, npc)
            .player("Brother Cedric are you okay?")
            .npc("yeesshhh, i'm very, very....")
            .npc("..drunk..hic..up..")
            .player("brother Omad needs the wine..")
            .player("..for the party")
            .npc("oh dear, oh dear")
            .npc("I knew I had to do something")
            .npc("pleashhh, find me some water")
            .npc("once i'm sober i'll help you..")
            .npc("..take the wine back.")
            .start();
    }

    private void giveWater(Npc npc) {
        Player p = getOwner();
        if (getStage() != FIND_CEDRIC) {
            p.getActionSender().sendMessage("Brother Cedric does not want your water");
            return;
        }
        new Conversation(p, npc)
            .player("Cedric, here, drink some water")
            .take(BUCKET_OF_WATER, 1)
            .give(new InvItem(EMPTY_BUCKET, 1))
            .npc("oh yes, my head's starting to spin")
            .npc("gulp...gulp")
            .npc("aah, that's better")
            .npc("now i just need to fix...")
            .npc("..this cart..")
            .npc("..and we can go party")
            .npc(".could you help?")
            .then(new Effect() {
                public void run(Conversation c) {
                    setStage(SOBER);
                }
            })
            .options(new Choice("Yes i'd be happy to",
                                "No, i've helped enough monks today") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("i need some wood");
                    } else {
                        c.npc("in that case i'd better drink..")
                         .npc("..more wine. It help's me think.");
                    }
                }
            })
            .start();
    }

    private void wantsWood(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.player("Hello Cedric")
         .npc("want to help me fix the cart?");
        if (p.getInventory().countId(LOGS) <= 0) {
            c.options(new Choice("Yes i'd be happy to", "No, not really") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("i need some wood");
                    }
                }
            });
            c.start();
            return;
        }
        c.npc("i need some wood")
         .player("here you go..")
         .player("I've got some wood")
         .take(LOGS, 1)
         .npc("well done, now i'll fix this cart")
         .npc("you head back to Brother Omad")
         .npc("Tell him i'm on my way")
         .npc("I won't be long")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(CART_FIXED);
             }
         })
         .start();
    }
}
