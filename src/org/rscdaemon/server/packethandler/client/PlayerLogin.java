/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;

public class PlayerLogin
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        int loginCode;
        Player player;
        block6: {
            player = (Player)session.getAttachment();
            try {
                boolean reconnecting = p.readByte() == 1;
                short clientVersion = p.readShort();
                /*
                 * 62, and it is always 62: four session keys and a uid (5 * 4
                 * bytes), then the username and password as 20 characters each
                 * with a newline after them. The client pads both to exactly 20
                 * -- addCharacters and padCharacters both loop to i, so a short
                 * name is space-filled and a long one truncated -- so the block
                 * has no variable part at all.
                 *
                 * The length has to be stated because RSA gives back a number,
                 * not a byte string, and a number cannot remember how many
                 * leading zeros were written in front of it. See
                 * DataConversions.toFixedLength.
                 */
                RSCPacket loginPacket = DataConversions.decryptRSA(p.readBytes(p.readByte()), 62);
                int[] sessionKeys = new int[4];
                for (int key = 0; key < sessionKeys.length; ++key) {
                    sessionKeys[key] = loginPacket.readInt();
                }
                int uid = loginPacket.readInt();
                String username = loginPacket.readString(20).trim();
                loginPacket.skip(1);
                String password = loginPacket.readString(20).trim();
                loginPacket.skip(1);
                if (world.countPlayers() >= Config.MAX_PLAYERS) {
                    loginCode = 10;
                    break block6;
                }
                if (clientVersion < Config.SERVER_VERSION) {
                    loginCode = 4;
                    break block6;
                }
                if (System.getProperty("rscd.sessiondebug") != null) {
                    int[] expected = player.getSessionKeys();
                    org.rscdaemon.server.util.Logger.print("[session] from " + session.getRemoteAddress()
                        + " reconnecting=" + reconnecting + " expected[2,3]=" + expected[2] + "," + expected[3]
                        + " received[2,3]=" + sessionKeys[2] + "," + sessionKeys[3]
                        + " player=" + System.identityHashCode(player));
                }
                if (!player.setSessionKeys(sessionKeys)) {
                    loginCode = 5;
                    break block6;
                }
                player.load(username, password, uid, reconnecting);
                return;
            }
            catch (Exception e) {
                /* A failed login is a routine player-facing event, not a
                   crash; one line in the log, code 7 to the client. */
                org.rscdaemon.server.util.Logger.print("[login] rejected with code 7: " + e);
                loginCode = 7;
            }
        }
        RSCPacketBuilder pb = new RSCPacketBuilder();
        pb.setBare(true);
        pb.addByte((byte)loginCode);
        session.write((Object)pb.toPacket());
        /*
         * Refused before the login server was ever asked: world full, client
         * too old, bad session keys, or a malformed login packet. Same reason
         * the close is needed here as in the login-server response handler --
         * this player is not in world.getPlayers(), so ClientUpdater will
         * never reap it and the connection would stay open forever. The close
         * queues behind the response byte, so the client still sees the code.
         */
        player.destroy(true);
        session.close();
    }
}

