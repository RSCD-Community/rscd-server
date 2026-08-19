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
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.npchandler.NpcHandler;

/*
 * The Make over mage, south of Falador at (371, 580) -- a spawn that matches
 * the recovered world data exactly, so only what he says was ever wrong.
 *
 * RSCD had him charging 500 coins and speaking three invented lines. The
 * recovered transcript has 3000, split across two lines, and a first option
 * the player would actually say. Restored against it; the mechanics were
 * already right -- the coins come out and the appearance screen opens in the
 * same breath, which is what the transcript records.
 */
public class MakeOverMage
implements NpcHandler {
    public static final World world = World.getWorld();

    /** Transcript: "Of 3000 coins". RSCD asked 500. */
    private static final int PRICE = 3000;
    private static final int COINS = 10;

    public void handleNpc(final Npc npc, Player player) throws Exception {
        player.informOfNpcMessage(new ChatMessage(npc, "Are you happy with your looks?", player));
        player.setBusy(true);
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.informOfNpcMessage(new ChatMessage(npc, "If not I can change them for the cheap cheap price", this.owner));
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        // His own second line, not a continuation this server
                        // folded into the first.
                        this.owner.informOfNpcMessage(new ChatMessage(npc, "Of " + PRICE + " coins", this.owner));
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                this.owner.setBusy(false);
                                String[] options = new String[]{"I'm happy with how I look thank you", "Yes change my looks please"};
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
                                                switch (option) {
                                                    case 1: {
                                                        if (this.owner.getInventory().countId(COINS) < PRICE) {
                                                            this.owner.informOfChatMessage(new ChatMessage(this.owner, "I'll just go get the cash", npc));
                                                            break;
                                                        }
                                                        if (this.owner.getInventory().remove(COINS, PRICE) <= -1) break;
                                                        this.owner.setChangingAppearance(true);
                                                        this.owner.getActionSender().sendAppearanceScreen();
                                                        this.owner.getActionSender().sendInventory();
                                                    }
                                                }
                                                npc.unblock();
                                            }
                                        });
                                    }
                                });
                                this.owner.getActionSender().sendMenu(options);
                            }
                        });
                    }
                });
            }
        });
        npc.blockedBy(player);
    }
}
