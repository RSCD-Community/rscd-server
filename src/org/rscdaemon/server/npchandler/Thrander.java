package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.dialogue.Conversation;

/**
 * Thrander, the smith in Varrock who converts plate mail bodies to tops.
 *
 * RSCD had him introduce himself as "Vekk the smith" -- a name that belongs to
 * nobody in RuneScape Classic -- and ran his five lines together into three.
 * Restored verbatim from Transcript:Thrander, "visa versa" and all: the wiki
 * marks that one {{sic}}, so it is Jagex's spelling and not a transcription
 * slip.
 *
 * Only the greeting lives here -- the conversion he describes (give him a
 * plate mail body, get the top back, and back again) is implemented
 * separately, in InvUseOnNpc's THRANDER_PAIRS table, which is where using an
 * item on him is actually handled.
 */
public class Thrander implements NpcHandler {

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .npc("Hello I'm Thrander the smith")
            .npc("I'm an expert in armour modification")
            .npc("Give me your armour designed for men")
            .npc("And I can convert it into something more comfortable for a women")
            .npc("And visa versa")
            .start();
    }
}
