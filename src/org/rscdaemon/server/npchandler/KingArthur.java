package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * King Arthur, of Camelot.
 *
 * He belongs to two quests -- Merlin's crystal starts and ends with him, and The
 * Holy Grail starts, gets its golden feather and ends with him -- and an npc can
 * only be associated with one. So he lives here instead, and both quests are
 * driven through the names they publish: QuestManager.reached() to ask where a
 * player has got to, and note() to tell a quest something happened.
 *
 * Nothing here decides anything. Every branch is a question put to whichever
 * quest owns the answer, and every consequence is reported back to it; the
 * stages themselves stay inside the quest files where they belong.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class KingArthur implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int GOLDEN_FEATHER = 745;
    private static final int HOLY_GRAIL = 746;

    public void handleNpc(Npc npc, Player player) throws Exception {
        QuestManager q = player.getQuestManager();

        if (q.completed(Quests.THE_HOLY_GRAIL)) {
            new Conversation(player, npc)
                .npc("Thankyou for retrieving the grail")
                .npc("You shall be long remembered")
                .npc("As one of the greatest heros")
                .npc("Amongst the knights of the round table")
                .start();
            return;
        }
        if (q.reached(Quests.THE_HOLY_GRAIL, "started")) {
            grailProgress(npc, player, q);
            return;
        }
        if (q.completed(Quests.MERLINS_CRYSTAL)) {
            grailOffer(npc, player);
            return;
        }
        if (q.reached(Quests.MERLINS_CRYSTAL, "merlin-freed")) {
            new Conversation(player, npc)
                .player("I have freed Merlin from his crystal")
                .npc("Ah a good job well done")
                .npc("I knight thee")
                .npc("You are now a knight of the round table")
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().getQuestManager()
                            .note(Quests.MERLINS_CRYSTAL, "knighted");
                    }
                })
                .start();
            return;
        }
        court(npc, player, q.reached(Quests.MERLINS_CRYSTAL, "started"));
    }

    // ------------------------------------------------------ Merlin's crystal --

    private void court(Npc npc, Player player, boolean questStarted) {
        Conversation c = new Conversation(player, npc)
            .npc("Welcome to the court of King Arthur")
            .npc("I am King Arthur");
        if (questStarted) {
            c.options(new Choice("So what are you doing in Runescape",
                                 "thankyou very much") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        inRunescape(c);
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("I want to become a knight of the round table",
                             "So what are you doing in Runescape",
                             "thankyou very much") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well I think you need to go on a quest to prove yourself worthy")
                     .npc("My knights like a good quest")
                     .npc("Unfortunately our current quest is to rescue Merlin")
                     .npc("Back in England he got himself trapped in some sort of magical Crystal")
                     .npc("We've moved him from the cave we found him in")
                     .npc("He's upstairs in his tower")
                     .player("I will see what I can do then")
                     .npc("Talk to my knights if you need any help")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             c.getPlayer().getQuestManager()
                                 .note(Quests.MERLINS_CRYSTAL, "quest-accepted");
                         }
                     });
                    return;
                }
                if (option == 1) {
                    inRunescape(c);
                }
            }
        });
        c.start();
    }

    private void inRunescape(Conversation c) {
        c.npc("Well legend says we will return to Britain in it's time of greatest need")
         .npc("But that's not for quite a while")
         .npc("So we've moved the whole outfit here for now")
         .npc("We're passing the time in Runescape");
    }

    // -------------------------------------------------------- The Holy Grail --

    private void grailOffer(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("Now i am a knight of the round table")
            .player("Do you have anymore quests for me?")
            .npc("Aha, I'm glad you are here")
            .npc("I am sending out various knights on an important quest")
            .npc("I was wondering if you too would like to take up this quest?")
            .options(new Choice("Tell me of this quest",
                                "I am weary of questing for the time being") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Maybe later then").player("Maybe so");
                        return;
                    }
                    c.npc("Well we recently found out")
                     .npc("The holy grail has passed into the runescape world")
                     .npc("This is most fortuitous")
                     .npc("None of my knights ever did return with it last time")
                     .npc("Now we have the opportunity to give it another go")
                     .npc("Maybe this time we will have more luck")
                     .options(new Choice("I'd enjoy trying that",
                                         "I may come back and try that later") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("Be sure that you come speak to me soon then");
                                 return;
                             }
                             c.npc("Go speak to Merlin")
                              .npc("He may be able to give a better clue as to where it is")
                              .npc("Now you have freed him from the crystal")
                              .npc("He has set up his workshop in the room next to the library")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      c.getPlayer().getQuestManager()
                                          .note(Quests.THE_HOLY_GRAIL, "quest-accepted");
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    private void grailProgress(Npc npc, Player player, QuestManager q) {
        Conversation c = new Conversation(player, npc).npc("How goes thy quest?");
        if (player.getInventory().countId(HOLY_GRAIL) > 0) {
            c.player("I have retrieved the grail")
             .npc("wow incredible you truly are a splendid knight")
             .then(new Effect() {
                 public void run(Conversation c) {
                     c.getPlayer().getQuestManager()
                         .note(Quests.THE_HOLY_GRAIL, "grail-returned");
                 }
             })
             .start();
            return;
        }
        // The fisher king asks after his son before Arthur will say a word about
        // him, so the option only appears once the player has been sent looking.
        if (q.reached(Quests.THE_HOLY_GRAIL, "seeking-percival")
                && player.getInventory().countId(GOLDEN_FEATHER) == 0) {
            c.options(new Choice("I am making progress",
                                 "Hello, do you have a knight named Sir Percival?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        elusive(c);
                        return;
                    }
                    c.npc("Ah yes I remember, young percival")
                     .npc("He rode off on a quest a couple of months ago")
                     .npc("We are getting a bit worried, he's not back yet")
                     .npc("He was going to try and recover the golden boots of Arkaneeses")
                     .player("Any idea which way that would be?")
                     .npc("Not exactly")
                     .npc("We discovered, some magic golden feathers")
                     .npc("They are said to point the way to the boots")
                     .npc("they certainly point somewhere")
                     .npc("just blowing gently on them")
                     .npc("Will make them show the way to go")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             Player p = c.getPlayer();
                             p.getInventory().add(new InvItem(GOLDEN_FEATHER, 1));
                             p.getActionSender().sendInventory();
                             p.getQuestManager()
                                 .note(Quests.THE_HOLY_GRAIL, "given-feather");
                         }
                     });
                }
            });
            c.start();
            return;
        }
        c.player("I am making progress");
        elusive(c);
        c.start();
    }

    private void elusive(Conversation c) {
        c.player("But I have not recovered the grail yet")
         .npc("Well the grail is very elusive")
         .npc("It may take some perserverance");
    }
}
