import org.rscdaemon.server.model.ChatMessage;
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
 * Pirate's treasure.
 *
 * Redbeard Frank will trade the location of a dead captain's chest for a bottle
 * of Karamja rum. Rum cannot leave Karamja -- the customs officer confiscates it
 * on the way out -- so the only way home with a bottle is to hide it in Luthas's
 * banana crate and collect it from the back room of Wydin's shop in Port Sarim.
 * The key Frank hands over opens a chest upstairs in the Blue Moon inn.
 *
 * Two quest points, 450 coins, a gold ring and an emerald. No experience.
 *
 * The smuggle is a loop rather than a ladder, and the stage numbers say so. Rum
 * put in the crate is rum out of the player's hands, and coming out the far end
 * at Wydin's puts them back at STARTED -- holding rum, needing to reach Frank.
 * Lose the bottle after that and the loop is simply run again, which is what
 * would happen in the real game too.
 *
 * How many bananas are currently in the crate IS saved, in persistent var slot
 * 0. An earlier revision kept it session-only on the argument that a quest
 * persists one integer, its stage -- which stopped being true when Quest grew
 * var slots. The session-only version was a live landmine: a server restart
 * (or plain logout) between loading bananas and telling Luthas silently
 * emptied the crate, and the player found out only when the Port Sarim crate
 * answered bananas instead of rum.
 *
 * Wydin's back room is shut to anyone who is not wearing a white apron and has
 * not asked him for a job, which is what the apron is for. The dialogue belongs
 * to the door rather than to Wydin -- that is how the transcript records it, and
 * it is also what lets Wydin stay the shopkeeper he is. Registering him as an
 * npc handler would take him away from the grocery.
 *
 * Being hired IS saved too (var slot 1). It used to be session-only on the
 * one-integer-per-quest argument above; with var slots there is no reason for
 * Wydin to forget his own employee across a logout, and in the real game he
 * does not.
 *
 * One line of Wydin's is therefore missing: in the real game, once the player has
 * been in the back room he greets them with "Is it nice and tidy round the back
 * now" instead of his shop patter. That is the shopkeeper's dialogue and this
 * quest does not own it.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class PiratesTreasure extends Quest {

    public final static int UID = Quests.PIRATES_TREASURE;

    /** Frank has named his price. */
    private static final int STARTED = 1;
    /** A bottle is hidden in the crate on the plantation. */
    private static final int RUM_IN_CRATE = 2;
    /** Luthas has shipped that crate to Port Sarim. */
    private static final int RUM_SHIPPED = 3;
    /** Frank has been paid in rum and has handed over Hector's key. */
    private static final int HAS_KEY = 4;
    private static final int FINISHED = 5;
    /**
     * The chest has been opened and the message inside read: dig behind the
     * south bench in Falador park.
     *
     * Numbered above FINISHED rather than between HAS_KEY and it, which looks
     * wrong and is deliberate. Stages are stored as bare integers in the save
     * and are only ever compared for equality (Quest.isComplete matches the
     * final stage exactly; nothing anywhere compares stages with &lt; or &gt;).
     * Renumbering FINISHED to make room would silently reinterpret every
     * already-saved 5 -- every player who has finished this quest -- as
     * "message read, go and dig", and pay them the reward a second time.
     * Appending costs nothing and breaks nobody.
     */
    private static final int HAS_MESSAGE = 6;

    private static final int REDBEARD_FRANK = 128;
    private static final int LUTHAS = 164;

    /** The plantation crate. Command two is "Search". */
    private static final int CRATE_KARAMJA = 182;
    private static final int CRATE_KARAMJA_X = 337;
    private static final int CRATE_KARAMJA_Y = 711;

    /** Its twin in the back room of Wydin's grocery, and the only one of its id. */
    private static final int CRATE_SARIM = 185;

    /** Upstairs in the Blue Moon inn, Varrock, at (126,1467). Command one is "Open". */
    private static final int CHEST = 187;

    /**
     * The flowerbed in Falador park, where the treasure is actually buried.
     *
     * Three placements of this id exist in the world; the two that matter are
     * (289,548) and (290,548), the bed behind the park's south bench. The
     * third is far away and is not this quest's, hence the coordinate check.
     */
    private static final int FLOWER = 188;
    private static final int FLOWER_X = 289;
    private static final int FLOWER_Y = 548;
    private static final int SPADE = 211;

    /**
     * Wyson the gardener, who is on his feet in the park and does not care
     * what your quest log says. He is a plain NpcHandler rather than part of
     * this quest -- see Wyson.java, which has documented this scene since
     * before there was any code behind it.
     */
    private static final int WYSON = 116;

    /** Wydin, who owns the grocery and the back room. Kept as the shopkeeper. */
    private static final int WYDIN = 129;
    /** The back room door, and the only door of its id in the world. */
    private static final int BACK_DOOR = 47;
    private static final int BACK_DOOR_X = 277;
    private static final int BACK_DOOR_Y = 658;
    /** The doorframe every door in the game swings open into. */
    private static final int OPEN_DOOR = 11;

    /** "Apron -- A mostly clean apron". Not 191, the crafting guild's. */
    private static final int WHITE_APRON = 182;

    private static final int COINS = 10;

    /**
     * "Chest key -- A key to One eyed Hector's chest".
     *
     * RSCD renamed this one to "Team 1 Key" and wired it into a team minigame in
     * ObjectAction and InvUseOnObject, so for now the same item opens Hector's
     * chest and one of that minigame's doors. The id is Jagex's and the quest
     * keeps it; the minigame is the thing squatting, and belongs on an appended
     * id of its own the way the Ignis Isle spells do.
     */
    private static final int KEY = 382;
    private static final int BANANA = 249;
    private static final int KARAMJA_RUM = 318;
    private static final int GOLD_RING = 283;
    private static final int EMERALD = 163;

    private static final int CRATE_HOLDS = 10;
    private static final int LUTHAS_PAYS = 30;
    private static final int TREASURE_COINS = 450;

    /**
     * Bananas in the plantation crate right now -- persistent var slot 0,
     * see the class comment for why it stopped being session-only.
     */
    private static final int VAR_BANANAS = 0;

    private int bananasInCrate() {
        return getVar(VAR_BANANAS, 0);
    }

    private void setBananasInCrate(int count) {
        setVar(VAR_BANANAS, count);
    }

    /**
     * Wydin has taken the player on. Persisted -- see the class comment.
     */
    private static final int VAR_HIRED = 1;

    private boolean hiredByWydin() { return getVar(VAR_HIRED, 0) == 1; }

    public PiratesTreasure(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Pirate's treasure");
        setFinalStage(FINISHED);
        associateNpc(REDBEARD_FRANK);
        associateNpc(LUTHAS);
        associateObject(CRATE_KARAMJA);
        associateObject(CRATE_SARIM);
        associateObject(CHEST);
        /* By placement, not by id. Associating an object hands the quest every
           interaction with it outright (see InvUseOnObject), and this id also
           stands somewhere far from Falador; claiming only the two tiles of the
           park bed leaves that one behaving exactly as it did. */
        associateObject(FLOWER, FLOWER_X, FLOWER_Y);
        associateObject(FLOWER, FLOWER_X + 1, FLOWER_Y);
        associateDoor(BACK_DOOR);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Redbeard Frank knows where secret pirate treasure is hidden, it may require some work to persuade him to let you know where though.");
        setStartPoint("Port Sarim");
        setSpeakTo("Redbeard Frank");
        setMissionLength("Medium");
        rewardItem(COINS, TREASURE_COINS);
        rewardItem(GOLD_RING, 1);
        rewardItem(EMERALD, 1);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Pirate's treasure quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc && trigger == QuestTrigger.NPC_TALK) {
            Npc npc = (Npc) entity;
            if (npc.getID() == REDBEARD_FRANK) {
                talkToFrank(npc);
            } else if (npc.getID() == LUTHAS) {
                talkToLuthas(npc);
            }
            return;
        }
        if (entity instanceof GameObject
                && (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2)) {
            backDoor((GameObject) entity);
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            switch (object.getID()) {
                case CRATE_KARAMJA:
                    if (trigger == QuestTrigger.OBJECT_ACT2) {
                        searchPlantationCrate(object);
                    }
                    break;
                case CRATE_SARIM:
                    if (trigger == QuestTrigger.OBJECT_ACT2) {
                        searchSarimCrate();
                    }
                    break;
                case CHEST:
                    if (trigger == QuestTrigger.OBJECT_ACT1) {
                        openChest();
                    }
                    break;
            }
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (object.getID() == CRATE_KARAMJA) {
                hideRum(object, used);
                return;
            }
            if (object.getID() == FLOWER && used != null && used.getID() == SPADE) {
                digFlowers();
                return;
            }
        }
        triggerEntity(trigger, entity);
    }

    // ------------------------------------------------- wydin's back room --

    /**
     * The door into the back of the grocery.
     *
     * Wydin does the talking, so if he is not on his feet the door is simply a
     * door: nobody is there to stop anyone, and shutting the room on an empty
     * shop would strand a player who walked in before he died.
     */
    private void backDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != BACK_DOOR_X || door.getY() != BACK_DOOR_Y) {
            p.getActionSender().sendMessage("@gry@ Nothing interesting happens");
            return;
        }

        Npc wydin = world.getNpc(WYDIN, BACK_DOOR_X - 4, BACK_DOOR_X, BACK_DOOR_Y - 3, BACK_DOOR_Y + 3);
        boolean leaving = p.getX() >= BACK_DOOR_X;
        if (leaving || wydin == null || (hiredByWydin() && p.getInventory().wielding(WHITE_APRON))) {
            walkThrough(door);
            return;
        }

        if (hiredByWydin()) {
            new Conversation(p, wydin)
                .npc("Can you put your apron on before going in there please")
                .start();
            return;
        }

        Conversation c = new Conversation(p, wydin)
            .npc("Heh you can't go in there")
            .npc("Only employees of the grocery store can go in")
            .options(new Choice("Well can I get a job here?", "Sorry I didn't realise") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    askForJob(c);
                }
            }.says(0, "Can I get a job here?"));
        c.start();
    }

    /**
     * Being taken on. Wydin asks whether the player owns an apron and the answer
     * is the one they can prove -- offering "Yes I have" with nothing in the
     * pack would only let them lie to a man who can see them.
     */
    private void askForJob(Conversation c) {
        final boolean hasApron = c.getPlayer().getInventory().countId(WHITE_APRON) > 0
            || c.getPlayer().getInventory().wielding(WHITE_APRON);

        c.npc("Well you're keen I'll give you that")
         .npc("Ok I'll give you a go")
         .npc("Have you got your own apron?");

        if (!hasApron) {
            c.player("No")
             .npc("Well you can't work here unless you have an apron")
             .npc("Health and safety regulations, you understand");
            return;
        }

        c.player("Yes I have")
         .npc("Wow you are prepared, you're hired")
         .npc("Go through to the back and tidy up for me please")
         .then(new Effect() {
             public void run(Conversation c) {
                 setVar(VAR_HIRED, 1);
             }
         });
    }

    /**
     * Swing the door and step through it. It faces east/west, so it stands
     * between x-1 and x and the player comes out on the side they were not on.
     */
    private void walkThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(), door.getY(), false);
    }

    // -------------------------------------------------------------- frank --

    private void talkToFrank(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Arrrh Matey");

        // HAS_MESSAGE counts as done as far as Frank is concerned: he has been
        // paid, he has handed over the key, and the rest is between the player
        // and a flowerbed.
        if (completed() || getStage() == HAS_KEY || getStage() == HAS_MESSAGE) {
            afterwards(c);
            c.start();
            return;
        }

        if (!questStarted()) {
            c.options(new Choice("I'm in search of treasure", "Arrrh", "Do you want to trade?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Arrrh");
                        return;
                    }
                    if (option == 2) {
                        c.npc("No, I've got nothing to trade");
                        return;
                    }
                    c.npc("Arrrh treasure you be after eh?")
                     .npc("Well I might be able to tell you where to find some.")
                     .npc("For a price")
                     .player("What sort of price?")
                     .npc("Well for example if you can get me a bottle of rum")
                     .npc("Not just any rum mind")
                     .npc("I'd like some rum brewed on Karamja island")
                     .npc("There's no rum like Karamja rum")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(STARTED);
                         }
                     });
                }
            });
            c.start();
            return;
        }

        c.npc("Have Ye brought some rum for yer old mate Frank");
        if (getOwner().getInventory().countId(KARAMJA_RUM) < 1) {
            c.player("No not yet").start();
            return;
        }
        c.player("Yes I've got some")
         .take(KARAMJA_RUM, 1)
         .npc("Now a deals a deal, I'll tell ye about the treasure")
         .npc("I used to serve under a pirate captain called One Eyed Hector")
         .npc("Hector was a very succesful pirate and became very rich")
         .npc("but about a year ago we were boarded by the Royal Asgarnian Navy")
         .npc("Hector was killed along with many of the crew")
         .npc("I was one of the few to escape")
         .npc("And I escaped with this")
         .npc("This is Hector's key")
         .give(new InvItem(KEY, 1))
         .message("Redbeard Frank gives you a key")
         .npc("I believe it opens his chest")
         .npc("In his old room in the blue moon inn in Varrock")
         .npc("With any luck his treasure will be in there")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(HAS_KEY);
             }
         })
         .options(new Choice("Ok thanks, I'll go and get it", "So why didn't you ever get it?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("I'm not allowed in the blue moon inn")
                      .npc("Apparently I'm a drunken trouble maker");
                 }
             }
         });
        c.start();
    }

    /**
     * Frank once the key is his no longer.
     *
     * He replaces a lost key without complaint, which the transcript records and
     * which matters: the chest is the only thing the key opens, and there is no
     * second way into it.
     */
    private void afterwards(Conversation c) {
        final boolean lostKey = !completed()
            && getOwner().getInventory().countId(KEY) < 1;
        if (lostKey) {
            c.player("I seem to have lost my chest key")
             .npc("Arrr silly you")
             .npc("Fortunatly I took the precaution to have another made")
             .give(new InvItem(KEY, 1))
             .message("Redbeard Frank gives you a key");
            return;
        }
        c.options(new Choice("Arrrh", "Do you want to trade?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Arrrh");
                } else {
                    c.npc("No, I've got nothing to trade");
                }
            }
        });
    }

    // ------------------------------------------------------------- luthas --

    private void talkToLuthas(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        final boolean full = bananasInCrate() >= CRATE_HOLDS;

        if (full) {
            c.player("I've filled a create with bananas")
             .npc("Well done here is your payment")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     p.getInventory().add(new InvItem(COINS, LUTHAS_PAYS));
                     p.getActionSender().sendInventory();
                     PiratesTreasure.this.setBananasInCrate(0);
                     if (getStage() == RUM_IN_CRATE) {
                         setStage(RUM_SHIPPED);
                     }
                 }
             })
             .options(new Choice("Will you pay me for another crate full?",
                                 "So where are these bananas going to be delivered to?",
                                 "Thankyou, I'll be on my way") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("Yes certainly")
                          .npc("If you go outside you should see the old crate has been loaded on to the ship")
                          .npc("and there is another empty crate in it's place");
                     } else if (option == 1) {
                         c.npc("I sell them to Wydin who runs a grocery store in Port Sarim");
                     }
                 }
             });
            c.start();
            return;
        }

        if (bananasInCrate() > 0) {
            c.npc("Have you completed your task yet?")
             .options(new Choice("No, the crate isn't full yet", "What did I have to do again?") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("Well come back when it is");
                         return;
                     }
                     theJob(c);
                 }
             });
            c.start();
            return;
        }

        c.npc("Hello I'm Luthas, I run the banana plantation here")
         .options(new Choice("Could you offer me employment on your plantation?",
                             "That customs officer is annoying isn't she?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Well I know her pretty well")
                      .npc("She doesn't cause me any trouble any more")
                      .npc("She doesn't even search my export crates any more")
                      .npc("She knows they only contain bananas");
                     return;
                 }
                 c.npc("Yes, I can sort something out");
                 theJob(c);
             }
         });
        c.start();
    }

    private void theJob(Conversation c) {
        c.npc("Yes there's a crate outside ready for loading up on the ship")
         .npc("If you could fill it up with bananas")
         .npc("I'll pay you " + LUTHAS_PAYS + " gold");
    }

    // ------------------------------------------------------------- crates --

    private void searchPlantationCrate(GameObject crate) {
        Player p = getOwner();
        if (crate.getX() != CRATE_KARAMJA_X || crate.getY() != CRATE_KARAMJA_Y) {
            // Five other crates share this id. Claiming an object id claims all
            // of them, so the rest get the answer they had before.
            p.getActionSender().sendMessage("You search the crate but find nothing");
            return;
        }
        if (bananasInCrate() >= CRATE_HOLDS) {
            p.getActionSender().sendMessage("The crate is full of bananas");
            return;
        }
        int carried = p.getInventory().countId(BANANA);
        if (carried < 1) {
            p.getActionSender().sendMessage("The crate is "
                + (bananasInCrate() == 0 ? "empty" : "part full of bananas"));
            return;
        }
        int room = CRATE_HOLDS - bananasInCrate();
        int put = carried < room ? carried : room;
        p.getInventory().remove(BANANA, put);
        p.getActionSender().sendInventory();
        setBananasInCrate(bananasInCrate() + put);
        p.getActionSender().sendMessage("You put " + put + " banana"
            + (put == 1 ? "" : "s") + " in the crate");
        if (bananasInCrate() >= CRATE_HOLDS) {
            p.getActionSender().sendMessage("The crate is now full");
        }
    }

    /**
     * The bottle goes in on top of the bananas.
     *
     * It has to be a full crate: an unfinished one is not going anywhere, and
     * Luthas only ships the ones he has paid for.
     */
    private void hideRum(GameObject crate, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != KARAMJA_RUM) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (crate.getX() != CRATE_KARAMJA_X || crate.getY() != CRATE_KARAMJA_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!questStarted() || getStage() != STARTED) {
            p.getActionSender().sendMessage("There is no reason to hide rum in there");
            return;
        }
        if (bananasInCrate() < CRATE_HOLDS) {
            p.getActionSender().sendMessage("You need to fill the crate with bananas first");
            return;
        }
        p.getInventory().remove(KARAMJA_RUM, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You hide the rum under the bananas");
        setStage(RUM_IN_CRATE);
    }

    private void searchSarimCrate() {
        Player p = getOwner();
        if (getStage() != RUM_SHIPPED) {
            p.getActionSender().sendMessage("You search the crate and find some bananas");
            return;
        }
        p.getInventory().add(new InvItem(KARAMJA_RUM, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find your rum, still where you left it");
        // Back to the beginning of the errand: rum in hand, Frank waiting.
        setStage(STARTED);
    }

    // -------------------------------------------------------------- chest --

    private void openChest() {
        // Unlike the crates, this chest id is placed exactly once, upstairs in
        // the Blue Moon inn, so there is nothing else to tell it apart from.
        Player p = getOwner();
        if (completed()) {
            p.getActionSender().sendMessage("The chest is empty");
            return;
        }
        // KEY's id is reused elsewhere for an unrelated minigame's "Team 1 Key"
        // (see the class comment), so holding the id alone does not mean this
        // player ever started the quest via Redbeard Frank.
        if (!questStarted() || p.getInventory().countId(KEY) < 1) {
            p.getActionSender().sendMessage("The chest is locked");
            return;
        }
        p.getInventory().remove(KEY, 1);
        p.getActionSender().sendMessage("You unlock the chest");
        p.getActionSender().sendMessage("@que@All that is in the chest is a message");
        sleep(1800);
        p.getActionSender().sendMessage("@que@You take the message from the chest");
        sleep(1800);
        p.getActionSender().sendMessage("@que@It says dig just behind the south bench in the park");
        setStage(HAS_MESSAGE);
    }

    // ------------------------------------------------------------ flowers --

    /**
     * Digging up the Falador park flowerbed, which is where the quest really
     * ends.
     *
     * The chest used to be the end of it: it announced "Inside you find One
     * Eyed Hector's treasure" and paid out on the spot. That was two objects
     * short. The chest holds a note, and the treasure is buried behind the
     * south bench in Falador park -- attested by packet captures on both
     * halves (the chest text, and separately the aggressive-Wyson and the
     * dig-for-no-reason responses), and by our own Wyson.java, which has
     * described him catching players digging his flowers since before any of
     * it was implemented.
     *
     * Wyson comes first and outranks everything, including having finished
     * the quest: he is a gardener watching someone put a spade in his
     * flowerbed, and he does not ask what it is for.
     */
    private void digFlowers() {
        Player p = getOwner();
        for (Npc n : p.getViewArea().getNpcsInView()) {
            if (n.getID() != WYSON) {
                continue;
            }
            p.informOfNpcMessage(new ChatMessage(n, "Hey leave off my flowers", p));
            n.attackPlayer(p);
            return;
        }

        if (getStage() != HAS_MESSAGE) {
            // Also what a player who never started the quest gets, and what
            // anyone who has already dug gets ever after. There is nothing
            // down there for them either way.
            p.getActionSender().sendMessage("It seems a shame to dig up these nice flowers for no reason");
            return;
        }

        p.getActionSender().sendMessage("@que@You dig a hole in the ground");
        sleep(1800);
        p.getActionSender().sendMessage("@que@You find a little bag of treasure");
        sleep(1800);
        // setStage calls completeQuest() itself on the final stage -- calling
        // it here as well would pay the treasure out twice.
        setStage(getFinalStage());
    }
}
