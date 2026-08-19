/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.DoorAgilityDef;
import org.rscdaemon.server.entityhandling.defs.extras.DoorThievingDef;
import org.rscdaemon.server.entityhandling.defs.DoorDef;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.WalkToPointEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.npchandler.Doorman;
import org.rscdaemon.server.npchandler.ShantayPass;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.Formulae;

public class WallObjectAction
implements PacketHandler {
    public static final World world = World.getWorld();

    /** Stat slot 17. */
    private static final int THIEVING = 17, AGILITY = 16;
    private static final int LOCKPICK = 714;

    /**
     * Every "Odd looking wall" (DoorDef 22) that can actually be pushed through,
     * as x,y pairs. There are seven of them placed in the world and only these
     * five divide anything: for the other two, (634,3303) and (545,3283), the
     * landscape says both sides of the wall belong to the same room, so they are
     * decoration and keep the "nothing interesting happens" answer.
     *
     * Each of these five was checked the same way -- flood-fill the tiles either
     * side of the wall against the landscape's own wall bytes and see whether
     * the two halves are separate. All five are, and two of them shut off rooms
     * that nothing else reaches.
     */
    private static final int[][] SECRET_WALLS = {
        { 219, 3282 },   /* Varrock sewer */
        { 273,  435 },   /* Black knights' fortress, ground floor */
        { 281, 2325 },   /* Black knights' fortress, top floor */
        /* A four-tile closet at (582..583, 624..625) with a ladder in it. This
           wall is its only entrance -- without it the ladder cannot be reached
           at all. */
        { 584, 3457 },
        /* The skeleton room at (667..670, 3279..3290). It has an ordinary door
           at (668,3281) as well, so this one is the back way in rather than the
           only way. */
        { 670, 3290 },
        /* Glarial's tomb. The landscape joins the two sides of this wall, so
           strictly it divides nothing -- but the recorded quest has the player
           push through it on the way to the coffin, and a wall that answers
           "Nothing interesting happens" mid-quest reads as broken. */
        { 634, 3303 },
        /* The "unusual looking wall" (def 87, not 22) into the Kharid
           scorpion's secret room in the Taverley dungeon, the one the Seer's
           hint points at in Scorpion catcher. Def 87's only other job is
           decoration, so it shares this list and this case. */
        { 383, 3353 }
    };

    private static boolean isSecretWall(int x, int y) {
        for (int[] wall : SECRET_WALLS) {
            if (wall[0] == x && wall[1] == y) {
                return true;
            }
        }
        return false;
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        int click;
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        final GameObject object = world.getTile(p.readShort(), p.readShort()).getDoor();
        int n = click = pID == 126 ? 0 : 1;
        if (object == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        player.setStatus(Action.USING_DOOR);
        world.getDelayedEventHandler().add(new WalkToPointEvent(player, object.getLocation(), 1, false){

            private void replaceGameObject(int newID, boolean open) {
                world.registerGameObject(new GameObject(object.getLocation(), newID, object.getDirection(), object.getType()));
                this.owner.getActionSender().sendSound(open ? "opendoor" : "closedoor");
            }

            private void doDoor() {
                this.owner.getActionSender().sendSound("opendoor");
                world.registerGameObject(new GameObject(object.getLocation(), 11, object.getDirection(), object.getType()));
                world.delayedSpawnObject(object.getLoc(), 1000);
            }

            /**
             * Open a door nothing else has an opinion about, and step through it.
             *
             * Most of the world's doors are id 2, which the cases above swing
             * open and shut. The rest -- and there are several hundred of them,
             * each with an id of its own -- had no case and fell through to
             * "Nothing interesting happens", which is to say they were walls.
             * That was invisible for as long as RSCD's object file had those
             * doors flattened to id 2 or replaced by their own open frame; with
             * the ids restored from the landscape it is not.
             *
             * Only the "Open" command opens anything. A click whose command is
             * anything else -- Push, a door some quest is meant to claim, the
             * empty second command most doors have -- keeps the old answer, so
             * nothing that used to be shut is opened by this. "Pick lock" is
             * the one exception, and it does not reach here directly: pickLock
             * runs first, and calls this only once the lock has given.
             *
             * A door facing 0 stands between (x,y) and (x,y-1); one facing 1
             * stands between (x,y) and (x-1,y). Same idiom the gated doors below
             * use, and the same one the quests copied.
             */
            private boolean openOrdinaryDoor(String command) {
                /*
                 * Doors 153-160 -- the tree gnome stronghold's whole family,
                 * 157 placements between them, none quest-gated -- carry
                 * "walk through" as their only command instead of "open".
                 * They had no case and no quest claim, so every treehouse
                 * door answered with nothing and the Grand Tree could be
                 * entered but never left. Walking through IS opening for
                 * these; same swap and step-through.
                 */
                /*
                 * The Tent Doors -- 196 and 198, three placements in the
                 * world -- carry "go through" instead, and had the same
                 * nothing. One of them hangs diagonally across its tile
                 * (direction 2, the big Bedabin tent), which no door before
                 * it did: a diagonal has no single crossing axis, so step to
                 * the far side of whichever axis the player stands off on,
                 * and skip the open-frame swap -- id 11 is a straight door
                 * and would draw wrong on the slant.
                 */
                if (!"open".equals(command) && !"walk through".equals(command)
                        && !"go through".equals(command)) {
                    return false;
                }
                boolean tentFlap = "go through".equals(command);
                if (tentFlap && object.getDirection() > 1) {
                    this.owner.getActionSender().sendSound("opendoor");
                    int dx = this.owner.getX() - object.getX();
                    int dy = this.owner.getY() - object.getY();
                    if (dy != 0) {
                        this.owner.teleport(object.getX(),
                            dy < 0 ? object.getY() + 1 : object.getY() - 1, false);
                    } else if (dx != 0) {
                        this.owner.teleport(
                            dx < 0 ? object.getX() + 1 : object.getX() - 1,
                            object.getY(), false);
                    }
                    return true;
                }
                if (tentFlap) {
                    /* Canvas, not carpentry: no swap to the open wooden
                     * frame, you just push through. */
                    this.owner.getActionSender().sendSound("opendoor");
                } else {
                    this.doDoor();
                }
                if (object.getDirection() == 0) {
                    this.owner.teleport(object.getX(),
                        this.owner.getY() >= object.getY() ? object.getY() - 1 : object.getY(),
                        false);
                } else {
                    this.owner.teleport(
                        this.owner.getX() >= object.getX() ? object.getX() - 1 : object.getX(),
                        object.getY(), false);
                }
                return true;
            }

            /**
             * Jump a low wall.
             *
             * The last two obstacles of the Barbarian Outpost course are low
             * walls, and a low wall is a door in the landscape rather than
             * scenery -- so it is here rather than in ObjectAction. Nothing
             * else about it is special: a door already stands between two
             * adjacent tiles, so the crossing is the ordinary step-through and
             * the def only has to say what it is worth. Neither wall can be
             * failed, which is why there is no roll.
             */
            private boolean jumpLowWall() {
                DoorAgilityDef def = EntityHandler.getDoorAgilityDef(object.getID());
                if (def == null || click != 0) {
                    return false;
                }
                if (this.owner.getCurStat(AGILITY) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need an agility level of " + def.getReqLevel() + " to do that");
                    return true;
                }
                // Both low walls are listed as stopping at full fatigue.
                if (this.owner.getFatigue() >= 100) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You are too tired to do that, get some rest!");
                    return true;
                }
                this.owner.getActionSender().sendMessage("@pnk@ " + def.getMessage());
                if (object.getDirection() == 0) {
                    this.owner.teleport(object.getX(),
                        this.owner.getY() >= object.getY() ? object.getY() - 1 : object.getY(),
                        false);
                } else {
                    this.owner.teleport(
                        this.owner.getX() >= object.getX() ? object.getX() - 1 : object.getX(),
                        object.getY(), false);
                }
                ObjectAction.awardAgility(this.owner, def.getCourseId(), def.getStage(),
                    def.getExp(), def.hasLapBonus() ? def.getLapExp() : 0, false);
                return true;
            }

            /**
             * Pick a lock, and walk through if it gives.
             *
             * Eleven doors in Classic can be opened this way and they are the
             * last quarter of Thieving. Four of them want a lockpick in the bag
             * as well as the level: the two Wilderness huts, the Yanille
             * agility dungeon, and -- through Legend's Quest rather than here --
             * the Viyeldi caves.
             *
             * Two things here are this server's and not Jagex's. The first is
             * that a pick can
             * fail at all: nothing recovered says whether a Classic lock could
             * be failed, and the roll used is the same one a pocket and a stall
             * get. The second is that a failed pick costs nothing but the
             * attempt -- no damage, and nobody comes running, because unlike a
             * stall a door is not being watched by anyone.
             *
             * A picked door opens for a second and shuts again, which is what
             * every other door in the game does; there is no state to remember
             * and no way to prop one open.
             */
            private boolean pickLock(String command) {
                if (!"pick lock".equals(command)) {
                    return false;
                }
                DoorThievingDef def = EntityHandler.getDoorThievingDef(object.getID());
                if (def == null) {
                    return false;
                }
                if (this.owner.getCurStat(THIEVING) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need a thieving level of " + def.getReqLevel() + " to pick this lock");
                    return true;
                }
                if (def.needsLockpick() && this.owner.getInventory().countId(LOCKPICK) <= 0) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a lockpick to pick this lock");
                    return true;
                }
                this.owner.getActionSender().sendMessage("@pnk@ You attempt to pick the lock");
                if (Formulae.catchThief(this.owner.getCurStat(THIEVING), def.getReqLevel())) {
                    this.owner.getActionSender().sendMessage("@gry@ You fail to pick the lock");
                    return true;
                }
                this.owner.getActionSender().sendMessage("@pnk@ You manage to pick the lock");
                if (def.getExp() > 0) {
                    this.owner.incExp(THIEVING, def.getExp(), true);
                    this.owner.getActionSender().sendStat(THIEVING);
                }
                // Same open-and-step-through the ordinary doors get; a picked
                // lock is not a special kind of doorway once it is open.
                this.openOrdinaryDoor("open");
                return true;
            }

            public void arrived() {
                this.owner.resetPath();
                DoorDef def = object.getDoorDef();
                if (this.owner.isBusy() || this.owner.isRanging() || !this.owner.nextTo(object) || def == null || this.owner.getStatus() != Action.USING_DOOR) {
                    return;
                }
                this.owner.resetAll();
                // A quest that has claimed this door id owns it, the same
                // contract scenery gets in ObjectAction. Doors are matched
                // against the quest's door list rather than its object list,
                // because DoorDef and GameObjectDef number from zero
                // independently and door 35 is not scenery 35.
                if (this.owner.getQuestManager().triggerDoor(
                        click == 0 ? QuestTrigger.DOOR_ACT1 : QuestTrigger.DOOR_ACT2, object)) {
                    return;
                }
                String command = (click == 0 ? def.getCommand1() : def.getCommand2()).toLowerCase();
                Point telePoint = EntityHandler.getObjectTelePoint(object.getLocation(), command);
                if (telePoint != null) {
                    this.owner.teleport(telePoint.getX(), telePoint.getY(), false);
                } else {
                    switch (object.getID()) {
                        case 1: {
                            this.replaceGameObject(2, false);
                            break;
                        }
                        case 2: {
                            this.replaceGameObject(1, true);
                            break;
                        }
                        case 9: {
                            this.replaceGameObject(8, false);
                            break;
                        }
                        case 8: {
                            this.replaceGameObject(9, true);
                            break;
                        }
                        case 23: {
                            this.owner.getActionSender().sendMessage("@gry@ The door is locked");
                            break;
                        }
                        case 112: {
                            if (object.getX() != 586 || object.getY() != 524) break;
                            if (this.owner.getY() > 523) {
                                if (this.owner.getCurStat(10) < 68) {
                                    this.owner.setBusy(true);
                                    Npc masterFisher = world.getNpc(368, 582, 588, 524, 527);
                                    if (masterFisher != null) {
                                        this.owner.informOfNpcMessage(new ChatMessage(masterFisher, "Hello only the top fishers are allowed in here", this.owner));
                                    }
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            this.owner.getActionSender().sendMessage("@gry@ You need a fishing level of 68 to enter");
                                        }
                                    });
                                    break;
                                }
                                this.doDoor();
                                this.owner.teleport(586, 523, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(586, 524, false);
                            break;
                        }
                        case 55: {
                            if (object.getX() != 268 || object.getY() != 3381) break;
                            if (this.owner.getY() <= 3380) {
                                /*
                                 * Mining 60, not 66. The requirement was wrong
                                 * here, not just the wording: Jagex's own two
                                 * refusal messages for this guild -- the door's
                                 * and the ladder's -- both say "a mining of
                                 * level 60", so the attested text contradicted
                                 * the attested-looking number we shipped. Six
                                 * levels of a skill is a real gate, so this is
                                 * a fix rather than a tidy-up.
                                 */
                                if (this.owner.getCurStat(14) < 60) {
                                    this.owner.setBusy(true);
                                    Npc dwarf = world.getNpc(191, 265, 270, 3379, 3380);
                                    if (dwarf != null) {
                                        // "Sorry", not "Hello", and "in there"
                                        // rather than "in here" -- he is on the
                                        // outside of the door with you. Both
                                        // were RSCD paraphrases.
                                        this.owner.informOfNpcMessage(new ChatMessage(dwarf, "Sorry only the top miners are allowed in there", this.owner));
                                    }
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            // "a mining of level 60" is the
                                            // recorded wording, twice over on
                                            // two different objects. It reads
                                            // like a typo and is not one.
                                            this.owner.getActionSender().sendMessage("@gry@ The door won't open - you need a mining of level 60 to enter");
                                        }
                                    });
                                    break;
                                }
                                this.doDoor();
                                this.owner.teleport(268, 3381, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(268, 3380, false);
                            break;
                        }
                        case 68: {
                            if (object.getX() != 347 || object.getY() != 601) {
                                return;
                            }
                            if (this.owner.getY() <= 600) {
                                if (this.owner.getCurStat(12) < 40) {
                                    this.owner.setBusy(true);
                                    Npc master = world.getNpc(231, 341, 349, 599, 612);
                                    if (master != null) {
                                        // Recorded wording. "experienced
                                        // craftsmen", not "the top crafters".
                                        this.owner.informOfNpcMessage(new ChatMessage(master, "Sorry only experienced craftsmen are allowed in here", this.owner));
                                    }
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of 40 to enter");
                                        }
                                    });
                                    break;
                                }
                                if (!this.owner.getInventory().wielding(191)) {
                                    final Npc master = world.getNpc(231, 341, 349, 599, 612);
                                    if (master == null) break;
                                    /*
                                     * Two lines, not one. "Where's your brown
                                     * apron?" -- contracted, and it names the
                                     * apron -- followed by the sentence that
                                     * actually tells the player what to do. The
                                     * second line was missing entirely, which
                                     * is the same shape of gap the cooking
                                     * guild door had (case 43 above).
                                     */
                                    this.owner.informOfNpcMessage(new ChatMessage(master, "Where's your brown apron?", this.owner));
                                    this.owner.setBusy(true);
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            this.owner.informOfNpcMessage(new ChatMessage(master, "You can't come in here unless you're wearing a brown apron", this.owner));
                                        }
                                    });
                                    break;
                                }
                                this.doDoor();
                                this.owner.teleport(347, 601, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(347, 600, false);
                            break;
                        }
                        case 43: {
                            if (object.getX() != 179 || object.getY() != 488) break;
                            if (this.owner.getY() >= 488) {
                                /* Both refusals are the head chef's own words,
                                   from Transcript:Head chef, which records this
                                   door as three branches of his dialogue rather
                                   than as a door. RSCD had paraphrases in both
                                   places -- "Hello only the top cooks are
                                   allowed in here" and a one-line "Where is
                                   your chef's hat?" -- and was missing the
                                   second half of the hat refusal entirely.
                                   FlavorNpcs.headChef says the same lines for
                                   the same states when he is talked to. */
                                if (this.owner.getCurStat(7) < 32) {
                                    this.owner.setBusy(true);
                                    Npc chef = world.getNpc(133, 176, 181, 480, 487);
                                    if (chef != null) {
                                        this.owner.informOfNpcMessage(new ChatMessage(chef, "Sorry. Only the finest chefs are allowed in here", this.owner));
                                    }
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            /* Kept, though the transcript ends the
                                               branch after the chef's line: an npc
                                               transcript records speech, not the
                                               grey system messages beside it, so
                                               its silence here is not evidence
                                               either way. Flagged rather than
                                               removed on a guess. */
                                            this.owner.getActionSender().sendMessage("@gry@ You need a cooking level of 32 to enter");
                                        }
                                    });
                                    break;
                                }
                                if (!this.owner.getInventory().wielding(192)) {
                                    final Npc chef = world.getNpc(133, 176, 181, 480, 487);
                                    if (chef == null) break;
                                    this.owner.setBusy(true);
                                    this.owner.informOfNpcMessage(new ChatMessage(chef, "Where's your chef's hat", this.owner));
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            this.owner.informOfNpcMessage(new ChatMessage(chef, "You can't come in here unless you're wearing a chef's hat", this.owner));
                                        }
                                    });
                                    break;
                                }
                                this.doDoor();
                                this.owner.teleport(179, 487, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(179, 488, false);
                            break;
                        }
                        case 146: {
                            if (object.getX() != 599 || object.getY() != 757) break;
                            if (this.owner.getX() <= 598) {
                                if (this.owner.getCurStat(6) < 66) {
                                    this.owner.setBusy(true);
                                    Npc wizard = world.getNpc(513, 596, 597, 755, 758);
                                    if (wizard != null) {
                                        this.owner.informOfNpcMessage(new ChatMessage(wizard, "Hello only the top wizards are allowed in here", this.owner));
                                    }
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            this.owner.setBusy(false);
                                            this.owner.getActionSender().sendMessage("@gry@ You need a magic level of 66 to enter");
                                        }
                                    });
                                    break;
                                }
                                this.doDoor();
                                this.owner.teleport(599, 757, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(598, 757, false);
                            break;
                        }
                        case 44: {
                            /*
                             * Champions' Guild, door object 44 at (150,554).
                             * DragonSlayer's guildmaster dialogue (npc 111,
                             * the same npc reused for the taunt here) already
                             * checks 32 quest points for its own purpose --
                             * see that class's own comment: "the guild's own
                             * door is not claimed here; that is guild
                             * membership, not this quest." This is that door,
                             * built the same way as the other five guild gates
                             * in this file (Fishing/Mining/Crafting/Cooks'/
                             * Wizards'): a fixed x/y check to scope it to just
                             * this one door, a teleport through rather than a
                             * normal open, and a free walk back out.
                             */
                            if (object.getX() != 150 || object.getY() != 554) break;
                            if (this.owner.getY() >= 554) {
                                this.doDoor();
                                this.owner.teleport(150, 553, false);
                                break;
                            }
                            if (this.owner.getQuestPoints() < 32) {
                                this.owner.setBusy(true);
                                Npc guildmaster = world.getNpc(111, 148, 152, 554, 560);
                                if (guildmaster != null) {
                                    this.owner.informOfNpcMessage(new ChatMessage(guildmaster, "You have not proven yourself worthy to enter here yet", this.owner));
                                }
                                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                    public void action() {
                                        this.owner.setBusy(false);
                                        this.owner.getActionSender().sendMessage("@gry@ You need at least 32 quest points to enter");
                                    }
                                });
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(150, 555, false);
                            break;
                        }
                        case 22:
                        case 87: {  // 87 is the "unusual looking wall" variant
                            // Odd looking walls. Only the Varrock sewer one was
                            // ever wired up, which left both of the Black
                            // knights' fortress walls dead and that quest
                            // impassable -- the grill is behind one and the hole
                            // is behind the other. They all do the same thing,
                            // so they are one list now rather than one branch
                            // each.
                            if (!isSecretWall(object.getX(), object.getY())) {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens");
                                break;
                            }
                            this.owner.getActionSender().sendSound("secretdoor");
                            world.unregisterGameObject(object);
                            world.delayedSpawnObject(object.getLoc(), 1000);
                            this.owner.getActionSender().sendMessage("@pnk@ You just went through a secret door");
                            // A wall facing 0 stands between (x,y) and (x,y-1);
                            // one facing 1 stands between (x,y) and (x-1,y). The
                            // player comes out on whichever side they were not
                            // standing on.
                            if (object.getDirection() == 0) {
                                this.owner.teleport(object.getX(),
                                    this.owner.getY() >= object.getY() ? object.getY() - 1 : object.getY(), false);
                            } else {
                                this.owner.teleport(
                                    this.owner.getX() >= object.getX() ? object.getX() - 1 : object.getX(),
                                    object.getY(), false);
                            }
                            break;
                        }
                        case 58: {
                            if (object.getX() != 406 || object.getY() != 3518) {
                                return;
                            }
                            this.doDoor();
                            if (this.owner.getY() <= 3517) {
                                this.owner.teleport(406, 3518, false);
                                break;
                            }
                            this.owner.teleport(406, 3517, false);
                            break;
                        }
                        case 101: {
                            if (object.getX() != 540 || object.getY() != 445) {
                                return;
                            }
                            if (this.owner.getX() >= 540) {
                                this.owner.getActionSender().sendMessage("@pnk@ You push your way through");
                                this.owner.teleport(539, 445, false);
                                break;
                            }
                            this.owner.getActionSender().sendMessage("@pnk@ You can't seem to get through");
                            break;
                        }
                        case 38: {
                            if (object.getX() != 271 || object.getY() != 441) {
                                return;
                            }
                            if (this.owner.getX() <= 270) {
                                if (!this.owner.getInventory().wielding(7) || !this.owner.getInventory().wielding(104)) {
                                    this.owner.getActionSender().sendMessage("@gry@ Only guards are allowed in there!");
                                    return;
                                }
                                this.doDoor();
                                this.owner.teleport(271, 441, false);
                                break;
                            }
                            this.doDoor();
                            this.owner.teleport(270, 441, false);
                            break;
                        }
                        case 36: {
                            if (object.getX() != 210 || object.getY() != 553) {
                                return;
                            }
                            if (this.owner.getY() >= 553) {
                                this.doDoor();
                                this.owner.teleport(210, 552, false);
                                break;
                            }
                            this.owner.getActionSender().sendMessage("@gry@ The door is locked shut");
                            break;
                        }
                        case 37: {
                            if (object.getX() != 199 || object.getY() != 551) {
                                return;
                            }
                            if (this.owner.getY() >= 551) {
                                this.doDoor();
                                this.owner.teleport(199, 550, false);
                                break;
                            }
                            this.owner.getActionSender().sendMessage("@gry@ The door is locked shut");
                            break;
                        }
                        case 60: {
                            if (this.owner.getX() > 337) {
                                this.doDoor();
                                this.owner.teleport(337, this.owner.getY(), false);
                                break;
                            }
                            this.owner.getActionSender().sendMessage("@gry@ The door is locked shut");
                            break;
                        }
                        case 30: {
                            this.owner.getActionSender().sendMessage("@gry@ The door is locked shut");
                            break;
                        }
                        case 176: {
                            /*
                             * The cell at the Shantay Pass. Door 176 is the
                             * only Jail Door in the game and (66,729) is the
                             * only place it stands -- the landscape writes it
                             * as wall 177, one higher, because wall bytes are
                             * one-based and DoorDef ids are not -- so this
                             * case is that cell and nothing else.
                             *
                             * It would otherwise have fallen through to the
                             * ordinary "Open" below, which would have made the
                             * five gold piece fine optional -- walk in, walk
                             * out. Locked from the outside for the same
                             * reason it is locked from the inside: a cell
                             * anyone can wander into is a cell anyone can
                             * shut themselves in.
                             */
                            if (!ShantayPass.inCell(this.owner)) {
                                this.owner.getActionSender().sendMessage("@gry@ The cell door is locked");
                                break;
                            }
                            ShantayPass.payAtCellDoor(this.owner);
                            break;
                        }
                        case 67: {
                            /*
                             * Door 67 is a plain door used all over the world,
                             * so this is a place check rather than an id one:
                             * only the two that the Zanaris faerie market's
                             * doormen stand between cost anything. Everything
                             * else with that id keeps falling through to the
                             * ordinary open below.
                             *
                             * The toll is charged in both directions, which is
                             * Jagex's -- the transcript's heading is "into or
                             * out of the market". The pass-through is handed
                             * to the doorman as the thing to do once the
                             * diamond is paid, so a refusal leaves the door
                             * exactly as it was.
                             */
                            if (!Doorman.guarded(object)) {
                                if (this.openOrdinaryDoor(command)) {
                                    break;
                                }
                                this.owner.getActionSender().sendMessage("@gry@ Nothing interesting happens.");
                                break;
                            }
                            if (!"open".equals(command)) {
                                this.owner.getActionSender().sendMessage("@gry@ Nothing interesting happens.");
                                break;
                            }
                            Doorman.challenge(this.owner, new Runnable() {
                                public void run() {
                                    openOrdinaryDoor("open");
                                }
                            });
                            break;
                        }
                        default: {
                            if (this.jumpLowWall()) {
                                break;
                            }
                            if (this.pickLock(command)) {
                                break;
                            }
                            if (this.openOrdinaryDoor(command)) {
                                break;
                            }
                            this.owner.getActionSender().sendMessage("@gry@ Nothing interesting happens.");
                        }
                    }
                }
            }
        });
    }
}

