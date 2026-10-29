package org.pepsoft.worldpainter.docking;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.Icons;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.swing.SwingConstants.HORIZONTAL;
import static javax.swing.SwingConstants.VERTICAL;

/**
 * A lightweight, dependency free docking framework. It manages a workspace in the centre, surrounded by a column of
 * {@link DockableFrame}s on the left and on the right. Each column consists of vertically stacked groups which the user
 * can resize; a group contains one or more frames, shown as tabs if there is more than one. Frames can be floated in
 * their own window, hidden and shown again, and moved to the other side. The layout can be persisted with
 * {@link #getLayoutRawData()} and restored with {@link #loadLayoutFrom(byte[])}.
 */
public class DockingManager {
    public DockingManager(Window owner) {
        this.owner = owner;
        workspace = new JPanel(new BorderLayout());
        columns.put(Side.WEST, new DockColumn(Side.WEST));
        columns.put(Side.EAST, new DockColumn(Side.EAST));
        mainContainer = new JPanel(new BorderLayout());
        mainContainer.add(columns.get(Side.WEST), BorderLayout.WEST);
        mainContainer.add(workspace, BorderLayout.CENTER);
        mainContainer.add(columns.get(Side.EAST), BorderLayout.EAST);
    }

    /**
     * Get the component containing the docks and the workspace, which should be added to the main window.
     */
    public JComponent getMainContainer() {
        return mainContainer;
    }

    /**
     * Get the container in the centre of the docks, to which the main content should be added.
     */
    public JPanel getWorkspace() {
        return workspace;
    }

    public void addFrame(DockableFrame frame) {
        final String key = frame.getKey();
        if (frames.containsKey(key)) {
            throw new IllegalArgumentException("Frame with key " + key + " already added");
        }
        final Placement pending = pendingPlacements.remove(key);
        final FrameInfo info = new FrameInfo(frame, (pending != null) ? pending : defaultPlacement(frame));
        frame.addPropertyChangeListener(frameListener);
        frames.put(key, info);
        rebuild();
        if (info.placement.state == State.FLOATING) {
            openFloatingWindow(info);
        }
    }

    public void removeFrame(String key) {
        final FrameInfo info = frames.remove(key);
        if (info != null) {
            info.frame.removePropertyChangeListener(frameListener);
            closeFloatingWindow(info);
            final Container parent = info.frame.getParent();
            if (parent != null) {
                parent.remove(info.frame);
            }
            rebuild();
        }
    }

    public DockableFrame getFrame(String key) {
        final FrameInfo info = frames.get(key);
        return (info != null) ? info.frame : null;
    }

    public List<DockableFrame> getFrames() {
        final List<DockableFrame> result = new ArrayList<>(frames.size());
        frames.values().forEach(info -> result.add(info.frame));
        return result;
    }

    /**
     * Dock a frame at a specific location, regardless of its current state.
     */
    public void dockFrame(String key, Side side, int index) {
        final FrameInfo info = getInfo(key);
        closeFloatingWindow(info);
        info.placement = new Placement(side, index, State.DOCKED, info.placement.floatingBounds);
        rebuild();
    }

    /**
     * Make sure a frame is visible, by docking it at its last location if it is hidden, selecting its tab if it is
     * part of a group, or bringing its window to the front if it is floating.
     */
    public void showFrame(String key) {
        final FrameInfo info = getInfo(key);
        // A panel which is asked for brings its side back if it was collapsed
        if ((info.placement.state != State.FLOATING) && isCollapsed(info.placement.side)) {
            setCollapsed(info.placement.side, false);
        }
        switch (info.placement.state) {
            case HIDDEN:
                info.placement = info.placement.withState(State.DOCKED);
                selectedKeys.put(groupId(info.placement.side, info.placement.index), key);
                rebuild();
                break;
            case FLOATING:
                if (info.window != null) {
                    info.window.toFront();
                }
                break;
            case DOCKED:
                selectedKeys.put(groupId(info.placement.side, info.placement.index), key);
                final Container parent = info.frame.getParent();
                if (parent instanceof JTabbedPane) {
                    ((JTabbedPane) parent).setSelectedComponent(info.frame);
                }
                break;
        }
    }

    /**
     * Show a frame and give it the keyboard focus.
     */
    public void activateFrame(String key) {
        showFrame(key);
        final DockableFrame frame = getInfo(key).frame;
        SwingUtilities.invokeLater(frame::requestFocusInWindow);
    }

    public void hideFrame(String key) {
        final FrameInfo info = getInfo(key);
        closeFloatingWindow(info);
        info.placement = info.placement.withState(State.HIDDEN);
        rebuild();
    }

    public void floatFrame(String key) {
        final FrameInfo info = getInfo(key);
        if (info.placement.state != State.FLOATING) {
            final Point location = info.frame.isShowing() ? info.frame.getLocationOnScreen() : null;
            final Dimension size = info.frame.isShowing() ? info.frame.getSize() : info.frame.getPreferredSize();
            info.placement = info.placement.withState(State.FLOATING);
            if (info.placement.floatingBounds == null) {
                info.placement.floatingBounds = new Rectangle((location != null) ? location.x + 32 : -1, (location != null) ? location.y + 32 : -1, Math.max(size.width, 200), Math.max(size.height, 150));
            }
            rebuild();
            openFloatingWindow(info);
        }
    }

    /**
     * Float a frame in a window of its own with specific bounds on the screen.
     */
    public void floatFrame(String key, Rectangle bounds) {
        final FrameInfo info = getInfo(key);
        closeFloatingWindow(info);
        info.placement = info.placement.withState(State.FLOATING);
        info.placement.floatingBounds = new Rectangle(bounds);
        rebuild();
        openFloatingWindow(info);
    }

    /**
     * Collapse (hide) or expand the column of panels on one side, to make room for the map.
     */
    public void setCollapsed(Side side, boolean collapsed) {
        if (collapsed ? collapsedSides.add(side) : collapsedSides.remove(side)) {
            rebuild();
            if (collapseListener != null) {
                collapseListener.run();
            }
        }
    }

    public boolean isCollapsed(Side side) {
        return collapsedSides.contains(side);
    }

    /**
     * Set something to run when a side is collapsed or expanded, for instance to update the buttons which do it.
     */
    public void setCollapseListener(Runnable collapseListener) {
        this.collapseListener = collapseListener;
    }

    public boolean isFrameVisible(String key) {
        final FrameInfo info = frames.get(key);
        return (info != null) && (info.placement.state != State.HIDDEN);
    }

    /**
     * Put all frames back at their initial location and reset all sizes.
     */
    public void resetToDefault() {
        for (FrameInfo info: frames.values()) {
            closeFloatingWindow(info);
            info.placement = defaultPlacement(info.frame);
        }
        pendingPlacements.clear();
        selectedKeys.clear();
        groupHeights.clear();
        columnWidths.clear();
        rebuild();
    }

    /**
     * Serialise the current layout to a byte array.
     */
    public byte[] getLayoutRawData() {
        final StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\t').append(FORMAT_VERSION).append('\n');
        columnWidths.forEach((side, width) -> sb.append("width\t").append(side).append('\t').append(width).append('\n'));
        groupHeights.forEach((groupId, height) -> sb.append("height\t").append(encode(groupId)).append('\t').append(height).append('\n'));
        selectedKeys.forEach((groupId, key) -> sb.append("selected\t").append(encode(groupId)).append('\t').append(encode(key)).append('\n'));
        final Map<String, Placement> allPlacements = new LinkedHashMap<>(pendingPlacements);
        frames.forEach((key, info) -> {
            if (info.window != null) {
                info.placement.floatingBounds = info.window.getBounds();
            }
            allPlacements.put(key, info.placement);
        });
        allPlacements.forEach((key, placement) -> {
            sb.append("frame\t").append(encode(key)).append('\t').append(placement.side).append('\t').append(placement.index).append('\t').append(placement.state);
            if (placement.floatingBounds != null) {
                final Rectangle b = placement.floatingBounds;
                sb.append('\t').append(b.x).append('\t').append(b.y).append('\t').append(b.width).append('\t').append(b.height);
            }
            sb.append('\n');
        });
        return sb.toString().getBytes(UTF_8);
    }

    public boolean loadLayoutFrom(InputStream in) throws IOException {
        return loadLayoutFrom(in.readAllBytes());
    }

    /**
     * Restore a layout previously obtained from {@link #getLayoutRawData()}. Data in an unknown format (such as layouts
     * stored by previous versions of WorldPainter) is ignored.
     *
     * @return {@code true} if the layout was restored.
     */
    public boolean loadLayoutFrom(byte[] data) {
        if (data == null) {
            return false;
        }
        final String[] lines = new String(data, UTF_8).split("\n");
        if ((lines.length == 0) || (! lines[0].startsWith(MAGIC + '\t'))) {
            return false;
        }
        final Map<Side, Integer> newColumnWidths = new EnumMap<>(Side.class);
        final Map<String, Integer> newGroupHeights = new HashMap<>();
        final Map<String, String> newSelectedKeys = new HashMap<>();
        final Map<String, Placement> newPlacements = new LinkedHashMap<>();
        try {
            for (int i = 1; i < lines.length; i++) {
                final String[] tokens = lines[i].split("\t");
                switch (tokens[0]) {
                    case "width":
                        newColumnWidths.put(Side.valueOf(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    case "height":
                        newGroupHeights.put(decode(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    case "selected":
                        newSelectedKeys.put(decode(tokens[1]), decode(tokens[2]));
                        break;
                    case "frame":
                        final Placement placement = new Placement(Side.valueOf(tokens[2]), Integer.parseInt(tokens[3]), State.valueOf(tokens[4]), null);
                        if (tokens.length >= 9) {
                            placement.floatingBounds = new Rectangle(Integer.parseInt(tokens[5]), Integer.parseInt(tokens[6]), Integer.parseInt(tokens[7]), Integer.parseInt(tokens[8]));
                        }
                        newPlacements.put(decode(tokens[1]), placement);
                        break;
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Ignoring corrupt workspace layout data", e);
            return false;
        }
        columnWidths.clear();
        columnWidths.putAll(newColumnWidths);
        groupHeights.clear();
        groupHeights.putAll(newGroupHeights);
        selectedKeys.clear();
        selectedKeys.putAll(newSelectedKeys);
        final List<FrameInfo> toFloat = new ArrayList<>();
        newPlacements.forEach((key, placement) -> {
            final FrameInfo info = frames.get(key);
            if (info != null) {
                closeFloatingWindow(info);
                info.placement = placement;
                if (placement.state == State.FLOATING) {
                    toFloat.add(info);
                }
            } else {
                pendingPlacements.put(key, placement);
            }
        });
        rebuild();
        toFloat.forEach(this::openFloatingWindow);
        return true;
    }

    /**
     * Create a menu which lists all frames and allows the user to hide and show them.
     */
    public JMenu createPanelsMenu(String text) {
        final JMenu menu = new JMenu(text);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                for (FrameInfo info: frames.values()) {
                    final String key = info.frame.getKey();
                    final JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(info.frame.getTitle(), info.frame.getFrameIcon(), info.placement.state != State.HIDDEN);
                    menuItem.addActionListener(event -> {
                        if (menuItem.isSelected()) {
                            showFrame(key);
                        } else {
                            hideFrame(key);
                        }
                    });
                    menu.add(menuItem);
                }
            }

            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        return menu;
    }

    private FrameInfo getInfo(String key) {
        final FrameInfo info = frames.get(key);
        if (info == null) {
            throw new IllegalArgumentException("No frame with key " + key);
        }
        return info;
    }

    private Placement defaultPlacement(DockableFrame frame) {
        return new Placement(frame.getInitSide(), frame.getInitIndex(), State.DOCKED, null);
    }

    /**
     * Recreate the contents of both columns from the current placements.
     */
    private void rebuild() {
        for (Side side: Side.values()) {
            final SortedMap<Integer, List<FrameInfo>> groups = new TreeMap<>();
            for (FrameInfo info: frames.values()) {
                if ((info.placement.state == State.DOCKED) && (info.placement.side == side)) {
                    groups.computeIfAbsent(info.placement.index, i -> new ArrayList<>()).add(info);
                }
            }
            columns.get(side).rebuild(groups);
        }
        mainContainer.revalidate();
        mainContainer.repaint();
    }

    private void openFloatingWindow(FrameInfo info) {
        if (info.window != null) {
            return;
        }
        final JDialog window = new JDialog(owner, info.frame.getTitle(), Dialog.ModalityType.MODELESS);
        window.setType(Window.Type.UTILITY);
        window.getRootPane().putClientProperty("Window.style", "small");
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Closing a floating window docks the frame again
                closeFloatingWindow(info);
                info.placement = info.placement.withState(State.DOCKED);
                rebuild();
            }
        });
        window.getContentPane().add(info.frame, BorderLayout.CENTER);
        info.frame.setVisible(true);
        final Rectangle bounds = info.placement.floatingBounds;
        if ((bounds != null) && (bounds.x != -1) && isOnScreen(bounds)) {
            window.setBounds(bounds);
        } else {
            window.pack();
            if (bounds != null) {
                window.setSize(Math.max(bounds.width, window.getWidth()), Math.max(bounds.height, window.getHeight()));
            }
            window.setLocationRelativeTo(owner);
        }
        info.window = window;
        window.setVisible(true);
    }

    private void closeFloatingWindow(FrameInfo info) {
        if (info.window != null) {
            info.placement.floatingBounds = info.window.getBounds();
            info.window.getContentPane().remove(info.frame);
            info.window.dispose();
            info.window = null;
        }
    }

    private static boolean isOnScreen(Rectangle bounds) {
        for (GraphicsDevice device: GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private static String groupId(Side side, int index) {
        return side + "." + index;
    }

    private static String encode(String str) {
        return URLEncoder.encode(str, UTF_8);
    }

    private static String decode(String str) {
        return URLDecoder.decode(str, UTF_8);
    }

    private JPopupMenu createFramePopupMenu(FrameInfo info) {
        final String key = info.frame.getKey();
        final JPopupMenu menu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem("Float");
        menuItem.addActionListener(e -> floatFrame(key));
        menu.add(menuItem);
        menuItem = new JMenuItem("Hide");
        menuItem.addActionListener(e -> hideFrame(key));
        menu.add(menuItem);
        menu.addSeparator();
        final Side otherSide = info.placement.side.opposite();
        menuItem = new JMenuItem((otherSide == Side.WEST) ? "Move to left side" : "Move to right side");
        menuItem.addActionListener(e -> dockFrame(key, otherSide, info.placement.index));
        menu.add(menuItem);
        menuItem = new JMenuItem("Move to own group");
        menuItem.addActionListener(e -> {
            int maxIndex = info.placement.index;
            for (FrameInfo other: frames.values()) {
                if (other.placement.side == info.placement.side) {
                    maxIndex = Math.max(maxIndex, other.placement.index);
                }
            }
            dockFrame(key, info.placement.side, maxIndex + 1);
        });
        menu.add(menuItem);
        return menu;
    }

    private final Window owner;
    private final JPanel mainContainer, workspace;
    private final Map<Side, DockColumn> columns = new EnumMap<>(Side.class);
    private final Map<String, FrameInfo> frames = new LinkedHashMap<>();
    private final Map<String, Placement> pendingPlacements = new LinkedHashMap<>();
    private final Map<String, String> selectedKeys = new HashMap<>();
    private final Map<String, Integer> groupHeights = new HashMap<>();
    private final Map<Side, Integer> columnWidths = new EnumMap<>(Side.class);
    private final Set<Side> collapsedSides = EnumSet.noneOf(Side.class);
    private Runnable collapseListener;
    private final PropertyChangeListener frameListener = this::frameChanged;

    /**
     * Keep the docks in sync when the key, title or icon of a frame changes.
     */
    private void frameChanged(java.beans.PropertyChangeEvent event) {
        final DockableFrame frame = (DockableFrame) event.getSource();
        switch (event.getPropertyName()) {
            case "key":
                final FrameInfo info = frames.remove((String) event.getOldValue());
                if (info != null) {
                    frames.put(frame.getKey(), info);
                }
                break;
            case "title":
            case "frameIcon":
                final Container parent = frame.getParent();
                if (parent instanceof JTabbedPane) {
                    final JTabbedPane tabbedPane = (JTabbedPane) parent;
                    final int index = tabbedPane.indexOfComponent(frame);
                    if (index != -1) {
                        tabbedPane.setTitleAt(index, frame.getTitle());
                        tabbedPane.setIconAt(index, frame.getFrameIcon());
                    }
                } else if (parent instanceof DockGroup) {
                    ((DockGroup) parent).updateHeader();
                } else {
                    final Window window = SwingUtilities.getWindowAncestor(frame);
                    if ((window instanceof JDialog) && (window != owner)) {
                        ((JDialog) window).setTitle(frame.getTitle());
                    }
                }
                break;
        }
    }

    private static final String MAGIC = "WPDOCK";
    private static final int FORMAT_VERSION = 1;
    private static final int HANDLE_SIZE = 5, MIN_GROUP_HEIGHT = 48, MIN_COLUMN_WIDTH = 120;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DockingManager.class);

    private enum State {DOCKED, FLOATING, HIDDEN}

    private static final class Placement {
        Placement(Side side, int index, State state, Rectangle floatingBounds) {
            this.side = side;
            this.index = index;
            this.state = state;
            this.floatingBounds = floatingBounds;
        }

        Placement withState(State state) {
            return new Placement(side, index, state, floatingBounds);
        }

        final Side side;
        final int index;
        final State state;
        Rectangle floatingBounds;
    }

    private static final class FrameInfo {
        FrameInfo(DockableFrame frame, Placement placement) {
            this.frame = frame;
            this.placement = placement;
        }

        final DockableFrame frame;
        Placement placement;
        JDialog window;
    }

    /**
     * A column of vertically stacked groups on one side of the workspace, with a resize handle on the edge facing the
     * workspace and between each pair of groups.
     */
    private final class DockColumn extends JPanel {
        DockColumn(Side side) {
            super(null);
            this.side = side;
            setLayout(new ColumnLayout());
            edgeHandle = new ResizeHandle(HORIZONTAL, delta -> {
                // The stored width excludes the resize handle itself
                final int width = Math.max(MIN_COLUMN_WIDTH, getWidth() - HANDLE_SIZE + ((side == Side.WEST) ? delta : -delta));
                columnWidths.put(side, width);
                revalidate();
            });
            setVisible(false);
        }

        void rebuild(SortedMap<Integer, List<FrameInfo>> groupMap) {
            for (Component component: getComponents()) {
                if (component instanceof DockGroup) {
                    ((DockGroup) component).dispose();
                }
            }
            removeAll();
            groups.clear();
            for (Map.Entry<Integer, List<FrameInfo>> entry: groupMap.entrySet()) {
                final DockGroup group = new DockGroup(side, entry.getKey(), entry.getValue());
                if (! groups.isEmpty()) {
                    final DockGroup groupAbove = groups.get(groups.size() - 1);
                    add(new ResizeHandle(VERTICAL, delta -> {
                        groupHeights.put(groupAbove.id, Math.max(MIN_GROUP_HEIGHT, groupAbove.getHeight() + delta));
                        revalidate();
                    }));
                }
                groups.add(group);
                add(group);
            }
            add(edgeHandle);
            setVisible((! groups.isEmpty()) && (! collapsedSides.contains(side)));
            revalidate();
            repaint();
        }

        private final Side side;
        private final List<DockGroup> groups = new ArrayList<>();
        private final ResizeHandle edgeHandle;

        /**
         * Lays out the groups vertically. Each group except the last one gets the height the user dragged it to, or
         * else its preferred height; the last group gets the remaining space. If there is not enough room the groups
         * are scaled down proportionally.
         */
        private final class ColumnLayout implements LayoutManager {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                final Integer width = columnWidths.get(side);
                int prefWidth = MIN_COLUMN_WIDTH, prefHeight = 0;
                for (DockGroup group: groups) {
                    final Dimension size = group.getPreferredSize();
                    prefWidth = Math.max(prefWidth, size.width);
                    prefHeight += size.height + HANDLE_SIZE;
                }
                // Leave room for a vertical scrollbar
                prefWidth += UIManager.getInt("ScrollBar.width") > 0 ? UIManager.getInt("ScrollBar.width") : 12;
                return new Dimension(((width != null) ? width : prefWidth) + HANDLE_SIZE, prefHeight);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(MIN_COLUMN_WIDTH + HANDLE_SIZE, groups.size() * (MIN_GROUP_HEIGHT + HANDLE_SIZE));
            }

            @Override
            public void layoutContainer(Container parent) {
                final int width = parent.getWidth() - HANDLE_SIZE, height = parent.getHeight();
                final int x = (side == Side.WEST) ? 0 : HANDLE_SIZE;
                edgeHandle.setBounds((side == Side.WEST) ? width : 0, 0, HANDLE_SIZE, height);
                final int count = groups.size();
                if (count == 0) {
                    return;
                }
                final int available = height - (count - 1) * HANDLE_SIZE;
                final int[] heights = new int[count];
                int fixedTotal = 0;
                for (int i = 0; i < count - 1; i++) {
                    final DockGroup group = groups.get(i);
                    final Integer userHeight = groupHeights.get(group.id);
                    heights[i] = Math.max(MIN_GROUP_HEIGHT, (userHeight != null) ? userHeight : group.getPreferredSize().height);
                    fixedTotal += heights[i];
                }
                final int maxFixed = available - MIN_GROUP_HEIGHT;
                if ((fixedTotal > maxFixed) && (fixedTotal > 0)) {
                    final float scale = Math.max(0, maxFixed) / (float) fixedTotal;
                    fixedTotal = 0;
                    for (int i = 0; i < count - 1; i++) {
                        heights[i] = Math.round(heights[i] * scale);
                        fixedTotal += heights[i];
                    }
                }
                heights[count - 1] = Math.max(0, available - fixedTotal);
                int y = 0;
                final Component[] components = parent.getComponents();
                int groupIndex = 0;
                for (Component component: components) {
                    if (component == edgeHandle) {
                        continue;
                    } else if (component instanceof DockGroup) {
                        component.setBounds(x, y, width, heights[groupIndex]);
                        y += heights[groupIndex++];
                    } else {
                        component.setBounds(x, y, width, HANDLE_SIZE);
                        y += HANDLE_SIZE;
                    }
                }
            }
        }
    }

    /**
     * A group of one or more frames, shown with a header if there is only one frame, or as tabs otherwise.
     */
    private final class DockGroup extends JPanel {
        DockGroup(Side side, int index, List<FrameInfo> frameInfos) {
            super(new BorderLayout());
            id = groupId(side, index);
            this.frameInfos = frameInfos;
            if (frameInfos.size() == 1) {
                final FrameInfo info = frameInfos.get(0);
                header = new JPanel(new BorderLayout());
                header.setBorder(new HairlineBorder(false, false, true, false, new Insets(4, 10, 4, 4)));
                titleLabel = new JLabel();
                titleLabel.putClientProperty("FlatLaf.styleClass", "semibold");
                titleLabel.setIconTextGap(8);
                header.add(titleLabel, BorderLayout.CENTER);
                header.add(createButtons(() -> info), BorderLayout.EAST);
                header.setComponentPopupMenu(createFramePopupMenu(info));
                titleLabel.setInheritsPopupMenu(true);
                add(header, BorderLayout.NORTH);
                info.frame.setVisible(true);
                add(info.frame, BorderLayout.CENTER);
                tabbedPane = null;
                updateHeader();
            } else {
                header = null;
                titleLabel = null;
                tabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
                tabbedPane.putClientProperty("JTabbedPane.tabHeight", 32);
                tabbedPane.putClientProperty("JTabbedPane.tabInsets", new Insets(0, 10, 0, 10));
                tabbedPane.putClientProperty("JTabbedPane.minimumTabWidth", 24);
                // Only show the title of the selected tab, and icons for the others, so that many tabs fit in a narrow
                // column
                tabbedPane.putClientProperty("JTabbedPane.tabWidthMode", "compact");
                // Use the drop down list instead of scroll arrows when the tabs do not fit
                tabbedPane.putClientProperty("JTabbedPane.scrollButtonsPolicy", "never");
                tabbedPane.putClientProperty("JTabbedPane.showContentSeparator", Boolean.TRUE);
                for (FrameInfo info: frameInfos) {
                    info.frame.setVisible(true);
                    tabbedPane.addTab(info.frame.getTitle(), info.frame.getFrameIcon(), info.frame, (info.frame.getToolTipText() != null) ? info.frame.getToolTipText() : info.frame.getTitle());
                }
                final String selectedKey = selectedKeys.get(id);
                if (selectedKey != null) {
                    for (int i = 0; i < frameInfos.size(); i++) {
                        if (frameInfos.get(i).frame.getKey().equals(selectedKey)) {
                            tabbedPane.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                tabbedPane.addChangeListener(e -> {
                    final Component selected = tabbedPane.getSelectedComponent();
                    if (selected instanceof DockableFrame) {
                        selectedKeys.put(id, ((DockableFrame) selected).getKey());
                    }
                });
                tabbedPane.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        maybeShowPopup(e);
                    }

                    @Override public void mouseReleased(MouseEvent e) {
                        maybeShowPopup(e);
                    }

                    private void maybeShowPopup(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            final int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                            if (tabIndex != -1) {
                                createFramePopupMenu(frameInfos.get(tabIndex)).show(tabbedPane, e.getX(), e.getY());
                            }
                        }
                    }
                });
                final JComponent buttons = createButtons(this::getSelectedFrameInfo);
                if (UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLaf) {
                    tabbedPane.putClientProperty("JTabbedPane.trailingComponent", buttons);
                } else {
                    final JPanel buttonBar = new JPanel(new BorderLayout());
                    buttonBar.add(buttons, BorderLayout.EAST);
                    add(buttonBar, BorderLayout.NORTH);
                }
                add(tabbedPane, BorderLayout.CENTER);
            }
        }

        void updateHeader() {
            if (titleLabel != null) {
                final DockableFrame frame = frameInfos.get(0).frame;
                titleLabel.setText(frame.getTitle());
                titleLabel.setIcon(frame.getFrameIcon());
            }
        }

        void dispose() {
            if (tabbedPane != null) {
                tabbedPane.removeAll();
            }
            removeAll();
        }

        private FrameInfo getSelectedFrameInfo() {
            final int index = tabbedPane.getSelectedIndex();
            return frameInfos.get(Math.max(index, 0));
        }

        private JComponent createButtons(java.util.function.Supplier<FrameInfo> target) {
            final JToolBar toolBar = new JToolBar();
            toolBar.setFloatable(false);
            toolBar.setOpaque(false);
            toolBar.setBorder(null);
            final JButton floatButton = new JButton(Icons.muted("picture-in-picture-2", 14));
            floatButton.setToolTipText("<html><b>Float panel</b><br>Open this panel in its own window</html>");
            floatButton.addActionListener(e -> floatFrame(target.get().frame.getKey()));
            toolBar.add(floatButton);
            final JButton hideButton = new JButton(Icons.muted("x", 14));
            hideButton.setToolTipText("<html><b>Hide panel</b><br>Show it again from View → Panels</html>");
            hideButton.addActionListener(e -> hideFrame(target.get().frame.getKey()));
            toolBar.add(hideButton);
            return toolBar;
        }

        private final String id;
        private final List<FrameInfo> frameInfos;
        private final JPanel header;
        private final JLabel titleLabel;
        private final JTabbedPane tabbedPane;
    }

    /**
     * A thin draggable bar that reports how far it has been dragged.
     */
    private static final class ResizeHandle extends JComponent {
        ResizeHandle(int orientation, java.util.function.IntConsumer dragListener) {
            setCursor(Cursor.getPredefinedCursor((orientation == HORIZONTAL) ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            final MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastPosition = (orientation == HORIZONTAL) ? e.getXOnScreen() : e.getYOnScreen();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    final int position = (orientation == HORIZONTAL) ? e.getXOnScreen() : e.getYOnScreen();
                    final int delta = position - lastPosition;
                    if (delta != 0) {
                        dragListener.accept(delta);
                        lastPosition = position;
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                private int lastPosition;
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color colour = UIManager.getColor(hover ? "Component.focusColor" : "Separator.foreground");
            if (colour == null) {
                colour = hover ? Color.GRAY : Color.LIGHT_GRAY;
            }
            g.setColor(colour);
            if (getWidth() > getHeight()) {
                g.fillRect(0, getHeight() / 2, getWidth(), hover ? 2 : 1);
            } else {
                g.fillRect(getWidth() / 2, 0, hover ? 2 : 1, getHeight());
            }
        }

        private boolean hover;
    }
}
