/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.GameObjectDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectAgilityDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectFishDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectFishingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectMiningDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectStallDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectChestDef;
import org.rscdaemon.server.entityhandling.defs.extras.ThievingLoot;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectWoodcuttingDef;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.event.WalkToObjectEvent;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Mill;
import org.rscdaemon.server.model.Multicannon;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.BorderGuard;
import org.rscdaemon.server.npchandler.EntranaMonks;
import org.rscdaemon.server.npchandler.FishingTrawler;
import org.rscdaemon.server.npchandler.ShantayPass;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class ObjectAction
implements PacketHandler {
    public static final World world = World.getWorld();
    /** The seventeen Underground Pass grills that give way, all one id. */
    private static final int GRILL_TRAP = 782;
    /** The eight that hold, each with an id of its own. See handleGrill. */
    private static final int[] GRILL_SAFE = {777, 785, 786, 787, 788, 789, 790, 791};
    /**
     * Where a fallen player crawls out.
     *
     * "the beginning of the room" in the recovered account, which is the tile
     * outside the one grill that leads in from the west. A DETERMINATION: the
     * room's own entry tile is known, the words are Jagex's, and the two were
     * matched up here.
     */
    private static final int GRILL_START_X = 679, GRILL_START_Y = 3447;

    /** Stat slots 17 and 3. */
    private static final int THIEVING = 17, HITS = 3, AGILITY = 16;
    private static final int LOCKPICK = 714;

    /**
     * Who comes for a thief who is caught at a stall, hardest first.
     *
     * "the player will be attacked by a guard, knight, paladin, or hero
     * depending on the level required to steal from the stall". These are the
     * npcs that stand in the Ardougne market and around it, so the one that
     * answers is whichever of them is genuinely nearby -- nothing is summoned,
     * and a thief working a stall with nobody around simply gets away with
     * being seen. That is a consequence of not inventing spawns, and it is the
     * same answer vanilla gives when the guards are all dead.
     */
    private static final int[] STALL_GUARDS = { 324, 323, 322, 321, 100, 65 };

    /** A whole table as items, for the room check a chest needs. */
    private static java.util.List<InvItem> asItems(java.util.List<ThievingLoot> loot) {
        java.util.ArrayList<InvItem> items = new java.util.ArrayList<InvItem>();
        for (ThievingLoot l : loot) {
            items.add(new InvItem(l.getID(), l.getAmountLow()));
        }
        return items;
    }

    /** One entry from a table, drawn flat. */
    private static InvItem draw(java.util.List<ThievingLoot> loot) {
        ThievingLoot l = loot.get(DataConversions.random(0, loot.size() - 1));
        return new InvItem(l.getID(), DataConversions.random(l.getAmountLow(), l.getAmountHigh()));
    }

    /**
     * Set the nearest guard on a thief, hardest first.
     *
     * The level of the stall picks the floor: a hero does not come out for the
     * cake stall. Anything at or below the thief's own tier is fair game, and
     * the search widens outward from the thief rather than from the stall,
     * because it is the thief who was seen.
     */
    private static void raiseTheGuard(Player player, int stallLevel) {
        for (int id : STALL_GUARDS) {
            Npc guard = world.getNpc(id, player.getX() - 10, player.getX() + 10,
                                     player.getY() - 10, player.getY() + 10);
            if (guard == null || guard.isBusy() || guard.getOpponent() != null) {
                continue;
            }
            guard.attackPlayer(player);
            return;
        }
    }


    /**
     * Pay for an Agility obstacle and keep the lap honest.
     *
     * A course is a lap: Classic pays a bonus for crossing every obstacle of a
     * course in order, and the bonus is most of what the course is worth --
     * 375 of the Wilderness course's 472.5, 75 of the Barbarian Outpost's 150.
     * So the obstacles have to be counted, and counted in order. Crossing one
     * out of turn is not cheating and is not punished; it simply is not a lap,
     * and the count starts again from wherever the player actually is.
     *
     * Standalone shortcuts pass courseId 0 and skip all of this.
     *
     * @param lapExp the bonus this obstacle pays for finishing a lap, or zero
     *               if it is not the last obstacle of its course.
     */
    /**
     * The voices of the Underground Pass.
     *
     * Crossing an obstacle anywhere in the pass has a chance of being followed,
     * a few seconds later, by a red whisper from Iban -- the recovered footage
     * shows them landing between the crossing lines themselves. Two of the five
     * lines play for anyone; the other three only once the quest is past the
     * fallen bridge, when Iban has noticed the intruder.
     */
    public static void ibanWhisper(final Player player) {
        int x = player.getX(), y = player.getY();
        boolean inPass = x >= 660 && x <= 795
            && ((y >= 3395 && y <= 3525) || (y >= 550 && y <= 650));
        if (!inPass) {
            return;
        }
        final int roll = DataConversions.random(0, 5);
        int stage = player.getQuestStage(41);
        final boolean noticed = stage != -1 && (stage & 31) >= 5;
        World.getWorld().getDelayedEventHandler().add(
            new SingleEvent(player, DataConversions.random(3000, 15000)) {
                public void action() {
                    if (this.owner.isRemoved()) {
                        return;
                    }
                    switch (roll) {
                        case 0:
                            this.owner.getActionSender().sendMessage("@red@iban will save you....he'll save us all");
                            break;
                        case 1:
                            this.owner.getActionSender().sendMessage("@red@join us...join us...embrace the mysery");
                            break;
                        case 2:
                            if (noticed) this.owner.getActionSender().sendMessage("@red@I see you adventurer...you can't hide");
                            break;
                        case 3:
                            if (noticed) this.owner.getActionSender().sendMessage("@red@Come taste the pleasure of evil");
                            break;
                        case 4:
                            if (noticed) this.owner.getActionSender().sendMessage("@red@Death is only the beginning");
                            break;
                        default:
                            break;
                    }
                }
            });
    }

    public static void awardAgility(Player player, int courseId, int stage,
                                    int exp, int lapExp, boolean ignoresFatigue) {
        if (courseId > 0) {
            boolean inOrder = stage == 0
                || (player.getAgilityCourse() == courseId && player.getAgilityStage() == stage - 1);
            if (!inOrder) {
                player.resetAgilityProgress();
            } else if (lapExp > 0) {
                exp += lapExp;
                player.getActionSender().sendMessage("@pnk@ You have completed a lap of the course");
                player.resetAgilityProgress();
            } else {
                player.setAgilityProgress(courseId, stage);
            }
        }
        if (exp <= 0) {
            return;
        }
        if (ignoresFatigue) {
            player.incExpNoFatigue(AGILITY, exp);
        } else {
            player.incExp(AGILITY, exp, true);
        }
        player.getActionSender().sendStat(AGILITY);
    }

    public void handlePacket(Packet p, Connection session) {
        int click;
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        GameObject object = world.getTile(p.readShort(), p.readShort()).getGameObject();
        int n = click = pID == 51 ? 0 : 1;
        if (object == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        player.setStatus(Action.USING_OBJECT);
        world.getDelayedEventHandler().add(new WalkToObjectEvent(player, object, false){

            private void replaceGameObject(int newID, boolean open) {
                world.registerGameObject(new GameObject(this.object.getLocation(), newID, this.object.getDirection(), this.object.getType()));
                this.owner.getActionSender().sendSound(open ? "opendoor" : "closedoor");
            }

            private void doGate() {
                /* 181 is the open form of the Lumbridge-Al Kharid toll gate.
                   RSCD swapped it in for every gate in the game; gates whose
                   own open form is known pass it explicitly below. */
                this.doGate(181);
            }

            private void doGate(int openID) {
                this.owner.getActionSender().sendSound("opendoor");
                world.registerGameObject(new GameObject(this.object.getLocation(), openID, this.object.getDirection(), this.object.getType()));
                world.delayedSpawnObject(this.object.getLoc(), 1000);
            }

            private int[] coordModifier(Player player, boolean up) {
                if (this.object.getGameObjectDef().getHeight() <= 1) {
                    return new int[]{player.getX(), Formulae.getNewY(player.getY(), up)};
                }
                int[] coords = new int[]{this.object.getX(), Formulae.getNewY(this.object.getY(), up)};
                switch (this.object.getDirection()) {
                    case 0: {
                        coords[1] = coords[1] - (up ? -this.object.getGameObjectDef().getHeight() : 1);
                        break;
                    }
                    case 2: {
                        coords[0] = coords[0] - (up ? -this.object.getGameObjectDef().getHeight() : 1);
                        break;
                    }
                    case 4: {
                        coords[1] = coords[1] + (up ? -1 : this.object.getGameObjectDef().getHeight());
                        break;
                    }
                    case 6: {
                        coords[0] = coords[0] + (up ? -1 : this.object.getGameObjectDef().getHeight());
                    }
                }
                return coords;
            }

            public void arrived() {
                block133: {
                    String command;
                    block142: {
                        block141: {
                            block140: {
                                block139: {
                                    block138: {
                                        block137: {
                                            block136: {
                                                block135: {
                                                    block134: {
                                                        block132: {
                                                            this.owner.resetPath();
                                                            GameObjectDef def = this.object.getGameObjectDef();
                                                            boolean claimed = this.owner.getQuestManager().associatedWithQuest(this.object);
                                                            /*
                                                             * nextTo() is a reachability test, not an adjacency one: it
                                                             * walks a straight line to the object and fails if anything
                                                             * blocks the way. That is right for a bank booth or a ladder
                                                             * -- it is what stops you using them through a wall -- and
                                                             * wrong for an object that IS the wall.
                                                             *
                                                             * The Mage Arena gates are the case that exposed it. A gate
                                                             * is a type-2 def, so registerObject() blocks one edge of
                                                             * it, and the arena fence is in the landscape besides. From
                                                             * outside, the step onto the gate tile happens to check the
                                                             * bit that is not set, so the gate opened; from inside it
                                                             * checks the bit that is, so nextTo() failed and the click
                                                             * died here -- silently, before the quest was ever asked.
                                                             * You could walk into the arena and never walk out.
                                                             *
                                                             * isObjectBlocking() already has the escape hatch for this
                                                             * (Formulae.objectAtFacing: the object you are walking to
                                                             * does not block you from reaching itself), but
                                                             * isMapBlocking() has no equivalent, and a fence drawn in
                                                             * the landscape blocks through it.
                                                             *
                                                             * So for objects a quest has claimed -- and only those --
                                                             * require adjacency instead. atObject() is the same test
                                                             * WalkToObjectEvent used to decide we had arrived: standing
                                                             * on or beside a tile of the footprint. Everything else
                                                             * keeps the strict check, so no bank, ladder or door is
                                                             * loosened by this. A quest that wants to care which side
                                                             * you are on can still ask -- barrier() below does.
                                                             */
                                                            boolean reached = claimed
                                                                ? this.owner.atObject(this.object)
                                                                : this.owner.nextTo(this.object);
                                                            if (this.owner.isBusy() || this.owner.isRanging() || !reached || def == null || this.owner.getStatus() != Action.USING_OBJECT) {
                                                                return;
                                                            }
                                                            this.owner.resetAll();
                                                            // A quest that has associated this object owns it outright,
                                                            // the same contract npc handlers get in TalkToNpcHandler:
                                                            // the built-in behaviour below is skipped entirely. Only
                                                            // objects a quest actually claimed reach here, so ordinary
                                                            // doors and ladders are untouched.
                                                            if (claimed) {
                                                                this.owner.getQuestManager().triggerEntity(
                                                                    click == 0 ? QuestTrigger.OBJECT_ACT1 : QuestTrigger.OBJECT_ACT2,
                                                                    this.object);
                                                                return;
                                                            }
                                                            // Agility, asked before the command is even read.
                                                            // Every obstacle is a scenery id that does one thing,
                                                            // and the commands they carry -- "climb up", "enter",
                                                            // "balance on" -- are shared with ladders and doorways
                                                            // that have to keep their old answers.
                                                            if (this.handlePassage()) {
                                                                return;
                                                            }
                                                            if (this.handleTrapRocks()) {
                                                                return;
                                                            }
                                                            if (this.handleCoalTruck()) {
                                                                return;
                                                            }
                                                            if (this.handleLoreRocks()) {
                                                                return;
                                                            }
                                                            if (this.handleTripWire()) {
                                                                return;
                                                            }
                                                            if (this.handleSwingPassage()) {
                                                                return;
                                                            }
                                                            if (this.handleLedgeJump()) {
                                                                return;
                                                            }
                                                            if (this.handleAgility()) {
                                                                return;
                                                            }
                                                            // The Underground Pass grill maze. Not Agility --
                                                            // there is no level and no experience -- and not a
                                                            // quest, because the maze is scenery and stays a
                                                            // maze long after the quest is over.
                                                            if (this.handleGrill()) {
                                                                return;
                                                            }
                                                            if (this.handlePassRocks()) {
                                                                return;
                                                            }
                                                            if (this.handleShantayGate()) {
                                                                return;
                                                            }
                                                            if (this.handleBankChest()) {
                                                                return;
                                                            }
                                                            if (this.handleLeafyPalm()) {
                                                                return;
                                                            }
                                                            // The Grave of Scorpius, which carries "Read".
                                                            if (this.handleGrave()) {
                                                                return;
                                                            }
                                                            // Entrana: the ship home, and the one-way ladder.
                                                            if (this.handleEntrana()) {
                                                                return;
                                                            }
                                                            // The Fishing Trawler's four deck objects.
                                                            if (this.handleTrawler()) {
                                                                return;
                                                            }
                                                            // The rune essence mine's spires and exit portals.
                                                            if (this.handleEssenceMine()) {
                                                                return;
                                                            }
                                                            // The Party Cannons in the Seers' party hall. They
                                                            // fire themselves; "fire" just reports the load.
                                                            if (this.object.getID() == org.rscdaemon.server.model.PartyCannon.OBJECT) {
                                                                org.rscdaemon.server.model.PartyCannon.inspect(this.owner, this.object);
                                                                return;
                                                            }
                                                            // The dwarf multicannon. Its four objects stand
                                                            // wherever a player set them down rather than in
                                                            // the world data, so they are asked for by owner
                                                            // and not by id -- 'fire' on the finished cannon,
                                                            // 'pick up' on any stage of it.
                                                            if (Multicannon.isCannonObject(this.object.getID())) {
                                                                Multicannon cannon = Multicannon.at(this.object);
                                                                if (cannon != null) {
                                                                    String c = (click == 0 ? def.getCommand1() : def.getCommand2()).toLowerCase();
                                                                    if (c.equals("fire")) {
                                                                        cannon.fire(this.owner);
                                                                        return;
                                                                    }
                                                                    if (c.equals("pick up")) {
                                                                        cannon.pickUp(this.owner);
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                            command = (click == 0 ? def.getCommand1() : def.getCommand2()).toLowerCase();
                                                            Point telePoint = EntityHandler.getObjectTelePoint(this.object.getLocation(), command);
                                                            if (telePoint == null) break block132;
                                                            this.owner.teleport(telePoint.getX(), telePoint.getY(), false);
                                                            break block133;
                                                        }
                                                        if (this.object.getID() != 198 || this.object.getX() != 251 || this.object.getY() != 468) break block134;
                                                        /*
                                                         * The gate is MEMBERSHIP, not prayer: "Attempting
                                                         * to climb the ladder will initiate a dialogue
                                                         * with Abbot Langley if you have not yet joined
                                                         * the monks order" (wiki, Monastery). Prayer 31 is
                                                         * only the bar for being allowed to join. The
                                                         * joined-the-order flag persists in the
                                                         * quest-stage store under reserved id 3001, the
                                                         * same trick as the coal trucks' 3000, so the
                                                         * acceptance scene fires exactly once.
                                                         *
                                                         * The option label reads "Well can i join your
                                                         * order?" with a lower-case i while the spoken
                                                         * line has a capital I, and the other branch does
                                                         * the same thing in reverse -- "Oh sorry" as the
                                                         * label, "Oh Sorry" as the line. Two separate
                                                         * sics, opposite directions, both Jagex's. Do not
                                                         * make them agree.
                                                         */
                                                        if (this.owner.getQuestStage(3001) > 0) {
                                                            this.owner.teleport(251, 1411, false);
                                                            break block133;
                                                        }
                                                        final Npc abbot = world.getNpc(174, 249, 252, 458, 468);
                                                        if (abbot == null) {
                                                            if (this.owner.getMaxStat(5) < 31) {
                                                                this.owner.getActionSender().sendMessage("@gry@ You need a prayer level of 31 to enter");
                                                            } else {
                                                                this.owner.teleport(251, 1411, false);
                                                            }
                                                            break block133;
                                                        }
                                                        new Conversation(this.owner, abbot)
                                                            .npc("Only members of our order can go up there")
                                                            .picker(new Choice("Well can i join your order?", "Oh sorry") {
                                                                public void picked(int option, Conversation c) {
                                                                    if (option != 0) {
                                                                        c.player("Oh Sorry");
                                                                        return;
                                                                    }
                                                                    c.player("Well can I join your order?");
                                                                    if (c.getPlayer().getMaxStat(5) < 31) {
                                                                        c.npc("No I feel you are not devout enough")
                                                                         .message("You need a prayer level of 31");
                                                                        return;
                                                                    }
                                                                    c.npc("Ok I see you are someone suitable for our order")
                                                                     .npc("You may join")
                                                                     .then(new org.rscdaemon.server.quest.dialogue.Effect() {
                                                                         public void run(Conversation conv) {
                                                                             conv.getPlayer().setQuestStage(3001, 1);
                                                                             conv.getPlayer().teleport(251, 1411, false);
                                                                         }
                                                                     });
                                                                }
                                                            })
                                                            .start();
                                                        break block133;
                                                    }
                                                    /*
                                                     * The first guard used to be half of an invented
                                                     * pair that teleported the jogre cave's exit
                                                     * ladder to Edgeville (219,435) and back; it is
                                                     * kept unmatchable rather than restructuring the
                                                     * decompiler's block chain.
                                                     */
                                                    if (this.object.getID() != -1) break block135;
                                                    break block133;
                                                }
                                                /*
                                                 * The jogre cave's exit ladder. Its surface twin,
                                                 * ladder 6 at (426,740), is one plane above, and the
                                                 * walked landing tile beside it is (426,739) -- the
                                                 * generic climb-up lands on the player's own column
                                                 * instead, which can be off by a tile.
                                                 */
                                                if (this.object.getID() != 5 || this.object.getX() != 426 || this.object.getY() != 3572) break block136;
                                                this.owner.teleport(426, 739, false);
                                                break block133;
                                            }
                                            if (this.object.getID() != 223 || this.object.getX() != 274 || this.object.getY() != 566) break block137;
                                            // Mining 60 and the recorded refusal
                                            // -- see the matching guild door in
                                            // WallObjectAction case 55 for why
                                            // the number changed as well as the
                                            // words. The ladder's grey message
                                            // is the shorter of the two Jagex
                                            // wrote; the door gets its own.
                                            if (this.owner.getCurStat(14) < 60) {
                                                this.owner.setBusy(true);
                                                Npc dwarf = world.getNpc(191, 272, 277, 563, 567);
                                                if (dwarf != null) {
                                                    this.owner.informOfNpcMessage(new ChatMessage(dwarf, "Sorry only the top miners are allowed in there", this.owner));
                                                }
                                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                                    public void action() {
                                                        this.owner.setBusy(false);
                                                        this.owner.getActionSender().sendMessage("@gry@ You need a mining of level 60 to enter");
                                                    }
                                                });
                                            } else {
                                                this.owner.teleport(274, 3397, false);
                                            }
                                            break block133;
                                        }
                                        if (!command.equals("climb-up") && !command.equals("climb up") && !command.equals("go up")) break block138;
                                        int[] coords = this.coordModifier(this.owner, true);
                                        this.owner.teleport(coords[0], coords[1], false);
                                        break block133;
                                    }
                                    if (!command.equals("climb-down") && !command.equals("climb down") && !command.equals("go down")) break block139;
                                    int[] coords = this.coordModifier(this.owner, false);
                                    this.owner.teleport(coords[0], coords[1], false);
                                    break block133;
                                }
                                // "operate" belongs to the windmill hoppers and
                                // to nothing else that is built, so it is asked
                                // by command rather than given four cases.
                                if (command.equals("operate") && Mill.isHopper(this.object.getID())) {
                                    Mill.operate(this.owner);
                                    break block133;
                                }
                                // Thieving. Asked here, above the open/close
                                // switch, because a trapped chest carries
                                // "open" as well and must not fall into it: the
                                // switch is for doors and lids and would answer
                                // "Nothing interesting happens" to a chest that
                                // in Classic answers by hurting you.
                                if (command.equals("steal from")) {
                                    this.handleStall();
                                    break block133;
                                }
                                if (command.equals("search for traps") || command.equals("picklock")) {
                                    this.handleChest();
                                    break block133;
                                }
                                if (command.equals("open") && EntityHandler.getObjectChestDef(this.object.getID()) != null) {
                                    this.forceChest();
                                    break block133;
                                }
                                /* The sinister chest and the Taverley crystal
                                   chest carry "Open" too, and neither is a
                                   thieving chest -- each opens for one key and
                                   nothing else. Without this they fell to the
                                   door switch below and answered "Nothing
                                   interesting happens", which reads as scenery
                                   rather than as a locked chest. Using the key
                                   on them is handled in InvUseOnObject. */
                                if (command.equals("open")
                                        && (this.object.getID() == 645 || this.object.getID() == 248)) {
                                    this.owner.getActionSender().sendMessage("@gry@ The chest is locked");
                                    break block133;
                                }
                                if (!command.equals("pull")) break block140;
                                switch (this.object.getID()) {
                                    case 488: {
                                        this.owner.getActionSender().sendMessage("@pnk@ You pull the lever...");
                                        this.owner.teleport(555, 555, false);
                                        return;
                                    }
                                }
                                break block133;
                            }
                            /* Both words, because Jagex used both. Five of the
                               six beds in GameObjectDef carry "rest" as their
                               first command -- 14 and 15 (Bed), 1035 and 1162
                               (Crude bed), 1171 (Comfy bed) -- and the digsite
                               bed, 1182, carries "sleep". Asking only for
                               "rest" left that one answering "Nothing
                               interesting happens", and no quest claims it.

                               setBusy for the duration, which the bed did not
                               do and the sleeping bag always did
                               (InvActionHandler:1263). Resting is resting in
                               both places or it is not resting in either. */
                            /* "lie in" is the gnome hammock (641), the tree
                               gnomes' only bed -- same rest, third wording. */
                            if (!command.equals("rest") && !command.equals("sleep") && !command.equals("lie in")) break block141;
                            this.owner.setBusy(true);
                            this.owner.getActionSender().sendMessage(
                                "lie in".equals(command) ? "@pnk@ You lie down in the hammock"
                                                         : "@pnk@ You rest on the bed");
                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                public void action() {
                                    this.owner.setFatigue(0);
                                    this.owner.getActionSender().sendFatigue();
                                    this.owner.getActionSender().sendMessage("@pnk@ You wake up - feeling refreshed");
                                    this.owner.setBusy(false);
                                }
                            });
                            break block133;
                        }
                        if (!command.equals("close") && !command.equals("open")) break block142;
                        switch (this.object.getID()) {
                            case 58: {
                                this.replaceGameObject(57, false);
                                return;
                            }
                            case 57: {
                                this.replaceGameObject(58, true);
                                return;
                            }
                            case 63: {
                                this.replaceGameObject(64, false);
                                return;
                            }
                            case 64: {
                                this.replaceGameObject(63, true);
                                return;
                            }
                            case 79: {
                                this.replaceGameObject(78, false);
                                return;
                            }
                            case 78: {
                                this.replaceGameObject(79, true);
                                return;
                            }
                            case 60: {
                                this.replaceGameObject(59, true);
                                return;
                            }
                            case 59: {
                                this.replaceGameObject(60, false);
                                return;
                            }
                            case 660: {
                                /*
                                 * The wooden gate beside the Karamja glider
                                 * crash site, (387,760) -- its one placement
                                 * in the world. Nothing gates it: the fence
                                 * is only trees and the gate's own blocked
                                 * tiles, so it is an ordinary open-and-step-
                                 * through that simply had no case. Its 1x2
                                 * footprint lies along x, so the crossing
                                 * moves y.
                                 */
                                if (this.object.getX() != 387 || this.object.getY() != 760) {
                                    return;
                                }
                                /*
                                 * 623 is the wooden open form (the shipyard
                                 * gate's own); the doGate() default of 181 is
                                 * the metal toll gate and flashed the wrong
                                 * model here. No teleport either -- the gate
                                 * stands open long enough to walk through,
                                 * which also cannot step anyone the wrong
                                 * way.
                                 */
                                this.owner.getActionSender().sendMessage("You open the gate and walk through");
                                this.owner.getActionSender().sendSound("opendoor");
                                world.registerGameObject(new GameObject(this.object.getLocation(), 623, this.object.getDirection(), this.object.getType()));
                                world.delayedSpawnObject(this.object.getLoc(), 4000);
                                break block133;
                            }
                            case 137: {
                                if (this.object.getX() != 341 || this.object.getY() != 487) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getX() <= 341) {
                                    this.owner.teleport(342, 487, false);
                                } else {
                                    this.owner.teleport(341, 487, false);
                                }
                                break block133;
                            }
                            case 138: {
                                if (this.object.getX() != 343 || this.object.getY() != 581) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 580) {
                                    this.owner.teleport(343, 581, false);
                                } else {
                                    this.owner.teleport(343, 580, false);
                                }
                                break block133;
                            }
                            case 180: {
                                if (this.object.getX() != 92 || this.object.getY() != 649) {
                                    return;
                                }
                                // The Al Kharid toll gate. It used to open for
                                // anybody, which quietly deleted both the toll
                                // and Prince Ali rescue's reward for paying it.
                                // A guard on duty now asks for the ten coins;
                                // if neither guard is on the square -- both dead
                                // or not yet spawned -- the gate opens as it did
                                // rather than shutting the road.
                                if (!BorderGuard.passesFree(this.owner)) {
                                    Npc guard = BorderGuard.guard();
                                    if (guard != null) {
                                        BorderGuard.askToPass(this.owner, guard);
                                        break block133;
                                    }
                                }
                                this.doGate();
                                if (this.owner.getX() <= 91) {
                                    this.owner.teleport(92, 649, false);
                                } else {
                                    this.owner.teleport(91, 649, false);
                                }
                                break block133;
                            }
                            case 254: {
                                if (this.object.getX() != 434 || this.object.getY() != 682) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getX() <= 434) {
                                    this.owner.teleport(435, 682, false);
                                } else {
                                    this.owner.teleport(434, 682, false);
                                }
                                break block133;
                            }
                            case 563: {
                                if (this.object.getX() != 660 || this.object.getY() != 551) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 551) {
                                    this.owner.teleport(660, 552, false);
                                } else {
                                    this.owner.teleport(660, 551, false);
                                }
                                break block133;
                            }
                            case 626: {
                                if (this.object.getX() != 703 || this.object.getY() != 531) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 531) {
                                    this.owner.teleport(703, 532, false);
                                } else {
                                    this.owner.teleport(703, 531, false);
                                }
                                break block133;
                            }
                            case 305: {
                                if (this.object.getX() != 196 || this.object.getY() != 3266) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 3265) {
                                    this.owner.teleport(196, 3266, false);
                                } else {
                                    this.owner.teleport(196, 3265, false);
                                }
                                break block133;
                            }
                            /*
                             * Farmer Brumty's field gate, north of Ardougne.
                             * It carries "open" and did nothing at all, which
                             * made Sheep herder unfinishable: the cattle prod
                             * spawns at (597,543), inside the fence, and this
                             * is the only break in it. There is one placement
                             * of object 443 in the world and the fence it sits
                             * in runs north to south, so the crossing is
                             * between 588 and 589.
                             */
                            case 443: {
                                if (this.object.getX() != 588 || this.object.getY() != 540) {
                                    return;
                                }
                                // 442 is this picket gate's own open form, not
                                // the toll-gate arch the default would draw.
                                this.doGate(442);
                                if (this.owner.getX() <= 588) {
                                    this.owner.teleport(589, 540, false);
                                } else {
                                    this.owner.teleport(588, 540, false);
                                }
                                break block133;
                            }
                            case 1089: {
                                if (this.object.getX() != 59 || this.object.getY() != 573) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getX() <= 58) {
                                    this.owner.teleport(59, 573, false);
                                } else {
                                    this.owner.teleport(58, 573, false);
                                }
                                break block133;
                            }
                            /*
                             * The main gate into McGrubor's wood, which does
                             * not open. It does not open from either side:
                             * the Foresters turn everybody back, and the
                             * loose fence panel on the east side is the only
                             * way in or out.
                             *
                             * RSCD had made this a woodcutting 70 gate,
                             * guarded by npc 255 -- Grubor, who is a Hero's
                             * Quest contact in Brimhaven and was spawned a
                             * second time here for the pun on the name. That
                             * spawn is gone and so is the level check.
                             */
                            case 356: {
                                if (this.object.getX() != 560 || this.object.getY() != 472) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                final Npc forester = world.getNpc(348, 553, 566, 468, 480);
                                if (forester != null) {
                                    this.owner.informOfNpcMessage(new ChatMessage(forester, "Hey you can't come through here", this.owner));
                                }
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.setBusy(false);
                                        if (forester != null) {
                                            this.owner.informOfNpcMessage(new ChatMessage(forester, "This is private land", this.owner));
                                        }
                                        this.owner.getActionSender().sendMessage("@gry@ You will need to find another way in");
                                    }
                                });
                                break block133;
                            }
                            case 1191: {
                                if (this.owner.getInventory().countId(382) > 0 && this.owner.isTeam1() || this.owner.getInventory().countId(391) > 1 && this.owner.isTeam1()) {
                                    if (this.owner.getInventory().countId(382) > 1 && this.owner.isTeam1() || this.owner.getInventory().countId(391) > 0 && this.owner.isTeam1()) {
                                        for (Player p : world.getPlayers()) {
                                            if (this.owner.isTeam1()) {
                                                this.owner.teleport(70, 1640, true);
                                                for (InvItem item : this.owner.getInventory().getItems()) {
                                                    if (!item.isWielded()) continue;
                                                    item.setWield(false);
                                                    this.owner.updateWornItems(item.getWieldableDef().getWieldPos(), this.owner.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                                                }
                                                this.owner.removeSkull();
                                                this.owner.getInventory().getItems().clear();
                                                this.owner.getActionSender().sendInventory();
                                                if (p != this.owner) {
                                                    if (p.getLocation().inArena()) {
                                                        p.teleport(70, 1640, true);
                                                        for (InvItem item : p.getInventory().getItems()) {
                                                            if (!item.isWielded()) continue;
                                                            item.setWield(false);
                                                            p.updateWornItems(item.getWieldableDef().getWieldPos(), p.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                                                        }
                                                        p.removeSkull();
                                                        p.getInventory().getItems().clear();
                                                        p.getActionSender().sendInventory();
                                                        p.getActionSender().sendMessage(this.owner.getUsername() + " of Team 1 has won the game");
                                                    }
                                                } else {
                                                    this.owner.getActionSender().sendMessage(this.owner.getUsername() + " of Team 1 has won the game");
                                                }
                                            } else if (this.owner.isTeam2()) {
                                                this.owner.teleport(70, 1640, true);
                                                for (InvItem item : this.owner.getInventory().getItems()) {
                                                    if (!item.isWielded()) continue;
                                                    item.setWield(false);
                                                    this.owner.updateWornItems(item.getWieldableDef().getWieldPos(), this.owner.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                                                }
                                                this.owner.removeSkull();
                                                this.owner.getInventory().getItems().clear();
                                                this.owner.getActionSender().sendInventory();
                                                if (p != this.owner) {
                                                    if (p.getLocation().inArena()) {
                                                        p.teleport(70, 1640, true);
                                                        for (InvItem item : p.getInventory().getItems()) {
                                                            if (!item.isWielded()) continue;
                                                            item.setWield(false);
                                                            p.updateWornItems(item.getWieldableDef().getWieldPos(), p.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                                                        }
                                                        p.removeSkull();
                                                        p.getInventory().getItems().clear();
                                                        p.getActionSender().sendInventory();
                                                        p.getActionSender().sendMessage(this.owner.getUsername() + " of Team 2 has won the game");
                                                    }
                                                } else {
                                                    this.owner.getActionSender().sendMessage(this.owner.getUsername() + " of Team 2 has won the game");
                                                }
                                            }
                                            GameObject rail1 = new GameObject(Point.location(228, 134), 5, 1, 1);
                                            world.registerDoor(rail1);
                                            GameObject rail2 = new GameObject(Point.location(228, 133), 5, 1, 1);
                                            world.registerDoor(rail2);
                                            GameObject rail3 = new GameObject(Point.location(228, 132), 5, 1, 1);
                                            world.registerDoor(rail3);
                                            GameObject rail4 = new GameObject(Point.location(228, 131), 5, 1, 1);
                                            world.registerDoor(rail4);
                                            GameObject rail5 = new GameObject(Point.location(228, 130), 5, 1, 1);
                                            world.registerDoor(rail5);
                                            GameObject rail6 = new GameObject(Point.location(228, 129), 5, 1, 1);
                                            world.registerDoor(rail6);
                                            GameObject rail7 = new GameObject(Point.location(228, 128), 5, 1, 1);
                                            world.registerDoor(rail7);
                                            GameObject rail8 = new GameObject(Point.location(228, 127), 5, 1, 1);
                                            world.registerDoor(rail8);
                                            GameObject rail9 = new GameObject(Point.location(228, 126), 5, 1, 1);
                                            world.registerDoor(rail9);
                                            GameObject rail10 = new GameObject(Point.location(229, 134), 5, 1, 1);
                                            world.registerDoor(rail10);
                                            GameObject rail11 = new GameObject(Point.location(229, 133), 5, 1, 1);
                                            world.registerDoor(rail11);
                                            GameObject rail12 = new GameObject(Point.location(229, 132), 5, 1, 1);
                                            world.registerDoor(rail12);
                                            GameObject rail13 = new GameObject(Point.location(229, 131), 5, 1, 1);
                                            world.registerDoor(rail13);
                                            GameObject rail14 = new GameObject(Point.location(229, 130), 5, 1, 1);
                                            world.registerDoor(rail14);
                                            GameObject rail15 = new GameObject(Point.location(229, 129), 5, 1, 1);
                                            world.registerDoor(rail15);
                                            GameObject rail16 = new GameObject(Point.location(229, 128), 5, 1, 1);
                                            world.registerDoor(rail16);
                                            GameObject rail17 = new GameObject(Point.location(229, 127), 5, 1, 1);
                                            world.registerDoor(rail17);
                                            GameObject rail18 = new GameObject(Point.location(229, 126), 5, 1, 1);
                                            world.registerDoor(rail18);
                                            GameObject door1 = new GameObject(Point.location(237, 127), 215, 0, 1);
                                            world.registerDoor(door1);
                                            GameObject door2 = new GameObject(Point.location(237, 126), 215, 0, 1);
                                            world.registerDoor(door2);
                                            GameObject door3 = new GameObject(Point.location(234, 130), 216, 1, 1);
                                            world.registerDoor(door3);
                                            GameObject door4 = new GameObject(Point.location(223, 130), 217, 1, 1);
                                            world.registerDoor(door4);
                                            GameObject door5 = new GameObject(Point.location(219, 127), 214, 0, 1);
                                            world.registerDoor(door5);
                                            GameObject door6 = new GameObject(Point.location(219, 126), 214, 0, 1);
                                            world.registerDoor(door6);
                                        }
                                    } else {
                                        this.owner.getActionSender().sendMessage("You need your opponents team key to finish!");
                                    }
                                }
                                break block133;
                            }
                            case 142: {
                                this.owner.getActionSender().sendMessage("@gry@ The doors are locked");
                                break block133;
                            }
                            case 93: {
                                if (this.object.getX() != 140 || this.object.getY() != 180) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 180) {
                                    this.owner.teleport(140, 181, false);
                                } else {
                                    this.owner.teleport(140, 180, false);
                                }
                                break block133;
                            }
                            case 508: {
                                if (this.object.getX() != 285 || this.object.getY() != 185) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getX() <= 284) {
                                    this.owner.teleport(285, 185, false);
                                } else {
                                    this.owner.teleport(284, 185, false);
                                }
                                break block133;
                            }
                            case 319: {
                                if (this.object.getX() != 243 || this.object.getY() != 178) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 178) {
                                    this.owner.teleport(243, 179, false);
                                } else {
                                    this.owner.teleport(243, 178, false);
                                }
                                break block133;
                            }
                            case 712: {
                                if (this.object.getX() != 394 || this.object.getY() != 851) {
                                    return;
                                }
                                this.owner.teleport(383, 851, false);
                                break block133;
                            }
                            case 611: {
                                if (this.object.getX() != 388 || this.object.getY() != 851) {
                                    return;
                                }
                                this.owner.teleport(394, 851, false);
                                break block133;
                            }
                            case 1079: {
                                if (this.object.getX() != 512 || this.object.getY() != 550) {
                                    return;
                                }
                                this.doGate();
                                if (this.owner.getY() <= 550) {
                                    this.owner.teleport(513, 551, false);
                                } else {
                                    this.owner.teleport(513, 550, false);
                                }
                                break block133;
                            }
                            default: {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                        }
                    }
                    if (command.equals("pick") || command.equals("pick banana")) {
                        switch (this.object.getID()) {
                            case 72: {
                                this.owner.getActionSender().sendMessage("@pnk@ You get some grain");
                                this.owner.getInventory().add(new InvItem(29, 1));
                                break;
                            }
                            case 191: {
                                this.owner.getActionSender().sendMessage("@pnk@ You pick a potato");
                                this.owner.getInventory().add(new InvItem(348, 1));
                                break;
                            }
                            case 313: {
                                this.owner.getActionSender().sendMessage("@pnk@ You uproot a flax plant");
                                this.owner.getInventory().add(new InvItem(675, 1));
                                break;
                            }
                            case 183: {
                                this.owner.getActionSender().sendMessage("@pnk@ You pull a banana off the tree");
                                this.owner.getInventory().add(new InvItem(249, 1));
                                break;
                            }
                            default: {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                        }
                        this.owner.getActionSender().sendInventory();
                        this.owner.getActionSender().sendSound("potato");
                        this.owner.setBusy(true);
                        world.getDelayedEventHandler().add(new SingleEvent(this.owner, 200){

                            public void action() {
                                this.owner.setBusy(false);
                            }
                        });
                    } else if (command.equals("mine") || command.equals("prospect")) {
                        /* The ten Daconia rocks in the Grand Tree dungeon carry
                           Mine and Prospect and yield nothing to either. The
                           rock the quest wants is inside a root, not in these.
                           They were missing from the world file entirely. */
                        if (this.object.getID() == 699) {
                            this.owner.getActionSender().sendMessage("@gry@ Nothing interesting happens");
                            return;
                        }
                        this.handleMining(click);
                    } else if (command.equals("lure") || command.equals("bait") || command.equals("net") || command.equals("harpoon") || command.equals("cage")) {
                        this.handleFishing(click);
                    } else if (command.equals("chop")) {
                        this.handleWoodcutting(click);
                    } else if (command.equals("hit")) {
                        this.handleDummy();
                    } else if (command.equals("recharge at")) {
                        int maxPray;
                        this.owner.getActionSender().sendMessage("@pnk@ You recharge at the altar.");
                        this.owner.getActionSender().sendSound("recharge");
                        /* Two altars recharge past the cap: the monastery's
                           Monks Altar (200) and the Heroes guild's Altar of
                           Guthix (235), both to max+2. */
                        int n = maxPray = this.object.getID() == 200 || this.object.getID() == 235 ? this.owner.getMaxStat(5) + 2 : this.owner.getMaxStat(5);
                        if (this.owner.getCurStat(5) < maxPray) {
                            this.owner.setCurStat(5, maxPray);
                        }
                        this.owner.getActionSender().sendStat(5);
                    } else if (command.equals("board")) {
                        this.owner.getActionSender().sendMessage("@pnk@ You must talk to the owner about this.");
                    } else {
                        switch (this.object.getID()) {
                            case 613: {
                                if (this.object.getX() != 384 || this.object.getY() != 851) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ You search for a way over the cart");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ You climb across");
                                        if (this.owner.getX() <= 383) {
                                            this.owner.teleport(386, 851, false);
                                        } else {
                                            this.owner.teleport(383, 851, false);
                                        }
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            case 643: {
                                if (this.object.getX() != 416 || this.object.getY() != 161) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ You twist the stone tile to one side");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ It reveals a ladder, you climb down");
                                        this.owner.teleport(703, 3284, false);
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            case 890: {
                                if (this.object.getX() != 766 || this.object.getY() != 585) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ you climb the pile of mud");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ it leads to an old stair way");
                                        this.owner.teleport(773, 3417, false);
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            case 633: {
                                if (this.object.getX() != 581 || this.object.getY() != 3525) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ You climb up the dirty rubble...");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ You just hope this is the last rubble to climb...");
                                        this.owner.teleport(501, 3425, false);
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            case 638: {
                                if (this.object.getX() != 701 || this.object.getY() != 3280) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ You push the roots");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ They wrap around you and drag you forwards");
                                        this.owner.teleport(701, 3278, false);
                                        this.owner.setBusy(false);
                                    }
                                });
                            }
                            case 639: {
                                if (this.object.getX() != 701 || this.object.getY() != 3279) {
                                    return;
                                }
                                this.owner.setBusy(true);
                                this.owner.getActionSender().sendMessage("@pnk@ You push the roots");
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@pnk@ They wrap around you and drag you forwards");
                                        this.owner.teleport(701, 3281, false);
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            // Object 1155 -- the Mage Arena's magic pool -- used to be
                            // handled here, and teleported the player to (555,555):
                            // a placeholder on the surface, nowhere near the chamber
                            // the pool is supposed to reach, with two invented
                            // messages in place of the recorded ones. MageArena.java
                            // owns it now, along with the return pool, the barrier,
                            // both gates and the three god stones, and a quest that
                            // has claimed an object takes it outright before this
                            // switch is ever reached. Removed rather than left
                            // shadowed so it cannot come back if that claim ever
                            // lapses.
                            default: {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                        }
                    }
                }
            }

            /**
             * Steal from a stall or a counter.
             *
             * Vanilla asks one more question than this does: the thief must be
             * out of the stallholder's line of sight and out of any nearby
             * guard's. RSCD has no line-of-sight test of any kind, so the
             * question cannot be asked and is not faked -- what stands in for
             * it is the ordinary Thieving roll, which is the same roll a pocket
             * gets. Failing brings the nearest guard, knight, paladin or hero
             * down on the thief exactly as vanilla does, so the consequence
             * survives even though the trigger is coarser. This is the largest
             * knowing deviation in the skill.
             */
            private void handleStall() {
                final ObjectStallDef def = EntityHandler.getObjectStallDef(this.object.getID());
                if (def == null) {
                    return;
                }
                // The four ogre counters in Gu'tanoth. They carry the command
                // and hold nothing, and this is the message vanilla gives.
                if (def.getLoot().isEmpty()) {
                    this.owner.getActionSender().sendMessage("@gry@ You find nothing to steal");
                    return;
                }
                if (this.owner.getCurStat(THIEVING) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need a thieving level of " + def.getReqLevel() + " to steal from this stall");
                    return;
                }
                if (this.owner.getInventory().full()) {
                    this.owner.getActionSender().sendMessage("@gry@ You don't have room for that");
                    return;
                }
                final String stall = this.object.getGameObjectDef().getName().toLowerCase();
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ You attempt to steal from the " + stall);
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        if (Formulae.catchThief(this.owner.getCurStat(THIEVING), def.getReqLevel())) {
                            this.owner.getActionSender().sendMessage("@gry@ You fail to steal from the " + stall);
                            ObjectAction.raiseTheGuard(this.owner, def.getReqLevel());
                            return;
                        }
                        InvItem stolen = ObjectAction.draw(def.getLoot());
                        this.owner.getInventory().add(stolen);
                        this.owner.getActionSender().sendInventory();
                        this.owner.getActionSender().sendMessage(
                            "@pnk@ You steal " + stolen.getDef().getName().toLowerCase());
                        if (def.getExp() > 0) {
                            this.owner.incExp(THIEVING, def.getExp(), true);
                            this.owner.getActionSender().sendStat(THIEVING);
                        }
                        if (def.getEmptyId() >= 0) {
                            world.registerGameObject(new GameObject(object.getLocation(), def.getEmptyId(),
                                object.getDirection(), object.getType()));
                            world.delayedSpawnObject(object.getLoc(), def.getRespawnTime() * 1000);
                        }
                    }
                });
            }

            /**
             * Cross an Agility obstacle.
             *
             * An obstacle is a crossing between two named tiles, so the whole
             * of it is: work out which end the player is standing on, roll for
             * a fall if the obstacle can throw people, and put them down on the
             * other end. Which end they are on is decided by distance rather
             * than by a side test, because several of these cross a floor --
             * the gnome watch tower's two ends are 944 tiles apart in y and
             * nothing about the landscape says which is "the far side".
             *
             * A failed obstacle in Classic drops the player into whatever is
             * underneath -- a spiked pit, water, a wolf pit. None of those
             * landing spots were recorded, so this server leaves the player
             * where they were and takes the damage instead.
             */
            /**
             * The three trapped rocks between the cage gate and the furnace
             * room and the three on the far side of it, scenery 806-811 -- "step over" and "search". Each one
             * hides a trap: searched first, it is disarmed and the step-over
             * falls through to the agility crossing below; stepped on blind,
             * it springs and hurts. Disarming is per player and per rock.
             */
            private boolean handleTrapRocks() {
                final int id = this.object.getID();
                if ((id < 806 || id > 811) && id != 882 && id != 883) {
                    return false;
                }
                String key = "pass.trap." + this.object.getX() + "." + this.object.getY();
                if (click == 1) {
                    this.owner.getActionSender().sendMessage("@pnk@ you search the rocks");
                    this.owner.getActionSender().sendMessage("@pnk@ you find a trap deviously hidden in the rocks");
                    this.owner.getActionSender().sendMessage("@pnk@ you disarm the trap");
                    this.owner.setFlag(key, 1);
                    return true;
                }
                if (click == 0 && this.owner.getFlag(key) == 0) {
                    int damage = Math.max(1, this.owner.getCurStat(HITS) / 5);
                    this.owner.getActionSender().sendMessage("@pnk@ you step onto the rocks");
                    this.owner.getActionSender().sendMessage("@pnk@ a hidden trap springs and spikes shoot up");
                    this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                    this.owner.getActionSender().sendStat(HITS);
                    return true;
                }
                return false;
            }

            /**
             * The flat-rock Passage north of the well area (825/828/829), the
             * way to the northern unicorn orb. The rocks are pressure traps:
             * the only safe crossing is laying a plank over them (Use plank,
             * handled by the agility entries), and the plank is spent each
             * way. Searching the passage instead sets the trap off -- flames
             * throw the player back to the south mouth whichever end they
             * poked it from.
             */
            private boolean handlePassage() {
                final int id = this.object.getID();
                if ((id != 825 && id != 828 && id != 829) || click != 1) {
                    return false;
                }
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ you search the passage");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner) {

                    public void action() {
                        this.owner.setBusy(false);
                        int damage = Math.max(1, this.owner.getCurStat(HITS) / 5);
                        this.owner.getActionSender().sendMessage("@pnk@ you stand on a pressure trigger");
                        this.owner.getActionSender().sendMessage("@pnk@ flames shoot up from below and throw you from the passage");
                        this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                        this.owner.getActionSender().sendStat(HITS);
                        this.owner.teleport(728, 3441, false);
                    }
                });
                return true;
            }

            /**
             * The seven inscribed rocks of the Underground Pass. They carry
             * "read" and deliver the quest's whole story: Iban scripture on
             * the way in, Kardia's journal on the platforms, and the tomb
             * warning that hints at the brew and the tinderbox.
             */
            /*
             * The Coal Trucks (383). Coal used on any truck at the mine east
             * of McGrubor's Wood rides the track to the pair west of Seers'
             * Village bank; the load belongs to the player, not the truck, so
             * every truck answers with the same count. It is kept in the
             * quest-stage store under an id no quest will ever have (3000), so
             * it survives logout like banked coal should. The classic
             * capacity is 121, and withdrawal is one lump per click. The
             * deposit half lives in InvUseOnObject.
             */
            private boolean handleCoalTruck() {
                if (this.object.getID() != 383 || click != 0) {
                    return false;
                }
                int stored = Math.max(0, this.owner.getQuestStage(3000));
                if (stored == 0) {
                    this.owner.getActionSender().sendMessage("@gry@ The truck is empty");
                    return true;
                }
                if (this.owner.getInventory().full()) {
                    this.owner.getActionSender().sendMessage("@gry@ Your inventory is too full to take any coal");
                    return true;
                }
                this.owner.setQuestStage(3000, stored - 1);
                this.owner.getInventory().add(new InvItem(155, 1));
                this.owner.getActionSender().sendInventory();
                this.owner.getActionSender().sendMessage("@pnk@ You get some coal from the truck");
                return true;
            }

            private boolean handleLoreRocks() {
                final int id = this.object.getID();
                String[] text;
                switch (id) {
                    case 832: text = new String[] {
                        "All those who thirst for knowledge, Bow down to the lord",
                        "All you that crave eternal life, Come and meet your God",
                        "For no man nor beast can cast a spell",
                        "Against the wake of eternal hell" }; break;
                    case 833: text = new String[] {
                        "Most men do live in fear of death, That it might steal their soul",
                        "Some work and pray to shield their life, From the ravages of the cold",
                        "But only those who embrace the end, Can truly make their life extend",
                        "And when all hope begins to fade",
                        "look above and use nature as your aid" }; break;
                    case 834: text = new String[] {
                        "And now our God has given us, One who is from our own",
                        "A saviour who once sat upon, His father's glorious thrown",
                        "It is in your name that we will lead the attack",
                        "Iban son of Zamorak!" }; break;
                    case 835: text = new String[] {
                        "Here lies the sacred font, Where the great Iban will bless",
                        "all his disciples in the name of evil",
                        "Here the forces of darkness are so concentrated",
                        "they rise when they detect any positive force close by" }; break;
                    case 881: text = new String[] {
                        "Leave this battered corpse be",
                        "For now he lives as spirit alone",
                        "Let his flesh rest and become one with the earth",
                        "As it is the soil that shall rise to protect him",
                        "Only as flesh becomes dust, as wood becomes ash...",
                        "..will Iban's corpse embrace nature and finally rest" }; break;
                    case 922: text = new String[] {
                        "Crumbling some of the dove's bones onto the doll,",
                        "I cast my mind's eye onto Iban's body",
                        "My ritual was complete, soon he would be coming to life",
                        "I, Kardia, had resurrected the legendary Iban,",
                        "the most powerful evil being ever to take human form",
                        "And I alone knew that the same process that I had used",
                        "to create him, was also capable of destroying him" }; break;
                    case 923: text = new String[] {
                        "Ibans Shadow",
                        "Recreating the parts of a man that cannot be seen or",
                        "touched, I performed the ancient ritual of Incantia",
                        "Opening my eyes again, I saw the three demons that had",
                        "been summoned, standing in a triangle,",
                        "their energy focused on the doll",
                        "These demons would be the keepers of Iban's shadow",
                        "Black as night, their shared spirit would follow",
                        "his undead body like an angel of death" }; break;
                    default: return false;
                }
                if (click != 0) {
                    return false;
                }
                this.owner.getActionSender().sendMessage("@pnk@ the writing seems to have been scracthed...");
                this.owner.getActionSender().sendMessage("@pnk@ ..into the rock with bare hands, it reads..");
                for (int i = 0; i < text.length; i++) {
                    this.owner.getActionSender().sendMessage("@red@" + text[i]);
                }
                return true;
            }

            /**
             * The trip-wired boulder corridor west of the well (819-824), the
             * way to one of the four orbs. The crossings themselves live in
             * the agility table; this adds the wire. Searching warns about
             * it, and clearing the rocks brushes it roughly one time in
             * seven -- a nick of damage and the crossing is lost, but no
             * cascade: the corridor is walked rock by rock either way.
             */
            private boolean handleTripWire() {
                final int id = this.object.getID();
                if (id < 819 || id > 824) {
                    return false;
                }
                if (click == 1) {
                    this.owner.getActionSender().sendMessage("@pnk@ you search the rocks");
                    this.owner.getActionSender().sendMessage("@pnk@ you find a trip wire running through the rubble");
                    this.owner.getActionSender().sendMessage("@pnk@ best to tread carefully here");
                    return true;
                }
                if (click == 0 && Math.random() < 0.15D) {
                    this.owner.setBusy(true);
                    this.owner.getActionSender().sendMessage("@pnk@ you move the rocks from your path");
                    world.getDelayedEventHandler().add(new ShortEvent(this.owner) {

                        public void action() {
                            this.owner.setBusy(false);
                            int damage = Math.max(1, this.owner.getCurStat(HITS) / 16 + 2);
                            this.owner.getActionSender().sendMessage("@pnk@ ...but you brush against a trip wire");
                            this.owner.getActionSender().sendMessage("@pnk@ you hear a strange mechanical sound");
                            this.owner.getActionSender().sendMessage("@pnk@ spikes shoot up from between the rocks");
                            this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                            this.owner.getActionSender().sendStat(HITS);
                        }
                    });
                    return true;
                }
                return false;
            }

            /**
             * The swinging passage (815) south-west of the well. Walking down
             * it swings the whole floor away: with a rope tied to the
             * stalagmite outside (the ROPED bit of the Underground Pass quest
             * stage, 65536) the player is lowered gently into the spike pit
             * pocket; without one they land on the spikes. Either pocket
             * holds an orb of light and a climb out (816/817).
             */
            private boolean handleSwingPassage() {
                if (this.object.getID() != 815 || click != 0) {
                    return false;
                }
                final boolean roped =
                    (this.owner.getQuestStage(org.rscdaemon.server.quest.Quests.UNDERGROUND_PASS) & 65536) != 0;
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ you walk down the passage way");
                this.owner.getActionSender().sendMessage("@pnk@ the floor seems unstable");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner) {

                    public void action() {
                        this.owner.setBusy(false);
                        this.owner.getActionSender().sendMessage("@pnk@ suddenly with a huge creak the whole passage way swings down");
                        if (roped) {
                            this.owner.getActionSender().sendMessage("@pnk@ your rope saves you, slowly you lower yourself to the floor");
                            this.owner.teleport(716, 3482, false);
                            return;
                        }
                        int damage = Math.max(1, this.owner.getCurStat(HITS) / 5 + 5);
                        this.owner.getActionSender().sendMessage("@pnk@ throwing you onto a pit of spikes");
                        this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                        this.owner.getActionSender().sendStat(HITS);
                        this.owner.teleport(709, 3473, false);
                    }
                });
                return true;
            }

            /**
             * The high ledge 837 on the platforms. Its two commands part
             * ways: "climb up" is the crossing the agility table records,
             * "jump off" is a running leap that always lands short -- the
             * recovered account has no success line for it at all.
             */
            private boolean handleLedgeJump() {
                if (this.object.getID() != 837) {
                    return false;
                }
                final ObjectAgilityDef def =
                    EntityHandler.getObjectAgilityDef(this.object.getLocation(), 837);
                if (def == null) {
                    return false;
                }
                if (click == 0) {
                    this.owner.setBusy(true);
                    this.owner.getActionSender().sendMessage("@pnk@ you take a few paces back...");
                    this.owner.getActionSender().sendMessage("@pnk@ and run towards the ledge...");
                    world.getDelayedEventHandler().add(new ShortEvent(this.owner) {

                        public void action() {
                            this.owner.setBusy(false);
                            int damage = Math.max(1, this.owner.getCurStat(HITS) / 5 + 5);
                            this.owner.getActionSender().sendMessage("@pnk@ you land way short of the other platform");
                            this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                            this.owner.getActionSender().sendStat(HITS);
                            this.owner.teleport(764, 3467, false);
                        }
                    });
                    return true;
                }
                if (click == 1) {
                    this.owner.setBusy(true);
                    this.owner.getActionSender().sendMessage("@pnk@ " + def.getMessage());
                    world.getDelayedEventHandler().add(new ShortEvent(this.owner) {

                        public void action() {
                            this.owner.setBusy(false);
                            int d1 = Math.abs(this.owner.getX() - def.getX1()) + Math.abs(this.owner.getY() - def.getY1());
                            int d2 = Math.abs(this.owner.getX() - def.getX2()) + Math.abs(this.owner.getY() - def.getY2());
                            this.owner.teleport(d1 <= d2 ? def.getX2() : def.getX1(),
                                d1 <= d2 ? def.getY2() : def.getY1(), false);
                        }
                    });
                    return true;
                }
                return false;
            }

            /**
             * The minable Rocks of the Underground Pass, scenery 770. They are
             * not ore -- fourteen of them stud the first level, and mining one
             * breaks off a load of rocks (item 986, the same load the Tourist
             * Trap slaves mine) that the two swamp crossings by the old bridge
             * ask for. Not quest logic: the swamp has to be crossable for
             * anybody standing in the dungeon, quest done or not.
             *
             * A pickaxe of any metal is asked for -- the quest lists one as a
             * requirement -- but no mining level, and the pick is a tie, not
             * a cost, the same as agility's rope obstacles.
             */
            private boolean handlePassRocks() {
                if (this.object.getID() != 770 || click != 0) {
                    return false;
                }
                boolean pick = false;
                for (int id : new int[] { 156, 1258, 1259, 1260, 1261, 1262 }) {
                    if (this.owner.getInventory().countId(id) > 0) {
                        pick = true;
                        break;
                    }
                }
                if (!pick) {
                    this.owner.getActionSender().sendMessage("You need a pickaxe to mine these rocks");
                    return true;
                }
                this.owner.getActionSender().sendMessage("You hack at the rock face");
                this.owner.getActionSender().sendMessage("You break off a load of rocks");
                this.owner.getInventory().add(new InvItem(986, 1));
                this.owner.getActionSender().sendInventory();
                return true;
            }

            private boolean handleAgility() {
                final ObjectAgilityDef def = EntityHandler.getObjectAgilityDef(
                    this.object.getLocation(), this.object.getID());
                if (def == null || click != 0) {
                    return false;
                }
                if (this.owner.getCurStat(AGILITY) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need an agility level of " + def.getReqLevel() + " to do that");
                    return true;
                }
                // Agility kept the oldest fatigue rule in the game: a fully
                // fatigued player cannot cross at all, rather than crossing
                // for nothing the way a tired miner still swings his pick.
                // The recovered course table lists it obstacle by obstacle,
                // and one of them -- the Barbarian balancing ledge -- is
                // marked as not stopping, which is what ignoresFatigue is.
                // An obstacle that wants a rope rather than a skill. The item
                // is looked for and left alone: these are ties, not costs.
                /* An obstacle with both an item tie and a recorded fall point
                   is the Underground Pass swamps: stepping out onto them is
                   always the wrong move, rocks in your pack or not -- only
                   laying the rocks down (Use, in InvUseOnObject) makes the
                   path. The step washes you downstream into the chasm and you
                   climb over rocks to get out. */
                if (def.getRequiredItem() > 0 && def.hasFallPoint()) {
                    this.owner.setBusy(true);
                    this.owner.getActionSender().sendMessage("@pnk@ you step out onto the swamp");
                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                        public void action() {
                            this.owner.setBusy(false);
                            this.owner.getActionSender().sendMessage("@pnk@ you sink into the muddy water");
                            this.owner.getActionSender().sendMessage("@pnk@ you are washed away and tumble down a chasm");
                            this.owner.teleport(def.getFailX(), def.getFailY(), false);
                        }
                    });
                    return true;
                }
                if (def.getRequiredItem() > 0
                        && this.owner.getInventory().countId(def.getRequiredItem()) <= 0) {
                    this.owner.getActionSender().sendMessage("@pnk@ " + def.getRefusal());
                    return true;
                }
                if (!def.ignoresFatigue() && this.owner.getFatigue() >= 100) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You are too tired to do that, get some rest!");
                    return true;
                }
                int d1 = Math.abs(this.owner.getX() - def.getX1()) + Math.abs(this.owner.getY() - def.getY1());
                int d2 = Math.abs(this.owner.getX() - def.getX2()) + Math.abs(this.owner.getY() - def.getY2());
                if (def.isOneWay() && d1 > d2) {
                    this.owner.getActionSender().sendMessage("@gry@ You cannot get back this way");
                    return true;
                }
                final int destX = d1 <= d2 ? def.getX2() : def.getX1();
                final int destY = d1 <= d2 ? def.getY2() : def.getY1();
                // A message with a '%' in it is a two-line crossing: the part
                // before is the attempt, spoken as the player steps out, and
                // the part after is only earned on success.
                final String attempt, success;
                int cut = def.getMessage().indexOf('%');
                if (cut >= 0) {
                    attempt = def.getMessage().substring(0, cut);
                    success = def.getMessage().substring(cut + 1);
                } else {
                    attempt = def.getMessage();
                    success = null;
                }
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ " + attempt);
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        if (def.canFail() && Formulae.failAgility(this.owner.getCurStat(AGILITY),
                                def.getReqLevel(), def.getFailStopLvl())) {
                            int damage = Math.max(1, this.owner.getCurStat(HITS) / 5);
                            this.owner.getActionSender().sendMessage("@pnk@ You slip and fall");
                            this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                            this.owner.getActionSender().sendStat(HITS);
                            this.owner.resetAgilityProgress();
                            // Where the fall lands, for the few obstacles whose
                            // landing spot is recorded. The rest leave the
                            // player standing where they were.
                            if (def.hasFallPoint()) {
                                this.owner.teleport(def.getFailX(), def.getFailY(), false);
                            }
                            if (def.getFailExp() > 0) {
                                this.owner.incExp(AGILITY, def.getFailExp(), true);
                                this.owner.getActionSender().sendStat(AGILITY);
                            }
                            return;
                        }
                        this.owner.teleport(destX, destY, false);
                        if (success != null) {
                            this.owner.getActionSender().sendMessage("@pnk@ " + success);
                        }
                        ObjectAction.awardAgility(this.owner, def.getCourseId(), def.getStage(),
                            def.getExp(), def.hasLapBonus() ? def.getLapExp() : 0, def.ignoresFatigue());
                        ObjectAction.ibanWhisper(this.owner);
                    }
                });
                return true;
            }

            /**
             * The twenty-five grills of the Underground Pass.
             *
             * A five by five grid of metal grills set in the walls of one room,
             * west of the swamp. Twenty-four of them are ordinary crossings
             * from one column of the room to the next; seventeen of those give
             * way and drop the player onto spikes. The recovered account is
             * plain that the safe route is the same for every player, which is
             * the opposite of how the same puzzle works in later RuneScape,
             * and that a wrong grill costs damage and the whole room.
             *
             * WHICH SEVENTEEN was not recorded anywhere, and it did not have to
             * be: the landscape says so. Seventeen grills share the id 782 and
             * the remaining eight carry ids of their own -- 777, 785, 786, 787,
             * 788, 789, 790 and 791 -- and those eight are exactly a connected
             * walk from the west door of the room to the east column where the
             * lever is. Column by column:
             *
             *     in from the west   777 at (680,3447)
             *     first to second    785 at (682,3447), 786 at (682,3449)
             *     second to third    787 at (684,3449)
             *     third to fourth    788 at (686,3449), 789 at (686,3451),
             *                        790 at (686,3453)
             *     fourth to fifth    791 at (688,3453)
             *
             * Every hop is covered and nothing is stranded, which no random
             * eight of twenty-five would be. The five columns are five separate
             * regions of the collision map with no other way between them, so
             * the room really is a maze and this really is its solution.
             *
             * Grills are wall-shaped and all face the same way, so a crossing
             * is always east-west: the tile one to the west of the grill and
             * the tile one to the east of it.
             */
            private boolean handleGrill() {
                int id = this.object.getID();
                boolean safe = false;
                for (int n = 0; n < GRILL_SAFE.length; ++n) {
                    if (GRILL_SAFE[n] == id) {
                        safe = true;
                        break;
                    }
                }
                if (!safe && id != GRILL_TRAP) {
                    return false;
                }
                final int west = this.object.getX() - 1, east = this.object.getX() + 1;
                final int y = this.object.getY();
                final boolean crosses = safe;
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ you step onto the metal grill");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        if (!crosses) {
                            this.owner.getActionSender().sendMessage("@pnk@ it's a trap");
                            this.owner.getActionSender().sendMessage("@pnk@ you fall onto a pit of spikes");
                            int damage = Math.max(1, this.owner.getCurStat(HITS) / 5);
                            this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                            this.owner.getActionSender().sendStat(HITS);
                            this.owner.getActionSender().sendMessage("@pnk@ you crawl out of the pit");
                            this.owner.teleport(GRILL_START_X, GRILL_START_Y, false);
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ you tread carefully as you move forward");
                        this.owner.getActionSender().sendMessage("@pnk@ and off the metal grill");
                        this.owner.teleport(this.owner.getX() <= west ? east : west, y, false);
                    }
                });
                return true;
            }

            /*
             * The Shantay Pass -- the desert border south of Al-Kharid. Object
             * 916 ("Stone Gate") is the real object at that location, found in
             * GameObjectLoc.xml.gz; an earlier pass at this gap mistook object
             * 176 ("Rocks", a plain scenery id that happens to also sit nearby)
             * for the gate. Crossing north, out of the desert, is always free.
             *
             * Crossing south is the guard's business, not the gate's: he reads
             * you the poster, takes the pass off you and hands you Shantay's
             * disclaimer. All of that is in npchandler/ShantayPass, which the
             * guard standing on the gate runs too -- clicking the gate and
             * asking him to let you through are the same sequence.
             */
            /**
             * Shake a Leafy Palm Tree for the palm leaf that wraps oomlie
             * meat. The 39 of them stand only in the Kharazi Jungle. While
             * shaken the tree spends about twenty seconds as an ordinary
             * PalmTree (scenery 33 -- the id a mapping replay pinned down)
             * with nothing to give, then comes back leafy; that swap is the
             * cooldown, exactly as the wiki's cited replay shows it.
             */
            private boolean handleLeafyPalm() {
                if (this.object.getID() != 1176) {
                    return false;
                }
                if (this.owner.getInventory().full()) {
                    this.owner.getActionSender().sendMessage("@gry@ Your inventory is full");
                    return true;
                }
                this.owner.getActionSender().sendMessage("@pnk@ You give the palm tree a good shake");
                this.owner.getInventory().add(new InvItem(1279, 1));
                this.owner.getActionSender().sendMessage("@pnk@ A palm leaf falls from the tree");
                this.owner.getActionSender().sendInventory();
                world.registerGameObject(new GameObject(this.object.getLocation(), 33,
                    this.object.getDirection(), this.object.getType()));
                world.delayedSpawnObject(this.object.getLoc(), 20000);
                return true;
            }

            /**
             * The Grave of Scorpius, scenery 941 at (694,649), north of the
             * Observatory. Its first command is "Read" and nothing answered
             * it, so the headstone that tells you the Spirit is there to be
             * found said nothing at all.
             *
             * The three lines are the whole of Transcript:Grave of Scorpius.
             * "Only those who have seen beyond the stars" is the quest gate
             * stated in the fiction: seeing beyond the stars is the telescope,
             * and npchandler/SpiritOfScorpius enforces the same rule.
             */
            private boolean handleGrave() {
                if (this.object.getID() != 941 || click != 0) {
                    return false;
                }
                this.owner.getActionSender().sendMessage("Here lies Scorpius:");
                this.owner.getActionSender().sendMessage("Only those who have seen beyond the stars");
                this.owner.getActionSender().sendMessage("may seek his counsel");
                return true;
            }

            /**
             * The Fishing Trawler, Port Khazard. Four scenery ids carry the
             * whole minigame, and all four stood in the map with nothing
             * answering them:
             *
             *   1071/1077 "Leak"          "fill"      -- swamp paste
             *   1101/1102 "Trawler net"   "inspect"   -- rope
             *   1106      "Trawler catch" "Search"    -- the dock haul
             *   1070      "barrel"        "climb on"  -- the way home
             *
             * The leaks are the only ones this handler ever creates; the
             * rest are permanent map furniture. "Examine" is left to the
             * generic handling below, so only the first command is claimed.
             * See npchandler/FishingTrawler for the round itself.
             */
            private boolean handleTrawler() {
                if (click != 0) {
                    return false;
                }
                switch (this.object.getID()) {
                    case 1071:
                    case 1077:
                        FishingTrawler.fillLeak(this.owner, this.object);
                        return true;
                    case 1101:
                    case 1102:
                        FishingTrawler.inspectNet(this.owner, this.object);
                        return true;
                    case 1106:
                        FishingTrawler.collectCatch(this.owner);
                        return true;
                    case 1070:
                        FishingTrawler.escapeBarrel(this.owner);
                        return true;
                }
                return false;
            }

            /**
             * The two Entrana objects, both of which used to answer with
             * something generic and wrong.
             *
             * 241/242/243 are the ships on the island's dock. Jagex's own
             * description of all three is "A ship to Port Sarim", and there is
             * no npc anywhere on that dock, so "You must talk to the owner
             * about this" left the island a one-way trip. See EntranaMonks for
             * the full data behind that -- including the Karamja control, whose
             * return dock does carry an npc and so keeps the old answer.
             *
             * 244 is the dungeon ladder, which stands in exactly one place in
             * the game, and climbing it generically skipped both the monk's
             * warning and the prayer drain that is the point of it.
             *
             * "Examine" is left to the generic handling in both cases; only the
             * first command is claimed.
             */
            private boolean handleEntrana() {
                if (click != 0) {
                    return false;
                }
                switch (this.object.getID()) {
                    case 241:
                    case 242:
                    case 243:
                        EntranaMonks.boardShipHome(this.owner);
                        return true;
                    case 244:
                        EntranaMonks.climbDungeonLadder(this.owner);
                        return true;
                }
                return false;
            }

            private boolean handleShantayGate() {
                if (this.object.getID() != 916 || this.object.getX() != 62 || this.object.getY() != 733) {
                    return false;
                }
                // "Look" is the gate's second command, and what it looks like
                // is the poster nailed to it.
                if (click != 0) {
                    ShantayPass.lookAtGate(this.owner);
                    return true;
                }
                if (this.owner.getY() > 733) {
                    this.owner.teleport(62, 732, false);
                    return true;
                }
                // Guard 719 is a fixed spawn on (63,732), one tile off the
                // gate; the box is wide enough to still find him if he is ever
                // nudged off it.
                ShantayPass.cross(this.owner, world.getNpc(719, 60, 66, 729, 734));
                return true;
            }

            /**
             * The bank chest at the Shantay Pass.
             *
             * Object 942 stands in exactly one place in the game, (58,731),
             * and Shantay's own dialogue sends you to it -- "you can also use
             * our free banking services by clicking on the chest". Nothing
             * answered the click, so the only bank south of Al-Kharid was a
             * box that did nothing. Its "Examine" is left to the generic
             * handling below; only "Open" is claimed here.
             *
             * The two lines are from the recovered sequence: the chest is not
             * a bank, it is Shantay's men carrying your things to one.
             */
            private boolean handleBankChest() {
                if (this.object.getID() != 942 || click != 0) {
                    return false;
                }
                new Conversation(this.owner, null)
                    .message("This chest is used by Shantay and his men.")
                    .message("They can put things in and out of storage for you.")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            Player p = c.getPlayer();
                            c.stop();
                            p.setAccessingBank(true);
                            p.getActionSender().showBank();
                        }
                    })
                    .start();
                return true;
            }

            /**
             * Open a chest the Thieving way.
             *
             * Both commands land here. Five chests are trapped and carry
             * "search for traps"; the Hemenster one is not trapped, carries
             * "picklock", and wants a lockpick.
             *
             * There is no failure roll. The recovered transcript of the
             * Nature-Rune chest runs search, find, disable, open and reward with
             * no branch, and the level is the whole gate -- so this is not a
             * simplification of a roll that existed, it is the absence of one.
             * The whole table is handed over at once for the same reason: a
             * chest is not a pocket.
             */
            private void handleChest() {
                final ObjectChestDef def = EntityHandler.getObjectChestDef(this.object.getID());
                if (def == null) {
                    return;
                }
                if (this.owner.getCurStat(THIEVING) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need a thieving level of " + def.getReqLevel() + " to open this chest");
                    return;
                }
                if (def.needsLockpick() && this.owner.getInventory().countId(LOCKPICK) <= 0) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a lockpick to open this chest");
                    return;
                }
                if (this.owner.getInventory().getRequiredSlots(ObjectAction.asItems(def.getLoot()))
                        > 30 - this.owner.getInventory().size()) {
                    this.owner.getActionSender().sendMessage("@gry@ You don't have room for that");
                    return;
                }
                this.owner.setBusy(true);
                if (def.needsLockpick()) {
                    this.owner.getActionSender().sendMessage("@pnk@ You attempt to pick the lock");
                } else {
                    this.owner.getActionSender().sendMessage("@pnk@ You search the chest for traps");
                    this.owner.getActionSender().sendMessage("@pnk@ You find a trap on the chest");
                }
                // The chest changes the moment the trap is found, not when the
                // loot comes out, so a second thief walking up cannot start on
                // one already being worked.
                world.registerGameObject(new GameObject(this.object.getLocation(), def.getSearchedId(),
                    this.object.getDirection(), this.object.getType()));
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        if (!def.needsLockpick()) {
                            this.owner.getActionSender().sendMessage("@pnk@ You disable the trap");
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You open the chest");
                        this.owner.getActionSender().sendMessage("@pnk@ You find treasure inside!");
                        for (ThievingLoot l : def.getLoot()) {
                            this.owner.getInventory().add(new InvItem(l.getID(),
                                DataConversions.random(l.getAmountLow(), l.getAmountHigh())));
                        }
                        this.owner.getActionSender().sendInventory();
                        this.owner.incExp(THIEVING, def.getExp(), true);
                        this.owner.getActionSender().sendStat(THIEVING);
                        this.owner.setBusy(false);
                        // Open for a moment, then back to searched for the rest
                        // of the wait. Both states are objects with no place of
                        // their own in the landscape, which is what an object
                        // that exists only at runtime looks like.
                        world.registerGameObject(new GameObject(object.getLocation(), def.getOpenId(),
                            object.getDirection(), object.getType()));
                        world.getDelayedEventHandler().add(new SingleEvent(null, 1800){

                            public void action() {
                                world.registerGameObject(new GameObject(object.getLocation(), def.getSearchedId(),
                                    object.getDirection(), object.getType()));
                            }
                        });
                        world.delayedSpawnObject(object.getLoc(), def.getRespawnTime() * 1000);
                    }
                });
            }

            /**
             * Open a trapped chest without searching it first.
             *
             * "Attempting to open the chest without searching it for traps will
             * cause damage to the player." How much damage is not recorded
             * anywhere, so a tenth of the thief's maximum hits is this server's
             * figure and not Jagex's.
             * The Hemenster chest is not trapped and simply says so.
             */
            private void forceChest() {
                ObjectChestDef def = EntityHandler.getObjectChestDef(this.object.getID());
                if (def.needsLockpick()) {
                    this.owner.getActionSender().sendMessage("@gry@ The chest is locked");
                    return;
                }
                int damage = Math.max(1, this.owner.getMaxStat(HITS) / 10);
                this.owner.getActionSender().sendMessage("@pnk@ You set off a trap on the chest");
                this.owner.setCurStat(HITS, Math.max(0, this.owner.getCurStat(HITS) - damage));
                this.owner.getActionSender().sendStat(HITS);
            }

            /**
             * The training dummies -- ten of them, in the house north of
             * Varrock east bank and in the Handelmort mansion. Nothing here
             * answered "hit" at all before, so a player could swing at one all
             * day and be told nothing.
             *
             * The level gate reads the CURRENT Attack level, not the maximum.
             * That is not a shortcut: pures famously drank jugs of wine to
             * knock their Attack back under 8 and keep training on dummies
             * after they should no longer have been able to, and the wiki
             * records it as something the game allowed. Reading getMaxStat
             * would quietly delete a known piece of RuneScape.
             *
             * Attack experience only -- a dummy never gave Hits.
             */
            private void handleDummy() {
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@que@You swing at the dummy");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.getActionSender().sendMessage("@que@You hit the dummy");
                        if (this.owner.getCurStat(0) < 8) {
                            /* Five experience. Nobody has a capture of the
                               number, so this is the figure the wiki carries
                               forward from the same object in the later game;
                               everything around it here is attested. */
                            this.owner.incExp(0, 5, false);
                            this.owner.getActionSender().sendStat(0);
                        } else {
                            this.owner.getActionSender().sendMessage("@que@There is nothing more you can learn from hitting a dummy");
                        }
                        this.owner.setBusy(false);
                    }
                });
            }

            /**
             * Jagex's name for a rock a player has just pulled an ore out of,
             * and for the plain grey ones that never had an ore in them. Both
             * use the rocks1/rocks2 models, so the player cannot tell them
             * apart by looking -- only by mining them.
             */
            private static final int MINED_OUT_ROCK = 98;

            /**
             * The gem a lucky swing turns up is announced by name, not as
             * "a gem". Ids are Jagex's: uncut sapphire, emerald, ruby, diamond.
             */
            private String gemFound(int gemId) {
                switch (gemId) {
                    case 157: {
                        return "@que@You just found a diamond!";
                    }
                    case 158: {
                        return "@que@You just found a ruby!";
                    }
                    case 159: {
                        return "@que@You just found an emerald!";
                    }
                }
                return "@que@You just found a sapphire!";
            }

            /**
             * What Prospect calls the ore. Jagex wrote these lower case, and
             * put an exclamation mark on the four worth getting excited about
             * -- "This rock contains gold!" against "This rock contains coal".
             * Every one of them is attested in a named replay capture.
             */
            private String prospectName(int oreId) {
                String name = new InvItem(oreId).getDef().getName().toLowerCase();
                switch (oreId) {
                    case 152:
                    case 690:
                    case 153:
                    case 154:
                    case 409: {
                        return name + "!";
                    }
                }
                return name;
            }

            private void handleProspect(final ObjectMiningDef def) {
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@que@You examine the rock for ores...");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.getActionSender().sendMessage(def == null ? "@que@You fail to find anything interesting" : "@que@This rock contains " + prospectName(def.getOreId()));
                        this.owner.setBusy(false);
                    }
                });
            }

            /*
             * Mining, rebuilt on Jagex's own wording.
             *
             * Three kinds of rock end up here. One with an ObjectMiningDef
             * holds an ore. One in the plain-rock family holds nothing but
             * still breaks off lumps. Object 98 is the stump left behind by a
             * rock that has already been mined out, and is the only one that
             * says so.
             *
             * The order matters as much as the words. Every one of them --
             * even the empty stump -- makes the player find a pickaxe, swing
             * it, and wait, before it tells them anything. The old code
             * answered "no ore available" and "you need a mining level of N"
             * instantly, up front, which let a player probe a rock without
             * ever swinging at it. Jagex gave no level message at all: too low
             * a level simply fails the roll, and the player is told they only
             * succeeded in scratching the rock. Formulae.getOre already
             * returns false below the requirement, so dropping the message
             * loses nothing but the tell.
             */
            private void handleMining(int click2) {
                final ObjectMiningDef def = EntityHandler.getObjectMiningDef(this.object.getID());
                final boolean plain = def == null && Formulae.isPlainRock(this.object.getID());
                if (click2 == 1) {
                    this.handleProspect(def);
                    return;
                }
                int axeId = -1;
                for (int id : Formulae.miningAxeIDs) {
                    if (this.owner.getInventory().countId(id) <= 0) continue;
                    axeId = id;
                    break;
                }
                if (axeId < 0) {
                    this.owner.getActionSender().sendMessage("You need a pickaxe to mine this rock");
                    return;
                }
                this.owner.setBusy(true);
                this.owner.getActionSender().sendSound("mine");
                Bubble bubble = new Bubble(this.owner, axeId);
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
                this.owner.getActionSender().sendMessage("@que@You swing your pick at the rock...");
                final int axeID = axeId;
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        if (def == null && !plain) {
                            this.owner.getActionSender().sendMessage("@que@There is currently no ore available in this rock");
                            this.owner.setBusy(false);
                            return;
                        }
                        /* A plain rock is worth the same swing a level-1 ore
                           rock is: the lumps are worthless, but the gem roll
                           behind them is not. */
                        if (Formulae.getOre(def == null ? 1 : def.getReqLevel(), this.owner.getCurStat(14), axeID)) {
                            if (Formulae.foundGem(this.owner)) {
                                int gemId = Formulae.getGem();
                                this.owner.getInventory().add(new InvItem(gemId, 1));
                                this.owner.getActionSender().sendMessage(gemFound(gemId));
                                this.owner.getActionSender().sendInventory();
                            } else if (plain) {
                                /* Nothing enters the inventory and no
                                   experience is awarded -- the lumps are
                                   flavour, and the rock does not deplete. */
                                this.owner.getActionSender().sendMessage("@que@A few lumps of uninteresting rock break off");
                            } else {
                                InvItem ore = new InvItem(def.getOreId());
                                this.owner.getInventory().add(ore);
                                this.owner.getActionSender().sendMessage("@que@You manage to obtain some " + ore.getDef().getName().toLowerCase());
                                this.owner.incExp(14, def.getExp(), true);
                                this.owner.getActionSender().sendStat(14);
                                world.registerGameObject(new GameObject(object.getLocation(), MINED_OUT_ROCK, object.getDirection(), object.getType()));
                                world.delayedSpawnObject(object.getLoc(), def.getRespawnTime() * 1000);
                                this.owner.getActionSender().sendInventory();
                            }
                        } else {
                            this.owner.getActionSender().sendMessage("@que@You only succeed in scratching the rock");
                        }
                        this.owner.setBusy(false);
                    }
                });
            }

            /**
             * The rune essence mine's two objects: the spires and the exit
             * portals. The spires are deliberately not ObjectMiningDefs --
             * essence breaks every rule the mining table encodes. The rock
             * never depletes and never respawns, a swing never fails (the
             * spire is a mass of the stuff, not ore hiding in stone), there
             * is no gem roll, and what comes off isn't decided by the rock
             * but by the miner: 30+ Mining chips off pure essence, anything
             * less gets regular. Easier to say that in code here than to
             * bend the def format around it.
             */
            private boolean handleEssenceMine() {
                if (this.object.getID() == org.rscdaemon.server.model.EssenceMine.PORTAL) {
                    if (click == 0) {
                        org.rscdaemon.server.model.EssenceMine.exitThroughPortal(this.owner);
                    }
                    return true;
                }
                if (this.object.getID() != org.rscdaemon.server.model.EssenceMine.SPIRE || click != 0) {
                    return false;
                }
                int axeId = -1;
                for (int id : Formulae.miningAxeIDs) {
                    if (this.owner.getInventory().countId(id) <= 0) continue;
                    axeId = id;
                    break;
                }
                if (axeId < 0) {
                    this.owner.getActionSender().sendMessage("You need a pickaxe to mine this rock");
                    return true;
                }
                this.owner.setBusy(true);
                this.owner.getActionSender().sendSound("mine");
                Bubble bubble = new Bubble(this.owner, axeId);
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
                this.owner.getActionSender().sendMessage("@que@You swing your pick at the rock...");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        boolean pure = this.owner.getMaxStat(14) >= 30;
                        InvItem essence = new InvItem(pure ? 1305 : 1304, 1);
                        if (this.owner.getInventory().full()) {
                            this.owner.getActionSender().sendMessage("@que@Your bag is full, you can't carry any more rocks");
                            this.owner.setBusy(false);
                            return;
                        }
                        this.owner.getInventory().add(essence);
                        this.owner.getActionSender().sendMessage("@que@You manage to obtain some " + (pure ? "pure essence" : "rune essence"));
                        this.owner.incExp(14, 20, true);
                        this.owner.getActionSender().sendStat(14);
                        this.owner.getActionSender().sendInventory();
                        this.owner.setBusy(false);
                    }
                });
                return true;
            }

            private void handleFishing(final int click2) {
                ObjectFishingDef def = EntityHandler.getObjectFishingDef(this.object.getID(), click2);
                if (def == null) {
                    return;
                }
                if (this.owner.getCurStat(10) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a fishing level of " + def.getReqLevel() + " to fish here.");
                    return;
                }
                int netId = def.getNetId();
                if (this.owner.getInventory().countId(netId) <= 0) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a " + EntityHandler.getItemDef(netId).getName() + " to catch these fish.");
                    return;
                }
                final int baitId = def.getBaitId();
                if (baitId >= 0 && this.owner.getInventory().countId(baitId) <= 0) {
                    this.owner.getActionSender().sendMessage("@gry@ You don't have any " + EntityHandler.getItemDef(baitId).getName() + " left.");
                    return;
                }
                this.owner.setBusy(true);
                this.owner.getActionSender().sendSound("fish");
                Bubble bubble = new Bubble(this.owner, netId);
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
                this.owner.getActionSender().sendMessage("@pnk@ You attempt to catch some fish");
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        ObjectFishDef def = Formulae.getFish(object.getID(), this.owner.getCurStat(10), click2);
                        if (def != null) {
                            if (baitId >= 0) {
                                int idx = this.owner.getInventory().getLastIndexById(baitId);
                                InvItem bait = this.owner.getInventory().get(idx);
                                int newCount = bait.getAmount() - 1;
                                if (newCount <= 0) {
                                    this.owner.getInventory().remove(idx);
                                } else {
                                    bait.setAmount(newCount);
                                }
                            }
                            InvItem fish = new InvItem(def.getId());
                            this.owner.getInventory().add(fish);
                            this.owner.getActionSender().sendMessage("@pnk@ You catch a " + fish.getDef().getName() + ".");
                            this.owner.getActionSender().sendInventory();
                            this.owner.incExp(10, def.getExp(), true);
                            this.owner.getActionSender().sendStat(10);
                        } else {
                            this.owner.getActionSender().sendMessage("@pnk@ You fail to catch anything.");
                        }
                        this.owner.setBusy(false);
                    }
                });
            }

            private void handleWoodcutting(int click2) {
                final ObjectWoodcuttingDef def = EntityHandler.getObjectWoodcuttingDef(this.object.getID());
                if (def == null) {
                    return;
                }
                if (this.owner.getCurStat(8) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a woodcutting level of " + def.getReqLevel() + " to axe this tree.");
                    return;
                }
                int axeId = -1;
                for (int a : Formulae.woodcuttingAxeIDs) {
                    if (this.owner.getInventory().countId(a) <= 0) continue;
                    axeId = a;
                    break;
                }
                if (axeId < 0) {
                    this.owner.getActionSender().sendMessage("@gry@ You need an axe to chop this tree down.");
                    return;
                }
                this.owner.setBusy(true);
                Bubble bubble = new Bubble(this.owner, axeId);
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
                this.owner.getActionSender().sendMessage("@pnk@ You swing your " + EntityHandler.getItemDef(axeId).getName() + " at the tree...");
                final int axeID = axeId;
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        if (Formulae.getLog(def, this.owner.getCurStat(8), axeID)) {
                            InvItem log = new InvItem(def.getLogId());
                            this.owner.getInventory().add(log);
                            this.owner.getActionSender().sendMessage("@pnk@ You get some wood.");
                            this.owner.getActionSender().sendInventory();
                            this.owner.incExp(8, def.getExp(), true);
                            this.owner.getActionSender().sendStat(8);
                            if (DataConversions.random(1, 100) <= def.getFell()) {
                                world.registerGameObject(new GameObject(object.getLocation(), 4, object.getDirection(), object.getType()));
                                world.delayedSpawnObject(object.getLoc(), def.getRespawnTime() * 1000);
                            }
                        } else {
                            this.owner.getActionSender().sendMessage("@pnk@ You slip and fail to hit the tree.");
                        }
                        this.owner.setBusy(false);
                    }
                });
            }
        });
    }
}

