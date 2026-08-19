/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.quest;

import java.util.HashMap;
import java.util.Vector;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestLoader;
import org.rscdaemon.server.quest.QuestTrigger;

public class QuestManager {
    private Player owner;
    private HashMap<String, Quest> quests = new HashMap();

    public QuestManager(Player owner) {
        this.owner = owner;
        try {
            this.loadQuests();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final void loadQuests() throws Exception {
        Vector<Class> classes = QuestLoader.getClasses();
        if (classes == null) {
            // Printed and then fell straight into the loop below, so the
            // complaint was immediately followed by the NPE it was warning
            // about. getClasses() is only null before QuestLoader.initClasses()
            // has run, which GameEngine does at startup.
            System.out.println("Problem loading classes at loadQuests() inside QuestManager.java");
            return;
        }
        for (Class c : classes) {
            /* One bad quest must not cost this player the rest of them.
               QuestLoader has already refused anything without this
               constructor, so reaching the catch means the quest's own
               define() threw. */
            try {
                Quest quest = (Quest)c.getConstructor(Player.class, Integer.class).newInstance(this.owner, -1);
                quest.define();
                this.quests.put(c.getName(), quest);
            }
            catch (Exception e) {
                System.out.println("Quest " + c.getName() + " failed to start for "
                    + this.owner.getUsername() + ": " + e);
            }
        }
    }

    /**
     * Re-read every quest's stage from the player, once their saved progress has
     * actually arrived from the login server.
     *
     * Quests are constructed with the Player, which happens on connect, long
     * before the login packet is decoded -- so at construction every quest reads
     * back "not started". This is the second read, and the one that counts.
     */
    public final void restoreProgress() {
        for (Quest quest : this.quests.values()) {
            quest.load();
        }
    }

    /**
     * Mark every finished quest in the array, indexed by quest id.
     *
     * Ids outside the array are ours rather than Jagex's (see Quests.FIRST_CUSTOM)
     * and simply have nowhere to go in the stock client's quest tab.
     */
    public final void fillCompletion(boolean[] done) {
        for (Quest quest : this.quests.values()) {
            int uid = quest.getUID();
            if (uid >= 0 && uid < done.length && quest.completed()) {
                done[uid] = true;
            }
        }
    }

    /*
     * As fillCompletion, but three-valued: 0 not started, 1 started, 2
     * complete. RSC's own quest tab never had a middle state -- it painted
     * everything red until the moment it finished, which classic.runescape.wiki
     * confirms -- but there was nothing stopping the client from knowing one:
     * the protocol already carries a per-quest byte, this is just more of it.
     * That is an addition on top of the authentic tab, not a change to it; a
     * quest with no stage recorded yet reads 0 exactly as it always has.
     *
     * "Started" is stage != -1, Quest's own sentinel for never having called
     * setStage() -- see the field's declaration. completed() is checked first
     * because a quest can be started and finished in the same stage write.
     */
    public final void fillProgress(byte[] progress) {
        for (Quest quest : this.quests.values()) {
            int uid = quest.getUID();
            if (uid < 0 || uid >= progress.length) {
                continue;
            }
            if (quest.completed()) {
                progress[uid] = 2;
            } else if (quest.getStage() != -1) {
                progress[uid] = 1;
            }
        }
    }

    /**
     * This player's copy of a quest, by uid. Null if no such quest is loaded.
     *
     * Quests are per-player objects, so this is the only honest way to ask
     * "where is this player up to" from outside the quest itself. Npc handlers
     * need it: Aggie and Wyson both belong to more than one quest and so cannot
     * live inside either of them, but they still have to know which one the
     * player is on.
     */
    public final Quest getQuest(int uid) {
        for (Quest quest : this.quests.values()) {
            if (quest.getUID() == uid) {
                return quest;
            }
        }
        return null;
    }

    /** Whether the player has finished a quest. False if it is not loaded. */
    public final boolean completed(int uid) {
        Quest quest = this.getQuest(uid);
        return quest != null && quest.completed();
    }

    /** A quest's stage, or -1 for "not started" and for quests that failed to load. */
    public final int stageOf(int uid) {
        Quest quest = this.getQuest(uid);
        return quest == null ? -1 : quest.getStage();
    }

    /**
     * Ask a quest a question it has published a name for. False if the quest is
     * not loaded, has not started, or does not know the name.
     *
     * See {@link Quest#reached} for why this is by name and not by stage number.
     */
    public final boolean reached(int uid, String key) {
        Quest quest = this.getQuest(uid);
        return quest != null && quest.reached(key);
    }

    /**
     * Report a named event to a quest. Does nothing if the quest is not loaded.
     *
     * See {@link Quest#note} -- this is the only route by which anything outside
     * a quest can move it along, and even here the quest decides what the event
     * means and which stage it lands on.
     */
    public final void note(int uid, String key) {
        Quest quest = this.getQuest(uid);
        if (quest != null) {
            quest.note(key);
        }
    }

    /**
     * Has any quest claimed this npc's right-click command?
     *
     * The narrow form of associatedWithQuest, and the only question the command
     * dispatcher may ask. See Quest.associateNpcCommand for why the wide one is
     * wrong there.
     */
    public boolean commandClaimed(Npc npc) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (quest.npcCommandAssociated(npc.getID())) {
                return true;
            }
        }
        return false;
    }

    public boolean associatedWithQuest(Entity entity) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        try {
            for (Quest quest : clone.values()) {
                if (!(entity instanceof Npc ? quest.npcAssociated(((Npc)entity).getID()) : (entity instanceof GameObject ? quest.objectAssociated(((GameObject)entity).getID(), ((GameObject)entity).getX(), ((GameObject)entity).getY()) : entity instanceof InvItem && quest.itemAssociated(((InvItem)entity).getID())))) continue;
                return true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean triggerEntity(final QuestTrigger trigger, final Entity entity) {
        return this.triggerEntity(trigger, entity, null);
    }

    /**
     * Ask the quests that claimed this item whether it may be picked up.
     *
     * Synchronous and answered before the item moves, which is what separates
     * it from ITEM_PICKUP: that is a report, this is a decision. The first
     * quest to refuse wins, and has already told the player why.
     */
    public boolean refusesPickup(InvItem item) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.itemAssociated(item.getID())) continue;
            try {
                if (quest.refusesPickup(item)) {
                    return true;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * The same question for telekinetic grab, which reaches the floor without
     * going through PickupItem at all. Same contract; quests answer it with
     * their pickup answer unless they override it.
     */
    public boolean refusesTelegrab(InvItem item) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.itemAssociated(item.getID())) continue;
            try {
                if (quest.refusesTelegrab(item)) {
                    return true;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Ask the quests that claimed this npc whether it may die.
     *
     * The mirror of refusesPickup(), for the same reason and with the same
     * contract: synchronous, answered before the death goes through, first
     * refusal wins, and the refusing quest has already told the player why.
     */
    public boolean refusesKill(Npc npc) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.npcAssociated(npc.getID())) continue;
            try {
                if (quest.refusesKill(npc)) {
                    return true;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Ask the quests that claimed this npc whether the fight may start.
     *
     * Same contract as {@link #refusesKill}: synchronous, answered before the
     * player commits to the fight, first refusal wins, and the refusing quest
     * has already told the player why.
     */
    public boolean refusesAttack(Npc npc) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.npcAssociated(npc.getID())) continue;
            try {
                if (quest.refusesAttack(npc)) {
                    return true;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Tell the quests that claimed this npc that a spell landed on it.
     *
     * Synchronous, unlike the entity triggers, because refusesKill() is asked a
     * moment later in the same damage path and has to see this spell already
     * counted. There is nothing to take away from the caller either way: the
     * spell has landed by the time this runs.
     */
    public void triggerSpell(Npc npc, int spellId, int damage) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.npcAssociated(npc.getID())) continue;
            try {
                quest.spellCast(npc, spellId, damage);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Tell the quests that claimed this piece of scenery that a spell was cast
     * at it, and say whether anybody was listening.
     *
     * Threaded like the entity triggers and not synchronous like the npc cast,
     * because what a quest does about it is talk, teleport and sleep, none of
     * which may run on the packet thread. The boolean therefore reports only
     * that the object was claimed, decided here before any thread starts: it is
     * the caller's licence to charge for the spell, and a claimed object that
     * turns out to have nothing to say at this stage of the quest still costs
     * the runes, exactly as a wasted cast does everywhere else.
     */
    public boolean triggerSpell(final GameObject object, final int spellId) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        boolean handled = false;
        for (final Quest quest : clone.values()) {
            if (!quest.objectAssociated(object.getID(), object.getX(), object.getY())) continue;
            handled = true;
            new Thread(new Runnable(){

                public void run() {
                    try {
                        quest.spellCast(object, spellId);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        return handled;
    }

    /**
     * Tell the quests that claimed this npc that an arrow landed on it.
     *
     * Synchronous for the same reason as {@link #triggerSpell}: refusesKill()
     * is asked further down the same shot and has to see this arrow already
     * counted.
     */
    public void triggerRanged(Npc npc, int arrowId, int damage) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (Quest quest : clone.values()) {
            if (!quest.npcAssociated(npc.getID())) continue;
            try {
                quest.rangedShot(npc, arrowId, damage);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Tell whoever is watching that an item hit the floor.
     *
     * Matched against associatedDrops, not associatedItems -- see
     * Quest.associateDroppedItem() for why those are separate lists. Returns
     * nothing, because by the time this runs the item is already gone from the
     * inventory and lying on the ground: there is no decision left to take
     * away from the caller, only a fact to report.
     */
    public void triggerDrop(final InvItem item) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        for (final Quest quest : clone.values()) {
            if (!quest.dropAssociated(item.getID())) continue;
            new Thread(new Runnable(){

                public void run() {
                    quest.triggerEntity(QuestTrigger.ITEM_DROP, item, null);
                }
            }).start();
        }
    }

    /**
     * Hand an item on the ground, and the item being used on it, to whichever
     * quest claimed the one on the ground.
     *
     * Matched against associatedItems and not against the pair the way
     * triggerItemPair() is, because the thing on the ground is the subject
     * here: a quest that claimed Fluffs has claimed everything poured over
     * her, and what a player carries to her is their business.
     *
     * Returns true if a quest took it, in which case InvUseOnGroundItem must
     * stop -- same contract scenery gets in ObjectAction.
     */
    public boolean triggerGroundItem(final Item item, final InvItem used) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        boolean handled = false;
        for (final Quest quest : clone.values()) {
            if (!quest.itemAssociated(item.getID())) continue;
            new Thread(new Runnable(){

                public void run() {
                    quest.triggerEntity(QuestTrigger.ITEM_ON_GROUND_ITEM, item, used);
                }
            }).start();
            handled = true;
        }
        return handled;
    }

    /**
     * Hand a door to whichever quest claimed it.
     *
     * Doors get their own dispatch rather than riding on triggerEntity because
     * they are matched against associatedDoors, not associatedObjects; a
     * GameObject alone cannot say which table its id came from.
     *
     * Returns true if a quest took it, in which case the caller must do nothing
     * else -- the quest owns the door outright, same contract scenery gets in
     * ObjectAction and npcs get in TalkToNpcHandler.
     */
    public boolean triggerDoor(final QuestTrigger trigger, final GameObject door) {
        return this.triggerDoor(trigger, door, null);
    }

    /**
     * The same, with an item -- for ITEM_ON_DOOR. Null <code>used</code> lands
     * in Quest's one-argument override exactly as the two-argument form does.
     */
    public boolean triggerDoor(final QuestTrigger trigger, final GameObject door, final InvItem used) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        boolean handled = false;
        for (final Quest quest : clone.values()) {
            if (!quest.doorAssociated(door.getID(), door.getX(), door.getY())) continue;
            new Thread(new Runnable(){

                public void run() {
                    quest.triggerEntity(trigger, door, used);
                }
            }).start();
            handled = true;
        }
        return handled;
    }

    /**
     * Hand a pair of inventory items to a quest that claimed both of them.
     *
     * Both, deliberately. An item is not owned the way an npc is -- poison is
     * used on fish food in one quest and on a dagger in ordinary play -- so a
     * quest only takes the interaction when it has declared an interest in both
     * halves of it. The pair is offered in the order the player used them and
     * then the other way round, so a quest need only handle one arrangement.
     */
    public boolean triggerItemPair(final InvItem first, final InvItem second) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        boolean handled = false;
        for (final Quest quest : clone.values()) {
            if (!quest.itemAssociated(first.getID()) || !quest.itemAssociated(second.getID())) continue;
            new Thread(new Runnable(){

                public void run() {
                    quest.triggerEntity(QuestTrigger.ITEM_ON_ITEM, first, second);
                }
            }).start();
            handled = true;
        }
        return handled;
    }

    /**
     * Dispatch a trigger that has a second subject.
     *
     * Only ITEM_ON_OBJECT uses <code>used</code>; every other trigger passes
     * null and lands in Quest's one-argument override exactly as before.
     */
    public boolean triggerEntity(final QuestTrigger trigger, final Entity entity, final InvItem used) {
        HashMap<String, Quest> clone = (HashMap<String, Quest>)this.quests.clone();
        boolean handled = false;
        try {
            QuestManager questManager = this;
            synchronized (questManager) {
                for (final Quest quest : clone.values()) {
                    if (entity instanceof Npc && !quest.npcAssociated(((Npc)entity).getID()) || entity instanceof InvItem && !quest.itemAssociated(((InvItem)entity).getID()) || entity instanceof GameObject && !quest.objectAssociated(((GameObject)entity).getID(), ((GameObject)entity).getX(), ((GameObject)entity).getY())) continue;
                    new Thread(new Runnable(){

                        public void run() {
                            quest.triggerEntity(trigger, entity, used);
                        }
                    }).start();
                    handled = true;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return handled;
    }
}

