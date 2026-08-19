/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.util.List;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.client.GameObjectPositionPacketBuilder;
import org.rscdaemon.server.packetbuilder.client.ItemPositionPacketBuilder;
import org.rscdaemon.server.packetbuilder.client.NpcPositionPacketBuilder;
import org.rscdaemon.server.packetbuilder.client.NpcUpdatePacketBuilder;
import org.rscdaemon.server.packetbuilder.client.PlayerPositionPacketBuilder;
import org.rscdaemon.server.packetbuilder.client.PlayerUpdatePacketBuilder;
import org.rscdaemon.server.packetbuilder.client.WallObjectPositionPacketBuilder;
import org.rscdaemon.server.util.EntityList;

/**
 * The once-per-tick broadcast: everything every connected client is told
 * about the world, in one pass, driven from GameEngine's main loop.
 *
 * updateClients() runs in two phases. First the world moves as a whole --
 * every mob walks one step of its path and every player's watched-entity
 * lists are revalidated against what is now in view. Only then does each
 * player get their packets, so nobody is ever sent a half-moved world where
 * one mob has stepped and its neighbour has not. updateCollections() runs
 * last and clears all the changed-this-tick flags, which is why the packet
 * builders may read them freely: nothing resets until everyone has been told.
 *
 * The builders are stateful (setPlayer then getPacket) and shared across
 * players, which is safe only because this whole class runs on the single
 * engine thread.
 */
public final class ClientUpdater {
    private static World world = World.getWorld();
    private EntityList<Player> players = world.getPlayers();
    private EntityList<Npc> npcs = world.getNpcs();
    private PlayerPositionPacketBuilder playerPositionBuilder = new PlayerPositionPacketBuilder();
    private PlayerUpdatePacketBuilder playerApperanceBuilder = new PlayerUpdatePacketBuilder();
    private GameObjectPositionPacketBuilder gameObjectPositionBuilder = new GameObjectPositionPacketBuilder();
    private WallObjectPositionPacketBuilder wallObjectPositionPacketBuilder = new WallObjectPositionPacketBuilder();
    private ItemPositionPacketBuilder itemPositionBuilder = new ItemPositionPacketBuilder();
    private NpcPositionPacketBuilder npcPositionPacketBuilder = new NpcPositionPacketBuilder();
    private NpcUpdatePacketBuilder npcApperanceBuilder = new NpcUpdatePacketBuilder();

    public ClientUpdater() {
        world.setClientUpdater(this);
    }

    public void updateClients() {
        this.updateNpcPositions();
        this.updatePlayersPositions();
        this.updateMessageQueues();
        this.updateOffers();
        for (Player p : this.players) {
            this.updateTimeouts(p);
            this.updatePlayerPositions(p);
            this.updateNpcPositions(p);
            this.updateGameObjects(p);
            this.updateWallObjects(p);
            this.updateItems(p);
            this.updatePlayerApperances(p);
            this.updateNpcApperances(p);
        }
        this.updateCollections();
    }

    private void updateNpcPositions() {
        for (Npc n : this.npcs) {
            n.resetMoved();
            n.updatePosition();
            n.updateAppearanceID();
        }
    }

    private void updatePlayersPositions() {
        for (Player p : this.players) {
            p.resetMoved();
            p.updatePosition();
            p.updateAppearanceID();
        }
        for (Player p : this.players) {
            p.revalidateWatchedPlayers();
            p.revalidateWatchedObjects();
            p.revalidateWatchedItems();
            p.revalidateWatchedNpcs();
            p.updateViewedPlayers();
            p.updateViewedObjects();
            p.updateViewedItems();
            p.updateViewedNpcs();
        }
    }

    /* One chat message per sender per tick, delivered to everyone in view
       who is willing to hear it: the block-all privacy setting and the
       ignore list both filter here, and a player moderator talks through
       both -- mutes are enforceable only if the muted can be addressed. */
    private void updateMessageQueues() {
        for (Player sender : this.players) {
            ChatMessage message = sender.getNextChatMessage();
            if (message == null || !sender.loggedIn()) continue;
            for (Player recipient : sender.getViewArea().getPlayersInView()) {
                if (sender.getIndex() == recipient.getIndex() || !recipient.loggedIn() || recipient.getPrivacySetting(0) && !recipient.isFriendsWith(sender.getUsernameHash()) && !sender.isPMod() || recipient.isIgnoring(sender.getUsernameHash()) && !sender.isPMod()) continue;
                recipient.informOfChatMessage(message);
            }
        }
    }

    public void updateOffers() {
        for (Player player : this.players) {
            Player affectedPlayer;
            if (!player.requiresOfferUpdate()) continue;
            player.setRequiresOfferUpdate(false);
            if (player.isTrading()) {
                affectedPlayer = player.getWishToTrade();
                if (affectedPlayer == null) continue;
                affectedPlayer.getActionSender().sendTradeItems();
                continue;
            }
            if (!player.isDueling() || (affectedPlayer = player.getWishToDuel()) == null) continue;
            player.getActionSender().sendDuelSettingUpdate();
            affectedPlayer.getActionSender().sendDuelSettingUpdate();
            affectedPlayer.getActionSender().sendDuelItems();
        }
    }

    /* Flushes each player's queued packets to the wire, and does the actual
       disconnect for players marked destroyed -- after the flush, so the
       goodbye the destroy queued (a logout confirmation, a fatal error
       message) still arrives before the socket closes. */
    public void sendQueuedPackets() {
        for (Player p : this.players) {
            List<RSCPacket> packets = p.getActionSender().getPackets();
            for (RSCPacket packet : packets) {
                p.getSession().write((Object)packet);
            }
            p.getActionSender().clearPackets();
            if (!p.destroyed()) continue;
            p.getSession().close();
            p.remove();
        }
    }

    /* Three ways to be dropped: a client that has not pinged for 30 seconds
       is gone; a player warned about idling who stays put for 20 minutes
       total is logged out; and the warning itself is issued at 15 minutes
       without a step. */
    private void updateTimeouts(Player p) {
        if (p.destroyed()) {
            return;
        }
        long curTime = System.currentTimeMillis();
        if (curTime - p.getLastPing() >= 30000L) {
            p.destroy(false);
        } else if (p.warnedToMove()) {
            if (curTime - p.getLastMoved() >= 1200000L && p.loggedIn()) {
                p.destroy(false);
            }
        } else if (curTime - p.getLastMoved() >= 900000L) {
            p.getActionSender().sendMessage("@gry@ You have not moved for 15 mins, please move to a new area to avoid logout.");
            p.warnToMove();
        }
    }

    private void updateNpcPositions(Player p) {
        this.npcPositionPacketBuilder.setPlayer(p);
        RSCPacket temp = this.npcPositionPacketBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updateNpcApperances(Player p) {
        this.npcApperanceBuilder.setPlayer(p);
        RSCPacket temp = this.npcApperanceBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updateWallObjects(Player p) {
        this.wallObjectPositionPacketBuilder.setPlayer(p);
        RSCPacket temp = this.wallObjectPositionPacketBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updateGameObjects(Player p) {
        this.gameObjectPositionBuilder.setPlayer(p);
        RSCPacket temp = this.gameObjectPositionBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updateItems(Player p) {
        this.itemPositionBuilder.setPlayer(p);
        RSCPacket temp = this.itemPositionBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updatePlayerPositions(Player p) {
        this.playerPositionBuilder.setPlayer(p);
        RSCPacket temp = this.playerPositionBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updatePlayerApperances(Player p) {
        this.playerApperanceBuilder.setPlayer(p);
        RSCPacket temp = this.playerApperanceBuilder.getPacket();
        if (temp != null) {
            p.getSession().write((Object)temp);
        }
    }

    private void updateCollections() {
        for (Player p : this.players) {
            if (!p.isRemoved() || !p.initialized()) continue;
            world.unregisterPlayer(p);
        }
        for (Player p : this.players) {
            p.getWatchedPlayers().update();
            p.getWatchedObjects().update();
            p.getWatchedItems().update();
            p.getWatchedNpcs().update();
            p.clearProjectilesNeedingDisplayed();
            p.clearPlayersNeedingHitsUpdate();
            p.clearNpcsNeedingHitsUpdate();
            p.clearChatMessagesNeedingDisplayed();
            p.clearNpcMessagesNeedingDisplayed();
            p.clearBubblesNeedingDisplayed();
            p.resetSpriteChanged();
            p.setAppearnceChanged(false);
        }
        for (Npc n : this.npcs) {
            n.resetSpriteChanged();
            n.setAppearnceChanged(false);
        }
    }
}

