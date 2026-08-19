package org.rscdaemon.server.quest.dialogue;

import java.util.LinkedList;

import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;

/**
 * A conversation between a player and an npc, written as a script instead of as
 * nested callbacks.
 *
 * Dialogue in this server is paced by the client: each line has to sit on screen
 * for a moment before the next one replaces it, so a conversation is inherently
 * a sequence of delayed steps. Written directly that means one anonymous
 * ShortEvent per line, each nested inside the last, and a branch doubles the
 * nesting again. Fisherman is nine levels deep to offer a single menu.
 *
 * Here the same conversation is a queue. Building it is flat:
 *
 *     new Conversation(player, npc)
 *         .npc("Hello, what can I do for you?")
 *         .player("Who are you?")
 *         .npc("I'm the cook.")
 *         .start();
 *
 * and a branch only nests once, no matter how deep the tree goes:
 *
 *     .options(new Choice("Yes", "No") {
 *         public void picked(int option, Conversation c) {
 *             if (option == 0) c.npc("Good.").give(new InvItem(10, 1));
 *             else c.npc("Suit yourself.");
 *         }
 *     })
 *
 * Everything runs on the game thread via the delayed-event handler, so a
 * conversation never blocks and never mutates the world from a stray thread.
 */
public class Conversation {

    /** Matches ShortEvent, which is what every hand-written handler already used. */
    public static final int LINE_DELAY = 1500;

    private static final World world = World.getWorld();

    private final Player player;
    private final Npc npc;
    private final LinkedList<Step> queue = new LinkedList<Step>();

    private boolean started = false;
    private boolean finished = false;

    /**
     * Where a branch's steps go. A Choice runs when its menu is answered, which
     * is in the middle of the queue, not at the end -- so while it is building,
     * steps are inserted at the cursor rather than appended, and whatever the
     * script had queued after the menu still follows on afterwards.
     */
    private int cursor = -1;

    public Conversation(Player player, Npc npc) {
        this.player = player;
        this.npc = npc;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Npc getNpc() {
        return this.npc;
    }

    // ------------------------------------------------------------- script --

    /** The npc says a line. */
    public Conversation npc(String text) {
        return add(new Say(text, true));
    }

    /**
     * A different npc says a line.
     *
     * For scenes with a bystander who chips in -- the two goblin generals argue
     * with each other while the player stands between them. Only the npc the
     * conversation was opened with is held and unblocked; the speaker here is
     * just a mouth.
     */
    public Conversation npc(Npc speaker, String text) {
        return add(new Say(text, speaker));
    }

    /** The player says a line. */
    public Conversation player(String text) {
        return add(new Say(text, false));
    }

    /** A server message in the chat box -- not speech. */
    public Conversation message(String text) {
        return add(new Message(text));
    }

    /**
     * Offer a menu. The chosen option is echoed as the player saying it, which
     * is how the real game behaved, so a Choice's own dialogue should not repeat
     * it.
     */
    public Conversation options(Choice choice) {
        return add(new Options(choice, true));
    }

    /**
     * Offer a menu whose answer is not spoken.
     *
     * The certers' lists are the reason this exists. "what sort of certificate
     * do you wish to trade in?" is followed by a menu of goods, and picking
     * "Coal" is not the player saying the word "Coal" -- the transcripts show
     * no player line there, where they do show one for every real answer. Same
     * for the quantity menus behind them.
     */
    public Conversation picker(Choice choice) {
        return add(new Options(choice, false));
    }

    /** Run code between lines. Takes no time in itself. */
    public Conversation then(Effect effect) {
        return add(new Run(effect));
    }

    /** Convenience for the commonest effect. */
    public Conversation give(final InvItem item) {
        return then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().getInventory().add(item);
                c.getPlayer().getActionSender().sendInventory();
            }
        });
    }

    /**
     * Convenience for the second commonest.
     *
     * This used to loop, because Inventory.remove(id, amount) honoured the
     * amount only for a stackable item and took exactly one entry of anything
     * else however large the amount was -- so every script asking for two of
     * something unstackable was paid with one. That defect is fixed at source
     * now, and the loop has to go with it: against a correct remove() it took
     * the amount and then kept going, over-charging any player who happened to
     * be carrying spares.
     */
    public Conversation take(final int id, final int amount) {
        return then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().getInventory().remove(id, amount);
                c.getPlayer().getActionSender().sendInventory();
            }
        });
    }

    private Conversation add(Step step) {
        if (this.cursor >= 0) {
            this.queue.add(this.cursor++, step);
        } else {
            this.queue.addLast(step);
        }
        return this;
    }

    // -------------------------------------------------------------- drive --

    /**
     * Begin. The npc is held for the duration so it cannot wander off or be
     * talked to by someone else, and the player is marked busy so the rest of
     * the game ignores them until the conversation ends.
     */
    public void start() {
        if (this.started) {
            return;
        }
        this.started = true;
        this.player.setBusy(true);
        if (this.npc != null) {
            this.npc.blockedBy(this.player);
        }
        next();
    }

    /** End early. Safe to call at any point, including from inside a step. */
    public void stop() {
        this.queue.clear();
        finish();
    }

    private void next() {
        // A step can drop the player -- logout, death, a teleport out of range.
        // Anything queued behind it is no longer wanted.
        if (this.finished) {
            return;
        }
        if (this.player == null || this.player.isRemoved()) {
            finish();
            return;
        }
        if (this.queue.isEmpty()) {
            finish();
            return;
        }
        this.queue.removeFirst().play(this);
    }

    /** Continue after the usual line delay. */
    private void schedule() {
        world.getDelayedEventHandler().add(new ShortEvent(this.player) {
            public void action() {
                Conversation.this.next();
            }
        });
    }

    private void finish() {
        if (this.finished) {
            return;
        }
        this.finished = true;
        if (this.player != null) {
            this.player.setBusy(false);
            this.player.resetMenuHandler();
        }
        if (this.npc != null) {
            this.npc.unblock();
        }
    }

    // -------------------------------------------------------------- steps --

    private static abstract class Step {
        abstract void play(Conversation c);
    }

    private static class Say extends Step {
        private final String text;
        private final boolean fromNpc;
        /** Null means "whoever this conversation is with". */
        private final Npc speaker;

        Say(String text, boolean fromNpc) {
            this(text, fromNpc, null);
        }

        Say(String text, Npc speaker) {
            this(text, true, speaker);
        }

        private Say(String text, boolean fromNpc, Npc speaker) {
            this.text = text;
            this.fromNpc = fromNpc;
            this.speaker = speaker;
        }

        void play(Conversation c) {
            if (this.fromNpc) {
                Npc who = this.speaker != null ? this.speaker : c.npc;
                c.player.informOfNpcMessage(new ChatMessage(who, this.text, c.player));
            } else {
                c.player.informOfChatMessage(new ChatMessage(c.player, this.text, c.npc));
            }
            c.schedule();
        }
    }

    private static class Message extends Step {
        private final String text;

        Message(String text) {
            this.text = text;
        }

        void play(Conversation c) {
            c.player.getActionSender().sendMessage(this.text);
            c.schedule();
        }
    }

    private static class Run extends Step {
        private final Effect effect;

        Run(Effect effect) {
            this.effect = effect;
        }

        void play(Conversation c) {
            this.effect.run(c);
            // No delay: an effect is bookkeeping, not something the player reads.
            c.next();
        }
    }

    private static class Options extends Step {
        private final Choice choice;
        private final boolean echo;

        Options(Choice choice, boolean echo) {
            this.choice = choice;
            this.echo = echo;
        }

        void play(final Conversation c) {
            final String[] opts = this.choice.getOptions();
            c.player.setBusy(false); // the client will not answer a busy player
            c.player.setMenuHandler(new MenuHandler(opts) {
                public void handleReply(final int option, String reply) {
                    if (c.finished || option < 0 || option >= opts.length) {
                        return;
                    }
                    c.player.setBusy(true);
                    c.player.resetMenuHandler();
                    // Not opts[option]: an option whose label differs from what
                    // the player then says out loud carries its own lines, and
                    // there can be more than one of them.
                    final String[] said = Options.this.echo
                            ? Options.this.choice.getSpoken(option) : null;
                    if (said != null) {
                        c.player.informOfChatMessage(new ChatMessage(c.player, said[0], c.npc));
                    }
                    world.getDelayedEventHandler().add(new ShortEvent(c.player) {
                        public void action() {
                            c.cursor = 0;
                            try {
                                // Lines two onward go in front of whatever the
                                // branch queues, so the answer finishes before
                                // the npc replies to it.
                                if (said != null) {
                                    for (int i = 1; i < said.length; ++i) {
                                        c.player(said[i]);
                                    }
                                }
                                Options.this.choice.picked(option, c);
                            } finally {
                                c.cursor = -1;
                            }
                            c.next();
                        }
                    });
                }
            });
            c.player.getActionSender().sendMenu(opts);
        }
    }
}
