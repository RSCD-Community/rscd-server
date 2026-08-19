package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Abbot Langley (174), the Monastery north of Falador.
 *
 * He runs the place and he was silent. What is here is his "general talk"
 * transcript only -- the greeting, the heal and the one piece of small talk.
 *
 * His other recorded scene, the one that gates the stairs to the upper floor,
 * does NOT live here. That is an object interaction on the staircase and it
 * already speaks through him; see ObjectAction, object 198 at (251,468), where
 * the refusal was reworded to his attested lines in the same pass that added
 * this file. Splitting an npc's dialogue across two files is unpleasant, but
 * the alternative is worse: a talk handler cannot know which staircase you were
 * standing on, and the stair scene is meaningless anywhere else.
 *
 * ----------------------------------------------------------- the heal ------
 *
 * Five hitpoints, and the number is worth a paragraph because the obvious move
 * is wrong.
 *
 * MonkHealer.java, which serves npc 93, is the existing implementation of what
 * is almost certainly the same rule. Its text is verbatim correct -- the same
 * "Greetings traveller", the same "The monk places his hands on your head" and
 * "You feel a little better", the same single heal option -- so it looks like
 * the thing to copy. But it heals TEN, and the wiki attests five for Langley.
 *
 * That is the drop-table pattern exactly: genuine Jagex text wrapped around a
 * number nobody recorded. So this file uses the attested five and does not
 * inherit the ten, because "it is what the tree already does" is how the
 * invented numbers got in and stayed in.
 *
 * npc 93 is deliberately left ALONE at ten rather than quietly corrected to
 * match. Langley's transcript attests Langley. Changing a second npc on the
 * inference that the two share a rule is the same unsourced leap in the other
 * direction, and 93 is a healer players actually use. It is flagged for a
 * decision instead. If a capture ever settles 93, it settles this file too.
 *
 * One caveat kept in the open: "restores up to 5 hitpoints" is the wiki's own
 * parenthetical rather than a transcript line, so the CAP is attested but the
 * rule behind it is not. Flat five capped at maximum is the reading taken here,
 * and it is the same shape MonkHealer already uses.
 */
public class AbbotLangley implements NpcHandler {

    public static final int ABBOT_LANGLEY = 174;

    /** Hitpoints is stat 3. */
    private static final int HITS = 3;

    /** Attested for Langley. MonkHealer's 10 is not evidence -- see above. */
    private static final int HEAL_AMOUNT = 5;

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .npc("Greetings traveller")
            /*
             * picker() rather than options() on purpose. options() echoes the
             * label back as the player's line, and the first label here is one
             * option but TWO spoken lines in the transcript -- "Can you heal
             * me?" then "I'm injured". picker() keeps the label silent so each
             * branch can speak exactly what was recorded.
             */
            .picker(new Choice("Can you heal me? I'm injured",
                               "Isn't this place built a bit out the way?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        heal(c);
                    } else {
                        outOfTheWay(c);
                    }
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

    private void outOfTheWay(Conversation c) {
        // Four lines, and the fourth begins lower-case because it continues the
        // third mid-sentence. That is the transcript, not a dropped capital.
        c.player("Isn't this place built a bit out the way?")
         .npc("We like it that way")
         .npc("We get disturbed less")
         .npc("We still get rather a large amount of travellers")
         .npc("looking for sanctuary and healing here as it is");
    }
}
