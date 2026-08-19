package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packetbuilder.RSCPacketBuilder;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Heartbeat;

/**
 * Answers the community registry's proof-of-address check.
 *
 * The registry will not list an address on the say-so of whoever sent the
 * heartbeat, or anyone could list somebody else's server -- or a host that
 * simply is not theirs. So the heartbeat reply carries a nonce, and the
 * registry then connects to the address that was claimed and asks for it. Only
 * the process actually listening there can answer, which is exactly the thing
 * being proved.
 *
 * The reply is the raw nonce with no packet header, the same shape as the
 * session key SessionRequest sends, because the caller here is a small script
 * reading a fixed number of bytes rather than the game client.
 *
 * A server with the heartbeat off has no nonce and answers nothing. So does
 * one whose nonce has not been asked for yet. Neither is an error; the check
 * simply does not pass, and the world stays unlisted.
 */
public class Challenge implements PacketHandler {

    public void handlePacket(Packet p, Connection session) throws Exception {
        byte[] nonce = Heartbeat.getNonce();
        if (nonce == null || nonce.length == 0) {
            return;
        }

        RSCPacketBuilder pb = new RSCPacketBuilder();
        pb.setBare(true);
        pb.addBytes(nonce);
        session.write((Object) pb.toPacket());
    }
}
