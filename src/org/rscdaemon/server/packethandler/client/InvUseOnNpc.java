/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;

public class InvUseOnNpc
implements PacketHandler {
    public static final World world = World.getWorld();

    /**
     * Thrander's armour modification, as Transcript:Thrander records it: seven
     * metals of plate mail body to top, seven of plate mail legs to plated
     * skirt, and both back again. Every pair is symmetric, so the table is
     * written once and read in either direction.
     *
     * RSCD carried the body/top half plus mithril, adamantite and rune legs,
     * leaving bronze, iron, steel and black legs with nothing to convert to
     * even though all four skirts exist as items (214, 215, 225, 434).
     */
    private static final int[][] THRANDER_PAIRS = {
        {117, 308},  // bronze plate mail body     <-> bronze plate mail top
        {8,   312},  // iron
        {118, 309},  // steel
        {196, 313},  // black
        {119, 310},  // mithril
        {120, 311},  // adamantite
        {401, 407},  // rune
        {206, 214},  // bronze plate mail legs     <-> bronze plated skirt
        {9,   215},  // iron
        {121, 225},  // steel
        {248, 434},  // black
        {122, 226},  // mithril
        {123, 227},  // adamantite
        {402, 406},  // rune plate mail legs       <-> rune skirt
    };

    private static int thranderPairOf(int id) {
        for (int i = 0; i < THRANDER_PAIRS.length; ++i) {
            if (THRANDER_PAIRS[i][0] == id) {
                return THRANDER_PAIRS[i][1];
            }
            if (THRANDER_PAIRS[i][1] == id) {
                return THRANDER_PAIRS[i][0];
            }
        }
        return -1;
    }

    /**
     * "some bronze plate mail legs", "an adamantite plate mail body",
     * "a rune skirt". The transcript uses "some" for legs and a/an by first
     * letter for everything else, and is consistent about it across all
     * twenty-eight lines even where its capitalisation is not.
     */
    private static String thranderName(InvItem item) {
        String name = item.getDef().getName();
        if (name.toLowerCase().indexOf("legs") > -1) {
            return "some " + name;
        }
        return ("aeiou".indexOf(Character.toLowerCase(name.charAt(0))) > -1 ? "an " : "a ") + name;
    }

    /**
     * Returns false when the item is not something Thrander works on, so the
     * caller can give the usual "Nothing interesting happens."
     */
    private static boolean thranderConvert(final Player player, final InvItem item, final Npc thrander) {
        int newID = thranderPairOf(item.getID());
        if (newID < 0) {
            return false;
        }
        final InvItem converted = new InvItem(newID, 1);
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You give Thrander " + thranderName(item));
        player.getActionSender().sendMessage("@pnk@ Thrander hammers it for a bit");
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                // The removal has to succeed before anything is handed back --
                // otherwise a player who dropped the piece between the click
                // and the hammering gets the converted one for free.
                if (this.owner.getInventory().remove(item) > -1) {
                    this.owner.getInventory().add(converted);
                    this.owner.getActionSender().sendInventory();
                    this.owner.getActionSender().sendMessage("@pnk@ Thrander gives you "
                            + thranderName(converted));
                }
                this.owner.setBusy(false);
                thrander.unblock();
            }
        });
        thrander.blockedBy(player);
        return true;
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        final Npc affectedNpc = world.getNpc(p.readShort());
        final InvItem item = player.getInventory().get(p.readShort());
        if (affectedNpc == null || item == null) {
            return;
        }
        player.setFollowing(affectedNpc);
        player.setStatus(Action.USING_INVITEM_ON_NPC);
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedNpc, 1){

            public void arrived() {
                this.owner.resetPath();
                if (!this.owner.getInventory().contains(item) || this.owner.isBusy() || this.owner.isRanging() || !this.owner.nextTo(affectedNpc) || affectedNpc.isBusy() || this.owner.getStatus() != Action.USING_INVITEM_ON_NPC) {
                    return;
                }
                this.owner.resetAll();
                // A quest that owns this npc owns what is used on it, and the
                // hardcoded cases below are skipped entirely. Nothing here is
                // claimed by a quest today, so shearing and Vekk are untouched.
                if (this.owner.getQuestManager().triggerEntity(QuestTrigger.ITEM_ON_NPC, affectedNpc, item)) {
                    return;
                }
                switch (affectedNpc.getID()) {
                    case 2: {
                        if (!this.itemId(new int[]{144})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.setBusy(true);
                        affectedNpc.blockedBy(this.owner);
                        affectedNpc.resetPath();
                        this.showBubble();
                        this.owner.getActionSender().sendMessage("@pnk@ You attempt to shear the sheep");
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (DataConversions.random(0, 4) != 0) {
                                    this.owner.getActionSender().sendMessage("@pnk@ You get some wool");
                                    this.owner.getInventory().add(new InvItem(145, 1));
                                    this.owner.getActionSender().sendInventory();
                                } else {
                                    this.owner.getActionSender().sendMessage("@pnk@ The sheep manages to get away from you!");
                                }
                                this.owner.setBusy(false);
                                affectedNpc.unblock();
                            }
                        });
                        break;
                    }
                    case 6:
                    case 217: {
                        /*
                         * Milking a cow. The sibling of shearing above, and
                         * the only one of the pair that was never built. It
                         * did not block Cook's assistant -- milk is stocked in
                         * two shops, so the quest was always finishable by
                         * buying the ingredient. It just meant the free source
                         * standing in every field did nothing.
                         *
                         * Both cow ids, on Jagex's own examine text: 6 is
                         * "It's a multi purpose cow" and 217 is "It's a dairy
                         * cow". A dairy cow that cannot be milked would be a
                         * strange thing to write that line about, and "multi
                         * purpose" is the other half of the same joke. The
                         * oracles register milking against a single unnumbered
                         * "cow" and so settle nothing here; the cache does.
                         *
                         * No delay and no failure roll -- unlike shearing,
                         * the capture shows the bucket going straight to milk.
                         */
                        if (item.getID() != 21) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.showBubble();
                        this.owner.getInventory().remove(21, 1);
                        this.owner.getInventory().add(new InvItem(22, 1));
                        this.owner.getActionSender().sendInventory();
                        /* @que@, not the @pnk@ the cases around it use: that is
                           the prefix the packet capture of this line shows.
                           The neighbours predate the captures and disagree with
                           them; correcting those is a wider cosmetic sweep than
                           this case, so it is flagged rather than done here. */
                        this.owner.getActionSender().sendMessage("@que@You milk the cow");
                        break;
                    }
                    case 596: {
                        // Shooting at the Gnome Ball goal -- see GnomeBall.
                        if (item.getID() != 981) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        org.rscdaemon.server.npchandler.GnomeBall.shoot(this.owner, affectedNpc);
                        break;
                    }
                    case 733:
                    case 734: {
                        /*
                         * Murphy takes no items. An earlier build had the
                         * trawler's repairs done by using rope or swamp
                         * paste on him, because it had no deck objects to
                         * click; the real deck furniture is in the map
                         * after all, so rope goes on the torn net and
                         * paste goes in the hole. Point the player at it
                         * rather than leaving a dead click.
                         */
                        if (item.getID() == 237) {
                            this.owner.getActionSender().sendMessage("@pnk@ The rope is for the nets, not for Murphy.");
                            return;
                        }
                        if (item.getID() == 785) {
                            this.owner.getActionSender().sendMessage("@pnk@ The swamp paste is for the holes in the deck.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        return;
                    }
                    case 160: {
                        // Thrander, Varrock. Bodies to tops and legs to
                        // skirts, and back again, exactly as Transcript:
                        // Thrander records it. RSCD had the body/top half and
                        // three of the seven leg pairs, and told you "Vekk
                        // hammers the armour" while Thrander was doing it.
                        if (!InvUseOnNpc.thranderConvert(this.owner, item, affectedNpc)) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        }
                        break;
                    }
                    case 778: {
                        // Sidney Smith, Yanille. Handing her the item or the
                        // certificate goes straight to "How many ... do you
                        // want to change?", which the transcript records as
                        // the fast way to use her.
                        if (!org.rscdaemon.server.npchandler.SidneySmith.use(this.owner, affectedNpc, item)) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        }
                        break;
                    }
                    default: {
                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                    }
                }
            }

            private boolean itemId(int[] ids) {
                return DataConversions.inArray(ids, item.getID());
            }

            private void showBubble() {
                Bubble bubble = new Bubble(this.owner, item.getID());
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
            }
        });
    }
}

