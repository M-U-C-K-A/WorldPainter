package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatSliderUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import org.pepsoft.worldpainter.design.Glass;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The sliders of the Glass theme: a thin track, filled with the accent up to the value, and a thumb of liquid glass
 * through which the track is seen magnified, as through a lens.
 */
public class GlassSliderUI extends FlatSliderUI {
    public static ComponentUI createUI(JComponent c) {
        return new GlassSliderUI();
    }

    @Override
    public void paintTrack(Graphics g) {
        if (slider.getOrientation() != JSlider.HORIZONTAL) {
            super.paintTrack(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            paintBar(g2);
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }

    private void paintBar(Graphics2D g2) {
        final float height = UIScale.scale(6f), y = trackRect.y + (trackRect.height - height) / 2f;
        final Shape bar = new RoundRectangle2D.Float(trackRect.x, y, trackRect.width, height, height, height);
        g2.setColor(LiquidGlass.colour("WorldPainter.glass.track"));
        g2.fill(bar);
        final float valueX = xPositionForValue(slider.getValue());
        final Shape savedClip = g2.getClip();
        g2.clip(new Rectangle.Float(trackRect.x - height, y - 1, valueX - trackRect.x + height, height + 2));
        g2.setColor(slider.isEnabled() ? UIManager.getColor("Component.accentColor") : LiquidGlass.colour("WorldPainter.glass.track"));
        g2.fill(bar);
        g2.setClip(savedClip);
    }

    @Override
    public void paintThumb(Graphics g) {
        if (slider.getOrientation() != JSlider.HORIZONTAL) {
            super.paintThumb(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final float x = thumbRect.x, width = thumbRect.width, height = Math.min(thumbRect.height, UIScale.scale(22f));
            final float y = thumbRect.y + (thumbRect.height - height) / 2f;
            final Shape capsule = new RoundRectangle2D.Float(x, y, width, height, height, height);
            LiquidGlass.paintShadow(g2, capsule);
            // The track seen through the thumb, magnified as through a lens
            final Graphics2D lens = (Graphics2D) g2.create();
            try {
                lens.clip(capsule);
                lens.setColor(LiquidGlass.colour("WorldPainter.glass.lensBackground"));
                lens.fill(capsule);
                final double centreX = x + width / 2.0, centreY = y + height / 2.0, zoom = isDragging() ? 1.8 : 1.5;
                lens.translate(centreX, centreY);
                lens.scale(zoom, zoom);
                lens.translate(-centreX, -centreY);
                paintBar(lens);
            } finally {
                lens.dispose();
            }
            Glass.paintLiquid(g2, capsule, null);
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }
}
