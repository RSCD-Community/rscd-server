package org.rscdaemon.ls.packethandler.loginserver;

import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.LSPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.sql.Rows;

/**
 * The party hall's calendar, kept where the account table is kept.
 *
 * Three packets. 16 books a party: the fee was already taken game-side, so
 * this only answers whether the booking stands -- 0 booked, 1 the account
 * already has one coming up, 2 no such account, 3 the slot overlaps a party
 * someone else already booked. The one-per-account rule is on
 * rscd_players.owner, the website account, deliberately: characters share an
 * owner, so a player with six alts still holds one slot on the calendar. The
 * overlap rule is on the half-hour window, not a rigid grid: a party runs 30
 * minutes from its start, so a new start is refused while it falls within 30
 * minutes either side of an existing one. That keeps every running party's
 * window owned by exactly one host, which is what the Party Animals tally
 * below relies on.
 *
 * 17 lists the calendar: every party still running or yet to start (a party is
 * 30 minutes, so "still running" reaches half an hour back). The game server
 * calls it on a timer for the herald, and on every read of the Magical Party
 * Schedule, so the book is always the database's truth and never a stale copy.
 *
 * 18 is the Party Animals tally: the game side reports how many items the
 * party cannons just fired while a scheduled party was running, and the
 * running party's host is credited in rscd_party_animals -- lifetime totals,
 * because the calendar rows themselves are swept after a day. Which party owns
 * the moment is decided here against the database, not against the game's
 * cache, which can be minutes stale about a booking made moments ago. Shots
 * fired outside any scheduled window are never reported and never counted:
 * the hiscore ranks scheduled parties, not private cannon use.
 *
 * Rows older than a day are swept on each list rather than by any scheduled
 * job -- the calendar is read every few minutes forever, which is as good as a
 * cron and one less moving part.
 */
public class PartyScheduleHandler implements PacketHandler {

    private static boolean tableReady;

    private static void ensureTable() throws MysqlException {
        if (tableReady) {
            return;
        }
        Server.db.updateQuery("CREATE TABLE IF NOT EXISTS `rscd_parties` ("
            + "`id` INT NOT NULL AUTO_INCREMENT,"
            + "`owner` INT NOT NULL,"
            + "`user` BIGINT NOT NULL,"
            + "`start` BIGINT NOT NULL,"
            + "PRIMARY KEY (`id`), KEY `owner_idx` (`owner`), KEY `start_idx` (`start`)"
            + ") ENGINE=InnoDB");
        Server.db.updateQuery("CREATE TABLE IF NOT EXISTS `rscd_party_animals` ("
            + "`user` BIGINT NOT NULL,"
            + "`items` INT NOT NULL DEFAULT 0,"
            + "`stamp` BIGINT NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (`user`)"
            + ") ENGINE=InnoDB");
        tableReady = true;
    }

    /** How long a party owns its window, in seconds -- PartySchedule.MINUTES. */
    private static final long WINDOW = 1800L;

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        LSPacketBuilder reply = new LSPacketBuilder();
        reply.setUID(uID);
        long now = System.currentTimeMillis() / 1000L;

        try {
            ensureTable();

            if (((LSPacket)p).getID() == 16) {
                long user = p.readLong();
                long start = p.readLong();

                Rows account = Server.db.getQuery(
                    "SELECT owner FROM `rscd_players` WHERE `user`=" + user);
                if (!account.next()) {
                    reply.addByte((byte)2);
                } else {
                    int owner = account.getInt("owner");
                    Rows existing = Server.db.getQuery(
                        "SELECT 1 FROM `rscd_parties` WHERE `owner`=" + owner
                        + " AND `start` > " + now);
                    Rows clash = Server.db.getQuery(
                        "SELECT 1 FROM `rscd_parties` WHERE `start` > "
                        + (start - WINDOW) + " AND `start` < " + (start + WINDOW));
                    if (existing.next()) {
                        reply.addByte((byte)1);
                    } else if (clash.next()) {
                        reply.addByte((byte)3);
                    } else {
                        Server.db.updateQuery(
                            "INSERT INTO `rscd_parties`(`owner`, `user`, `start`) VALUES("
                            + owner + ", " + user + ", " + start + ")");
                        reply.addByte((byte)0);
                    }
                }
            } else if (((LSPacket)p).getID() == 18) {
                int shots = p.readByte();
                Rows hosts = Server.db.getQuery(
                    "SELECT `user` FROM `rscd_parties` WHERE `start` <= " + now
                    + " AND `start` > " + (now - WINDOW));
                while (hosts.next()) {
                    Server.db.updateQuery(
                        "INSERT INTO `rscd_party_animals`(`user`, `items`, `stamp`) VALUES("
                        + hosts.getLong("user") + ", " + shots + ", " + now + ")"
                        + " ON DUPLICATE KEY UPDATE `items`=`items`+" + shots
                        + ", `stamp`=" + now);
                }
                reply.addByte((byte)0);
            } else {
                Server.db.updateQuery(
                    "DELETE FROM `rscd_parties` WHERE `start` < " + (now - 86400L));
                Rows rows = Server.db.getQuery(
                    "SELECT `user`, `start` FROM `rscd_parties` WHERE `start` > "
                    + (now - 1800L) + " ORDER BY `start` LIMIT 20");
                List<long[]> parties = new ArrayList<long[]>();
                while (rows.next()) {
                    parties.add(new long[] { rows.getLong("user"), rows.getLong("start") });
                }
                reply.addByte((byte)parties.size());
                for (long[] party : parties) {
                    reply.addLong(party[0]);
                    reply.addLong(party[1]);
                }
            }
        } catch (MysqlException e) {
            Server.error(e);
            /* An empty reply would leave the game side waiting on a handler
               that never fires; a status byte always goes back. */
            reply.addByte((byte)2);
        }

        session.write((Object)reply.toPacket());
    }
}
