/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.npchandler.NpcHandler;

public class Bankers
implements NpcHandler {
    public static final World world = World.getWorld();

    public void handleNpc(final Npc npc, Player player) throws Exception {
        player.setBusy(true);
        player.informOfChatMessage(new ChatMessage(player, "I'd like to access my bank account please", npc));
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.informOfNpcMessage(new ChatMessage(npc, "Certainly " + (this.owner.isMale() ? "sir" : "miss"), this.owner));
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        this.owner.setAccessingBank(true);
                        this.owner.getActionSender().showBank();
                    }
                });
                npc.unblock();
            }
        });
        npc.blockedBy(player);
    }
}

