/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.npchandler.NpcHandler;

public class KebabSeller
implements NpcHandler {
    public static final World world = World.getWorld();

    public void handleNpc(final Npc npc, Player player) throws Exception {
        player.informOfNpcMessage(new ChatMessage(npc, "Would you like to buy a nice kebab? Only 1 gold", player));
        player.setBusy(true);
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.setBusy(false);
                String[] options = new String[]{"I think I'll give it a miss", "Yes please"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(final int option, String reply) {
                        if (this.owner.isBusy()) {
                            return;
                        }
                        this.owner.informOfChatMessage(new ChatMessage(this.owner, reply, npc));
                        this.owner.setBusy(true);
                        DelayedEvent.world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                this.owner.setBusy(false);
                                if (option == 1) {
                                    if (this.owner.getInventory().remove(10, 1) > -1) {
                                        this.owner.getActionSender().sendMessage("You buy a kebab");
                                        this.owner.getInventory().add(new InvItem(210, 1));
                                        this.owner.getActionSender().sendInventory();
                                        npc.unblock();
                                    } else {
                                        this.owner.informOfChatMessage(new ChatMessage(this.owner, "Oops I forgot to bring any money with me", npc));
                                        this.owner.setBusy(true);
                                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                            public void action() {
                                                this.owner.setBusy(false);
                                                this.owner.informOfNpcMessage(new ChatMessage(npc, "Come back when you have some", this.owner));
                                                npc.unblock();
                                            }
                                        });
                                    }
                                } else {
                                    npc.unblock();
                                }
                            }
                        });
                    }
                });
                this.owner.getActionSender().sendMenu(options);
            }
        });
        npc.blockedBy(player);
    }
}

