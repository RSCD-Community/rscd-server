/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.locs;

public class NPCLoc {
    public int id;
    public int startX;
    public int minX;
    public int maxX;
    public int startY;
    public int minY;
    public int maxY;

    /*
     * For the definition reader (util/XmlObjects), which fills the fields
     * afterwards. XStream never called a constructor at all -- it allocated
     * through sun.reflect.ReflectionFactory, which is exactly the JDK internal
     * this project is getting off -- so a constructor that does nothing
     * reproduces the old behaviour precisely: every field starts at its default
     * and the document sets it.
     */
    public NPCLoc() {
    }

    public NPCLoc(int id, int startX, int startY, int minX, int maxX, int minY, int maxY) {
        this.id = id;
        this.startX = startX;
        this.startY = startY;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public int getId() {
        return this.id;
    }

    public int startX() {
        return this.startX;
    }

    public int minX() {
        return this.minX;
    }

    public int maxX() {
        return this.maxX;
    }

    public int startY() {
        return this.startY;
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }
}

