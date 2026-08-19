/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.DoorDef;
import org.rscdaemon.server.entityhandling.defs.GameObjectDef;
import org.rscdaemon.server.entityhandling.locs.GameObjectLoc;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.Point;

public class GameObject
extends Entity {
    private int direction;
    private int type;
    private GameObjectLoc loc = null;
    private boolean removed = false;

    public GameObject(GameObjectLoc loc) {
        this.direction = loc.direction;
        this.type = loc.type;
        this.loc = loc;
        super.setID(loc.id);
        super.setLocation(Point.location(loc.x, loc.y));
    }

    public GameObject(Point location, int id, int direction, int type) {
        this(new GameObjectLoc(id, location.getX(), location.getY(), direction, type));
    }

    public boolean isRemoved() {
        return this.removed;
    }

    public void remove() {
        this.removed = true;
    }

    public GameObjectLoc getLoc() {
        return this.loc;
    }

    public GameObjectDef getGameObjectDef() {
        return EntityHandler.getGameObjectDef(super.getID());
    }

    public DoorDef getDoorDef() {
        return EntityHandler.getDoorDef(super.getID());
    }

    public boolean isTelePoint() {
        return EntityHandler.getObjectTelePoint(this.getLocation(), null) != null;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getDirection() {
        return this.direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public boolean equals(Object o) {
        if (o instanceof GameObject) {
            GameObject go = (GameObject)o;
            return go.getLocation().equals(this.getLocation()) && go.getID() == this.getID() && go.getDirection() == this.getDirection() && go.getType() == this.getType();
        }
        return false;
    }

    public boolean isOn(int x, int y) {
        int width;
        int height;
        if (this.type == 1) {
            height = 1;
            width = 1;
        } else if (this.direction == 0 || this.direction == 4) {
            width = this.getGameObjectDef().getWidth();
            height = this.getGameObjectDef().getHeight();
        } else {
            height = this.getGameObjectDef().getWidth();
            width = this.getGameObjectDef().getHeight();
        }
        if (this.type == 0) {
            return x >= this.getX() && x <= this.getX() + width && y >= this.getY() && y <= this.getY() + height;
        }
        return x == this.getX() && y == this.getY();
    }

    public String toString() {
        return (this.type == 0 ? "GameObject" : "WallObject") + ":id = " + this.id + "; dir = " + this.direction + "; location = " + this.location.toString() + ";";
    }
}

