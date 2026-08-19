package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Logger;

/**
 * The client's abuse report form (packet 7).
 *
 * The client sends the accused player's name as a 12-character hash followed
 * by one of the twelve rule numbers its own form lists.
 *
 * What was here before read neither field and answered every report with
 * "Please go to www.fsclassic.net to report that user" -- a private server
 * that has not existed in years and was never ours to send anybody to. Every
 * report a player filed went nowhere, and told them to go somewhere dead.
 * Found by the packet-handler sweep of the invented-dialogue register.
 *
 * This reads both fields and writes the report to the server log, so that a
 * report lands somewhere a moderator can read it. The client's form also
 * promises "a snapshot of the last 60 secs of activity"; no chat history is
 * retained to snapshot, so that promise is not repeated here and is not kept.
 */
public class ReportHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    /** The twelve rules, worded and numbered as the client's own form lists them. */
    private static final String[] RULES = {
        "?", "Offensive language", "Item scamming", "Password scamming",
        "Bug abuse", "Staff impersonation", "Account sharing/trading",
        "Macroing", "Multiple logging in", "Encouraging others to break rules",
        "Misuse of customer support", "Advertising / website",
        "Real world item trading",
    };

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        long accused = p.readLong();
        int rule = p.readByte() & 0xff;
        String name = DataConversions.hashToUsername(accused);
        Logger.event("[report] " + player.getUsername() + " reported " + name
            + " for rule " + rule + " ("
            + (rule > 0 && rule < RULES.length ? RULES[rule] : "unknown") + ")");
        player.getActionSender().sendMessage(
            "@gre@ Thank you - your report about " + name + " has been passed to the moderators.");
    }
}
