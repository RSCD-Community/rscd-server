/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Inventory {
    private static World world = World.getWorld();
    public static final int MAX_SIZE = 30;
    private Player player;
    private ArrayList<InvItem> list = new ArrayList();

    public Inventory() {
    }

    public Inventory(Player player) {
        this.player = player;
    }

    public ArrayList<InvItem> getItems() {
        return this.list;
    }

    public boolean wielding(int id) {
        for (InvItem i : this.list) {
            if (i.getID() != id || !i.isWielded()) continue;
            return true;
        }
        return false;
    }

    public int add(InvItem item) {
        if (item.getAmount() <= 0) {
            return -1;
        }
        if (item.getDef().isStackable()) {
            for (int index = 0; index < this.list.size(); ++index) {
                if (!item.equals(this.list.get(index))) continue;
                this.list.get(index).setAmount(this.list.get(index).getAmount() + item.getAmount());
                return index;
            }
        } else if (item.getAmount() > 1) {
            item.setAmount(1);
        }
        if (this.full()) {
            this.player.getActionSender().sendMessage("Your Inventory is full, the " + item.getDef().getName() + " drops to the ground!");
            world.registerItem(new Item(item.getID(), this.player.getX(), this.player.getY(), item.getAmount(), this.player));
            return -1;
        }
        this.list.add(item);
        return this.list.size() - 2;
    }

    /**
     * Takes <code>amount</code> of item <code>id</code> out of the inventory and
     * returns the highest slot it touched, or -1 if nothing was taken.
     *
     * A non-stackable item occupies one slot per unit, so removing several of
     * them means removing several slots. The old implementation returned after
     * the first match whatever the amount, which quietly charged a player one
     * ball of wool where twenty were asked for (and one hide, one rum, one of
     * every non-stackable ingredient) while still reporting success.
     *
     * It is all-or-nothing on purpose. Every caller reads the result as a
     * success flag, so a partial take that reports success is exactly how a
     * player pays less than they should -- or more than they have.
     */
    public int remove(int id, int amount) {
        if (amount <= 0 || this.countId(id) < amount) {
            return -1;
        }
        int size = this.list.size();
        ListIterator<InvItem> iterator = this.list.listIterator(size);
        int index = size - 1;
        int outstanding = amount;
        int highest = -1;
        while (iterator.hasPrevious() && outstanding > 0) {
            InvItem i = iterator.previous();
            if (id == i.getID()) {
                if (highest < 0) {
                    highest = index;
                }
                if (i.getDef().isStackable() && outstanding < i.getAmount()) {
                    i.setAmount(i.getAmount() - outstanding);
                    outstanding = 0;
                } else {
                    outstanding -= i.getDef().isStackable() ? i.getAmount() : 1;
                    if (i.isWielded()) {
                        this.player.getActionSender().sendSound("click");
                        i.setWield(false);
                        this.player.updateWornItems(i.getWieldableDef().getWieldPos(), this.player.getPlayerAppearance().getSprite(i.getWieldableDef().getWieldPos()));
                        this.player.getActionSender().sendEquipmentStats();
                    }
                    iterator.remove();
                }
            }
            --index;
        }
        return highest;
    }

    public int remove(InvItem item) {
        return this.remove(item.getID(), item.getAmount());
    }

    public void remove(int index) {
        InvItem item = this.get(index);
        if (item == null) {
            return;
        }
        this.remove(item.getID(), item.getAmount());
    }

    public void sort() {
        Collections.sort(this.list);
    }

    public ListIterator<InvItem> iterator() {
        return this.list.listIterator();
    }

    public int getLastIndexById(int id) {
        for (int index = this.list.size() - 1; index >= 0; --index) {
            if (this.list.get(index).getID() != id) continue;
            return index;
        }
        return -1;
    }

    public int countId(int id) {
        int temp = 0;
        for (InvItem i : this.list) {
            if (i.getID() != id) continue;
            temp += i.getAmount();
        }
        return temp;
    }

    public boolean full() {
        return this.list.size() >= 30;
    }

    public boolean contains(InvItem i) {
        return this.list.contains(i);
    }

    public InvItem get(InvItem item) {
        for (int index = this.list.size() - 1; index >= 0; --index) {
            if (!this.list.get(index).equals(item)) continue;
            return this.list.get(index);
        }
        return null;
    }

    public InvItem get(int index) {
        if (index < 0 || index >= this.list.size()) {
            return null;
        }
        return this.list.get(index);
    }

    public int size() {
        return this.list.size();
    }

    public int getFreedSlots(List<InvItem> items) {
        int freedSlots = 0;
        for (InvItem item : items) {
            freedSlots += this.getFreedSlots(item);
        }
        return freedSlots;
    }

    public int getFreedSlots(InvItem item) {
        return item.getDef().isStackable() && this.countId(item.getID()) > item.getAmount() ? 0 : 1;
    }

    public int getRequiredSlots(List<InvItem> items) {
        int requiredSlots = 0;
        for (InvItem item : items) {
            requiredSlots += this.getRequiredSlots(item);
        }
        return requiredSlots;
    }

    public int getRequiredSlots(InvItem item) {
        return item.getDef().isStackable() && this.list.contains(item) ? 0 : 1;
    }

    public boolean canHold(InvItem item) {
        return 30 - this.list.size() >= this.getRequiredSlots(item);
    }
}

