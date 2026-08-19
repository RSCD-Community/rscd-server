import java.util.List;

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
 * The knight's sword, 6 April 2001, by Paul Gower.
 *
 * Sir Vyvin's squire has lost the ceremonial sword he was carrying up from
 * Lumbridge, and would rather replace it than admit to it. The replacement has
 * to be made by an Imcando dwarf, a tribe everybody believes died out in the
 * barbarian invasions; Reldo knows better, and knows what they will work for,
 * which is redberry pie.
 *
 * One quest point, and smithing experience worth level * 375 + 350 -- the
 * largest of the free quest experience rewards, and the reason it is worth
 * training smithing to 15 before handing the sword in rather than after.
 *
 * Three npcs, in three cities. The squire 132 starts and ends it in the White
 * Knights Castle; Reldo 20 gives the one piece of lore that makes it solvable;
 * Thurgo 134 does all the work. Reldo is not associated here -- he belongs to
 * Shield of Arrav, which owns the whole of his dialogue and answers the Imcando
 * question there once this quest has been started. A quest may read another
 * quest's stage; it may never write it.
 *
 * The cupboard is the second of Jagex's two-player puzzles, after Shield of
 * Arrav's. Sir Vyvin is standing next to it and will not have it searched while
 * he can see, so somebody else has to be talking to him at the moment it is
 * opened. Like the gang split it is kept as designed rather than quietly made
 * soloable; if a single-player shortcut is ever wanted it belongs beside this,
 * not inside it.
 *
 * Two deviations, both noted where they happen:
 *
 * - Jagex's squire forgets about the portrait as soon as he has mentioned it,
 *   and goes back to saying "I'm still looking for Imcando dwarves" even though
 *   the player now knows better. That is a slip in his state machine, not a
 *   design, and it strands anyone who did not read the hint the first time. The
 *   hint here stays available for as long as the portrait is wanted.
 *
 * - Blurite rock 176 had no entry in ObjectMining, so blurite ore could not be
 *   mined at all and this quest could not be finished. The entry has been added
 *   to conf/server/defs/extras/ObjectMining.xml with Jagex's own figures --
 *   mining 10, 18 experience.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TheKnightsSword extends Quest {

    public final static int UID = Quests.THE_KNIGHTS_SWORD;

    /** The squire has asked for the Imcando dwarves to be tracked down. */
    private static final int STARTED = 1;
    /** Thurgo has been fed his pie and will talk properly. */
    private static final int FED_PIE = 2;
    /** Thurgo has agreed to make the sword, and wants a picture of it. */
    private static final int NEEDS_PORTRAIT = 3;
    /** He has the picture, and wants blurite ore and two iron bars. */
    private static final int NEEDS_MATERIALS = 4;
    /** The sword has been made. It can be made again, which is the drop trick. */
    private static final int MADE_SWORD = 5;
    private static final int FINISHED = 6;

    private static final int SQUIRE = 132;
    private static final int SIR_VYVIN = 138;
    private static final int THURGO = 134;

    /**
     * The open cupboard in Sir Vyvin's room, on the second floor of the White
     * Knights Castle. 174 is the same cupboard shut, which is what closing it
     * puts there for a moment.
     */
    private static final int CUPBOARD = 175;
    private static final int CUPBOARD_SHUT = 174;
    private static final int CUPBOARD_X = 318;
    private static final int CUPBOARD_Y = 2454;

    private static final int REDBERRY_PIE = 258;
    private static final int PORTRAIT = 264;
    private static final int BLURITE_ORE = 266;
    private static final int IRON_BAR = 170;
    private static final int SWORD = 265;

    private static final int SMITHING = 13;

    public TheKnightsSword(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("The knight's sword");
        setFinalStage(FINISHED);
        associateNpc(SQUIRE);
        associateNpc(SIR_VYVIN);
        associateNpc(THURGO);
        associateObject(CUPBOARD);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Sir Vyvin's squire is in trouble. He has lost Sir Vyvin's ceremonial sword. Help him find a replacement without Sir Vyvin finding out.");
        setStartPoint("White knight's castle, falador");
        setSpeakTo("Squire");
        setMissionLength("Long");
        require("Mining level 10");
        require("Must not be afraid of lvl 68 Ice Giant");
        rewardExp(SMITHING, 350, 375);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed The knight's sword quest");
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger == QuestTrigger.NPC_TALK && entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (npc.getID() == SQUIRE) {
                talkToSquire(npc);
            } else if (npc.getID() == SIR_VYVIN) {
                talkToVyvin(npc);
            } else if (npc.getID() == THURGO) {
                talkToThurgo(npc);
            }
            return;
        }
        if (entity instanceof GameObject && ((GameObject) entity).getID() == CUPBOARD) {
            GameObject cupboard = (GameObject) entity;
            if (trigger == QuestTrigger.OBJECT_ACT1) {
                searchCupboard(cupboard);
            } else if (trigger == QuestTrigger.OBJECT_ACT2) {
                closeCupboard(cupboard);
            }
        }
    }

    // ------------------------------------------------------------ helpers --

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    private boolean carrying(int id) {
        return count(id) > 0;
    }

    /** Blurite ore and the two iron bars, all in the pack at once. */
    private boolean gotMaterials() {
        return carrying(BLURITE_ORE) && count(IRON_BAR) >= 2;
    }

    // ------------------------------------------------------------- squire --

    private void talkToSquire(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Hello friend")
             .npc("thanks for your help before")
             .npc("Vyvin never even realised it was a different sword")
             .start();
            return;
        }

        if (questStarted()) {
            if (carrying(SWORD)) {
                c.player("I have retrieved your sword for you")
                 .take(SWORD, 1)
                 .npc("Thankyou, Thankyou")
                 .npc("I was seriously worried I'd have to own up to Sir Vyvin")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(FINISHED);
                     }
                 })
                 .start();
                return;
            }
            c.npc("So how are you doing getting a sword?");
            if (getStage() >= NEEDS_MATERIALS) {
                c.player("I've found a dwarf who will make the sword")
                 .player("I've just got to find the materials for it now");
            } else if (getStage() == NEEDS_PORTRAIT) {
                // Jagex's squire says this once and then forgets it -- see the
                // class comment. Here it stays said for as long as it is true.
                c.player("I've found an Imcando dwarf")
                 .player("But he needs a picture of the sword before he can make it")
                 .npc("A picture eh?")
                 .npc("The only one I can think of is in a small portrait of Sir Vyvin's father")
                 .npc("Sir Vyvin keeps it in a cupboard in his room I think");
            } else {
                c.player("I'm still looking for Imcando dwarves");
            }
            c.start();
            return;
        }

        c.npc("Hello I am the squire to Sir Vyvin")
         .player("And how is life as a squire?")
         .npc("Well Sir Vyvin is a good guy to work for")
         .npc("However I'm in a spot of trouble today")
         .npc("I've gone and lost Sir Vyvin's sword")
         .player("Do you know where you lost it?")
         .npc("Well now if I knew that")
         .npc("It wouldn't be lost,now would it?")
         .player("Well do you know the vague area you lost it in?")
         .npc("No I was carrying it for him all the way from where he had it stored in Lumbridge")
         .npc("It must have slipped from my pack during the trip")
         .npc("And you know what people are like these days")
         .npc("Someone will have just picked it up and kept it for themselves")
         .options(squireMenu())
         .start();
    }

    /**
     * The squire's opening menu. Built fresh each time it is offered, because
     * three of the five answers put the player straight back in front of it --
     * the transcript's "[above]" -- and a Choice is consumed when it is picked.
     */
    private Choice squireMenu() {
        return new Choice("I can make a new sword if you like",
                          "Well the kingdom is fairly abundant with swords",
                          "Is he angry?",
                          "Wouldn't you prefer to be a squire for me?",
                          "Well I hope you find it soon") {
            public void picked(int option, Conversation c) {
                switch (option) {
                case 0:
                    offerToHelp(c);
                    return;
                case 1:
                    c.npc("Yes you can get bronze swords anywhere")
                     .npc("But this isn't any old sword")
                     .npc("The thing is,this sword is a family heirloom")
                     .options(squireMenu());
                    return;
                case 2:
                    c.npc("He doesn't know yet")
                     .npc("I was hoping I could think of something to do")
                     .npc("Before he does find out")
                     .npc("But I find myself at a loss")
                     .options(squireMenu());
                    return;
                case 3:
                    c.npc("No, sorry I'm loyal to Vyvin");
                    return;
                default:
                    c.npc("Yes me too")
                     .npc("I'm not looking forward to telling Vyvin I've lost it")
                     .npc("He's going to want it for the parade next week as well");
                }
            }
        };
    }

    private void offerToHelp(Conversation c) {
        c.npc("Thanks for the offer")
         .npc("I'd be surprised if you could though")
         .npc("The thing is,this sword is a family heirloom")
         .npc("It has been passed down through Vyvin's family for five generations")
         .npc("It was originally made by the Imcando Dwarves")
         .npc("Who were a particularly skilled tribe of dwarven smiths")
         .npc("I doubt anyone could make it in the style they do")
         .player("So would these dwarves make another one?")
         .npc("I'm not a hundred percent sure the Imcando tribe exists anymore")
         .npc("I should think Reldo the palace librarian in Varrock will know")
         .npc("He has done a lot of research on the races of Runescape")
         .npc("I don't suppose you could try and track down the Imcando dwarves for me?")
         .npc("I've got so much work to do")
         .options(new Choice("Ok I'll give it a go",
                             "No I've got lots of mining work to do") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     return;
                 }
                 c.npc("Thankyou very much")
                  .npc("As I say the best place to start should be with Reldo")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  });
             }
         });
    }

    // ---------------------------------------------------------- sir vyvin --

    /**
     * Sir Vyvin has nothing to do with the quest and does not know it is
     * happening. He is here because the cupboard is his: whoever is talking to
     * him is the distraction, so his dialogue has to be long enough to hold him
     * still for a moment.
     */
    private void talkToVyvin(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("Hello")
            .npc("Greetings traveller")
            .options(new Choice("Do you have anything to trade?",
                                "Why are there so many knights in this city?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("No I'm sorry");
                        return;
                    }
                    c.npc("We are the White Knights of Falador")
                     .npc("We are the most powerfull order of knights in the land")
                     .npc("We are helping the king Vallance rule the kingdom")
                     .npc("As he is getting old and tired");
                }
            })
            .start();
    }

    // ---------------------------------------------------------- cupboard --

    /** Sir Vyvin, if he is standing where the player can see him. */
    private Npc vyvinInView() {
        List<Npc> inView = getOwner().getViewArea().getNpcsInView();
        for (Npc npc : inView) {
            if (npc.getID() == SIR_VYVIN) {
                return npc;
            }
        }
        return null;
    }

    private void searchCupboard(GameObject cupboard) {
        Player p = getOwner();
        if (cupboard.getX() != CUPBOARD_X || cupboard.getY() != CUPBOARD_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (completed()) {
            p.getActionSender().sendMessage("There is just a load of junk in here");
            return;
        }

        Npc vyvin = vyvinInView();
        // He is distracted only while somebody else is holding him in
        // conversation. The player doing the searching cannot be that somebody:
        // a conversation marks them busy, and a busy player cannot click on a
        // cupboard.
        boolean distracted = vyvin == null
            || (vyvin.getBlocker() != null && vyvin.getBlocker() != p);
        if (!distracted) {
            new Conversation(p, vyvin)
                .npc("Hey what are you doing?")
                .npc("That's my cupboard")
                .start();
            return;
        }
        if (getStage() != NEEDS_PORTRAIT || carrying(PORTRAIT)) {
            p.getActionSender().sendMessage("There is just a load of junk in here");
            return;
        }
        p.getInventory().add(new InvItem(PORTRAIT, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find a small portrait in here which you take");
    }

    private void closeCupboard(GameObject cupboard) {
        Player p = getOwner();
        p.getActionSender().sendMessage("You close the cupboard");
        world.registerGameObject(new GameObject(cupboard.getLocation(), CUPBOARD_SHUT,
            cupboard.getDirection(), cupboard.getType()));
        world.delayedSpawnObject(cupboard.getLoc(), 3000);
    }

    // ------------------------------------------------------------ thurgo --

    private void talkToThurgo(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        /*
         * He says nothing at all until the player has a reason to be asking.
         * We used to give the full "Hello are you are an Imcando Dwarf?"
         * exchange to anybody who wandered past, which hands out the answer to
         * the quest's one puzzle before the quest exists.
         *
         * Jagex's own gate is Reldo rather than the squire -- the transcript
         * heading is "Before speaking to Reldo in The knight's sword quest",
         * so a player who has started but not yet been told about the Imcando
         * still gets this. We cannot ask that question: Reldo belongs to Shield
         * of Arrav and records nothing about having answered it, and inventing a
         * flag now would strike every player who is already mid-quest dumb. So
         * the gate here is the squire, which is the same shape one step earlier
         * and errs towards talking rather than towards silence.
         */
        if (!questStarted()) {
            getOwner().getActionSender().sendMessage("Thurgo doesn't appear to be interested in talking");
            return;
        }

        if (completed()) {
            c.player("Thanks for your help in getting the sword for me")
             .npc("No worries mate")
             .start();
            return;
        }

        c.player("Hello are you are an Imcando Dwarf?")
         .npc("Yeah what about it?");

        if (getStage() >= NEEDS_MATERIALS) {
            materials(c);
            c.start();
            return;
        }
        if (getStage() == NEEDS_PORTRAIT) {
            if (carrying(PORTRAIT)) {
                c.player("I have found a picture of the sword I would like you to make")
                 .take(PORTRAIT, 1)
                 .message("You give the portrait to Thurgo")
                 .message("Thurgo studies the portrait")
                 .npc("Ok you'll need to get me some stuff for me to make this")
                 .npc("I'll need two Iron bars to make the sword to start with")
                 .npc("I'll also need an ore called blurite")
                 .npc("It's useless for making actual weapons for fighting with")
                 .npc("But I'll need some as decoration for the hilt")
                 .npc("It is a fairly rare sort of ore")
                 .npc("The only place I know where to get it")
                 .npc("Is under this cliff here")
                 .npc("But it is guarded by a very powerful ice giant")
                 .npc("Most the rocks in that clif are pretty useless")
                 .npc("Don't contain much of anything")
                 .npc("But there's definitly some blurite in there")
                 .npc("You'll need a little bit of mining experience")
                 .npc("TO be able to find it")
                 .player("Ok I'll go and find them")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(NEEDS_MATERIALS);
                     }
                 });
            } else {
                c.npc("Have you got a picture of the sword for me yet?")
                 .player("Sorry not yet");
            }
            c.start();
            return;
        }

        // Before he has been fed he is no use to anybody, and the pie is the
        // only thing that changes his mind. The offer is on the menu whenever
        // one is in the pack, because he never says no to another.
        if (carrying(REDBERRY_PIE)) {
            c.options(new Choice("Can you make me a special sword?",
                                 "Would you like some redberry Pie?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        pie(c);
                        return;
                    }
                    if (getStage() == FED_PIE) {
                        agreesToMakeIt(c);
                        return;
                    }
                    c.npc("no I don't do that anymore")
                     .npc("I'm getting old");
                }
            });
        } else if (getStage() == FED_PIE) {
            c.options(new Choice("Can you make me a special sword?") {
                public void picked(int option, Conversation c) {
                    agreesToMakeIt(c);
                }
            });
        } else if (questStarted()) {
            c.options(new Choice("Can you make me a special sword?") {
                public void picked(int option, Conversation c) {
                    c.npc("no I don't do that anymore")
                     .npc("I'm getting old");
                }
            });
        }
        c.start();
    }

    private void pie(Conversation c) {
        c.message("Thurgo's eyes light up")
         .npc("I'd never say no to a redberry pie")
         .npc("It's great stuff")
         .take(REDBERRY_PIE, 1)
         .message("You hand over the pie")
         .message("Thurgo eats the pie")
         .message("Thurgo pats his stomach")
         .npc("By Guthix that was good pie")
         .npc("Anyone who makes pie like that has gotta be alright")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (questStarted() && getStage() < FED_PIE) {
                     setStage(FED_PIE);
                 }
             }
         });
    }

    private void agreesToMakeIt(Conversation c) {
        c.npc("Well after you've brought me such a great pie")
         .npc("I guess I should give it a go")
         .npc("What sort of sword is it?")
         .player("I need you to make a sword for one of Falador's knights")
         .player("He had one which was passed down through five generations")
         .player("But his squire has lost it")
         .player("So we need an identical one to replace it")
         .npc("A Knight's sword eh?")
         .npc("Well I'd need to know exactly how it looked")
         .npc("Before I could make a new one")
         .npc("All the Faladian knights used to have swords with different designs")
         .npc("could you bring me a picture or something?")
         .player("I'll see if I can find one")
         .player("I'll go and ask his squire")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(NEEDS_PORTRAIT);
             }
         });
    }

    /**
     * The materials block. It is not left behind once the sword has been made:
     * bring him another ore and two more bars and he will make another, which is
     * how a player keeps one for themselves. Jagex's own trick was to drop the
     * first before asking; here it is simply that he will do the work again.
     */
    private void materials(Conversation c) {
        c.npc("How are you doing finding sword materials?");
        if (!gotMaterials()) {
            c.player("I haven't found everything yet")
             .npc("Well come back when you do")
             .npc("Remember I need blurite ore and two iron bars");
            return;
        }
        c.player("I have them all")
         .take(BLURITE_ORE, 1)
         .take(IRON_BAR, 2)
         .message("You give some blurite ore and two iron bars to Thurgo")
         .message("Thurgo starts making a sword")
         .message("Thurgo hammers away")
         .message("Thurgo hammers some more")
         .give(new InvItem(SWORD, 1))
         .message("Thurgo hands you a sword")
         .player("Thank you very much")
         .npc("Just remember to call in with more pie some time")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (getStage() < MADE_SWORD) {
                     setStage(MADE_SWORD);
                 }
             }
         });
    }
}
