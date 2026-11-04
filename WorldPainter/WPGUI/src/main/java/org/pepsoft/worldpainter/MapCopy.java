package org.pepsoft.worldpainter;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.util.swing.TiledImageViewerContainer;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.docking.DockableFrame;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.Objects;

/**
 * Another view of the map, in an area of its own, as Blender can show the same editor in several areas: the same
 * dimension with the same settings as the map, which it follows, but moved and zoomed on its own, for instance to see
 * the whole map next to a detail. The current tool works in it too.
 */
final class MapCopy {
    MapCopy(App app, WorldPainter map, String key, ColourScheme colourScheme, CustomBiomeManager customBiomeManager) {
        this.app = app;
        this.map = map;
        view = new WorldPainter(colourScheme, customBiomeManager);
        view.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        sync();
        view.setZoom(map.getZoom());
        final Point centre = map.getViewCentreInWorldCoords();
        view.moveTo(centre.x, centre.y);
        // Follow the settings of the map: most of them say when they change, the others are checked now and then
        mapListener = event -> sync();
        map.addPropertyChangeListener(mapListener);
        syncTimer = new Timer(1000, e -> sync());
        syncTimer.start();

        final TiledImageViewerContainer container = new TiledImageViewerContainer(view);
        page = new DockableFrame(key);
        page.setTitle("Map");
        page.setFrameIcon(Icons.get("map", 16));
        page.setGroup(App.GROUP_MAP);
        page.setDescription("Another view of the map, which you can move and zoom on its own");
        page.add(org.pepsoft.worldpainter.design.Glass.frame(container), BorderLayout.CENTER);
        page.add(createFoot(), BorderLayout.SOUTH);

        final MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // Let the current tool work here, unless a button is held down (dragging in from another view)
                if ((e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK)) == 0) {
                    app.useMapView(view);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                final Point coords = view.viewToWorld(e.getX(), e.getY());
                locationLabel.setText(coords.x + ", " + coords.y);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                locationLabel.setText(" ");
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragPoint = e.getLocationOnScreen();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPoint != null) {
                    final Point point = e.getLocationOnScreen();
                    view.moveBy(dragPoint.x - point.x, dragPoint.y - point.y);
                    dragPoint = point;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragPoint = null;
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown() || e.isMetaDown()) {
                    zoom(-e.getWheelRotation(), e.getX(), e.getY());
                } else {
                    // The size and rotation of the brush, as on the map
                    app.mapWheelMoved(e);
                }
            }

            private Point dragPoint;
        };
        view.addMouseListener(mouseAdapter);
        view.addMouseMotionListener(mouseAdapter);
        view.addMouseWheelListener(mouseAdapter);
    }

    DockableFrame getPage() {
        return page;
    }

    WorldPainter getView() {
        return view;
    }

    /**
     * Stop following the map, when the area is closed.
     */
    void dispose() {
        syncTimer.stop();
        map.removePropertyChangeListener(mapListener);
        app.useMapView(map);
        view.setDimension(null);
    }

    private JComponent createFoot() {
        locationLabel = new JLabel(" ");
        locationLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        zoomLabel = new JLabel("100%", SwingConstants.CENTER);
        zoomLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        zoomLabel.setPreferredSize(new java.awt.Dimension(zoomLabel.getPreferredSize().width + 20, zoomLabel.getPreferredSize().height));
        zoomLabel.setToolTipText("<html><b>Zoom</b><br>Click to reset to 100%</html>");
        zoomLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        zoomLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                zoom(-view.getZoom(), view.getWidth() / 2, view.getHeight() / 2);
            }
        });
        final JButton zoomOut = new JButton(Icons.get("zoom-out", 16)), zoomIn = new JButton(Icons.get("zoom-in", 16));
        zoomOut.setToolTipText("Zoom out");
        zoomOut.addActionListener(e -> zoom(-1, view.getWidth() / 2, view.getHeight() / 2));
        zoomIn.setToolTipText("Zoom in");
        zoomIn.addActionListener(e -> zoom(1, view.getWidth() / 2, view.getHeight() / 2));
        final JToolBar zoomBar = new JToolBar();
        zoomBar.setFloatable(false);
        zoomBar.setOpaque(false);
        zoomBar.setBorder(null);
        for (JButton button: new JButton[] {zoomOut, zoomIn}) {
            button.setMargin(new Insets(1, 3, 1, 3));
            button.setFocusable(false);
        }
        zoomBar.add(zoomOut);
        zoomBar.add(zoomLabel);
        zoomBar.add(zoomIn);
        final JPanel foot = new JPanel(new BorderLayout(8, 0));
        foot.setBorder(new HairlineBorder(true, false, false, false, new Insets(0, 8, 0, 4)));
        foot.add(locationLabel, BorderLayout.CENTER);
        foot.add(zoomBar, BorderLayout.EAST);
        updateZoomLabel();
        return foot;
    }

    private void zoom(int steps, int x, int y) {
        final int zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, view.getZoom() + steps));
        if (zoom != view.getZoom()) {
            view.setZoom(zoom, x, y);
            updateZoomLabel();
        }
    }

    private void updateZoomLabel() {
        final float scale = (view.getDimension() != null) ? view.getDimension().getScale() : 1.0f;
        zoomLabel.setText(Math.round(100 * Math.pow(2.0, view.getZoom()) / scale) + "%");
    }

    /**
     * Bring the settings up to date with those of the map, changing only what changed, since most changes make the
     * view draw everything again.
     */
    private void sync() {
        if (view.getDimension() != map.getDimension()) {
            view.setDimension(map.getDimension());
            if (zoomLabel != null) {
                updateZoomLabel();
            }
        }
        if (view.getColourScheme() != map.getColourScheme()) {
            view.setColourScheme(map.getColourScheme());
        }
        if (! Objects.equals(view.getHiddenLayers(), map.getHiddenLayers())) {
            view.setHiddenLayers(map.getHiddenLayers());
        }
        if (view.getColourRamp() != map.getColourRamp()) {
            view.setColourRamp(map.getColourRamp());
        }
        if (view.isDrawContours() != map.isDrawContours()) {
            view.setDrawContours(map.isDrawContours());
        }
        if (view.getContourSeparation() != map.getContourSeparation()) {
            view.setContourSeparation(map.getContourSeparation());
        }
        if (view.getLightOrigin() != map.getLightOrigin()) {
            view.setLightOrigin(map.getLightOrigin());
        }
        if (view.isDrawBorders() != map.isDrawBorders()) {
            view.setDrawBorders(map.isDrawBorders());
        }
        if (view.isDrawBiomes() != map.isDrawBiomes()) {
            view.setDrawBiomes(map.isDrawBiomes());
        }
        if (view.isDrawMinecraftBorder() != map.isDrawMinecraftBorder()) {
            view.setDrawMinecraftBorder(map.isDrawMinecraftBorder());
        }
        if (view.getViewDistance() != map.getViewDistance()) {
            view.setViewDistance(map.getViewDistance());
        }
        if (view.isDrawViewDistance() != map.isDrawViewDistance()) {
            view.setDrawViewDistance(map.isDrawViewDistance());
        }
        if (view.isDrawWalkingDistance() != map.isDrawWalkingDistance()) {
            view.setDrawWalkingDistance(map.isDrawWalkingDistance());
        }
        // The brush, drawn where the mouse is
        if (view.isDrawBrush() != map.isDrawBrush()) {
            view.setDrawBrush(map.isDrawBrush());
        }
        if (view.getRadius() != map.getRadius()) {
            view.setRadius(map.getRadius());
        }
        if (view.getBrushShape() != map.getBrushShape()) {
            view.setBrushShape(map.getBrushShape());
        }
        if (view.getCustomBrushShape() != map.getCustomBrushShape()) {
            view.setCustomBrushShape(map.getCustomBrushShape());
        }
        if (view.getBrushRotation() != map.getBrushRotation()) {
            view.setBrushRotation(map.getBrushRotation());
        }
        if (view.isPaintGrid() != map.isPaintGrid()) {
            view.setPaintGrid(map.isPaintGrid());
        }
        if (view.getGridSize() != map.getGridSize()) {
            view.setGridSize(map.getGridSize());
        }
        if (! Objects.equals(view.getBackground(), map.getBackground())) {
            view.setBackground(map.getBackground());
        }
    }

    private final App app;
    private final WorldPainter map, view;
    private final DockableFrame page;
    private final PropertyChangeListener mapListener;
    private final Timer syncTimer;
    private JLabel locationLabel, zoomLabel;

    private static final int MIN_ZOOM = -4, MAX_ZOOM = 6;
}
