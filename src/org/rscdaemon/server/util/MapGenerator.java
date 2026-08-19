/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.TileValue;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.util.Config;

public class MapGenerator {
    private static final World world = World.getWorld();
    private static final String[] labels = new String[]{"Ground", "Level-1", "Level-2", "Underground"};
    private static final int WIDTH = 1888;
    private static final int HEIGHT = 7552;
    private static final int RED = new Color(255, 0, 0).getRGB();
    private static final int BLUE = new Color(0, 0, 255).getRGB();
    private static final int BLACK = new Color(0, 0, 0).getRGB();
    private static final int PURPLE = new Color(150, 0, 255).getRGB();
    private BufferedImage image = new BufferedImage(1888, 7552, 2);
    private Graphics gfx = this.image.getGraphics();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MapGenerator <output.png> [conf/server/Conf.xml]");
            return;
        }
        String configFile = "conf/server/Conf.xml";
        if (args.length > 1) {
            File f = new File(args[1]);
            // getPath(), not getName(): see Server.main. The directory used to
            // be discarded here too, so any config outside the working
            // directory silently fell back to the compiled-in defaults.
            if (f.isFile()) {
                configFile = f.getPath();
            } else {
                System.out.println("No config file at '" + args[1] + "'; trying "
                        + configFile + " instead");
            }
        }
        Config.initConfig(configFile);
        MapGenerator mapGen = new MapGenerator();
        mapGen.generate();
        mapGen.save(args[0]);
    }

    private void drawDot(int xCoord, int yCoord, int colour) {
        this.image.setRGB(1888 - xCoord - 1, yCoord, colour);
    }

    private void fillTile(int xCoord, int yCoord, int colour) {
        for (int xOff = 0; xOff < 2; ++xOff) {
            for (int yOff = 0; yOff < 2; ++yOff) {
                this.drawDot(xCoord + xOff, yCoord + yOff, colour);
            }
        }
    }

    public void generate() {
        this.gfx.fillRect(0, 0, 1888, 7552);
        int label = 0;
        for (int x = 0; x < 1888; x += 2) {
            for (int y = 0; y < 7552; y += 2) {
                if (y % 1888 == 0) {
                    if (x != 0) continue;
                    this.gfx.setColor(Color.GREEN);
                    this.gfx.drawLine(0, y, 1888, y);
                    this.gfx.drawLine(0, y + 1, 1888, y + 1);
                    this.gfx.drawString(labels[label++], x + 10, y + 20);
                    continue;
                }
                this.handleTile(x, y, world.getTileValue(x / 2, y / 2));
                ActiveTile t = MapGenerator.world.tiles[x / 2][y / 2];
                if (t == null) continue;
                if (t.hasNpcs()) {
                    this.fillTile(x, y, RED);
                }
                if (t.hasItems()) {
                    this.fillTile(x, y, PURPLE);
                }
                if (!t.hasGameObject() && !t.hasDoor()) continue;
                this.fillTile(x, y, BLACK);
            }
        }
    }

    private void handleTile(int xImg, int yImg, TileValue tile) {
        this.handleTile(xImg, yImg, tile.mapValue);
        this.handleTile(xImg, yImg, tile.objectValue);
    }

    private void handleTile(int xImg, int yImg, byte type) {
        if ((type & 1) != 0) {
            this.drawDot(xImg, yImg, BLACK);
            this.drawDot(xImg + 1, yImg, BLACK);
        }
        if ((type & 2) != 0) {
            this.drawDot(xImg, yImg, BLACK);
            this.drawDot(xImg, yImg + 1, BLACK);
        }
        if ((type & 4) != 0) {
            this.drawDot(xImg, yImg + 1, BLACK);
            this.drawDot(xImg + 1, yImg + 1, BLACK);
        }
        if ((type & 8) != 0) {
            this.drawDot(xImg + 1, yImg, BLACK);
            this.drawDot(xImg + 1, yImg + 1, BLACK);
        }
        if ((type & 0x10) != 0) {
            this.drawDot(xImg + 1, yImg, BLACK);
            this.drawDot(xImg, yImg + 1, BLACK);
        }
        if ((type & 0x20) != 0) {
            this.drawDot(xImg, yImg, BLACK);
            this.drawDot(xImg + 1, yImg + 1, BLACK);
        }
        if ((type & 0x40) != 0) {
            this.fillTile(xImg, yImg, BLUE);
        }
    }

    public boolean save(String filename) {
        try {
            File file = new File(filename);
            ImageIO.write((RenderedImage)this.image, "png", file);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
}

