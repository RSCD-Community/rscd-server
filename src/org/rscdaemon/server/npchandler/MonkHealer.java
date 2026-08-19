package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The healing monks of the Edgeville monastery, and their twin at Ardougne.
 *
 * The single menu entry reads "Can you heal me? I'm injured", but the
 * transcript shows the player speaking it as two lines. picker() rather than
 * options() keeps the label silent so the branch can speak both, the same way
 * {@link AbbotLangley} does -- the abbot offers the identical option.
 *
 * The heal is ten points here where the transcript attests five. That is
 * inherited and left alone on purpose; it is on the list of numbers awaiting a
 * decision rather than something to change quietly.
 */
public class MonkHealer implements NpcHandler {

    private static final int HITS = 3;
    private static final int HEAL_AMOUNT = 10;

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .npc("Greetings traveller")
            .picker(new Choice("Can you heal me? I'm injured") {
                public void picked(int option, Conversation c) {
                    heal(c);
                }
            })
            .start();
    }

    private void heal(Conversation c) {
        c.player("Can you heal me?")
         .player("I'm injured")
         .npc("Ok")
         .message("The monk places his hands on your head")
         .message("You feel a little better")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 int healed = p.getCurStat(HITS) + HEAL_AMOUNT;
                 if (healed > p.getMaxStat(HITS)) {
                     healed = p.getMaxStat(HITS);
                 }
                 p.setCurStat(HITS, healed);
                 p.getActionSender().sendStat(HITS);
             }
         });
    }
}
