/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.PrayerDef;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.MiniEvent;
import org.rscdaemon.server.event.RangeEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.Bank;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Path;
import org.rscdaemon.server.model.PlayerAppearance;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packetbuilder.client.MiscPacketBuilder;
import org.rscdaemon.server.packetbuilder.loginserver.SavePacketBuilder;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.StatefulEntityCollection;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public final class Player
extends Mob {
    private ShortEvent sEvent = null;
    private QuestManager questManager;
    /**
     * Quest id -> stage reached, as last loaded from or written back to the
     * login server. This is the player's authoritative copy: the Quest objects
     * themselves are rebuilt on every login and cannot outlive the session.
     *
     * Sorted, because it is serialised into the save packet and read back
     * positionally.
     */
    private final java.util.TreeMap<Integer, Integer> questStages = new java.util.TreeMap<Integer, Integer>();
    private String username;
    private long usernameHash;
    /* MD5 of the password, never the plaintext. Hashed the moment the login
       packet is unpacked -- see load() -- so nothing that runs after
       authentication (commands, logs, heap dumps) can ever hold the real
       password. The login server compares this same hash against the DB. */
    private String passwordHash;
    private int groupID = 4;
    private boolean loggedIn = false;
    private Connection ioSession;
    private long lastPing = System.currentTimeMillis();
    /* When this player was last written to the database. Seeded at construction
       rather than 0 so that somebody who has just logged in is not saved again
       on the next sweep with nothing changed since load. */
    private long lastSaved = System.currentTimeMillis();
    private PlayerAppearance appearance;
    private int[] wornItems = new int[12];
    private int[] curStat = new int[19];
    private int[] maxStat = new int[19];
    private int[] exp = new int[19];
    /**
     * Teleports left before a charged dragonstone amulet reverts.
     *
     * This lives on the player and not on the amulet because there is nowhere
     * on an amulet to put it. Jagex's item table has exactly two dragonstone
     * amulet states -- 597 charged and 522 not -- with no (4)/(3)/(2)/(1)
     * variants of the kind RS2 later used, and an InvItem carries only id,
     * amount and wielded. So the count cannot travel with the item, and it
     * does not travel in a trade either: hand somebody a 597 and they get
     * however many teleports THEIR counter has left.
     *
     * Four is both the starting value and the value it returns to, and it is
     * never topped up by anything -- see spendAmuletCharge(). Dipping at the
     * Fountain of heros converts 522 into 597 and deliberately does not touch
     * this number.
     */
    private int amuletCharges = 4;

    /*
     * Poison. See Poison.java for the mechanic; these are the two numbers it
     * needs to remember about a player -- the strength of the current band and
     * how many of that band's five hits have landed. Strength 0 is clean.
     *
     * Both are saved, because vanilla poison survived a logout: the wiki notes
     * the damage "can be stalled by logging out before the 20th second
     * passes", which only means anything if it is still there on the way back
     * in. Losing it at the door would make logging out a free cure.
     *
     * The immunity a potion buys is deliberately NOT saved. It is a few
     * minutes long, it is a benefit rather than a penalty, and dropping it at
     * the door costs the player nothing they can be robbed of.
     */
    private int poisonStrength = 0;
    private int poisonHits = 0;
    private long poisonImmuneUntil = 0L;

    /*
     * The gnome dish being assembled -- see GnomeCooking. Jagex gave gnome
     * cooking no intermediate item ids, so a half-built batta is item 884
     * whether it has cheese on it or not and the progress has to be held here
     * rather than on the item.
     *
     * One slot, because Gianne's rule is one unfinished dish at a time: "you
     * need to finish, eat or drop the unfinished dish you hold / before you
     * can make another - giannes rules".
     *
     * Deliberately not saved. It is a few minutes of work at most, the
     * half-made items themselves survive a logout, and a stale recipe reloaded
     * against an inventory that has since changed would be worse than starting
     * the dish again.
     */
    private java.util.ArrayList<Integer> gnomeDish = null;

    private boolean suspicious = false;
    private HashMap<Integer, Integer> knownPlayersAppearanceIDs = new HashMap();
    private StatefulEntityCollection<Player> watchedPlayers = new StatefulEntityCollection();
    private StatefulEntityCollection<GameObject> watchedObjects = new StatefulEntityCollection();
    private StatefulEntityCollection<Item> watchedItems = new StatefulEntityCollection();
    private StatefulEntityCollection<Npc> watchedNpcs = new StatefulEntityCollection();
    private Inventory inventory;
    private Bank bank;
    private boolean[] privacySettings = new boolean[4];
    private boolean[] gameSettings = new boolean[7];
    private MiscPacketBuilder actionSender;
    private long lastLogin = 0L;
    private long currentLogin = 0L;
    private String lastIP = "0.0.0.0";
    private String currentIP = "0.0.0.0";
    private boolean reconnecting = false;
    private boolean changingAppearance = false;
    private boolean maleGender;
    private Player wishToTrade = null;
    private Player wishToDuel = null;
    private boolean isTrading = false;
    private boolean isDueling = false;
    private ArrayList<InvItem> tradeOffer = new ArrayList();
    private ArrayList<InvItem> duelOffer = new ArrayList();
    private boolean tradeOfferAccepted = false;
    private boolean duelOfferAccepted = false;
    private boolean tradeConfirmAccepted = false;
    private boolean duelConfirmAccepted = false;
    private TreeMap<Long, Integer> friendList = new TreeMap();
    private ArrayList<Long> ignoreList = new ArrayList();
    private ArrayList<Projectile> projectilesNeedingDisplayed = new ArrayList();
    /** Shots fired by npcs, which ride the npc update block instead. */
    private ArrayList<Projectile> npcProjectilesNeedingDisplayed = new ArrayList();
    private ArrayList<Player> playersNeedingHitsUpdate = new ArrayList();
    private ArrayList<Npc> npcsNeedingHitsUpdate = new ArrayList();
    private ArrayList<ChatMessage> chatMessagesNeedingDisplayed = new ArrayList();
    private ArrayList<ChatMessage> npcMessagesNeedingDisplayed = new ArrayList();
    private ArrayList<Bubble> bubblesNeedingDisplayed = new ArrayList();
    private long lastSpellCast = 0L;
    private HashMap<Long, Long> attackedBy = new HashMap();
    /**
     * Named flags for world mechanics that need to remember one small thing
     * between two clicks and nothing beyond that.
     *
     * The windmill is the first of them: grain goes in the hopper, and then the
     * hopper is operated, and between those the mill has to know it is loaded.
     * Quests have their stage for this sort of thing and everything else had
     * nowhere at all, which is why the mill was never built.
     *
     * Deliberately not saved. A flag here survives until logout and no longer;
     * anything that ought to outlive a session belongs on a quest, or in the
     * player's own row.
     */
    private HashMap<String, Integer> flags = new HashMap<String, Integer>();
    private long lastReport = 0L;
    private long lastCharge = System.currentTimeMillis() - 600000L;
    private long lastBlackout = System.currentTimeMillis() - 15000L;
    private long lastSay = 0L;
    private int combatStyle = 0;
    private boolean destroy = false;
    private int[] sessionKeys = new int[4];
    private boolean inBank = false;
    private MenuHandler menuHandler = null;
    private DelayedEvent drainer;
    private int drainRate = 0;
    private DelayedEvent skullEvent = null;
    private int fatigue = 0;
    private boolean initialized = false;
    private Shop shop = null;
    private Npc interactingNpc = null;
    private int owner = 1;
    private LinkedList<RSCPacket> lastPackets = new LinkedList();
    private String subscriptionExpires = "9/21/2009";
    private Mob following;
    private DelayedEvent followEvent;
    private RangeEvent rangeEvent;
    private long lastArrow = 0L;
    private long lastCount = 0L;
    private int packetCount = 0;
    private LinkedList<ChatMessage> chatQueue = new LinkedList();
    private long lastTradeDuelRequest = 0L;
    private String className = "NOT_SET";
    private Action status = Action.IDLE;
    private boolean[] duelOptions = new boolean[4];
    private boolean requiresOfferUpdate = false;

    public void setSEvent(ShortEvent sEvent) {
        this.sEvent = sEvent;
        world.getDelayedEventHandler().add(sEvent);
    }

    public QuestManager getQuestManager() {
        return this.questManager;
    }

    /** @param stage -1 for "not started", which is not persisted. */
    public void setQuestStage(int quest, int stage) {
        if (stage < 0) {
            this.questStages.remove(quest);
        } else {
            this.questStages.put(quest, stage);
        }
    }

    /** @return the stage reached, or -1 if this player has never started it. */
    public int getQuestStage(int quest) {
        Integer stage = this.questStages.get(quest);
        return stage == null ? -1 : stage.intValue();
    }

    public java.util.Map<Integer, Integer> getQuestStages() {
        return this.questStages;
    }

    /**
     * Which quests this player has finished, in the client's quest-tab order.
     *
     * Completion is not a stored flag: a quest is finished when its stage has
     * reached the final stage its own class declares, so the QuestManager is
     * asked rather than the stage map. Quests the server has no class for stay
     * false, which is what the client draws in red.
     */
    public boolean[] getQuestCompletion() {
        boolean[] done = new boolean[Quests.count()];
        if (this.questManager != null) {
            this.questManager.fillCompletion(done);
        }
        return done;
    }

    /** As getQuestCompletion, but 0/1/2 (not started/started/complete) per quest. */
    public byte[] getQuestProgress() {
        byte[] progress = new byte[Quests.count()];
        if (this.questManager != null) {
            this.questManager.fillProgress(progress);
        }
        return progress;
    }

    /** Sum of the points for every completed quest. */
    public int getQuestPoints() {
        int points = 0;
        boolean[] done = this.getQuestCompletion();
        for (int i = 0; i < done.length; ++i) {
            if (done[i]) {
                points += Quests.points(i);
            }
        }
        return points;
    }

    public void setRequiresOfferUpdate(boolean b) {
        this.requiresOfferUpdate = b;
    }

    public boolean requiresOfferUpdate() {
        return this.requiresOfferUpdate;
    }

    public void setStatus(Action a) {
        this.status = a;
    }

    public Action getStatus() {
        return this.status;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    public boolean tradeDuelThrottling() {
        long now = System.currentTimeMillis();
        if (now - this.lastTradeDuelRequest > 1000L) {
            this.lastTradeDuelRequest = now;
            return false;
        }
        return true;
    }

    public void addMessageToChatQueue(byte[] messageData) {
        this.chatQueue.add(new ChatMessage(this, messageData));
        if (this.chatQueue.size() > 2) {
            this.destroy(false);
        }
    }

    public ChatMessage getNextChatMessage() {
        return this.chatQueue.poll();
    }

    public void setArrowFired() {
        this.lastArrow = System.currentTimeMillis();
    }

    public void setRangeEvent(RangeEvent event) {
        if (this.isRanging()) {
            this.resetRange();
        }
        this.rangeEvent = event;
        this.rangeEvent.setLastRun(this.lastArrow);
        world.getDelayedEventHandler().add(this.rangeEvent);
    }

    public boolean isRanging() {
        return this.rangeEvent != null;
    }

    public void resetRange() {
        if (this.rangeEvent != null) {
            this.rangeEvent.stop();
            this.rangeEvent = null;
        }
        this.setStatus(Action.IDLE);
    }

    public boolean canLogout() {
        return !this.isBusy() && System.currentTimeMillis() - this.getCombatTimer() > 10000L;
    }

    public boolean isFollowing() {
        return this.followEvent != null && this.following != null;
    }

    public boolean isFollowing(Mob mob) {
        return this.isFollowing() && mob.equals(this.following);
    }

    public void setFollowing(Mob mob) {
        this.setFollowing(mob, 0);
    }

    public void setFollowing(final Mob mob, final int radius) {
        if (this.isFollowing()) {
            this.resetFollowing();
        }
        this.following = mob;
        this.followEvent = new DelayedEvent(this, 500){

            public void run() {
                if (!this.owner.withinRange(mob) || mob.isRemoved() || this.owner.isBusy() && !this.owner.isDueling()) {
                    Player.this.resetFollowing();
                } else if (!this.owner.finishedPath() && this.owner.withinRange(mob, radius)) {
                    this.owner.resetPath();
                } else if (this.owner.finishedPath() && !this.owner.withinRange(mob, radius + 1)) {
                    this.owner.setPath(new Path(this.owner.getX(), this.owner.getY(), mob.getX(), mob.getY()));
                }
            }
        };
        world.getDelayedEventHandler().add(this.followEvent);
    }

    public void resetFollowing() {
        this.following = null;
        if (this.followEvent != null) {
            this.followEvent.stop();
            this.followEvent = null;
        }
        this.resetPath();
    }

    public void setSkulledOn(Player player) {
        player.addAttackedBy(this);
        if (System.currentTimeMillis() - this.lastAttackedBy(player) > 1200000L) {
            this.addSkull(1200000L);
        }
    }

    public void addPacket(RSCPacket p) {
        long now = System.currentTimeMillis();
        if (now - this.lastCount > 3000L) {
            this.lastCount = now;
            this.packetCount = 0;
        }
        if (!DataConversions.inArray(Formulae.safePacketIDs, p.getID()) && this.packetCount++ >= 60) {
            this.destroy(false);
        }
        if (this.lastPackets.size() >= 60) {
            this.lastPackets.remove();
        }
        this.lastPackets.addLast(p);
    }

    public List<RSCPacket> getPackets() {
        return this.lastPackets;
    }

    public boolean isSuspicious() {
        return this.suspicious;
    }

    public void setOwner(int owner) {
        this.owner = owner;
    }

    public int getOwner() {
        return this.owner;
    }

    public Npc getNpc() {
        return this.interactingNpc;
    }

    public void setNpc(Npc npc) {
        this.interactingNpc = npc;
    }

    @Override
    public void remove() {
        this.removed = true;
    }

    public boolean initialized() {
        return this.initialized;
    }

    public void setInitialized() {
        this.initialized = true;
    }

    public int getDrainRate() {
        return this.drainRate;
    }

    public void setDrainRate(int rate) {
        this.drainRate = rate;
    }

    public int getRangeEquip() {
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded() || !DataConversions.inArray(Formulae.bowIDs, item.getID()) && !DataConversions.inArray(Formulae.xbowIDs, item.getID())) continue;
            return item.getID();
        }
        return -1;
    }

    public void resetAll() {
        this.resetAllExceptTradeOrDuel();
        this.resetTrade();
        this.resetDuel();
    }

    public void resetTrade() {
        Player opponent = this.getWishToTrade();
        if (opponent != null) {
            opponent.resetTrading();
        }
        this.resetTrading();
    }

    public void resetDuel() {
        Player opponent = this.getWishToDuel();
        if (opponent != null) {
            opponent.resetDueling();
        }
        this.resetDueling();
    }

    public void resetAllExceptTrading() {
        this.resetAllExceptTradeOrDuel();
        this.resetDuel();
    }

    public void resetAllExceptDueling() {
        this.resetAllExceptTradeOrDuel();
        this.resetTrade();
    }

    private void resetAllExceptTradeOrDuel() {
        if (this.getMenuHandler() != null) {
            this.resetMenuHandler();
        }
        if (this.accessingBank()) {
            this.resetBank();
        }
        if (this.accessingShop()) {
            this.resetShop();
        }
        if (this.interactingNpc != null) {
            this.interactingNpc.unblock();
        }
        if (this.isFollowing()) {
            this.resetFollowing();
        }
        if (this.isRanging()) {
            this.resetRange();
        }
        this.setStatus(Action.IDLE);
    }

    public void setMenuHandler(MenuHandler menuHandler) {
        menuHandler.setOwner(this);
        this.menuHandler = menuHandler;
    }

    public void setQuestMenuHandler(MenuHandler menuHandler) {
        this.menuHandler = menuHandler;
        menuHandler.setOwner(this);
        this.actionSender.sendMenu(menuHandler.getOptions());
    }

    public void resetMenuHandler() {
        this.menuHandler = null;
        this.actionSender.hideMenu();
    }

    public MenuHandler getMenuHandler() {
        return this.menuHandler;
    }

    public boolean accessingShop() {
        return this.shop != null;
    }

    public void setAccessingShop(Shop shop) {
        this.shop = shop;
        if (shop != null) {
            shop.addPlayer(this);
        }
    }

    public void resetShop() {
        if (this.shop != null) {
            this.shop.removePlayer(this);
            this.shop = null;
            this.actionSender.hideShop();
        }
    }

    public boolean accessingBank() {
        return this.inBank;
    }

    public Shop getShop() {
        return this.shop;
    }

    public void setAccessingBank(boolean b) {
        this.inBank = b;
    }

    public void resetBank() {
        this.setAccessingBank(false);
        this.actionSender.hideBank();
    }

    public Player(Connection ios) {
        this.ioSession = ios;
        this.currentIP = ((InetSocketAddress)ios.getRemoteAddress()).getAddress().getHostAddress();
        this.currentLogin = System.currentTimeMillis();
        this.actionSender = new MiscPacketBuilder(this);
        this.setBusy(true);
        this.questManager = new QuestManager(this);
    }

    public void setServerKey(long key) {
        this.sessionKeys[2] = (int)(key >> 32);
        this.sessionKeys[3] = (int)key;
    }

    /** Diagnostic only -- see PlayerLogin's rscd.sessiondebug trace. */
    public int[] getSessionKeys() {
        return this.sessionKeys;
    }

    public boolean setSessionKeys(int[] keys) {
        boolean valid = this.sessionKeys[2] == keys[2] && this.sessionKeys[3] == keys[3];
        this.sessionKeys = keys;
        return valid;
    }

    public boolean destroyed() {
        return this.destroy;
    }

    public void destroy(boolean force) {
        if (this.destroy) {
            return;
        }
        if (force || this.canLogout()) {
            this.destroy = true;
            this.actionSender.sendLogout();
        } else {
            final long startDestroy = System.currentTimeMillis();
            world.getDelayedEventHandler().add(new DelayedEvent(this, 3000){

                public void run() {
                    if (this.owner.canLogout() || (!this.owner.inCombat() || !this.owner.isDueling()) && System.currentTimeMillis() - startDestroy > 60000L) {
                        this.owner.destroy(true);
                        this.running = false;
                    }
                }
            });
        }
    }

    @Override
    public int getCombatStyle() {
        return this.combatStyle;
    }

    public void setCombatStyle(int style) {
        this.combatStyle = style;
    }

    public void load(String username, String password, int uid, boolean reconnecting) {
        this.setID(uid);
        this.passwordHash = DataConversions.md5(password);
        this.reconnecting = reconnecting;
        this.usernameHash = DataConversions.usernameToHash(username);
        this.username = DataConversions.hashToUsername(this.usernameHash);
        world.getServer().getLoginConnector().getActionSender().playerLogin(this);
        world.getDelayedEventHandler().add(new DelayedEvent(this, 60000){

            public void run() {
                for (int statIndex = 0; statIndex < 19; ++statIndex) {
                    int maxStat;
                    if (statIndex == 5) continue;
                    /*
                     * Rapid heal (prayer 7) and Rapid restore (prayer 6) were
                     * defined, drained prayer points, and did nothing -- the
                     * upstream RSCDaemon code never referenced either outside
                     * PrayerDef. Each doubles this timer's rate for its own
                     * stat set: Rapid heal for Hits (3) only, Rapid restore
                     * for every other stat this loop already covers.
                     */
                    /* Rapid renewal (22, ours) is Rapid heal's advanced
                       form: four points a minute to Rapid heal's two. The
                       two are mutually exclusive in PrayerHandler, so the
                       first branch that matches is the only one active. */
                    int step = statIndex == 3
                            ? (Player.this.isPrayerActivated(22) ? 4
                                : Player.this.isPrayerActivated(7) ? 2 : 1)
                            : (Player.this.isPrayerActivated(6) ? 2 : 1);
                    int curStat = Player.this.getCurStat(statIndex);
                    if (curStat > (maxStat = Player.this.getMaxStat(statIndex))) {
                        Player.this.setCurStat(statIndex, Math.max(maxStat, curStat - step));
                        Player.this.getActionSender().sendStat(statIndex);
                        this.checkStat(statIndex);
                        continue;
                    }
                    if (curStat >= maxStat) continue;
                    Player.this.setCurStat(statIndex, Math.min(maxStat, curStat + step));
                    Player.this.getActionSender().sendStat(statIndex);
                    this.checkStat(statIndex);
                }
            }

            private void checkStat(int statIndex) {
                if (statIndex != 3 && this.owner.getCurStat(statIndex) == this.owner.getMaxStat(statIndex)) {
                    this.owner.getActionSender().sendMessage("@pnk@ Your " + Formulae.statArray[statIndex] + " ability has returned to normal.");
                }
            }
        });
        this.drainer = new DelayedEvent(this, Integer.MAX_VALUE){

            public void run() {
                int curPrayer = Player.this.getCurStat(5);
                if (Player.this.getDrainRate() > 0 && curPrayer > 0) {
                    Player.this.incCurStat(5, -1);
                    Player.this.getActionSender().sendStat(5);
                    if (curPrayer <= 1) {
                        for (int prayerID = 0; prayerID < EntityHandler.prayerCount(); ++prayerID) {
                            Player.this.setPrayer(prayerID, false);
                        }
                        Player.this.setDrainRate(0);
                        this.setDelay(Integer.MAX_VALUE);
                        Player.this.getActionSender().sendMessage("@gry@ You have run out of prayer points. Return to a church to recharge");
                        Player.this.getActionSender().sendPrayers();
                    } else {
                        /* Re-read the interval every point rather than only
                           when a prayer is toggled, because the other half of
                           it is what the player is wearing and that can change
                           with no prayer involved -- pulling on monk robes
                           mid-fight has to start paying off. Doing it here
                           costs one wrong interval at worst and needs no hook
                           in the wielding code, which is the failure mode that
                           would be silent. */
                        this.setDelay(Player.this.prayerDrainDelay());
                    }
                }
            }
        };
        world.getDelayedEventHandler().add(this.drainer);
    }

    public void resetTrading() {
        if (this.isTrading()) {
            this.actionSender.sendTradeWindowClose();
            this.setStatus(Action.IDLE);
        }
        this.setWishToTrade(null);
        this.setTrading(false);
        this.setTradeOfferAccepted(false);
        this.setTradeConfirmAccepted(false);
        this.resetTradeOffer();
    }

    public void resetDueling() {
        if (this.isDueling()) {
            this.actionSender.sendDuelWindowClose();
            this.setStatus(Action.IDLE);
        }
        this.setWishToDuel(null);
        this.setDueling(false);
        this.setDuelOfferAccepted(false);
        this.setDuelConfirmAccepted(false);
        this.resetDuelOffer();
        this.clearDuelOptions();
    }

    public void clearDuelOptions() {
        for (int i = 0; i < 4; ++i) {
            this.duelOptions[i] = false;
        }
    }

    public void save() {
        SavePacketBuilder builder = new SavePacketBuilder();
        builder.setPlayer(this);
        LSPacket temp = builder.getPacket();
        if (temp != null) {
            world.getServer().getLoginConnector().getSession().write((Object)temp);
        }
        this.lastSaved = System.currentTimeMillis();
    }

    /**
     * When this player was last handed to the login server to be written out.
     *
     * Stamped even if the packet could not be built, because a save that failed
     * is not a reason to retry it every tick. GameEngine's autosave sweep reads
     * this; see the comment there for why it is per player and not global.
     */
    public long getLastSaved() {
        return this.lastSaved;
    }

    public void setBlackOut() {
        this.lastBlackout = System.currentTimeMillis();
    }

    public boolean isBlackOut() {
        long l = System.currentTimeMillis() - this.lastBlackout;
        return l <= 15000L;
    }

    public void setCharged() {
        this.lastCharge = System.currentTimeMillis();
    }

    public boolean isCharged() {
        long l = System.currentTimeMillis() - this.lastCharge;
        return l <= 600000L;
    }

    public boolean canReport() {
        return System.currentTimeMillis() - this.lastReport > 60000L;
    }

    public void setLastReport() {
        this.lastReport = System.currentTimeMillis();
    }

    public boolean canSay() {
        return System.currentTimeMillis() - this.lastSay > 10000L;
    }

    public void setLastSay() {
        this.lastSay = System.currentTimeMillis();
    }

    /**
     * The quest rewards that a death never takes. Family crest hands out the
     * steel gauntlets and their three enchanted forms, and Dimintheis promises
     * outright that "if you die you will always retain these gauntlets";
     * Klank's gauntlets carry the same promise from Hero's quest. There is no
     * other keep-on-death item in RSC, so this is the whole list rather than a
     * flag on ItemDef.
     *
     * Not consulted for a stake: a duel hands over exactly what both sides
     * offered, and nothing protects an item the player has agreed to lose.
     */
    private static boolean isKeptOnDeath(int itemID) {
        return itemID == 698 || itemID == 699 || itemID == 700 || itemID == 701 || itemID == 1006;
    }

    public void killedBy(Mob mob) {
        this.killedBy(mob, false);
    }

    @Override
    public void killedBy(Mob mob, boolean stake) {
        Player player;
        Mob opponent;
        if (!this.loggedIn) {
            Logger.error(this.username + " not logged in, but killed!");
            return;
        }
        if (mob instanceof Player) {
            Player player2 = (Player)mob;
            player2.getActionSender().sendMessage("@pnk@ You have defeated " + this.getUsername() + "!");
            player2.getActionSender().sendSound("victory");
            world.getDelayedEventHandler().add(new MiniEvent(player2){

                public void action() {
                    this.owner.getActionSender().sendScreenshot();
                }
            });
            world.getServer().getLoginConnector().getActionSender().logKill(player2.getUsernameHash(), this.usernameHash, stake);
        }
        // Must run before the prayer-reset loop below wipes activatedPrayers,
        // or Retribution always reads as off at the only moment it matters.
        PrayerEffects.applyRetribution(this, mob);
        if ((opponent = super.getOpponent()) != null) {
            opponent.resetCombat(CombatState.WON);
        }
        this.actionSender.sendDied();
        /* "Poison can be cured by dying" -- and it buys no immunity on the way
           out, so walking back to the same scorpion poisons you again. */
        Poison.cureOnDeath(this);
        for (int i = 0; i < 19; ++i) {
            this.curStat[i] = this.maxStat[i];
            this.actionSender.sendStat(i);
        }
        Player player3 = player = mob instanceof Player ? (Player)mob : null;
        if (stake) {
            for (InvItem item : this.duelOffer) {
                InvItem affectedItem = this.getInventory().get(item);
                if (affectedItem == null) {
                    this.setSuspiciousPlayer(true);
                    Logger.error("Missing staked item [" + item.getID() + ", " + item.getAmount() + "] from = " + this.usernameHash + "; to = " + player.getUsernameHash() + ";");
                    continue;
                }
                if (affectedItem.isWielded()) {
                    affectedItem.setWield(false);
                    this.updateWornItems(affectedItem.getWieldableDef().getWieldPos(), this.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos()));
                }
                this.getInventory().remove(item);
                world.registerItem(new Item(item.getID(), this.getX(), this.getY(), item.getAmount(), player));
            }
        } else {
            this.inventory.sort();
            ListIterator<InvItem> iterator = this.inventory.iterator();
            if (!this.isSkulled()) {
                for (int i = 0; i < 3 && iterator.hasNext(); ++i) {
                    if (!iterator.next().getDef().isStackable()) continue;
                    iterator.previous();
                    break;
                }
            }
            if (this.activatedPrayers[8] && iterator.hasNext() && iterator.next().getDef().isStackable()) {
                iterator.previous();
            }
            int slot = 0;
            while (iterator.hasNext()) {
                InvItem item = iterator.next();
                // "If you die you will always retain these gauntlets" is what
                // Dimintheis says when he hands them over, and Klank says the
                // same of his. Kept above the three-item rule rather than
                // inside it: they are not one of the three, they simply never
                // drop.
                if (Player.isKeptOnDeath(item.getID())) {
                    continue;
                }
                if (item.isWielded()) {
                    item.setWield(false);
                    this.updateWornItems(item.getWieldableDef().getWieldPos(), this.appearance.getSprite(item.getWieldableDef().getWieldPos()));
                }
                iterator.remove();
                world.registerItem(new Item(item.getID(), this.getX(), this.getY(), item.getAmount(), player));
                ++slot;
            }
            this.removeSkull();
        }
        world.registerItem(new Item(20, this.getX(), this.getY(), 1, player));
        for (int x = 0; x < this.activatedPrayers.length; ++x) {
            if (!this.activatedPrayers[x]) continue;
            this.removePrayerDrain(x);
            this.activatedPrayers[x] = false;
        }
        this.actionSender.sendPrayers();
        // Lumbridge, as upstream RSCD v25 has it. Ignis Isle moved the death
        // respawn to (555,555), which is in Ardougne -- a members area, and
        // not where vanilla RSC puts you.
        this.setLocation(Point.location(122, 647), true);
        Collection<Player> allWatched = this.watchedPlayers.getAllEntities();
        for (Player p : allWatched) {
            p.removeWatchedPlayer(this);
        }
        this.resetPath();
        this.resetCombat(CombatState.LOST);
        this.actionSender.sendWorldInfo();
        this.actionSender.sendEquipmentStats();
        this.actionSender.sendInventory();
    }

    public void addAttackedBy(Player p) {
        long now = System.currentTimeMillis();
        // This map was never pruned: one entry accrued per distinct attacker
        // for the lifetime of the character's session. Only recent attacks
        // matter (lastAttackedBy is a short combat-timing check), so drop
        // anything older than 10 minutes to keep the map bounded.
        Iterator<Map.Entry<Long, Long>> it = this.attackedBy.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 600000L) {
                it.remove();
            }
        }
        this.attackedBy.put(p.getUsernameHash(), now);
    }

    public long lastAttackedBy(Player p) {
        Long time = this.attackedBy.get(p.getUsernameHash());
        if (time != null) {
            return time;
        }
        return 0L;
    }

    public void setCastTimer() {
        this.lastSpellCast = System.currentTimeMillis();
    }

    public void setSpellFail() {
        this.lastSpellCast = System.currentTimeMillis() + 20000L;
    }

    public void setSpellBlock() {
        this.lastSpellCast = System.currentTimeMillis() + 1000L;
    }

    public int getSpellWait() {
        return DataConversions.roundUp((double)(1200L - (System.currentTimeMillis() - this.lastSpellCast)) / 1000.0);
    }

    public long getCastTimer() {
        return this.lastSpellCast;
    }

    public boolean castTimer() {
        return System.currentTimeMillis() - this.lastSpellCast > 1200L;
    }

    public boolean checkAttack(Mob mob, boolean missile) {
        if (mob instanceof Player) {
            Player opponent;
            Player victim = (Player)mob;
            if (this.inCombat() && this.isDueling() && victim.inCombat() && victim.isDueling() && (opponent = (Player)this.getOpponent()) != null && victim.equals(opponent)) {
                return true;
            }
            if (System.currentTimeMillis() - mob.getCombatTimer() < (long)(mob.getCombatState() == CombatState.RUNNING || mob.getCombatState() == CombatState.WAITING ? 3000 : 500) && !mob.inCombat()) {
                return false;
            }
            if (victim.getLocation().inArena() && this.inCombat() && this.isDueling() && victim.inCombat() && victim.isDueling() && (opponent = (Player)this.getOpponent()) != null && victim.equals(opponent)) {
                return true;
            }
            int myWildLvl = this.getLocation().wildernessLevel();
            int victimWildLvl = victim.getLocation().wildernessLevel();
            if (myWildLvl < 1 || victimWildLvl < 1) {
                this.actionSender.sendMessage("@gry@ You cannot attack other players outside of the wilderness!");
                return false;
            }
            int combDiff = Math.abs(this.getCombatLevel() - victim.getCombatLevel());
            if (combDiff > myWildLvl && !victim.getLocation().inArena()) {
                this.actionSender.sendMessage("@gry@ You must move to at least level " + combDiff + " wilderness to attack " + victim.getUsername() + "!");
                return false;
            }
            if (combDiff > victimWildLvl && !victim.getLocation().inArena()) {
                this.actionSender.sendMessage("@gry@ " + victim.getUsername() + " is not in high enough wilderness for you to attack!");
                return false;
            }
            return true;
        }
        if (mob instanceof Npc) {
            Npc victim = (Npc)mob;
            if (!victim.getDef().isAttackable()) {
                /* Scripted guardians (Druidic Ritual's Suits of armour) are
                   unattackable so they can't be pre-killed, but they engage
                   the player themselves -- once one is your opponent it is a
                   real fight, and ranged and magic must be able to answer it
                   the same as the melee retaliation already can. */
                if (victim.getOpponent() == this) {
                    return true;
                }
                this.setSuspiciousPlayer(true);
                return false;
            }
            return true;
        }
        return true;
    }

    public void informOfBubble(Bubble b) {
        this.bubblesNeedingDisplayed.add(b);
    }

    public List<Bubble> getBubblesNeedingDisplayed() {
        return this.bubblesNeedingDisplayed;
    }

    public void clearBubblesNeedingDisplayed() {
        this.bubblesNeedingDisplayed.clear();
    }

    public void informOfChatMessage(ChatMessage cm) {
        this.chatMessagesNeedingDisplayed.add(cm);
    }

    public void sayMessage(String msg, Mob to) {
        ChatMessage cm = new ChatMessage(this, msg, to);
        this.chatMessagesNeedingDisplayed.add(cm);
    }

    public void informOfNpcMessage(ChatMessage cm) {
        this.npcMessagesNeedingDisplayed.add(cm);
    }

    public List<ChatMessage> getNpcMessagesNeedingDisplayed() {
        return this.npcMessagesNeedingDisplayed;
    }

    public List<ChatMessage> getChatMessagesNeedingDisplayed() {
        return this.chatMessagesNeedingDisplayed;
    }

    public void clearNpcMessagesNeedingDisplayed() {
        this.npcMessagesNeedingDisplayed.clear();
    }

    public void clearChatMessagesNeedingDisplayed() {
        this.chatMessagesNeedingDisplayed.clear();
    }

    public void informOfModifiedHits(Mob mob) {
        if (mob instanceof Player) {
            this.playersNeedingHitsUpdate.add((Player)mob);
        } else if (mob instanceof Npc) {
            this.npcsNeedingHitsUpdate.add((Npc)mob);
        }
    }

    public List<Player> getPlayersRequiringHitsUpdate() {
        return this.playersNeedingHitsUpdate;
    }

    public List<Npc> getNpcsRequiringHitsUpdate() {
        return this.npcsNeedingHitsUpdate;
    }

    public void clearPlayersNeedingHitsUpdate() {
        this.playersNeedingHitsUpdate.clear();
    }

    public void clearNpcsNeedingHitsUpdate() {
        this.npcsNeedingHitsUpdate.clear();
    }

    /*
     * A projectile is carried by whoever fired it, so which of the two update
     * blocks it belongs in is decided by the caster and nothing else. Callers
     * do not have to know that: they hand the shot over and it is filed here.
     */
    public void informOfProjectile(Projectile p) {
        if (p.getCaster() instanceof Npc) {
            this.npcProjectilesNeedingDisplayed.add(p);
        } else {
            this.projectilesNeedingDisplayed.add(p);
        }
    }

    public List<Projectile> getProjectilesNeedingDisplayed() {
        return this.projectilesNeedingDisplayed;
    }

    public List<Projectile> getNpcProjectilesNeedingDisplayed() {
        return this.npcProjectilesNeedingDisplayed;
    }

    public void clearProjectilesNeedingDisplayed() {
        this.projectilesNeedingDisplayed.clear();
        this.npcProjectilesNeedingDisplayed.clear();
    }

    /**
     * A combat round, and the drain rate that spends exactly one prayer point
     * in one of them.
     *
     * "The unit of measurement with prayer drain is the combat round, which is
     * in intervals of about three seconds or more precisely 3.33 seconds.
     * Having a prayer with drain rate of 60, the player's points decreases by 1
     * for every round."
     *
     * So a point costs ROUND_MS * 60 / drain milliseconds, and drains ADD
     * before that division rather than after -- the article works the sum
     * 30 + 30 + 60 = 120 = 2*60 and reads it as two points a round. It is a
     * straight division with no cap and no knee anywhere in its range.
     *
     * What we had was 240000, which is the same shape with the round taken as
     * 4 seconds instead of 3.33. That made every prayer in the game last
     * 240000/199800 = 1.2012 times too long -- flatly, at every drain sum from
     * 1 to the 460 you get with all fourteen prayers lit, the only variation
     * being integer truncation in the fourth decimal place.
     */
    private static final int ROUND_MS = 3330;
    private static final int DRAIN_PER_ROUND = 60;

    /**
     * "For every +1 in the prayer equipment status, the player can expect a
     * reduction of about 3.1-3.2% in their drain rate."
     *
     * Read as TIME GAINED rather than as rate removed, because the article
     * gives two worked figures and only that reading hits them:
     *
     *   bonus 11 (full Monks robes)          -> 1 + 0.031*11 = 1.341, "about 34% more time"
     *   bonus 19 (+ Holy Symbol, the max)    -> 1 + 0.031*19 = 1.589, "about 59% increase of time"
     *
     * The rival reading, drain * (1 - 0.031*bonus), gives 1.52 and 2.43 and
     * misses both. 0.032 also overshoots the 19 case (1.61 against "about
     * 59%"), so the low end of the article's own 3.1-3.2% band is the one that
     * fits, and it is not a preference.
     *
     * Our item data agrees with the article's table item for item -- Holy
     * Symbol 8, Monks robe 6 and 5, Druids robe 4 and 4, Priest robe and gown
     * 3 and 3, robe of Zamorak 3 and 3 -- and 6 + 5 + 8 is the +19 it calls the
     * maximum possible. So the accumulator was never the broken part.
     */
    private static final double BONUS_TIME_PER_POINT = 0.031;

    /**
     * Milliseconds per prayer point at the current drain rate and the current
     * worn prayer bonus, or {@link Integer#MAX_VALUE} when nothing is lit.
     *
     * getPrayerPoints() is the equipment-panel number the client renders, and
     * it counts from 1, not from 0 -- same as getMagicPoints() and the rest.
     * The bonus the article talks about is that number minus its base.
     */
    private int prayerDrainDelay() {
        if (this.drainRate <= 0) {
            return Integer.MAX_VALUE;
        }
        int bonus = this.getPrayerPoints() - 1;
        if (bonus < 0) {
            bonus = 0;
        }
        double perPoint = (double) ROUND_MS * DRAIN_PER_ROUND / this.drainRate;
        perPoint *= 1.0 + BONUS_TIME_PER_POINT * bonus;
        long ms = Math.round(perPoint);
        if (ms < 1L) {
            ms = 1L;
        }
        return ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms;
    }

    public void addPrayerDrain(int prayerID) {
        PrayerDef prayer = EntityHandler.getPrayerDef(prayerID);
        this.drainRate += prayer.getDrainRate();
        this.drainer.setDelay(this.prayerDrainDelay());
    }

    public void removePrayerDrain(int prayerID) {
        PrayerDef prayer = EntityHandler.getPrayerDef(prayerID);
        this.drainRate -= prayer.getDrainRate();
        if (this.drainRate <= 0) {
            this.drainRate = 0;
        }
        this.drainer.setDelay(this.prayerDrainDelay());
    }

    public boolean isFriendsWith(long usernameHash) {
        return this.friendList.containsKey(usernameHash);
    }

    public boolean isIgnoring(long usernameHash) {
        return this.ignoreList.contains(usernameHash);
    }

    public Collection<Map.Entry<Long, Integer>> getFriendList() {
        return this.friendList.entrySet();
    }

    public ArrayList<Long> getIgnoreList() {
        return this.ignoreList;
    }

    public void removeFriend(long id) {
        this.friendList.remove(id);
    }

    public void removeIgnore(long id) {
        this.ignoreList.remove(id);
    }

    public void addFriend(long id, int world) {
        this.friendList.put(id, world);
    }

    public void addIgnore(long id) {
        this.ignoreList.add(id);
    }

    public int friendCount() {
        return this.friendList.size();
    }

    public int ignoreCount() {
        return this.ignoreList.size();
    }

    public void setTradeConfirmAccepted(boolean b) {
        this.tradeConfirmAccepted = b;
    }

    public void setDuelConfirmAccepted(boolean b) {
        this.duelConfirmAccepted = b;
    }

    public boolean isTradeConfirmAccepted() {
        return this.tradeConfirmAccepted;
    }

    public boolean isDuelConfirmAccepted() {
        return this.duelConfirmAccepted;
    }

    public void setTradeOfferAccepted(boolean b) {
        this.tradeOfferAccepted = b;
    }

    public void setDuelOfferAccepted(boolean b) {
        this.duelOfferAccepted = b;
    }

    public boolean isTradeOfferAccepted() {
        return this.tradeOfferAccepted;
    }

    public boolean isDuelOfferAccepted() {
        return this.duelOfferAccepted;
    }

    public void resetTradeOffer() {
        this.tradeOffer.clear();
    }

    public void resetDuelOffer() {
        this.duelOffer.clear();
    }

    public void addToTradeOffer(InvItem item) {
        this.tradeOffer.add(item);
    }

    public void addToDuelOffer(InvItem item) {
        this.duelOffer.add(item);
    }

    public ArrayList<InvItem> getTradeOffer() {
        return this.tradeOffer;
    }

    public ArrayList<InvItem> getDuelOffer() {
        return this.duelOffer;
    }

    public void setTrading(boolean b) {
        this.isTrading = b;
    }

    public void setDueling(boolean b) {
        this.isDueling = b;
    }

    public boolean isTrading() {
        return this.isTrading;
    }

    public boolean isDueling() {
        return this.isDueling;
    }

    public void setWishToTrade(Player p) {
        this.wishToTrade = p;
    }

    public void setWishToDuel(Player p) {
        this.wishToDuel = p;
    }

    public Player getWishToTrade() {
        return this.wishToTrade;
    }

    public Player getWishToDuel() {
        return this.wishToDuel;
    }

    public void setDuelSetting(int i, boolean b) {
        this.duelOptions[i] = b;
    }

    public boolean getDuelSetting(int i) {
        try {
            for (InvItem item : this.duelOffer) {
                if (!DataConversions.inArray(Formulae.runeIDs, item.getID())) continue;
                this.setDuelSetting(1, true);
                break;
            }
            for (InvItem item : this.wishToDuel.getDuelOffer()) {
                if (!DataConversions.inArray(Formulae.runeIDs, item.getID())) continue;
                this.setDuelSetting(1, true);
                break;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return this.duelOptions[i];
    }

    public void setMale(boolean male) {
        this.maleGender = male;
    }

    public boolean isMale() {
        return this.maleGender;
    }

    public void setChangingAppearance(boolean b) {
        this.changingAppearance = b;
    }

    public boolean isChangingAppearance() {
        return this.changingAppearance;
    }

    public boolean isReconnecting() {
        return this.reconnecting;
    }

    public void setLastLogin(long l) {
        this.lastLogin = l;
    }

    public long getLastLogin() {
        return this.lastLogin;
    }

    public int getDaysSinceLastLogin() {
        long now = Calendar.getInstance().getTimeInMillis() / 1000L;
        return (int)((now - this.lastLogin) / 86400L);
    }

    public long getCurrentLogin() {
        return this.currentLogin;
    }

    public void setLastIP(String ip) {
        this.lastIP = ip;
    }

    public String getCurrentIP() {
        return this.currentIP;
    }

    public String getLastIP() {
        return this.lastIP;
    }

    public int getAmuletCharges() {
        return this.amuletCharges;
    }

    /**
     * Restore the counter from a save. Anything outside 1..4 -- including the
     * zero a pre-migration row defaults to -- is a full four, because zero is
     * not a state this counter can rest in.
     */
    public void setAmuletCharges(int i) {
        this.amuletCharges = i >= 1 && i <= 4 ? i : 4;
    }

    // --------------------------------------------------------------- poison --

    public int getPoisonStrength() {
        return this.poisonStrength;
    }

    public int getPoisonHits() {
        return this.poisonHits;
    }

    /**
     * Set both halves of the poison state at once, because they only ever mean
     * anything together: a strength with no count is half a state. Strength 0
     * is clean and forces the count to 0 with it.
     */
    public void setPoison(int strength, int hits) {
        this.poisonStrength = strength > 0 ? strength : 0;
        this.poisonHits = this.poisonStrength > 0 ? hits : 0;
    }

    public boolean isPoisoned() {
        return this.poisonStrength > 0;
    }

    public long getPoisonImmuneUntil() {
        return this.poisonImmuneUntil;
    }

    public void setPoisonImmuneUntil(long when) {
        this.poisonImmuneUntil = when;
    }

    public boolean isImmuneToPoison() {
        return System.currentTimeMillis() < this.poisonImmuneUntil;
    }

    /**
     * Spend one teleport off the charged dragonstone amulet.
     *
     * True means that was the last one and the amulet the player just rubbed
     * has to become an uncharged 522. Reaching zero and going back to four are
     * the same event, so the player never sits at zero: hitting it hands the
     * amulet back uncharged and re-arms the counter immediately.
     */
    public boolean spendAmuletCharge() {
        if (--this.amuletCharges > 0) {
            return false;
        }
        this.amuletCharges = 4;
        return true;
    }

    public java.util.ArrayList<Integer> getGnomeDish() {
        return this.gnomeDish;
    }

    public void setGnomeDish(java.util.ArrayList<Integer> dish) {
        this.gnomeDish = dish;
    }

    public boolean isSubscriber() {
        return Config.EVERYONE_SUBSCRIBER || this.getSubDays() > 0L || this.isAdmin();
    }

    public void setSubscriptionExpires(long expires) {
        this.subscriptionExpires = DataConversions.hashToDate(expires);
    }

    /*
     * everyone_subscriber's own refresh, called on every login.
     *
     * Not a stacking extension -- "keep it 1yr from sign in" means exactly
     * that: whatever was there before is overwritten, so a player who logs in
     * daily always has ~365 days left, never a growing pile from repeated
     * logins.
     *
     * subscriptionExpires is never written back to the account row (see
     * getSubDays -- it arrives from the login server on login and goes
     * nowhere else), so there is no database trip here: it is reset the same
     * way it is always set, through the hash round-trip, and lives only for
     * this session.
     */
    public void refreshSubscriptionOneYear() {
        java.util.Calendar cal = new java.util.GregorianCalendar();
        cal.add(java.util.Calendar.YEAR, 1);
        String date = (cal.get(java.util.Calendar.MONTH) + 1) + "/" + cal.get(java.util.Calendar.DAY_OF_MONTH)
            + "/" + cal.get(java.util.Calendar.YEAR);
        this.setSubscriptionExpires(DataConversions.dateToHash(date));
    }

    public String getSubscriptionExpires() {
        return this.subscriptionExpires;
    }

    /*
     * Days of subscription left, or 0 if there is no subscription.
     *
     * THIS USED TO BREAK LOGIN OUTRIGHT. The expiry is stored as a packed long
     * and unpacked by DataConversions.hashToDate, which encodes '/' as 'g'. A
     * player who has never subscribed has 0 in that column, and hashToDate(0)
     * returns the empty string -- its loop runs while the value is non-zero, so
     * for 0 it never runs at all. "".split("/") is a one-element array, so
     * broke[2] threw ArrayIndexOutOfBoundsException.
     *
     * The throw landed in PlayerLogin.handlePacket, at the isSubscriber() check
     * near the very end -- after the profile was read and the player was in the
     * world, but BEFORE setBusy(false). So the player appeared to log in
     * normally and was then frozen: canLogout() is !isBusy() && ..., and every
     * movement path checks isBusy() too. Hence "cannot move" and "cannot log
     * out" together, with only "Exception with p[0] from LOGIN_SERVER" in the
     * log and no stack trace to say where.
     *
     * Any unsubscribed player hit this, which is to say every new account.
     *
     * Parsing is MM/DD/YYYY -- that is the order dateToHash was fed and the
     * order the original read back. GregorianCalendar's month is 0-based, so
     * the month is decremented; the original passed it straight through and so
     * dated every subscription one month late.
     */
    public long getSubDays() {
        String database_date = this.getSubscriptionExpires();
        if (database_date == null) {
            return 0L;
        }

        String[] broke = database_date.split("/");
        if (broke.length != 3) {
            return 0L;
        }

        int month;
        int day;
        int year;
        try {
            month = Integer.parseInt(broke[0].trim());
            day = Integer.parseInt(broke[1].trim());
            year = Integer.parseInt(broke[2].trim());
        }
        catch (NumberFormatException e) {
            // A corrupt expiry must not be able to stop someone logging in.
            return 0L;
        }

        Date d1 = new GregorianCalendar(year, month - 1, day, 0, 0).getTime();
        Date today = new Date();
        return (d1.getTime() - today.getTime()) / 86400000L;
    }

    public void setGroupID(int id) {
        this.groupID = id;
    }

    public int getGroupID() {
        return this.groupID;
    }

    public boolean isTeam1() {
        return this.groupID == 4;
    }

    public boolean isTeam2() {
        return this.groupID == 4;
    }

    public boolean isPMod() {
        return this.groupID == 6 || this.isMod() || this.isAdmin();
    }

    public boolean isMod() {
        return this.groupID == 2 || this.isAdmin();
    }

    public boolean isAdmin() {
        return this.groupID == 1;
    }

    @Override
    public int getArmourPoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getArmourPoints();
        }
        return points < 1 ? 1 : points;
    }

    @Override
    public int getWeaponAimPoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getWeaponAimPoints();
        }
        return points < 1 ? 1 : points;
    }

    @Override
    public int getWeaponPowerPoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getWeaponPowerPoints();
        }
        return points < 1 ? 1 : points;
    }

    public int getMagicPoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getMagicPoints();
        }
        return points < 1 ? 1 : points;
    }

    public int getPrayerPoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getPrayerPoints();
        }
        return points < 1 ? 1 : points;
    }

    public int getRangePoints() {
        int points = 1;
        for (InvItem item : this.inventory.getItems()) {
            if (!item.isWielded()) continue;
            points += item.getWieldableDef().getRangePoints();
        }
        return points < 1 ? 1 : points;
    }

    public MiscPacketBuilder getActionSender() {
        return this.actionSender;
    }

    /** A session flag, or 0 if it was never set. See the field. */
    public int getFlag(String key) {
        Integer value = this.flags.get(key);
        return value == null ? 0 : value.intValue();
    }

    /** Set a session flag. Setting it to 0 forgets it. */
    public void setFlag(String key, int value) {
        if (value == 0) {
            this.flags.remove(key);
        } else {
            this.flags.put(key, Integer.valueOf(value));
        }
    }

    /**
     * True if this exact item id is being worn.
     *
     * wornItems is the appearance array -- sprite numbers by equipment slot --
     * so it cannot answer this. The inventory is where wielded state lives.
     */
    public boolean isWearing(int id) {
        for (InvItem item : this.inventory.getItems()) {
            if (item.isWielded() && item.getID() == id) {
                return true;
            }
        }
        return false;
    }

    public int[] getWornItems() {
        return this.wornItems;
    }

    public void updateWornItems(int index, int id) {
        this.wornItems[index] = id;
        this.ourAppearanceChanged = true;
    }

    public void setWornItems(int[] worn) {
        this.wornItems = worn;
        this.ourAppearanceChanged = true;
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public void setInventory(Inventory i) {
        this.inventory = i;
    }

    public Bank getBank() {
        return this.bank;
    }

    public void setBank(Bank b) {
        this.bank = b;
    }

    public void setGameSetting(int i, boolean b) {
        this.gameSettings[i] = b;
    }

    public boolean getGameSetting(int i) {
        return this.gameSettings[i];
    }

    public void setPrivacySetting(int i, boolean b) {
        this.privacySettings[i] = b;
    }

    public boolean getPrivacySetting(int i) {
        return this.privacySettings[i];
    }

    public long getLastPing() {
        return this.lastPing;
    }

    public Connection getSession() {
        return this.ioSession;
    }

    public boolean loggedIn() {
        return this.loggedIn;
    }

    /**
     * Online-state persistence deliberately does NOT happen here.
     *
     * This method used to run two UPDATEs against org.rscdaemon.ls.Server.db.
     * That field is assigned in ls.Server.main(), and the login server is a
     * separate JVM process from the game server, so in this process it is
     * always null -- every call threw NullPointerException into an empty catch
     * block. The statements also named a table that does not exist
     * ("rscd_player"; the real one is "rscd_players") and interpolated the
     * player-supplied username straight into SQL, so had the null ever been
     * fixed in isolation the result would have been a live injection on the
     * login path.
     *
     * The login server owns the database. Online state is now maintained there,
     * in ls.model.World, which is told about logins and logouts over the LS
     * protocol and holds a real connection.
     */
    public void setLoggedIn(boolean loggedIn) {
        if (loggedIn) {
            this.currentLogin = System.currentTimeMillis();
        }
        this.loggedIn = loggedIn;
    }

    public String getUsername() {
        return this.username;
    }

    public long getUsernameHash() {
        return this.usernameHash;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public void ping() {
        this.lastPing = System.currentTimeMillis();
    }

    public boolean isSkulled() {
        return this.skullEvent != null;
    }

    public PlayerAppearance getPlayerAppearance() {
        return this.appearance;
    }

    public void setAppearance(PlayerAppearance appearance) {
        this.appearance = appearance;
    }

    public int getSkullTime() {
        if (this.isSkulled()) {
            return this.skullEvent.timeTillNextRun();
        }
        return 0;
    }

    public void addSkull(long timeLeft) {
        if (!this.isSkulled()) {
            this.skullEvent = new DelayedEvent(this, 1200000){

                public void run() {
                    Player.this.removeSkull();
                }
            };
            world.getDelayedEventHandler().add(this.skullEvent);
            super.setAppearnceChanged(true);
        }
        this.skullEvent.setLastRun(System.currentTimeMillis() - (1200000L - timeLeft));
    }

    public void removeSkull() {
        if (!this.isSkulled()) {
            return;
        }
        super.setAppearnceChanged(true);
        this.skullEvent.stop();
        this.skullEvent = null;
    }

    public void setSuspiciousPlayer(boolean suspicious) {
        this.suspicious = suspicious;
    }

    public void addPlayersAppearanceIDs(int[] indicies, int[] appearanceIDs) {
        for (int x = 0; x < indicies.length; ++x) {
            this.knownPlayersAppearanceIDs.put(indicies[x], appearanceIDs[x]);
        }
    }

    public List<Player> getPlayersRequiringAppearanceUpdate() {
        ArrayList<Player> needingUpdates = new ArrayList<Player>();
        needingUpdates.addAll(this.watchedPlayers.getNewEntities());
        if (this.ourAppearanceChanged) {
            needingUpdates.add(this);
        }
        for (Player p : this.watchedPlayers.getKnownEntities()) {
            if (!this.needsAppearanceUpdateFor(p)) continue;
            needingUpdates.add(p);
        }
        return needingUpdates;
    }

    private boolean needsAppearanceUpdateFor(Player p) {
        int playerServerIndex = p.getIndex();
        if (this.knownPlayersAppearanceIDs.containsKey(playerServerIndex)) {
            int knownPlayerAppearanceID = this.knownPlayersAppearanceIDs.get(playerServerIndex);
            return knownPlayerAppearanceID != p.getAppearanceID();
        }
        return true;
    }

    public void updateViewedPlayers() {
        List<Player> playersInView = this.viewArea.getPlayersInView();
        for (Player p : playersInView) {
            if (p.getIndex() == this.getIndex() || !p.loggedIn()) continue;
            this.informOfPlayer(p);
            p.informOfPlayer(this);
        }
    }

    public void updateViewedObjects() {
        List<GameObject> objectsInView = this.viewArea.getGameObjectsInView();
        for (GameObject o : objectsInView) {
            if (this.watchedObjects.contains(o) || o.isRemoved() || !this.withinRange(o)) continue;
            this.watchedObjects.add(o);
        }
    }

    public void updateViewedItems() {
        List<Item> itemsInView = this.viewArea.getItemsInView();
        for (Item i : itemsInView) {
            if (this.watchedItems.contains(i) || i.isRemoved() || !this.withinRange(i) || !i.visibleTo(this)) continue;
            this.watchedItems.add(i);
        }
    }

    /*
     * isRemoved() is the load-bearing half of this test, and it was missing.
     *
     * An npc that has been unregistered but is somehow still listed on its
     * ActiveTile used to be immortal on screen. revalidateWatchedNpcs would
     * mark it for removal (it does check isRemoved), this method would find it
     * on the tile again and re-add it in the same tick, and the packet went
     * out carrying both the remove bits and a fresh spawn -- so the client
     * dropped it and immediately redrew it, every tick, forever. The tick
     * after, update() had cleared both sets and it went out as a brand-new
     * npc instead. It never flickered and it never left.
     *
     * Meanwhile world.getNpc(index) was already null for it, so AttackHandler
     * answered "Attacking disabled." and it sat at 0 hits. Logging out did not
     * help: a fresh watchedNpcs is repopulated from getNpcsInView(), which
     * reads the tile. Nor did walking away and back, for the same reason.
     *
     * A removed npc is permanently dead -- nothing clears the flag, a respawn
     * builds a new instance -- so there is never a reason to show one.
     */
    public void updateViewedNpcs() {
        List<Npc> npcsInView = this.viewArea.getNpcsInView();
        for (Npc n : npcsInView) {
            if (n.isRemoved()) {
                /* Skipping it is the fix; saying so once is how we find out
                   what stranded it, since the skip is otherwise silent and
                   the symptom that used to report the bug is now gone. */
                if (n.reportStranded()) {
                    Logger.error("Stranded npc on tile: id " + n.getID() + " \"" + n.getDef().getName()
                            + "\" index " + n.getIndex() + " at " + n.getLocation().getX() + ","
                            + n.getLocation().getY() + " -- removed but still listed, seen by " + this.getUsername());
                }
                continue;
            }
            if (this.watchedNpcs.contains(n) && !this.watchedNpcs.isRemoving(n) || !this.withinRange(n)) continue;
            this.watchedNpcs.add(n);
        }
    }

    public void teleport(int x, int y, boolean bubble) {
        /* Checked before anything is torn down, so a destination that does
           not exist leaves the player exactly where they were rather than
           half-moved. The world is four 944-tile planes; anything past that
           has no tile, and the NPE that used to follow killed the game
           thread and dropped every player online. */
        if (!World.getWorld().withinWorld(x, y)) {
            Logger.error("Refusing to teleport " + this.getUsername() + " outside the world at " + x + "," + y);
            this.actionSender.sendMessage("@gry@ Nothing happens.");
            return;
        }
        Mob opponent = super.getOpponent();
        if (this.inCombat()) {
            this.resetCombat(CombatState.ERROR);
        }
        if (opponent != null) {
            opponent.resetCombat(CombatState.ERROR);
        }
        Iterator<Player> i$ = this.getWatchedPlayers().getAllEntities().iterator();
        while (i$.hasNext()) {
            Player o;
            Player p = o = i$.next();
            if (bubble) {
                p.getActionSender().sendTeleBubble(this.getX(), this.getY(), false);
            }
            p.removeWatchedPlayer(this);
        }
        if (bubble) {
            this.actionSender.sendTeleBubble(this.getX(), this.getY(), false);
        }
        this.setLocation(Point.location(x, y), true);
        this.resetPath();
        this.actionSender.sendWorldInfo();
    }

    /*
     * Every far move a player makes funnels through here with
     * teleported=true -- teleport() above, the death respawn in killedBy(),
     * quest scripts -- so this is the one place to keep the client's ground
     * state in sync across it. Left to the normal path, the next revalidate
     * emits per-entity removals whose one-byte offsets cannot span a
     * teleport, the client keeps every item/object/door it knew, and the
     * despawned ones haunt the floor until relog (the ghost bones). Purge
     * the blocks around where the player still is, forget the same entity
     * classes server-side, and let the next tick re-add whatever is really
     * in range of the destination.
     *
     * Not on login (the client resets its own arrays then, and the action
     * sender may not be live), and not for short hops like the duel/attack
     * snap-to-opponent, whose removals still fit their byte.
     */
    public void setLocation(Point p, boolean teleported) {
        Point from = this.getLocation();
        if (teleported && this.loggedIn() && from != null
                && (Math.abs(p.getX() - from.getX()) > 16 || Math.abs(p.getY() - from.getY()) > 16)) {
            this.actionSender.sendRegionPurge();
            this.watchedItems.forgetAll();
            this.watchedObjects.forgetAll();
        }
        super.setLocation(p, teleported);
    }

    public void informOfPlayer(Player p) {
        if ((!this.watchedPlayers.contains(p) || this.watchedPlayers.isRemoving(p)) && this.withinRange(p)) {
            this.watchedPlayers.add(p);
        }
    }

    public StatefulEntityCollection<Player> getWatchedPlayers() {
        return this.watchedPlayers;
    }

    public StatefulEntityCollection<GameObject> getWatchedObjects() {
        return this.watchedObjects;
    }

    public StatefulEntityCollection<Item> getWatchedItems() {
        return this.watchedItems;
    }

    public StatefulEntityCollection<Npc> getWatchedNpcs() {
        return this.watchedNpcs;
    }

    public void removeWatchedNpc(Npc n) {
        this.watchedNpcs.remove(n);
    }

    public void removeWatchedPlayer(Player p) {
        this.watchedPlayers.remove(p);
    }

    public void revalidateWatchedPlayers() {
        for (Player p : this.watchedPlayers.getKnownEntities()) {
            if (this.withinRange(p) && p.loggedIn()) continue;
            this.watchedPlayers.remove(p);
            this.knownPlayersAppearanceIDs.remove(p.getIndex());
        }
    }

    public void revalidateWatchedObjects() {
        for (GameObject o : this.watchedObjects.getKnownEntities()) {
            if (this.withinRange(o) && !o.isRemoved()) continue;
            this.watchedObjects.remove(o);
        }
    }

    /*
     * A ground item stops being watched when it goes out of range, when it is
     * flagged removed, when it stops being visible to this player -- or when it
     * is simply no longer on the tile it claims to be on.
     *
     * That last test is the important one, and it is here because the first
     * three are all statements about intent. Every one of them requires the
     * code that took the item away to have said so properly. Anything that
     * lifts an item off an ActiveTile WITHOUT setting isRemoved leaves it
     * listed here for ever: no removal is queued, no packet is sent, and the
     * client goes on drawing an item the world no longer has. It cannot be
     * picked up, because the server has nothing there to pick up, and it does
     * not go away, because nothing will ever tell the client otherwise. The
     * only cure is a relog or walking far enough for the sector to be purged.
     *
     * Item.java documents one such path that was found and fixed at source (a
     * value-based equals() letting List.remove strike a sibling by value, so
     * the sibling left the tile with its flag never set). Fixing that one did
     * not make the shape impossible, and the shape is nasty out of all
     * proportion to its cause: silent, permanent, player-visible, and
     * indistinguishable in-game from the server being broken.
     *
     * So this stops keying on intent alone and asks the world directly. An item
     * genuinely lying on the floor is always in its own tile's list, so the
     * honest case is unaffected; anything else is a ghost by definition,
     * whatever route it took to get there. Ground items never move -- nothing
     * relocates one, they are placed once and unregistered once -- so there is
     * no in-flight state for this to trip over.
     */
    public void revalidateWatchedItems() {
        for (Item i : this.watchedItems.getKnownEntities()) {
            if (this.withinRange(i) && !i.isRemoved() && i.visibleTo(this) && onItsOwnTile(i)) continue;
            this.watchedItems.remove(i);
        }
    }

    /** Whether the world still lists this item on the tile it reports being on. */
    private static boolean onItsOwnTile(Item i) {
        ActiveTile t = World.getWorld().getTile(i.getLocation());
        return t != null && t.hasItem(i);
    }

    public void revalidateWatchedNpcs() {
        for (Npc n : this.watchedNpcs.getKnownEntities()) {
            if (this.withinRange(n) && !n.isRemoved()) continue;
            this.watchedNpcs.remove(n);
        }
    }

    public boolean withinRange(Entity e) {
        int xDiff = this.location.getX() - e.getLocation().getX();
        int yDiff = this.location.getY() - e.getLocation().getY();
        return xDiff <= 16 && xDiff >= -15 && yDiff <= 16 && yDiff >= -15;
    }

    public int[] getCurStats() {
        return this.curStat;
    }

    public int getCurStat(int id) {
        return this.curStat[id];
    }

    @Override
    public int getHits() {
        return this.getCurStat(3);
    }

    @Override
    public int getAttack() {
        return this.getCurStat(0);
    }

    @Override
    public int getDefense() {
        return this.getCurStat(1);
    }

    @Override
    public int getStrength() {
        return this.getCurStat(2);
    }

    @Override
    public void setHits(int lvl) {
        this.setCurStat(3, lvl);
    }

    public void setCurStat(int id, int lvl) {
        if (lvl <= 0) {
            lvl = 0;
        }
        this.curStat[id] = lvl;
    }

    public int getMaxStat(int id) {
        return this.maxStat[id];
    }

    public void setMaxStat(int id, int lvl) {
        if (lvl < 0) {
            lvl = 0;
        }
        this.maxStat[id] = lvl;
    }

    public int[] getMaxStats() {
        return this.maxStat;
    }

    public int getSkillTotal() {
        int total = 0;
        for (int stat : this.maxStat) {
            total += stat;
        }
        return total;
    }

    public void incCurStat(int i, int amount) {
        int n = i;
        this.curStat[n] = this.curStat[n] + amount;
        if (this.curStat[i] < 0) {
            this.curStat[i] = 0;
        }
    }

    public void incMaxStat(int i, int amount) {
        int n = i;
        this.maxStat[n] = this.maxStat[n] + amount;
        if (this.maxStat[i] < 0) {
            this.maxStat[i] = 0;
        }
    }

    public void setFatigue(int fatigue) {
        this.fatigue = fatigue;
    }

    public int getFatigue() {
        return this.fatigue;
    }

    /**
     * Which Agility course this player is part-way round, and how far.
     *
     * Not saved. A lap is a thing you are in the middle of, not a thing you
     * own, and Classic forgot it when you logged out too -- the lap bonus is
     * paid for crossing every obstacle in order in one sitting, and walking
     * away is how you give it up.
     */
    private int agilityCourse = 0;
    private int agilityStage = -1;

    public int getAgilityCourse() {
        return this.agilityCourse;
    }

    public int getAgilityStage() {
        return this.agilityStage;
    }

    /** Remember that this obstacle of this course has just been crossed. */
    public void setAgilityProgress(int course, int stage) {
        this.agilityCourse = course;
        this.agilityStage = stage;
    }

    public void resetAgilityProgress() {
        this.agilityCourse = 0;
        this.agilityStage = -1;
    }

    /**
     * Award experience that fatigue cannot stop.
     *
     * One obstacle in the game behaves this way -- the Barbarian Outpost
     * balancing ledge, which "does not stop giving experience at 100%
     * fatigue". incExp forces the fatigue check on every caller, so rather
     * than weaken it for everybody the counter is set aside for the one
     * award and put back afterwards.
     */
    public void incExpNoFatigue(int i, int amount) {
        int held = this.fatigue;
        this.fatigue = 0;
        this.incExp(i, amount, false);
        this.fatigue = held;
        this.actionSender.sendFatigue();
    }

    /**
     * Experience is stored in quarter-units, the way real RSC kept it: the
     * displayed value times four, so that the fractional awards in the
     * original tables (3.75 for a bone) are exact integers here (15). The
     * client is sent the displayed value; only this class and the database
     * ever see quarters.
     *
     * incExp keeps its historical display-unit contract -- every existing
     * caller passes the number a player would see -- and multiplies. New
     * code correcting a table against the original guides should prefer
     * incExpQuarters with the exact quarter figure.
     */
    public void incExp(int i, int amount, boolean useFatigue) {
        this.incExpQuarters(i, amount * 4, useFatigue);
    }

    public void incExpQuarters(int i, int amount, boolean useFatigue) {
        int level;
        /*
         * Callers passing false (quest rewards) skip the fatigue gate --
         * except in the combat stats, where the gate always applies. Real
         * RSC paid quest xp regardless of fatigue, which forced xp into
         * attack/strength/defense that a pure never wanted; gating only
         * these six keeps that choice available (stay at 100% fatigue to
         * refuse the xp) without costing anyone else their reward.
         * 0 attack, 1 defense, 2 strength, 4 ranged, 5 prayer, 6 magic.
         */
        if (i <= 2 || (i >= 4 && i <= 6)) {
            useFatigue = true;
        }
        if (useFatigue) {
            if (this.fatigue >= 100) {
                this.actionSender.sendMessage("@gry@ You are too tired to gain experience, get some rest!");
                return;
            }
            if (this.fatigue >= 96) {
                this.actionSender.sendMessage("@gry@ You start to feel tired, maybe you should rest soon.");
            }
            ++this.fatigue;
            this.actionSender.sendFatigue();
        }
        int totaladd = 0;
        if (this.isSubscriber()) {
            totaladd += Config.SUBSCRIBER_EXP_MULT;
        }
        int n = i;
        this.exp[n] = this.exp[n] + (amount *= Config.EXP_MULT + totaladd);
        if (this.exp[i] < 0) {
            this.exp[i] = 0;
        }
        if ((level = Formulae.experienceToLevel(this.exp[i])) != this.maxStat[i]) {
            int advanced = level - this.maxStat[i];
            this.incCurStat(i, advanced);
            this.incMaxStat(i, advanced);
            this.actionSender.sendStat(i);
            this.actionSender.sendMessage("@gre@You just advanced " + advanced + " " + Formulae.statArray[i] + " level!");
            this.actionSender.sendSound("advance");
            world.getDelayedEventHandler().add(new MiniEvent(this){

                public void action() {
                    this.owner.getActionSender().sendScreenshot();
                }
            });
            int comb = Formulae.getCombatlevel(this.maxStat);
            if (comb != this.getCombatLevel()) {
                this.setCombatLevel(comb);
            }
        }
    }

    public int[] getExps() {
        return this.exp;
    }

    public int getExp(int id) {
        return this.exp[id];
    }

    public void setExp(int id, int lvl) {
        if (lvl < 0) {
            lvl = 0;
        }
        this.exp[id] = lvl;
    }

    public void setExp(int[] lvls) {
        this.exp = lvls;
    }

    public boolean equals(Object o) {
        if (o instanceof Player) {
            Player p = (Player)o;
            return this.usernameHash == p.getUsernameHash();
        }
        return false;
    }
}

