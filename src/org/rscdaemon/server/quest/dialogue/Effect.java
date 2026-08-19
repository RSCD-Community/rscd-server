package org.rscdaemon.server.quest.dialogue;

/**
 * Something a conversation does rather than says: hand over an item, advance a
 * quest stage, award experience.
 *
 * Runs on the game thread between two lines of dialogue and costs no time, so
 * the pacing the player sees is unaffected by how much bookkeeping happens.
 */
public interface Effect {

    void run(Conversation c);
}
