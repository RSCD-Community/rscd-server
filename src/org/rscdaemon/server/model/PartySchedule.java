package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.util.DataConversions;

/**
 * The game side of the party hall calendar.
 *
 * The database lives behind the login server (see the ls-side
 * PartyScheduleHandler), so this holds a cache of the upcoming parties and
 * refreshes it over the LS link: on boot, after every booking, and every five
 * minutes on the herald's own timer. Reads of the Magical Party Schedule do
 * not go through the cache at all -- they fetch fresh and render what came
 * back, so the book is never minutes out of date.
 *
 * The herald is the once-a-minute event installed from Server: five minutes
 * before a party it tells the whole world, once, in plain chat -- a message,
 * deliberately not an alert window; nobody's fishing trip should be
 * interrupted by a popup -- and again, once, when the party begins. What has
 * been said is remembered by (host, start) so a cache refresh, which builds
 * new Entry objects, cannot repeat it. The set survives restarts nowhere and
 * does not need to: re-announcing after a mid-window restart is the worst
 * case, and the window is five minutes wide.
 */
public final class PartySchedule {

    /** The guards' booking fee, in coins. */
    public static final int FEE = 50000;

    /**
     * How long a party runs. The cannons never stop for it, but the window
     * is real: bookings that overlap it are refused (ls-side
     * PartyScheduleHandler), and items the cannons fire inside it count
     * toward the host's Party Animals hiscore.
     */
    public static final int MINUTES = 30;

    public static final class Entry {
        public final long user;
        public final long start;

        public Entry(long user, long start) {
            this.user = user;
            this.start = start;
        }
    }

    private static List<Entry> cache = new ArrayList<Entry>();
    private static final Set<String> announced = new HashSet<String>();

    private PartySchedule() {
    }

    public static synchronized void setCache(List<Entry> entries) {
        cache = entries;
    }

    private static synchronized List<Entry> entries() {
        return cache;
    }

    /**
     * Whether a scheduled party's window covers this moment. Used by the
     * cannons to decide if a shot is worth reporting for the Party Animals
     * tally at all -- who gets the credit is decided database-side, this
     * only keeps the wire quiet while nothing is booked. The cache refreshes
     * within five minutes of a booking, so at worst the first minutes of a
     * booked-seconds-ago party go unreported; the announcement timer has the
     * same horizon, so such a party was never heralded either.
     */
    public static boolean partyActive() {
        long now = System.currentTimeMillis() / 1000L;
        for (Entry party : entries()) {
            if (party.start <= now && now < party.start + MINUTES * 60L) {
                return true;
            }
        }
        return false;
    }

    /** Install the herald and take the first calendar fetch. */
    public static void install(final World world) {
        world.getServer().getLoginConnector().getActionSender().partyList();
        world.getDelayedEventHandler().add(new DelayedEvent(null, 60000) {
            private int minutes;

            public void run() {
                this.updateLastRun();
                if (++this.minutes % 5 == 0) {
                    world.getServer().getLoginConnector().getActionSender().partyList();
                }
                herald(world);
            }
        });
    }

    private static void herald(World world) {
        long now = System.currentTimeMillis() / 1000L;
        for (Entry party : entries()) {
            long until = party.start - now;
            String host = DataConversions.hashToUsername(party.user).toLowerCase();
            if (until > 240 && until <= 300 && announced.add(party.user + ":" + party.start + ":soon")) {
                broadcast(world, "@gre@" + host + "'s party is about to begin at the Seer's party hall!");
            } else if (until <= 0 && until > -120 && announced.add(party.user + ":" + party.start + ":start")) {
                broadcast(world, "@gre@" + host + "'s party has begun at the Seer's party hall!");
            }
        }
    }

    private static void broadcast(World world, String message) {
        for (Player p : world.getPlayers()) {
            p.getActionSender().sendMessage(message);
        }
    }

    /**
     * The Magical Party Schedule's page, rendered from a fresh fetch's
     * entries. Alert-window format: lines split on %, blank lines are "% %",
     * and none of these lines may contain a literal percent.
     */
    public static String bookText(List<Entry> entries) {
        StringBuilder page = new StringBuilder("@yel@Magical Party Schedule% %");
        long now = System.currentTimeMillis() / 1000L;
        if (entries.isEmpty()) {
            page.append("@whi@Nothing is on the calendar.");
            page.append("%@whi@The party hall guards in Seer's Village take bookings.");
            return page.toString();
        }
        for (Entry party : entries) {
            String host = DataConversions.hashToUsername(party.user).toLowerCase();
            page.append("@whi@").append(host).append("'s party - ")
                .append(when(party.start - now)).append("%");
        }
        return page.toString();
    }

    /** A relative time in guardsman's English. */
    private static String when(long seconds) {
        if (seconds <= 0) {
            return "@gre@happening right now!";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return "@or1@in " + minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = minutes / 60;
        minutes %= 60;
        if (hours < 48) {
            return "@or1@in " + hours + (hours == 1 ? " hour" : " hours")
                + (minutes > 0 ? " " + minutes + " mins" : "");
        }
        return "@or1@in " + (hours / 24) + " days";
    }
}
