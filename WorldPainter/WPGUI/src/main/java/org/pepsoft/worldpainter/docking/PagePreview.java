package org.pepsoft.worldpainter.docking;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import org.pepsoft.worldpainter.help.ToolCard;
import org.pepsoft.worldpainter.util.ResponsiveGridLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * A picture of a page for the card of the editor type menu: what the page shows, drawn as it is now, in the colours of
 * the current theme. It leaves out what only introduces the page (its description, which the card already says, and
 * its search field), starting at its first palette; for the map and the 3D view it is only the picture, without the
 * bars around it. A page which is shown somewhere is drawn at the size it has there; one which is not is first laid out
 * at the size of a side panel. A page which cannot be drawn yet (the 3D view before it was first opened) shows a
 * picture which comes with WorldPainter instead.
 */
public final class PagePreview implements ToolCard.Preview {
    PagePreview(DockableFrame page) {
        this.page = page;
    }

    @Override
    public void paint(Graphics2D g2, int width, int height, double time) {
        if (image == null) {
            // At the resolution of the screen, which may be higher than that of the component
            final double scale = Math.max(1.0, Math.min(3.0, g2.getTransform().getScaleX()));
            image = render(width, height, scale);
        }
        if (image != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(image, 0, 0, width, height, null);
        }
    }

    @Override
    public boolean isAnimated() {
        return false;
    }

    private Image render(int width, int height, double scale) {
        final Object ready = page.getClientProperty(KEY_READY);
        if ((ready instanceof BooleanSupplier) && (! ((BooleanSupplier) ready).getAsBoolean())) {
            return fallback();
        }
        int pageWidth = page.getWidth(), pageHeight = page.getHeight();
        if ((! page.isShowing()) || (pageWidth <= 0) || (pageHeight <= 0)) {
            if (page.getParent() != null) {
                // Somewhere it is not shown, such as a window being closed; better no picture than a disturbed layout
                return fallback();
            }
            pageWidth = Math.round(PANEL_WIDTH * getUIScale());
            pageHeight = Math.round(PANEL_HEIGHT * getUIScale());
            page.setSize(pageWidth, pageHeight);
            // Without a window validate() does nothing, so lay the components out by hand
            layOut(page);
        }

        // Which part of the page to show
        final Rectangle part;
        final Object area = page.getClientProperty(KEY_AREA);
        final boolean picture = (area instanceof Component) && SwingUtilities.isDescendingFrom((Component) area, page) && (((Component) area).getWidth() > 0);
        if (picture) {
            part = SwingUtilities.convertRectangle(((Component) area).getParent(), ((Component) area).getBounds(), page);
        } else {
            final int top = Math.min(contentTop(), Math.max(0, pageHeight - 40));
            part = new Rectangle(0, top, pageWidth, pageHeight - top);
        }

        final BufferedImage full = new BufferedImage((int) Math.ceil(pageWidth * scale), (int) Math.ceil(pageHeight * scale), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D pageGraphics = full.createGraphics();
        try {
            pageGraphics.scale(scale, scale);
            pageGraphics.setColor(background());
            pageGraphics.fillRect(0, 0, pageWidth, pageHeight);
            page.printAll(pageGraphics);
        } catch (RuntimeException e) {
            // Some pages cannot be drawn outside a window; show the card without a picture
            return fallback();
        } finally {
            pageGraphics.dispose();
        }

        // Panels at their real size, cut off at the bottom; pictures (the map, the 3D view) scaled to fill the card,
        // around their middle
        final double factor = picture ? Math.max((double) width / part.width, (double) height / part.height) : Math.min(1.0, (double) width / part.width);
        final int partWidth = (int) Math.round(part.width * factor), partHeight = (int) Math.round(part.height * factor);
        final int dx = picture ? (width - partWidth) / 2 : 0, dy = picture ? (height - partHeight) / 2 : 0;
        final BufferedImage result = new BufferedImage((int) Math.ceil(width * scale), (int) Math.ceil(height * scale), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = result.createGraphics();
        try {
            g2.scale(scale, scale);
            g2.setColor(background());
            g2.fillRect(0, 0, width, height);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(full,
                    dx, dy, dx + partWidth, dy + partHeight,
                    (int) Math.round(part.x * scale), (int) Math.round(part.y * scale), (int) Math.round((part.x + part.width) * scale), (int) Math.round((part.y + part.height) * scale),
                    null);
        } finally {
            g2.dispose();
        }
        return result;
    }

    /**
     * Where the content of a panel starts: below its description and its search field, and at the heading of its
     * first palette if it has one.
     */
    private int contentTop() {
        final int[] top = {0};
        final int[] firstGrid = {Integer.MAX_VALUE};
        visit(page, component -> {
            final Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), page);
            if (! component.isVisible()) {
                return;
            }
            if ((component instanceof JComponent) && Boolean.TRUE.equals(((JComponent) component).getClientProperty(KEY_SKIP))) {
                top[0] = Math.max(top[0], bounds.y + bounds.height);
            } else if ((component instanceof JTextField) && isSearchField((JTextField) component)) {
                top[0] = Math.max(top[0], bounds.y + bounds.height + Math.round(4 * getUIScale()));
            } else if ((component instanceof Container) && (((Container) component).getLayout() instanceof ResponsiveGridLayout) && (bounds.height > 0)) {
                firstGrid[0] = Math.min(firstGrid[0], bounds.y);
            }
        });
        if (firstGrid[0] != Integer.MAX_VALUE) {
            // Leave room for the heading above the palette
            return Math.max(top[0], firstGrid[0] - Math.round(HEADING_HEIGHT * getUIScale()));
        }
        return top[0];
    }

    private static boolean isSearchField(JTextField field) {
        final Object placeholder = field.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT);
        return (placeholder instanceof String) && ((String) placeholder).startsWith("Search");
    }

    /**
     * The picture which comes with WorldPainter, in the light or dark version, for a page which cannot be drawn yet.
     */
    private Image fallback() {
        final Object name = page.getClientProperty(KEY_FALLBACK);
        if (! (name instanceof String)) {
            return null;
        }
        final String resource = "org/pepsoft/worldpainter/previews/" + name + (FlatLaf.isLafDark() ? "-dark" : "-light") + ".png";
        try (InputStream in = PagePreview.class.getClassLoader().getResourceAsStream(resource)) {
            return (in != null) ? ImageIO.read(in) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private Color background() {
        final Color background = UIManager.getColor("Panel.background");
        return (background != null) ? background : Color.GRAY;
    }

    private static void visit(Component component, java.util.function.Consumer<Component> visitor) {
        if (component instanceof Container) {
            for (Component child: ((Container) component).getComponents()) {
                visitor.accept(child);
                visit(child, visitor);
            }
        }
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
    private Image image;

    /**
     * Client property of a page: the component with its picture (the map, the 3D view), to show instead of the whole
     * page.
     */
    public static final String KEY_AREA = "WorldPainter.preview.area";

    /**
     * Client property of a page: a {@link BooleanSupplier} which says whether the page can be drawn now.
     */
    public static final String KEY_READY = "WorldPainter.preview.ready";

    /**
     * Client property of a page: the name of the picture to show when it cannot be drawn, in
     * {@code org/pepsoft/worldpainter/previews}, followed by {@code -light.png} or {@code -dark.png}.
     */
    public static final String KEY_FALLBACK = "WorldPainter.preview.fallback";

    /**
     * Client property of a component at the top of a page which only introduces it, such as its description.
     */
    public static final String KEY_SKIP = "WorldPainter.preview.skip";

    /**
     * The size at which to lay out pages which are not shown anywhere: that of a side panel.
     */
    private static final int PANEL_WIDTH = 280, PANEL_HEIGHT = 420;

    private static final int HEADING_HEIGHT = 24;
}
