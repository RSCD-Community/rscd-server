import java.util.ArrayList;

import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.Formulae;

/**
 * Hero's quest. Released 27 February 2002, written by Paul Gower.
 *
 * Achetties stands outside the hero's guild north of Taverley and will not let
 * anybody in who cannot show three things: the feather of an Entrana firebird,
 * a cooked lava eel, and a master thief armband. They can be fetched in any
 * order and have nothing to do with each other.
 *
 *   The feather. The firebird (252) on Entrana drops one when killed, and it
 *   is red hot -- picking it up burns anybody whose hands are not cold. The
 *   ice gloves that make it safe are dropped by the Ice queen (254), who lives
 *   at the bottom of the maze under White Wolf Mountain behind a rockslide
 *   (269) that takes fifty mining to dig through.
 *
 *   The eel. Gerrant in Port Sarim knows the method: blamish snail slime mixed
 *   into an unfinished harralander potion makes blamish oil, oil rubbed into an
 *   ordinary fishing rod makes a rod that will not burn, and with it a raw lava
 *   eel can be pulled out of the lava (271) in the deep part of Taverley
 *   dungeon. That part is behind the dusty key, which Velrak the explorer (272)
 *   hands over if he is let out of the black knights' jail first.
 *
 *   The armband. This is the two player half of the quest and the reason
 *   Hero's quest is remembered. Scarface Pete's mansion in Brimhaven holds a
 *   pair of candlesticks in a chest behind a locked door, and neither gang can
 *   reach them alone: a black arm walks in the front door wearing a dead black
 *   knight's face, and a phoenix comes up a passage behind a restaurant with
 *   nothing but a hole in a wall to shoot through. What one of them can open
 *   the other one needs.
 *
 * Which gang the player belongs to is Shield of Arrav's answer, remembered in
 * GangMembership after that quest has spent its own stage. Both gang leaders
 * stay owned by Shield of Arrav -- an npc has one owner -- so Straven and
 * Katrine host their own halves of this quest's dialogue there and report back
 * through the names below. Gerrant is a shopkeeper for everybody and lives in
 * src/ as his own NpcHandler for the same reason.
 *
 * The armband is genuinely two-player, exactly as Jagex shipped it (user's
 * decision, 2026-08-12, reversing an earlier set of solo-server additions):
 * the misc key crosses gangs by trade, Grip is only lured into the sniper's
 * line by the black arm at his cabinet, and Garv never leaves the front door,
 * so a phoenix has no way into the mansion at all. A player without a partner
 * from the other gang cannot finish this quest, and that is the design.
 *
 *   Jagex's refusal to let a black arm attack Grip -- "I can't attack the head
 *   guard here, there are too many witnesses" -- is enforced in refusesAttack,
 *   which AttackHandler asks for both melee and ranged. Magic does not consult
 *   it (an engine-wide gap, see SpellHandler), so a black arm with runes can
 *   still cheat the witnesses; noted rather than papered over.
 *
 *   The chest holds two candlesticks, one for each of the pair that got this
 *   far, so both partners hand one in.
 *
 * Jagex's exact wording for the hero's guild door was never recorded, so it
 * gets the ordinary "The door is locked". Everything else spoken below is from
 * the transcripts.
 *
 * Two things this quest wants are not in the server's data and are reported
 * rather than invented here: the Shrimp and Parrot has no entry in Shops.xml,
 * so Alfonse cannot take an order; and the corridor walls of the Ice Queen maze
 * are missing from Landscape.rscd, so the maze is one open room and its seven
 * ladders lead nowhere. Neither stops the quest -- the queen can be walked to
 * through Taverley dungeon -- and neither is papered over with a guess.
 */
public class HerosQuest extends Quest {

    public final static int UID = Quests.HEROS_QUEST;

    /* Stage is a set of bits rather than a line of numbers. The three tasks are
     * done in any order and the two gangs walk different ways through the same
     * mansion, so there is no order to count along. FINISHED is out on its own
     * above every combination of the rest, because completed() is exact
     * equality against the final stage. */
    private static final int STARTED      = 1;
    private static final int TOLD_ARMBAND = 2;
    private static final int GHERKIN      = 4;
    private static final int CHARLIE_TOLD = 8;
    private static final int HIDEOUT      = 16;
    private static final int GRIP_ID      = 32;
    private static final int GRIP_DEAD    = 64;
    private static final int CHEST_ROBBED = 128;
    private static final int ARMBAND      = 256;
    private static final int GARV_PASSED  = 512;
    private static final int GOT_PAPER    = 1024;
    private static final int FINISHED     = 2048;

    /** Shield of Arrav's leftover: which gang took this player in. */
    private static final int GANG = Quests.FIRST_CUSTOM;

    private static final int ACHETTIES = 253;
    private static final int GRUBOR    = 255;
    private static final int TROBERT   = 256;
    private static final int GARV      = 257;
    private static final int MANSION_GUARD = 258;
    private static final int GRIP      = 259;
    private static final int ALFONSE   = 260;
    private static final int CHARLIE   = 261;
    private static final int VELRAK    = 272;

    private static final int ICE_GLOVES  = 556;
    private static final int FEATHER     = 557;
    private static final int ID_PAPER    = 573;
    private static final int MISC_KEY    = 582;
    private static final int BUNCH_KEYS  = 583;
    private static final int WHISKY      = 584;
    private static final int CANDLESTICK = 585;
    private static final int ARMBAND_ITEM = 586;
    private static final int JAIL_KEYS   = 595;
    private static final int DUSTY_KEY   = 596;
    private static final int LAVA_EEL    = 590;

    private static final int BLACK_HELMET = 230;
    private static final int BLACK_BODY   = 196;
    private static final int BLACK_LEGS   = 248;

    private static final int CUPBOARD   = 264;
    private static final int CUPBOARD_X = 461, CUPBOARD_Y = 675;
    private static final int CHEST      = 265;
    private static final int CHEST_SHUT = 266;
    private static final int CHEST_X    = 469, CHEST_Y = 672;
    private static final int ROCKSLIDE  = 269;
    private static final int ROCKSLIDE_X = 426, ROCKSLIDE_Y = 438;

    private static final int GUILD_DOOR    = 74, GUILD_DOOR_X = 372, GUILD_DOOR_Y = 441;
    private static final int MANSION_DOOR  = 75, MANSION_DOOR_X = 463, MANSION_DOOR_Y = 681;
    private static final int HIDEOUT_DOOR  = 76, HIDEOUT_DOOR_X = 439, HIDEOUT_DOOR_Y = 694;
    private static final int KITCHEN_DOOR  = 78, KITCHEN_DOOR_X = 448, KITCHEN_DOOR_Y = 682;
    private static final int PANEL         = 79, PANEL_X = 456, PANEL_Y = 679;
    private static final int SIDE_DOOR     = 80, SIDE_DOOR_X = 459, SIDE_DOOR_Y = 674;
    private static final int TREASURE_DOOR = 81, TREASURE_DOOR_X = 472, TREASURE_DOOR_Y = 674;
    private static final int JAIL_DOOR     = 83;
    private static final int DUSTY_DOOR    = 84, DUSTY_DOOR_X = 355, DUSTY_DOOR_Y = 3353;

    private static final int OPEN_DOOR = 11;

    /** Where Grip goes when he hears somebody at his drinks cabinet. */
    private static final int LURE_X = 462, LURE_Y = 675;

    private static final int QUEST_POINTS_NEEDED = 55;
    private static final int MINING = 14;
    private static final int ROCKSLIDE_LEVEL = 50;
    /** RuneHQ and the wiki both list these three alongside Mining 50. */
    private static final int COOKING = 7;
    private static final int FISHING = 10;
    private static final int HERBLAW = 15;
    private static final int HERBLAW_LEVEL = 25;
    private static final int FISHING_LEVEL = 53;
    private static final int COOKING_LEVEL = 53;

    /** Attack, defense, hits, strength, cooking, fishing, mining, smithing,
     *  ranged, firemaking, woodcutting and herblaw, at level times fifty plus
     *  seventy five apiece. */
    private static final int[] REWARD_SKILLS = { 0, 1, 3, 2, 7, 10, 14, 13, 4, 11, 8, 15 };

    public HerosQuest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Hero's quest");
        setFinalStage(FINISHED);
        associateNpc(ACHETTIES);
        associateNpc(GRUBOR);
        associateNpc(TROBERT);
        associateNpc(GARV);
        associateNpc(GRIP);
        associateNpc(ALFONSE);
        associateNpc(CHARLIE);
        associateNpc(VELRAK);
        associateObject(CUPBOARD);
        associateObject(CHEST);
        associateObject(ROCKSLIDE);
        associateDoor(GUILD_DOOR);
        associateDoor(MANSION_DOOR);
        associateDoor(HIDEOUT_DOOR);
        associateDoor(KITCHEN_DOOR);
        associateDoor(PANEL);
        associateDoor(SIDE_DOOR);
        associateDoor(TREASURE_DOOR);
        associateDoor(JAIL_DOOR);
        associateDoor(DUSTY_DOOR);
        // The feather, so that lifting it comes here and can be refused. The
        // mansion guards are deliberately not associated: they say nothing of
        // their own, and the one line they have belongs to the cupboard.
        associateItem(FEATHER);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Prove you are worthy to enter the hero's guild. To prove your status as a hero you will need to obtain a number of items. There are many challenges standing between you and these items.");
        setStartPoint("Hero's guild");
        setSpeakTo("Achetties");
        setMissionLength("Long");
        /* Achetties' gate is qualified() below; the rockslide check is mining. */
        require("55 quest points");
        requireQuest(Quests.SHIELD_OF_ARRAV);
        requireQuest(Quests.DRAGON_SLAYER);
        requireQuest(Quests.MERLINS_CRYSTAL);
        requireQuest(Quests.LOST_CITY);
        requireLevel(MINING, ROCKSLIDE_LEVEL);
        requireLevel(HERBLAW, HERBLAW_LEVEL);
        requireLevel(FISHING, FISHING_LEVEL);
        requireLevel(COOKING, COOKING_LEVEL);
        for (int skill : REWARD_SKILLS) {
            rewardExp(skill, 75, 50);
        }
        rewardOther("Access to the Heroes' guild");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Hero's quest");
    }

    // ------------------------------------------------------------ helpers --

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    private boolean has(int bit) {
        int s = getStage();
        return s > 0 && (s & bit) != 0;
    }

    private void set(int bit) {
        int s = getStage();
        setStage((s < 0 ? 0 : s) | bit);
    }

    /** Started and not yet finished: the state everything below is about. */
    private boolean running() {
        return has(STARTED) && !completed();
    }

    private boolean started() {
        return has(STARTED) || completed();
    }

    private boolean phoenix() {
        return getOwner().getQuestManager().reached(GANG, "phoenix");
    }

    private boolean blackArm() {
        return getOwner().getQuestManager().reached(GANG, "black-arm");
    }

    /**
     * Everything Achetties asks for before she will set the tasks.
     *
     * Fifty five quest points and four quests, which between them are most of
     * the free game and the first two thirds of the members one. The Herblaw,
     * Fishing and Cooking levels the guides list are NOT checked here: they
     * are what the eel and its oil demand mid-quest, not what Achetties asks
     * for, and gating the start on them was a deviation -- the levels still
     * bite where the tasks themselves need them.
     */
    private boolean qualified() {
        Player p = getOwner();
        return p.getQuestPoints() >= QUEST_POINTS_NEEDED
            && p.getQuestManager().completed(Quests.SHIELD_OF_ARRAV)
            && p.getQuestManager().completed(Quests.DRAGON_SLAYER)
            && p.getQuestManager().completed(Quests.MERLINS_CRYSTAL)
            && p.getQuestManager().completed(Quests.LOST_CITY);
    }

    private boolean hasAll() {
        return count(FEATHER) > 0 && count(LAVA_EEL) > 0 && count(ARMBAND_ITEM) > 0;
    }

    /** Dressed as Hartigen the black knight was when they took his papers. */
    private boolean disguised() {
        return getOwner().getInventory().wielding(BLACK_HELMET)
            && getOwner().getInventory().wielding(BLACK_BODY)
            && getOwner().getInventory().wielding(BLACK_LEGS);
    }

    // ------------------------------------------------- what others may ask --

    /**
     * Answers for the two gang leaders, who belong to Shield of Arrav.
     *
     * "seeking-armband" is the whole of what a leader needs to decide whether
     * to talk about candlesticks; "stole-candlestick" is Jagex's rule that a
     * candlestick handed over by somebody who did not take it themselves is
     * worth nothing, and it is true of whoever killed Grip or emptied the
     * chest, which between them is both halves of a genuine pair.
     */
    public boolean reached(String key) {
        if ("started".equals(key))            { return started(); }
        if ("seeking-armband".equals(key))    { return running() && !has(ARMBAND); }
        if ("told-about-armband".equals(key)) { return has(TOLD_ARMBAND); }
        if ("stole-candlestick".equals(key))  { return has(GRIP_DEAD) || has(CHEST_ROBBED); }
        return false;
    }

    public void note(String key) {
        if (!running()) {
            return;
        }
        if ("told-about-armband".equals(key)) {
            set(TOLD_ARMBAND);
        } else if ("armband-earned".equals(key) && !has(ARMBAND)) {
            Player p = getOwner();
            p.getInventory().add(new InvItem(ARMBAND_ITEM, 1));
            p.getActionSender().sendInventory();
            set(ARMBAND);
        }
    }

    /**
     * The firebird's feather, which is still on fire when it lands.
     *
     * Jagex hit anybody who was not on the quest for nothing at all and let
     * them keep their hands; anybody who was got fifteen percent of what they
     * had left taken off them, and was never killed by it.
     */
    /** Jagex's witnesses rule: the black arm walked in the front door as one
     *  of the staff, and the staff do not get to murder the head guard in
     *  front of everybody. The phoenix, hidden behind the wall, may shoot. */
    public boolean refusesAttack(Npc npc) {
        if (npc.getID() != GRIP || !running() || !blackArm()) {
            return false;
        }
        getOwner().getActionSender().sendMessage(
            "I can't attack the head guard here, there are too many witnesses");
        return true;
    }

    public boolean refusesPickup(InvItem item) {
        if (item.getID() != FEATHER) {
            return false;
        }
        Player p = getOwner();
        if (!running()) {
            p.getActionSender().sendMessage("It looks dangerously hot");
            p.getActionSender().sendMessage("And I have no reason to take it");
            return true;
        }
        if (p.getInventory().wielding(ICE_GLOVES)) {
            return false;
        }
        p.getActionSender().sendMessage("Ouch that is too hot to take");
        p.getActionSender().sendMessage("I need something cold to pick it up with");
        int burn = Math.max(1, (p.getCurStat(3) * 15) / 100);
        p.setCurStat(3, Math.max(1, p.getCurStat(3) - burn));
        p.getActionSender().sendStat(3);
        return true;
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == GRIP) {
                    set(GRIP_DEAD);
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case ACHETTIES: talkToAchetties(npc); break;
                case GRUBOR:    talkToGrubor(npc);    break;
                case TROBERT:   talkToTrobert(npc);   break;
                case GARV:      talkToGarv(npc);      break;
                case GRIP:      talkToGrip(npc);      break;
                case ALFONSE:   talkToAlfonse(npc);   break;
                case CHARLIE:   talkToCharlie(npc);   break;
                case VELRAK:    talkToVelrak(npc);    break;
            }
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            switch (object.getID()) {
                case CUPBOARD:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchCupboard(object); }
                    break;
                case CHEST:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { searchChest(object); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { shutChest(object); }
                    break;
                case ROCKSLIDE:
                    if (trigger == QuestTrigger.OBJECT_ACT1) { mineRockslide(object); }
                    else if (trigger == QuestTrigger.OBJECT_ACT2) { prospectRockslide(); }
                    break;
            }
            if (trigger == QuestTrigger.DOOR_ACT1) {
                switch (object.getID()) {
                    case GUILD_DOOR:    openGuildDoor(object);    break;
                    case MANSION_DOOR:  openMansionDoor(object);  break;
                    case HIDEOUT_DOOR:  openHideoutDoor(object);  break;
                    case KITCHEN_DOOR:  openKitchenDoor(object);  break;
                    case PANEL:         pushPanel(object);        break;
                    case SIDE_DOOR:     openSideDoor(object, false);     break;
                    case TREASURE_DOOR: openTreasureDoor(object, false); break;
                    case JAIL_DOOR:     openJailDoor(object, false);     break;
                    case DUSTY_DOOR:    openDustyDoor(object, false);    break;
                }
            } else if (trigger == QuestTrigger.DOOR_ACT2) {
                getOwner().getActionSender().sendMessage("The door is shut");
            }
            return;
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_DOOR && entity instanceof GameObject) {
            GameObject door = (GameObject) entity;
            switch (door.getID()) {
                // Jagex made the jail keys the one pair that has to be used on
                // the door by hand rather than turning in the pocket, so that
                // is the only way in here. The rest take either.
                case JAIL_DOOR:
                    if (used.getID() == JAIL_KEYS) { openJailDoor(door, true); return; }
                    break;
                case DUSTY_DOOR:
                    if (used.getID() == DUSTY_KEY) { openDustyDoor(door, true); return; }
                    break;
                case SIDE_DOOR:
                    if (used.getID() == MISC_KEY) { openSideDoor(door, true); return; }
                    break;
                case TREASURE_DOOR:
                    if (used.getID() == BUNCH_KEYS) { openTreasureDoor(door, true); return; }
                    break;
            }
        }
        triggerEntity(trigger, entity);
    }

    // ---------------------------------------------------------- achetties --

    private void talkToAchetties(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.npc("Greetings welcome to the hero's guild");
        if (completed()) {
            c.start();
            return;
        }
        if (!started()) {
            c.npc("Only the foremost hero's of the land can enter here")
             .options(new Choice(new String[] {
                 "I'm a hero, may I apply to join?",
                 "Good for the foremost hero's of the land" }) {
                 public void picked(int option, Conversation c) {
                     if (option != 0) {
                         return;
                     }
                     if (!qualified()) {
                         c.npc("You're a hero?, I've never heard of you");
                         return;
                     }
                     c.npc("Ok you may begin the tasks of joining the hero's guild");
                     tasks(c);
                     c.then(new Effect() {
                         public void run(Conversation c) {
                             set(STARTED);
                         }
                     });
                     hints(c);
                 }
             });
            c.start();
            return;
        }
        c.npc("How goes thy quest?");
        if (hasAll()) {
            c.player("I have all the things needed")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(FINISHED);
                 }
             });
            c.start();
            return;
        }
        c.player("It's tough, I've not done it yet")
         .npc("Remember you need the feather of an Entrana firebird")
         .npc("A master thief armband")
         .npc("And a cooked lava eel");
        hints(c);
        c.start();
    }

    private void tasks(Conversation c) {
        c.npc("You need the feather of an Entrana firebird")
         .npc("A master thief armband")
         .npc("And a cooked lava eel");
    }

    private void hints(Conversation c) {
        c.options(new Choice(new String[] {
            "Any hints on getting the armband?",
            "Any hints on getting the feather?",
            "Any hints on getting the eel?",
            "I'll start looking for all those things then" }) {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("I'm sure you have relevant contacts to find out about that");
                        break;
                    case 1:
                        c.npc("Not really - Entrana firebirds live on Entrana");
                        break;
                    case 2:
                        c.npc("Maybe go and find someone who knows a lot about fishing?");
                        break;
                }
            }
        });
    }

    /**
     * The guild door.
     *
     * Jagex's wording for turning somebody away here was never written down, so
     * it gets the plain refusal every other locked door in the game gives. The
     * ungated case in WallObjectAction that used to open this door for anybody
     * was removed when this quest was written.
     */
    private void openGuildDoor(GameObject door) {
        if (door.getX() != GUILD_DOOR_X || door.getY() != GUILD_DOOR_Y) {
            return;
        }
        if (!completed()) {
            getOwner().getActionSender().sendMessage("The door is locked");
            return;
        }
        walkThrough(door);
    }

    // -------------------------------------------------------- white wolf --

    /**
     * The rockslide over the mouth of the Ice Queen maze.
     *
     * A pickaxe and fifty mining, and no experience for it -- these are rocks
     * in the way rather than an ore. It goes back after a minute the way every
     * other mined rock does.
     */
    private void mineRockslide(GameObject rocks) {
        Player p = getOwner();
        if (rocks.getX() != ROCKSLIDE_X || rocks.getY() != ROCKSLIDE_Y) {
            return;
        }
        int axe = -1;
        for (int id : Formulae.miningAxeIDs) {
            if (p.getInventory().countId(id) > 0) {
                axe = id;
                break;
            }
        }
        if (axe < 0) {
            /* Same refusal the mainland rocks give, word for word: no colour
               code and no full stop. "[servmsg]" was never a thing Jagex
               printed at a player. */
            p.getActionSender().sendMessage("You need a pickaxe to mine this rock");
            return;
        }
        if (p.getCurStat(MINING) < ROCKSLIDE_LEVEL) {
            /* An ore rock answers a low level with a failed swing, so there is
               no level message anywhere in mining -- but the rockslide has no
               roll to fail, and silence would leave the player with nothing at
               all. The transcript only records the success line, so this one
               stays as ours until something better turns up. */
            p.getActionSender().sendMessage("You need a mining level of "
                + ROCKSLIDE_LEVEL + " to mine this rock");
            return;
        }
        p.getActionSender().sendSound("mine");
        p.getActionSender().sendMessage("you manage to dig a way through the rockslide");
        world.unregisterGameObject(rocks);
        world.delayedSpawnObject(rocks.getLoc(), 60000);
    }

    private void prospectRockslide() {
        Player p = getOwner();
        p.getActionSender().sendMessage("these rocks contain nothing interesting");
        p.getActionSender().sendMessage("they are just in the way");
    }

    // ------------------------------------------------- taverley dungeon --

    /**
     * The two cells of the black knights' jail.
     *
     * Jagex would not open these for keys sitting in a pocket -- they have to
     * be taken out and used on the door, which is why a player who drops them
     * inside a cell is stuck there. Both cells take the same keys.
     */
    private void openJailDoor(GameObject door, boolean withKeys) {
        Player p = getOwner();
        if (!withKeys || count(JAIL_KEYS) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendMessage("You unlock the door");
        walkThrough(door);
    }

    private void openDustyDoor(GameObject door, boolean withKey) {
        Player p = getOwner();
        if (door.getX() != DUSTY_DOOR_X || door.getY() != DUSTY_DOOR_Y) {
            return;
        }
        if (count(DUSTY_KEY) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendMessage("You unlock the door");
        walkThrough(door);
    }

    private void talkToVelrak(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (count(DUSTY_KEY) > 0) {
            c.player("Are you still here?")
             .npc("Yes, I'm still plucking up courage")
             .npc("To run out past those black knights")
             .start();
            return;
        }
        c.npc("Thankyou for rescuing me")
         .npc("It isn't comfy in this cell")
         .options(new Choice(new String[] {
             "So do you know anywhere good to explore?",
             "Do I get a reward?" }) {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Well not really the black knights took all my stuff before throwing me in here");
                     return;
                 }
                 c.npc("Well this dungeon was quite good to explore")
                  .npc("Till I got captured")
                  .npc("I got given a key to an inner part of this dungeon")
                  .npc("By a mysterious cloaked stranger")
                  .npc("It's rather to tough for me to get that far though")
                  .npc("I keep getting captured")
                  .npc("Would you like to give it a go")
                  .options(new Choice(new String[] {
                      "Yes please",
                      "No it's to dangerous for me too" }) {
                      public void picked(int option, Conversation c) {
                          if (option != 0) {
                              return;
                          }
                          c.give(new InvItem(DUSTY_KEY, 1));
                      }
                  });
             }
         });
        c.start();
    }

    // ------------------------------------------------------ black arm way --

    /**
     * Grubor, who keeps the door of the Brimhaven hideout.
     *
     * He asks the same question of everybody and only one of the four answers
     * means anything. Katrine gives it as "four leafed clover" and Grubor wants
     * "four leaved clover", which is Jagex's typo and is kept.
     */
    private void talkToGrubor(Npc npc) {
        askGrubor(npc, null);
    }

    private void openHideoutDoor(GameObject door) {
        if (door.getX() != HIDEOUT_DOOR_X || door.getY() != HIDEOUT_DOOR_Y) {
            return;
        }
        Player p = getOwner();
        Npc grubor = world.getNpc(GRUBOR, p.getX() - 5, p.getX() + 5, p.getY() - 5, p.getY() + 5);
        if (grubor == null) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        askGrubor(grubor, door);
    }

    private void askGrubor(Npc npc, final GameObject door) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (has(HIDEOUT)) {
            if (door != null) {
                walkThrough(door);
                return;
            }
            c.player("Hi").npc("Hi, I'm a little busy right now").start();
            return;
        }
        c.npc("Yes? what do you want?");
        if (!running() || !blackArm()) {
            c.options(new Choice(new String[] {
                "Would you like to have your windows refitting?",
                "I want to come in",
                "Do you want to trade" }) {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0: c.npc("Don't be daft, we don't have any windows"); break;
                        case 1: c.npc("No, go away"); break;
                        default: c.npc("No I'm busy"); break;
                    }
                }
            });
            c.start();
            return;
        }
        c.options(new Choice(new String[] {
            "Rabbit's foot",
            "Four leaved clover",
            "Lucky Horseshoe",
            "Black cat" }) {
            public void picked(int option, Conversation c) {
                if (option != 1) {
                    c.npc("What are you on about").npc("Go away");
                    return;
                }
                c.npc("Oh you're one of the gang are you")
                 .npc("Just a second I'll let you in")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         set(HIDEOUT);
                         if (door != null) {
                             walkThrough(door);
                         }
                     }
                 });
            }
        });
        c.start();
    }

    /**
     * Trobert, who runs the Brimhaven end of the black arm gang.
     *
     * He hands over the papers of a black knight his people waylaid on the road
     * and will replace them if they are lost, right up until Grip has seen
     * them, after which the disguise has already been used and he has no more
     * to say.
     */
    private void talkToTrobert(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (!running() || !blackArm()) {
            return;
        }
        if (has(GRIP_ID)) {
            return;
        }
        if (count(ID_PAPER) < 1 && has(GOT_PAPER)) {
            c.player("I have lost Hartigen's I.D paper")
             .npc("That was careless")
             .npc("He had a spare fortunatley")
             .npc("Here it is")
             .npc("Be more careful this time")
             .give(new InvItem(ID_PAPER, 1))
             .start();
            return;
        }
        if (count(ID_PAPER) > 0) {
            return;
        }
        c.npc("Hi, welcome to our Brimhaven headquarters")
         .npc("I'm Trobert and I'm in charge here")
         .options(new Choice(new String[] {
             "So can you help me get Scarface Pete's candlesticks?",
             "Pleased to meet you" }) {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     return;
                 }
                 c.npc("Well we have made some progress there")
                  .npc("We know one of the keys to Pete's treasure room is carried by Grip the head guard")
                  .npc("So we thought it might be good to get close to the head guard")
                  .npc("Grip was taking on a new deputy called Hartigen")
                  .npc("Hartigen was an Asgarnian black knight")
                  .npc("However he was deserting the black knight fortress and seeking new employment")
                  .npc("We managed to waylay him on the way here")
                  .npc("We now have his i.d paper")
                  .npc("Next we need someone to impersonate the black knight")
                  .options(new Choice(new String[] {
                      "I volunteer to undertake that mission",
                      "Well good luck then" }) {
                      public void picked(int option, Conversation c) {
                          if (option != 0) {
                              return;
                          }
                          c.npc("Well here's the I.d")
                           .npc("Take that to the guard room at Scarface Pete's mansion")
                           .give(new InvItem(ID_PAPER, 1))
                           .then(new Effect() {
                               public void run(Conversation c) {
                                   set(GOT_PAPER);
                               }
                           });
                      }
                  });
             }
         });
        c.start();
    }

    /**
     * Garv on the mansion door.
     *
     * He wants Hartigen's papers and Hartigen's armour, and he checks both. Once
     * Grip is dead he is not there to check anything -- an addition, so that the
     * player who shot Grip through the wall can get in and take the keys off
     * him.
     */
    private void talkToGarv(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (!running() || count(ID_PAPER) < 1) {
            c.npc("Hello, what do you want?")
             .options(new Choice(new String[] {
                 "Can I go in there?",
                 "I want for nothing" }) {
                 public void picked(int option, Conversation c) {
                     if (option == 0) { c.npc("No in there is private"); }
                     else             { c.npc("You're one of a very lucky few then"); }
                 }
             });
            c.start();
            return;
        }
        c.npc("Hello, what do you want?")
         .player("Hi, I'm Hartigen")
         .player("I've come to work here");
        if (!disguised()) {
            c.npc("Hartigen the black knight?")
             .npc("I don't think so - he doesn't dress like that");
        } else {
            c.npc("So have you got your i.d paper?")
             .npc("You had better come in then")
             .npc("Grip will want to talk to you")
             .then(new Effect() {
                 public void run(Conversation c) {
                     /* Talking to Garv directly counts the same as showing the
                        papers at the door -- without this the door made the
                        player sit through the identical conversation twice. */
                     set(GARV_PASSED);
                 }
             });
        }
        c.start();
    }

    private void openMansionDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != MANSION_DOOR_X || door.getY() != MANSION_DOOR_Y) {
            return;
        }
        if (has(GARV_PASSED)) {
            // Once Garv has looked at the papers he does not ask again.
            walkThrough(door);
            return;
        }
        Npc garv = world.getNpc(GARV, p.getX() - 5, p.getX() + 5, p.getY() - 5, p.getY() + 5);
        if (garv == null) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        final Conversation c = new Conversation(p, garv);
        c.npc("Where do you think you're going?");
        if (!running() || count(ID_PAPER) < 1) {
            if (running()) {
                c.player("Hi, I'm Hartigen")
                 .player("I've come to work here")
                 .npc("So have you got your i.d paper?")
                 .player("No I must have left it in my other suit of armour");
            }
            c.start();
            return;
        }
        c.player("Hi, I'm Hartigen")
         .player("I've come to work here");
        if (!disguised()) {
            c.npc("Hartigen the black knight?")
             .npc("I don't think so - he doesn't dress like that")
             .start();
            return;
        }
        final GameObject open = door;
        c.npc("So have you got your i.d paper?")
         .npc("You had better come in then")
         .npc("Grip will want to talk to you")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(GARV_PASSED);
                 walkThrough(open);
             }
         });
        c.start();
    }

    /**
     * Grip, head guard, who thinks the player is his new deputy.
     *
     * The papers are taken the moment he asks for them, and after that he will
     * go over the same three subjects as often as he is asked. The key he hands
     * out is the one he cannot place, which is the whole hinge of the quest: it
     * opens a door on the other side of the building that he has never been
     * through.
     */
    private void talkToGrip(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (!running() || !blackArm()) {
            return;
        }
        if (!has(GRIP_ID)) {
            c.player("Hi I am Hartigen")
             .player("I've come to take the job as your deputy")
             .npc("Ah good at last, you took you're time getting here")
             .npc("Now let me see")
             .npc("Your quarters will be that room nearest the sink")
             .npc("I'll get your hours of duty sorted in a bit")
             .npc("Oh and have you got your I.D paper")
             .npc("Internal security is almost as important as external security for a guard");
            if (count(ID_PAPER) < 1) {
                c.player("Oh dear I don't have that with me any more").start();
                return;
            }
            c.take(ID_PAPER, 1)
             .message("You hand your I.D paper to grip")
             .then(new Effect() {
                 public void run(Conversation c) {
                     set(GRIP_ID);
                 }
             });
        }
        gripMenu(c);
        c.start();
    }

    private void gripMenu(Conversation c) {
        c.options(new Choice(new String[] {
            "So can I guard the treasure room please",
            "So what do my duties involve?",
            "Well I'd better sort my new room out" }) {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("Well I might post you outside it sometimes")
                         .npc("I prefer to be the only one allowed inside though")
                         .npc("There's some pretty valuable stuff in there")
                         .npc("Those keys stay only with the head guard and with Scarface Pete");
                        break;
                    case 1:
                        duties(c);
                        break;
                    default:
                        c.npc("Yeah I'll give you time to settle in");
                        break;
                }
            }
        });
    }

    private void duties(Conversation c) {
        c.npc("You'll have various guard duty shifts")
         .npc("I may have specific tasks to give you as they come up")
         .npc("If anything happens to me you need to take over as head guard")
         .npc("You'll find Important keys to the treasure room and Pete's quarters")
         .npc("Inside my jacket")
         .options(new Choice(new String[] {
             "Anything I can do now?",
             "So can I guard the treasure room please",
             "Well I'd better sort my new room out" }) {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Well I might post you outside it sometimes")
                      .npc("I prefer to be the only one allowed inside though")
                      .npc("There's some pretty valuable stuff in there")
                      .npc("Those keys stay only with the head guard and with Scarface Pete");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Yeah I'll give you time to settle in");
                     return;
                 }
                 if (count(MISC_KEY) > 0) {
                     c.npc("Can't think of anything right now");
                     return;
                 }
                 c.npc("Hmm well you could find out what this key does")
                  .npc("Apparantly it's to something in this building")
                  .npc("Though I don't for the life of me know what")
                  .message("Grip hands you a key")
                  .give(new InvItem(MISC_KEY, 1));
             }
         });
    }

    // --------------------------------------------------------- phoenix way --

    /**
     * Alfonse, who waits tables at the Shrimp and Parrot.
     *
     * The order he offers to take is served by "The Shrimp and Parrot" in
     * Shops.xml. That entry did not exist while this was written, so the
     * option is still built conditionally: a server whose shop table has been
     * edited down should drop the option rather than offer it and then fail.
     */
    private void talkToAlfonse(Npc npc) {
        final Shop shop = world.getShop(npc);
        final ArrayList<String> opts = new ArrayList<String>();
        final ArrayList<Integer> keys = new ArrayList<Integer>();
        if (shop != null) {
            opts.add("Yes please");  keys.add(0);
        }
        opts.add("No thankyou");     keys.add(1);
        if (running() && phoenix() && !has(GHERKIN)) {
            opts.add("Do you sell Gherkins?"); keys.add(2);
        }
        new Conversation(getOwner(), npc)
            .npc("Welcome to the shrimp and parrot")
            .npc("Would you like to order sir?")
            .options(new Choice(opts.toArray(new String[opts.size()])) {
                public void picked(int option, Conversation c) {
                    switch (keys.get(option).intValue()) {
                        case 0:
                            c.then(new Effect() {
                                public void run(Conversation c) {
                                    Player p = c.getPlayer();
                                    c.stop();
                                    p.setAccessingShop(shop);
                                    p.getActionSender().showShop(shop);
                                }
                            });
                            break;
                        case 2:
                            c.npc("Hmm ask Charlie the cook round the back")
                             .npc("He may have some Gherkins for you")
                             .then(new Effect() {
                                 public void run(Conversation c) {
                                     set(GHERKIN);
                                 }
                             });
                            break;
                    }
                }
            })
            .start();
    }

    private void openKitchenDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != KITCHEN_DOOR_X || door.getY() != KITCHEN_DOOR_Y) {
            return;
        }
        if (has(GHERKIN) || (completed() && phoenix())) {
            walkThrough(door);
            return;
        }
        Npc alfonse = world.getNpc(ALFONSE, p.getX() - 8, p.getX() + 8, p.getY() - 8, p.getY() + 8);
        if (alfonse == null) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        new Conversation(p, alfonse)
            .npc("Hey you can't go through there, that's private")
            .start();
    }

    /**
     * Charlie, head cook and phoenix man in Brimhaven.
     *
     * He describes the way in that his gang built and cannot use. The key he
     * hands over at the end of it is this server's addition -- in Jagex's
     * version it comes across from the black arm partner, and without a partner
     * the passage he has just described is a dead end.
     */
    private void talkToCharlie(Npc npc) {
        if (!phoenix() || !running()) {
            return;
        }
        final Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hey what are you doing round here")
         .options(new Choice(new String[] {
             "I'm looking for a gherkin",
             "I'm a fellow member of the phoenix gang",
             "Just exploring" }) {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     c.npc("This kitchen isn't for exploring")
                      .npc("It's a private establishment, now get out");
                     return;
                 }
                 c.npc("Aha a fellow phoenix");
                 if (option == 0) {
                     c.npc("What brings you to Brimhaven?");
                 }
                 c.options(new Choice(new String[] {
                     "Sun, sand and the fresh sea air",
                     "I want to steal Scarface Pete's candlesticks" }) {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.npc("Well they are some things we have here yes");
                             return;
                         }
                         candlesticks(c);
                     }
                 });
             }
         });
        c.start();
    }

    private void candlesticks(Conversation c) {
        c.npc("Ah yes the candlesticks")
         .npc("Our progress hasn't been amazing on that front")
         .npc("Though we can help you a bit")
         .npc("The setting up of this restaurant is the start of things")
         .npc("We have a secret door out of the back of here")
         .npc("It leads through the back of Mr Olbor's garden")
         .npc("At the other side of Olbor's garden is an old side entrance")
         .npc("To Scarface Pete's mansion")
         .npc("It seems to have been blocked off from the rest of the mansion")
         .npc("We can't find a way through, we're sure it must be of some use though")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(CHARLIE_TOLD);
             }
         });
        // The key to the side entrance is Grip's, and Grip only talks to the
        // black arm in disguise. It crosses the gang line by trade, or not at
        // all -- that is the two player design and it is kept.
    }

    private void pushPanel(GameObject panel) {
        if (panel.getX() != PANEL_X || panel.getY() != PANEL_Y) {
            return;
        }
        getOwner().getActionSender().sendMessage("You just went through a secret door");
        walkThrough(panel);
    }

    /**
     * The side door into the sniper's room. Getting Grip into the line of the
     * hole is the black arm partner's job, at the drinks cabinet inside.
     */
    private void openSideDoor(GameObject door, boolean withKey) {
        Player p = getOwner();
        if (door.getX() != SIDE_DOOR_X || door.getY() != SIDE_DOOR_Y) {
            return;
        }
        if (count(MISC_KEY) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            p.getActionSender().sendMessage("This room isn't a lot of use on it's own");
            p.getActionSender().sendMessage("Maybe I can get extra help from the inside somehow");
            p.getActionSender().sendMessage("I wonder if any of the other players have found a way in");
            return;
        }
        p.getActionSender().sendMessage("You unlock the door");
        p.getActionSender().sendMessage("You go through the door");
        walkThrough(door);
    }

    // -------------------------------------------------- the mansion itself --

    /**
     * Grip's drinks cabinet, and the one line the mansion guards have.
     *
     * Snooping in it brings Grip through from the guard room, which is what it
     * is for. It also holds a bottle, which Jagex only let anybody have once
     * Grip was not there to object.
     */
    private void searchCupboard(final GameObject cupboard) {
        Player p = getOwner();
        if (cupboard.getX() != CUPBOARD_X || cupboard.getY() != CUPBOARD_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (has(GRIP_DEAD) || !running()) {
            if (count(WHISKY) > 0) {
                p.getActionSender().sendMessage("You search the cupboard but find nothing");
                return;
            }
            p.getInventory().add(new InvItem(WHISKY, 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You find a bottle of whisky");
            return;
        }
        Npc guard = world.getNpc(MANSION_GUARD, p.getX() - 8, p.getX() + 8, p.getY() - 8, p.getY() + 8);
        if (guard == null) {
            lureGrip();
            return;
        }
        new Conversation(p, guard)
            .npc("I don't think Mr Grip will like you opening that up")
            .npc("That's his drinks cabinet")
            .options(new Choice(new String[] {
                "He won't notice me having a quick look",
                "Ok I'll leave it" }) {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            lureGrip();
                        }
                    });
                }
            })
            .start();
    }

    /** Grip comes through to his cabinet and says what he thinks of it. */
    private void lureGrip() {
        Player p = getOwner();
        Npc grip = world.getNpc(GRIP, LURE_X - 20, LURE_X + 20, LURE_Y - 20, LURE_Y + 20);
        if (grip == null) {
            return;
        }
        grip.setLocation(Point.location(LURE_X, LURE_Y), true);
        new Conversation(p, grip)
            .npc("Hey what are you doing there")
            .npc("That's my drinks cabinet get away from it")
            .start();
    }

    private void openTreasureDoor(GameObject door, boolean withKeys) {
        Player p = getOwner();
        if (door.getX() != TREASURE_DOOR_X || door.getY() != TREASURE_DOOR_Y) {
            return;
        }
        if (count(BUNCH_KEYS) < 1) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendMessage("you open the door");
        p.getActionSender().sendMessage("You go through the door");
        walkThrough(door);
    }

    /**
     * Scarface Pete's chest, and two candlesticks in it.
     *
     * Two, because Jagex meant one for each of the pair that got this far. A
     * player who got here alone keeps the spare.
     */
    private void searchChest(GameObject chest) {
        Player p = getOwner();
        if (chest.getX() != CHEST_X || chest.getY() != CHEST_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (has(CHEST_ROBBED) || !running()) {
            p.getActionSender().sendMessage("You search the chest but find nothing");
            return;
        }
        p.getInventory().add(new InvItem(CANDLESTICK, 1));
        p.getInventory().add(new InvItem(CANDLESTICK, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find two valuable candlesticks");
        set(CHEST_ROBBED);
    }

    /** Swing the chest shut for a moment. The map reopens it. */
    private void shutChest(GameObject chest) {
        Player p = getOwner();
        p.getActionSender().sendMessage("You close the chest");
        world.registerGameObject(new GameObject(chest.getLocation(), CHEST_SHUT,
            chest.getDirection(), chest.getType()));
        world.delayedSpawnObject(chest.getLoc(), 3000);
    }

    // ------------------------------------------------------------- doors --

    private void walkThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        if (door.getDirection() == 0) {
            p.teleport(door.getX(), p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
        } else {
            p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(), door.getY(), false);
        }
    }
}
