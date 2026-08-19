package org.rscdaemon.server.quest.dialogue;

/**
 * A menu the player is offered mid-conversation, and what each answer leads to.
 *
 * The options are the lines the player will be shown saying, so they are written
 * in the player's voice -- "Can I have a quest?", not "Ask for a quest". The
 * chosen one is echoed automatically before {@link #picked} runs.
 *
 * That is the usual case but not the universal one. About one option in six
 * across the transcripts is labelled with different words from the line the
 * player then speaks: Keli's "Heard of you? you are famous in Runescape!" is
 * answered out loud with "The great Lady Keli, of course I have heard of you",
 * and the certers' "What is an ore exchange stall?" is spoken as "...exchange
 * store?". Where they differ, {@link #says} carries the spoken line and the
 * constructor argument stays the menu label.
 */
public abstract class Choice {

    private final String[] options;
    private String[][] spoken;

    public Choice(String... options) {
        this.options = options;
    }

    public final String[] getOptions() {
        return this.options;
    }

    /**
     * Give one option a spoken line that differs from its menu label.
     *
     * <pre>
     *   new Choice("Yes, that sounds good teleport me", "No thanks") {
     *       ...
     *   }.says(0, "Yes, that sounds good")
     * </pre>
     *
     * Several options answer with more than one line -- the chef's "I see you
     * are a chef, will you cook me anything?" is two, "I see you are a chef"
     * and "Will you cook me anything?" -- so this takes as many as needed.
     *
     * Only worth using where a source records the two as different; leaving it
     * alone means the label is spoken, which is what nearly every option does.
     *
     * @param option index into the options given to the constructor
     * @param lines  what the player is shown saying instead
     */
    public final Choice says(int option, String... lines) {
        if (option < 0 || option >= this.options.length || lines == null || lines.length == 0) {
            return this;
        }
        if (this.spoken == null) {
            this.spoken = new String[this.options.length][];
        }
        this.spoken[option] = lines;
        return this;
    }

    /** The lines to echo for an option: its own if it has any, else the label. */
    public final String[] getSpoken(int option) {
        if (this.spoken != null && option >= 0 && option < this.spoken.length
                && this.spoken[option] != null) {
            return this.spoken[option];
        }
        return new String[]{this.options[option]};
    }

    /**
     * Add the dialogue that follows the player's answer. Steps added here run
     * before anything the script queued after the menu, so a branch can lead
     * back into a shared ending.
     *
     * @param option index into the options given to the constructor
     * @param c      the conversation, still building
     */
    public abstract void picked(int option, Conversation c);
}
