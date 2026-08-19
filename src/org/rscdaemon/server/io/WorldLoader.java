/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.locs.GameObjectLoc;
import org.rscdaemon.server.entityhandling.locs.ItemLoc;
import org.rscdaemon.server.entityhandling.locs.NPCLoc;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Sector;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.PersistenceManager;

public class WorldLoader {
    private ZipFile tileArchive;

    public void loadWorld(World world) {
        try {
            this.tileArchive = new ZipFile(new File(Config.CONF_DIR, "data/Landscape.rscd"));
        }
        catch (Exception e) {
            Logger.error(e);
        }
        for (int lvl = 0; lvl < 4; ++lvl) {
            int wildX = 2304;
            int wildY = 1776 - lvl * 944;
            for (int sx = 0; sx < 1000; sx += 48) {
                for (int sy = 0; sy < 1000; sy += 48) {
                    int x = (sx + wildX) / 48;
                    int y = (sy + lvl * 944 + wildY) / 48;
                    this.loadSection(x, y, lvl, world, sx, sy + 944 * lvl);
                }
            }
        }
        for (GameObjectLoc gameObject : (List<GameObjectLoc>)PersistenceManager.load("locs/GameObjectLoc.xml.gz")) {
            world.registerGameObject(new GameObject(gameObject));
        }
        for (ItemLoc item : (List<ItemLoc>)PersistenceManager.load("locs/ItemLoc.xml.gz")) {
            world.registerItem(new Item(item));
        }
        for (NPCLoc npc : (List<NPCLoc>)PersistenceManager.load("locs/NpcLoc.xml.gz")) {
            world.registerNpc(new Npc(npc));
        }
        for (Shop shop : (List<Shop>)PersistenceManager.load("locs/Shops.xml.gz")) {
            world.registerShop(shop);
        }
        /*
         * The Barbarian Outpost gate (object 311 at 494,543). The fence line
         * runs north-south, so the walk through the gate is EAST-WEST:
         * (494,544) is outside, (493,544) is inside. The landscape never
         * clipped the 494-to-493 edge on the gate's two rows, so anyone could
         * stroll into the outpost without the barcrawl. Wall both rows here
         * the same way a horizontal-wall tile would (bit 2 on the east tile,
         * bit 8 on its western neighbour). BarCrawl's open() teleports the
         * player across, so the gate never needs these edges passable.
         */
        world.getTileValue(494, 543).mapValue |= 2;
        world.getTileValue(493, 543).mapValue |= 8;
        world.getTileValue(494, 544).mapValue |= 2;
        world.getTileValue(493, 544).mapValue |= 8;
        System.gc();
    }

    private void loadSection(int sectionX, int sectionY, int height, World world, int bigX, int bigY) {
        Sector s = null;
        try {
            String filename = "h" + height + "x" + sectionX + "y" + sectionY;
            ZipEntry e = this.tileArchive.getEntry(filename);
            if (e == null) {
                throw new Exception("Missing tile: " + filename);
            }
            ByteBuffer data = DataConversions.streamToBuffer(new BufferedInputStream(this.tileArchive.getInputStream(e)));
            s = Sector.unpack(data);
        }
        catch (Exception e) {
            Logger.error(e);
        }
        for (int y = 0; y < 48; ++y) {
            for (int x = 0; x < 48; ++x) {
                int diagonalWalls;
                int horizontalWall;
                int verticalWall;
                int groundOverlay;
                int bx = bigX + x;
                int by = bigY + y;
                if (!world.withinWorld(bx, by)) continue;
                if ((s.getTile((int)x, (int)y).groundOverlay & 0xFF) == 250) {
                    s.getTile((int)x, (int)y).groundOverlay = (byte)2;
                }
                if ((groundOverlay = s.getTile((int)x, (int)y).groundOverlay & 0xFF) > 0 && EntityHandler.getTileDef(groundOverlay - 1).getObjectType() != 0) {
                    world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 0x40);
                }
                /* Bit 0x80 is "you are indoors", which the server has never
                   had a way to ask. It is not the roof texture, as you would
                   expect -- the client decides it from the floor you are
                   standing on, hiding the roof models whenever the ground
                   overlay's tile definition has unknown == 2. Ten of the
                   twenty-five overlays are marked that way and they are the
                   floorboards and flagstones, so the flag traces the inside
                   of every building in the game.

                   The bit blocks nothing. Every other reader of mapValue
                   masks for the one bit it wants, so it is inert to pathing. */
                if (groundOverlay > 0 && EntityHandler.getTileDef(groundOverlay - 1).getUnknown() == 2) {
                    world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 0x80);
                }
                if ((verticalWall = s.getTile((int)x, (int)y).verticalWall & 0xFF) > 0 && EntityHandler.getDoorDef(verticalWall - 1).getUnknown() == 0 && EntityHandler.getDoorDef(verticalWall - 1).getDoorType() != 0) {
                    world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 1);
                    world.getTileValue((int)bx, (int)(by - 1)).mapValue = (byte)(world.getTileValue((int)bx, (int)(by - 1)).mapValue | 4);
                }
                if ((horizontalWall = s.getTile((int)x, (int)y).horizontalWall & 0xFF) > 0 && EntityHandler.getDoorDef(horizontalWall - 1).getUnknown() == 0 && EntityHandler.getDoorDef(horizontalWall - 1).getDoorType() != 0) {
                    world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 2);
                    world.getTileValue((int)(bx - 1), (int)by).mapValue = (byte)(world.getTileValue((int)(bx - 1), (int)by).mapValue | 8);
                }
                if ((diagonalWalls = s.getTile((int)x, (int)y).diagonalWalls) > 0 && diagonalWalls < 12000 && EntityHandler.getDoorDef(diagonalWalls - 1).getUnknown() == 0 && EntityHandler.getDoorDef(diagonalWalls - 1).getDoorType() != 0) {
                    world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 0x20);
                }
                if (diagonalWalls <= 12000 || diagonalWalls >= 24000 || EntityHandler.getDoorDef(diagonalWalls - 12001).getUnknown() != 0 || EntityHandler.getDoorDef(diagonalWalls - 12001).getDoorType() == 0) continue;
                world.getTileValue((int)bx, (int)by).mapValue = (byte)(world.getTileValue((int)bx, (int)by).mapValue | 0x10);
            }
        }
    }
}

