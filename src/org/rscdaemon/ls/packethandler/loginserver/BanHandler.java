/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.sql.Rows;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packetbuilder.loginserver.ReplyPacketBuilder;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.util.DataConversions;

public class BanHandler
implements PacketHandler {
    private ReplyPacketBuilder builder = new ReplyPacketBuilder();

    public void handlePacket(Packet p, Connection session) throws Exception {
        long uID = ((LSPacket)p).getUID();
        boolean banned = ((LSPacket)p).getID() == 4;
        long user = p.readLong();
        // group_id lives on the player row in this schema — the legacy site's
        // `users` table does not exist.
        Rows result = Server.db.getQuery("SELECT p.group_id, p.playermod FROM `rscd_players` AS p WHERE p.user=" + user);
        if (!result.next()) {
            this.builder.setSuccess(false);
            this.builder.setReply("There is not an account by that username");
        } else if (banned && (result.getInt("group_id") < 3 || result.getInt("playermod") == 1)) {
            this.builder.setSuccess(false);
            this.builder.setReply("You cannot ban a (p)mod or admin!");
        } else {
            /*
             * The row exists -- the SELECT above just proved it -- so the
             * update's return value says nothing about whether the account is
             * there. It used to be read as if it did, and because MysqlClient
             * does not ask for CLIENT_FOUND_ROWS the count is rows *changed*,
             * not rows matched: unbanning somebody who was never banned sets
             * banned='0' over banned='0', changes nothing, and reported "There
             * is not an account by that username" for a command that had in
             * fact just succeeded.
             */
            Server.db.updateQuery("UPDATE `rscd_players` SET `banned`='" + (banned ? "1" : "0") + "' WHERE `user`=" + user);
            World w = Server.getServer().findWorld(user);
            if (w != null) {
                w.getActionSender().logoutUser(user);
            }
            this.builder.setSuccess(true);
            this.builder.setReply(DataConversions.hashToUsername(user) + " has been " + (banned ? "banned" : "unbanned"));
        }
        this.builder.setUID(uID);
        LSPacket temp = this.builder.getPacket();
        if (temp != null) {
            session.write((Object)temp);
        }
    }
}

