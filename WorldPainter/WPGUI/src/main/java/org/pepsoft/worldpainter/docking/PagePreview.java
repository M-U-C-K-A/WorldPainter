package org.pepsoft.worldpainter.docking;

import org.pepsoft.worldpainter.help.ToolCard;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * A picture of a page for the card of the editor type menu: the page itself, drawn as it is now and in the colours of
 * the current theme. A page which is shown somewhere is drawn at the size it has there; one which is not is first laid
 * out at the size of a side panel. Panels are shown from the top, as they would appear in an area; wide pages (the
 * map, the 3D view) are scaled down to fit and shown from their middle.
 */
final class PagePreview implements ToolCard.Preview {
    PagePreview(DockableFrame page) {
        this.page = page;
    }

    @Override
    public void paint(Graphics2D g2, int width, int height, double time) {
        if (image == null) {
            image = render(width, height);
        }
        if (image != null) {
            g2.drawImage(image, 0, 0, null);
        }
    }

    @Override
    public boolean isAnimated() {
        return false;
    }

    private BufferedImage render(int width, int height) {
        int pageWidth = page.getWidth(), pageHeight = page.getHeight();
        if ((! page.isShowing()) || (pageWidth <= 0) || (pageHeight <= 0)) {
            if (page.getParent() != null) {
                // Somewhere it is not shown, such as a window being closed; better no picture than a disturbed layout
                return null;
            }
            pageWidth = Math.round(PANEL_WIDTH * getUIScale());
            pageHeight = Math.round(PANEL_HEIGHT * getUIScale());
            page.setSize(pageWidth, pageHeight);
            // Without a window validate() does nothing, so lay the components out by hand
            layOut(page);
        }
        final BufferedImage full = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D pageGraphics = full.createGraphics();
        try {
            final Color background = UIManager.getColor("Panel.background");
            pageGraphics.setColor((background != null) ? background : Color.GRAY);
            pageGraphics.fillRect(0, 0, pageWidth, pageHeight);
            page.printAll(pageGraphics);
        } catch (RuntimeException e) {
            // Some pages cannot be drawn outside a window; show the card without a picture
            return null;
        } finally {
            pageGraphics.dispose();
        }
        // Panels at their real size, cut off at the bottom; wide pages scaled down to fit, around their middle
        final double scale = Math.min(1.0, (double) width / pageWidth);
        final int scaledHeight = (int) Math.round(pageHeight * scale);
        final int top = (pageWidth > width * 1.5) ? Math.max(0, (scaledHeight - height) / 2) : 0;
        final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = result.createGraphics();
        try {
            final Color background = UIManager.getColor("Panel.background");
            g2.setColor((background != null) ? background : Color.GRAY);
            g2.fillRect(0, 0, width, height);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(full, 0, -top, (int) Math.round(pageWidth * scale), scaledHeight, null);
        } finally {
            g2.dispose();
        }
        return result;
    }

    private static void layOut(Component component) {
        if (component instanceof Container) {
            final Container container = (Container) component;
            container.doLayout();
            for (Component child: container.getComponents()) {
                layOut(child);
            }
        }
    }

    private final DockableFrame page;
    private BufferedImage image;

    /**
     * The size at which to lay out pages which are not shown anywhere: that of a side panel.
     */
    private static final int PANEL_WIDTH = 280, PANEL_HEIGHT = 420;
}
