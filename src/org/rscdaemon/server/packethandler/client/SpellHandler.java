/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.SpellDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmeltingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ReqOreDef;
import org.rscdaemon.server.event.ObjectRemover;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.event.WalkToPointEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.Formulae;
import org.rscdaemon.server.util.Smelting;

public class SpellHandler
implements PacketHandler {
    public static final World world = World.getWorld();
    private Random r = new Random();
    private static TreeMap<Integer, InvItem[]> staffs = new TreeMap();

    /** Family crest, the wizard's reward. */
    private static final int CHAOS_GAUNTLETS = 701;

    /** Wind, Water, Earth and Fire bolt -- the four that take a chaos rune. */
    private static final int[] BOLT_SPELLS = {8, 11, 14, 17};

    /** The three Mage Arena staves. Any one of them lets Charge be cast. */
    private static final int STAFF_ZAMORAK = 1216;
    private static final int STAFF_GUTHIX = 1217;
    private static final int STAFF_SARADOMIN = 1218;

    /** Spell id of Telekinetic grab, the only ground-item spell in the game. */
    private static final int TELEKINETIC_GRAB = 16;

    private static final String TELEGRAB_WARDED =
            "@gry@A powerful force has prevented you from casting that spell here";

    /**
     * Whether the party hall's ward refuses this grab.
     *
     * Either end of the spell is enough to stop it: the caster standing inside,
     * or the item lying inside. Checking only the caster would leave the
     * obvious hole -- stand in the street, reach over the wall, and clear the
     * floor of a drop party from outside the room the ward was put there to
     * protect. Checking only the item would leave the mirror of it, a host
     * inside the hall grabbing back everything they had just dropped.
     *
     * @see org.rscdaemon.server.model.Point#inPartyHall()
     * @see org.rscdaemon.server.npchandler.PartyHall
     */
    private static boolean wardedFromTelegrab(Player caster, Item item) {
        return caster.getLocation().inPartyHall() || item.getLocation().inPartyHall();
    }

    private static boolean isBoltSpell(int spellID) {
        for (int bolt : BOLT_SPELLS) {
            if (bolt == spellID) {
                return true;
            }
        }
        return false;
    }

    public final int Rand(int low, int high) {
        return low + this.r.nextInt(high - low);
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy() && !player.inCombat() || player.isRanging()) {
            return;
        }
        if (player.isDueling() && player.getDuelSetting(1)) {
            player.getActionSender().sendMessage("@gry@ Magic is disabled in this duel");
            return;
        }
        /*
         * A pending option menu means a Conversation is waiting on an answer.
         * The player is deliberately not-busy in that window (the client will
         * not answer a busy player -- Conversation.Options), so the busy check
         * above lets a cast through, and resetAllExceptDueling below would
         * resetMenuHandler -- silently killing the dialogue mid-question.
         * Demon Slayer's incantation menu was the reported casualty: autocast
         * interrupted it and left the quest's vortex state stranded.
         */
        if (player.getMenuHandler() != null) {
            return;
        }
        player.resetAllExceptDueling();
        short idx = p.readShort();
        /*
         * Bound against the spell table itself, not a literal. This read
         * "idx >= 44", a stale count from a shorter spell list, and the five
         * spells at or above it -- 44 Earth wave, 45 Enfeeble, 46 Fire wave,
         * 47 Stun and 48 Charge -- were dropped here before any of them
         * reached a handler. Silently: no message, no rune cost, nothing in
         * the log. Water wave (40) and Wind wave (38) sat below the line and
         * worked, which is exactly the shape the bug was reported in.
         *
         * Worse than the no-op, it called setSuspiciousPlayer on the way out,
         * so casting Stun flagged an honest player as a cheat. getSpellDef
         * already range-checks and returns null, so asking it is both the
         * bounds check and the null guard the code below needs.
         */
        SpellDef spell = EntityHandler.getSpellDef(idx);
        if (spell == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        if (!SpellHandler.canCast(player)) {
            return;
        }
        if (player.isBlackOut()) {
            player.getActionSender().sendMessage("@pnk@ You try to cast the spell but are unable to! You are suffering a blackout.");
            player.resetPath();
            return;
        }
        if (player.getCurStat(6) < spell.getReqLevel()) {
            player.setSuspiciousPlayer(true);
            player.getActionSender().sendMessage("@gry@ Your magic ability is not high enough for this spell.");
            player.resetPath();
            return;
        }
        if (!Formulae.castSpell(spell,
                (int)(Formulae.magicPrayerBoost(player) * (double)player.getCurStat(6)),
                player.getMagicPoints())) {
            player.getActionSender().sendMessage("@pnk@ The spell fails, you may try again in 20 seconds.");
            player.getActionSender().sendSound("spellfail");
            player.setSpellFail();
            player.resetPath();
            return;
        }
        switch (pID) {
            case 206: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                if (spell.getSpellType() == 0) {
                    this.handleTeleport(player, spell, idx);
                }
                if (spell.getSpellType() != 7) break;
                if (player.inCombat() || player.isDueling()) {
                    player.resetPath();
                    player.getActionSender().sendMessage("@gry@ Spell disabled.");
                    return;
                }
                this.handleHeal(player, idx);
                break;
            }
            case 55: {
                Player affectedPlayer;
                if (spell.getSpellType() == 1 || spell.getSpellType() == 2) {
                    affectedPlayer = world.getPlayer(p.readShort());
                    if (affectedPlayer == null) {
                        player.resetPath();
                        return;
                    }
                    if (affectedPlayer.isMod() && affectedPlayer.getLocation().inWilderness()) {
                        player.resetPath();
                        player.getActionSender().sendMessage("@gry@ Spell disabled.");
                        return;
                    }
                    if (affectedPlayer.isAdmin() && affectedPlayer.getLocation().inWilderness()) {
                        player.resetPath();
                        player.getActionSender().sendMessage("@gry@ Spell disabled.");
                        return;
                    }
                    if (affectedPlayer.getX() < 86 && affectedPlayer.getX() > 61 && affectedPlayer.getY() == 129) {
                        player.resetPath();
                        player.getActionSender().sendMessage("@gry@ Spell disabled.");
                        return;
                    }
                    if (player.withinRange(affectedPlayer, 5)) {
                        player.resetPath();
                    }
                    this.handleMobCast(player, affectedPlayer, idx);
                }
                if (spell.getSpellType() == 8) {
                    affectedPlayer = world.getPlayer(p.readShort());
                    if (affectedPlayer.isDueling()) {
                        player.resetPath();
                        player.getActionSender().sendMessage("@gry@ Spell disabled.");
                        return;
                    }
                    if (affectedPlayer.inCombat() || affectedPlayer.isDueling()) {
                        player.resetPath();
                        player.getActionSender().sendMessage("@gry@ Spell disabled.");
                        return;
                    }
                    this.handleHealOther(player, affectedPlayer, idx);
                }
                if (spell.getSpellType() != 9) break;
                affectedPlayer = world.getPlayer(p.readShort());
                if (affectedPlayer.isMod() && affectedPlayer.getLocation().inWilderness()) {
                    player.resetPath();
                    player.getActionSender().sendMessage("@gry@ Spell disabled.");
                    return;
                }
                if (affectedPlayer.isAdmin() && affectedPlayer.getLocation().inWilderness()) {
                    player.resetPath();
                    player.getActionSender().sendMessage("@gry@ Spell disabled.");
                    return;
                }
                if (affectedPlayer.isDueling()) {
                    player.resetPath();
                    player.getActionSender().sendMessage("@gry@ Spell disabled.");
                    return;
                }
                if (affectedPlayer.getX() < 86 && affectedPlayer.getX() > 61 && affectedPlayer.getY() == 129) {
                    player.resetPath();
                    player.getActionSender().sendMessage("@gry@ Spell disabled.");
                    return;
                }
                this.handleBlackOut(player, affectedPlayer, idx);
                break;
            }
            case 71: {
                if (spell.getSpellType() != 2) break;
                Npc affectedNpc = world.getNpc(p.readShort());
                if (affectedNpc == null) {
                    player.resetPath();
                    return;
                }
                if (player.withinRange(affectedNpc, 5)) {
                    player.resetPath();
                }
                this.handleMobCast(player, affectedNpc, idx);
                break;
            }
            case 49: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                if (spell.getSpellType() != 3) break;
                InvItem item = player.getInventory().get(p.readShort());
                if (item == null) {
                    player.resetPath();
                    return;
                }
                this.handleInvItemCast(player, spell, idx, item);
                break;
            }
            case 67: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                player.getActionSender().sendMessage("@gry@ This type of spell is not yet implemented.");
                break;
            }
            /*
             * Cast at scenery -- the client's menu 400, and the only home the
             * four Charge Orb spells have ever had. Nothing in v25 read this
             * packet, so this is not an override of anything: the case sat here
             * printing "not yet implemented" from the day it was written.
             *
             * The runes are taken only if some quest claimed the object, which
             * is why the trigger is asked first. A charge cast at a rock is a
             * misclick and costs nothing; a charge cast at the Dark Metal Gate
             * is the answer to it and costs the orb, because the orb is one of
             * the spell's runes.
             */
            case 17: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                if (spell.getSpellType() != 5) break;
                GameObject affectedObject = world.getTile(p.readShort(), p.readShort()).getGameObject();
                if (affectedObject == null) {
                    player.resetPath();
                    return;
                }
                /*
                 * The four elemental obelisks are the only scenery outside a
                 * quest that answers this packet. Each Charge Orb spell only
                 * works at its own obelisk -- the unpowered orb is one of the
                 * spell's runes, so a matching cast consumes it along with the
                 * elemental and cosmic runes and hands back the powered orb.
                 * At the wrong obelisk nothing happens and nothing is spent.
                 */
                if (SpellHandler.chargeOrb(player, spell, idx, affectedObject)) {
                    player.resetPath();
                    break;
                }
                if (!player.getQuestManager().associatedWithQuest(affectedObject)) {
                    player.getActionSender().sendMessage("@gry@ Nothing interesting happens.");
                    player.resetPath();
                    break;
                }
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) break;
                player.incExp(6, spell.getExp(), true);
                player.getActionSender().sendStat(6);
                player.getQuestManager().triggerSpell(affectedObject, idx);
                break;
            }
            case 104: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                ActiveTile t = world.getTile(p.readShort(), p.readShort());
                short itemId = p.readShort();
                Item affectedItem = null;
                for (Item i : t.getItems()) {
                    if (i.getID() != itemId) continue;
                    affectedItem = i;
                    break;
                }
                if (affectedItem == null) {
                    return;
                }
                /*
                 * The party hall ward, asked here as well as in handleItemCast
                 * so that a refused grab is refused where the player is
                 * standing rather than after a five-tile walk. It has to be
                 * asked in both places and for two different reasons: here,
                 * because this is where the caster is still stood on the tile
                 * they cast from, and the rule is about where they were, not
                 * where the walk left them; there, because the item can be
                 * carried into the hall while they are on their way to it.
                 */
                if (idx == TELEKINETIC_GRAB && wardedFromTelegrab(player, affectedItem)) {
                    player.getActionSender().sendMessage(TELEGRAB_WARDED);
                    return;
                }
                this.handleItemCast(player, spell, idx, affectedItem);
                break;
            }
            case 232: {
                if (player.isDueling()) {
                    player.getActionSender().sendMessage("@gry@ This type of spell cannot be used in a duel.");
                    return;
                }
                /*
                 * Bones to bananas and Charge are both type 6 in SpellDef,
                 * not 7 -- this gate checked the wrong type, so both spells
                 * were unreachable (silently no-op, no rune cost, no
                 * message) despite handleGroundCast having real logic for
                 * both.
                 */
                if (spell.getSpellType() != 6) break;
                this.handleGroundCast(player, spell, idx);
            }
        }
        player.getActionSender().sendInventory();
        player.getActionSender().sendStat(6);
    }

    /*
     * handleHealOther, handleBlackOut and handleHeal below are Ignis Isle's own
     * spells -- Minor/Major Healing Aura, Minor Heal Other, Heal Other, Blackout.
     * They are kept, but they are dormant: each is reached only through a
     * getSpellType() == 8, == 9 or == 7 gate, and the restored v25 spell table has
     * no spell of any of those types. Nothing can currently trigger them.
     *
     * They were never reachable in practice anyway. Casting one meant the client
     * sending an id from its own table, which disagreed with the server's at 46
     * of 49 positions, so the id that arrived was some unrelated combat spell.
     *
     * The spell ids in their switches are still Ignis Isle's. When the god-favour
     * tier comes back it should append its spells after id 48 rather than
     * renumbering the vanilla ones, and these switches get the new ids then.
     */
    private void handleHealOther(Player player, Player affectedPlayer, final int spellID) {
        SpellDef spell = EntityHandler.getSpellDef(spellID);
        if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
            return;
        }
        if (!player.isBusy()) {
            player.setFollowing(affectedPlayer);
        }
        player.setStatus(Action.CASTING_MOB);
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedPlayer, 5){

            public void arrived() {
                SpellDef spell = EntityHandler.getSpellDef(spellID);
                Player affectedPlayer = (Player)this.affectedMob;
                this.owner.resetPath();
                if (!SpellHandler.canCast(this.owner) || !this.owner.canReach(affectedPlayer, 5) || this.owner.getStatus() != Action.CASTING_MOB) {
                    return;
                }
                this.owner.resetAllExceptDueling();
                Projectile projectil = new Projectile(this.owner, affectedPlayer, 1);
                ArrayList<Player> playersToInfor = new ArrayList<Player>();
                playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                playersToInfor.addAll(affectedPlayer.getViewArea().getPlayersInView());
                for (Player p : playersToInfor) {
                    p.informOfProjectile(projectil);
                }
                int curhp = affectedPlayer.getCurStat(3);
                int maxhp = affectedPlayer.getMaxStat(3);
                SpellHandler.this.finalizeSpell(this.owner, spell);
                switch (spellID) {
                    case 28: {
                        if (curhp == maxhp) {
                            this.owner.getActionSender().sendMessage("@pnk@ You tried to heal " + affectedPlayer.getUsername() + ", but they were max hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " tried to heal you, but you were max hits.");
                        } else if (maxhp - curhp <= 10) {
                            affectedPlayer.setCurStat(3, curhp + (maxhp - curhp));
                            this.owner.getActionSender().sendMessage("@pnk@ You heal " + affectedPlayer.getUsername() + " for " + (maxhp - curhp) + " hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " healed you for " + (maxhp - curhp) + " hits.");
                        } else if (maxhp - curhp > 10) {
                            affectedPlayer.setCurStat(3, curhp + 10);
                            this.owner.getActionSender().sendMessage("@pnk@ You heal " + affectedPlayer.getUsername() + " for " + 10 + " hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " healed you for " + 10 + " hits.");
                        }
                        affectedPlayer.getActionSender().sendStat(3);
                        return;
                    }
                    case 41: {
                        if (curhp == maxhp) {
                            this.owner.getActionSender().sendMessage("@pnk@ You tried to heal " + affectedPlayer.getUsername() + ", but they were max hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " tried to heal you, but you were max hits.");
                        } else if (maxhp - curhp <= 20) {
                            affectedPlayer.setCurStat(3, curhp + (maxhp - curhp));
                            this.owner.getActionSender().sendMessage("@pnk@ You heal " + affectedPlayer.getUsername() + " for " + (maxhp - curhp) + " hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " healed you for " + (maxhp - curhp) + " hits.");
                        } else if (maxhp - curhp > 20) {
                            affectedPlayer.setCurStat(3, curhp + 20);
                            this.owner.getActionSender().sendMessage("@pnk@ You heal " + affectedPlayer.getUsername() + " for " + 20 + " hits.");
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " healed you for " + 20 + " hits.");
                        }
                        affectedPlayer.getActionSender().sendStat(3);
                        return;
                    }
                }
            }
        });
    }

    private void handleBlackOut(Player player, Player affectedPlayer, final int spellID) {
        SpellDef spell = EntityHandler.getSpellDef(spellID);
        if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
            return;
        }
        if (!player.isBusy()) {
            player.setFollowing(affectedPlayer);
        }
        player.setStatus(Action.CASTING_MOB);
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedPlayer, 5){

            public void arrived() {
                SpellDef spell = EntityHandler.getSpellDef(spellID);
                Player affectedPlayer = (Player)this.affectedMob;
                this.owner.resetPath();
                if (!SpellHandler.canCast(this.owner) || !this.owner.canReach(affectedPlayer, 5) || this.owner.getStatus() != Action.CASTING_MOB) {
                    return;
                }
                this.owner.resetAllExceptDueling();
                Projectile projectil = new Projectile(this.owner, affectedPlayer, 1);
                ArrayList<Player> playersToInfor = new ArrayList<Player>();
                playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                playersToInfor.addAll(affectedPlayer.getViewArea().getPlayersInView());
                for (Player p : playersToInfor) {
                    p.informOfProjectile(projectil);
                }
                affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " has cast blackout on you!");
                affectedPlayer.setBlackOut();
                SpellHandler.this.finalizeSpell(this.owner, spell);
            }
        });
    }

    private void handleHeal(Player player, int spellID) {
        player.setStatus(Action.CASTING_MOB);
        SpellDef spell = EntityHandler.getSpellDef(spellID);
        if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
            return;
        }
        player.resetPath();
        if (!SpellHandler.canCast(player) || player.getStatus() != Action.CASTING_MOB) {
            return;
        }
        player.resetAllExceptDueling();
        int curhp = player.getCurStat(3);
        int maxhp = player.getMaxStat(3);
        this.finalizeSpell(player, spell);
        switch (spellID) {
            case 7: {
                if (curhp == maxhp) {
                    player.getActionSender().sendMessage("@pnk@ You tried to heal yourself, but were already max hits.");
                } else if (maxhp - curhp <= 5) {
                    player.setCurStat(3, curhp + (maxhp - curhp));
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for " + (maxhp - curhp) + " hits.");
                } else if (maxhp - curhp > 5) {
                    player.setCurStat(3, curhp + 5);
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for 5 hits.");
                }
                player.getActionSender().sendStat(3);
                return;
            }
            case 17: {
                if (curhp == maxhp) {
                    player.getActionSender().sendMessage("@pnk@ You tried to heal yourself, but were already max hits.");
                } else if (maxhp - curhp <= 15) {
                    player.setCurStat(3, curhp + (maxhp - curhp));
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for " + (maxhp - curhp) + " hits.");
                } else if (maxhp - curhp > 15) {
                    player.setCurStat(3, curhp + 15);
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for 15 hits.");
                }
                player.getActionSender().sendStat(3);
                return;
            }
            case 38: {
                if (curhp == maxhp) {
                    player.getActionSender().sendMessage("@pnk@ You tried to heal yourself, but were already max hits.");
                } else if (maxhp - curhp <= 30) {
                    player.setCurStat(3, curhp + (maxhp - curhp));
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for " + (maxhp - curhp) + " hits.");
                } else if (maxhp - curhp > 30) {
                    player.setCurStat(3, curhp + 30);
                    player.getActionSender().sendMessage("@pnk@ You heal yourself for 30 hits.");
                }
                player.getActionSender().sendStat(3);
                return;
            }
        }
    }

    private void handleMobCast(Player player, Mob affectedMob, final int spellID) {
        if (!player.isBusy()) {
            player.setFollowing(affectedMob);
        }
        player.setStatus(Action.CASTING_MOB);
        world.getDelayedEventHandler().add(new WalkToMobEvent(player, affectedMob, 5){

            public void arrived() {
                this.owner.resetPath();
                SpellDef spell = EntityHandler.getSpellDef(spellID);
                if (!SpellHandler.canCast(this.owner) || this.affectedMob.getHits() <= 0 || !this.owner.canReach(this.affectedMob, 5) || !this.owner.checkAttack(this.affectedMob, true) || this.owner.getStatus() != Action.CASTING_MOB) {
                    return;
                }
                this.owner.resetAllExceptDueling();
                /*
                 * Elvarg breathes before every spell that lands on her --
                 * the one part of that fight with direct packet evidence
                 * behind it, from two Dragon Slayer captures. See
                 * Formulae.elvargSpellBreath. It fires here, past the
                 * can-cast and in-range checks, so it answers a real cast
                 * and not a misclick, and it can kill: hence the death
                 * check, matching how Npc.attack handles a lethal engage
                 * breath.
                 */
                if (this.affectedMob instanceof Npc && ((Npc) this.affectedMob).getID() == Formulae.ELVARG) {
                    Formulae.elvargSpellBreath(this.owner);
                    if (this.owner.getHits() <= 0) {
                        this.owner.killedBy((Npc) this.affectedMob, false);
                        return;
                    }
                }
                switch (spellID) {
                    /* The stat-draining spells. RSCD v25 never implemented them
                       and said so rather than casting something else; Ignis Isle
                       dropped them from its spell list entirely, so the guard
                       went with them. Without it these fall to the default
                       branch below and become ordinary damage spells -- wrong
                       behaviour, and it eats the runes to do it.

                       v25's own case labels were 41/44/46 here, which are Charge
                       air Orb, Earth wave and Fire wave in the table it shipped:
                       it was written against the 48-spell list from before Lost
                       City teleport was inserted at 37 and never re-checked. The
                       ids below are read off the table. */
                    case 1:    // Confuse
                    case 5:    // Weaken
                    case 9:    // Curse
                    case 42:   // Vulnerability
                    case 45:   // Enfeeble
                    case 47: {  // Stun
                        SpellHandler.this.curseSpell(this.owner, this.affectedMob, spellID, spell);
                        break;
                    }
                    case 25: {  // Iban blast
                        boolean flagispro = false;
                        ListIterator<InvItem> iterator22 = this.owner.getInventory().iterator();
                        int slot = 0;
                        while (iterator22.hasNext()) {
                            InvItem cape = iterator22.next();
                            if (cape.getID() == 1000 && cape.isWielded()) {
                                if (!flagispro) {
                                    // empty if block
                                }
                                flagispro = true;
                            }
                            ++slot;
                        }
                        if (flagispro) {
                            // The staff holds 25 casts and is refilled at the
                            // Flames of zamorak; the count lives with the
                            // Underground Pass record. Both messages are the
                            // wiki's, verbatim.
                            if (!this.owner.getQuestManager().reached(
                                    org.rscdaemon.server.quest.Quests.UNDERGROUND_PASS, "staff-charged")) {
                                this.owner.getActionSender().sendMessage("you need to recharge the staff of iban");
                                this.owner.getActionSender().sendMessage("at iban's temple");
                                return;
                            }
                            if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                                return;
                            }
                            this.owner.getQuestManager().note(
                                org.rscdaemon.server.quest.Quests.UNDERGROUND_PASS, "staff-cast");
                            if (this.affectedMob instanceof Player && !this.owner.isDueling()) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                this.owner.setSkulledOn(affectedPlayer);
                            }
                            int damag = Formulae.calcSpellHit(20, this.owner.getMagicPoints());
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
                            }
                            Projectile projectil = new Projectile(this.owner, this.affectedMob, 1);
                            this.affectedMob.setLastDamage(damag);
                            int newhp = this.affectedMob.getHits() - damag;
                            this.affectedMob.setHits(newhp);
                            org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damag);
                            ArrayList<Player> playersToInfor = new ArrayList<Player>();
                            playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                            playersToInfor.addAll(this.affectedMob.getViewArea().getPlayersInView());
                            for (Player p : playersToInfor) {
                                p.informOfProjectile(projectil);
                                p.informOfModifiedHits(this.affectedMob);
                            }
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendStat(3);
                            }
                            if (newhp <= 0) {
                                this.affectedMob.killedBy(this.owner, this.owner.isDueling());
                            } else if (this.affectedMob instanceof Npc) {
                                ((Npc) this.affectedMob).retaliate(this.owner);
                            }
                            this.owner.getActionSender().sendInventory();
                            this.owner.getActionSender().sendStat(6);
                            SpellHandler.this.finalizeSpell(this.owner, spell);
                            break;
                        }
                        this.owner.getActionSender().sendMessage("you need the staff of iban to cast this spell");
                        return;
                    }
                    case 33: {  // Claws of Guthix
                        boolean flag = false;
                        int max = this.owner.isCharged() ? SpellHandler.this.Rand(0, 25) : SpellHandler.this.Rand(0, 10);
                        ListIterator<InvItem> iterator = this.owner.getInventory().iterator();
                        int slot = 0;
                        while (iterator.hasNext()) {
                            InvItem cape = iterator.next();
                            if (cape.getID() == 1217 && cape.isWielded()) {
                                if (!flag) {
                                    // empty if block
                                }
                                flag = true;
                            }
                            ++slot;
                        }
                        if (flag) {
                            if (!SpellHandler.this.checkGodSpellGate(this.owner, "guthix")) {
                                return;
                            }
                            if (!this.owner.isCharged()) {
                                this.owner.getActionSender().sendMessage("@gry@ You are not charged!");
                            }
                            if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                                return;
                            }
                            if (this.affectedMob instanceof Player && !this.owner.isDueling()) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                this.owner.setSkulledOn(affectedPlayer);
                            }
                            int damag = Formulae.calcGodSpells(this.owner, this.affectedMob);
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
                            }
                            Projectile projectil = new Projectile(this.owner, this.affectedMob, 1);
                            SpellHandler.this.godSpellObject(this.affectedMob, 33);
                            this.affectedMob.setLastDamage(damag);
                            int newhp = this.affectedMob.getHits() - damag;
                            this.affectedMob.setHits(newhp);
                            org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damag);
                            ArrayList<Player> playersToInfor = new ArrayList<Player>();
                            playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                            playersToInfor.addAll(this.affectedMob.getViewArea().getPlayersInView());
                            for (Player p : playersToInfor) {
                                p.informOfProjectile(projectil);
                                p.informOfModifiedHits(this.affectedMob);
                            }
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendStat(3);
                            }
                            if (newhp <= 0) {
                                this.affectedMob.killedBy(this.owner, this.owner.isDueling());
                            } else {
                                SpellHandler.this.drainPercent(this.affectedMob, 1, 5,
                                        "@pnk@ Your Defense has been drained by the power of Guthix!");
                                if (this.affectedMob instanceof Npc) {
                                    ((Npc) this.affectedMob).retaliate(this.owner);
                                }
                            }
                            this.owner.getActionSender().sendInventory();
                            this.owner.getActionSender().sendStat(6);
                            SpellHandler.this.finalizeSpell(this.owner, spell);
                            break;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ You need to be wearing the Staff of Guthix to cast this spell!");
                        return;
                    }
                    case 34: {  // Saradomin strike
                        boolean bool = false;
                        ListIterator<InvItem> iterat = this.owner.getInventory().iterator();
                        int slot = 0;
                        while (iterat.hasNext()) {
                            InvItem cape = iterat.next();
                            if (cape.getID() == 1218 && cape.isWielded()) {
                                if (!bool) {
                                    // empty if block
                                }
                                bool = true;
                            }
                            ++slot;
                        }
                        if (bool) {
                            if (!SpellHandler.this.checkGodSpellGate(this.owner, "saradomin")) {
                                return;
                            }
                            if (!this.owner.isCharged()) {
                                this.owner.getActionSender().sendMessage("@gry@ You are not charged!");
                            }
                            if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                                return;
                            }
                            if (this.affectedMob instanceof Player && !this.owner.isDueling()) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                this.owner.setSkulledOn(affectedPlayer);
                            }
                            int damag = Formulae.calcGodSpells(this.owner, this.affectedMob);
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
                            }
                            Projectile projectil = new Projectile(this.owner, this.affectedMob, 1);
                            SpellHandler.this.godSpellObject(this.affectedMob, 34);
                            this.affectedMob.setLastDamage(damag);
                            int newhp = this.affectedMob.getHits() - damag;
                            this.affectedMob.setHits(newhp);
                            org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damag);
                            ArrayList<Player> playersToInfor = new ArrayList<Player>();
                            playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                            playersToInfor.addAll(this.affectedMob.getViewArea().getPlayersInView());
                            for (Player p : playersToInfor) {
                                p.informOfProjectile(projectil);
                                p.informOfModifiedHits(this.affectedMob);
                            }
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendStat(3);
                            }
                            if (newhp <= 0) {
                                this.affectedMob.killedBy(this.owner, this.owner.isDueling());
                            } else {
                                SpellHandler.this.drainPrayerFlat(this.affectedMob);
                                if (this.affectedMob instanceof Npc) {
                                    ((Npc) this.affectedMob).retaliate(this.owner);
                                }
                            }
                            this.owner.getActionSender().sendInventory();
                            this.owner.getActionSender().sendStat(6);
                            SpellHandler.this.finalizeSpell(this.owner, spell);
                            break;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ You need to be wearing the Staff of Saradomin to cast this spell!");
                        return;
                    }
                    case 35: {  // Flames of Zamorak
                        boolean flag2 = false;
                        ListIterator<InvItem> iterato = this.owner.getInventory().iterator();
                        int slot = 0;
                        while (iterato.hasNext()) {
                            InvItem cape = iterato.next();
                            if (cape.getID() == 1216 && cape.isWielded()) {
                                if (!flag2) {
                                    // empty if block
                                }
                                flag2 = true;
                            }
                            ++slot;
                        }
                        if (flag2) {
                            if (!SpellHandler.this.checkGodSpellGate(this.owner, "zamorak")) {
                                return;
                            }
                            if (!this.owner.isCharged()) {
                                this.owner.getActionSender().sendMessage("@gry@ You are not charged!");
                            }
                            if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                                return;
                            }
                            if (this.affectedMob instanceof Player && !this.owner.isDueling()) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                this.owner.setSkulledOn(affectedPlayer);
                            }
                            int damag = Formulae.calcGodSpells(this.owner, this.affectedMob);
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
                            }
                            Projectile projectil = new Projectile(this.owner, this.affectedMob, 1);
                            SpellHandler.this.godSpellObject(this.affectedMob, 35);
                            this.affectedMob.setLastDamage(damag);
                            int newhp = this.affectedMob.getHits() - damag;
                            this.affectedMob.setHits(newhp);
                            org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damag);
                            ArrayList<Player> playersToInfor = new ArrayList<Player>();
                            playersToInfor.addAll(this.owner.getViewArea().getPlayersInView());
                            playersToInfor.addAll(this.affectedMob.getViewArea().getPlayersInView());
                            for (Player p : playersToInfor) {
                                p.informOfProjectile(projectil);
                                p.informOfModifiedHits(this.affectedMob);
                            }
                            if (this.affectedMob instanceof Player) {
                                Player affectedPlayer = (Player)this.affectedMob;
                                affectedPlayer.getActionSender().sendStat(3);
                            }
                            if (newhp <= 0) {
                                this.affectedMob.killedBy(this.owner, this.owner.isDueling());
                            } else {
                                SpellHandler.this.drainPercent(this.affectedMob, 6, 5,
                                        "@pnk@ Your Magic has been drained by the power of Zamorak!");
                                if (this.affectedMob instanceof Npc) {
                                    ((Npc) this.affectedMob).retaliate(this.owner);
                                }
                            }
                            this.owner.getActionSender().sendInventory();
                            this.owner.getActionSender().sendStat(6);
                            SpellHandler.this.finalizeSpell(this.owner, spell);
                            break;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ You need to be wearing the Staff of Zamorak to cast this spell");
                        return;
                    }
                    default: {
                        if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                            return;
                        }
                        if (this.affectedMob instanceof Player && !this.owner.isDueling()) {
                            Player affectedPlayer = (Player)this.affectedMob;
                            this.owner.setSkulledOn(affectedPlayer);
                        }
                        int damage = Formulae.calcSpellHit(EntityHandler.getSpellAggressiveLvl(spellID), this.owner.getMagicPoints());
                        /* Gauntlets of chaos, the Family crest reward the wizard
                           gives: "improves bolt spells" is the whole of what the
                           item says about itself, and Jagex never published the
                           number. Three, which is what the same gauntlets are
                           worth in the game RSC became. */
                        if (SpellHandler.isBoltSpell(spellID)
                                && this.owner.getInventory().wielding(CHAOS_GAUNTLETS)) {
                            damage += 3;
                        }
                        if (this.affectedMob instanceof Player) {
                            Player affectedPlayer = (Player)this.affectedMob;
                            affectedPlayer.getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
                        }
                        Projectile projectile = new Projectile(this.owner, this.affectedMob, 1);
                        this.affectedMob.setLastDamage(damage);
                        int newHp = this.affectedMob.getHits() - damage;
                        this.affectedMob.setHits(newHp);
                        org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damage);
                        ArrayList<Player> playersToInform = new ArrayList<Player>();
                        playersToInform.addAll(this.owner.getViewArea().getPlayersInView());
                        playersToInform.addAll(this.affectedMob.getViewArea().getPlayersInView());
                        for (Player p : playersToInform) {
                            p.informOfProjectile(projectile);
                            p.informOfModifiedHits(this.affectedMob);
                        }
                        if (this.affectedMob instanceof Player) {
                            Player affectedPlayer = (Player)this.affectedMob;
                            affectedPlayer.getActionSender().sendStat(3);
                        }
                        // Reported before the death is decided, so that a quest
                        // counting which spells have hit an npc has this one on
                        // the tally by the time refusesKill() is asked.
                        if (this.affectedMob instanceof Npc) {
                            this.owner.getQuestManager().triggerSpell((Npc)this.affectedMob, spellID, damage);
                        }
                        if (newHp <= 0) {
                            this.affectedMob.killedBy(this.owner, this.owner.isDueling());
                        } else if (this.affectedMob instanceof Npc) {
                            ((Npc) this.affectedMob).retaliate(this.owner);
                        }
                        SpellHandler.this.finalizeSpell(this.owner, spell);
                    }
                }
                this.owner.getActionSender().sendInventory();
                this.owner.getActionSender().sendStat(6);
            }
        });
    }

    /**
     * The six curse spells: Confuse, Weaken, Curse, Vulnerability, Enfeeble
     * and Stun. Each takes a percentage off one of the target's three combat
     * stats and does no damage at all.
     *
     *      Confuse         3      attack     5%
     *      Weaken         11      strength   5%
     *      Curse          19      defence    5%
     *      Vulnerability  66      defence   10%
     *      Enfeeble       73      strength  10%
     *      Stun           80      attack    10%
     *
     * The percentage comes off the base level, not off whatever the level
     * happens to be, so two casts of Weaken do not compound -- and they do not
     * need to, because they refuse to stack in the first place. A stat that has
     * already been pushed below its base is immune; a stat that a potion has
     * pushed above it is not, which is the one thing these spells are good for
     * in a fight and the reason two Weakens cancel a strength potion.
     */
    /**
     * The stat word the curse messages use. Only three of the eighteen stats
     * can be cursed, and the one that matters is defence: every Jagex line
     * spells it with a c, while our internal {@link Formulae#statArray} spells
     * it "defense" and is used for far too much to respell safely.
     */
    private static String curseStatName(int stat) {
        return stat == 1 ? "defence" : Formulae.statArray[stat];
    }

    private void curseSpell(Player caster, Mob target, int spellID, SpellDef spell) {
        int stat;
        int percent;
        String name;
        switch (spellID) {
            case 1:  { stat = 0; percent =  5; name = "confuse";       break; }
            case 5:  { stat = 2; percent =  5; name = "weaken";        break; }
            case 9:  { stat = 1; percent =  5; name = "curse";         break; }
            case 42: { stat = 1; percent = 10; name = "vulnerability"; break; }
            case 45: { stat = 2; percent = 10; name = "enfeeble";      break; }
            case 47: { stat = 0; percent = 10; name = "stun";          break; }
            default: { return; }
        }
        if (!SpellHandler.checkAndRemoveRunes(caster, spell)) {
            return;
        }
        if (target instanceof Player && !caster.isDueling()) {
            caster.setSkulledOn((Player)target);
        }
        if (target instanceof Player) {
            ((Player)target).getActionSender().sendMessage("@pnk@ " + caster.getUsername() + " is shooting at you!");
        }
        Projectile projectile = new Projectile(caster, target, 1);
        ArrayList<Player> playersToInform = new ArrayList<Player>();
        playersToInform.addAll(caster.getViewArea().getPlayersInView());
        playersToInform.addAll(target.getViewArea().getPlayersInView());
        for (Player p : playersToInform) {
            p.informOfProjectile(projectile);
        }
        int base = target instanceof Player ? ((Player)target).getMaxStat(stat) : ((Npc)target).getBaseStat(stat);
        int current = target instanceof Player ? ((Player)target).getCurStat(stat) : ((Npc)target).getBaseStat(stat) - ((Npc)target).getDrain(stat);
        if (current < base) {
            /* "Your opponent already has weakened <stat>" is Jagex's, from
               RSCSundae's skill_magic/curses.lua, which carries it identically
               in all six branches (player and npc targets of confuse, weaken
               and curse). Ours said "That defense has already been weakened",
               which named the stat with our internal spelling and never said
               whose it was. Note the stat word here is the spell's own -- the
               attested lines read "attack", "defence", "strength", so defence
               cannot come from Formulae.statArray, which spells it "defense". */
            caster.getActionSender().sendMessage("@gry@ Your opponent already has weakened " + curseStatName(stat));
            SpellHandler.this.finalizeSpell(caster, spell);
            return;
        }
        int drain = base * percent / 100;
        if (drain < 1) {
            drain = 1;
        }
        if (target instanceof Player) {
            Player victim = (Player)target;
            victim.setCurStat(stat, Math.max(0, current - drain));
            victim.getActionSender().sendStat(stat);
            // "defense" is Jagex's spelling of the stat and of this line.
            victim.getActionSender().sendMessage("@pnk@ Your " + Formulae.statArray[stat]
                + " has been reduced by a " + name + " spell!");
        } else {
            ((Npc)target).drain(stat, drain);
            ((Npc)target).retaliate(caster);
        }
        SpellHandler.this.finalizeSpell(caster, spell);
    }

    /*
     * All three god spells share Formulae.calcGodSpells for damage -- flat
     * 18/25 max hit, sourced where that method is defined -- but real RSC
     * set them apart with a second, quieter effect on every successful hit,
     * per classic.runescape.wiki (checked per spell):
     *
     *   Claws of Guthix    Defense lowered 5%, with a message to the target.
     *   Saradomin strike   Prayer lowered by a flat 1, with NO message -- the
     *                      page is explicit the target is not warned.
     *   Flames of Zamorak  Magic lowered. The page confirms the effect exists
     *                      but not its exact size; 5% is carried over from
     *                      Guthix's sourced figure for consistency, not
     *                      independently sourced for Zamorak the way the
     *                      other two are.
     *
     * Npcs have no Prayer or Magic stat in this codebase's model (Npc's
     * getBaseStat only knows attack/defense/strength -- see Npc.java), so the
     * Saradomin and Zamorak drains are Player-only; Guthix's Defense drain
     * applies to either target, the same as an ordinary curse spell.
     */
    private void drainPercent(Mob target, int stat, int percent, String message) {
        int base = target instanceof Player ? ((Player) target).getMaxStat(stat) : ((Npc) target).getBaseStat(stat);
        int current = target instanceof Player ? ((Player) target).getCurStat(stat) : ((Npc) target).getBaseStat(stat) - ((Npc) target).getDrain(stat);
        if (current < base) {
            return;
        }
        int drain = Math.max(1, base * percent / 100);
        if (target instanceof Player) {
            Player victim = (Player) target;
            victim.setCurStat(stat, Math.max(0, current - drain));
            victim.getActionSender().sendStat(stat);
            if (message != null) {
                victim.getActionSender().sendMessage(message);
            }
        } else {
            ((Npc) target).drain(stat, drain);
        }
    }

    private void drainPrayerFlat(Mob target) {
        if (!(target instanceof Player)) {
            return;
        }
        Player victim = (Player) target;
        victim.setCurStat(5, Math.max(0, victim.getCurStat(5) - 1));
        victim.getActionSender().sendStat(5);
    }

    public void godSpellObject(Mob affectedMob, int spell) {
        switch (spell) {
            case 33: {  // Claws of Guthix
                GameObject guthix = new GameObject(affectedMob.getLocation(), 1142, 0, 0);
                world.registerGameObject(guthix);
                world.getDelayedEventHandler().add(new ObjectRemover(guthix, 500));
                break;
            }
            case 34: {  // Saradomin strike
                GameObject sara = new GameObject(affectedMob.getLocation(), 1031, 0, 0);
                world.registerGameObject(sara);
                world.getDelayedEventHandler().add(new ObjectRemover(sara, 500));
                break;
            }
            case 35: {  // Flames of Zamorak
                GameObject zammy = new GameObject(affectedMob.getLocation(), 1036, 0, 0);
                world.registerGameObject(zammy);
                world.getDelayedEventHandler().add(new ObjectRemover(zammy, 500));
            }
        }
    }

    private void handleItemCast(Player player, final SpellDef spell, final int id, final Item affectedItem) {
        player.setStatus(Action.CASTING_GITEM);
        world.getDelayedEventHandler().add(new WalkToPointEvent(player, affectedItem.getLocation(), 5, true){

            public void arrived() {
                this.owner.resetPath();
                ActiveTile tile = world.getTile(this.location);
                if (!SpellHandler.canCast(this.owner) || !tile.hasItem(affectedItem) || this.owner.getStatus() != Action.CASTING_GITEM) {
                    return;
                }
                this.owner.resetAllExceptDueling();
                switch (id) {
                    case TELEKINETIC_GRAB: {
                        if (affectedItem.getLocation().inBounds(531, 3578, 550, 3610) || affectedItem.getLocation().inBounds(531, 3578, 550, 3610)) {
                            this.owner.getActionSender().sendMessage("@gry@ Spell disabled.");
                            return;
                        }
                        // Asked again after the walk, because the item can move
                        // while the caster is crossing to it. See the note at
                        // case 104 for why once is not enough either way.
                        if (wardedFromTelegrab(this.owner, affectedItem)) {
                            this.owner.getActionSender().sendMessage(TELEGRAB_WARDED);
                            return;
                        }
                        /*
                         * Asked before the runes are spent, and before the
                         * item moves. PickupItem asks the same question of the
                         * same quests; this spell used to reach the floor
                         * without asking anyone, so anything a quest was
                         * holding down could be lifted from five tiles away.
                         * The refusing quest sends its own message.
                         */
                        if (this.owner.getQuestManager().refusesTelegrab(
                                new InvItem(affectedItem.getID(), affectedItem.getAmount()))) {
                            return;
                        }
                        if (!SpellHandler.checkAndRemoveRunes(this.owner, spell)) {
                            return;
                        }
                        this.owner.getActionSender().sendTeleBubble(this.location.getX(), this.location.getY(), true);
                        Iterator<Player> i$ = this.owner.getWatchedPlayers().getAllEntities().iterator();
                        while (i$.hasNext()) {
                            Player o;
                            Player p = o = i$.next();
                            p.getActionSender().sendTeleBubble(this.location.getX(), this.location.getY(), true);
                        }
                        world.unregisterItem(affectedItem);
                        SpellHandler.this.finalizeSpell(this.owner, spell);
                        this.owner.getInventory().add(new InvItem(affectedItem.getID(), affectedItem.getAmount()));
                    }
                }
                this.owner.getActionSender().sendInventory();
                this.owner.getActionSender().sendStat(6);
            }
        });
    }

    private void handleInvItemCast(Player player, SpellDef spell, int id, InvItem affectedItem) {
        switch (id) {
            /* The five enchant spells all report success with the same line,
               "@que@You succesfully enchant the amulet" -- Jagex's spelling of
               "successfully", preserved. RSCSundae has it under a separate pcap
               citation for each of levels 1 to 4
               (`Skilling/Magic/spell- cosmic- enchant lvl N.pcap`). Level 5 is
               the one extrapolation: it is dragonstone, which did not exist in
               the 2001 tree those captures come from, so it gets the same line
               on the grounds that the other four are identical to each other.
               Before this, all five enchanted in complete silence. */
            case 3: {  // Enchant lvl-1 amulet
                if (affectedItem.getID() == 302) {
                    if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                        return;
                    }
                    player.getInventory().remove(affectedItem);
                    player.getInventory().add(new InvItem(314));
                    player.getActionSender().sendMessage("@que@You succesfully enchant the amulet");
                    this.finalizeSpell(player, spell);
                    break;
                }
                player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
                break;
            }
            case 10: {  // Low level alchemy
                if (affectedItem.getID() == 10) {
                    player.getActionSender().sendMessage("@gry@ You cannot alchemy that");
                    return;
                }
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                    return;
                }
                if (player.getInventory().remove(affectedItem) > 1) {
                    int value = (int)((double)affectedItem.getDef().getBasePrice() * 0.4 * (double)affectedItem.getAmount());
                    player.getInventory().add(new InvItem(10, value));
                }
                this.finalizeSpell(player, spell);
                break;
            }
            case 13: {  // Enchant lvl-2 amulet
                if (affectedItem.getID() == 303) {
                    if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                        return;
                    }
                    player.getInventory().remove(affectedItem);
                    player.getInventory().add(new InvItem(315));
                    player.getActionSender().sendMessage("@que@You succesfully enchant the amulet");
                    this.finalizeSpell(player, spell);
                    break;
                }
                player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
                break;
            }
            case 21: {  // Superheat item
                /* Coal is smeltable here even though it has no smelting def of
                   its own -- casting on coal makes steel, exactly as using coal
                   on a furnace does. It used to be turned away as "not this
                   kind of item". */
                Smelting.Recipe recipe = Smelting.recipeFor(player, affectedItem.getID(), false);
                if (recipe == null) {
                    player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
                    return;
                }
                if (!Smelting.canSmelt(player, recipe)) {
                    return;
                }
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                    return;
                }
                Smelting.consume(player, recipe);
                /* Superheat never fails on iron the way a furnace does -- the
                   spell is hot enough to burn the impurities off. */
                player.getActionSender().sendMessage(Smelting.made(recipe));
                player.getInventory().add(new InvItem(recipe.getBarId()));
                player.incExp(13, recipe.getExp(), true);
                player.getActionSender().sendStat(13);
                player.getActionSender().sendInventory();
                this.finalizeSpell(player, spell);
                break;
            }
            case 24: {  // Enchant lvl-3 amulet
                if (affectedItem.getID() == 304) {
                    if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                        return;
                    }
                    player.getInventory().remove(affectedItem);
                    player.getInventory().add(new InvItem(316));
                    player.getActionSender().sendMessage("@que@You succesfully enchant the amulet");
                    this.finalizeSpell(player, spell);
                    break;
                }
                player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
                break;
            }
            case 28: {  // High level alchemy
                if (affectedItem.getID() == 10) {
                    player.getActionSender().sendMessage("@gry@ You cannot alchemy that");
                    return;
                }
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                    return;
                }
                if (player.getInventory().remove(affectedItem) > -1) {
                    int value = (int)((double)affectedItem.getDef().getBasePrice() * 0.6 * (double)affectedItem.getAmount());
                    player.getInventory().add(new InvItem(10, value));
                }
                this.finalizeSpell(player, spell);
                break;
            }
            case 30: {  // Enchant lvl-4 amulet
                if (affectedItem.getID() == 305) {
                    if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                        return;
                    }
                    player.getInventory().remove(affectedItem);
                    player.getInventory().add(new InvItem(317));
                    player.getActionSender().sendMessage("@que@You succesfully enchant the amulet");
                    this.finalizeSpell(player, spell);
                    break;
                }
                player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
                break;
            }
            case 43: {  // Enchant lvl-5 amulet
                if (affectedItem.getID() == 610) {
                    if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                        return;
                    }
                    player.getInventory().remove(affectedItem);
                    player.getInventory().add(new InvItem(522));
                    player.getActionSender().sendMessage("@que@You succesfully enchant the amulet");
                    this.finalizeSpell(player, spell);
                    break;
                }
                player.getActionSender().sendMessage("@gry@ This spell cannot be used on this kind of item");
            }
        }
        if (affectedItem.isWielded()) {
            player.getActionSender().sendSound("click");
            affectedItem.setWield(false);
            player.updateWornItems(affectedItem.getWieldableDef().getWieldPos(), player.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos()));
            player.getActionSender().sendEquipmentStats();
        }
    }

    private void handleGroundCast(Player player, SpellDef spell, int id) {
        switch (id) {
            case 7: {  // Bones to bananas
                /* v25 had this and Ignis Isle dropped it along with the rest of
                   the vanilla spell list. Restored as v25 wrote it: every bone
                   in the inventory becomes a banana, one for one. */
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                    return;
                }
                Iterator<InvItem> inventory = player.getInventory().iterator();
                int boneCount = 0;
                while (inventory.hasNext()) {
                    InvItem i = inventory.next();
                    if (i.getID() != 20) continue;
                    inventory.remove();
                    ++boneCount;
                }
                for (int i = 0; i < boneCount; ++i) {
                    player.getInventory().add(new InvItem(249));
                }
                this.finalizeSpell(player, spell);
                break;
            }
            case 48: {  // Charge
                if (world.getTile(player.getLocation()).hasGameObject()) {
                    player.getActionSender().sendMessage("@gry@ You cannot charge here, please move to a different area.");
                    return;
                }
                /*
                 * "A god staff is required to be equipped to cast Charge, but
                 * a god cape is not. However, a god cape is required to be
                 * equipped to receive Charge's benefit." -- classic.runescape
                 * .wiki Charge, CiteReplay kRiStOf "Mage Arena Part 4of5".
                 * Only the staff half is a cast requirement; the cape half is
                 * already how the god spells read isCharged().
                 */
                if (!player.getInventory().wielding(STAFF_ZAMORAK)
                        && !player.getInventory().wielding(STAFF_GUTHIX)
                        && !player.getInventory().wielding(STAFF_SARADOMIN)) {
                    player.getActionSender().sendMessage("@gry@ You need to be wielding a god staff to cast this spell!");
                    return;
                }
                if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
                    return;
                }
                GameObject charge = new GameObject(player.getLocation(), 1147, 0, 0);
                world.registerGameObject(charge);
                world.delayedRemoveObject(charge, 500);
                /*
                 * Was player.isCharged() -- the getter, its return value
                 * discarded, so casting Charge never actually charged
                 * anyone. isCharged()/setCharged() are a real, correct
                 * 10-minute buff (confirmed against classic.runescape.wiki:
                 * Charge raises the three god spells' max hit 18->25 for a
                 * time "before a battle") -- this is unrelated to the
                 * permanent per-spell 100-in-arena-casts unlock in
                 * GodCharges/MageArena, which gates casting a god spell at
                 * all outside the Mage Arena. Two different kinds of
                 * "charged"; this one just had its setter typo'd into a
                 * getter.
                 */
                player.setCharged();
                /*
                 * v25 printed this and it went missing when the isCharged()
                 * typo above was fixed, so Charge landed with no feedback at
                 * all beyond the generic cast line. NOT ATTESTED as Jagex's
                 * wording: no replay or transcript on disk records a message
                 * for this spell, and the wiki page describes only the
                 * animation. It is restored because it is RSCD's own
                 * inherited line and OpenRSC independently carries the same
                 * sentence ("You feel charged with magic power"), which makes
                 * it the least-invented option -- not because it is sourced.
                 */
                player.getActionSender().sendMessage("@gre@You feel charged with magical power...");
                this.finalizeSpell(player, spell);
            }
        }
    }

    /*
     * The seven teleports, back on RSCD v25's destinations and v25's spell ids.
     *
     * Ignis Isle replaced the spell table with its own 45-spell list and re-keyed
     * this switch to match it (0/10/13/16/20). The client's table was never
     * changed, so it still sent v25's ids -- and since the two lists disagreed at
     * 46 of 49 positions, casting Varrock teleport arrived here as Earth bolt and
     * never reached this method at all. Three of the five destinations below were
     * (555, 555) besides: an unset placeholder in a field outside Falador.
     *
     * The coordinates are v25's, checked against this world's own spawn data
     * before being trusted -- Varrock lands beside Horvik and Baraek, Ardougne
     * beside the zoo keeper, Lost City beside the Fairy queen.
     */
    private void handleTeleport(Player player, SpellDef spell, int id) {
        if (player.inCombat()) {
            player.getActionSender().sendMessage("@gry@ Spell disabled.");
            return;
        }
        /* Above level 20 wilderness, as vanilla and as v25. Ignis Isle had this
           at >= 1, which blocked teleporting out of the whole wilderness rather
           than the deep half -- the escape teleport is the point of carrying
           runes into level 1-19. */
        if (player.getLocation().wildernessLevel() >= 20) {
            /* Both lines are Jagex's, from RSCSundae's skill_magic/teleport.lua
               (cited to `fsnom2@aol.com/06-19-2018 21.27.32.pcap`). Ours said
               "Spell disabled.", which is the generic refusal this file uses
               for everything and told the player nothing about why. The second
               line is the one that matters -- it is where a player learns the
               boundary is 20 and not the whole wilderness. */
            player.getActionSender().sendMessage("@gry@ A mysterious force blocks your teleport spell!");
            player.getActionSender().sendMessage("@gry@ You can't use teleport after level 20 wilderness");
            return;
        }
        if (player.getLocation().inModRoom() && !player.isMod()) {
            // No attested wording for this one -- it is not a Jagex place.
            player.getActionSender().sendMessage("@gry@ Spell disabled.");
            return;
        }
        /* Two of the seven are taught rather than known. Plague city ends with
           Edmond handing over a magic scroll and Watchtower with the wizard
           handing over a spell scroll, and it is reading the scroll that
           unlocks the spell -- finishing the quest is not enough, which is a
           thing players find out by finishing the quest and then losing the
           scroll. Each quest publishes the answer under a name; nothing in here
           can see a quest's stages.

           Both refusals are two lines and both are Jagex's. They say the quest
           is the requirement, which is not quite what the game checks. That is
           the game's own inconsistency and it is kept. */
        if (id == 26 && !player.getQuestManager().reached(org.rscdaemon.server.quest.Quests.PLAGUE_CITY, "ardougne-teleport")) {
            player.getActionSender().sendMessage("@gry@ You don't know how to cast this spell yet");
            player.getActionSender().sendMessage("@gry@ You need to do the plague city quest");
            return;
        }
        if (id == 31 && !player.getQuestManager().reached(org.rscdaemon.server.quest.Quests.WATCHTOWER, "watchtower-teleport")) {
            player.getActionSender().sendMessage("@gry@ You cannot cast this spell");
            player.getActionSender().sendMessage("@gry@ You need to finish the watchtower quest first");
            return;
        }
        /* Lost City teleport is gated on finishing the quest, not on a scroll --
           the quest has no scroll to lose, so completion is the whole condition
           and completed() is the right call rather than reached().

           Two things about this spell are worth knowing before touching it.
           First, the wording below is OURS. Jagex's refusal for it is not
           attested anywhere -- not on the wiki, not in any transcript or replay
           in the corpus -- so it is modelled on the Watchtower pair above,
           which is attested. Second, and the reason no wording exists: the
           string table in Jagex's own cache (jagex_data config85, string.dat)
           lists six teleports and Lost City is not among them. Whether this
           spell should exist at all is a separate question, flagged for the
           user; while it exists it must not be free. */
        if (id == 37 && !player.getQuestManager().completed(org.rscdaemon.server.quest.Quests.LOST_CITY)) {
            player.getActionSender().sendMessage("@gry@ You cannot cast this spell");
            player.getActionSender().sendMessage("@gry@ You need to finish the lost city quest first");
            return;
        }
        if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
            return;
        }
        Formulae.teleportContraband(player);
        switch (id) {
            case 12: {  // Varrock
                player.teleport(122, 503, true);
                break;
            }
            case 15: {  // Lumbridge
                player.teleport(118, 649, true);
                break;
            }
            case 18: {  // Falador
                player.teleport(313, 550, true);
                break;
            }
            case 22: {  // Camelot
                player.teleport(465, 456, true);
                break;
            }
            case 26: {  // Ardougne
                player.teleport(585, 621, true);
                break;
            }
            case 31: {  // Watchtower
                player.teleport(637, 2628, true);
                break;
            }
            case 37: {  // Lost City
                player.teleport(131, 3544, true);
                break;
            }
        }
        this.finalizeSpell(player, spell);
    }

    private static boolean canCast(Player player) {
        if (!player.castTimer()) {
            player.getActionSender().sendMessage("@gry@ You must wait another " + player.getSpellWait() + " seconds to cast another spell.");
            player.resetPath();
            return false;
        }
        return true;
    }

    private static boolean checkAndRemoveRunes(Player player, SpellDef spell) {
        boolean skipRune;
        for (Map.Entry<Integer, Integer> e : spell.getRunesRequired()) {
            skipRune = false;
            block1: for (InvItem staff : SpellHandler.getStaffs(e.getKey())) {
                if (!player.getInventory().contains(staff)) continue;
                for (InvItem item : player.getInventory().getItems()) {
                    if (!item.equals(staff) || !item.isWielded()) continue;
                    skipRune = true;
                    continue block1;
                }
            }
            if (skipRune || player.getInventory().countId(e.getKey()) >= e.getValue()) continue;
            player.setSuspiciousPlayer(true);
            player.getActionSender().sendMessage("@gry@ You don't have all the reagents you need for this spell");
            return false;
        }
        for (Map.Entry<Integer, Integer> e : spell.getRunesRequired()) {
            skipRune = false;
            block4: for (InvItem staff : SpellHandler.getStaffs(e.getKey())) {
                if (!player.getInventory().contains(staff)) continue;
                for (InvItem item : player.getInventory().getItems()) {
                    if (!item.equals(staff) || !item.isWielded()) continue;
                    skipRune = true;
                    continue block4;
                }
            }
            if (skipRune) continue;
            player.getInventory().remove(e.getKey(), e.getValue());
        }
        return true;
    }

    /*
     * A god spell (Claws of Guthix/Saradomin strike/Flames of Zamorak) may
     * only be cast outside the Mage Arena once that specific spell has
     * landed 100 casts inside it -- a permanent, per-spell unlock, tracked
     * in GodCharges.java (a quest-shaped record, not a real quest -- see
     * that file for why: quest classes compile into the default package,
     * so this file cannot name one directly and can only reach it by uid
     * through QuestManager.note()/.reached()).
     *
     * Before this check existed, ANY player wearing the cape and staff
     * could cast these anywhere, forever, with only a smaller max hit
     * (that part is Formulae/isCharged's own business, from the separate
     * Charge spell buff, and is untouched by this).
     */
    private boolean checkGodSpellGate(Player player, String god) {
        boolean inArena = player.getLocation().inMageArena();
        boolean charged = player.getQuestManager().reached(Quests.GOD_CHARGES, god + "-charged");
        if (!inArena && !charged) {
            player.getActionSender().sendMessage("@gry@ You need to train this spell inside the Mage Arena first.");
            return false;
        }
        if (inArena && !charged) {
            player.getQuestManager().note(Quests.GOD_CHARGES, god + "-cast");
        }
        return true;
    }

    private void finalizeSpell(Player player, SpellDef spell) {
        player.getActionSender().sendSound("spellok");
        player.getActionSender().sendMessage("@pnk@ Cast spell successfully");
        player.incExp(6, spell.getExp(), true);
        player.setCastTimer();
    }

    /*
     * spellID -> { obelisk, powered orb } for the four Charge Orb spells:
     * water at the obelisk of water, earth at earth, fire at fire, air at the
     * obelisk of air in the wilderness. The unpowered orb rides along as one
     * of the spell's runes, so checkAndRemoveRunes does all the taking and
     * success only has to hand the powered orb back.
     */
    private static TreeMap<Integer, int[]> chargeOrbs = new TreeMap();

    private static boolean isObelisk(int id) {
        return id == 300 || id == 301 || id == 303 || id == 304;
    }

    private static boolean chargeOrb(Player player, SpellDef spell, int spellID, GameObject object) {
        int[] charge = chargeOrbs.get(spellID);
        if (!SpellHandler.isObelisk(object.getID())) {
            /* Not an obelisk: a quest may still answer this cast -- the Dark
               Metal Gate takes any of the four Charge Orb spells. */
            return false;
        }
        if (charge == null || charge[0] != object.getID()) {
            player.getActionSender().sendMessage("@gry@ Nothing interesting happens.");
            return true;
        }
        if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
            return true;
        }
        player.getInventory().add(new InvItem(charge[1], 1));
        player.incExp(6, spell.getExp(), true);
        player.getActionSender().sendStat(6);
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("@pnk@ You charge the orb");
        return true;
    }

    private static InvItem[] getStaffs(int runeID) {
        InvItem[] items = staffs.get(runeID);
        if (items == null) {
            return new InvItem[0];
        }
        return items;
    }

    static {
        chargeOrbs.put(29, new int[]{300, 613});   // Charge Water Orb
        chargeOrbs.put(36, new int[]{304, 627});   // Charge earth Orb
        chargeOrbs.put(39, new int[]{301, 612});   // Charge Fire Orb
        chargeOrbs.put(41, new int[]{303, 626});   // Charge air Orb
        staffs.put(31, new InvItem[]{new InvItem(197), new InvItem(615), new InvItem(682)});
        staffs.put(32, new InvItem[]{new InvItem(102), new InvItem(616), new InvItem(683)});
        staffs.put(33, new InvItem[]{new InvItem(101), new InvItem(617), new InvItem(684)});
        staffs.put(34, new InvItem[]{new InvItem(103), new InvItem(618), new InvItem(685)});
    }
}

