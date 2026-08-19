/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.quest;

import java.util.Vector;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;

public abstract class Quest {
    protected Player owner = null;
    protected String name = "Pheonix Gang";
    protected int uid = -1;
    protected int stage = -1;
    protected int finalStage = -1;
    protected Vector<Integer> associatedNpcs = new Vector();
    protected Vector<Integer> commandNpcs = new Vector();
    protected Vector<Integer> associatedItems = new Vector();
    protected Vector<Integer> associatedObjects = new Vector();
    /**
     * Single placements this quest claims, as "id:x:y". Kept apart from
     * associatedObjects so that the two claims stay distinguishable: one is
     * "this object is mine", the other is "this object standing here is mine".
     */
    protected Vector<String> associatedPlacements = new Vector();
    /**
     * Doors this quest claims. Kept apart from associatedObjects because DoorDef
     * and GameObjectDef are separate tables that both start at zero: the two
     * lists hold the same numbers meaning different things.
     */
    protected Vector<Integer> associatedDoors = new Vector();
    protected Vector<Integer> associatedDrops = new Vector();
    /**
     * Further values of the stage that also mean "finished".
     *
     * A quest with one ending never touches this and behaves exactly as it did:
     * finalStage alone decides. It exists for the handful of quests Jagex wrote
     * with mutually exclusive endings -- Temple of Ikov can be finished for the
     * guardians of Armadyl or for Lucien, and the two leave the player holding
     * different things -- where the stage has to record which ending happened
     * and so cannot be one fixed number.
     */
    protected Vector<Integer> otherFinalStages = new Vector();
    /*
     * Declarative metadata: what the quest is, what it takes to start, and
     * what finishing it pays. Declared in define() like the associations
     * above, and load-bearing in the same way -- grantRewards() pays out of
     * rewardItems/rewardExp, so the declaration IS the granting mechanism
     * and cannot drift from it. The quest-data dump reads these same fields
     * to build the website's manual page for the quest.
     *
     * Requirements are declared, not enforced here: each quest's own
     * dialogue decides how to turn away an unqualified player, because
     * Jagex's scripts refuse in character ("You need level 31 cooking...")
     * and no one refusal fits all. unmetRequirements() does the checking
     * so a quest need only voice the result.
     */
    protected String description = "";
    protected String startPoint = "";
    protected String speakTo = "";
    protected String missionLength = "";
    /** Level requirements to start, as {skill index, level}. */
    protected Vector<int[]> requiredLevels = new Vector();
    /** Quests (by uid) that must be completed first. */
    protected Vector<Integer> requiredQuests = new Vector();
    /** Requirements no level or quest check captures, in Jagex's manual wording. */
    protected Vector<String> requiredOther = new Vector();
    /** Items handed over on completion, as {item id, amount}. */
    protected Vector<int[]> rewardItems = new Vector();
    /**
     * Experience granted on completion, as {skill index, base, perLevel}:
     * the amount is base + maxStat * perLevel, because RSC quest xp scales
     * with the player's level in the skill (Doric's pays 175 + 75/level of
     * mining). A flat award is just perLevel 0.
     */
    protected Vector<int[]> rewardExp = new Vector();
    /** Rewards granted by the quest's own code paths, described for the manual. */
    protected Vector<String> rewardOther = new Vector();
    protected World world = World.getWorld();

    public Quest(Player owner, Integer uid) {
        this.owner = owner;
        this.uid = uid;
        // Nothing to load yet: QuestManager builds every quest from the Player
        // constructor, which runs before the login packet carrying this player's
        // saved progress has been read. QuestManager.restoreProgress() calls
        // load() again once it has arrived.
        this.load();
    }

    public abstract void define();

    public void triggerEntity(QuestTrigger type, Entity entity) {
    }

    /**
     * The two-subject form, for ITEM_ON_OBJECT.
     *
     * Defaults to the one-subject form so that every quest written against the
     * original signature keeps working untouched; only a quest that cares which
     * item was used needs to override this one.
     */
    public void triggerEntity(QuestTrigger type, Entity entity, InvItem used) {
        this.triggerEntity(type, entity);
    }

    public abstract void completeQuest();

    /**
     * Answer a named question about this quest's progress, for code outside it.
     *
     * Quests are compiled into the default package and loaded at runtime, so
     * nothing in src/ can name a quest class, let alone its stage constants.
     * getStage() crosses that line but says nothing useful on its own -- "is
     * Dragon slayer at 3 yet" is a question only Dragon slayer can answer.
     *
     * So the question is asked by name instead. The quest decides what the name
     * means; the caller learns a yes or a no and never sees a stage number. Ned
     * asks "ship-ready" before offering to captain one.
     *
     * Unknown keys are false, so a quest need only answer the ones it publishes.
     */
    public boolean reached(String key) {
        return false;
    }

    /**
     * Say whether this quest is stopping the player picking an item up.
     *
     * Asked before the item moves, unlike ITEM_PICKUP, which is told about it
     * afterwards. Some things on the floor are not really there to be taken:
     * the Holy grail sits on the fisher king's table for the whole quest and
     * cannot be lifted until the realm has been put right.
     *
     * Only quests that have associated the item are asked, and the first one to
     * say yes stops the pickup. A quest that refuses must say why itself --
     * silently failing to pick something up reads as a bug.
     */
    public boolean refusesPickup(InvItem item) {
        return false;
    }

    /**
     * Say whether this quest is stopping the player telekinetic-grabbing an
     * item off the floor.
     *
     * Telekinetic grab is a second door onto the same act, and it used to walk
     * straight past {@link #refusesPickup} -- anything a quest was holding
     * down could be lifted from five tiles away instead. It defaults to the
     * same answer, so a quest that has already written refusesPickup gets this
     * closed for free; override it only when the refusal is worded
     * differently, as Jagex worded the god capes' ("I can't use telekinetic
     * grab on this object" rather than the pickup message).
     */
    public boolean refusesTelegrab(InvItem item) {
        return refusesPickup(item);
    }

    /**
     * Say whether this quest is stopping an npc from dying.
     *
     * The counterpart of {@link #refusesPickup}, and asked in the same way:
     * synchronously, before the death happens, of the quests that claimed the
     * npc, first refusal wins, and a quest that refuses must say why. Refusing
     * leaves the npc on one hit point with the fight still running, so it is a
     * reprieve rather than immortality.
     *
     * Chronozon is the reason it exists. He cannot be killed until all four
     * elemental blasts have hit him, and asking here rather than in the combat
     * code covers melee, ranged and magic at once -- the alternative was the
     * same test written into three separate damage paths.
     */
    public boolean refusesKill(Npc npc) {
        return false;
    }

    /**
     * Say whether this quest is stopping a fight from starting at all.
     *
     * Asked once, when the player has walked up to an npc it claimed and is
     * about to swing or shoot; the same contract as {@link #refusesKill} --
     * synchronous, first refusal wins, and a quest that refuses must say why.
     * Asked on arrival rather than on the click so that a refusing npc can
     * speak, which is what Jagex's do.
     *
     * Lucien is the reason it exists. Standing in his house he is level 21 and
     * perfectly killable, and the only thing between the player and the wrong
     * ending is that he talks them out of it unless they are wearing the
     * Pendant of Armadyl. That is a fact about the quest, not about combat.
     */
    public boolean refusesAttack(Npc npc) {
        return false;
    }

    /**
     * Tell this quest that a spell landed on an npc it claimed.
     *
     * Its own dispatch rather than a QuestTrigger because the spell is a number
     * and not an entity, and the triggers carry entities. Reported after the
     * damage and before the death, so a quest can count what has hit an npc and
     * then answer {@link #refusesKill} with the count.
     *
     * The damage comes along because a cast that rolled a zero still arrives
     * here, and Chronozon asks to be "successfully damaged" once by each of the
     * four blasts. Whether a nought counts is the quest's business, not the
     * dispatcher's.
     */
    public void spellCast(Npc npc, int spellId, int damage) {
    }

    /**
     * Tell this quest that a spell was cast at a piece of scenery it claimed.
     *
     * The type-5 spells -- the four Charge Orb casts -- are aimed at scenery
     * rather than at anything alive, and the client has always had a packet for
     * it. There is no damage to report and nothing to refuse afterwards, so
     * unlike the npc cast this one carries only the spell.
     *
     * Legend's quest is why it exists: the Dark Metal Gate in the Kharazi
     * underground opens to a charged orb and to nothing else, and an orb cast
     * is not an item used on an object -- the orb is spent as a rune by the
     * spell, not handed over.
     *
     * The runes are still in the player's inventory when this runs. Whether
     * they are taken depends on whether any quest claimed the object at all,
     * which is the dispatcher's decision and not this one's.
     */
    public void spellCast(GameObject object, int spellId) {
    }

    /**
     * Tell this quest that an arrow landed on an npc it claimed.
     *
     * The exact counterpart of {@link #spellCast}, reported from the same place
     * in the damage path and for the same reason: the Fire warrior of lesarkus
     * has to be opened up with an ice arrow before anything can hurt him, which
     * is a fact about which missile arrived and not about how much it hurt.
     *
     * The arrow id is the one the bow actually consumed, so a quest can tell an
     * ice arrow from the rune arrow fired straight after it.
     */
    public void rangedShot(Npc npc, int arrowId, int damage) {
    }

    /**
     * Tell this quest that something happened elsewhere, by the same naming.
     *
     * The mirror of {@link #reached}, and the only way a quest's stage can be
     * moved from outside the quest: the caller reports the event, the quest
     * decides whether that means anything and what stage it lands on. Nobody
     * outside a quest ever calls setStage().
     *
     * This exists for npcs that belong to more than one quest and so live in
     * src/ as handlers rather than inside either of them -- Ned agreeing to sail
     * to Crandor is Dragon slayer's business, but Ned is also Prince Ali's.
     *
     * Ignoring an unknown key is deliberate: a handler may report an event to a
     * quest that has not started, or does not care.
     */
    public void note(String key) {
    }

    public final synchronized Player getOwner() {
        return this.owner;
    }

    public final int getUID() {
        return this.uid;
    }

    public final void setStage(int stage) {
        this.stage = stage;
        this.save();
        // completeQuest() last: it hands out the rewards, and it should not be
        // able to run against a stage that has not been recorded yet.
        if (this.isFinalStage(stage)) {
            this.completeQuest();
            /*
             * The one green line of a quest ending, sent after the quest's own
             * white "Well done.You have completed the..." message. "haved" is
             * Jagex's -- the real client showed exactly this, typo and all, so
             * it is not ours to correct. Custom records (points 0) say nothing.
             */
            int points = Quests.points(this.uid);
            if (this.owner != null && points > 0) {
                this.owner.getActionSender().sendMessage(
                    "@gre@You haved gained " + points + " quest points!");
            }
            if (this.owner != null) {
                // The quest-point total is derived, so it only changes on screen
                // when the client is told to redraw.
                this.owner.getActionSender().sendStats();
            }
        }
        if (this.owner != null) {
            // Every stage write moves the quest tab -- not started to started,
            // one stage to the next, or on to complete just above -- and the tab
            // only changes on screen when the client is told to redraw. Sending
            // it here rather than only on completion used to leave every quest
            // showing stale ("not started") in the journal from the moment it
            // began until the player's next login, which is when sendQuests()
            // was otherwise called.
            this.owner.getActionSender().sendQuests();
        }
    }

    /**
     * Identify this quest for saving. Ids are fixed per quest and must never be
     * reused -- a saved row is only meaningful next to the quest that wrote it.
     * Call this from define().
     */
    public final void setUID(int uid) {
        this.uid = uid;
    }

    public final void setFinalStage(int stage) {
        this.finalStage = stage;
    }

    public final int getFinalStage() {
        return this.finalStage;
    }

    /**
     * Declare another stage value that also finishes this quest.
     *
     * Additive: setFinalStage() still names the ending, and a quest that never
     * calls this is unaffected. Call it from define(), once per alternative
     * ending, alongside setFinalStage(). Every declared value must be reachable
     * -- completed() is exact equality against this set, so a stage that is
     * "finished plus a bit" finishes nothing.
     */
    public final void addFinalStage(int stage) {
        Integer boxed = Integer.valueOf(stage);
        if (!this.otherFinalStages.contains(boxed)) {
            this.otherFinalStages.add(boxed);
        }
    }

    /**
     * True if this stage value is an ending, whether the declared one or an
     * alternative.
     */
    public final boolean isFinalStage(int stage) {
        return stage == this.finalStage || this.otherFinalStages.contains(Integer.valueOf(stage));
    }

    public final boolean completed() {
        return this.isFinalStage(this.stage);
    }

    public final int getStage() {
        return this.stage;
    }

    public final void setName(String name) {
        this.name = name;
    }

    // ------------------------------------------------- declarative metadata --

    /** One-line description for the manual, in Jagex's own wording where it survives. */
    public final void describe(String text) {
        this.description = text;
    }

    /** Where the quest starts, as the manual's "Start point" line. */
    public final void setStartPoint(String text) {
        this.startPoint = text;
    }

    /** Who begins it, as the manual's "Speak to" line. */
    public final void setSpeakTo(String text) {
        this.speakTo = text;
    }

    /** The manual's "Mission length" line: Short, Medium, Long... */
    public final void setMissionLength(String text) {
        this.missionLength = text;
    }

    /** Declare that starting needs this level in this skill (skill index as Player stats). */
    public final void requireLevel(int stat, int level) {
        this.requiredLevels.add(new int[]{stat, level});
    }

    /** Declare that starting needs this quest (by uid) completed first. */
    public final void requireQuest(int uid) {
        if (!this.requiredQuests.contains(uid)) {
            this.requiredQuests.add(uid);
        }
    }

    /** Declare a requirement no level or quest check captures ("Ability to defeat a level 22 warrior"). */
    public final void require(String text) {
        this.requiredOther.add(text);
    }

    /** Declare that completion hands over this item. Granted by grantRewards(). */
    public final void rewardItem(int id, int amount) {
        this.rewardItems.add(new int[]{id, amount});
    }

    /**
     * Declare that completion grants base + maxStat*perLevel experience in a
     * skill -- RSC quest xp scales with level. Granted by grantRewards().
     */
    public final void rewardExp(int stat, int base, int perLevel) {
        this.rewardExp.add(new int[]{stat, base, perLevel});
    }

    /** Describe, for the manual, a reward the quest's own code grants (access, unlocks...). */
    public final void rewardOther(String text) {
        this.rewardOther.add(text);
    }

    /**
     * Pay out the declared rewards: every rewardItem into the inventory, every
     * rewardExp at base + maxStat*perLevel. completeQuest() calls this instead
     * of granting by hand, so the manual's numbers and the paid numbers are the
     * same numbers. Anything beyond items and xp stays in the quest's own code
     * (and is declared with rewardOther so the manual can say so).
     */
    public final void grantRewards() {
        Player p = this.owner;
        if (p == null) {
            return;
        }
        for (int i = 0; i < this.rewardItems.size(); ++i) {
            int[] reward = this.rewardItems.get(i);
            p.getInventory().add(new InvItem(reward[0], reward[1]));
        }
        if (!this.rewardItems.isEmpty()) {
            p.getActionSender().sendInventory();
        }
        for (int i = 0; i < this.rewardExp.size(); ++i) {
            int[] reward = this.rewardExp.get(i);
            p.incExp(reward[0], reward[1] + p.getMaxStat(reward[0]) * reward[2], false);
            p.getActionSender().sendStat(reward[0]);
        }
    }

    /**
     * The declared start requirements this player does not meet, as sentences
     * a refusal can voice; empty means they qualify. Levels are checked
     * against max stats and quests against completion. requiredOther entries
     * are prose, not checkable, so they are declared for the manual but never
     * block anyone here -- the quest's own code enforces those its own way.
     */
    public final Vector<String> unmetRequirements() {
        Vector<String> unmet = new Vector<String>();
        if (this.owner == null) {
            return unmet;
        }
        for (int i = 0; i < this.requiredLevels.size(); ++i) {
            int[] need = this.requiredLevels.get(i);
            if (this.owner.getMaxStat(need[0]) < need[1]) {
                unmet.add("Level " + need[1] + " " + org.rscdaemon.server.util.Formulae.statArray[need[0]]);
            }
        }
        for (int i = 0; i < this.requiredQuests.size(); ++i) {
            int uid = this.requiredQuests.get(i);
            if (this.owner.getQuestManager() == null || !this.owner.getQuestManager().completed(uid)) {
                unmet.add("Completion of " + Quests.name(uid));
            }
        }
        return unmet;
    }

    /**
     * Write this quest's stage onto the player. The player's map is what the
     * save packet serialises, so this is all that "saving" means here -- the
     * trip to the database happens when the player is saved, not per stage.
     */
    public final boolean save() {
        if (this.owner == null || this.uid < 0) {
            return false;
        }
        this.owner.setQuestStage(this.uid, this.stage);
        return true;
    }

    /** Take this quest's stage back off the player. */
    public final boolean load() {
        if (this.owner == null || this.uid < 0) {
            return false;
        }
        this.stage = this.owner.getQuestStage(this.uid);
        return true;
    }

    // ------------------------------------------------------------ variables --

    /**
     * Per-player persistent quest variables.
     *
     * A quest that needs to remember more than its stage -- lever positions,
     * counters, flags that must survive a logout -- stores each value in a
     * numbered slot here. The value rides the same player quest-stage map the
     * stage itself uses (the thing the save packet already serialises and
     * rscd_quests already stores), under a synthetic id no real quest owns:
     * VAR_BASE + uid * VARS_PER_QUEST + slot. That mapping is injective, the
     * ids sit far above every real uid and every FIRST_CUSTOM record, and
     * QuestManager.fillProgress skips ids the client has no quest-tab row
     * for -- so nothing new is drawn, sent, or scored.
     *
     * This formalises what GangMembership and GodCharges each hand-rolled
     * with a private custom id. Values must be non-negative: a negative
     * stage means "remove" to Player.setQuestStage, so getVar answers the
     * fallback for a slot that was never set (or was cleared with clearVar).
     */
    public static final int VAR_BASE = 2000;
    public static final int VARS_PER_QUEST = 4;

    private int varId(int slot) {
        if (slot < 0 || slot >= VARS_PER_QUEST) {
            throw new IllegalArgumentException("quest var slot out of range: " + slot);
        }
        return VAR_BASE + this.uid * VARS_PER_QUEST + slot;
    }

    /** Store a non-negative value in this quest's numbered slot. Persisted. */
    protected final void setVar(int slot, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("quest var must be non-negative: " + value);
        }
        this.owner.setQuestStage(varId(slot), value);
    }

    /** The value stored in the slot, or fallback if it was never set. */
    protected final int getVar(int slot, int fallback) {
        int value = this.owner.getQuestStage(varId(slot));
        return value < 0 ? fallback : value;
    }

    /** Forget the slot entirely -- getVar answers its fallback again. */
    protected final void clearVar(int slot) {
        this.owner.setQuestStage(varId(slot), -1);
    }

    public final void associateNpc(int id) {
        if (!this.associatedNpcs.contains(id)) {
            this.associatedNpcs.add(id);
        }
    }

    public final boolean npcAssociated(int id) {
        return this.associatedNpcs.contains(id);
    }

    /**
     * Ask to be told when this npc's right-click command is used.
     *
     * A list of its own, and not a use of associateNpc(), because the ordinary
     * association is far too wide a claim for this one trigger. Black knights'
     * fortress associates guard 100 so it can talk to him, and a guard is one
     * of the most-pickpocketed npcs in the game; if the command dispatcher
     * asked the ordinary question it would hand every one of those guards to a
     * quest that has no NPC_COMMAND branch at all, and pickpocketing them would
     * do nothing whatsoever with no error anywhere to say why.
     *
     * So a quest that wants the command has to say so, and saying so is the
     * whole claim: Thieving does not get a look at an npc claimed here. That is
     * right for the three npcs that use it -- the two Digsite workmen, whose
     * pockets hold quest items and are the quest's business, and the Mercenary
     * Captain, who is watched rather than robbed.
     */
    public final void associateNpcCommand(int id) {
        this.associateNpc(id);
        if (!this.commandNpcs.contains(id)) {
            this.commandNpcs.add(id);
        }
    }

    public final boolean npcCommandAssociated(int id) {
        return this.commandNpcs.contains(id);
    }

    public final void associateItem(int id) {
        if (!this.associatedItems.contains(id)) {
            this.associatedItems.add(id);
        }
    }

    public final boolean itemAssociated(int id) {
        return this.associatedItems.contains(id);
    }

    /**
     * Ask to be told when an item id is dropped on the floor.
     *
     * Deliberately a list of its own rather than a use of associateItem().
     * Claiming an item takes its inventory command outright -- InvActionHandler
     * stops dead for an associated item -- so a quest that merely wants to
     * notice a drop would silently stop cheese being edible for every player
     * who has that quest loaded, which is every player. Wanting to watch a drop
     * and wanting to own an item are different claims, so they are different
     * lists, for the same reason doors are separate from scenery.
     *
     * Nothing is consumed by this: the drop has already happened by the time a
     * quest hears about it, so several quests may watch the same id and none of
     * them can stop the others. Witch's house watches cheese with it.
     */
    public final void associateDroppedItem(int id) {
        if (!this.associatedDrops.contains(id)) {
            this.associatedDrops.add(id);
        }
    }

    public final boolean dropAssociated(int id) {
        return this.associatedDrops.contains(id);
    }

    public final void associateObject(int id) {
        if (!this.associatedObjects.contains(id)) {
            this.associatedObjects.add(id);
        }
    }

    /**
     * Claim one placement of an object rather than every object with its id.
     *
     * Claiming an id claims it everywhere, and for scenery that is often more
     * than a quest can pay for: rock 210 is the runite rock, so Jungle potion
     * asking for the one at (428,819) must not take mining away from the four
     * rune rocks in the wilderness and the Heroes' guild. A quest cannot hand
     * mining back -- ObjectAction skips its whole body for a claimed object --
     * so the only honest answer is not to claim those placements in the first
     * place.
     *
     * Use this whenever the id is shared and the plain form only when the
     * object exists nowhere else, which for a quest's own scenery is common:
     * there are exactly three Jungle Vines in the world and all three are this
     * quest's. The two forms live in the same object list either way, so a
     * quest may mix them.
     */
    public final void associateObject(int id, int x, int y) {
        String at = id + ":" + x + ":" + y;
        if (!this.associatedPlacements.contains(at)) {
            this.associatedPlacements.add(at);
        }
    }

    /** True if this quest claimed the whole id, wherever it stands. */
    public final boolean objectAssociated(int id) {
        return this.associatedObjects.contains(id);
    }

    /**
     * True if this quest claimed this object -- by id, or by this one placement
     * of it. This is the test dispatch uses; the id-only form above remains for
     * callers that have an id and no position.
     */
    public final boolean objectAssociated(int id, int x, int y) {
        return this.associatedObjects.contains(id)
            || this.associatedPlacements.contains(id + ":" + x + ":" + y);
    }

    /**
     * Claim a door id, the way associateObject() claims scenery.
     *
     * The same warning applies and applies harder: there are only a couple of
     * hundred door definitions and most of the world's doors share a handful of
     * them, so claiming an id claims every door in the game that uses it. Guard
     * on coordinates and give the rest of them their ordinary behaviour back.
     */
    public final void associateDoor(int id) {
        if (!this.associatedDoors.contains(id)) {
            this.associatedDoors.add(id);
        }
    }

    /**
     * Claim one placement of a door, the way associateObject(id,x,y) claims one
     * placement of a piece of scenery.
     *
     * Guarding on coordinates inside the quest is only good enough while the
     * ordinary behaviour is something the quest can perform itself. Door 94 is
     * the picklock door, and there are four of them: the one in Handelmort's
     * mansion belongs to Tribal totem and the other three belong to Thieving,
     * which is not written yet. Claiming the id would quietly make sure it
     * never could be.
     */
    public final void associateDoor(int id, int x, int y) {
        String at = "d" + id + ":" + x + ":" + y;
        if (!this.associatedPlacements.contains(at)) {
            this.associatedPlacements.add(at);
        }
    }

    /** True if this quest claimed the whole door id, wherever it stands. */
    public final boolean doorAssociated(int id) {
        return this.associatedDoors.contains(id);
    }

    /**
     * True if this quest claimed this door -- by id, or by this one placement.
     * This is the test dispatch uses.
     *
     * Placements are keyed "d94:565:586" rather than "94:565:586" so that a
     * door and a piece of scenery sharing a number cannot claim each other:
     * DoorDef and GameObjectDef number from zero independently.
     */
    public final boolean doorAssociated(int id, int x, int y) {
        return this.associatedDoors.contains(id)
            || this.associatedPlacements.contains("d" + id + ":" + x + ":" + y);
    }

    public final void stopTalking() {
        this.owner.setStatus(Action.IDLE);
    }

    public final boolean questStarted() {
        return this.stage != -1;
    }

    public final String getName() {
        return this.name;
    }

    public final void sleep(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
    }

    public void sayMessage(String message) {
        this.owner.informOfChatMessage(new ChatMessage(this.owner, message, this.owner.getNpc()));
    }

    public void sayNpcMessage(String message) {
        this.owner.informOfNpcMessage(new ChatMessage(this.owner.getNpc(), message, this.owner));
    }
}

