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
 * Romeo &amp; Juliet.
 *
 * Four npcs in Varrock, in a fixed order: Romeo sends you to Juliet, Juliet
 * sends a message back to Romeo, Romeo sends you to Father Lawrence, Lawrence
 * sends you to the Apothecary, the Apothecary wants cadavaberries, and the
 * potion goes back to Juliet. Telling Romeo afterwards ends it. No experience,
 * five quest points -- the most in the free game.
 *
 * The quest can be started from either end. Romeo asking you to find Juliet and
 * Juliet asking you to carry a message to Romeo are both quest starts, which is
 * why STARTED and HAVE_MESSAGE are separate stages rather than one.
 *
 * One deliberate departure from vanilla: Juliet replaces a lost message every
 * time. The transcript records her refusing after the second loss, which leaves
 * the quest permanently unfinishable for that character. Reproducing a dead end
 * is not worth the fidelity, so she keeps handing out replacements and keeps the
 * scolding line she used the first time.
 *
 * Cadavaberries are ground spawns south-east of Varrock beside the redberries,
 * at (86, 542) and (83, 548) -- they had no spawn at all until
 * tools/restore_spawns.py put them back, which made this quest unfinishable for
 * a different reason.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class RomeoAndJuliet extends Quest {

    public final static int UID = Quests.ROMEO_AND_JULIET;

    /** Romeo has asked you to find Juliet. */
    private static final int STARTED = 1;
    /** Juliet has handed over the message. */
    private static final int HAVE_MESSAGE = 2;
    /** Romeo has read it and named Father Lawrence. */
    private static final int MESSAGE_DELIVERED = 3;
    /** Lawrence has named the Apothecary. */
    private static final int SEEK_APOTHECARY = 4;
    /** The Apothecary has asked for cadavaberries. */
    private static final int NEED_BERRIES = 5;
    /** Juliet has drunk the potion; only Romeo is left. */
    private static final int POTION_DELIVERED = 6;
    private static final int FINISHED = 7;

    private static final int ROMEO = 30;
    private static final int JULIET = 31;
    private static final int LAWRENCE = 32;
    private static final int APOTHECARY = 33;

    private static final int MESSAGE = 56;
    private static final int CADAVABERRIES = 55;
    private static final int CADAVA = 57;

    /* The Apothecary's other trade, which has nothing to do with the quest but
       is lost if this class does not answer for him. */
    private static final int RED_SPIDERS_EGGS = 219;
    private static final int LIMPWURT_ROOT = 220;
    private static final int STRENGTH_POTION = 221;   /* the four-dose */
    private static final int COINS = 10;
    private static final int POTION_FEE = 5;

    public RomeoAndJuliet(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Romeo & Juliet");
        setFinalStage(FINISHED);
        associateNpc(ROMEO);
        associateNpc(JULIET);
        associateNpc(LAWRENCE);
        associateNpc(APOTHECARY);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Romeo & Juliet are desperately in love, but Juliet's father doesn't approve. Help them to find a way to get married and live happily ever after.");
        setStartPoint("House west of Varrock");
        setSpeakTo("Juliet");
        setMissionLength("Short");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Romeo & Juliet quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        switch (npc.getID()) {
            case ROMEO:      talkToRomeo(npc);      break;
            case JULIET:     talkToJuliet(npc);     break;
            case LAWRENCE:   talkToLawrence(npc);   break;
            case APOTHECARY: talkToApothecary(npc); break;
        }
    }

    private boolean has(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    // -------------------------------------------------------------- Romeo --

    private void talkToRomeo(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("I heard Juliet had died. Terrible business")
             .npc("Her cousin and I are getting on well though")
             .npc("Thanks for your help")
             .start();
            return;
        }

        switch (getStage()) {
            case -1:
                c.npc("Juliet, Juliet, Juliet! Wherefore Art thou?")
                 .npc("Kind friend, Have you seen Juliet?")
                 .npc("Her and her Father seem to have disappeared")
                 .options(new Choice("Yes, I have seen her",
                                     "No, but that's a girl for you",
                                     "Can I help find her for you?") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.player("I think it was her. Blond, stressed")
                              .npc("Yes, that sounds like her");
                         } else if (option == 1) {
                             c.npc("Not my dear Juliet. She is different")
                              .npc("Could you find her for me?");
                         } else {
                             c.npc("Oh would you? That would be wonderful!");
                         }
                         c.npc("Please tell her I long to be with her")
                          .options(new Choice("Yes, I will tell her",
                                              "Sorry, I am too busy. Maybe later?",
                                              "I can't, it sounds like work for me") {
                              public void picked(int option, Conversation c) {
                                  if (option == 2) {
                                      c.player("I can't, it sounds like work to me")
                                       .npc("Well, I guess you are not the romantic type")
                                       .npc("Goodbye");
                                      return;
                                  }
                                  if (option != 0) {
                                      c.npc("Well if you do find her, I would be most grateful");
                                      return;
                                  }
                                  c.npc("You are the saviour of my heart, thank you.")
                                   .player("err, yes. Ok. Thats.... nice.")
                                   .then(new Effect() {
                                       public void run(Conversation c) {
                                           setStage(STARTED);
                                       }
                                   });
                              }
                          }.says(0, "Yes, I will tell her how you feel"));
                     }
                 }.says(1, "No, but that's girls for you"));
                break;

            case STARTED:
                c.npc("Please find my Juliet. I am so, so sad");
                break;

            case HAVE_MESSAGE:
                if (!has(MESSAGE)) {
                    c.player("Romeo, I have a message from Juliet")
                     .player("Except that I seem to have lost it");
                    break;
                }
                c.player("Romeo, I have a message from Juliet")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().getInventory().remove(MESSAGE, 1);
                         c.getPlayer().getActionSender().sendInventory();
                     }
                 })
                 .npc("Tragic news. Her father is opposing our marriage")
                 .npc("If her father sees me, he will kill me")
                 .npc("I dare not go near his lands")
                 .npc("She says Father Lawrence can help us")
                 .npc("Please find him for me. Tell him of our plight")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(MESSAGE_DELIVERED);
                     }
                 });
                break;

            case MESSAGE_DELIVERED:
                c.npc("Please friend, how goes our quest?")
                 .npc("Father Lawrence must be told. only he can help");
                break;

            case SEEK_APOTHECARY:
                c.npc("Did you find the Father? What did he suggest?")
                 .options(new Choice("He sent me to the Apothecary",
                                     "He seems keen for you marry Juliet") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.npc("I know him. He lives near the town square")
                              .npc("the small house behind the sloped building")
                              .npc("Good luck");
                         } else {
                             c.npc("I think he wants some peace. He was our messenger")
                              .npc("before you were kind enough to help us");
                         }
                     }
                 });
                break;

            case NEED_BERRIES:
                if (has(CADAVA)) {
                    c.npc("Ah, you have the potion. I was told what to do by the good Father")
                     .npc("Better get it to Juliet. She knows what is happening");
                } else {
                    c.npc("I hope the potion is near ready")
                     .npc("It is the last step for the great plan")
                     .npc("I hope I will be with my dear one soon");
                }
                break;

            default:
                c.player("Romeo, it's all set. Juliet has the potion")
                 .npc("Ah right")
                 .npc("What potion would that be then?")
                 .player("The one to get her to the crypt.")
                 .npc("Ah right")
                 .npc("So she is dead then. Ah thats a shame.")
                 .npc("Thanks for you help anyway.")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(getFinalStage());
                     }
                 });
                break;
        }
        c.start();
    }

    // ------------------------------------------------------------- Juliet --

    private void talkToJuliet(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("I sat in that cold crypt for ages waiting for Romeo")
             .npc("That useless fool never showed up")
             .npc("And all I got was indigestion. I am done with men like him")
             .npc("Now go away before I call my father!")
             .start();
            return;
        }

        switch (getStage()) {
            case -1:
                c.npc("Romeo, Romeo, wherefore art thou Romeo?")
                 .npc("Bold adventurer, have you seen Romeo on your travels?")
                 .npc("Skinny guy, a bit wishy washy, head full of poetry")
                 /*
                  * The three ways of saying yes are not one shared branch. She
                  * asks the same question in each, but the refusal she offers
                  * with it is different every time and the branch that leads
                  * with "I guess I could find him" offers no refusal at all --
                  * the player has already volunteered, so she takes it and the
                  * agreement is spoken rather than picked.
                  *
                  * We had all three collapsed onto one menu, which cost the
                  * "No, he was a little too weird for me" ending outright: three
                  * lines of Juliet that nothing in the server could reach.
                  *
                  * The spellings are Jagex's and they are not consistent with
                  * each other. She misspells "messge" only on the first branch;
                  * the agreement is labelled "Certainly" there and "Certinly" on
                  * the second, and is spoken "Certinly" on both.
                  */
                 .options(new Choice("Yes I have met him",
                                     "No, I think I would have remembered if I had",
                                     "I guess I could find him",
                                     "I think you could do better") {
                     public void picked(int option, Conversation c) {
                         if (option == 3) {
                             c.npc("He has his good points")
                              .npc("He doesn't spend all day on the internet, at least");
                             return;
                         }
                         if (option == 2) {
                             c.npc("That is most kind of you")
                              .npc("Could you please deliver a message to him?")
                              .player("Certinly, I will do so straight away")
                              .npc("It may be our only hope")
                              .message("Juliet gives you a message")
                              .then(giveMessage());
                             return;
                         }
                         if (option == 0) {
                             c.npc("Yes, that would be him.")
                              .npc("Could you please deliver a messge to him?")
                              .options(new Choice("Certainly, I will do so straight away",
                                                  "No, he was a little too weird for me") {
                                  public void picked(int option, Conversation c) {
                                      if (option != 0) {
                                          c.npc("Oh dear, that will be the ruin of our love")
                                           .npc("Well, I will just stay here and worry")
                                           .npc("You unromantic soul.");
                                          return;
                                      }
                                      c.npc("It may be our only hope")
                                       .message("Juliet gives you a message")
                                       .then(giveMessage());
                                  }
                              }.says(0, "Certinly, I will deliver your message straight away")
                               .says(1, "No"));
                             return;
                         }
                         c.npc("Could you please deliver a message to him?")
                          .options(new Choice("Certinly, I will do so straight away",
                                              "No, I have better things to do") {
                              public void picked(int option, Conversation c) {
                                  if (option != 0) {
                                      c.npc("I will not keep you from them. Goodbye");
                                      return;
                                  }
                                  c.npc("It may be our only hope")
                                   .message("Juliet gives you a message")
                                   .then(giveMessage());
                              }
                          }.says(0, "Certinly, I will deliver your message straight away"));
                     }
                 }.says(0, "I did see Romeo somewhere.", "He seemed a bit depressed.")
                  .says(1, "No, I think I would have remembered"));
                break;

            case STARTED:
                c.player("Juliet, I come from Romeo")
                 .player("He begs me tell you he cares still")
                 .npc("Please, Take this message to him")
                 .player("Certinly, I will deliver your message straight away")
                 .npc("It may be our only hope")
                 .message("Juliet gives you a message")
                 .then(giveMessage());
                break;

            case HAVE_MESSAGE:
                if (has(MESSAGE)) {
                    c.npc("Please, deliver the message to Romeo with all speed");
                } else {
                    c.npc("How could you lose this most important message?")
                     .npc("Please take this message to him, and please don't lose it")
                     .message("Juliet gives you another message")
                     .then(giveMessage());
                }
                break;

            case MESSAGE_DELIVERED:
                c.player("I have passed on your message")
                 .player("Now I go to Father Lawrence for help")
                 .npc("Yes, he knows many things that can be done")
                 .npc("I hope you find him soon");
                break;

            case SEEK_APOTHECARY:
                c.player("I found the Father. Now I seek the apothecary")
                 .npc("I do not know where he lives")
                 .npc("but please, make haste. My father is close");
                break;

            case NEED_BERRIES:
                if (!has(CADAVA)) {
                    c.player("I have to get a potion made for you")
                     .player("Not done that bit yet though. Still trying")
                     .npc("Fair luck to you, the end is close");
                    break;
                }
                c.player("I have a potion from Father Lawrence")
                 .player("it should make you seem dead, and get you away from this place")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().getInventory().remove(CADAVA, 1);
                         c.getPlayer().getActionSender().sendInventory();
                         setStage(POTION_DELIVERED);
                     }
                 })
                 .npc("Wonderful. I just hope Romeo can remember to get me from the Crypt")
                 .npc("Many thanks kind friend")
                 .npc("Please go to Romeo, make sure he understands")
                 .npc("He can be a bit dense sometimes");
                break;

            default:
                c.npc("Have you seen Romeo? He will reward you for your help")
                 .npc("He is the wealth in this story")
                 .npc("I am just the glamour");
                break;
        }
        c.start();
    }

    private Effect giveMessage() {
        return new Effect() {
            public void run(Conversation c) {
                c.getPlayer().getInventory().add(new InvItem(MESSAGE, 1));
                c.getPlayer().getActionSender().sendInventory();
                if (getStage() < HAVE_MESSAGE) {
                    setStage(HAVE_MESSAGE);
                }
            }
        };
    }

    // ---------------------------------------------------- Father Lawrence --

    private void talkToLawrence(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (getStage() == MESSAGE_DELIVERED) {
            c.player("Romeo sent me. Hey says you can help")
             .npc("Ah Romeo, yes. A fine lad, but a little bit confused")
             .player("Juliet must be rescued from her fathers control")
             .npc("I know just the thing. A potion to make her appear dead")
             .npc("Then Romeo can collect her from the crypt")
             .npc("Go to the Apothecary, tell him I sent you")
             .npc("You need some Cadava Potion")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(SEEK_APOTHECARY);
                 }
             })
             .start();
            return;
        }
        if (getStage() == SEEK_APOTHECARY) {
            c.npc("Ah have you found the Apothecary yet?")
             .npc("Remember, Cadava potion, for Father Lawrence")
             .start();
            return;
        }
        if (getStage() == NEED_BERRIES) {
            c.npc("Did you find the Apothecary?");
            if (has(CADAVABERRIES) || has(CADAVA)) {
                c.player("I am on my way back to him with the ingredients")
                 .npc("Good work. Get the potion to Juliet when you have it")
                 .npc("I will tell Romeo to be ready");
            } else {
                c.player("Yes, I must find some berries")
                 // He is wrong about the gloves. He is wrong about a lot of
                 // things; the wiki blames the whiskey.
                 .npc("Well, take care. They are poisonous to the touch")
                 .npc("You will need gloves");
            }
            c.start();
            return;
        }
        if (questStarted()) {
            c.npc("Oh to be a father in the times of whiskey")
             .npc("I sing and I drink and I wake up in gutters")
             .npc("Top of the morning to you")
             .npc("To err is human, to forgive, quite difficult")
             .start();
            return;
        }

        c.npc("Hello adventurer, do you seek a quest?")
         .options(new Choice("I am always looking for a quest",
                             "No, I prefer to kill things",
                             "Can you recommend a good bar?") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("Well, I see poor Romeo wandering around the square. I think he may need help")
                      .npc("I was helping him and Juliet to meet, but it became impossible")
                      .npc("I am sure he can use some help");
                 } else if (option == 1) {
                     c.npc("That's a fine career in these lands")
                      .npc("There is more that needs killing every day");
                 } else {
                     c.npc("Drinking will be the death of you")
                      .npc("But the Blue Moon in the city is cheap enough")
                      .npc("And providing you buy one drink an hour they let you stay all night");
                 }
             }
         }.says(1, "No, I just prefer to kill things"))
         .start();
    }

    // --------------------------------------------------------- Apothecary --

    private void talkToApothecary(Npc npc) {
        if (getStage() == SEEK_APOTHECARY) {
            new Conversation(getOwner(), npc)
                .player("Apothecary. Father Lawrence sent me")
                .player("I need some Cadava potion to help Romeo and Juliet")
                .npc("Cadava potion. Its pretty nasty. And hard to make")
                .npc("Wing of Rat, Tail of frog. Ear of snake and horn of dog")
                .npc("I have all that, but I need some cadavaberries")
                .npc("You will have to find them while I get the rest ready")
                .npc("Bring them here when you have them. But be careful. They are nasty")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage(NEED_BERRIES);
                    }
                })
                .start();
            return;
        }
        if (getStage() == NEED_BERRIES) {
            Conversation c = new Conversation(getOwner(), npc);
            if (has(CADAVABERRIES)) {
                c.npc("Well done. You have the berries")
                 .npc("Here is what you need")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         c.getPlayer().getInventory().remove(CADAVABERRIES, 1);
                         c.getPlayer().getInventory().add(new InvItem(CADAVA, 1));
                         c.getPlayer().getActionSender().sendInventory();
                     }
                 });
            } else {
                c.npc("Keep searching for the berries")
                 .npc("They are needed for the potion");
            }
            c.start();
            return;
        }
        strengthPotions(npc);
    }

    /**
     * The Apothecary's day job.
     *
     * Nothing to do with Romeo and Juliet, but associating an npc with a quest
     * takes it away from every other handler, so this has to live here or the
     * only source of strength potions in the free game disappears.
     */
    private void strengthPotions(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("I am the apothecary")
            .npc("I have potions to brew. Do you need anything specific?")
            .options(new Choice("Can you make a strength potion?",
                                "Do you know a potion to make hair fall out?",
                                "Have you got any good potions to give way?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("I do indeed. I gave it to my mother. That's why I now live alone");
                        return;
                    }
                    if (option == 2) {
                        c.npc("Sorry, charity is not my strong point");
                        return;
                    }
                    if (!has(RED_SPIDERS_EGGS) || !has(LIMPWURT_ROOT)) {
                        c.npc("Yes. But the ingredients are a little hard to find")
                         .npc("If you ever get them I will make it for you. For a cost")
                         .player("So what are the ingredients?")
                         .npc("You'll need to find the eggs of the deadly red spider")
                         .npc("And a limpwurt root")
                         .npc("Oh and you'll have to pay me 5 coins")
                         .player("Ok, I'll look out for them");
                        return;
                    }
                    c.player("I have the root and spiders eggs needed to make it")
                     .npc("Well give me them and 5 gold and I'll make you your potion")
                     .options(new Choice("Yes ok", "No thanks") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 return;
                             }
                             Player p = c.getPlayer();
                             if (p.getInventory().countId(COINS) < POTION_FEE) {
                                 p.getActionSender().sendMessage("You don't have enough coins.");
                                 return;
                             }
                             p.getInventory().remove(RED_SPIDERS_EGGS, 1);
                             p.getInventory().remove(LIMPWURT_ROOT, 1);
                             p.getInventory().remove(COINS, POTION_FEE);
                             p.getInventory().add(new InvItem(STRENGTH_POTION, 1));
                             p.getActionSender().sendInventory();
                         }
                     });
                }
            })
            .start();
    }
}
