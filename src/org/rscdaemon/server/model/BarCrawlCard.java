package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.util.DataConversions;

/**
 * The Alfred Grimhand barcrawl card, item 668.
 *
 * State for the bar crawl lives here rather than in either of the two places
 * that drive it, because both need it and they are in different compilation
 * units: quests/BarCrawl.java owns the Barbarian guard, the gate and the
 * card's "read" command, while npchandler/Bartenders.java owns the six
 * bartenders who sign it. quests/ is compiled separately into the default
 * package and cannot be imported from a named one, so the shared half sits in
 * src/ where both can reach it.
 *
 * <h3>How progress is stored</h3>
 *
 * On the quest's stage, as a bitmask offset by one:
 *
 * <pre>
 *     stage &lt;= 0   never asked a guard for a card
 *     stage 1..64  a card has been issued; (stage - 1) is a six-bit mask of
 *                  which bars have signed, in {@link #LINES} order
 *     stage 65     the card has been handed back and the outpost is open
 * </pre>
 *
 * The offset is what separates "card issued, nothing signed" (stage 1) from
 * "never started" (stage 0 or -1 -- Player.getQuestStage returns -1 for a
 * quest with no saved row, the trap GodCharges documents).
 *
 * The card itself carries no state, which is Jagex's design and not a
 * simplification: the wiki records that losing the card means revisiting
 * every bar, and that is exactly what happens here, because the guard's
 * replacement card resets the mask.
 *
 * <h3>What the drinks do</h3>
 *
 * The stated rule is the wiki's, verbatim: "All non-Hits stat drains reduce
 * the stat by 5% of its current level plus a constant amount of four." That
 * compounds -- five percent of the level as it stands after the last drink,
 * not of the base -- and is applied here to the skills each bar's page lists.
 *
 * Hits is the one number the sources disagree about, and the wiki flags the
 * page {{Incomplete}} over precisely this ("Level dependency on drain for the
 * distinct drinks"). Two claims are on it:
 *
 *  - the bar table says only the gutrot and the hand of death cocktail do
 *    damage, "up to 2" each;
 *  - the notes say "You will lose up to about 27 Hits if your Hits level is
 *    99", and warn that drinking at 5-10 Hits could kill you in one hit.
 *
 * Those cannot both be read literally: two drinks doing two damage is four,
 * not twenty-seven, and is not lethal at any level. Of the models that fit,
 * one lands: a drink costs five percent of current Hits, rounded up, at least
 * one. The black skull ale is exempt -- the table is unambiguous that the
 * Rusty Anchor costs no stats and no Hits at all -- so a full crawl is five
 * such drinks, and five of those from 99 comes to 24, which is the "about
 * 27". At the Hits level a wiki editor is likely to have been testing at,
 * forty or so, the same formula produces exactly the "up to 2" the table
 * records for the two drinks it does mention.
 *
 * The editorial "could kill you at 5-10 Hits" is treated as caution rather
 * than as a measurement -- under this model a drink at 5 Hits costs 1. It can
 * still kill: {@link #drink} routes a lethal result through killedBy(null)
 * the same way Poison does, crediting nobody.
 */
public final class BarCrawlCard {

    public static final int CARD = 668;

    private static final int COINS = 10;
    private static final int HITS = 3;

    /* The six bars, in the order the card prints them. */
    public static final int JOLLY_BOAR = 0;
    public static final int BLUE_MOON = 1;
    public static final int RISING_SUN = 2;
    public static final int DEAD_MANS_CHEST = 3;
    public static final int FORESTERS_ARMS = 4;
    public static final int RUSTY_ANCHOR = 5;

    public static final int BARS = 6;

    private static final int ALL = (1 << BARS) - 1;

    /** The stage that means the card has been handed back in. */
    public static final int DONE = ALL + 2;

    private static final String[] LINES = {
        "The jolly boar inn",
        "The blue moon inn",
        "The rising sun",
        "The dead man's chest",
        "The forester's arms",
        "The rusty anchor" };

    private BarCrawlCard() {
    }

    // ------------------------------------------------------------- state --

    private static Quest quest(Player player) {
        return player.getQuestManager().getQuest(Quests.BAR_CRAWL);
    }

    private static int stage(Player player) {
        Quest q = quest(player);
        return q == null ? 0 : q.getStage();
    }

    /** A card has been issued at some point, and the crawl is not finished. */
    public static boolean started(Player player) {
        int s = stage(player);
        return s >= 1 && s < DONE;
    }

    /** The outpost is open. */
    public static boolean finished(Player player) {
        return stage(player) >= DONE;
    }

    /** Which bars have signed. Meaningless unless {@link #started} is true. */
    public static int signatures(Player player) {
        int s = stage(player);
        return s >= 1 && s < DONE ? s - 1 : 0;
    }

    public static boolean signed(Player player, int bar) {
        return (signatures(player) & (1 << bar)) != 0;
    }

    /** Every bar has signed, but the card is still in the player's hands. */
    public static boolean allSigned(Player player) {
        return started(player) && signatures(player) == ALL;
    }

    public static boolean holdingCard(Player player) {
        return player.getInventory().countId(CARD) > 0;
    }

    /**
     * True when this bar should offer its barcrawl line: the player is on the
     * crawl, has the card in hand, and this bar has not signed it yet.
     */
    public static boolean wants(Player player, int bar) {
        return started(player) && holdingCard(player) && !signed(player, bar);
    }

    /** Issue a fresh card, wiping any signatures the old one carried. */
    public static void issue(Player player) {
        Quest q = quest(player);
        if (q != null) {
            q.setStage(1);
        }
        player.getInventory().add(new InvItem(CARD));
        player.getActionSender().sendInventory();
    }

    public static void sign(Player player, int bar) {
        Quest q = quest(player);
        if (q == null || !started(player)) {
            return;
        }
        q.setStage(1 + (signatures(player) | (1 << bar)));
    }

    /** Hand the card back. Takes it away and opens the outpost. */
    public static void handIn(Player player) {
        Quest q = quest(player);
        if (q == null) {
            return;
        }
        player.getInventory().remove(CARD, 1);
        player.getActionSender().sendInventory();
        q.setStage(DONE);
    }

    // -------------------------------------------------------------- read --

    /**
     * The card's "read" command. A window, not a burst of chat lines --
     * seven messages at once scroll past unread. The one-line drunk refusal
     * stays in chat, where a one-liner belongs.
     */
    public static void read(Player player) {
        if (allSigned(player)) {
            player.getActionSender().sendMessage("@gre@You are to drunk to be able to read the barcrawl card");
            return;
        }
        int mask = signatures(player);
        StringBuilder page = new StringBuilder("The official Alfred Grimhand barcrawl% %");
        for (int bar = 0; bar < BARS; bar++) {
            page.append(LINES[bar]).append(" - ")
                .append((mask & (1 << bar)) != 0 ? "completed" : "not completed")
                .append(bar < BARS - 1 ? "%" : "");
        }
        player.getActionSender().sendAlert(page.toString(), true);
    }

    // ------------------------------------------------------------- drink --

    /**
     * Can the player pay? The price is checked everywhere, even where it is
     * not then taken -- see {@link #pay}.
     */
    public static boolean canAfford(Player player, int price) {
        return player.getInventory().countId(COINS) >= price;
    }

    /**
     * Take the price.
     *
     * The Rusty Anchor passes charge=false. Its eight coins were checked and
     * never removed in the real game -- two separate recorded replays on
     * classic.runescape.wiki show it, which is why the total for the crawl is
     * "in theory 171 coins" and in practice 163. It is reproduced rather than
     * quietly corrected, because a bug Jagex shipped for the life of the game
     * is part of what the game was.
     */
    public static void pay(Player player, int price, boolean charge) {
        if (!charge) {
            return;
        }
        player.getInventory().remove(COINS, price);
        player.getActionSender().sendInventory();
    }

    /**
     * Drain the skills this drink hits, then take the Hits off.
     *
     * @param stats skill indices, Formulae.statArray order; never contains 3.
     */
    public static void drink(Player player, int[] stats) {
        for (int i = 0; i < stats.length; i++) {
            int stat = stats[i];
            int current = player.getCurStat(stat);
            int amount = DataConversions.roundUp(current / 100.0 * 5.0) + 4;
            player.setCurStat(stat, Math.max(0, current - amount));
            player.getActionSender().sendStat(stat);
        }
        hit(player, Math.max(1, DataConversions.roundUp(player.getHits() / 100.0 * 5.0)));
    }

    /** Same shape as Poison.hit -- the splat has to reach everyone watching. */
    private static void hit(Player player, int damage) {
        player.setLastDamage(damage);
        int hits = player.getHits() - damage;
        player.setHits(hits);

        List<Player> toInform = new ArrayList<Player>();
        toInform.addAll(player.getViewArea().getPlayersInView());
        for (Player viewer : toInform) {
            viewer.informOfModifiedHits(player);
        }
        player.getActionSender().sendStat(HITS);

        if (hits <= 0) {
            /* Nobody defeated them; killedBy takes the null and skips the
               "You have defeated" branch and the kill log, as Poison does. */
            player.killedBy(null, false);
        }
    }
}
