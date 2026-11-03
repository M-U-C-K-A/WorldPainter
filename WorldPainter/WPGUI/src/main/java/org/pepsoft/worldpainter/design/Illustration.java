package org.pepsoft.worldpainter.design;

import java.awt.*;
import java.awt.geom.*;

/**
 * Small drawings at the top of a window which say at a glance what it is about: a cross section of the ground for the
 * properties of the land, a map turning into a block for exporting, and so on.
 */
public enum Illustration {
    SETTINGS, GROUND, EXPORT, WORLD, IMPORT, TRANSFORM, MERGE, VIEW, LAYERS;

    /**
     * Draw the illustration in a rectangle.
     *
     * @param accent The colour of the window it is for.
     */
    public void paint(Graphics2D g2, int x, int y, int w, int h, Color accent) {
        final Graphics2D g = (Graphics2D) g2.create(x, y, w, h);
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.clipRect(0, 0, w, h);
            switch (this) {
                case SETTINGS -> settings(g, w, h, accent);
                case GROUND -> ground(g, w, h);
                case EXPORT -> export(g, w, h, accent);
                case WORLD -> world(g, w, h);
                case IMPORT -> importing(g, w, h, accent);
                case TRANSFORM -> transform(g, w, h, accent);
                case MERGE -> merge(g, w, h, accent);
                case VIEW -> view(g, w, h, accent);
                case LAYERS -> layers(g, w, h, accent);
            }
        } finally {
            g.dispose();
        }
    }

    private static void settings(Graphics2D g, int w, int h, Color accent) {
        final Color track = Design.mix(Design.background(), Design.foreground(), 0.25f);
        final float[] positions = {0.7f, 0.3f, 0.55f};
        for (int i = 0; i < 3; i++) {
            final float y = h * (0.22f + 0.28f * i);
            g.setColor(track);
            g.fill(new Rectangle2D.Float(w * 0.08f, y - 1.5f, w * 0.84f, 3));
            g.setColor(accent);
            g.fill(new Rectangle2D.Float(w * 0.08f, y - 1.5f, w * 0.84f * positions[i], 3));
            final float knob = h * 0.17f;
            g.setColor(Design.background());
            g.fill(new Rectangle2D.Float(w * 0.08f + w * 0.84f * positions[i] - knob / 2, y - knob / 2, knob, knob));
            g.setColor(accent);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Rectangle2D.Float(w * 0.08f + w * 0.84f * positions[i] - knob / 2, y - knob / 2, knob, knob));
        }
    }

    private static void ground(Graphics2D g, int w, int h) {
        // A cross section of the ground: grass, dirt, stone with layers, deepslate, bedrock, a cave with some ore
        final Path2D line = new Path2D.Float();
        line.moveTo(0, h * 0.24f);
        line.curveTo(w * 0.25f, h * 0.08f, w * 0.45f, h * 0.34f, w * 0.7f, h * 0.18f);
        line.curveTo(w * 0.82f, h * 0.1f, w * 0.92f, h * 0.2f, w, h * 0.16f);
        final Path2D surface = new Path2D.Float(line);
        surface.lineTo(w, h);
        surface.lineTo(0, h);
        surface.closePath();
        g.setColor(new Color(0x7d7d82));
        g.fill(surface);
        g.setClip(surface);
        g.setColor(new Color(0x8b5e3c));
        g.fill(band(surface, h * 0.07f, h * 0.3f));
        g.setColor(new Color(0x5b9a3c));
        g.fill(band(surface, 0, h * 0.07f));
        g.setColor(new Color(0x6c6c71));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 3; i++) {
            final float y = h * (0.48f + 0.09f * i);
            g.draw(new Line2D.Float(0, y + i * 2, w, y - 2));
        }
        g.setColor(new Color(0x4d4d55));
        g.fill(new Rectangle2D.Float(0, h * 0.74f, w, h * 0.26f));
        g.setColor(new Color(0x26262b));
        final Path2D bedrock = new Path2D.Float();
        bedrock.moveTo(0, h);
        for (int i = 0; i <= 12; i++) {
            bedrock.lineTo(w * i / 12f, h * ((i % 2 == 0) ? 0.88f : 0.93f));
        }
        bedrock.lineTo(w, h);
        bedrock.closePath();
        g.fill(bedrock);
        g.setColor(new Color(0x2f2f34));
        g.fill(new Ellipse2D.Float(w * 0.18f, h * 0.5f, w * 0.26f, h * 0.17f));
        g.setColor(new Color(0xe6c34a));
        g.fill(new Rectangle2D.Float(w * 0.62f, h * 0.55f, 3, 3));
        g.fill(new Rectangle2D.Float(w * 0.66f, h * 0.6f, 3, 3));
        g.setColor(new Color(0x4fd6d6));
        g.fill(new Rectangle2D.Float(w * 0.8f, h * 0.8f, 3, 3));
        g.setClip(null);
        g.setColor(new Color(0x3f7a2a));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(line);
    }

    /**
     * The part of the ground between two depths below its surface.
     */
    private static Shape band(Path2D surface, float from, float to) {
        final Area band = new Area(AffineTransform.getTranslateInstance(0, from).createTransformedShape(surface));
        band.subtract(new Area(AffineTransform.getTranslateInstance(0, to).createTransformedShape(surface)));
        return band;
    }

    private static void export(Graphics2D g, int w, int h, Color accent) {
        // A map seen from above, an arrow, and a block of grass
        final float size = h * 0.7f, top = h * 0.15f;
        mapTile(g, w * 0.06f, top, size);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        final float arrowY = h * 0.5f, arrowX0 = w * 0.06f + size + w * 0.05f, arrowX1 = w * 0.58f;
        g.draw(new Line2D.Float(arrowX0, arrowY, arrowX1, arrowY));
        g.draw(new Line2D.Float(arrowX1 - 6, arrowY - 6, arrowX1, arrowY));
        g.draw(new Line2D.Float(arrowX1 - 6, arrowY + 6, arrowX1, arrowY));
        cube(g, w * 0.78f, h * 0.5f, h * 0.36f);
    }

    private static void mapTile(Graphics2D g, float x, float y, float size) {
        g.setColor(new Color(0x3b82c4));
        g.fill(new Rectangle2D.Float(x, y, size, size));
        final Path2D land = new Path2D.Float();
        land.moveTo(x + size * 0.15f, y + size * 0.3f);
        land.curveTo(x + size * 0.4f, y + size * 0.02f, x + size * 0.95f, y + size * 0.15f, x + size * 0.85f, y + size * 0.55f);
        land.curveTo(x + size * 0.78f, y + size * 0.95f, x + size * 0.25f, y + size * 0.92f, x + size * 0.15f, y + size * 0.3f);
        g.setColor(new Color(0x5b9a3c));
        g.fill(land);
        g.setColor(new Color(0x86c06c));
        g.fill(new Ellipse2D.Float(x + size * 0.42f, y + size * 0.35f, size * 0.25f, size * 0.22f));
        g.setColor(new Color(0x2a2a2e));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Float(x, y, size, size));
    }

    private static void cube(Graphics2D g, float cx, float cy, float r) {
        final float half = r * 0.87f;
        final Path2D top = new Path2D.Float(), left = new Path2D.Float(), right = new Path2D.Float();
        top.moveTo(cx, cy - r);
        top.lineTo(cx + half, cy - r / 2);
        top.lineTo(cx, cy);
        top.lineTo(cx - half, cy - r / 2);
        top.closePath();
        left.moveTo(cx - half, cy - r / 2);
        left.lineTo(cx, cy);
        left.lineTo(cx, cy + r);
        left.lineTo(cx - half, cy + r / 2);
        left.closePath();
        right.moveTo(cx + half, cy - r / 2);
        right.lineTo(cx, cy);
        right.lineTo(cx, cy + r);
        right.lineTo(cx + half, cy + r / 2);
        right.closePath();
        g.setColor(new Color(0x6aa84f));
        g.fill(top);
        g.setColor(new Color(0x8b5e3c));
        g.fill(left);
        g.setColor(new Color(0x6f4a2f));
        g.fill(right);
    }

    private static void world(Graphics2D g, int w, int h) {
        final float size = Math.min(w, h) * 0.86f;
        final float x = (w - size) / 2, y = (h - size) / 2;
        mapTile(g, x, y, size);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Float(x + size * 0.35f, y + size * 0.28f, size * 0.4f, size * 0.38f));
        g.draw(new Ellipse2D.Float(x + size * 0.28f, y + size * 0.2f, size * 0.55f, size * 0.55f));
    }

    private static void importing(Graphics2D g, int w, int h, Color accent) {
        // A height map in shades of grey, an arrow, and the relief it becomes
        final float size = h * 0.7f, x = w * 0.06f, y = h * 0.15f;
        final int cells = 8;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                final double v = 0.5 + 0.5 * Math.sin(i * 0.8) * Math.cos(j * 0.7 + 0.4);
                final int grey = (int) (50 + 180 * v);
                g.setColor(new Color(grey, grey, grey));
                g.fill(new Rectangle2D.Float(x + i * size / cells, y + j * size / cells, size / cells + 0.5f, size / cells + 0.5f));
            }
        }
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        final float arrowY = h * 0.5f, arrowX0 = x + size + w * 0.05f, arrowX1 = w * 0.52f;
        g.draw(new Line2D.Float(arrowX0, arrowY, arrowX1, arrowY));
        g.draw(new Line2D.Float(arrowX1 - 6, arrowY - 6, arrowX1, arrowY));
        g.draw(new Line2D.Float(arrowX1 - 6, arrowY + 6, arrowX1, arrowY));
        final Path2D relief = new Path2D.Float();
        relief.moveTo(w * 0.58f, h * 0.85f);
        relief.lineTo(w * 0.68f, h * 0.45f);
        relief.lineTo(w * 0.74f, h * 0.6f);
        relief.lineTo(w * 0.84f, h * 0.18f);
        relief.lineTo(w * 0.96f, h * 0.85f);
        relief.closePath();
        g.setColor(new Color(0x7d7d82));
        g.fill(relief);
        g.setColor(new Color(0xf1f5f9));
        final Path2D snow = new Path2D.Float();
        snow.moveTo(w * 0.84f, h * 0.18f);
        snow.lineTo(w * 0.8f, h * 0.34f);
        snow.lineTo(w * 0.875f, h * 0.34f);
        snow.closePath();
        g.fill(snow);
    }

    private static void transform(Graphics2D g, int w, int h, Color accent) {
        final float size = h * 0.5f;
        g.setColor(Design.mix(Design.background(), Design.foreground(), 0.35f));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
        g.draw(new Rectangle2D.Float(w * 0.18f, h * 0.3f, size, size));
        mapTile(g, w * 0.42f, h * 0.12f, h * 0.72f);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Float(w * 0.18f + size, h * 0.3f + size, w * 0.42f, h * 0.84f));
    }

    private static void merge(Graphics2D g, int w, int h, Color accent) {
        mapTile(g, w * 0.1f, h * 0.12f, h * 0.55f);
        g.setColor(new Color(0x7d7d82));
        g.fill(new Rectangle2D.Float(w * 0.3f, h * 0.33f, h * 0.55f, h * 0.55f));
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Float(w * 0.62f, h * 0.5f, w * 0.78f, h * 0.5f));
        g.draw(new Line2D.Float(w * 0.72f, h * 0.44f, w * 0.78f, h * 0.5f));
        g.draw(new Line2D.Float(w * 0.72f, h * 0.56f, w * 0.78f, h * 0.5f));
        cube(g, w * 0.9f, h * 0.5f, h * 0.24f);
    }

    private static void view(Graphics2D g, int w, int h, Color accent) {
        mapTile(g, w * 0.08f, h * 0.12f, h * 0.76f);
        g.setColor(new Color(255, 255, 255, 120));
        g.setStroke(new BasicStroke(1f));
        final float size = h * 0.76f, x = w * 0.08f, y = h * 0.12f;
        for (int i = 1; i < 4; i++) {
            g.draw(new Line2D.Float(x + size * i / 4, y, x + size * i / 4, y + size));
            g.draw(new Line2D.Float(x, y + size * i / 4, x + size, y + size * i / 4));
        }
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f));
        final float ex = w * 0.72f, ey = h * 0.5f;
        g.draw(new Arc2D.Float(ex - w * 0.16f, ey - h * 0.3f, w * 0.32f, h * 0.6f, 25, 130, Arc2D.OPEN));
        g.draw(new Arc2D.Float(ex - w * 0.16f, ey - h * 0.3f, w * 0.32f, h * 0.6f, 205, 130, Arc2D.OPEN));
        g.fill(new Ellipse2D.Float(ex - h * 0.12f, ey - h * 0.12f, h * 0.24f, h * 0.24f));
    }

    private static void layers(Graphics2D g, int w, int h, Color accent) {
        final Color[] colours = {Design.mix(accent, Color.BLACK, 0.3f), accent, Design.mix(accent, Color.WHITE, 0.35f)};
        for (int i = 0; i < 3; i++) {
            final float cy = h * (0.72f - 0.2f * i);
            final Path2D layer = new Path2D.Float();
            layer.moveTo(w * 0.5f, cy - h * 0.2f);
            layer.lineTo(w * 0.85f, cy);
            layer.lineTo(w * 0.5f, cy + h * 0.2f);
            layer.lineTo(w * 0.15f, cy);
            layer.closePath();
            g.setColor(colours[i]);
            g.fill(layer);
            g.setColor(Design.background());
            g.setStroke(new BasicStroke(1.5f));
            g.draw(layer);
        }
    }
}
