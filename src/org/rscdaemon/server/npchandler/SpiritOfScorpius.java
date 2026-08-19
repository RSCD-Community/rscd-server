package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;

/**
 * The Spirit of Scorpius, npc 665, standing on his own grave at (692,650)
 * north of the Observatory.
 *
 * He is the missing end of the unholy symbol chain. The two halves that turn
 * metal into a symbol were already built and working -- InvUseOnObject casts
 * mould 1026 with silver into an unstrung symbol 1027, and InvUseOnItem
 * strings it with wool into the unblessed symbol 1028 -- but nothing in the
 * server ever handed out the mould and nothing ever blessed 1028 into 1029.
 * The whole chain was therefore unreachable from both ends: no way to start it
 * and no way to finish it. He is its only source for either.
 *
 * The handler that used to sit on 665 was RSCD's own MattSpirit, removed with
 * the rest of that content in the August 2026 sweep, which is how 665 came to
 * be in no handler list and no quest at all.
 *
 * Gated on the Observatory quest, which is the gate the fiction states: the
 * grave itself reads "Only those who have seen beyond the stars / may seek his
 * counsel", and seeing beyond the stars is the telescope. Before that he says
 * one line and nothing else.
 *
 * Every line is verbatim from Transcript:Spirit of Scorpius, lower-case "lord"
 * in "The symbol of our lord has been blessed with power!" included -- he
 * capitalises it in the line above and does not here.
 */
public class SpiritOfScorpius implements NpcHandler {

    public static final int SPIRIT = 665;

    /** The mould, the unblessed symbol, and the blessed one. */
    private static final int MOULD = 1026;
    private static final int UNBLESSED = 1028;
    private static final int BLESSED = 1029;

    public void handleNpc(Npc npc, Player player) throws Exception {
        if (!player.getQuestManager().completed(Quests.OBSERVATORY_QUEST)) {
            new Conversation(player, npc)
                .npc("How dare you disturb me!")
                .start();
            return;
        }
        new Conversation(player, npc)
            .options(new Choice("I have come to seek a blessing",
                                "I need another unholy symbol mould",
                                "I have come to kill you") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:  bless(c);  return;
                        case 1:  mould(c);  return;
                        default: c.npc("The might of mortals to me is as the dust is to the sea!");
                    }
                }
            }.says(1, "I need another mould for the unholy symbol"))
            .start();
    }

    /**
     * The blessing. Unblessed first: a player carrying both symbols has come
     * for the one that still needs doing.
     */
    private static void bless(Conversation c) {
        Player p = c.getPlayer();
        if (p.getInventory().countId(UNBLESSED) > 0) {
            c.npc("I see you have the unholy symbol of our Lord")
             .npc("I will bless it for you")
             .message("The ghost mutters in a strange voice")
             .message("The unholy symbol throbs with power")
             .take(UNBLESSED, 1)
             .give(new InvItem(BLESSED, 1))
             .npc("The symbol of our lord has been blessed with power!")
             .npc("My master calls...");
            return;
        }
        if (p.getInventory().countId(BLESSED) > 0) {
            c.npc("I see you have the unholy symbol of our Lord")
             .npc("It is blessed with the Lord Zamorak's power")
             .npc("Come to me when your faith weakens");
            return;
        }
        c.npc("No blessings will be given to those")
         .npc("Who have no symbol of our Lord's love!");
    }

    /**
     * The mould, which he calls a replacement and which is in practice also
     * how the first one is got: the quest's own reward speech never mentions
     * it and the wiki records no other source, so "another" is his word for
     * it however many you have had.
     */
    private static void mould(Conversation c) {
        if (c.getPlayer().getInventory().countId(MOULD) > 0) {
            c.npc("One you already have, another is not needed")
             .npc("Leave me be!");
            return;
        }
        c.npc("To lose an object is easy to replace")
         .npc("To lose the affections of our lord is impossible to forgive...")
         .message("The ghost hands you another mould")
         .give(new InvItem(MOULD, 1));
    }
}
