package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;

/**
 * The mourners of East and West Ardougne, who until now said nothing at all.
 *
 * A handler rather than a quest, for the reason Aggie and Ned are: there are
 * eight mourner ids and three quests want them. Biohazard has already claimed
 * the three at the mourner headquarters (492 at the door, 502 on the ground
 * floor, 495 upstairs) and Plague city has the head mourner (469). An npc a
 * quest associates is taken away from every other quest, so the four that are
 * left cannot live inside any of them.
 *
 * Which mourner is which, from the spawns:
 *
 *   444  (613,580)              East Ardougne, outside Edmond's house
 *   491  (621,589)              East Ardougne, at the wall gateway
 *   451  (622,590)              East Ardougne, at the wall gateway
 *   445  (628,603) (631,604)    West Ardougne
 *        (636,603) (633,582)
 *
 * 444 stands beside Edmond and Alrena and is the one the recovered transcript
 * calls "Mourner by Edmonds' house": his whole conversation is replaced six
 * times as Plague city goes on, and he is the reason this file asks the quest
 * anything. He knows no stage numbers -- he asks Plague city five questions by
 * name and it answers yes or no. See Quest.reached.
 *
 * 451 and 491 stand either side of the gateway through the quarantine wall and
 * get the border guard's lines. The transcript has one further mourner, the one
 * who says "keep away civilian" as you approach the watch tower, and there is
 * no spawn left to be him -- both gateway mourners are also within sight of the
 * tower. Left out rather than guessed at.
 *
 * 445 is inside the wall and greets you the way the head mourner does, which is
 * the transcript's own doing: the two share their opening and their first two
 * answers word for word.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class Mourners implements NpcHandler {

    /** Outside Edmond's house, East Ardougne. */
    private static final int EDMONDS = 444;
    /** Either side of the gateway through the quarantine wall. */
    private static final int GATE_WEST = 451, GATE_EAST = 491;

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case EDMONDS: {
                edmonds(npc, player);
                return;
            }
            case GATE_WEST:
            case GATE_EAST: {
                gateway(npc, player);
                return;
            }
            default: {
                westArdougne(npc, player);
            }
        }
    }

    // ------------------------------------------- 444, outside Edmond's house --

    /**
     * Six conversations, one per stretch of Plague city, and the player never
     * gets the old one back. They are in reverse order because the quest only
     * ever moves forwards: the last one he has is also the one he keeps for
     * good once the quest is over.
     */
    private void edmonds(Npc npc, Player player) {
        QuestManager qm = player.getQuestManager();
        if (qm.reached(Quests.PLAGUE_CITY, "grill-removed")) {
            new Conversation(player, npc)
                .player("hello")
                .npc("what are you up to?")
                .player("nothing")
                .npc("i don't trust you")
                .player("you don't have to")
                .npc("if i find that you attempting to cross the wall")
                .npc("I'll make sure you never return")
                .start();
            return;
        }
        if (qm.reached(Quests.PLAGUE_CITY, "in-sewer")) {
            new Conversation(player, npc)
                .player("hello there")
                .npc("been digging have we?")
                .player("what do you mean!")
                .npc("your hands are covered in mud")
                .player("oh that")
                .player("I've just been helping Edmond with his allotment")
                .npc("funny, you don't look like the gardening type")
                .player("oh no, i love gardening")
                .player("it's my favourite pass time")
                .start();
            return;
        }
        if (qm.reached(Quests.PLAGUE_CITY, "soil-softened")) {
            new Conversation(player, npc)
                .player("hello")
                .npc("what are you up to with old man Edmond?")
                .player("nothing, we've just been chatting")
                .npc("what about, his daughter?")
                .player("oh, you know about that then")
                .npc("we know about everything that goes on in ardougne")
                .npc("we have to if we are to contain the plague")
                .player("have you seen his daughter recently")
                .npc("i imagine she's caught the plague")
                .npc("either way she won't be allowed out of west Ardougne")
                // "to great" is Jagex's spelling.
                .npc("The risk is to great")
                .start();
            return;
        }
        if (qm.reached(Quests.PLAGUE_CITY, "gasmask")) {
            new Conversation(player, npc)
                .player("hello")
                .npc("are you ok")
                .player("yes I'm fine thanks")
                .npc("have you experienced any plague symptoms?")
                .options(new Choice("What are the symptoms?",
                                    "No i feel fine",
                                    "No, but tell me where did the plague come from?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("firstly you'll come down with a heavy flu")
                             .npc("this is usually followed by horrifying nightmares")
                             .player("i used to have nightmares when i was younger")
                             .npc("not like these i assure you")
                             .npc("soon after a thick black liquid will seep from your nose and eyes")
                             .player("yuck!")
                             .npc("when it get's to this stage there's nothing we can do for you");
                            return;
                        }
                        if (option == 1) {
                            c.npc("well if you take a turn for the worse let me know straight away")
                             .player("can you cure it then?")
                             .npc("no")
                             .npc("but you will have to be treated")
                             .player("treated?")
                             .npc("we have to take measures to contain the disease")
                             .npc("that's why you must let us know immediately if you take a turn for the worst");
                            return;
                        }
                        c.npc("many put it down to the low living standards of the west ardougnians")
                         .npc("however this is not the case")
                         .npc("the truth is the king Tyras of west ardougne")
                         .npc("unknowingly brought the plague into his kingdom")
                         .npc("when returning from one of his visits to the darklands in the north west");
                    }
                })
                .start();
            return;
        }
        if (qm.reached(Quests.PLAGUE_CITY, "started")) {
            new Conversation(player, npc)
                .player("hello")
                .npc("what do you want?")
                .options(new Choice("who are you?", "nothing just being polite") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("I'm a mourner")
                             .npc("it's my job to help heal the plague victims of west ardougne")
                             .npc("and to make sure the disease is contained")
                             .player("who pays you?")
                             .npc("we feel as the kings henchmen it's our duty to help the people of ardougne")
                             .player("very noble of you")
                             .npc("if you come down with any symptoms such as a flu or nightmares")
                             .npc("let me know immediately");
                            return;
                        }
                        c.npc("hmm ok then")
                         .npc("be on your way");
                    }
                })
                .start();
            return;
        }
        new Conversation(player, npc)
            .player("hello there")
            // "Do you a have problem" is Jagex's word order.
            .npc("Do you a have problem traveller?")
            .player("no i just wondered why your wearing that outfit")
            .player("is it fancy dress?")
            .npc("no it's for protection")
            .player("protection from what")
            .npc("the plague of course")
            .start();
    }

    // ------------------------------------------------ 451 and 491, the gateway --

    private void gateway(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello there")
            .npc("can I help you?")
            .player("what are you doing?")
            .npc("I'm guarding the border to west ardougne")
            .npc("no one except us mourners can pass through")
            .player("why?")
            .npc("the plague of course")
            .npc("we can't risk cross contamination")
            .options(new Choice("What brought the plague to ardougne?",
                                "What are the symptoms of the plague?",
                                "Ok then see you around") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("it's all down to king tyras of west ardougne")
                         .npc("rather than protecting his people")
                         .npc("he spends his time in the lands to the west")
                         .npc("when he returned last he brought the plague with him")
                         .npc("then left before the problem became serious")
                         .player("does he know how bad the situation is now?")
                         .npc("if he did he wouldn't care")
                         .npc("i believe he wants his people to suffer")
                         .npc("he's an evil man")
                         .player("isn't that treason?")
                         .npc("he's not my king");
                        return;
                    }
                    if (option == 1) {
                        c.npc("the first signs are typical flu symptoms")
                         .npc("these tend to be followed by severe nightmares")
                         .npc("horrifying hallucinations which drive many to madness")
                         .player("sounds nasty")
                         .npc("it gets worse")
                         .npc("next the victims blood supply changes into a thick black tar like liquid")
                         .npc("at this point they're past help")
                         .npc("their skin is cold to the touch")
                         .npc("the victim is now brain dead")
                         .npc("their body however lives on driven by the virus")
                         .npc("roaming like a zombie")
                         .npc("spreading itself further wherever possible")
                         .player("I think I've heard enough");
                        return;
                    }
                    c.npc("maybe");
                }
            })
            .start();
    }

    // ------------------------------------------------------- 445, inside the wall --

    /**
     * The same greeting and the same first two answers as the head mourner,
     * which is how the transcript has it. Elena drops off the list once she is
     * home, as she does for him.
     */
    private void westArdougne(Npc npc, Player player) {
        final boolean elena = !player.getQuestManager().completed(Quests.PLAGUE_CITY);
        String[] options = elena
            ? new String[]{"So what's a mourner?",
                           "I've not got the plague though",
                           "I'm looking for a woman named Elena"}
            : new String[]{"So what's a mourner?",
                           "I've not got the plague though"};
        new Conversation(player, npc)
            // "how did you did get over here" is Jagex's, and is what the head
            // mourner says too.
            .npc("hmm how did you did get over here?")
            .npc("You're not one of this rabble")
            .npc("Ah well you'll have to stay")
            .npc("Can't risk you going back now")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("We're working for King Luthas of East ardougne")
                         .npc("Trying to contain the accursed plague sweeping west Ardougne")
                         .npc("We also do our best to ease these peoples suffering")
                         .npc("We're nicknamed mourners")
                         .npc("because we spend a lot of time at plague victims funerals")
                         .npc("no one else is allowed to risk the funerals")
                         .npc("It's a demanding job")
                         .npc("And we get little thanks from the people here");
                        return;
                    }
                    if (option == 1) {
                        c.npc("Can't risk you being a carrier")
                         .npc("that protective clothing you have")
                         .npc("isn't regulation issue")
                         .npc("It won't meet safety standards");
                        return;
                    }
                    c.npc("ah yes I've heard of her")
                     .npc("A missionary I believe")
                     .npc("She must be mad coming over here voluntarily")
                     .npc("I hear rumours she has probably caught the plague now")
                     .npc("Very tragic stupid waste of life");
                }
            })
            .start();
    }
}
