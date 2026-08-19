/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.PlayerSave;
import org.rscdaemon.ls.model.World;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.util.DataConversions;

public class PlayerSaveHandler
implements PacketHandler {
    public void handlePacket(Packet p, Connection session) throws Exception {
        World world = (World)session.getAttachment();
        long usernameHash = p.readLong();
        int owner = p.readInt();
        PlayerSave save = Server.getServer().findSave(usernameHash, world);
        if (save == null) {
            System.out.println("Error loading data for: " + DataConversions.hashToUsername(usernameHash));
            return;
        }
        System.out.println("Adding save data for: " + save.getUsername());
        if (owner != save.getOwner()) {
            System.out.println("WARNING ATTEMPTED DUPE");
        }
        save.setOwner(owner);
        save.setLogin(p.readLong(), p.readLong());
        save.setTotals(p.readShort(), p.readShort());
        save.setLocation(p.readShort(), p.readShort());
        save.setFatigue(p.readShort());
        save.setAppearance(p.readByte(), p.readByte(), p.readByte(), p.readByte(), p.readByte(), p.readByte(), p.readByte() == 1, p.readLong());
        save.setCombatStyle(p.readByte());
        for (int i = 0; i < 19; ++i) {
            save.setStat(i, p.readLong(), p.readShort());
        }
        int invCount = p.readShort();
        save.clearInvItems();
        for (int i = 0; i < invCount; ++i) {
            save.addInvItem(p.readShort(), p.readInt(), p.readByte() == 1);
        }
        int bnkCount = p.readShort();
        save.clearBankItems();
        for (int i = 0; i < bnkCount; ++i) {
            save.addBankItem(p.readShort(), p.readInt());
        }
        int questCount = p.readShort();
        save.clearQuests();
        for (int i = 0; i < questCount; ++i) {
            save.setQuestStage(p.readShort(), p.readInt());
        }
        save.setAmuletCharges(p.readByte());
        save.setPoison(p.readByte(), p.readByte());
        save.setLastUpdate(System.currentTimeMillis());
        if (!save.save()) {
            System.out.println("Error saving: " + save.getUsername());
        }
    }
}

