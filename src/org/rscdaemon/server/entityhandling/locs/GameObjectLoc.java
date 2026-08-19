/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.locs;

public class GameObjectLoc {
    public int id;
    public int x;
    public int y;
    public int direction;
    public int type;

    /*
     * For the definition reader (util/XmlObjects), which fills the fields
     * afterwards. XStream never called a constructor at all -- it allocated
     * through sun.reflect.ReflectionFactory, which is exactly the JDK internal
     * this project is getting off -- so a constructor that does nothing
     * reproduces the old behaviour precisely: every field starts at its default
     * and the document sets it.
     */
    public GameObjectLoc() {
    }

    public GameObjectLoc(int id, int x, int y, int direction, int type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.type = type;
    }

    public int getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getDirection() {
        return this.direction;
    }

    public int getType() {
        return this.type;
    }
}

