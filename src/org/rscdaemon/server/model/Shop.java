/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.util.DataConversions;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Shop {
    public static final World world = World.getWorld();
    private static int MAX_SIZE = 40;
    private String name;
    private boolean general;
    private int sellModifier;
    private int buyModifier;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private String greeting;
    private String[] options;
    private ArrayList<InvItem> items;
    private int[] equilibriumIds;
    private int[] equilibriumAmounts;
    private ArrayList<Player> players;
    private int respawnRate;

    /**
     * How much this shop's price moves per unit of over- or understock, in
     * percentage points -- Jagex's StockSensitivity, recovered with the two
     * multipliers from the shop table.
     *
     * It is recorded here and not applied, deliberately. The shop screen is
     * drawn entirely client-side: packet 253 carries the two multipliers as a
     * single byte each for the whole shop, then two shorts per line -- item id
     * and stock -- and nothing else. The client works out every price itself as
     * modifier * basePrice / 100 (mudclient.java:6807 for buying, :6832 for
     * selling) and never looks at the stock it was just sent. So a server-side
     * price curve would charge one number while the client showed another, and
     * ShopHandler's anti-tamper check -- it rejects a purchase whose claimed
     * price is not the one the shop quotes -- would reject every trade after
     * the first.
     *
     * The 253 we write (MiscPacketBuilder.showShop) matches what the client
     * reads, field for field, so there is no desync today.
     *
     * Worth knowing before anyone calls this impossible: RSCSundae's shop
     * packet writes a THIRD field per line, a signed byte carrying exactly this
     * per-item modifier (src/protocol/outgoing.c:2113). The channel existed in
     * the era it targets; our 2003 client simply has no field to read it into.
     * Adding it is a coordinated client-and-server protocol change -- and the
     * webclient would need it too -- which is not something to do quietly
     * inside a data fix.
     */
    private int stockSensitivity;

    /**
     * Which entry in options() closes the conversation instead of opening the
     * shop. Almost always the last one, but the two Grand Tree grocers and
     * Scavvo really do list the refusal first, and Scavvo has two options that
     * both open the shop, so "option 0 opens it" was never general.
     */
    private int declineOption;

    /**
     * Milliseconds per unit restocked, one entry per line of items, in the
     * same order. Jagex gave every stock line its own timer -- a bronze axe
     * comes back in seconds, a rune chain body in half an hour -- and RSCD had
     * flattened all of them to one 15-second timer for the whole shop.
     */
    private int[] restockRates;

    /** id -> when that line next moves one step toward equilibrium. */
    private transient Map<Integer, Long> nextRestock;

    /** How often the restock event wakes up. One RSC game tick. */
    private static final int RESTOCK_TICK = 640;

    public boolean shouldStock(int id) {
        if (this.general) {
            return true;
        }
        for (int eqID : this.equilibriumIds) {
            if (eqID != id) continue;
            return true;
        }
        return false;
    }

    public void addPlayer(Player player) {
        this.players.add(player);
    }

    public void removePlayer(Player player) {
        this.players.remove(player);
    }

    /**
     * The per-line restock clock.
     *
     * RSCD ran one timer for the whole shop: every respawnRate milliseconds
     * every understocked line gained a unit, and every fourth pass every
     * overstocked line lost one. Jagex gave each line its own timer instead,
     * and the spread is wide -- Noterazzo's bronze axes come back every 12
     * seconds, Scavvo's rune chain body every 32 minutes -- which is what
     * stops a rune shop from being an infinite tap. restockRates carries
     * those, one entry per line of items in the same order.
     *
     * Overstock decays on the same clock as restock rather than on RSCD's
     * every-fourth-pass rule. A line dumped on by a player drains at the rate
     * that line moves, which is the same number Jagex published, instead of at
     * a rate invented to be four times slower than restocking.
     */
    public void initRestock() {
        this.players = new ArrayList<Player>();
        if (this.restockRates == null || this.restockRates.length != this.equilibriumIds.length) {
            this.restockRates = new int[this.equilibriumIds.length];
            Arrays.fill(this.restockRates, this.respawnRate);
        }
        this.nextRestock = new HashMap<Integer, Long>();
        final Shop shop = this;
        world.getDelayedEventHandler().add(new DelayedEvent(null, RESTOCK_TICK){

            public void run() {
                long now = System.currentTimeMillis();
                boolean changed = false;
                Iterator<InvItem> iterator = Shop.this.items.iterator();
                while (iterator.hasNext()) {
                    InvItem shopItem = iterator.next();
                    Integer key = Integer.valueOf(shopItem.getID());
                    Long due = Shop.this.nextRestock.get(key);
                    if (due == null || now < due.longValue()) {
                        if (due == null) {
                            Shop.this.nextRestock.put(key, Long.valueOf(now + shop.getRestockRate(shopItem.getID())));
                        }
                        continue;
                    }
                    Shop.this.nextRestock.put(key, Long.valueOf(now + shop.getRestockRate(shopItem.getID())));
                    int eq = shop.getEquilibrium(shopItem.getID());
                    if (shopItem.getAmount() > eq) {
                        shopItem.setAmount(shopItem.getAmount() - 1);
                        if (shopItem.getAmount() <= 0 && !DataConversions.inArray(Shop.this.equilibriumIds, shopItem.getID())) {
                            iterator.remove();
                            Shop.this.nextRestock.remove(key);
                        }
                        changed = true;
                        continue;
                    }
                    if (shopItem.getAmount() >= eq) continue;
                    shopItem.setAmount(shopItem.getAmount() + 1);
                    changed = true;
                }
                if (changed) {
                    shop.updatePlayers();
                }
            }
        });
    }

    /**
     * Milliseconds this shop takes to move the given line one unit. Falls back
     * to the shop-wide respawnRate for a line the shop does not normally
     * carry -- everything a player has sold to a general store -- and for the
     * lines Jagex's table has no timer for.
     */
    public int getRestockRate(int id) {
        for (int idx = 0; idx < this.equilibriumIds.length; ++idx) {
            if (this.equilibriumIds[idx] != id) continue;
            if (this.restockRates != null && idx < this.restockRates.length && this.restockRates[idx] > 0) {
                return this.restockRates[idx];
            }
            break;
        }
        return this.respawnRate;
    }

    public void updatePlayers() {
        Iterator<Player> iterator = this.players.iterator();
        while (iterator.hasNext()) {
            Player p = iterator.next();
            if (!this.equals(p.getShop())) {
                iterator.remove();
                continue;
            }
            p.getActionSender().showShop(this);
        }
    }

    public int getEquilibrium(int id) {
        for (int idx = 0; idx < this.equilibriumIds.length; ++idx) {
            if (this.equilibriumIds[idx] != id) continue;
            return this.equilibriumAmounts[idx];
        }
        return 0;
    }

    public InvItem getFirstById(int id) {
        for (int index = 0; index < this.items.size(); ++index) {
            if (this.items.get(index).getID() != id) continue;
            return this.items.get(index);
        }
        return null;
    }

    public void setEquilibrium() {
        this.equilibriumIds = new int[this.items.size()];
        this.equilibriumAmounts = new int[this.items.size()];
        for (int idx = 0; idx < this.items.size(); ++idx) {
            this.equilibriumIds[idx] = this.items.get(idx).getID();
            this.equilibriumAmounts[idx] = this.items.get(idx).getAmount();
        }
    }

    public String getName() {
        return this.name;
    }

    public String getGreeting() {
        return this.greeting;
    }

    public String[] getOptions() {
        return this.options;
    }

    public boolean withinShop(Point p) {
        return p.getX() >= this.minX && p.getX() <= this.maxX && p.getY() >= this.minY && p.getY() <= this.maxY;
    }

    public ArrayList<InvItem> getItems() {
        return this.items;
    }

    public boolean contains(InvItem i) {
        return this.items.contains(i);
    }

    public int add(InvItem item) {
        if (item.getAmount() <= 0) {
            return -1;
        }
        for (int index = 0; index < this.items.size(); ++index) {
            if (!item.equals(this.items.get(index))) continue;
            this.items.get(index).setAmount(this.items.get(index).getAmount() + item.getAmount());
            return index;
        }
        this.items.add(item);
        return this.items.size() - 2;
    }

    public int remove(InvItem item) {
        Iterator<InvItem> iterator = this.items.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            InvItem i = iterator.next();
            if (item.getID() == i.getID()) {
                if (item.getAmount() < i.getAmount()) {
                    i.setAmount(i.getAmount() - item.getAmount());
                } else if (DataConversions.inArray(this.equilibriumIds, item.getID())) {
                    i.setAmount(0);
                } else {
                    iterator.remove();
                }
                return index;
            }
            ++index;
        }
        return -1;
    }

    public ListIterator<InvItem> iterator() {
        return this.items.listIterator();
    }

    public int countId(int id) {
        for (InvItem i : this.items) {
            if (i.getID() != id) continue;
            return i.getAmount();
        }
        return 0;
    }

    public boolean full() {
        return this.items.size() >= MAX_SIZE;
    }

    public int size() {
        return this.items.size();
    }

    public boolean isGeneral() {
        return this.general;
    }

    public int getSellModifier() {
        return this.sellModifier;
    }

    public int getBuyModifier() {
        return this.buyModifier;
    }

    /** See the field: recorded from Jagex's table, not applied. */
    public int getStockSensitivity() {
        return this.stockSensitivity;
    }

    public int getDeclineOption() {
        return this.declineOption;
    }

    public int getRequiredSlots(List<InvItem> items) {
        int requiredSlots = 0;
        for (InvItem item : items) {
            if (items.contains(item)) continue;
            ++requiredSlots;
        }
        return requiredSlots;
    }

    public int getRequiredSlots(InvItem item) {
        return this.items.contains(item) ? 0 : 1;
    }

    public boolean canHold(InvItem item) {
        return MAX_SIZE - this.items.size() >= this.getRequiredSlots(item);
    }

    public boolean canHold(ArrayList<InvItem> items) {
        return MAX_SIZE - items.size() >= this.getRequiredSlots(items);
    }

    public boolean equals(Object o) {
        if (o instanceof Shop) {
            Shop shop = (Shop)o;
            return shop.getName().equals(this.name);
        }
        return false;
    }
}

