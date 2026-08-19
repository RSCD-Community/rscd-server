/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

public class Path {
    private int startX;
    private int startY;
    private byte[] waypointXoffsets;
    private byte[] waypointYoffsets;

    public Path(int startX, int startY, byte[] waypointXoffsets, byte[] waypointYoffsets) {
        this.startX = startX;
        this.startY = startY;
        this.waypointXoffsets = waypointXoffsets;
        this.waypointYoffsets = waypointYoffsets;
    }

    public Path(int x, int y, int endX, int endY) {
        this.startX = endX;
        this.startY = endY;
        this.waypointXoffsets = new byte[0];
        this.waypointYoffsets = new byte[0];
    }

    public int getStartX() {
        return this.startX;
    }

    public int getStartY() {
        return this.startY;
    }

    public int length() {
        if (this.waypointXoffsets == null) {
            return 0;
        }
        return this.waypointXoffsets.length;
    }

    public int getWaypointX(int wayPoint) {
        return this.startX + this.getWaypointXoffset(wayPoint);
    }

    public int getWaypointY(int wayPoint) {
        return this.startY + this.getWaypointYoffset(wayPoint);
    }

    public byte getWaypointXoffset(int wayPoint) {
        if (wayPoint >= this.length()) {
            return 0;
        }
        return this.waypointXoffsets[wayPoint];
    }

    public byte getWaypointYoffset(int wayPoint) {
        if (wayPoint >= this.length()) {
            return 0;
        }
        return this.waypointYoffsets[wayPoint];
    }
}

