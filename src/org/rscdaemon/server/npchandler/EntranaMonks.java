/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.npchandler;

import java.util.ListIterator;

import org.rscdaemon.server.entityhandling.defs.extras.ItemWieldableDef;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.npchandler.NpcHandler;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.Formulae;

public class EntranaMonks
implements NpcHandler {
    public static final World world = World.getWorld();

    /**
     * Two monks, two jobs.
     *
     * 212 stands on the Port Sarim dock, at (266,659) and (262,659), and runs
     * the ferry. 213 stands on Entrana, at (427,548) and (426,546) -- which is
     * either side of Ladder 244 at (426,548), the only ladder with that id
     * anywhere in the world. He is not a ferryman; he is the sign on the door.
     *
     * 213 was spawned and wired to nothing at all, so the warning below existed
     * only as a paragraph on a wiki page.
     *
     * THE RETURN TRIP, AND WHY IT IS AN OBJECT AND NOT A MONK.
     *
     * This file used to record that there was no way back off Entrana by boat,
     * and guessed that Jagex meant the ladder and the wilderness portal to be
     * the only exit. That guess was wrong, and Jagex's own data says so.
     *
     * Every ferry in Classic is a PAIR of ship objects, one at each end, and
     * the description on each says where it goes:
     *
     *   Port Sarim (265,645..652)  155/156/157  "A ship to Karamja"
     *   Karamja    (319..326,710)  161/162/163  "A ship to Port Sarim"
     *   Port Sarim (257..266,661)  238/239/240  "A ship to Entrana"
     *   Entrana    (414..423,571)  241/242/243  "A ship to Port Sarim"
     *
     * So the boat home exists, it is scenery rather than a person, and its
     * destination is shipped text -- not a reading of one. 241/242/243 stand
     * in exactly four places in the whole game and all four are this dock.
     *
     * The reason the two ends are not symmetric is that only one end needs a
     * gatekeeper. Outbound, somebody has to search you, so monk 212 stands on
     * (266,659) and (262,659), one tile from ships 238/239 at y=661, and the
     * ships there keep ObjectAction's "You must talk to the owner about this."
     * Inbound there is nothing to check, so there is nobody to talk to: the
     * Entrana dock has NO npc on it at all. The nearest are the High priest at
     * (406,562) and the Crone at (411,560), both inland, and monk 213 up at the
     * ladder. Karamja is the control that shows the difference is deliberate --
     * its return dock DOES carry an npc, Customs Officer 163 on (325,713) and
     * (331,713), because Karamja checks what you take off the island.
     *
     * That leaves the "Are you ready to go back to the mainland?" half of
     * handleNpc below still unreachable, since it wants a 212 standing inside
     * the Entrana bounds and none is spawned. It is left alone rather than
     * deleted: it costs nothing, and an operator who does place a monk there
     * gets a working ferryman.
     */
    public static final int PORT_SARIM = 212;
    public static final int ENTRANA = 213;

    /**
     * "Player's prayer is drained down by around 93%."
     *
     * That is the transcript's own wording -- a measurement, not a rule -- so
     * the number is as approximate as it looks and this is the one figure on
     * this page that is not certain. Kept as a proportion of what the player
     * has rather than a drain to a fixed value, because a proportion is what
     * was observed.
     */
    private static final int PRAYER_KEPT_PERCENT = 7;
    private static final int PRAYER = 5;

    /**
     * Entrana is a Saradomin outpost and the monks who run the ferry will not
     * carry anything that could be fought with. That is not decoration: Lost
     * City puts a level 95 tree spirit in the dungeon below the island and
     * expects it to be fought with whatever the monks let through, which is why
     * the dungeon's zombies drop a bronze axe.
     *
     * The line the monks draw is combat bonuses. Anything with armour, aim,
     * power or ranged points is refused; magic and prayer bonuses are not, so
     * amulets, capes and robes travel. That covers hatchets too, which have a
     * weapon bonus like any other blade.
     *
     * Three exceptions carry armour points the monks overlook. Ice gloves,
     * because Hero's Quest requires them equipped ON Entrana to take the
     * firebird's feather, so the real game necessarily let them past the
     * search; and plain leather gloves and boots, which the recorded guides
     * list as permitted despite their point or two of armour.
     */
    private static final int ICE_GLOVES = 556;
    private static final int LEATHER_GLOVES = 16, LEATHER_BOOTS = 17;

    private static boolean carryingWeapons(Player player) {
        ListIterator<InvItem> items = player.getInventory().iterator();
        while (items.hasNext()) {
            InvItem item = items.next();
            int id = item.getID();
            if (id == ICE_GLOVES || id == LEATHER_GLOVES || id == LEATHER_BOOTS) {
                continue;
            }
            ItemWieldableDef def = item.getWieldableDef();
            if (def == null) {
                continue;
            }
            if (def.getArmourPoints() > 0 || def.getWeaponAimPoints() > 0
                    || def.getWeaponPowerPoints() > 0 || def.getRangePoints() > 0) {
                return true;
            }
        }
        return false;
    }

    public void handleNpc(final Npc npc, Player player) throws Exception {
        if (npc.getID() == ENTRANA) {
            dungeonWarning(player, npc);
            return;
        }
        final boolean toEntrana = !player.getLocation().inBounds(390, 530, 440, 580);
        player.informOfNpcMessage(new ChatMessage(npc, toEntrana ? "Are you looking to take passage to our holy island?" : "Are you ready to go back to the mainland?", player));
        player.setBusy(true);
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                if (toEntrana) {
                    this.owner.informOfNpcMessage(new ChatMessage(npc, "If so your weapons and armour must be left behind", this.owner));
                }
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        /* "No thanks" was RSCD's. The recorded refusal is
                           longer, and the whole point of these two lines is
                           that the monk has just told you to leave your gear
                           behind, so the refusal answers him. The ORDER --
                           accept first -- is not attested either way; the
                           transcript lists the refusal first, but that is a
                           wiki listing rather than a menu, so it was left
                           alone rather than flipped on a hunch. */
                        String[] options = new String[]{"Yes, Okay I'm ready to go", "No I don't wish to go"};
                        this.owner.setMenuHandler(new MenuHandler(options){

                            public void handleReply(final int option, String reply) {
                                if (this.owner.isBusy()) {
                                    npc.unblock();
                                    return;
                                }
                                this.owner.informOfChatMessage(new ChatMessage(this.owner, reply, npc));
                                this.owner.setBusy(true);
                                DelayedEvent.world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        if (option != 0) {
                                            this.owner.setBusy(false);
                                            npc.unblock();
                                            return;
                                        }
                                        /* The search happens whether or not it
                                           finds anything, and the player is told
                                           so. It was missing entirely, which made
                                           the refusal below arrive out of nowhere
                                           and the clean pass look like no check
                                           had been made at all. */
                                        if (toEntrana) {
                                            this.owner.getActionSender().sendMessage("The monk quickly searches you");
                                        }
                                        if (toEntrana && EntranaMonks.carryingWeapons(this.owner)) {
                                            this.owner.informOfNpcMessage(new ChatMessage(npc, "Sorry we cannow allow you on to our island", this.owner));
                                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                                public void action() {
                                                    this.owner.informOfNpcMessage(new ChatMessage(npc, "Make sure you are not carrying weapons or armour please", this.owner));
                                                    this.owner.setBusy(false);
                                                    npc.unblock();
                                                }
                                            });
                                            return;
                                        }
                                        this.owner.getActionSender().sendMessage("You board the ship");
                                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                            public void action() {
                                                if (toEntrana) {
                                                    this.owner.teleport(418, 570, false);
                                                } else {
                                                    this.owner.teleport(263, 659, false);
                                                }
                                                this.owner.getActionSender().sendMessage("The ship arrives at " + (toEntrana ? "Entrana" : "Port Sarim"));
                                                this.owner.setBusy(false);
                                                npc.unblock();
                                            }
                                        });
                                    }
                                });
                            }
                        });
                        this.owner.getActionSender().sendMenu(options);
                    }
                });
            }
        });
        npc.blockedBy(player);
    }

    /**
     * Boarding one of the ships on the Entrana dock -- 241, 242 or 243, the
     * ones Jagex described as "A ship to Port Sarim".
     *
     * There is no conversation here because there is nobody to have one with,
     * and none is invented: the command on the object is "board", which is an
     * instruction and not a question. The two messages and the pacing are the
     * outbound trip's own, so a round trip reads the same in both directions.
     *
     * The landing tile is the one the unreachable monk branch above already
     * chose, (263,659), which sits on the Port Sarim dock walkway between the
     * two monks. Reused rather than picked afresh -- it is the better-founded
     * of the two guesses available.
     *
     * The bounds test is a safety net, not the id test. Those three ids exist
     * only on this dock today, but a ship is an obvious thing for an operator
     * to decorate a port with, and a decorative ship must not be a free
     * teleport off wherever it stands.
     */
    public static void boardShipHome(Player player) {
        if (!player.getLocation().inBounds(390, 530, 440, 580)) {
            player.getActionSender().sendMessage("@pnk@ You must talk to the owner about this.");
            return;
        }
        player.setBusy(true);
        player.getActionSender().sendMessage("You board the ship");
        world.getDelayedEventHandler().add(new ShortEvent(player){

            public void action() {
                this.owner.teleport(263, 659, false);
                this.owner.getActionSender().sendMessage("The ship arrives at Port Sarim");
                this.owner.setBusy(false);
            }
        });
    }

    /**
     * Ladder 244 on (426,548), the way down into the Entrana dungeon.
     *
     * The transcript ties the monk's warning to talking to him OR to using the
     * ladder, and only the first was built, so the ladder answered generically:
     * you went down with no warning and, more to the point, with your prayer
     * intact. The drain belongs to the place -- "the evilness seems to block
     * off our contact with our gods" is what the monk is warning you ABOUT --
     * so it has to happen however you arrive, not only when you were polite
     * enough to ask first.
     *
     * The monk is looked up rather than required. If he is missing the descent
     * still happens and still drains, because a missing npc is not a reason for
     * the dungeon to stop being the dungeon; only the words are lost.
     *
     * The warning fires every time. Suppressing repeats would need a persisted
     * flag and nothing attests one, and of the two possible errors -- warning a
     * player twice, or dropping a level 95 tree spirit on them silently -- the
     * repeat is the harmless one.
     */
    public static void climbDungeonLadder(Player player) {
        Npc monk = world.getNpc(ENTRANA, 426, 428, 546, 549);
        if (monk == null) {
            descend(player);
            return;
        }
        dungeonWarning(player, monk);
    }

    /**
     * The monk at the ladder. Nine lines and a choice, and the choice is real:
     * the dungeon below holds a level 95 tree spirit, the ladder does not go
     * back up, and the only exit is a portal into the wilderness.
     */
    public static void dungeonWarning(Player player, Npc monk) {
        new Conversation(player, monk)
            .npc("Be careful going in there")
            .npc("You are unarmed, and there is much evilness lurking down there")
            .npc("The evilness seems to block off our contact with our gods")
            .npc("our prayers seem to have less effect down there")
            .npc("Oh also you won't be able to come back this way")
            .npc("This ladder only goes one way")
            .npc("The only way out is a portal which leads deep into the wilderness")
            .options(new Choice("I don't think I'm strong enough to enter then",
                                "Well that is a risk I will have to take") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        return;
                    }
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            descend(c.getPlayer());
                        }
                    });
                }
            })
            .start();
    }

    /** Down the ladder, and the gods stop listening on the way. */
    private static void descend(Player player) {
        int prayer = player.getCurStat(PRAYER);
        int left = prayer * PRAYER_KEPT_PERCENT / 100;
        if (left != prayer) {
            player.setCurStat(PRAYER, left);
            player.getActionSender().sendStat(PRAYER);
        }
        player.teleport(player.getX(), Formulae.getNewY(player.getY(), false), false);
    }
}
