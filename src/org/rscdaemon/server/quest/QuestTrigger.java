/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.quest;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public enum QuestTrigger {
    NPC_KILLED,
    NPC_TALK,
    ITEM_PICKUP,
    ITEM_DROP,
    /**
     * An inventory item's own command was used -- read, rub, open, and so on.
     *
     * The item arrives as the entity and is matched against the quest's item
     * list, the same way ITEM_PICKUP is. A quest that claims the item takes the
     * command outright and InvActionHandler stops, so an item that is both a
     * quest item and edible must not be claimed by a quest that does not want
     * to feed it to the player. Reading the shield of Arrav book is the first
     * use of this.
     */
    ITEM_COMMAND,
    OBJECT_ACT1,
    OBJECT_ACT2,
    /**
     * An inventory item was used on a scenery object.
     *
     * Appended rather than folded into OBJECT_ACT1, because it is the only
     * trigger that has two subjects. The object is the entity, as it is for the
     * OBJECT_ACT triggers; the item comes alongside it through the three-argument
     * QuestManager.triggerEntity(). Several vanilla quests turn on this and
     * nothing else -- putting the ghost's skull in his coffin, for one.
     */
    ITEM_ON_OBJECT,
    /**
     * One inventory item was used on another.
     *
     * Dispatched only when a single quest has associated both ids, because
     * unlike an npc or a piece of scenery an item is half of a pair, and one
     * quest owning poison must not take poison away from every other use of it.
     * The first item arrives as the entity and the second alongside it, in the
     * same three-argument triggerEntity() that ITEM_ON_OBJECT uses.
     */
    ITEM_ON_ITEM,
    /**
     * An inventory item was used on an npc.
     *
     * The npc arrives as the entity and the item alongside it, the same shape
     * ITEM_ON_OBJECT has. Unlike ITEM_ON_ITEM one association is enough, because
     * a quest that has claimed an npc has claimed everything done to it -- rope
     * used on Lady Keli is Prince Ali rescue's business and nobody else's.
     */
    ITEM_ON_NPC,
    /**
     * A door was opened -- its first and second commands respectively.
     *
     * Separate from the OBJECT_ACT triggers because doors are not scenery: they
     * are wall objects whose ids come from DoorDef, a different table entirely.
     * Door 35 and scenery 35 are unrelated things, so a quest declares its
     * interest with associateDoor() rather than associateObject().
     */
    DOOR_ACT1,
    DOOR_ACT2,
    /**
     * An inventory item was used on a door.
     *
     * Matched against the quest's door list like the DOOR_ACT triggers, and
     * carries the item alongside like ITEM_ON_OBJECT. Prince Ali's cell is
     * unlocked this way rather than by opening it: the door tells the player
     * "Maybe you should try using your key on it".
     */
    ITEM_ON_DOOR,
    /**
     * An inventory item was used on an item lying on the ground.
     *
     * The ground item is the entity -- a model.Item, not an InvItem -- and the
     * one being used arrives alongside it. Matched against the quest's item
     * list, since a ground item is an item wherever it is standing.
     *
     * The only trigger whose subject is a thing nobody owns. Fluffs the cat is
     * a ground item rather than an npc, so Gertrude's cat gives her milk and a
     * seasoned sardine this way; before this, InvUseOnGroundItem could light
     * logs and nothing else.
     */
    ITEM_ON_GROUND_ITEM,
    /**
     * An npc's own right-click command was used.
     *
     * The npc arrives as the entity and is matched against the quest's npc
     * list, the same way NPC_TALK is. Distinct from NPC_TALK because the two
     * mean different things to the player: talking opens a conversation, the
     * command does whatever the command says.
     *
     * Only three commands exist in the whole of Classic. "pickpocket" is on
     * eighty-odd npcs and belongs to Thieving (task #36); "tackle" and "pass
     * to" are gnome ball. The fourth is "watch", which appears on exactly one
     * npc in the game -- the Mercenary Captain of Tourist trap, npc 669 --
     * where watching him reveals that he never fights his own battles, which
     * is what unlocks the taunt that makes him fight you alone.
     *
     * Adding the trigger rather than special-casing the captain keeps the
     * pickpocket work that comes later a matter of writing NpcCommand's other
     * branch, not of rewriting this one.
     */
    NPC_COMMAND;

}

