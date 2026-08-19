/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.List;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class ViewArea {
    private static World world = World.getWorld();
    private Mob mob;

    public ViewArea(Mob mob) {
        this.mob = mob;
    }

    public ActiveTile[][] getViewedArea(int x1, int y1, int x2, int y2) {
        int endY;
        int endX;
        int startY;
        int mobX = this.mob.getX();
        int mobY = this.mob.getY();
        int startX = mobX - x1;
        if (startX < 0) {
            startX = 0;
        }
        if ((startY = mobY - y1) < 0) {
            startY = 0;
        }
        if ((endX = mobX + x2) >= 944) {
            endX = 943;
        }
        if ((endY = mobY + y2) >= 3776) {
            endY = 3775;
        }
        int xWidth = startX > endX ? startX - endX : endX - startX;
        int yWidth = startY > endY ? startY - endY : endY - startY;
        ActiveTile[][] temp = new ActiveTile[xWidth][yWidth];
        int x = 0;
        while (x + startX < endX) {
            int y = 0;
            while (y + startY < endY) {
                temp[x][y] = ViewArea.world.tiles[x + startX][y + startY];
                ++y;
            }
            ++x;
        }
        return temp;
    }

    public List<Player> getPlayersInView() {
        ArrayList<Player> players = new ArrayList<Player>();
        ActiveTile[][] viewArea = this.getViewedArea(15, 15, 16, 16);
        for (int x = 0; x < viewArea.length; ++x) {
            for (int y = 0; y < viewArea[x].length; ++y) {
                List<Player> temp;
                ActiveTile t = viewArea[x][y];
                if (t == null || (temp = t.getPlayers()) == null) continue;
                players.addAll(temp);
            }
        }
        return players;
    }

    public List<Item> getItemsInView() {
        ArrayList<Item> items = new ArrayList<Item>();
        ActiveTile[][] viewArea = this.getViewedArea(21, 21, 21, 21);
        for (int x = 0; x < viewArea.length; ++x) {
            for (int y = 0; y < viewArea[x].length; ++y) {
                ActiveTile t = viewArea[x][y];
                if (t == null) continue;
                items.addAll(t.getItems());
            }
        }
        return items;
    }

    public List<GameObject> getGameObjectsInView() {
        ArrayList<GameObject> objects = new ArrayList<GameObject>();
        ActiveTile[][] viewArea = this.getViewedArea(21, 21, 21, 21);
        for (int x = 0; x < viewArea.length; ++x) {
            for (int y = 0; y < viewArea[x].length; ++y) {
                ActiveTile t = viewArea[x][y];
                if (t == null) continue;
                if (t.hasGameObject()) {
                    objects.add(t.getGameObject());
                }
                if (t.hasDoor()) {
                    objects.add(t.getDoor());
                }
                if (t.getSecondDoor() != null) {
                    objects.add(t.getSecondDoor());
                }
            }
        }
        return objects;
    }

    public List<Npc> getNpcsInView() {
        ArrayList<Npc> npcs = new ArrayList<Npc>();
        ActiveTile[][] viewArea = this.getViewedArea(15, 15, 16, 16);
        for (int x = 0; x < viewArea.length; ++x) {
            for (int y = 0; y < viewArea[x].length; ++y) {
                List<Npc> temp;
                ActiveTile t = viewArea[x][y];
                if (t == null || (temp = t.getNpcs()) == null) continue;
                npcs.addAll(temp);
            }
        }
        return npcs;
    }
}

