package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.NpcPickpocketDef;
import org.rscdaemon.server.entityhandling.defs.extras.ThievingLoot;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

/**
 * An npc's own right-click command.
 *
 * This was a one-line stub that answered "@or1@Coming soon (hopefully)" to
 * everything. Classic has four such commands in total: "pickpocket", which is
 * Thieving and is implemented here; "tackle" and "pass to", which are gnome
 * ball; and "watch", which exists on exactly one npc in the game -- npc 669,
 * the Mercenary Captain of Tourist trap.
 *
 * So this handler does what TalkToNpcHandler does -- walk to the npc, check
 * nobody is busy, then act. A quest that has claimed the npc's *command* gets
 * first refusal through the NPC_COMMAND trigger, because a quest command beats
 * a skill: the Digsite workmen are pickpocketed for a specimen brush the quest
 * needs, and that has to be the quest's answer and not this one's.
 *
 * The claim asked about is commandClaimed and not the ordinary
 * associatedWithQuest, which would be wrong here and quietly so -- see
 * Quest.associateNpcCommand. Anything no quest has claimed and that has a
 * pickpocket table is robbed here. Gnome ball keeps the old message, which is
 * still honest -- it genuinely is not implemented.
 */
public class NpcCommand implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player) session.getAttachment();
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
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedNpc, 1) {

            public void arrived() {
                this.owner.resetPath();
                if (this.owner.isBusy() || this.owner.isRanging()
                        || !this.owner.nextTo(affectedNpc)
                        || this.owner.getStatus() != Action.TALKING_MOB) {
                    return;
                }
                this.owner.resetAll();
                if (affectedNpc.isBusy()) {
                    this.owner.getActionSender().sendMessage(
                        affectedNpc.getDef().getName() + " is currently busy.");
                    return;
                }
                affectedNpc.resetPath();
                if (this.owner.getQuestManager().commandClaimed(affectedNpc)) {
                    this.owner.getQuestManager()
                        .triggerEntity(QuestTrigger.NPC_COMMAND, affectedNpc);
                    return;
                }
                NpcPickpocketDef def = EntityHandler.getNpcPickpocketDef(affectedNpc.getID());
                if (def != null) {
                    NpcCommand.pickpocket(this.owner, affectedNpc, def);
                    return;
                }
                this.owner.getActionSender().sendMessage("@or1@Coming soon (hopefully)");
            }
        });
    }

    /**
     * Rob one npc.
     *
     * Classic is unusually blunt about this: there is no stun and no escape
     * roll. Either the hand comes out with something, or the owner of the
     * pocket turns round and attacks, and the only way to keep going is to run
     * away or kill them and wait for the respawn. Nothing here changes that.
     *
     * The npc is not blocked while this runs. Blocking it would stop it
     * fighting back, which is the entire consequence of failing.
     */
    private static void pickpocket(Player player, Npc npc, NpcPickpocketDef def) {
        String name = npc.getDef().getName();
        if (player.getCurStat(THIEVING) < def.getReqLevel()) {
            player.getActionSender().sendMessage(
                "You need to be a level " + def.getReqLevel() + " thief to pick the " + name + "'s pocket");
            return;
        }
        if (player.getInventory().full()) {
            player.getActionSender().sendMessage("You don't have room for that");
            return;
        }
        player.getActionSender().sendMessage("You attempt to pick the " + name + "'s pocket");
        if (Formulae.catchThief(player.getCurStat(THIEVING), def.getReqLevel())) {
            player.getActionSender().sendMessage("You fail to pick the " + name + "'s pocket");
            // The Man has a recorded line for being caught; nobody else does.
            org.rscdaemon.server.npchandler.RandomChat.pickpocketFailLine(npc, player);
            npc.attackPlayer(player);
            return;
        }
        /*
         * One entry, drawn flat. Classic never handed out two rewards from a
         * single pocket, and the weighting between a hero's nine possibilities
         * is not recorded anywhere -- an even draw is this server's choice, not
         * Jagex's.
         */
        ThievingLoot loot = def.getLoot().get(
            DataConversions.random(0, def.getLoot().size() - 1));
        int amount = loot.getAmountLow();
        if (loot.getAmountHigh() > loot.getAmountLow()) {
            amount = DataConversions.random(loot.getAmountLow(), loot.getAmountHigh());
        }
        player.getInventory().add(new InvItem(loot.getID(), amount));
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("You pick the " + name + "'s pocket");
        if (def.getExp() > 0) {
            player.incExp(THIEVING, def.getExp(), true);
            player.getActionSender().sendStat(THIEVING);
        }
    }

    /** Stat slot 17. */
    private static final int THIEVING = 17;
}
