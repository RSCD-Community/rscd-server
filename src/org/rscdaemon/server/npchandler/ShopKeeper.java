/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.npchandler.NpcHandler;

public class ShopKeeper
implements NpcHandler {
    public static final World world = World.getWorld();

    public void handleNpc(final Npc npc, Player player) throws Exception {
        final Shop shop = world.getShop(npc);
        if (shop == null) {
            return;
        }
        if (shop.getGreeting() != null) {
            player.informOfNpcMessage(new ChatMessage(npc, shop.getGreeting(), player));
        }
        player.setBusy(true);
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.setBusy(false);
                this.owner.setMenuHandler(new MenuHandler(shop.getOptions()){

                    public void handleReply(final int option, String reply) {
                        if (this.owner.isBusy()) {
                            return;
                        }
                        this.owner.informOfChatMessage(new ChatMessage(this.owner, reply, npc));
                        this.owner.setBusy(true);
                        DelayedEvent.world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                this.owner.setBusy(false);
                                /* Not "option == 0". Jagex lists the refusal
                                   first for the two Grand Tree grocers, and
                                   Scavvo has two different options that both
                                   open his shop, so the shop names the one
                                   option that closes the conversation. */
                                if (option != shop.getDeclineOption()) {
                                    this.owner.setAccessingShop(shop);
                                    this.owner.getActionSender().showShop(shop);
                                }
                                npc.unblock();
                            }
                        });
                    }
                });
                this.owner.getActionSender().sendMenu(shop.getOptions());
            }
        });
        npc.blockedBy(player);
    }
}

