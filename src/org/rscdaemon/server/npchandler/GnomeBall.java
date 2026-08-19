package org.rscdaemon.server.npchandler;

import java.util.ArrayList;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.util.DataConversions;

/**
 * Gnome Ball, at the Tree Gnome Stronghold. A repeatable minigame, not a
 * quest -- released 12 Dec 2002 alongside the Gnome Agility Course.
 *
 * Real rules, per classic.runescape.wiki's Gnome Ball page: talk to the
 * Referee (601) to get a ball
 * (item 981), shoot it at the Goalie (596) to score, opposing Gnome
 * Ballers tackle whoever is carrying the ball and steal it, and the only
 * way to get it back from a gnome who holds it is to tackle that specific
 * gnome. No win condition, no team roster, no timer -- it's continuous
 * free play. The reward is Ranged + Agility experience per goal, not an
 * item; the ball itself cannot leave the field (already enforced by it
 * being on the untradeable-items list in Formulae).
 *
 * Simplification, documented rather than invented around: the real client
 * has a dedicated right-click "Tackle" npc-command for both directions
 * (a gnome tackling the ball carrier, and the player tackling a gnome who
 * holds the ball). This project's NpcCommand opcode is not wired for a
 * custom per-npc command set the way Agility/Thieving needed their own
 * mechanics built for them. Rather
 * than build a whole new command-dispatch feature for one minigame,
 * both tackle directions are folded into ordinary "talk to" here: talking
 * to an opposing Gnome Baller while holding the ball is read as walking
 * into their reach, and talking to the specific Gnome Baller currently
 * holding the ball is read as attempting to take it back. This keeps the
 * actual risk/reward (damage on a failed attempt, the ball changing
 * hands, needing a fresh ball from the Referee after certain losses)
 * intact while reusing an interaction this project already has, instead
 * of half-building a second one.
 */
public class GnomeBall implements NpcHandler {

    public static final World world = World.getWorld();

    public static final int BALL = 981;
    private static final int HITS = 3;

    public static final int GOALIE = 596;
    public static final int REFEREE = 601;
    public static final int CHEERLEADER = 611;
    public static final int OFFICIAL = 625;

    /**
     * The twelve opponents. 609 and 610 are NOT in this list, and that is the
     * whole point of it -- they were until 2026-08-07, which meant your own two
     * teammates tackled you and took the ball off you.
     *
     * The split is not a judgement call. Jagex's own NPCDef partitions these
     * fourteen ids 12/2 on two independent fields at once:
     *
     *     595 597 598 599 600 602 603 604 605 606 607 608
     *         description "A tree gnome ball player"   attackable true
     *     609 610
     *         description "He's on your team"          attackable false
     *
     * attackable=false puts 609 and 610 in the same class as the Referee and
     * the Official -- scenery you talk to, not opposition. One field agreeing
     * would be suggestive; two fields agreeing on the same partition, in the
     * shipped data, is the answer. The wiki keeps them on a separate page for
     * the same reason, and the Official says it out loud in the dialogue below:
     * "the gnomes in orange are on your team".
     *
     * That line was unreachable until today, because nothing was wired to 625.
     * The bug and its only in-game documentation were missing together, which
     * is why nobody ever noticed either.
     */
    public static final int[] BALLERS = {
        595, 597, 598, 599, 600, 602, 603, 604, 605, 606, 607, 608
    };

    /**
     * The two gnomes in orange. Named so the ids are not loose in the file, and
     * so the next person to read BALLERS can see where they went. Nothing
     * dispatches on them yet: passing to a teammate is a real mechanic we do
     * not have -- two squares to pass out, any distance to receive back -- and
     * inventing it from an article sentence is not the same as recovering it.
     */
    public static final int[] TEAMMATES = { 609, 610 };

    /** Which Gnome Baller currently holds the ball, or null if nobody does. */
    private static Npc ballHolder;

    public static boolean isBaller(int npcId) {
        for (int id : BALLERS) {
            if (id == npcId) {
                return true;
            }
        }
        return false;
    }

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case REFEREE:
                referee(npc, player);
                return;
            case GOALIE:
                new Conversation(player, npc)
                    .npc("The Goalie does not appear interested in talking.")
                    .start();
                return;
            case CHEERLEADER:
                new Conversation(player, npc)
                    .player("Having fun?")
                    .npc("This is the greatest gnome ball game ever made!")
                    .npc("Can you believe you're not a gnome?")
                    .start();
                return;
            case OFFICIAL:
                official(npc, player);
                return;
            default:
                if (isBaller(npc.getID())) {
                    baller(npc, player);
                }
        }
    }

    /**
     * The Official, npc 625, one spawn at (747,448) -- two tiles from the
     * Referee. He is the game's explainer, and he was silent: the rules of gnome
     * ball existed only in the code that enforced them.
     *
     * "it's also great way to improve your agility" is missing its "a" and is
     * shipped exactly as recorded. That is a different call from the one made
     * for "oneat a time" in Tourist Trap, and the difference is the kind of
     * error each one is: dropping a small word is something a writer does, and
     * losing the space between two words is something a transcriber does. This
     * one reads as Jagex's.
     */
    private void official(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello there")
            .npc("well hello adventurer, are you playing?")
            .options(new Choice("not at the moment",
                                "yes, i'm just having a break") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        notPlaying(c);
                    } else {
                        c.npc("good stuff, there's nothing like chasing a pigs bladder..")
                         .npc("..to remind one that they're alive");
                    }
                }
            })
            .start();
    }

    private void notPlaying(Conversation c) {
        c.npc("well really you shouldn't be on the pitch")
         .npc("some of these games get really rough")
         .options(new Choice("how do you play?",
                             "it looks like a silly game anyway") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("it's easy, you're given a ball from the ref")
                     .npc("the gnomes in orange are on your team")
                     .npc("you then charge at the gnome defense and try to throw the ball..")
                     .npc("..through the net to the goal catcher, it's a rough game but fun")
                     .npc("it's also great way to improve your agility");
                } else {
                    c.npc("gnome ball silly!, this my friend is the backbone of our community")
                     .npc("it also happens to be a great way to stay fit and agile");
                }
            }
        });
    }

    private void referee(Npc npc, Player player) {
        if (player.getInventory().countId(BALL) > 0) {
            new Conversation(player, npc)
                .npc("The ball's still in play, effendi -- er, traveller.")
                .start();
            return;
        }
        if (ballHolder != null) {
            new Conversation(player, npc)
                .npc("One of the Gnome Ballers still has the ball in play.")
                .npc("Get it back off them, or wait for them to lose it.")
                .start();
            return;
        }
        Conversation c = new Conversation(player, npc);
        c.npc("Hi, welcome to gnome ball!")
         .npc("Take the ball, run at the gnome defense, and throw it through")
         .npc("the net to score. There are no rules, so it can get a bit rough.")
         .player("I'm in.")
         .npc("Ready... go!")
         .message("The ref throws the ball into the air");
        Player p = player;
        c.then(new org.rscdaemon.server.quest.dialogue.Effect() {
            public void run(Conversation c) {
                Player pl = c.getPlayer();
                pl.getInventory().add(new InvItem(BALL, 1));
                pl.getActionSender().sendInventory();
            }
        });
        c.start();
    }

    /** Called from InvUseOnNpc when the ball is used on the Goalie -- shooting. */
    public static void shoot(Player player, Npc goalie) {
        player.getInventory().remove(BALL, 1);
        player.getActionSender().sendInventory();
        if (DataConversions.random(0, 1) == 0) {
            player.getActionSender().sendMessage("@pnk@ It flies through the net, into the hands of the goal catcher!");
            player.incExp(4, 40, true);  // Ranged
            player.incExp(16, 40, true); // Agility
            player.getActionSender().sendStat(4);
            player.getActionSender().sendStat(16);
        } else {
            player.getActionSender().sendMessage("@gry@ It misses! You'll need to see the Referee for another ball.");
        }
    }

    /**
     * Called from InvUseOnPlayer when the ball is used on another player --
     * throwing it to them. Deliberately not fenced to the field: the ball is
     * a social item that works between any two players, anywhere. The client
     * draws it with projectile sprite 3163 (type 3), the gnome ball throw
     * from the genuine media archive. No experience is given -- only goals
     * shot at the Goalie pay out, so carrying a ball around the world is
     * fun, not a training method.
     */
    public static void throwBall(Player player, Player target) {
        if (target.isBusy()) {
            player.getActionSender().sendMessage("@gry@ " + target.getUsername() + " isn't ready to catch it right now.");
            return;
        }
        if (target.getInventory().full()) {
            player.getActionSender().sendMessage("@gry@ " + target.getUsername() + "'s hands are full!");
            return;
        }
        player.getInventory().remove(BALL, 1);
        player.getActionSender().sendInventory();
        Projectile ball = new Projectile(player, target, 3);
        ArrayList<Player> viewers = new ArrayList<Player>();
        viewers.addAll(player.getViewArea().getPlayersInView());
        viewers.addAll(target.getViewArea().getPlayersInView());
        for (Player p : viewers) {
            p.informOfProjectile(ball);
        }
        target.getInventory().add(new InvItem(BALL, 1));
        target.getActionSender().sendInventory();
        player.getActionSender().sendMessage("@yel@ You throw the ball to " + target.getUsername() + "!");
        target.getActionSender().sendMessage("@yel@ " + player.getUsername() + " throws you the ball!");
    }

    private void baller(Npc npc, Player player) {
        if (npc.equals(ballHolder)) {
            if (DataConversions.random(0, 1) == 0) {
                new Conversation(player, npc)
                    .player("Give me that ball back!")
                    .npc("hee hee")
                    .message("You skillfully grab the ball and push the gnome to the floor")
                    .start();
                ballHolder = null;
                player.getInventory().add(new InvItem(BALL, 1));
                player.getActionSender().sendInventory();
            } else {
                new Conversation(player, npc)
                    .player("Give me that ball back!")
                    .npc("hee hee")
                    .message("Ouch -- the gnome shrugs you off")
                    .start();
                hurt(player, 1, 3);
            }
            return;
        }
        if (player.getInventory().countId(BALL) <= 0) {
            new Conversation(player, npc)
                .npc("The Gnome Baller does not appear interested in talking.")
                .start();
            return;
        }
        if (DataConversions.random(0, 1) == 0) {
            new Conversation(player, npc)
                .message("The gnome tries to tackle you...")
                .message("He takes the ball and pushes you to the floor!")
                .start();
            player.getInventory().remove(BALL, 1);
            player.getActionSender().sendInventory();
            ballHolder = npc;
            hurt(player, 1, 4);
        } else {
            new Conversation(player, npc)
                .message("The gnome tries to tackle you...")
                .message("You manage to push him away.")
                .start();
        }
    }

    private void hurt(Player player, int min, int max) {
        int damage = Math.min(player.getCurStat(HITS), DataConversions.random(min, max));
        player.setCurStat(HITS, player.getCurStat(HITS) - damage);
        player.getActionSender().sendStat(HITS);
    }
}
