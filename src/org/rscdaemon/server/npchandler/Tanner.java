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

public class Tanner
implements NpcHandler {
    public static final World world = World.getWorld();

    public void handleNpc(final Npc npc, Player player) throws Exception {
        player.informOfNpcMessage(new ChatMessage(npc, "Greeting friend i'm a manufacturer of leather", player));
        player.setBusy(true);
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.setBusy(false);
                String[] options = new String[]{"Can I buy some leather then?", "Here's some cow hides, can I buy some leather now?", "Leather is rather weak stuff"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(final int option, String reply) {
                        if (this.owner.isBusy()) {
                            return;
                        }
                        this.owner.informOfChatMessage(new ChatMessage(this.owner, reply, npc));
                        this.owner.setBusy(true);
                        DelayedEvent.world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                switch (option) {
                                    case 0: {
                                        this.owner.informOfNpcMessage(new ChatMessage(npc, "I make leather from cow hides", this.owner));
                                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                            public void action() {
                                                this.owner.setBusy(false);
                                                this.owner.informOfNpcMessage(new ChatMessage(npc, "Bring me some of them and a gold coin per hide", this.owner));
                                                npc.unblock();
                                            }
                                        });
                                        break;
                                    }
                                    case 1: {
                                        this.owner.informOfNpcMessage(new ChatMessage(npc, "Ok", this.owner));
                                        world.getDelayedEventHandler().add(new DelayedEvent(this.owner, 500){

                                            public void run() {
                                                InvItem hides = this.owner.getInventory().get(this.owner.getInventory().getLastIndexById(147));
                                                if (hides == null) {
                                                    this.owner.getActionSender().sendMessage("You have run out of cow hides");
                                                    this.running = false;
                                                    this.owner.setBusy(false);
                                                } else if (this.owner.getInventory().countId(10) < 1) {
                                                    this.owner.getActionSender().sendMessage("You have run out of coins");
                                                    this.running = false;
                                                    this.owner.setBusy(false);
                                                } else if (this.owner.getInventory().remove(hides) > -1 && this.owner.getInventory().remove(10, 1) > -1) {
                                                    this.owner.getInventory().add(new InvItem(148, 1));
                                                    this.owner.getActionSender().sendInventory();
                                                } else {
                                                    this.running = false;
                                                    this.owner.setBusy(false);
                                                }
                                            }
                                        });
                                        npc.unblock();
                                        break;
                                    }
                                    case 2: {
                                        this.owner.setBusy(false);
                                        this.owner.informOfNpcMessage(new ChatMessage(npc, "Well yes if all you're concerned with is how much it will protect you in a fight", this.owner));
                                        npc.unblock();
                                        break;
                                    }
                                    default: {
                                        this.owner.setBusy(false);
                                        npc.unblock();
                                    }
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

