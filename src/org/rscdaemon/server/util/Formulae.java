/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.SpellDef;
import org.rscdaemon.server.entityhandling.defs.extras.ChestLootDef;
import org.rscdaemon.server.entityhandling.defs.extras.FiremakingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemDropDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectFishDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectMiningDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectWoodcuttingDef;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.util.DataConversions;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Formulae {
    public static final int[] experienceArray = new int[]{83, 174, 276, 388, 512, 650, 801, 969, 1154, 1358, 1584, 1833, 2107, 2411, 2746, 3115, 3523, 3973, 4470, 5018, 5624, 6291, 7028, 7842, 8740, 9730, 10824, 12031, 13363, 14833, 16456, 18247, 20224, 22406, 24815, 27473, 30408, 33648, 37224, 41171, 45529, 50339, 55649, 61512, 67983, 75127, 83014, 91721, 101333, 111945, 123660, 136594, 150872, 166636, 184040, 203254, 224466, 247886, 273742, 302288, 333804, 368599, 407015, 449428, 496254, 547953, 605032, 668051, 737627, 814445, 899257, 992895, 1096278, 1210421, 1336443, 1475581, 1629200, 1798808, 0x1E4E14, 2192818, 2421087, 2673114, 2951373, 3258594, 3597792, 3972294, 4385776, 4842295, 5346332, 5902831, 6517253, 7195629, 7944614, 8771558, 9684577, 10692629, 11805606, 13034431, 14391160};
    public static final int[] eArray = new int[]{0, 0, 83, 174, 276, 388, 512, 650, 801, 969, 1154, 1358, 1584, 1833, 2107, 2411, 2746, 3115, 3523, 3973, 4470, 5018, 5624, 6291, 7028, 7842, 8740, 9730, 10824, 12031, 13363, 14833, 16456, 18247, 20224, 22406, 24815, 27473, 30408, 33648, 37224, 41171, 45529, 50339, 55649, 61512, 67983, 75127, 83014, 91721, 101333, 111945, 123660, 136594, 150872, 166636, 184040, 203254, 224466, 247886, 273742, 302288, 333804, 368599, 407015, 449428, 496254, 547953, 605032, 668051, 737627, 814445, 899257, 992895, 1096278, 1210421, 1336443, 1475581, 1629200, 1798808, 0x1E4E14, 2192818, 2421087, 2673114, 2951373, 3258594, 3597792, 3972294, 4385776, 4842295, 5346332, 5902831, 6517253, 7195629, 7944614, 8771558, 9684577, 10692629, 11805606, 13034431, 14391160};
    /*
     * Slot 17 is Thieving and was mislabelled "quest" here from the beginning.
     * Nothing computed from it was ever wrong -- the array is only a name-to-
     * index lookup, and its one caller is the setstat command -- but it meant
     * "::setstat bob thieving 50" silently did nothing findable while
     * "::setstat bob quest 50" set a player's Thieving level, and it is the
     * reason half the quest files carry a comment explaining what slot 17
     * really is.
     */
    public static final String[] statArray = new String[]{"attack", "defense", "strength", "hits", "ranged", "prayer", "magic", "cooking", "woodcut", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining", "herblaw", "agility", "thieving", "runecrafting"};
    /*
     * All three lists are searched best-first: the first id the player is
     * carrying is the one used, so the strongest tool or arrow wins.
     *
     * Each of them used to begin with an id that has no item behind it --
     * 1329, 1322 and 1327. Those were RSCD's own custom items (in the v25
     * tree 1322 is "Legendary PKer Axe" and 1327 is "Party Hat"; 1329 was
     * past the end even there), and this tree's ItemDef stops at 1289
     * "Scythe" because the custom item set was removed. A leading id that
     * can never match is dead weight, so they are gone.
     *
     * The Dragon axe (594) is deliberately NOT in the woodcutting list.
     * Jagex's own cache gives it the description "A vicious looking axe",
     * which is the battle axe line -- the woodcutting axes all read "A
     * powerful axe". It is a weapon, not a tree tool.
     */
    public static final int[] woodcuttingAxeIDs = new int[]{405, 204, 203, 428, 88, 12, 87};
    public static final int[] miningAxeIDs = new int[]{1262, 1261, 1260, 1259, 1258, 156};
    public static final int[] arrowIDs = new int[]{723, 647, 646, 645, 644, 643, 642, 641, 640, 639, 638, 574, 11};

    /*
     * Untradeable items -- now taken directly from Jagex's own data. The
     * 2003 client cache (config85.jag, the jagex_data/ oracle) carries a
     * per-item "special" byte, and the vanilla 2003 client hard-blocks both
     * trade offers and duel offers on it, with the messages players
     * remember: "This object cannot be traded with other players" / "This
     * object cannot be added to a duel offer". This array is exactly the
     * 394 items Jagex flagged, plus one deliberate deviation kept from the
     * old curated list: 978 (ResetCrystal), whose flag byte in the cache is
     * a corrupt 207 -- the real client's strict == 1 check let this dev
     * item slip through Jagex's own net, and blocking it is clearly what
     * they meant.
     *
     * Two ids from the earlier wiki-curated list were the WRONG member of a
     * same-named pair and are gone: 48 (Shield of Arrav weapon store key --
     * Jagex flags 47, the Phoenix gang DOOR key, instead; 48 must stay
     * tradeable because the quest's two-player co-op hinges on handing it
     * across gangs) and 412 (a skull; Jagex flags 27). Everything the old
     * comment called "ambiguous by name" is settled here by id, because the
     * cache flags items by id, not name.
     *
     * The client tree cannot enforce any of this -- its ItemDef has no
     * special field -- so this list, checked by TradeHandler and
     * DuelHandler, is the only enforcement. One authentic behaviour NOT
     * modelled: on free-to-play worlds the 2003 client additionally treated
     * every members item as untradeable at load time. Irrelevant while the
     * server runs members content; revisit if a free-world mode ever ships.
     */
    private static final int[] untradeableIds = {
        23, 24, 25, 26, 27, 30, 47, 51, 57, 175,
        178, 208, 212, 213, 217, 240, 242, 244, 245, 247,
        264, 265, 266, 271, 274, 275, 318, 382, 390, 391,
        392, 393, 394, 395, 415, 416, 417, 418, 505, 506,
        507, 508, 509, 510, 538, 539, 540, 556, 557, 558,
        586, 587, 588, 589, 590, 591, 595, 596, 599, 600,
        601, 602, 603, 605, 606, 668, 678, 679, 680, 681,
        686, 687, 688, 689, 690, 691, 692, 693, 694, 695,
        696, 697, 698, 699, 700, 701, 704, 705, 715, 716,
        717, 718, 719, 720, 721, 722, 723, 724, 725, 726,
        727, 728, 729, 730, 732, 733, 734, 736, 737, 738,
        740, 741, 742, 743, 744, 745, 746, 747, 752, 753,
        754, 755, 756, 757, 758, 759, 760, 761, 762, 763,
        764, 766, 767, 768, 769, 770, 771, 773, 774, 775,
        776, 777, 778, 780, 782, 787, 788, 789, 794, 796,
        797, 798, 799, 800, 801, 802, 803, 804, 805, 806,
        809, 810, 811, 812, 813, 815, 816, 817, 818, 819,
        820, 821, 822, 823, 824, 826, 835, 852, 919, 921,
        922, 925, 926, 927, 928, 929, 930, 931, 958, 959,
        960, 961, 962, 972, 973, 974, 976, 977, 978, 981,
        984, 985, 986, 987, 988, 989, 991, 992, 993, 994,
        995, 996, 997, 998, 999, 1000, 1001, 1002, 1003, 1004,
        1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1014, 1017,
        1018, 1021, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1036,
        1037, 1038, 1039, 1040, 1042, 1043, 1044, 1045, 1046, 1047,
        1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1058,
        1059, 1060, 1061, 1071, 1072, 1073, 1074, 1086, 1087, 1093,
        1095, 1096, 1097, 1098, 1111, 1112, 1113, 1114, 1115, 1116,
        1117, 1118, 1121, 1141, 1142, 1143, 1144, 1145, 1146, 1147,
        1148, 1149, 1150, 1151, 1152, 1153, 1154, 1155, 1156, 1157,
        1158, 1159, 1160, 1161, 1162, 1163, 1164, 1165, 1166, 1167,
        1168, 1169, 1170, 1171, 1173, 1174, 1175, 1176, 1177, 1178,
        1180, 1181, 1182, 1183, 1188, 1189, 1194, 1195, 1196, 1197,
        1198, 1199, 1200, 1201, 1202, 1203, 1204, 1205, 1206, 1207,
        1208, 1209, 1210, 1211, 1212, 1213, 1214, 1215, 1216, 1217,
        1218, 1219, 1220, 1221, 1222, 1223, 1224, 1225, 1226, 1227,
        1228, 1229, 1230, 1231, 1232, 1233, 1234, 1235, 1236, 1237,
        1238, 1239, 1240, 1241, 1242, 1243, 1244, 1246, 1250, 1251,
        1252, 1253, 1254, 1255, 1256, 1257, 1264, 1265, 1266, 1267,
        1284, 1286, 1287, 1288, 1289, 1306, 1307
    };

    public static boolean isUntradeable(int itemId) {
        return Arrays.binarySearch(untradeableIds, itemId) >= 0;
    }

    public static final int ANTI_DRAGON_SHIELD = 420;

    private static final int DB_ATTACK = 0, DB_DEFENSE = 1, DB_STRENGTH = 2, DB_HITS = 3, DB_PRAYER = 5;

    /*
     * Dragon breath, applied once "before the first round of combat" per
     * every RSC-specific source found for it (classic.runescape.wiki, each
     * citing RSC+ replay recordings of real 2001 sessions -- there is no
     * client-cache equivalent for a server-side combat mechanic like this
     * one, unlike sprites/stats). This replaced a first attempt that guessed
     * one uniform "percent of current hits, shield halves it" shape from a
     * generic overview page; the real mechanic is a different shape per
     * dragon:
     *
     *   Black (291): unshielded drains Attack/Defense/Strength 4% each and
     *   Hits 20%; shielded, the drain is replaced by 10% Hits damage instead
     *   (and that shielded damage is explicitly noted to bypass Paralyze
     *   Monster). This is the one the user supplied verbatim and it was
     *   checked word for word against the live page.
     *
     *   Blue (202): -1 flat (not percent) to Attack/Defense/Strength plus 15%
     *   Hits unshielded; 5% Hits, no stat drop, shielded. Blue dragons ONLY
     *   breathe if THEY start the fight -- see dragonInitiated below -- a
     *   condition no other dragon's page mentions.
     *
     *   Baby blue (203): 10% Hits unshielded; shielded, the page says the
     *   breath "will usually not deal any damage" with no percentage given
     *   for the rare exception, so shielded is modeled as no damage at all.
     *
     * Two real, checked-twice gaps, left unquantified rather than guessed:
     * Red dragon (201) drains melee stats per its page, but the page never
     * gives a number for that drain anywhere, only for its 5%/3% Hits
     * damage, which IS applied; King Black Dragon (477) melee breath reduces
     * Prayer "vastly" with no number given, so Prayer is left untouched
     * while its quantified Attack/Defense/Strength (2%) and Hits (20%/15%)
     * figures are applied. Baby Red Dragon (797) has no RSC wiki page at all
     * and gets no breath effect. King Black Dragon's separate ranged/magic-
     * triggered variant, and Elvarg/KBD's projectile-instant-breath rule,
     * are not modeled -- no hook into the ranged-vs-npc attack path exists
     * yet to trigger from. Elvarg's own continuous small "unavoidable"
     * breath damage is handled per-round in FightEvent, not here; this
     * method only applies her 80%-Prayer-drain-on-engage.
     *
     * On the percentage model vs a flat damage roll -- asked and answered,
     * recorded here because it will be asked again:
     *
     * The percentages above come from the wiki. The only direct packet
     * evidence anyone has, the two Dragon Slayer captures behind
     * elvargSpellBreath below, shows a flat roll instead (0-65 unshielded,
     * 0-13 shielded) with no reference to current hits. OpenRSC independently
     * uses flat per-dragon maxima too (Elvarg and King Black Dragon 65, Red
     * and Black 55, Blue 50, baby blue a percentage of current hits) plus
     * armour mitigation we have no equivalent of at all: dragonhide/scale
     * roughly -10%, plate -15%. That looked like grounds to suspect the
     * percentages were RS2 written back onto the Classic pages.
     *
     * They are not. Every percentage above carries a {{CiteReplay}} pointing
     * at a specific RSC 2001 replay file -- Black's 4%/20% has three of them,
     * Blue's -1 and 15% several including one from a folder named for
     * dragonfire stat effects, Elvarg's 80% Prayer two. That is the same
     * class of evidence as the packet captures, not an editor importing from
     * a later game, so switching would trade cited capture for uncited
     * inference across six monsters.
     *
     * The apparent conflict was mostly an artefact of what got compared.
     * Elvarg is already flat on the wiki: her article says up to 50 damage
     * unshielded and up to 12 shielded, and her infobox gives a dragonfire
     * max hit of 65. The captured 0-65/0-13 agrees with that and sharpens it;
     * only the qualitative summary table ("Very high"/"Low") reads as
     * disagreement. So Elvarg is flat here and the other five stay
     * percentage-based, which is what both bodies of evidence actually say.
     * If a CiteReplay-backed absolute damage number ever turns up for one of
     * the five, that would reopen it; a search for one came back empty.
     */
    // ------------------------------------------------------------ silverlight --

    /** Silverlight's item id. */
    private static final int SILVERLIGHT = 52;

    /**
     * Every npc the wiki's Demon article names as a demon and that exists in
     * our defs. Othanian is on that list and has no npc here, so it is absent
     * rather than forgotten.
     *
     * Duplicate ids are real: Lesser Demon is both 22 and 181, Black Demon both
     * 290 and 568, and Kolodion has six because each of his forms is its own
     * npc. Kolodion is included for completeness only -- the Mage Arena entry
     * rule turns away every weapon, so no player can reach him holding this
     * sword.
     */
    private static final java.util.Set<Integer> DEMONS =
        new java.util.HashSet<Integer>(Arrays.asList(
            22,   // Lesser Demon
            35,   // Delrith
            114,  // Imp
            181,  // Lesser Demon
            184,  // Greater Demon
            288,  // Thrantax
            290,  // Black Demon
            315,  // Chronozon
            568,  // Black Demon
            646,  // Doomion
            647,  // Holthion
            712, 713, 757, 758, 759, 760,  // Kolodion, all forms
            769   // Nezikchened
        ));

    /**
     * Silverlight's weapon power when the thing on the other end is a demon.
     * The sword is aim 10 / power 10 in the tables, which is a little worse
     * than an iron long sword, and the wiki says that against a demon its
     * power is "similar to that of a runite long sword" -- 49 in our own item
     * defs, which is the number used here. Aim is deliberately not touched:
     * the same article is explicit that the sword "significantly boosts
     * WeaponPower while fighting demons" but "has a low WeaponAim", so it
     * hits no more often, it hits harder.
     */
    private static final int SILVERLIGHT_DEMON_POWER = 49;

    /**
     * How much of a demon's attack, defence and strength Silverlight takes off:
     * fifteen percent of the demon's CURRENT level in each.
     *
     * Not our number. It is RSCSundae's `data/lua/rs1/items/silverlight.lua`,
     * three `npcsubstat(npc, STAT_*, 0, 15)` calls, cited to a named 2018
     * replay capture (`replays/fsnom2@aol.com/07-30-2018 12.37.33`). RSCSundae
     * is public domain, so this is usable outright, and percent-of-current is
     * the same substat semantic we already adopted for dragonfire -- their
     * `stat_remove` in `src/stat.c`, sourced to Stormykins' dragonfire research.
     *
     * An earlier revision of this used three quarters, fitted to the one figure
     * the wiki gives (Delrith's weakened max hit of 3). That fit was worthless:
     * our maxHit() returns 4 for Delrith weakened OR not under either the
     * current strength constant or the corrected one, so the number being
     * fitted was unreachable and the multiplier was really measuring our own
     * max-hit defect rather than Silverlight. An attested implementation beats
     * a curve fit through a point we cannot land on.
     */
    private static final int SILVERLIGHT_WEAKEN_PERCENT = 15;

    public static boolean isDemon(Mob mob) {
        return mob instanceof Npc && DEMONS.contains(((Npc) mob).getID());
    }

    private static boolean wieldingSilverlight(Mob mob) {
        return mob instanceof Player
            && ((Player) mob).getInventory().wielding(SILVERLIGHT);
    }

    /**
     * Called once at the start of a fight, from both places a fight can begin.
     * Whether the player swung first or the demon did, what matters is that the
     * sword was already in hand -- drawing it mid-fight does nothing, which is
     * the one negative the wiki states outright.
     *
     * The drain goes through the same {@link Npc#drain} the curse spells use,
     * which gets two things right for free: it comes off the getters so a
     * weapon switch cannot undo it, and it dies with the npc, since a kill
     * builds a fresh Npc from the NPCLoc. The guard flag is what keeps it to
     * once per demon -- without it, walking away and re-engaging would drain
     * another quarter each time and eventually reduce anything to zero.
     */
    /**
     * What a teleport does to contraband, run before any teleport moves the
     * player -- the spell teleports and the charged dragonstone amulet both
     * call it. Two rules, both OpenRSC's authentic behaviour:
     *
     * Karamja rum vanishes, but only when the teleport starts on the island
     * (Point.inKaramja). That is the mechanic -- rum cannot leave Karamja
     * except hidden in the banana crate -- not a curse on the bottle: on the
     * mainland it is legal cargo and teleports fine. No message; the customs
     * officers get one because they are searching you, magic just loses it.
     *
     * The plague sample disintegrates on any teleport, with Jagex's two lines.
     * Biohazard's header used to record this as a known gap ("there is no hook
     * on teleporting"); this is that hook.
     */
    public static void teleportContraband(Player p) {
        final int KARAMJA_RUM = 318;
        final int PLAGUE_SAMPLE = 812;
        if (p.getLocation().inKaramja() && p.getInventory().countId(KARAMJA_RUM) > 0) {
            p.getInventory().remove(KARAMJA_RUM, p.getInventory().countId(KARAMJA_RUM));
            p.getActionSender().sendInventory();
        }
        if (p.getInventory().countId(PLAGUE_SAMPLE) > 0) {
            p.getActionSender().sendMessage("the plague sample is too delicate...");
            p.getActionSender().sendMessage("it disintegrates in the crossing");
            p.getInventory().remove(PLAGUE_SAMPLE, p.getInventory().countId(PLAGUE_SAMPLE));
            p.getActionSender().sendInventory();
        }
    }

    public static void applySilverlight(Npc demon, Player attacker) {
        if (!isDemon(demon) || demon.isSilverlightWeakened()
                || !wieldingSilverlight(attacker)) {
            return;
        }
        demon.setSilverlightWeakened(true);
        for (int stat = 0; stat < 3; ++stat) {
            int current = demon.getBaseStat(stat) - demon.getDrain(stat);
            demon.drain(stat, (int)((double)current * SILVERLIGHT_WEAKEN_PERCENT / 100.0));
        }
        attacker.getActionSender().sendMessage(
            "As you strike the demon with silverlight he appears to weaken a lot");
    }

    /**
     * The attacker's weapon power for this swing, which is its ordinary power
     * except when Silverlight meets a demon.
     */
    private static int weaponPowerAgainst(Mob attacker, Mob defender) {
        if (isDemon(defender) && wieldingSilverlight(attacker)) {
            return SILVERLIGHT_DEMON_POWER;
        }
        return attacker.getWeaponPowerPoints();
    }

    public static void applyDragonBreath(Npc dragon, Player target, boolean dragonInitiated) {
        boolean shielded = target.getInventory().wielding(ANTI_DRAGON_SHIELD);
        switch (dragon.getID()) {
            case 291:
                if (shielded) {
                    drainStat(target, DB_HITS, 0.10);
                } else {
                    drainStat(target, DB_ATTACK, 0.04);
                    drainStat(target, DB_DEFENSE, 0.04);
                    drainStat(target, DB_STRENGTH, 0.04);
                    drainStat(target, DB_HITS, 0.20);
                }
                break;
            case 201:
                drainStat(target, DB_HITS, shielded ? 0.03 : 0.05);
                break;
            case 202:
                if (!dragonInitiated) {
                    break;
                }
                if (shielded) {
                    drainStat(target, DB_HITS, 0.05);
                } else {
                    /*
                     * "Melee stats below 50 will not be lowered" -- the Blue
                     * Dragon page, on its own replay citation, and the only
                     * dragon with a floor like this. Without it a level-40
                     * Strength gets drained where the capture says it stays
                     * put.
                     *
                     * Read against the current stat rather than the base
                     * level, because that is what the sentence says and it is
                     * also what a replay could actually show: an observer
                     * watching a number not move cannot tell which of the two
                     * the game checked. It makes the drain self-limiting --
                     * 50 goes to 49 and stops there -- which is the more
                     * conservative of the two readings.
                     */
                    dropStatFlatAbove(target, DB_ATTACK, 1, 50);
                    dropStatFlatAbove(target, DB_DEFENSE, 1, 50);
                    dropStatFlatAbove(target, DB_STRENGTH, 1, 50);
                    drainStat(target, DB_HITS, 0.15);
                }
                break;
            case 203:
                if (!shielded) {
                    drainStat(target, DB_HITS, 0.10);
                }
                break;
            case 477:
                if (shielded) {
                    drainStat(target, DB_HITS, 0.15);
                } else {
                    drainStat(target, DB_ATTACK, 0.02);
                    drainStat(target, DB_DEFENSE, 0.02);
                    drainStat(target, DB_STRENGTH, 0.02);
                    drainStat(target, DB_HITS, 0.20);
                }
                break;
            case 196:
                drainPrayer80(target);
                break;
            default:
                break;
        }
    }

    private static void drainStat(Player target, int statId, double fraction) {
        int amount = DataConversions.roundUp(target.getCurStat(statId) * fraction);
        target.setCurStat(statId, target.getCurStat(statId) - amount);
        target.getActionSender().sendStat(statId);
    }

    private static void dropStatFlat(Player target, int statId, int amount) {
        target.setCurStat(statId, target.getCurStat(statId) - amount);
        target.getActionSender().sendStat(statId);
    }

    /** As dropStatFlat, but leaves a stat alone once it is below floor. */
    private static void dropStatFlatAbove(Player target, int statId, int amount, int floor) {
        if (target.getCurStat(statId) < floor) {
            return;
        }
        dropStatFlat(target, statId, amount);
    }

    /**
     * Elvarg's Prayer drain: 80% of CURRENT Prayer, truncated.
     *
     * The rounding is not a style choice, it is the only one that fits the
     * evidence. Two independent Dragon Slayer packet captures preserved by
     * the RSCSundae project (public domain, both cited in its
     * quest/dragon_slayer/dragon.lua) record the before/after Prayer:
     *
     *   46/46 -> 10/46   reduction 36.  46 * 0.8 = 36.8, truncated 36. OK
     *                                   rounded up that is 37, giving 9. NO
     *   35/42 ->  7/42   reduction 28.  35 * 0.8 = 28.0 exactly. OK either way
     *
     * So truncation reproduces both captures and rounding up reproduces only
     * one. Note this figure was re-derived from those captures rather than
     * taken from RSCSundae's own code, which also subtracts a flat 1 on top
     * and therefore does not reproduce either capture (it lands on 9 and 6).
     *
     * The drain compounds: it is 80% of what you have left, applied on every
     * breath, which is why prayer collapses so fast in this fight.
     */
    private static void drainPrayer80(Player target) {
        int current = target.getCurStat(DB_PRAYER);
        target.setCurStat(DB_PRAYER, current - (int)(current * 0.80));
        target.getActionSender().sendStat(DB_PRAYER);
    }

    public static final int ELVARG = 196;

    /**
     * Elvarg's breath, shared by her per-round melee attacks and by
     * SpellHandler when the player casts a spell at her.
     *
     * The roll and messages are the one part of this fight there is direct
     * packet evidence for: two Dragon Slayer captures preserved by
     * RSCSundae (public domain; Revisionism 2018-06-16 and ShaunDreclin
     * 2018-06-08) show a mage being breathed on before every successful
     * cast, with these damage rolls --
     *
     *   with an anti-dragon-breath shield:  0-13
     *   without one:                        0-65
     *
     * -- and the quest guide's "hits you constantly for small numbers and
     * is unavoidable" matches the shielded end of that range.
     *
     * Originally this method covered only the spell-cast case, and melee
     * used a separate flat shield-blind 1-10 chip, because the captures are
     * of a mage and say nothing about what she does to someone meleeing
     * her. That scoping is now overridden by a second, independent oracle:
     * 2003scape's rsc-server (`plugins/npcs/dragon.js`, cited to the RSC
     * wiki's "Dragon (race)" and "Dragon (Dragon Slayer)" pages) applies
     * this exact same roll -- 0-65 unshielded, x0.2 shielded (=0-13,
     * matching our figure exactly) -- unconditionally on every dragon
     * attack, melee included, with the same three message strings. Two
     * independently-sourced numeric matches is stronger than the silence
     * of a mage-only capture, so the shield now mitigates melee too.
     *
     * The unshielded roll really is that brutal, and it is why the shield
     * is not optional equipment for this quest -- an unlucky roll kills
     * outright.
     */
    public static int elvargBreath(Player target) {
        boolean shielded = target.getInventory().wielding(ANTI_DRAGON_SHIELD);
        target.getActionSender().sendMessage("@que@The dragon breathes fire at you");
        int damage;
        if (shielded) {
            target.getActionSender().sendMessage("@que@Your shield prevents some of the damage from the flames");
            damage = DataConversions.random(0, 13);
        } else {
            target.getActionSender().sendMessage("@que@You are fried");
            damage = DataConversions.random(0, 65);
        }
        drainPrayer80(target);
        return damage;
    }

    /** Kept as a name FightEvent's per-round chip calls -- see elvargBreath. */
    public static int elvargBreathChip(Player target) {
        return elvargBreath(target);
    }

    /**
     * Elvarg's breath as it answers a spell. The unlucky-roll-kills-outright
     * case routes through killedBy exactly as Npc.attack does when its
     * engage breath is lethal -- see the caller in SpellHandler.
     */
    public static void elvargSpellBreath(Player target) {
        int damage = elvargBreath(target);
        target.setCurStat(DB_HITS, Math.max(0, target.getCurStat(DB_HITS) - damage));
        target.getActionSender().sendStat(DB_HITS);
    }

    public static final int[] bowIDs = new int[]{188, 189, 648, 649, 650, 651, 652, 653, 654, 655, 656, 657};
    public static final int[] boltIDs = new int[]{786, 592, 190};
    public static final int[] xbowIDs = new int[]{59, 60};

    /** The longbows, by id. They are the only weapons that reach five tiles. */
    private static final int[] longbowIDs = new int[]{188, 648, 650, 652, 654, 656};

    /**
     * How many tiles this weapon can shoot.
     *
     * Jagex's own projectile.txt (config46.jag -- see arrowPower) records a
     * range against each projectile, and it is not one number: the longbow
     * arrow reaches 5, the shortbow arrow and the crossbow bolt reach 4.
     * That difference is the whole point of a longbow, and it had been
     * flattened to 5 for everything, which quietly made the shortbow strictly
     * better than the longbow at every tier -- faster, cheaper, same reach.
     */
    public static int bowRange(int bowID) {
        return DataConversions.inArray(longbowIDs, bowID) ? 5 : 4;
    }
    public static final int[] safePacketIDs = new int[]{70, 123, 128, 255};
    public static final int[] headSprites = new int[]{1, 4, 6, 7, 8};
    public static final int[] bodySprites = new int[]{2, 5};
    public static final int[] runeIDs = new int[]{31, 32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 46, 619, 825, 958};
    public static final int[] potionsUnfinished = new int[]{454, 455, 456, 457, 458, 459, 460, 461, 462, 463};
    public static final int[] potions1Dose = new int[]{224, 476, 479, 482, 485, 488, 491, 494, 497, 500, 568, 571};
    public static final int[] potions2Dose = new int[]{223, 475, 478, 481, 484, 487, 490, 493, 496, 499, 567, 570};
    public static final int[] potions3Dose = new int[]{222, 474, 477, 480, 483, 486, 489, 492, 495, 498, 566, 569};
    private static Random r = new Random();

    public static int Rand(int low, int high) {
        return low + r.nextInt(high - low);
    }

    public static int getPotionDose(int id) {
        if (DataConversions.inArray(potions1Dose, id)) {
            return 1;
        }
        if (DataConversions.inArray(potions2Dose, id)) {
            return 2;
        }
        if (DataConversions.inArray(potions3Dose, id)) {
            return 3;
        }
        return 0;
    }

    public static String getLvlDiffColour(int lvlDiff) {
        if (lvlDiff < -9) {
            return "@red@";
        }
        if (lvlDiff < -6) {
            return "@or3@";
        }
        if (lvlDiff < -3) {
            return "@or2@";
        }
        if (lvlDiff < 0) {
            return "@or1@";
        }
        if (lvlDiff > 9) {
            return "@gre@";
        }
        if (lvlDiff > 6) {
            return "@gr3@";
        }
        if (lvlDiff > 3) {
            return "@gr2@";
        }
        if (lvlDiff > 0) {
            return "@gr1@";
        }
        return "@whi@";
    }

    public static int getStat(String stat) {
        for (int i = 0; i < statArray.length; ++i) {
            if (!statArray[i].equalsIgnoreCase(stat)) continue;
            return i;
        }
        return -1;
    }

    public static boolean catchThief(int lvl, int reqLevel) {
        double rand = (r.nextDouble() * 100.0 + 1.0) / 100.0;
        double success = Formulae.getMiningFailPercent(lvl, reqLevel) / 100.0;
        if (success < 0.35) {
            success = 0.35;
        }
        if (reqLevel < 15 && lvl - reqLevel < 10 && Formulae.Rand(1, 10) == 5) {
            success = 1.0;
        }
        return !(rand < success);
    }

    /**
     * Does this Agility obstacle throw the player off?
     *
     * Classic recorded exactly one number about failing: a level 72 player
     * failed the Yanille agility dungeon rope swing "about one time in
     * thirty-five", the swing asks for 57, and it stops throwing anyone at 77.
     * Nothing else about the roll survived -- not its shape, not its value at
     * the requirement, not whether every obstacle used the same one. So the
     * curve here is this server's and not Jagex's:
     *
     *     p = 0.45 * ((stop - lvl) / (stop - req))^2
     *
     * A square rather than a straight line because the one data point is far
     * too low for a line: at level 72 a linear fall from 0.45 would still be
     * throwing the player one time in nine, and the record says one in
     * thirty-five. The square gives one in thirty-two there, which is as close
     * as a made-up curve has any business being. At the requirement itself it
     * is a little under one attempt in two, and it reaches zero at stop, which
     * is what "you stop failing at 65" means for the ledge.
     */
    public static boolean failAgility(int lvl, int reqLevel, int stopLevel) {
        if (lvl >= stopLevel || stopLevel <= reqLevel) {
            return false;
        }
        double span = (double)(stopLevel - Math.min(lvl, stopLevel)) / (double)(stopLevel - reqLevel);
        return r.nextDouble() < 0.45 * span * span;
    }

    /**
     * One roll of the crystal chest, as items ready to hand over.
     *
     * The chest picks a possibility, not an item: whichever one it lands on
     * gives everything on it. Weights are relative and the roll is against
     * their sum, so KeyChestLoot.xml.gz can keep the source's own numbers
     * without them having to add up to anything in particular.
     *
     * The uncut dragonstone is not in here. It is not part of the roll -- the
     * chest gives one every time -- and the caller adds it.
     *
     * Every InvItem returned is new, and a non-stackable stack is already
     * spread into one item per bag slot, so the caller can add them straight
     * across and the list's length is the number of slots it needs. This used
     * to return the loaded document's own list, and the InvItems in it went
     * into players' inventories by reference: Inventory.add matches on id
     * alone, so the next coins a player picked up were added to the chest's
     * own coin item and the drop grew for everyone until the server restarted.
     */
    public static List<InvItem> getKeyChestLoot() {
        ChestLootDef[] table = EntityHandler.getKeyChestLoots();
        int total = 0;
        for (ChestLootDef possibility : table) {
            total += possibility.getWeight();
        }

        ChestLootDef rolled = table[table.length - 1];
        int hit = DataConversions.random(0, total - 1);
        for (ChestLootDef possibility : table) {
            if (hit < possibility.getWeight()) {
                rolled = possibility;
                break;
            }
            hit -= possibility.getWeight();
        }

        List<InvItem> loot = new ArrayList<InvItem>();
        for (ItemDropDef entry : rolled.getItems()) {
            spread(loot, entry);
        }
        List<ItemDropDef> choice = rolled.getChoice();
        if (!choice.isEmpty()) {
            spread(loot, choice.get(DataConversions.random(0, choice.size() - 1)));
        }
        return loot;
    }

    /** One item per bag slot: coins stack, runite bars do not. */
    private static void spread(List<InvItem> into, ItemDropDef entry) {
        InvItem item = new InvItem(entry.getID(), entry.getAmount());
        if (item.getDef().isStackable() || entry.getAmount() <= 1) {
            into.add(item);
            return;
        }
        for (int i = 0; i < entry.getAmount(); i++) {
            into.add(new InvItem(entry.getID(), 1));
        }
    }

    public static boolean doorAtFacing(Entity e, int x, int y, int dir) {
        if (dir >= 0 && e instanceof GameObject) {
            GameObject obj = (GameObject)e;
            return obj.getType() == 1 && obj.getDirection() == dir && obj.isOn(x, y);
        }
        return false;
    }

    public static boolean objectAtFacing(Entity e, int x, int y, int dir) {
        if (dir >= 0 && e instanceof GameObject) {
            GameObject obj = (GameObject)e;
            return obj.getType() == 0 && obj.getDirection() == dir && obj.isOn(x, y);
        }
        return false;
    }

    public static int bitToDoorDir(int bit) {
        switch (bit) {
            case 1: {
                return 0;
            }
            case 2: {
                return 1;
            }
            case 4: {
                return -1;
            }
            case 8: {
                return -1;
            }
        }
        return -1;
    }

    public static int bitToObjectDir(int bit) {
        switch (bit) {
            case 1: {
                return 6;
            }
            case 2: {
                return 0;
            }
            case 4: {
                return 2;
            }
            case 8: {
                return 4;
            }
        }
        return -1;
    }

    public static int getNewY(int currentY, boolean up) {
        int newHeight;
        int height = Formulae.getHeight(currentY);
        if (up) {
            if (height == 3) {
                newHeight = 0;
            } else {
                if (height >= 2) {
                    return currentY;
                }
                newHeight = height + 1;
            }
        } else if (height == 0) {
            newHeight = 3;
        } else {
            if (height >= 3) {
                return currentY;
            }
            newHeight = height - 1;
        }
        return newHeight * 944 + currentY % 944;
    }

    public static int getEmptyJug(int fullJug) {
        switch (fullJug) {
            case 50: {
                return 21;
            }
            case 141: {
                return 140;
            }
            case 342: {
                return 341;
            }
        }
        return -1;
    }

    /**
     * Base odds of a swing at a rock turning up a gem instead of its ore.
     *
     * One in 201, which is what the mining code has always rolled.
     */
    private static final int GEM_ODDS = 201;

    /**
     * The same odds scaled for a charged dragonstone amulet, as a numerator
     * over GEM_DENOMINATOR.
     *
     * The amulet is documented as a 40% greater chance of finding a gem, so
     * 1/201 becomes 1.4/201. Integers cannot hold that, so both cases are
     * expressed over a common denominator of 1005: 5/1005 plain, 7/1005 with
     * the amulet, and 7/5 is exactly 1.4.
     */
    private static final int GEM_DENOMINATOR = GEM_ODDS * 5;

    /**
     * Whether this swing found a gem.
     *
     * The bonus is the other half of what the Fountain of heros promises when
     * it charges an amulet -- "It now also means you can find more gems when
     * mining" -- and it had never been implemented, so the fountain was making
     * a claim the server did not honour.
     *
     * Only the charged amulet counts. An uncharged 522 has the same combat
     * stats but no gem bonus, which is one of the few things that makes the
     * charged one worth keeping charged beyond the teleports.
     *
     * Worn rather than merely carried. The sources say "whilst mining" with
     * one, without ever spelling out which; every other amulet effect in the
     * game needs it round your neck, so this one does too.
     */
    public static boolean foundGem(Player player) {
        int chances = player.isWearing(597) ? 7 : 5;
        return DataConversions.random(0, GEM_DENOMINATOR - 1) < chances;
    }

    public static int getGem() {
        int rand = DataConversions.random(0, 100);
        if (rand < 10) {
            return 157;
        }
        if (rand < 30) {
            return 158;
        }
        if (rand < 60) {
            return 159;
        }
        return 160;
    }

    public static boolean crackPot(int requiredLvl, int craftingLvl) {
        int levelDiff = craftingLvl - requiredLvl;
        if (levelDiff < 0) {
            return true;
        }
        if (levelDiff >= 20) {
            return false;
        }
        return DataConversions.random(0, levelDiff + 1) == 0;
    }

    public static boolean castSpell(SpellDef def, int magicLevel, int magicEquip) {
        int levelDiff = magicLevel - def.getReqLevel();
        if (levelDiff < 0) {
            return false;
        }
        if (levelDiff >= 10) {
            return true;
        }
        return DataConversions.random(0, (levelDiff + 2) * 2) != 0;
    }

    public static boolean looseArrow(int damage) {
        return DataConversions.random(0, 6) == 0;
    }

    public static int getSmithingExp(int barID, int barCount) {
        int[] exps = new int[]{13, 25, 37, 50, 83, 74};
        int type = Formulae.getBarType(barID);
        if (type < 0) {
            return 0;
        }
        return exps[type] * barCount;
    }

    public static int minSmithingLevel(int barID) {
        int[] levels = new int[]{1, 15, 30, 50, 70, 85};
        int type = Formulae.getBarType(barID);
        if (type < 0) {
            return -1;
        }
        return levels[type];
    }

    public static int getBarType(int barID) {
        switch (barID) {
            case 169: {
                return 0;
            }
            case 170: {
                return 1;
            }
            case 171: {
                return 2;
            }
            case 173: {
                return 3;
            }
            case 174: {
                return 4;
            }
            case 408: {
                return 5;
            }
        }
        return -1;
    }

    public static int firemakingExp(int level, int baseExp) {
        return DataConversions.roundUp(baseExp + level);
    }

    public static boolean lightLogs(FiremakingDef def, int firemakingLvl) {
        int levelDiff = firemakingLvl - def.getRequiredLevel();
        if (levelDiff < 0) {
            return false;
        }
        if (levelDiff >= 20) {
            return true;
        }
        return DataConversions.random(0, levelDiff + 1) != 0;
    }

    public static long generateSessionKey(byte userByte) {
        return DataConversions.getRandom().nextLong();
    }

    public static int getStatIndex(String stat) {
        for (int index = 0; index < statArray.length; ++index) {
            if (!stat.equalsIgnoreCase(statArray[index])) continue;
            return index;
        }
        return -1;
    }

    /*
     * The real per-kill award is (2 x combat level + 20) displayed
     * experience in total, verified against the classic wiki's Experience &
     * Fatigue tables (chicken, CL 3: hits 6.5, melee 19.5, ranged 26;
     * lesser demon, CL 79: 44.5 / 133.5 / 178). The old
     * (CL*10+10)*0.22 matched nothing in those tables.
     *
     * Returned in quarter-units, where the total divides exactly: this
     * value IS the hits share, the trained melee stat gets three of them,
     * and a ranged kill takes all four.
     *
     * The reduction for player victims is inherited RSCD behaviour, kept on
     * purpose: nothing attests PvP experience either way, and full-rate
     * PKing is a large balance lever to pull on a guess.
     */
    public static int combatExperienceQuarters(Mob mob) {
        int share = mob.getCombatLevel() * 2 + 20;
        return mob instanceof Player ? share / 4 : share;
    }

    /**
     * The ranged and magic boost trios (Sharp/Hawk/Eagle eye, Mystic
     * will/lore/might) -- ours, not Jagex's; RSC shipped no ranged or magic
     * prayers at all. Same 5/10/15 percent ladder as the melee trios so no
     * combat style prays harder than another. Ranged multiplies the ranged
     * stat wherever calcRangeHit reads it (accuracy and damage both -- one
     * trio has to do the work of melee's two); magic multiplies the
     * effective magic level in the cast-failure roll and the god-spell
     * hit chance.
     */
    public static double rangedPrayerBoost(Mob m) {
        return Formulae.addPrayers(m.isPrayerActivated(14), m.isPrayerActivated(16), m.isPrayerActivated(18));
    }

    public static double magicPrayerBoost(Mob m) {
        return Formulae.addPrayers(m.isPrayerActivated(15), m.isPrayerActivated(17), m.isPrayerActivated(19));
    }

    private static double addPrayers(boolean first, boolean second, boolean third) {
        if (third) {
            return 1.15;
        }
        if (second) {
            return 1.1;
        }
        if (first) {
            return 1.05;
        }
        return 1.0;
    }

    /**
     * The power of one piece of ammunition, on Jagex's scale -- the number
     * that goes into (level + 8) * (power + 64), not a damage in hitpoints.
     *
     * Where the scale comes from. Jagex's own projectile.txt, inside
     * config46.jag (the December 2001 client config, recovered from the
     * Internet Archive capture of 66.28.11.53 and checked against the
     * published SHA-256 304ff714...4061e94), gives three player projectiles
     * and no others:
     *
     *     shortbowarrow   range 4   aim 10   power 14
     *     longbowarrow    range 5   aim 16   power 20
     *     crossbowbolt    range 4   aim 12   power 22
     *
     * That is 2001, before Fletching: there is one arrow, and the power
     * hangs off the bow. By 2003 the bow carries only aim and the arrow
     * carries the power, and no surviving Jagex file records the per-tier
     * numbers -- bronze through rune arrows simply did not exist when
     * config46 was written.
     *
     * So the tiers below are a reconstruction, not a recovery. They follow
     * the step-of-5 rule the preservation community settled on, anchored so
     * that the bottom rung lands where Jagex's own numbers are: a bronze
     * arrow at 15 against Jagex's shortbow arrow at 14, a crossbow bolt at
     * 20 against Jagex's 22. Treat the ladder as best evidence and the two
     * ends as measurement.
     *
     * Everything past the arrows and bolts -- darts, knives, spears -- is
     * unreachable today, because nothing throws them (see arrowIDs and
     * boltIDs, and the thrown-weapon gap in FORMULA-AUDIT.md). They are kept
     * on the same scale so that whoever wires throwing up does not have to
     * rediscover it.
     */
    private static int arrowPower(int arrowID) {
        switch (arrowID) {
            case 11:    // Bronze Arrows
            case 574: { // Poison Bronze Arrows
                return 15;
            }
            case 638:   // Iron Arrows
            case 639: { // Poison Iron Arrows
                return 20;
            }
            case 640:   // Steel Arrows
            case 641: { // Poison Steel Arrows
                return 25;
            }
            case 642:   // Mithril Arrows
            case 643: { // Poison Mithril Arrows
                return 30;
            }
            case 644:   // Adamantite Arrows
            case 645: { // Poison Adamantite Arrows
                return 35;
            }
            case 646:   // Rune Arrows
            case 647:   // Poison Rune Arrows
            case 723: { // Ice Arrows -- quest ammunition, no attested power.
                        // Held level with rune rather than above it: nothing
                        // in the game suggests Jagex made a quest arrow the
                        // strongest in the game, and the one fight that wants
                        // them (the Fire warrior of Lesarkus) is scripted.
                return 40;
            }
            case 190:   // Crossbow bolts
            case 592: { // Poison Crossbow bolts
                return 20;
            }
            case 786: { // Oyster pearl bolts
                return 30;
            }
            case 1013:  // Bronze Throwing Dart
            case 1122: {
                return 15;
            }
            case 1015:  // Iron Throwing Dart
            case 1123: {
                return 17;
            }
            case 1024:  // Steel Throwing Dart
            case 1124: {
                return 22;
            }
            case 1068:  // Mithril Throwing Dart
            case 1125: {
                return 25;
            }
            case 1069:  // Adamantite Throwing Dart
            case 1126: {
                return 27;
            }
            case 1070:  // Rune Throwing Dart
            case 1127: {
                return 30;
            }
            case 1076:  // Bronze throwing knife
            case 1128: {
                return 25;
            }
            case 1075:  // Iron throwing knife
            case 1129: {
                return 30;
            }
            case 1077:  // Steel throwing knife
            case 1130:
            case 1081:  // Black throwing knife
            case 1132: {
                return 35;
            }
            case 1078:  // Mithril throwing knife
            case 1131: {
                return 40;
            }
            case 1079:  // Adamantite throwing knife
            case 1133: {
                return 45;
            }
            case 1080:  // Rune throwing knife
            case 1134: {
                return 50;
            }
            case 827:   // Bronze Spear
            case 1135: {
                return 29;
            }
            case 1088:  // Iron Spear
            case 1136: {
                return 37;
            }
            case 1089:  // Steel Spear
            case 1137: {
                return 46;
            }
            case 1090:  // Mithril Spear
            case 1138: {
                return 53;
            }
            case 1091:  // Adamantite Spear
            case 1139: {
                return 61;
            }
            case 1092:  // Rune Spear
            case 1140: {
                return 69;
            }
        }
        // Swamp paste (785) used to sit here at the top of the scale. It is
        // not ammunition, nothing can fire it, and it never reached this
        // method; it is gone rather than rescaled.
        return 0;
    }

    public static double getMiningFailPercent(double curLvl, double reqLvl) {
        double dif = curLvl - reqLvl;
        return 3.27 * Math.pow(10.0, -6.0) * Math.pow(dif, 4.0) + -5.516 * Math.pow(10.0, -4.0) * Math.pow(dif, 3.0) + 0.014307 * Math.pow(dif, 2.0) + 1.65560813 * dif + 18.2095966;
    }

    /**
     * One roll of Jagex's combat maximum: a level against a bonus.
     *
     * Every roll in Classic combat -- attack, defence, strength, ranged --
     * is this same expression. Our melee max hit already used it in another
     * form; this is it named once so the ranged path and any future one can
     * share it.
     */
    private static int combatMaxRoll(int stat, int bonus) {
        return stat * (64 + bonus);
    }

    /**
     * The defence side of any roll against this mob, on Jagex's scale.
     *
     * Players get the +8 constant, their defence prayers, their combat style
     * and their armour. Npcs get their defence level flat: they carry no
     * equipment, so there is no armour bonus to add and no +8 -- which is
     * why a low-level monster is easy to hit and a dragon is not, instead of
     * everything in the game defending identically.
     */
    private static int combatDefenceRoll(Mob defender) {
        if (!(defender instanceof Player)) {
            return Formulae.combatMaxRoll(defender.getDefense(), 0);
        }
        int stat = (int)(Formulae.addPrayers(defender.isPrayerActivated(0), defender.isPrayerActivated(3), defender.isPrayerActivated(9)) * (double)defender.getDefense()) + 8 + Formulae.styleBonus(defender, 1);
        return Formulae.combatMaxRoll(stat, defender.getArmourPoints());
    }

    /**
     * Damage from one arrow, or 0 for a miss.
     *
     * This is Jagex's ranged model, and it replaces an inherited one that
     * was wrong in both halves.
     *
     * What it was. Accuracy came from a "ratio" built out of the bow's bonus
     * and the target's armour and nothing else -- neither combatant's levels
     * appeared -- and then a miss was thrown away again on a coin flip. The
     * effect was that every shot in the game landed between 80.7% (shortbow)
     * and 86.6% (magic longbow), at every level, against everything from a
     * rat to the King Black Dragon. Damage was rangedLevel * 0.15 + 0.85
     * plus a flat 0-to-7 for the arrow, so a fresh account with rune arrows
     * had a maximum hit of 6 at Ranged 1.
     *
     * What it is now. Two rolls, higher wins, then a damage roll -- the same
     * shape the melee path already uses, and the shape three independent
     * reimplementations of Classic agree on (RSCSundae's mob_combat_roll,
     * OpenRSC's CombatFormula, 2003scape's combat.js; the first is a two-roll
     * comparison, the second the closed form of the same distribution). Aim
     * comes off the bow and power off the arrow, both on Jagex's own scale --
     * see arrowPower for where that scale was recovered from.
     *
     * The consequence worth knowing: ranged now scales. At Ranged 1 with
     * rune arrows the maximum hit is 1, not 6; at Ranged 99 it is 17. And a
     * shot at something well-defended can miss, which it previously could
     * not.
     */
    public static int calcRangeHit(Player attacker, Mob defender, int arrowID) {
        int rangeStat = (int)(Formulae.rangedPrayerBoost(attacker) * (double)attacker.getCurStat(4)) + 8;
        int attRoll = (int)((double)Formulae.combatMaxRoll(rangeStat, attacker.getRangePoints()) * DataConversions.getRandom().nextDouble());
        int defRoll = (int)((double)Formulae.combatDefenceRoll(defender) * DataConversions.getRandom().nextDouble());
        if (attRoll <= defRoll) {
            return 0;
        }
        double damageRoll = (double)Formulae.combatMaxRoll(rangeStat, Formulae.arrowPower(arrowID)) * DataConversions.getRandom().nextDouble();
        return (int)((damageRoll + 320.0) / 640.0);
    }

    public static int calcGodSpells(Mob attacker, Mob defender) {
        int newDef;
        Player owner;
        int newAtt;
        int hitChance;
        if (attacker instanceof Player && (hitChance = DataConversions.random(0, 150 + ((newAtt = (owner = (Player)attacker).getMagicPoints() + (int)(Formulae.magicPrayerBoost(owner) * (double)owner.getCurStat(6))) - (newDef = (int)(Formulae.addPrayers(defender.isPrayerActivated(0), defender.isPrayerActivated(3), defender.isPrayerActivated(9)) * (double)defender.getDefense() / 4.0 + (double)defender.getArmourPoints() / 4.0))))) > (defender instanceof Npc ? 50 : 60)) {
            /*
             * Was Rand(15,25) charged / Rand(0,10) uncharged -- both
             * invented, and both wrong. classic.runescape.wiki, checked
             * directly against Claws of Guthix's and Saradomin strike's own
             * pages: max hit is a flat 18 uncharged, 25 charged (Charge
             * "doubles this spell's power... maximum hit of 25"). These are
             * fixed ceilings, not ranges -- the actual roll's variance
             * already comes from the tiered probability structure below,
             * the same shape calcFightHit uses for ordinary melee.
             */
            int max = owner.isCharged() ? 25 : 18;
            int maxProb = 5;
            int nearMaxProb = 10;
            int avProb = 80;
            int lowHit = 5;
            int shiftValue = (int)Math.round((double)defender.getArmourPoints() * 0.02);
            nearMaxProb -= (int)Math.round((double)shiftValue * 1.5);
            avProb -= (int)Math.round((double)shiftValue * 2.0);
            lowHit += (int)Math.round((double)shiftValue * 3.5);
            int hitRange = DataConversions.random(0, 100);
            if (hitRange >= 100 - (maxProb -= shiftValue)) {
                return max;
            }
            if (hitRange >= 100 - nearMaxProb) {
                return DataConversions.roundUp(Math.abs((double)max - (double)max * ((double)DataConversions.random(0, 10) * 0.01)));
            }
            if (hitRange >= 100 - avProb) {
                int newMax = DataConversions.roundUp((double)max - (double)max * 0.1);
                return DataConversions.roundUp(Math.abs((double)newMax - (double)newMax * ((double)DataConversions.random(0, 50) * 0.01)));
            }
            int newMax = DataConversions.roundUp((double)max - (double)max * 0.5);
            return DataConversions.roundUp(Math.abs((double)newMax - (double)newMax * ((double)DataConversions.random(0, 95) * 0.01)));
        }
        return 0;
    }

    public static int styleBonus(Mob mob, int skill) {
        int style = mob.getCombatStyle();
        if (style == 0) {
            return 1;
        }
        return skill == 0 && style == 2 || skill == 1 && style == 3 || skill == 2 && style == 1 ? 3 : 0;
    }

    public static int calcFightHit(Mob attacker, Mob defender) {
        int newAtt = (int)(Formulae.addPrayers(attacker.isPrayerActivated(2), attacker.isPrayerActivated(5), attacker.isPrayerActivated(11)) * (double)attacker.getAttack() + (double)attacker.getWeaponAimPoints() / 4.0 + (double)Formulae.styleBonus(attacker, 0));
        int newDef = (int)(Formulae.addPrayers(defender.isPrayerActivated(0), defender.isPrayerActivated(3), defender.isPrayerActivated(9)) * (double)defender.getDefense() + (double)defender.getArmourPoints() / 4.0 + (double)Formulae.styleBonus(attacker, 1));
        int hitChance = DataConversions.random(0, 100) + (newAtt - (int)Math.round((double)newDef * 0.85));
        if (hitChance > (defender instanceof Npc ? 50 : 60)) {
            int max = Formulae.maxHit(attacker.getStrength(), Formulae.weaponPowerAgainst(attacker, defender), attacker.isPrayerActivated(1), attacker.isPrayerActivated(4), attacker.isPrayerActivated(10), Formulae.styleBonus(attacker, 2));
            int maxProb = 5;
            int nearMaxProb = 10;
            int avProb = 80;
            int lowHit = 5;
            int shiftValue = (int)Math.round((double)defender.getArmourPoints() * 0.02);
            nearMaxProb -= (int)Math.round((double)shiftValue * 2.0);
            avProb -= (int)Math.round((double)shiftValue * 3.0);
            lowHit += (int)Math.round((double)shiftValue * 2.5);
            int hitRange = DataConversions.random(0, 100);
            if (hitRange >= 100 - (maxProb -= shiftValue)) {
                return max;
            }
            if (hitRange >= 100 - nearMaxProb) {
                return DataConversions.roundUp(Math.abs((double)max - (double)max * ((double)DataConversions.random(0, 10) * 0.01)));
            }
            if (hitRange >= 100 - avProb) {
                int newMax = DataConversions.roundUp((double)max - (double)max * 0.1);
                return DataConversions.roundUp(Math.abs((double)newMax - (double)newMax * ((double)DataConversions.random(0, 50) * 0.01)));
            }
            int newMax = DataConversions.roundUp((double)max - (double)max * 0.5);
            return DataConversions.roundUp(Math.abs((double)newMax - (double)newMax * ((double)DataConversions.random(0, 95) * 0.01)));
        }
        return 0;
    }

    /**
     * Whether a swing at a web gets through it. Classic was a coin flip; this
     * was four times in five, which made every web a formality rather than the
     * small toll it is meant to be. Nothing about the roll depends on level,
     * on the blade, or on fatigue.
     */
    public static boolean cutWeb() {
        return DataConversions.random(0, 1) == 0;
    }

    public static int calcSpellHit(int spellStr, int magicEquip) {
        int mageRatio = (int)(50.0 + (double)magicEquip);
        int max = (int)((double)spellStr / 100.0 * 70.0) + 1;
        int peak = (int)((double)spellStr / 100.0 * (double)mageRatio);
        int dip = (int)((double)peak / 3.0 * 2.0);
        return DataConversions.randomWeighted(0, dip, peak, max);
    }

    /**
     * Food that cannot be spoiled, whatever the cook's level.
     *
     * Only the raw lava eel, which is caught at 53 fishing and cooked at 53
     * cooking -- a level difference of nought, which the table below would burn
     * roughly half the time. Jagex exempted it, presumably because the quest
     * that needs one would otherwise ask a 53-cook for several.
     */
    private static final int[] NEVER_BURNS = { 591 };

    public static boolean burnFood(int foodId, int cookingLevel) {
        for (int id : NEVER_BURNS) {
            if (id == foodId) {
                return false;
            }
        }
        int levelDiff = cookingLevel - EntityHandler.getItemCookingDef(foodId).getReqLevel();
        if (levelDiff < 0) {
            return true;
        }
        if (levelDiff >= 20) {
            return false;
        }
        return DataConversions.random(0, levelDiff + 1) == 0;
    }

    private static int offsetToPercent(int levelDiff) {
        return levelDiff > 40 ? 70 : 30 + levelDiff;
    }

    public static ObjectFishDef getFish(int waterId, int fishingLevel, int click) {
        ArrayList<ObjectFishDef> fish = new ArrayList<ObjectFishDef>();
        for (ObjectFishDef def : EntityHandler.getObjectFishingDef(waterId, click).getFishDefs()) {
            if (fishingLevel < def.getReqLevel()) continue;
            fish.add(def);
        }
        if (fish.size() <= 0) {
            return null;
        }
        ObjectFishDef thisFish = (ObjectFishDef)fish.get(DataConversions.random(0, fish.size() - 1));
        int levelDiff = fishingLevel - thisFish.getReqLevel();
        if (levelDiff < 0) {
            return null;
        }
        return DataConversions.percentChance(Formulae.offsetToPercent(levelDiff)) ? thisFish : null;
    }

    public static boolean getLog(ObjectWoodcuttingDef def, int woodcutLevel, int axeId) {
        int levelDiff = woodcutLevel - def.getReqLevel();
        if (levelDiff < 0) {
            return false;
        }
        switch (axeId) {
            case 87: {
                levelDiff += 0;
                break;
            }
            case 12: {
                levelDiff += 2;
                break;
            }
            case 428: {
                levelDiff += 4;
                break;
            }
            case 88: {
                levelDiff += 6;
                break;
            }
            case 203: {
                levelDiff += 8;
                break;
            }
            case 204: {
                levelDiff += 10;
                break;
            }
            case 405: {
                levelDiff += 12;
                break;
            }
        }
        if (def.getReqLevel() == 1 && levelDiff >= 40) {
            return true;
        }
        return DataConversions.percentChance(Formulae.offsetToPercent(levelDiff));
    }

    public static boolean getOre(ObjectMiningDef def, int miningLevel, int axeId) {
        return Formulae.getOre(def.getReqLevel(), miningLevel, axeId);
    }

    /*
     * The plain grey rocks scattered around the world -- Jagex's "Rock
     * (uninteresting)". They have no ore and so no ObjectMiningDef, but they
     * are still swung at and still succeed or fail, so they need the same
     * roll a level-1 rock gets.
     *
     * The list is every object in Jagex's own cache named Rock/Rocks whose
     * command is Mine and whose model is rocks1 or rocks2, less two. 98 is
     * the stump this server drops in place of a rock that has just been
     * mined out, so it has to keep meaning "no ore left in here" rather than
     * "uninteresting". 496 is the Tutorial Island rock, which the tutorial
     * claims and scripts itself. Every other member of the family is scenery
     * the map places deliberately.
     */
    private static final int[] plainRockIDs = new int[]{99, 164, 165, 166, 167, 168, 169, 170, 171, 172, 197, 212, 515, 516, 517, 518, 519, 520, 521, 522, 523, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551, 552};

    public static boolean isPlainRock(int objectId) {
        for (int id : plainRockIDs) {
            if (id == objectId) {
                return true;
            }
        }
        return false;
    }

    public static boolean getOre(int reqLevel, int miningLevel, int axeId) {
        int levelDiff = miningLevel - reqLevel;
        if (levelDiff < 0) {
            return false;
        }
        int bonus = 0;
        switch (axeId) {
            case 156: {
                bonus = 0;
                break;
            }
            case 1258: {
                bonus = 2;
                break;
            }
            case 1259: {
                bonus = 6;
                break;
            }
            case 1260: {
                bonus = 8;
                break;
            }
            case 1261: {
                bonus = 10;
                break;
            }
            case 1262: {
                bonus = 12;
            }
        }
        return DataConversions.percentChance(Formulae.offsetToPercent(levelDiff + bonus));
    }

    public static int getHeight(int y) {
        return y / 944;
    }

    public static int getHeight(Point location) {
        return Formulae.getHeight(location.getY());
    }

    public static int maxHit(int strength, int weaponPower, boolean burst, boolean superhuman, boolean ultimate, int bonus) {
        double newStrength = (double)strength * Formulae.addPrayers(burst, superhuman, ultimate) + (double)bonus;
        return (int)(newStrength * ((double)weaponPower * 0.00175 + 0.1) + 1.05);
    }

    /** Takes stored experience, which is in quarter-units; the curve is displayed units. */
    public static int experienceToLevel(int exp) {
        int displayed = exp / 4;
        for (int level = 0; level < 98; ++level) {
            if (displayed >= experienceArray[level]) continue;
            return level + 1;
        }
        return 99;
    }

    public static int lvlToXp(int level) {
        return eArray[level];
    }

    public static int getCombatlevel(int[] stats) {
        return Formulae.getCombatLevel(stats[0], stats[1], stats[2], stats[3], stats[6], stats[5], stats[4]);
    }

    public static int getCombatLevel(int att, int def, int str, int hits, int magic, int pray, int range) {
        double attack = att + str;
        double defense = def + hits;
        double mage = pray + magic;
        mage /= 8.0;
        if (attack < (double)range * 1.5) {
            return (int)(defense / 4.0 + (double)range * 0.375 + mage);
        }
        return (int)(attack / 4.0 + defense / 4.0 + mage);
    }
}

