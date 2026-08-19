/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.rscdaemon.server.model.InvItem;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Bank {
    public static final int MAX_SIZE = 192;
    private ArrayList<InvItem> list = new ArrayList();

    public ArrayList<InvItem> getItems() {
        return this.list;
    }

    public int add(InvItem item) {
        if (item.getAmount() <= 0) {
            return -1;
        }
        for (int index = 0; index < this.list.size(); ++index) {
            if (!item.equals(this.list.get(index))) continue;
            this.list.get(index).setAmount(this.list.get(index).getAmount() + item.getAmount());
            return index;
        }
        this.list.add(item);
        return this.list.size() - 2;
    }

    public int remove(int id, int amount) {
        Iterator<InvItem> iterator = this.list.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            InvItem i = iterator.next();
            if (id == i.getID()) {
                if (amount < i.getAmount()) {
                    i.setAmount(i.getAmount() - amount);
                } else {
                    iterator.remove();
                }
                return index;
            }
            ++index;
        }
        return -1;
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

    public ListIterator<InvItem> iterator() {
        return this.list.listIterator();
    }

    public int getFirstIndexById(int id) {
        for (int index = 0; index < this.list.size(); ++index) {
            if (this.list.get(index).getID() != id) continue;
            return index;
        }
        return -1;
    }

    public int countId(int id) {
        for (InvItem i : this.list) {
            if (i.getID() != id) continue;
            return i.getAmount();
        }
        return 0;
    }

    public boolean full() {
        return this.list.size() >= 192;
    }

    public boolean contains(InvItem i) {
        return this.list.contains(i);
    }

    public InvItem get(InvItem item) {
        for (InvItem i : this.list) {
            if (!item.equals(i)) continue;
            return i;
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

    public int getRequiredSlots(List<InvItem> items) {
        int requiredSlots = 0;
        for (InvItem item : items) {
            if (this.list.contains(item)) continue;
            ++requiredSlots;
        }
        return requiredSlots;
    }

    public int getRequiredSlots(InvItem item) {
        return this.list.contains(item) ? 0 : 1;
    }

    public boolean canHold(InvItem item) {
        return 192 - this.list.size() >= this.getRequiredSlots(item);
    }

    public boolean canHold(ArrayList<InvItem> items) {
        return 192 - this.list.size() >= this.getRequiredSlots(items);
    }
}

