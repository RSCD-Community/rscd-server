/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.model;

import org.rscdaemon.server.util.sql.Rows;
import org.rscdaemon.server.util.sql.MysqlException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.model.BankItem;
import org.rscdaemon.ls.model.InvItem;
import org.rscdaemon.ls.util.DataConversions;

public class PlayerSave {
    public static final String[] statArray = new String[]{"attack", "defense", "strength", "hits", "ranged", "prayer", "magic", "cooking", "woodcut", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining", "herblaw", "agility", "thieving", "runecrafting"};
    private long user;
    private int owner;
    private int group;
    private long subExpires;
    private long[] exp = new long[19];
    private int[] lvl = new int[19];
    private ArrayList<InvItem> invItems = new ArrayList();
    private ArrayList<BankItem> bankItems = new ArrayList();
    private long lastUpdate = 0L;
    private ArrayList<Long> friendList = new ArrayList();
    private ArrayList<Long> ignoreList = new ArrayList();
    private byte hairColour;
    private byte topColour;
    private byte trouserColour;
    private byte skinColour;
    private byte headSprite;
    private byte bodySprite;
    private boolean male;
    private long skulled;
    private int x;
    private int y;
    private int fatigue;
    private byte combatStyle;
    private int combat;
    private int skillTotal;
    private long loginDate;
    private long loginIP;
    private boolean cameraAuto;
    private boolean oneMouse;
    private boolean soundOff;
    private boolean showRoof;
    private boolean autoScreenshot;
    private boolean combatWindow;
    private boolean blockChat;
    private boolean blockPrivate;
    private boolean blockTrade;
    private boolean blockDuel;
    /**
     * Quest id -> stage reached. Sorted so the login packet and the save packet
     * always carry the same quests in the same order; the game server reads them
     * back positionally and an unordered map would make that ordering depend on
     * hash layout.
     */
    private TreeMap<Integer, Integer> questStages = new TreeMap<Integer, Integer>();
    /**
     * Dragonstone amulet teleports left, 1..4. See Player.amuletCharges for why
     * this is a property of the account and not of the amulet.
     */
    private int amuletCharges = 4;
    /**
     * Poison carried across a logout. Strength is the damage of the next hit
     * and hits is how many are left at that strength; both zero means clean.
     * Stored because "poison can be stalled by logging out" only means anything
     * if it is still there on the way back in.
     */
    private int poisonStrength = 0;
    private int poisonHits = 0;

    public static PlayerSave loadPlayer(long user) {
        PlayerSave save = new PlayerSave(user);
        try {
            int i;
            // group_id and sub_expires are denormalized onto the player row in
            // this schema — the legacy site's `users` table does not exist.
            Rows result = Server.db.getQuery("SELECT * FROM `rscd_players` WHERE `user`=" + save.getUser());
            if (!result.next()) {
                return save;
            }
            save.setOwner(result.getInt("owner"), result.getInt("group_id"), result.getLong("sub_expires"));
            save.setLogin(result.getLong("login_date"), DataConversions.IPToLong(result.getString("login_ip")));
            save.setLocation(result.getInt("x"), result.getInt("y"));
            save.setFatigue(result.getInt("fatigue"));
            save.setCombatStyle((byte)result.getInt("combatstyle"));
            save.setPrivacy(result.getInt("block_chat") == 1, result.getInt("block_private") == 1, result.getInt("block_trade") == 1, result.getInt("block_duel") == 1);
            save.setSettings(result.getInt("cameraauto") == 1, result.getInt("onemouse") == 1, result.getInt("soundoff") == 1, result.getInt("showroof") == 1, result.getInt("autoscreenshot") == 1, result.getInt("combatwindow") == 1);
            save.setAmuletCharges(result.getInt("amulet_charges"));
            save.setPoison(result.getInt("poison_strength"), result.getInt("poison_hits"));
            save.setAppearance((byte)result.getInt("haircolour"), (byte)result.getInt("topcolour"), (byte)result.getInt("trousercolour"), (byte)result.getInt("skincolour"), (byte)result.getInt("headsprite"), (byte)result.getInt("bodysprite"), result.getInt("male") == 1, result.getInt("skulled"));
            result = Server.db.getQuery("SELECT * FROM `rscd_experience` WHERE `user`=" + save.getUser());
            if (!result.next()) {
                return save;
            }
            for (i = 0; i < 19; ++i) {
                save.setExp(i, result.getInt("exp_" + statArray[i]));
            }
            result = Server.db.getQuery("SELECT * FROM `rscd_curstats` WHERE `user`=" + save.getUser());
            if (!result.next()) {
                return save;
            }
            for (i = 0; i < 19; ++i) {
                save.setLvl(i, result.getInt("cur_" + statArray[i]));
            }
            result = Server.db.getQuery("SELECT * FROM `rscd_invitems` WHERE `user`=" + save.getUser() + " ORDER BY `slot` ASC");
            while (result.next()) {
                save.addInvItem(result.getInt("id"), result.getInt("amount"), result.getInt("wielded") == 1);
            }
            result = Server.db.getQuery("SELECT * FROM `rscd_bank` WHERE `owner`='" + save.getOwner() + "' ORDER BY `slot` ASC");
            while (result.next()) {
                save.addBankItem(result.getInt("id"), result.getInt("amount"));
            }
            result = Server.db.getQuery("SELECT * FROM `rscd_friends` WHERE `user`=" + save.getUser());
            while (result.next()) {
                save.addFriend(result.getLong("friend"));
            }
            result = Server.db.getQuery("SELECT * FROM `rscd_ignores` WHERE `user`=" + save.getUser());
            while (result.next()) {
                save.addIgnore(result.getLong("ignore"));
            }
            result = Server.db.getQuery("SELECT `quest`, `stage` FROM `rscd_quests` WHERE `user`=" + save.getUser());
            while (result.next()) {
                save.setQuestStage(result.getInt("quest"), result.getInt("stage"));
            }
        }
        catch (MysqlException e) {
            Server.error("SQL Exception Loading " + DataConversions.hashToUsername(user) + ": " + e.getMessage());
        }
        return save;
    }

    private PlayerSave(long user) {
        this.user = user;
    }

    public void setOwner(int owner) {
        this.owner = owner;
    }

    public void setOwner(int owner, int group, long subExpires) {
        this.owner = owner;
        this.group = group;
        this.subExpires = subExpires;
    }

    public long getUser() {
        return this.user;
    }

    public String getUsername() {
        return DataConversions.hashToUsername(this.user);
    }

    public int getOwner() {
        return this.owner;
    }

    public int getGroup() {
        return this.group;
    }

    public long getSubscriptionExpires() {
        return this.subExpires;
    }

    public long getLastIP() {
        return this.loginIP;
    }

    public long getLastLogin() {
        return this.loginDate;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getFatigue() {
        return this.fatigue;
    }

    public byte getCombatStyle() {
        return this.combatStyle;
    }

    public boolean blockChat() {
        return this.blockChat;
    }

    public boolean blockPrivate() {
        return this.blockPrivate;
    }

    public boolean blockTrade() {
        return this.blockTrade;
    }

    public boolean blockDuel() {
        return this.blockDuel;
    }

    public boolean cameraAuto() {
        return this.cameraAuto;
    }

    public boolean oneMouse() {
        return this.oneMouse;
    }

    public boolean soundOff() {
        return this.soundOff;
    }

    public boolean showRoof() {
        return this.showRoof;
    }

    public boolean autoScreenshot() {
        return this.autoScreenshot;
    }

    public boolean combatWindow() {
        return this.combatWindow;
    }

    public int getHairColour() {
        return this.hairColour;
    }

    public int getTopColour() {
        return this.topColour;
    }

    public int getTrouserColour() {
        return this.trouserColour;
    }

    public int getSkinColour() {
        return this.skinColour;
    }

    public int getHeadSprite() {
        return this.headSprite;
    }

    public int getBodySprite() {
        return this.bodySprite;
    }

    public boolean isMale() {
        return this.male;
    }

    public long getSkullTime() {
        return this.skulled;
    }

    public long getExp(int i) {
        return this.exp[i];
    }

    public int getStat(int i) {
        return this.lvl[i];
    }

    public int getInvCount() {
        return this.invItems.size();
    }

    public InvItem getInvItem(int i) {
        return this.invItems.get(i);
    }

    public int getBankCount() {
        return this.bankItems.size();
    }

    public BankItem getBankItem(int i) {
        return this.bankItems.get(i);
    }

    public int getFriendCount() {
        return this.friendList.size();
    }

    public long getFriend(int i) {
        return this.friendList.get(i);
    }

    public void addFriend(long friend) {
        this.friendList.add(friend);
    }

    public void removeFriend(long friend) {
        this.friendList.remove(friend);
    }

    public int getIgnoreCount() {
        return this.ignoreList.size();
    }

    public long getIgnore(int i) {
        return this.ignoreList.get(i);
    }

    public void addIgnore(long friend) {
        this.ignoreList.add(friend);
    }

    public void removeIgnore(long friend) {
        this.ignoreList.remove(friend);
    }

    public void setPrivacy(boolean blockChat, boolean blockPrivate, boolean blockTrade, boolean blockDuel) {
        this.blockChat = blockChat;
        this.blockPrivate = blockPrivate;
        this.blockTrade = blockTrade;
        this.blockDuel = blockDuel;
    }

    public void setPrivacySetting(int idx, boolean on) {
        switch (idx) {
            case 0: {
                this.blockChat = on;
                break;
            }
            case 1: {
                this.blockPrivate = on;
                break;
            }
            case 2: {
                this.blockTrade = on;
                break;
            }
            case 3: {
                this.blockDuel = on;
            }
        }
    }

    public void setSettings(boolean cameraAuto, boolean oneMouse, boolean soundOff, boolean showRoof, boolean autoScreenshot, boolean combatWindow) {
        this.cameraAuto = cameraAuto;
        this.oneMouse = oneMouse;
        this.soundOff = soundOff;
        this.showRoof = showRoof;
        this.autoScreenshot = autoScreenshot;
        this.combatWindow = combatWindow;
    }

    public void setGameSetting(int idx, boolean on) {
        switch (idx) {
            case 0: {
                this.cameraAuto = on;
                break;
            }
            case 2: {
                this.oneMouse = on;
                break;
            }
            case 3: {
                this.soundOff = on;
                break;
            }
            case 4: {
                this.showRoof = on;
                break;
            }
            case 5: {
                this.autoScreenshot = on;
                break;
            }
            case 6: {
                this.combatWindow = on;
            }
        }
    }

    public void setLogin(long loginDate, long loginIP) {
        this.loginDate = loginDate;
        this.loginIP = loginIP;
    }

    public void setTotals(int combat, int skillTotal) {
        this.combat = combat;
        this.skillTotal = skillTotal;
    }

    public void setCombatStyle(byte combatStyle) {
        this.combatStyle = combatStyle;
    }

    public void setFatigue(int fatigue) {
        this.fatigue = fatigue;
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setAppearance(byte hairColour, byte topColour, byte trouserColour, byte skinColour, byte headSprite, byte bodySprite, boolean male, long skulled) {
        this.hairColour = hairColour;
        this.topColour = topColour;
        this.trouserColour = trouserColour;
        this.skinColour = skinColour;
        this.headSprite = headSprite;
        this.bodySprite = bodySprite;
        this.male = male;
        this.skulled = skulled;
    }

    public void setExp(int stat, long exp) {
        this.exp[stat] = exp;
    }

    public void setLvl(int stat, int lvl) {
        this.lvl[stat] = lvl;
    }

    public void setStat(int stat, long exp, int lvl) {
        this.exp[stat] = exp;
        this.lvl[stat] = lvl;
    }

    public void clearInvItems() {
        this.invItems.clear();
    }

    public void addInvItem(int id, int amount, boolean wielded) {
        this.invItems.add(new InvItem(id, amount, wielded));
    }

    public void clearBankItems() {
        this.bankItems.clear();
    }

    public void addBankItem(int id, int amount) {
        this.bankItems.add(new BankItem(id, amount));
    }

    /**
     * Record a quest's progress. Stage -1 means "not started", which is the
     * default for every quest the player has never touched, so it is not stored.
     */
    public void setQuestStage(int quest, int stage) {
        if (stage < 0) {
            this.questStages.remove(quest);
        } else {
            this.questStages.put(quest, stage);
        }
    }

    public Map<Integer, Integer> getQuestStages() {
        return this.questStages;
    }

    public int getAmuletCharges() {
        return this.amuletCharges;
    }

    /**
     * Anything outside 1..4 becomes a full four. That covers the zero a row
     * carries before the migration has been run, and it costs nothing: zero is
     * not a state the counter can rest in, so a stored zero can only ever mean
     * "this column has not been written yet".
     */
    public void setAmuletCharges(int charges) {
        this.amuletCharges = charges >= 1 && charges <= 4 ? charges : 4;
    }

    public int getPoisonStrength() {
        return this.poisonStrength;
    }

    public int getPoisonHits() {
        return this.poisonHits;
    }

    /**
     * Either half being zero or negative clears both, since a strength with no
     * hits left and hits with no strength are the same thing as not poisoned.
     */
    public void setPoison(int strength, int hits) {
        if (strength <= 0 || hits <= 0) {
            this.poisonStrength = 0;
            this.poisonHits = 0;
            return;
        }
        this.poisonStrength = strength > 6 ? 6 : strength;
        this.poisonHits = hits > 30 ? 30 : hits;
    }

    public void clearQuests() {
        this.questStages.clear();
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public long getLastUpdate() {
        return this.lastUpdate;
    }

    public boolean save() {
        try {
            int i;
            String query;
            Server.db.updateQuery("DELETE FROM `rscd_bank` WHERE `owner`='" + this.owner + "'");
            if (this.bankItems.size() > 0) {
                query = "INSERT INTO `rscd_bank`(`owner`, `id`, `amount`, `slot`) VALUES";
                int slot = 0;
                for (BankItem item : this.bankItems) {
                    query = query + "('" + this.owner + "', '" + item.getID() + "', '" + item.getAmount() + "', '" + slot++ + "'),";
                }
                Server.db.updateQuery(query.substring(0, query.length() - 1));
            }
            Server.db.updateQuery("DELETE FROM `rscd_invitems` WHERE `user`=" + this.user);
            Rows result = Server.db.getQuery("Select 1 FROM `rscd_players` WHERE `user`=" + this.user + " AND `owner`=" + this.owner);
            if (!result.next()) {
                return false;
            }
            Server.db.updateQuery("UPDATE `rscd_players` SET `combat`=" + this.combat + ", skill_total=" + this.skillTotal + ", `x`=" + this.x + ", `y`='" + this.y + "', `fatigue`='" + this.fatigue + "', `haircolour`=" + this.hairColour + ", `topcolour`=" + this.topColour + ", `trousercolour`=" + this.trouserColour + ", `skincolour`=" + this.skinColour + ", `headsprite`=" + this.headSprite + ", `bodysprite`=" + this.bodySprite + ", `male`=" + (this.male ? 1 : 0) + ", `skulled`=" + this.skulled + ", `combatstyle`=" + this.combatStyle + ", `amulet_charges`=" + this.amuletCharges + ", `poison_strength`=" + this.poisonStrength + ", `poison_hits`=" + this.poisonHits + " WHERE `user`=" + this.user);
            query = "UPDATE `rscd_experience` SET ";
            for (i = 0; i < 19; ++i) {
                /* The stamp records when the current experience figure was
                   attained -- the hiscores break ties first-come with it. It
                   must be assigned before the experience column in the same
                   statement: MySQL applies SET left to right, so the IF still
                   compares against the value being replaced. */
                query = query + "`stamp_" + statArray[i] + "`=IF(`exp_" + statArray[i] + "`<>" + this.exp[i] + ", UNIX_TIMESTAMP(), `stamp_" + statArray[i] + "`),";
                query = query + "`exp_" + statArray[i] + "`=" + this.exp[i] + ",";
            }
            Server.db.updateQuery(query.substring(0, query.length() - 1) + " WHERE `user`=" + this.user);
            query = "UPDATE `rscd_curstats` SET ";
            for (i = 0; i < 19; ++i) {
                query = query + "`cur_" + statArray[i] + "`=" + this.lvl[i] + ",";
            }
            Server.db.updateQuery(query.substring(0, query.length() - 1) + " WHERE `user`=" + this.user);
            if (this.invItems.size() > 0) {
                query = "INSERT INTO `rscd_invitems`(`user`, `id`, `amount`, `wielded`, `slot`) VALUES";
                int slot = 0;
                for (InvItem item : this.invItems) {
                    query = query + "('" + this.user + "', '" + item.getID() + "', '" + item.getAmount() + "', '" + (item.isWielded() ? 1 : 0) + "', '" + slot++ + "'),";
                }
                Server.db.updateQuery(query.substring(0, query.length() - 1));
            }
            // Rewritten wholesale rather than upserted: a quest that dropped out
            // of the map has been reset, and leaving its old row behind would
            // resurrect the progress on the next login.
            Server.db.updateQuery("DELETE FROM `rscd_quests` WHERE `user`=" + this.user);
            if (this.questStages.size() > 0) {
                query = "INSERT INTO `rscd_quests`(`user`, `quest`, `stage`) VALUES";
                for (Map.Entry<Integer, Integer> e : this.questStages.entrySet()) {
                    query = query + "('" + this.user + "', '" + e.getKey() + "', '" + e.getValue() + "'),";
                }
                Server.db.updateQuery(query.substring(0, query.length() - 1));
            }
            return true;
        }
        catch (MysqlException e) {
            Server.error(e);
            return false;
        }
    }
}

