/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling;

import java.util.HashMap;
import java.util.List;
import org.rscdaemon.server.entityhandling.defs.DoorDef;
import org.rscdaemon.server.entityhandling.defs.GameObjectDef;
import org.rscdaemon.server.entityhandling.defs.ItemDef;
import org.rscdaemon.server.entityhandling.defs.NPCDef;
import org.rscdaemon.server.entityhandling.defs.PrayerDef;
import org.rscdaemon.server.entityhandling.defs.SpellDef;
import org.rscdaemon.server.entityhandling.defs.TileDef;
import org.rscdaemon.server.entityhandling.defs.extras.CerterDef;
import org.rscdaemon.server.entityhandling.defs.extras.ChestLootDef;
import org.rscdaemon.server.entityhandling.defs.extras.FiremakingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemArrowHeadDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemBowStringDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemCookingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemCraftingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemDartTipDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemGemDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemHerbDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemHerbSecond;
import org.rscdaemon.server.entityhandling.defs.extras.ItemLogCutDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmeltingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmithingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemUnIdentHerbDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemWieldableDef;
import org.rscdaemon.server.entityhandling.defs.extras.DoorAgilityDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectAgilityDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectFishingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectMiningDef;
import org.rscdaemon.server.entityhandling.defs.extras.NpcPickpocketDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectStallDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectChestDef;
import org.rscdaemon.server.entityhandling.defs.extras.DoorThievingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectWoodcuttingDef;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.TelePoint;
import org.rscdaemon.server.util.PersistenceManager;

public class EntityHandler {
    private static DoorDef[] doors = (DoorDef[])PersistenceManager.load("defs/DoorDef.xml.gz");
    private static GameObjectDef[] gameObjects = (GameObjectDef[])PersistenceManager.load("defs/GameObjectDef.xml.gz");
    private static NPCDef[] npcs = (NPCDef[])PersistenceManager.load("defs/NPCDef.xml.gz");
    private static PrayerDef[] prayers = (PrayerDef[])PersistenceManager.load("defs/PrayerDef.xml.gz");
    private static ItemDef[] items = (ItemDef[])PersistenceManager.load("defs/ItemDef.xml.gz");
    private static TileDef[] tiles;
    private static ChestLootDef[] keyChestLoots;
    private static ItemHerbSecond[] herbSeconds;
    private static HashMap<Integer, ItemDartTipDef> dartTips;
    private static HashMap<Integer, ItemGemDef> gems;
    private static HashMap<Integer, ItemLogCutDef> logCut;
    private static HashMap<Integer, ItemBowStringDef> bowString;
    private static HashMap<Integer, ItemArrowHeadDef> arrowHeads;
    private static HashMap<Integer, FiremakingDef> firemaking;
    private static HashMap<Integer, int[]> itemAffectedTypes;
    private static HashMap<Integer, ItemWieldableDef> itemWieldable;
    private static HashMap<Integer, ItemUnIdentHerbDef> itemUnIdentHerb;
    private static HashMap<Integer, ItemHerbDef> itemHerb;
    private static HashMap<Integer, Integer> itemEdibleHeals;
    private static HashMap<Integer, ItemCookingDef> itemCooking;
    private static ItemSmithingDef[] itemSmithing;
    private static ItemCraftingDef[] itemCrafting;
    private static HashMap<Integer, ItemSmeltingDef> itemSmelting;
    private static SpellDef[] spells;
    private static HashMap<Integer, Integer> spellAggressiveLvl;
    private static HashMap<Point, TelePoint> objectTelePoints;
    private static HashMap<Integer, CerterDef> certers;
    private static HashMap<Integer, ObjectMiningDef> objectMining;
    private static HashMap<Integer, ObjectWoodcuttingDef> objectWoodcutting;
    private static HashMap<Integer, NpcPickpocketDef> npcPickpocket;
    private static HashMap<Integer, ObjectStallDef> objectStall;
    private static HashMap<Integer, ObjectChestDef> objectChest;
    private static HashMap<Integer, DoorThievingDef> doorThieving;
    private static HashMap<Integer, ObjectAgilityDef> objectAgility;
    private static HashMap<Point, ObjectAgilityDef> objectAgilityLoc;
    private static HashMap<Integer, DoorAgilityDef> doorAgility;
    private static HashMap<Integer, ObjectFishingDef[]> objectFishing;

    public static ChestLootDef[] getKeyChestLoots() {
        return (ChestLootDef[])keyChestLoots.clone();
    }

    public static ItemHerbSecond getItemHerbSecond(int secondID, int unfinishedID) {
        for (ItemHerbSecond def : herbSeconds) {
            if (def.getSecondID() != secondID || def.getUnfinishedID() != unfinishedID) continue;
            return def;
        }
        return null;
    }

    public static ItemDartTipDef getItemDartTipDef(int id) {
        return dartTips.get(id);
    }

    public static ItemGemDef getItemGemDef(int id) {
        return gems.get(id);
    }

    public static ItemArrowHeadDef getItemArrowHeadDef(int id) {
        return arrowHeads.get(id);
    }

    public static ItemLogCutDef getItemLogCutDef(int id) {
        return logCut.get(id);
    }

    public static ItemBowStringDef getItemBowStringDef(int id) {
        return bowString.get(id);
    }

    public static FiremakingDef getFiremakingDef(int id) {
        return firemaking.get(id);
    }

    public static ItemCraftingDef getCraftingDef(int id) {
        if (id < 0 || id >= itemCrafting.length) {
            return null;
        }
        return itemCrafting[id];
    }

    public static ItemSmithingDef getSmithingDef(int id) {
        if (id < 0 || id >= itemSmithing.length) {
            return null;
        }
        return itemSmithing[id];
    }

    public static CerterDef getCerterDef(int id) {
        return certers.get(id);
    }

    public static ItemSmeltingDef getItemSmeltingDef(int id) {
        return itemSmelting.get(id);
    }

    public static ItemCookingDef getItemCookingDef(int id) {
        return itemCooking.get(id);
    }

    public static ObjectFishingDef getObjectFishingDef(int id, int click) {
        ObjectFishingDef[] defs = objectFishing.get(id);
        if (defs == null) {
            return null;
        }
        return defs[click];
    }

    public static ObjectMiningDef getObjectMiningDef(int id) {
        return objectMining.get(id);
    }

    public static ObjectWoodcuttingDef getObjectWoodcuttingDef(int id) {
        return objectWoodcutting.get(id);
    }

    /**
     * What is in this npc's pocket, or null if it has nothing worth taking.
     *
     * Null is the ordinary answer for most of the game: only twenty-nine npcs
     * carry the pickpocket command and only those twenty-nine have an entry.
     */
    public static NpcPickpocketDef getNpcPickpocketDef(int id) {
        return npcPickpocket.get(id);
    }

    /**
     * What this stall or counter holds, or null if it is not stealable.
     *
     * Nine object ids have an entry, and the four ogre counters share one
     * of them. Everything else in the game with a stall-shaped model is
     * scenery -- objects 29 and 30 are the plain counter and the plain
     * market stall, and neither has a command at all.
     */
    public static ObjectStallDef getObjectStallDef(int id) {
        return objectStall.get(id);
    }

    /**
     * What this chest holds, or null if Thieving cannot open it.
     *
     * Null for every chest that wants a key, for the decorative chests, and
     * for the quest chests -- six ids in all have an entry.
     */
    public static ObjectChestDef getObjectChestDef(int id) {
        return objectChest.get(id);
    }

    /**
     * What it takes to pick this door's lock, or null if it has no lock.
     *
     * Keyed by DoorDef id, which is its own numbering: never pass a
     * GameObjectDef id to this, and never pass a door id to
     * getObjectChestDef.
     */
    public static DoorThievingDef getDoorThievingDef(int id) {
        return doorThieving.get(id);
    }

    /**
     * The Agility obstacle this scenery carries, or null if it is not one.
     *
     * Forty object ids have an entry: the three courses, the four Yanille
     * agility dungeon obstacles, and the shortcuts scattered around the map.
     * Obstacles a quest owns are not here -- the quest teleports the player
     * itself -- so this returning null is the ordinary answer for a handhold
     * or a pile of rubble that belongs to Watchtower or Shilo Village.
     */
    public static ObjectAgilityDef getObjectAgilityDef(int id) {
        return objectAgility.get(id);
    }

    /**
     * The same question asked of one particular obstacle rather than of an id.
     *
     * Most of the map can be keyed by id: there is one gnome log balance, one
     * Yanille ledge, one pipe under Barbarian Outpost, and the id says which.
     * The Underground Pass cannot. It reuses the same eleven ledge ids and the
     * same handful of rock ids up and down a dungeon a hundred and eighty tiles
     * across, so ledge 862 is two different crossings depending on which one
     * you are standing next to and the id alone cannot tell them apart.
     *
     * So obstacles that need it are written down by where they stand, in
     * locs/extras/ObjectAgilityLoc.xml.gz, and the location is asked first.
     * Nothing that was keyed by id before is affected: an obstacle with no
     * entry of its own falls through to the id table exactly as it always did.
     */
    public static ObjectAgilityDef getObjectAgilityDef(Point location, int id) {
        ObjectAgilityDef def = objectAgilityLoc.get(location);
        return def != null ? def : objectAgility.get(id);
    }

    /**
     * The Agility obstacle this door carries, or null if it has none.
     *
     * Two doors qualify, both of them low walls at the end of the Barbarian
     * Outpost course. Keyed by DoorDef id, which is its own numbering: never
     * pass a GameObjectDef id to this, and never pass a door id to
     * getObjectAgilityDef.
     */
    public static DoorAgilityDef getDoorAgilityDef(int id) {
        return doorAgility.get(id);
    }

    public static ItemHerbDef getItemHerbDef(int id) {
        return itemHerb.get(id);
    }

    public static Point getObjectTelePoint(Point location, String command) {
        TelePoint point = objectTelePoints.get(location);
        if (point == null) {
            return null;
        }
        if (command == null || point.getCommand().equalsIgnoreCase(command)) {
            return point;
        }
        return null;
    }

    public static int getSpellAggressiveLvl(int id) {
        Integer lvl = spellAggressiveLvl.get(id);
        if (lvl != null) {
            return lvl;
        }
        return 0;
    }

    public static int getItemEdibleHeals(int id) {
        Integer heals = itemEdibleHeals.get(id);
        if (heals != null) {
            return heals;
        }
        return 0;
    }

    public static int[] getItemAffectedTypes(int type) {
        return itemAffectedTypes.get(type);
    }

    public static ItemUnIdentHerbDef getItemUnIdentHerbDef(int id) {
        return itemUnIdentHerb.get(id);
    }

    public static ItemWieldableDef getItemWieldableDef(int id) {
        return itemWieldable.get(id);
    }

    public static DoorDef getDoorDef(int id) {
        if (id < 0 || id >= doors.length) {
            return null;
        }
        return doors[id];
    }

    public static GameObjectDef getGameObjectDef(int id) {
        if (id < 0 || id >= gameObjects.length) {
            return null;
        }
        return gameObjects[id];
    }

    public static ItemDef getItemDef(int id) {
        if (id < 0 || id >= items.length) {
            return null;
        }
        return items[id];
    }

    public static TileDef getTileDef(int id) {
        if (id < 0 || id >= tiles.length) {
            return null;
        }
        return tiles[id];
    }

    public static NPCDef getNpcDef(int id) {
        if (id < 0 || id >= npcs.length) {
            return null;
        }
        return npcs[id];
    }

    public static PrayerDef getPrayerDef(int id) {
        if (id < 0 || id >= prayers.length) {
            return null;
        }
        return prayers[id];
    }

    /**
     * The prayer book's size comes from the data, not a constant: the client
     * builds its panel from the same document (Prayers.xml.data is the
     * PrayerDef.xml.gz payload) and sizes everything off it, so the one
     * number the two sides must agree on lives in one place.
     */
    public static int prayerCount() {
        return prayers.length;
    }

    public static SpellDef getSpellDef(int id) {
        if (id < 0 || id >= spells.length) {
            return null;
        }
        return spells[id];
    }

    static {
        spells = (SpellDef[])PersistenceManager.load("defs/SpellDef.xml.gz");
        tiles = (TileDef[])PersistenceManager.load("defs/TileDef.xml.gz");
        keyChestLoots = (ChestLootDef[])PersistenceManager.load("defs/extras/KeyChestLoot.xml.gz");
        herbSeconds = (ItemHerbSecond[])PersistenceManager.load("defs/extras/ItemHerbSecond.xml.gz");
        dartTips = (HashMap)PersistenceManager.load("defs/extras/ItemDartTipDef.xml.gz");
        gems = (HashMap)PersistenceManager.load("defs/extras/ItemGemDef.xml.gz");
        logCut = (HashMap)PersistenceManager.load("defs/extras/ItemLogCutDef.xml.gz");
        bowString = (HashMap)PersistenceManager.load("defs/extras/ItemBowStringDef.xml.gz");
        arrowHeads = (HashMap)PersistenceManager.load("defs/extras/ItemArrowHeadDef.xml.gz");
        firemaking = (HashMap)PersistenceManager.load("defs/extras/FiremakingDef.xml.gz");
        itemAffectedTypes = (HashMap)PersistenceManager.load("defs/extras/ItemAffectedTypes.xml.gz");
        itemWieldable = (HashMap)PersistenceManager.load("defs/extras/ItemWieldableDef.xml.gz");
        itemUnIdentHerb = (HashMap)PersistenceManager.load("defs/extras/ItemUnIdentHerbDef.xml.gz");
        itemHerb = (HashMap)PersistenceManager.load("defs/extras/ItemHerbDef.xml.gz");
        itemEdibleHeals = (HashMap)PersistenceManager.load("defs/extras/ItemEdibleHeals.xml.gz");
        itemCooking = (HashMap)PersistenceManager.load("defs/extras/ItemCookingDef.xml.gz");
        itemSmelting = (HashMap)PersistenceManager.load("defs/extras/ItemSmeltingDef.xml.gz");
        itemSmithing = (ItemSmithingDef[])PersistenceManager.load("defs/extras/ItemSmithingDef.xml.gz");
        itemCrafting = (ItemCraftingDef[])PersistenceManager.load("defs/extras/ItemCraftingDef.xml.gz");
        objectMining = (HashMap)PersistenceManager.load("defs/extras/ObjectMining.xml.gz");
        objectWoodcutting = (HashMap)PersistenceManager.load("defs/extras/ObjectWoodcutting.xml.gz");
        npcPickpocket = (HashMap)PersistenceManager.load("defs/extras/NpcPickpocket.xml.gz");
        objectStall = (HashMap)PersistenceManager.load("defs/extras/ObjectStall.xml.gz");
        objectChest = (HashMap)PersistenceManager.load("defs/extras/ObjectChest.xml.gz");
        doorThieving = (HashMap)PersistenceManager.load("defs/extras/DoorThieving.xml.gz");
        objectAgility = (HashMap)PersistenceManager.load("defs/extras/ObjectAgility.xml.gz");
        objectAgilityLoc = (HashMap)PersistenceManager.load("locs/extras/ObjectAgilityLoc.xml.gz");
        doorAgility = (HashMap)PersistenceManager.load("defs/extras/DoorAgility.xml.gz");
        objectFishing = (HashMap)PersistenceManager.load("defs/extras/ObjectFishing.xml.gz");
        spellAggressiveLvl = (HashMap)PersistenceManager.load("defs/extras/SpellAggressiveLvl.xml.gz");
        objectTelePoints = (HashMap)PersistenceManager.load("locs/extras/ObjectTelePoints.xml.gz");
        certers = (HashMap)PersistenceManager.load("defs/extras/NpcCerters.xml.gz");
    }
}

