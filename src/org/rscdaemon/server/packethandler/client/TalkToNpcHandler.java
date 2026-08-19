/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.NpcHandler;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.Logger;

public class TalkToNpcHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        final Npc affectedNpc = world.getNpc(p.readShort());
        if (affectedNpc == null) {
            return;
        }
        player.setFollowing(affectedNpc);
        player.setStatus(Action.TALKING_MOB);
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedNpc, 1){

            public void arrived() {
                this.owner.resetPath();
                if (this.owner.isBusy() || this.owner.isRanging() || !this.owner.nextTo(affectedNpc) || this.owner.getStatus() != Action.TALKING_MOB) {
                    return;
                }
                this.owner.resetAll();
                if (affectedNpc.isBusy()) {
                    this.owner.getActionSender().sendMessage(affectedNpc.getDef().getName() + " is currently busy.");
                    return;
                }
                affectedNpc.resetPath();
                NpcHandler handler = world.getNpcHandler(affectedNpc.getID());
                boolean quest = this.owner.getQuestManager().associatedWithQuest(affectedNpc);
                if (handler != null) {
                    try {
                        handler.handleNpc(affectedNpc, this.owner);
                    }
                    catch (Exception e) {
                        Logger.error("Exception with npc[" + affectedNpc.getIndex() + "] from " + this.owner.getUsername() + " [" + this.owner.getCurrentIP() + "]: " + e.getMessage());
                        this.owner.getActionSender().sendLogout();
                        this.owner.destroy(false);
                    }
                } else if (quest) {
                    this.owner.getQuestManager().triggerEntity(QuestTrigger.NPC_TALK, affectedNpc);
                }
            }
        });
    }
}

