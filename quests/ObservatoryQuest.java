import org.rscdaemon.server.event.SingleEvent;
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
import org.rscdaemon.server.util.DataConversions;

/**
 * Observatory quest. Released 17 March 2003, written by Ian Taylor.
 *
 * A family of goblins has moved in under the observatory west of the gnome
 * village and broken the telescope. The professor wants three planks, a bronze
 * bar and molten glass; then he wants the lens mould the goblins stole, which
 * is in a sack behind a locked gate at the bottom of the cavern; then he wants
 * the lens itself, because his own crafting is not up to it.
 *
 *     Observatory Professor 652   (715,682) (714,679) (715,681), reception
 *     Observatory assistant 654   (714,683)
 *     Professor 662               (713,698), up on the cliff by the telescope
 *     Goblin guard 651            (690,3512), in the keep
 *
 *     Ladder 928  (712,679)   reception down into the cavern
 *     Ladder 933  (713,695)   observatory down into the cavern
 *     Ladder 5    (712,3511) and (713,3527), the matching climbs back up
 *     Telescope 925 (713,699)
 *     Gate 926    (689,3513)  the keep, locked
 *     sacks 927   (692,3515)  the lens mould
 *
 * The four chest ids in the cavern all examine as "All these chests look the
 * same!" and are told apart only by what happens when you open one:
 *
 *     930 (717,3542)                keep key          opens to 919
 *     935 (705,3520)                cure poison (1)   opens to 934
 *     929 x6                        a dungeon spider  opens to 917
 *     937 (677,3526) (678,3508)     nothing           opens to 936
 *
 * Only the ladders are left to the world: the generic climb-up/climb-down in
 * ObjectAction already moves a player between (x,y) and (x,y+2832), which is
 * exactly what all four of these ladders do. The reception ladder is claimed
 * anyway, because the assistant calls a warning after you as you go down.
 *
 * Deviations:
 *
 *  - Jagex documented a level 10 crafting requirement for the lens and then
 *    never checked it. This checks it: the project's rule is the intended
 *    game, not the shipped bugs, and the requirement was intended. The
 *    refusal message is ours.
 *
 *  - Looking through the telescope opens the big alert window with the
 *    constellation drawn in stars and no name attached, as the real client
 *    did; the player works out which sign it was from the chart. The star
 *    positions are measured from screenshots of the original windows and
 *    reproduce within two pixels. "You look through the telescope" is the
 *    only invented line left in the file -- every other line is Jagex's,
 *    from the transcripts.
 *
 *  - The professor's reward speech names your constellation, so re-using the
 *    telescope before you talk to him re-rolls it, exactly as it did in 2003.
 */
public class ObservatoryQuest extends Quest {

    public final static int UID = Quests.OBSERVATORY_QUEST;

    private static final int PROFESSOR = 652, ASSISTANT = 654;
    private static final int STARGAZER = 662, GUARD = 651, SPIDER = 656;

    private static final int LADDER = 928, LADDER_X = 712, LADDER_Y = 679;
    /* Beside the cavern's climb-up ladder, not on it. Object 5 stands on
     * (712,3511) and a ladder is solid, so that tile walled the player in with
     * nothing to click; (711,3511) is the open corridor next to it. */
    private static final int CAVERN_X = 711, CAVERN_Y = 3511;
    private static final int TELESCOPE = 925;
    private static final int GATE = 926, SACKS = 927;

    private static final int KEY_CHEST = 930, KEY_CHEST_OPEN = 919;
    private static final int POTION_CHEST = 935, POTION_CHEST_OPEN = 934;
    private static final int SPIDER_CHEST = 929, SPIDER_CHEST_OPEN = 917;
    private static final int EMPTY_CHEST = 937, EMPTY_CHEST_OPEN = 936;

    private static final int SAPPHIRE = 160, BRONZE_BAR = 169, PLANK = 410;
    private static final int GLASS = 623, POISON_CURE = 568;
    private static final int KEEP_KEY = 1012, MOULD = 1017, LENS = 1018;

    private static final int WATER_RUNE = 32, LAW_RUNE = 42;
    private static final int AMULET = 315, TUNA = 367, TWO_HANDER = 426;
    private static final int SUPER_STRENGTH = 492, WEAPON_POISON = 572;
    private static final int MAPLE_LONGBOW = 652;

    private static final int ATTACK = 0, DEFENSE = 1, STRENGTH = 2, HITS = 3;
    private static final int CRAFTING = 12;

    private static final int STARTED = 1;
    private static final int PLANKS = 2;
    private static final int BRONZE = 4;
    private static final int GLASS_GIVEN = 8;
    private static final int MOULD_GIVEN = 16;
    private static final int FIXED = 32;
    private static final int GATE_OPEN = 64;
    /** Four bits holding the constellation as 1..12, or 0 for none seen. */
    private static final int SEEN_SHIFT = 7;
    private static final int SEEN_MASK = 1920;
    private static final int FINISHED = 8192;

    /** In the order the professor reads them out, which is alphabetical. */
    private static final String[] SIGNS = {
        "Aquarius", "Aries", "Cancer", "Capricorn", "Gemini", "Leo",
        "Libra", "Pisces", "Sagittarius", "Scorpio", "Taurus", "Virgo"
    };

    /**
     * The twelve constellations as the telescope draws them: rows of
     * spaces and stars laid out for the big alert window, one line per
     * 14-pixel font-1 row, every line padded to the same pixel width so
     * the client's centring keeps the columns still. Star positions are
     * measured from screenshots of the original windows.
     */
    private static final String[] STAR_MAPS = {
        /* Aquarius */
        " % %                                                  *                                                                  %                                                *                                                                    %                                                                  *                                                  %                                          *                                    *                                     %                                                      *                                                              % %                          *                                                                                          %                  *                                                                                                  %                                   *                                                                                 ",
        /* Aries */
        " % % % %                                                    *                                                                %                                                                  *                                                  %                                                                      *                                              %                                          *                                                                          %                                  *                                                                                  %                                                                              *                                      ",
        /* Cancer */
        " % %                                                      *                                                              % % % %                                                    *                                                                % %                                                *                                                                    % % %                                        *                                                                            % %                                                                      *                                              ",
        /* Capricorn */
        " % %                                                                          *                                          %                                                                      *                                              %                                      *                                                                              %                                 *                *                                                                  % %                                                                  *                                                  %                                            *                                                                        % %                                                      *       *                                                      ",
        /* Gemini */
        " % %                                            *                                                                        %                                                      *                                                              %                                      *                                                                              % %                                     *                        *                                                      %                                                                      *                                              % %                                            *                                                                        %                                                  *                                                                  %                                            *                                                                        %                                                                     *                                               ",
        /* Leo */
        " % %                                                              *                                                      %                                                    *                                                                %                                    *                              *                                                 %                                                          *                                                          %                            *                                                                                        %                                            *             *                                                          % %                                        *                                                                            %                                                          *                                                          %                                                                           *                                         ",
        /* Libra */
        " % %                                                            *                                                        %                                *                                                                                    %                                   *             *                                                                   % %                                                                    *                                                % % % %                                                            *                                                        % %                                                    *                                                                ",
        /* Pisces */
        " % %                                          *                                                                          %                                      *                                                                              % %                                    *                                                                                % %                              *                                                                                      % %                        *                                                                                            % %                *          *                    *                                                                   %                                                                *             *                                      %                                                                          *                                          %                                                                                    *                                %                                                                      *        *                                     ",
        /* Sagittarius */
        " % %                                                    *  *                                                             %                                                                        *                                            %                                                                    *                                                %                                                        *                                                            % %                                                                      *                                              %                                                    *                                                                %                                                                            *                                        % %                                                                  *                                                  %                                         *                                                                           ",
        /* Scorpio */
        " % %                                                            *                                                        %                                                                    *                                                %                                                  *                                                                  %                                               *                    *                                                % %                                                                        *                                            %                                        *                                                                            % %                       *              *                                                                              %                   *                    *                                                                            %                          *        *                                                                                 ",
        /* Taurus */
        " % %                                  *                                                                                  % %                                *                                                                                    % %                                                        *                                                            %                                                    *                                                                %                                                          *                                                          %                                                                            *                                        %                                                                   *           *                                     % %                                                                *                                                    % %                                                                    *                                                ",
        /* Virgo */
        " % %                                                *                                                                    %                      *                                                                                              %                            *                                   *                                                    %                                                        *                  *                                         %                         *         *                                                                                 %                                                             *     *                                                 %                              *                          *                                                           %                                                      *                                                              %                                  *                                                                                  %                                                   *                                                                 "
    };


    public ObservatoryQuest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Observatory quest");
        setFinalStage(FINISHED);
        associateNpc(PROFESSOR);
        associateNpc(ASSISTANT);
        associateNpc(STARGAZER);
        associateNpc(GUARD);
        associateObject(LADDER, LADDER_X, LADDER_Y);
        associateObject(TELESCOPE);
        associateObject(GATE);
        associateObject(SACKS);
        associateObject(KEY_CHEST);
        associateObject(POTION_CHEST);
        associateObject(SPIDER_CHEST);
        associateObject(EMPTY_CHEST);
        /* Both halves, so that glass on the mould reaches ITEM_ON_ITEM. */
        associateItem(GLASS);
        associateItem(MOULD);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("Goblins living under the observatory west of the gnome village have broken the telescope; gather the parts, recover the stolen lens mould, and help the professor repair it.");
        setStartPoint("The observatory reception, west of the gnome village");
        setSpeakTo("Observatory Professor");
        rewardItem(SAPPHIRE, 1);
        rewardExp(CRAFTING, 250, 100);
        rewardOther("A further reward decided by the constellation seen through the telescope");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Observatory quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    private void take(int id, int amount) {
        Player p = getOwner();
        p.getInventory().remove(id, amount);
        p.getActionSender().sendInventory();
    }

    /** 0 when nothing has been seen, else 1..12. */
    private int seen() {
        return questStarted() ? (getStage() & SEEN_MASK) >> SEEN_SHIFT : 0;
    }

    private void setSeen(int sign) {
        int base = (questStarted() ? getStage() : 0) & ~SEEN_MASK;
        setStage(base | STARTED | ((sign + 1) << SEEN_SHIFT));
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            Npc npc = (Npc) entity;
            switch (npc.getID()) {
                case PROFESSOR: professor(npc); return;
                case ASSISTANT: assistant(npc); return;
                case STARGAZER: stargazer(npc); return;
                case GUARD:     guard(npc); return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_ON_ITEM) {
                craftLens((InvItem) entity, used);
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (object.getID() == GATE) {
                unlockGate(used);
            } else {
                getOwner().getActionSender().sendMessage("Nothing interesting happens");
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case LADDER:        climbDown(); return;
            case TELESCOPE:     telescope(); return;
            case GATE:          openGate(); return;
            case SACKS:         sacks(); return;
            case KEY_CHEST:     keyChest(object); return;
            case POTION_CHEST:  potionChest(object); return;
            case SPIDER_CHEST:  spiderChest(object); return;
            case EMPTY_CHEST:   emptyChest(object); return;
        }
    }

    // -------------------------------------------------------------- ladder --

    /**
     * The assistant calls a warning down after you. He is four tiles from the
     * ladder and always in view, but the fall-back matters if he is ever moved.
     */
    private void climbDown() {
        final Player p = getOwner();
        Npc assistant = null;
        for (Npc n : p.getViewArea().getNpcsInView()) {
            if (n.getID() == ASSISTANT) {
                assistant = n;
                break;
            }
        }
        if (assistant == null) {
            p.teleport(CAVERN_X, CAVERN_Y, false);
            return;
        }
        new Conversation(p, assistant)
            .npc("No problem at all, come and visit again")
            .npc("Take great care down there")
            .npc("Remember the goblins have taken over the cavern")
            .player("Oh, okay thanks for the warning")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(CAVERN_X, CAVERN_Y, false);
                }
            })
            .start();
    }

    // --------------------------------------------------------------- keep --

    private void unlockGate(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != KEEP_KEY) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        take(KEEP_KEY, 1);
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("The gate unlocks");
        p.getActionSender().sendMessage("The keep key is broken - I'll discard it");
        set(GATE_OPEN);
    }

    /**
     * The gate is a piece of scenery rather than a door, so crossing it is a
     * step through the wall line, which runs east-west: (690,3513) is the
     * corridor outside the keep and (690,3514) is inside it.
     */
    private void openGate() {
        Player p = getOwner();
        if (!has(GATE_OPEN)) {
            p.getActionSender().sendMessage("The gate is locked");
            return;
        }
        boolean outside = p.getY() < 3514;
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("you go through the gate");
        p.teleport(690, outside ? 3514 : 3513, false);
        if (outside) {
            new Conversation(p, null)
                .player("I'd better be quick")
                .player("There may be more guards about")
                .start();
        }
    }

    private void sacks() {
        Player p = getOwner();
        if (holding(MOULD)) {
            p.getActionSender().sendMessage("You already have this lens mould");
            p.getActionSender().sendMessage("Another one will be of no use");
            return;
        }
        p.getActionSender().sendMessage("Underneath you find a peculiar mould");
        give(MOULD, 1);
    }

    // -------------------------------------------------------------- chests --

    /** Swing a chest open for a moment. The map shuts it again. */
    private void open(GameObject chest, int openID) {
        getOwner().getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(chest.getLocation(), openID,
            chest.getDirection(), chest.getType()));
        world.delayedSpawnObject(chest.getLoc(), 10000);
    }

    private void keyChest(GameObject chest) {
        Player p = getOwner();
        open(chest, KEY_CHEST_OPEN);
        if (holding(KEEP_KEY)) {
            p.getActionSender().sendMessage("You already have a key");
            return;
        }
        p.getActionSender().sendMessage("You find a small key inside the chest");
        give(KEEP_KEY, 1);
    }

    private void potionChest(GameObject chest) {
        Player p = getOwner();
        open(chest, POTION_CHEST_OPEN);
        p.getActionSender().sendMessage("You find a potion inside the chest");
        give(POISON_CURE, 1);
    }

    /**
     * The spider is spawned on the chest and set on the player. It does not
     * respawn, and it clears itself up if nobody has killed it in five minutes.
     */
    private void spiderChest(GameObject chest) {
        final Player p = getOwner();
        open(chest, SPIDER_CHEST_OPEN);
        p.getActionSender().sendMessage("@red@A spider jumps out of the chest!");
        int x = chest.getX(), y = chest.getY();
        final Npc spider = new Npc(SPIDER, x, y, x - 2, x + 2, y - 2, y + 2);
        spider.setRespawn(false);
        world.registerNpc(spider);
        spider.attackPlayer(p);
        world.getDelayedEventHandler().add(new SingleEvent(null, 300000){
            public void action() {
                if (spider.getID() == SPIDER) {
                    world.unregisterNpc(spider);
                }
            }
        });
    }

    private void emptyChest(GameObject chest) {
        open(chest, EMPTY_CHEST_OPEN);
        getOwner().getActionSender().sendMessage("You find nothing inside the chest");
    }

    // ---------------------------------------------------------- the lens --

    private void craftLens(InvItem first, InvItem second) {
        Player p = getOwner();
        int a = first.getID(), b = second.getID();
        if (!((a == GLASS && b == MOULD) || (a == MOULD && b == GLASS))) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!has(MOULD_GIVEN)) {
            p.getActionSender().sendMessage("I have no reason to do that");
            return;
        }
        if (p.getMaxStat(CRAFTING) < 10) {
            p.getActionSender().sendMessage("You need a crafting level of 10 to make the lens");
            return;
        }
        take(GLASS, 1);
        give(LENS, 1);
        p.getActionSender().sendMessage("You cast the molten glass in the mould");
        p.getActionSender().sendMessage("@gre@It cools into a perfectly formed lens");
    }

    // ---------------------------------------------------------- telescope --

    private void telescope() {
        Player p = getOwner();
        if (!has(FIXED)) {
            p.getActionSender().sendMessage("The telescope is broken");
            p.getActionSender().sendMessage("Perhaps the professor knows more about it");
            return;
        }
        int sign = DataConversions.random(0, SIGNS.length - 1);
        setSeen(sign);
        p.getActionSender().sendMessage("You look through the telescope");
        p.getActionSender().sendAlert(STAR_MAPS[sign], true);
    }

    // ------------------------------------------------------- the reception --

    private void professor(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Aha, my friend returns")
                .npc("Thanks for all your help with the telescope")
                .npc("What can I do for you ?")
                .options(new Choice("Do you have any more quests ?", "Nothing, thanks") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("No I'm all out of quests now")
                             .npc("But the stars may hold a secret for you...");
                        } else {
                            c.npc("Okay no problem");
                        }
                    }
                })
                .start();
            return;
        }
        if (has(FIXED)) {
            new Conversation(p, npc)
                .npc("The telescope is now repaired")
                .npc("Let's go to the Observatory")
                .start();
            return;
        }
        if (has(MOULD_GIVEN)) {
            lensStage(npc);
            return;
        }
        if (has(GLASS_GIVEN)) {
            mouldStage(npc);
            return;
        }
        if (has(BRONZE)) {
            glassStage(npc);
            return;
        }
        if (has(PLANKS)) {
            bronzeStage(npc);
            return;
        }
        if (questStarted()) {
            plankStage(npc);
            return;
        }
        offer(npc);
    }

    private void offer(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Hello adventurer")
            .npc("What brings you to these parts ?")
            .options(new Choice("I am lost!!!",
                                "I'd like to have a look through that telescope",
                                "Whats the ladder over there for ?",
                                "It is of no concern of yours...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Lost ? it must have been those gnomes that have lead you astray")
                         .npc("Head North-East to find the land Ardougne")
                         .player("I'm sure I'll find the way")
                         .player("Thanks for your help")
                         .npc("No problem at all, come and visit again");
                        return;
                    }
                    if (option == 2) {
                        c.npc("The ladder leads to the entrance of the cavern")
                         .npc("That leads from here to the observatory");
                        return;
                    }
                    if (option == 3) {
                        c.npc("Okay Okay, there's no need to be insulting!");
                        return;
                    }
                    c.npc("So would I !!")
                     .npc("The trouble is, its not working")
                     .player("What do you mean ?")
                     .npc("Did you see those houses outside ?")
                     .player("Yes, I've seen them")
                     .npc("Well it's a family of goblins")
                     .npc("Since they moved here they cause me nothing but trouble")
                     .npc("Last week my telescope was tampered with")
                     .npc("And now parts need replacing before it can be used again")
                     .npc("Err, I don't suppose you would be willing to help?")
                     .options(new Choice("Sounds interesting, what can I do for you ?",
                                         "Oh sorry, I don't have time for that") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("Oh dear, I really do need some help")
                                  .npc("If you see anyone who can help please send them my way");
                                 return;
                             }
                             c.npc("Oh thanks so much!")
                              .npc("I need three new parts for the telescope so it can be used again")
                              .npc("I need wood to make a new tripod")
                              .npc("Bronze to make a new tube")
                              .npc("And glass for a replacement lens")
                              .npc("My assistant will help you obtaining these")
                              .npc("Ask him if you need any help")
                              .player("Okay what do I need to do ?")
                              .npc("First I need three planks of wood for the tripod")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      set(STARTED);
                                  }
                              });
                         }
                     });
                }
            }.says(2, "What's the ladder there for ?"))
            .start();
    }

    private void plankStage(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("I'ts my helping hand back again!")
            .npc("Do you have the planks yet ?")
            .options(new Choice("Yes I've got them", "No, sorry not yet") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Oh dear, well please bring them soon");
                        return;
                    }
                    if (getOwner().getInventory().countId(PLANK) < 3) {
                        c.npc("You don't seem to have enough planks!")
                         .npc("I need three in total");
                        return;
                    }
                    c.npc("Well done, I can start the tripod construction now")
                     .npc("Now for the bronze")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             take(PLANK, 3);
                             set(PLANKS);
                         }
                     });
                }
            })
            .start();
    }

    private void bronzeStage(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Hello again, do you have the bronze yet ?")
            .options(new Choice("Yes I have it", "I'm still looking") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Please carry on trying to find some");
                        return;
                    }
                    if (!holding(BRONZE_BAR)) {
                        c.npc("That's not bronze!")
                         .npc("Please bring me some");
                        return;
                    }
                    c.npc("Great, now all I need is the lens made")
                     .npc("Next on the list is molten glass")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             take(BRONZE_BAR, 1);
                             set(BRONZE);
                         }
                     });
                }
            })
            .start();
    }

    /**
     * He asks for the glass and then hands it straight back, so nothing is
     * taken here: the same molten glass goes into the mould later on.
     */
    private void glassStage(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("How are you getting on finding me some glass ?")
            .options(new Choice("Here it is!", "No luck yes I'm afraid") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("I hope you find some soon");
                        return;
                    }
                    if (!holding(GLASS)) {
                        c.npc("Sorry, you don't have any glass with you")
                         .npc("Please don't tease me, I really need this part!");
                        return;
                    }
                    c.npc("Excellent! now all I need is to make the lens")
                     .npc("Oh no, I can't use this glass!")
                     .npc("Until I find the lens mould used to cast it")
                     .player("What do you mean, lens mould")
                     .npc("I need my lens mould")
                     .npc("Without it I'll never get the correct shape")
                     .npc("I'll have to ask you to try and find it")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(GLASS_GIVEN);
                         }
                     });
                }
            }.says(1, "No luck yet I'm afraid"))
            .start();
    }

    private void mouldStage(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Did you bring me the mould ?")
            .options(new Choice("Yes, I've managed to find it",
                                "I haven't found it yet",
                                "I had it then lost it") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Perhaps the goblins have stolen it ?");
                        return;
                    }
                    if (option == 2) {
                        c.npc("Well, I wouldn't worry")
                         .npc("No doubt the goblins copied the design")
                         .npc("I'm sure if you checked again")
                         .npc("You'll find another one");
                        return;
                    }
                    if (!holding(MOULD)) {
                        c.npc("Where is the mould! You dont even have it on you")
                         .npc("Please try and find it");
                        return;
                    }
                    c.npc("At last you've brought all the items I need")
                     .npc("To repair the telescope")
                     .npc("Oh no! I can't do this")
                     .player("What do you mean ?")
                     .npc("My crafting skill is not good enough")
                     .npc("To finish this off")
                     .npc("Are you skilled at crafting ?")
                     .options(new Choice("Yes I have much experience in crafting",
                                         "No sorry I'm not good at that") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("Oh dear, without the lens its useless")
                                  .npc("Maybe you'll find someone who can Finish the job for you ?");
                                 return;
                             }
                             c.npc("Thank goodness for that!")
                              .npc("You can use the mould with molten glass")
                              .npc("To make a new lens")
                              .npc("As long as you have practised your crafting skills")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      set(MOULD_GIVEN);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    private void lensStage(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .npc("Is the lens finished ?")
            .options(new Choice("Yes here it is", "I haven't finished it yet") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Oh, okay please hurry");
                        return;
                    }
                    if (!holding(LENS)) {
                        c.npc("Why do you tell lies ?")
                         .npc("Please come back when the lens is made");
                        return;
                    }
                    c.npc("Wonderful, at last I can fix the telescope")
                     .npc("I'll take back that mould for use again")
                     .npc("Meet me at the Observatory later...")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             take(LENS, 1);
                             if (holding(MOULD)) {
                                 take(MOULD, 1);
                             }
                             set(FIXED);
                         }
                     });
                }
            })
            .start();
    }

    private void assistant(Npc npc) {
        Player p = getOwner();
        if (has(FIXED) || completed()) {
            if (has(FIXED) && !completed()) {
                new Conversation(p, npc)
                    .npc("Well hello again")
                    .npc("thanks for helping out the professor")
                    .npc("You've made my life much easier!")
                    .npc("Have a drink on me!")
                    .player("Thanks very much")
                    .start();
            } else {
                new Conversation(p, npc).npc("Thanks again").start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .npc("Hello wanderer")
                .npc("Do you require any assistance ?")
                .options(new Choice("Yes, what do you two do here ?",
                                    "Can I have a look through that telescope ?",
                                    "No, just looking around thanks") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("This is the observatory reception")
                             .npc("Up on the cliff is the observatory dome")
                             .npc("From here we view the heavens")
                             .npc("That is before the telescope was damaged")
                             .npc("By those monsters outside...");
                        } else if (option == 1) {
                            c.npc("I'm sorry but it's broken!")
                             .npc("The Professor will explain if you speak to him");
                        } else {
                            c.npc("Okay, be my guest")
                             .npc("If you need any help let me know...");
                        }
                    }
                })
                .start();
            return;
        }
        final String[] answer;
        String ask;
        if (has(MOULD_GIVEN)) {
            ask = "I can't make the lens!";
            answer = new String[] { "Crafting objects like this requires skill",
                                    "You may need to practice more first..." };
        } else if (has(GLASS_GIVEN)) {
            ask = "I can't find the lens mould";
            answer = new String[] { "Can't you find the mould ?",
                                    "I'm sure I heard one of those goblins talking about it...",
                                    "I bet they have hidden it somewhere" };
        } else if (has(BRONZE)) {
            ask = "I'm having problems getting glass";
            answer = new String[] { "Don't you know how to make glass ?",
                                    "Unfortunately we dont have those skills",
                                    "I remember reading about that somewhere..." };
        } else if (has(PLANKS)) {
            ask = "I can't see any bronze around";
            answer = new String[] { "You'll need to mix purified copper and tin together",
                                    "To produce this metal" };
        } else {
            ask = "I can't find any planks!";
            answer = new String[] { "I understand planks can be found at the barbarian outpost",
                                    "To the north east of ardougne",
                                    "You will probably have to trek over there to find some..." };
        }
        new Conversation(p, npc)
            .npc("How can I help you ?")
            .options(new Choice(ask, "I don't need any help thanks") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Oh, okay then if you are sure");
                        return;
                    }
                    for (int i = 0; i < answer.length; i++) {
                        c.npc(answer[i]);
                    }
                }
            })
            .start();
    }

    // -------------------------------------------------------- the cliff top --

    private void stargazer(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Hello friend")
                .npc("The stars hold many secrets")
                .npc("The moon rises in Scorpio...")
                .start();
            return;
        }
        if (!has(FIXED)) {
            if (!questStarted()) {
                new Conversation(p, npc)
                    .npc("Hello friend")
                    .npc("This is my poorly telescope")
                    .npc("It's been tampered with and is not working")
                    .npc("If your good at crafting")
                    .npc("I would appreciate your help!")
                    .npc("Come to the reception if you can")
                    .start();
            } else {
                new Conversation(p, npc)
                    .npc("Hello friend")
                    .npc("I hope you get all the parts soon")
                    .npc("Return to the reception")
                    .npc("When you have the things I need")
                    .start();
            }
            return;
        }
        if (seen() == 0) {
            new Conversation(p, npc)
                .npc("Hello friend")
                .npc("It's time to use the telescope")
                .start();
            return;
        }
        reward(npc);
    }

    /**
     * The reward speech. Both of the top options run into the same reading of
     * the constellation, which is why the wiki records the second as pointing
     * back at the first.
     */
    private void reward(Npc npc) {
        Player p = getOwner();
        final int sign = seen() - 1;
        new Conversation(p, npc)
            .npc("Well done, well done!!")
            .npc("Let's see what the stars have in store for us today")
            .options(new Choice("I can see a constellation",
                                "What am I looking at ?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Yes, with this device")
                         .npc("The heavens are opened to us...");
                    } else {
                        c.npc("This is the revealed sky");
                    }
                    c.npc("The constellation you saw was");
                    for (int i = 0; i < READING[sign].length; i++) {
                        c.npc(READING[sign][i]);
                    }
                    c.npc("By Saradomin's earlobes!")
                     .npc("You must be a friend of the gods indeed")
                     .npc("Look in your backpack for your reward")
                     .npc("In payment for your work")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             grant(sign);
                             setStage(FINISHED);
                         }
                     })
                     .npc("Now I have work to do...");
                }
            })
            .start();
    }

    private static final String[][] READING = {
        { "Aquarius the water-bearer", "the Gods of water award you with water runes" },
        { "Aries the ram", "The ram's strength improves your attack abilities" },
        { "Cancer the crab", "The armoured crab gives you an amulet of protection" },
        { "Capricorn the goat", "you are granted an increase in strength" },
        { "Gemini the twins", "The double nature of Gemini awards you a two-handed weapon" },
        { "Leo the lion", "The power of the lion has increased your hitpoints" },
        { "Libra the scales", "The scales of justice award you with Law Runes" },
        { "Pisces the fish", "The gods rain food from the sea on you" },
        { "Sagittarius the Centaur", "The Gods award you a maple longbow" },
        { "Scorpio the scorpion", "The scorpion gives you poison from it's sting" },
        { "Taurus the bull", "You are given the strength of a bull" },
        { "Virgo the virtuous", "The strong and peaceful nature of virgo boosts your defence" }
    };

    private void grant(int sign) {
        Player p = getOwner();
        switch (sign) {
            case 0:  give(WATER_RUNE, 25); return;
            case 1:  bless(ATTACK); return;
            case 2:  give(AMULET, 1); return;
            case 3:  bless(STRENGTH); return;
            case 4:  give(TWO_HANDER, 1); return;
            case 5:  bless(HITS); return;
            case 6:  give(LAW_RUNE, 3); return;
            case 7:  give(TUNA, 1); give(TUNA, 1); give(TUNA, 1); return;
            case 8:  give(MAPLE_LONGBOW, 1); return;
            case 9:  give(WEAPON_POISON, 1); return;
            case 10: give(SUPER_STRENGTH, 1); return;
            case 11: bless(DEFENSE); return;
        }
    }

    private void bless(int skill) {
        Player p = getOwner();
        p.incExp(skill, (p.getMaxStat(skill) * 25) + 125, false);
        p.getActionSender().sendStat(skill);
    }

    private void guard(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("What are you doing here ?")
            .npc("This is our domain now")
            .npc("Begone foul human!")
            .start();
    }
}
