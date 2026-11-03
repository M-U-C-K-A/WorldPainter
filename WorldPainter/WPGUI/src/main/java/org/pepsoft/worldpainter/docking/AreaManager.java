package org.pepsoft.worldpainter.docking;

import org.pepsoft.worldpainter.docking.AreaTree.Leaf;
import org.pepsoft.worldpainter.docking.AreaTree.Node;
import org.pepsoft.worldpainter.docking.AreaTree.Split;
import org.pepsoft.worldpainter.util.Icons;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.pepsoft.util.GUIUtils.getUIScale;
import static org.pepsoft.worldpainter.docking.AreaTree.AUTO;
import static org.pepsoft.worldpainter.docking.AreaTree.FILL;

/**
 * Arranges the pages of the window in areas, the way Blender does. The window is divided into areas, side by side or
 * one above the other, which the user can resize by dragging the gaps between them, split by dragging from one of
 * their corners into them, join by dragging from a corner into a neighbour, move by dragging their header onto another
 * area, swap, maximise, or detach into a window of their own which floats over the main one. The button at the top left
 * of each area chooses what it shows. Pages of the same kind (such as the terrain, the layers and the biomes) share the
 * tabs down the side of the area which shows them.
 *
 * <p>Every page is shown in at most one area. Choosing a page which is already shown in another area swaps the two
 * areas' pages, so that nothing ever disappears by accident.
 *
 * <p>There are several workspaces, each with its own arrangement, chosen with the tabs in the header of the window,
 * like the workspaces of Blender. They are made from templates, and can be added, renamed, duplicated, reset and
 * deleted. The state of all of them can be stored with {@link #saveState()} and restored with
 * {@link #loadState(byte[])}.
 */
public class AreaManager {
    public AreaManager(JFrame owner) {
        this.owner = owner;
        divider = Math.max(3, Math.round(5 * getUIScale()));
        screen = new ScreenPanel(this);
        drag = new AreaDrag(this);
        saveTimer = new Timer(500, e -> fireStateChanged());
        saveTimer.setRepeats(false);
        // Keep track of the area the user last clicked in, for the commands of the Area menu
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if ((event.getID() == MouseEvent.MOUSE_PRESSED) && (event.getSource() instanceof Component)) {
                final AreaPanel panel = (AreaPanel) SwingUtilities.getAncestorOfClass(AreaPanel.class, (Component) event.getSource());
                if ((panel != null) && (panel.getManager() == this)) {
                    activeLeaf = panel.getLeaf();
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        // Pages which are not shown when the theme changes are not in a window, so bring them up to date separately
        UIManager.addPropertyChangeListener(event -> {
            if ("lookAndFeel".equals(event.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    for (DockableFrame page: pages.values()) {
                        if (SwingUtilities.getWindowAncestor(page) == null) {
                            SwingUtilities.updateComponentTreeUI(page);
                        }
                    }
                });
            }
        });
        // Ctrl+Space maximises the area under the mouse, or puts it back, as in Blender
        final JRootPane rootPane = owner.getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "toggleMaximiseArea");
        rootPane.getActionMap().put("toggleMaximiseArea", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleMaximise(getLeafUnderMouse());
            }
        });
        // Ctrl+Page Up and Ctrl+Page Down go to the previous and next workspace, as in Blender
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK), "previousWorkspace");
        rootPane.getActionMap().put("previousWorkspace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (workspaces.size() > 1) {
                    activateWorkspace((getActiveWorkspaceIndex() + workspaces.size() - 1) % workspaces.size());
                }
            }
        });
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK), "nextWorkspace");
        rootPane.getActionMap().put("nextWorkspace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (workspaces.size() > 1) {
                    activateWorkspace((getActiveWorkspaceIndex() + 1) % workspaces.size());
                }
            }
        });
    }

    /**
     * The component which holds the areas, to be added to the main window.
     */
    public JComponent getScreen() {
        return screen;
    }

    // Kinds of editors and pages

    /**
     * Set the columns of the editor type menu, in order.
     */
    public void setCategories(String... categories) {
        this.categories.clear();
        this.categories.addAll(Arrays.asList(categories));
    }

    /**
     * Define a kind of editor, of which pages can be shown in an area.
     *
     * @param id The key of the group, which the pages refer to.
     * @param title The name of the kind of editor.
     * @param icon The name of its {@link Icons icon}.
     * @param category The column of the editor type menu in which its pages are listed.
     */
    public void defineGroup(String id, String title, String icon, String category) {
        groups.put(id, new EditorGroup(id, title, icon, category));
    }

    public void addPage(DockableFrame page) {
        final String key = page.getKey();
        if (pages.containsKey(key)) {
            throw new IllegalArgumentException("Page with key " + key + " already added");
        }
        if ((page.getGroup() == null) || (! groups.containsKey(page.getGroup()))) {
            throw new IllegalArgumentException("Page " + key + " has unknown group " + page.getGroup());
        }
        pages.put(key, page);
        page.addPropertyChangeListener(pageListener);
        refresh();
    }

    public void removePage(String key) {
        final DockableFrame page = pages.remove(key);
        if (page != null) {
            page.removePropertyChangeListener(pageListener);
            final Container parent = page.getParent();
            if (parent != null) {
                parent.remove(page);
            }
            refresh();
        }
    }

    public DockableFrame getPage(String key) {
        return pages.get(key);
    }

    public List<DockableFrame> getPages() {
        return new ArrayList<>(pages.values());
    }

    /**
     * Whether a page is shown in an area of the current workspace (which may be folded away or behind a maximised
     * area) or in a window of its own.
     */
    public boolean isPageShown(String key) {
        return assignment.containsKey(key);
    }

    /**
     * Make sure a page can be seen: unfold its side or restore the maximised area if necessary, bring its window to
     * the front if it is floating, and if it is not shown anywhere show it in place of another page of the same kind,
     * or else in a window of its own.
     */
    public void showPage(String key) {
        final DockableFrame page = pages.get(key);
        if (page == null) {
            throw new IllegalArgumentException("No page with key " + key);
        } else if (active == null) {
            return;
        }
        Leaf leaf = assignment.get(key);
        if (leaf == null) {
            // Show it instead of another page of the same kind
            for (Leaf candidate: allLeaves(active)) {
                final String shown = display.get(candidate);
                if ((shown != null) && page.getGroup().equals(pages.get(shown).getGroup())) {
                    leaf = candidate;
                    break;
                }
            }
            if (leaf != null) {
                leaf.page = key;
                leaf.group = page.getGroup();
                refresh();
            } else {
                floatPage(key, null);
                return;
            }
        }
        final FloatingArea floating = findFloating(leaf);
        if (floating != null) {
            if (floating.window != null) {
                floating.window.toFront();
            }
            return;
        }
        if ((active.maximised != null) && (active.maximised != leaf)) {
            active.maximised = null;
        }
        // Unfold the side it is on
        final Map<Node, Side> sides = sidesAroundMain();
        Node node = leaf;
        while (node != null) {
            final Side side = sides.get(node);
            if ((side != null) && active.collapsed.remove(side)) {
                break;
            }
            node = node.parent;
        }
        refresh();
    }

    /**
     * Show a page and give it the keyboard focus.
     */
    public void activatePage(String key) {
        showPage(key);
        final DockableFrame page = pages.get(key);
        SwingUtilities.invokeLater(page::requestFocusInWindow);
    }

    /**
     * Stop showing a page. The area which showed it shows another page of the same kind, or is closed if there is
     * none.
     */
    public void hidePage(String key) {
        final Leaf leaf = assignment.get(key);
        if (leaf == null) {
            return;
        }
        final String group = pages.get(key).getGroup();
        for (DockableFrame page: pagesOf(group)) {
            if ((! page.getKey().equals(key)) && (! assignment.containsKey(page.getKey()))) {
                leaf.page = page.getKey();
                refresh();
                return;
            }
        }
        closeArea(leaf);
    }

    /**
     * Show a page in a window of its own, floating over the main window.
     *
     * @param bounds The bounds of the window on the screen, or {@code null} for somewhere sensible.
     */
    public void floatPage(String key, Rectangle bounds) {
        final DockableFrame page = pages.get(key);
        if ((page == null) || (active == null)) {
            return;
        }
        final Leaf current = assignment.get(key);
        if (current != null) {
            final FloatingArea floating = findFloating(current);
            if ((floating != null) && (current.page != null) && current.page.equals(key)) {
                if (bounds != null) {
                    floating.bounds = new Rectangle(bounds);
                    if (floating.window != null) {
                        floating.window.setBounds(bounds);
                    }
                }
                return;
            }
            // Leave the area it is in to show another page
            current.page = null;
        }
        final Leaf leaf = new Leaf(page.getGroup(), key);
        final FloatingArea floating = new FloatingArea(leaf);
        floating.bounds = (bounds != null) ? new Rectangle(bounds) : defaultFloatingBounds(page);
        floating.returnLeaf = (current != null) && active.tree.contains(current) ? current : null;
        floating.returnSide = Side.EAST;
        floating.returnSize = Math.round(300 * getUIScale());
        active.floating.add(floating);
        refresh();
    }

    /**
     * Add something to be told when a page starts or stops being shown in the current workspace.
     */
    public void addPageVisibilityListener(BiConsumer<String, Boolean> listener) {
        visibilityListeners.add(listener);
    }

    /**
     * Add something to be told whenever the arrangement changes, for instance to update buttons, or to store the state.
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    // Folding the panels around the map away

    /**
     * Fold away the areas on one side of the map (or put them back), to make room for it.
     */
    public void setCollapsed(Side side, boolean collapsed) {
        if ((active != null) && (collapsed ? active.collapsed.add(side) : active.collapsed.remove(side))) {
            refresh();
        }
    }

    public boolean isCollapsed(Side side) {
        return (active != null) && active.collapsed.contains(side);
    }

    /**
     * Whether there are any areas on one side of the map.
     */
    public boolean hasAreasOn(Side side) {
        return (active != null) && sidesAroundMain().containsValue(side);
    }

    // Workspaces

    /**
     * Add a built-in arrangement, from which workspaces are made.
     *
     * @param id The key of the template.
     * @param name The name of the workspaces made from it.
     * @param icon The name of its {@link Icons icon}.
     * @param description What it is for.
     * @param layout The arrangement of the areas, in the text form described by {@link AreaTree}, with the sizes in
     *               pixels at a scale of 100%.
     * @param collapsed The sides of the map which are folded away.
     */
    public void addTemplate(String id, String name, String icon, String description, String layout, Side... collapsed) {
        // Check that it is valid
        AreaTree.parse(layout, 1.0f);
        templates.put(id, new Template(id, name, icon, description, layout, collapsed));
    }

    /**
     * Start with one workspace for each template, unless there are already workspaces.
     */
    public void createDefaultWorkspaces() {
        if (workspaces.isEmpty()) {
            for (Template template: templates.values()) {
                workspaces.add(createWorkspace(template, template.name));
            }
            activate(workspaces.get(0));
        }
    }

    public List<String> getWorkspaceNames() {
        final List<String> names = new ArrayList<>(workspaces.size());
        workspaces.forEach(workspace -> names.add(workspace.name));
        return names;
    }

    public int getActiveWorkspaceIndex() {
        return workspaces.indexOf(active);
    }

    public void activateWorkspace(int index) {
        activate(workspaces.get(index));
    }

    /**
     * Add a workspace made from a template, and switch to it.
     */
    public void newWorkspace(String templateId) {
        final Template template = templates.get(templateId);
        final Workspace workspace = createWorkspace(template, uniqueName(template.name));
        workspaces.add(workspaces.indexOf(active) + 1, workspace);
        activate(workspace);
    }

    /**
     * Add a copy of a workspace, and switch to it.
     */
    public void duplicateWorkspace(int index) {
        final Workspace original = workspaces.get(index);
        final Workspace copy = copy(original, uniqueName(original.name));
        workspaces.add(index + 1, copy);
        activate(copy);
    }

    public void renameWorkspace(int index, String name) {
        if ((name != null) && (! name.isBlank())) {
            workspaces.get(index).name = name.trim();
            fireChanged();
        }
    }

    /**
     * Delete a workspace. The last one cannot be deleted.
     */
    public void deleteWorkspace(int index) {
        if (workspaces.size() < 2) {
            return;
        }
        final Workspace workspace = workspaces.get(index);
        if (workspace == active) {
            activate(workspaces.get((index > 0) ? index - 1 : 1));
        }
        workspaces.remove(workspace);
        fireChanged();
    }

    public void moveWorkspace(int index, int newIndex) {
        if ((newIndex >= 0) && (newIndex < workspaces.size()) && (newIndex != index)) {
            workspaces.add(newIndex, workspaces.remove(index));
            fireChanged();
        }
    }

    /**
     * Put a workspace back the way its template has it.
     */
    public void resetWorkspace(int index) {
        final Workspace workspace = workspaces.get(index);
        final Template template = templates.getOrDefault(workspace.template, templates.values().iterator().next());
        final Workspace fresh = createWorkspace(template, workspace.name);
        if (workspace == active) {
            deactivate();
            workspaces.set(index, fresh);
            activate(fresh);
        } else {
            workspaces.set(index, fresh);
            fireChanged();
        }
    }

    /**
     * Replace all workspaces with one for each template.
     */
    public void resetAllWorkspaces() {
        deactivate();
        workspaces.clear();
        active = null;
        createDefaultWorkspaces();
    }

    public String getTemplateOf(int index) {
        final Template template = templates.get(workspaces.get(index).template);
        return (template != null) ? template.name : null;
    }

    // Areas

    /**
     * Split an area in two. The new area shows another page of the same kind, if there is one which is not shown
     * yet, or else is empty, with a choice of what to show in it.
     *
     * @param side The side of the area on which to put the new one.
     * @param size The size of the new area across the split, in pixels, or -1 for half the area.
     */
    void split(Leaf leaf, Side side, int size) {
        if (! active.tree.contains(leaf)) {
            return;
        }
        final int across = side.isHorizontal() ? leaf.bounds.width : leaf.bounds.height;
        if (size < 0) {
            size = (across - divider) / 2;
        }
        final boolean freePage = (leaf.group != null) && pagesOf(leaf.group).stream().anyMatch(page -> ! assignment.containsKey(page.getKey()));
        final Leaf newLeaf = new Leaf(freePage ? leaf.group : null, null);
        active.maximised = null;
        active.tree.insert(newLeaf, leaf, side, Math.max(1, size));
        activeLeaf = newLeaf;
        refresh();
    }

    void join(Leaf keep, Leaf absorbed) {
        if (active.tree.canJoin(keep, absorbed)) {
            active.maximised = null;
            active.tree.join(keep, absorbed);
            refresh();
        }
    }

    /**
     * Take an area away, its neighbour taking its space. The last area of the window is emptied instead.
     */
    void closeArea(Leaf leaf) {
        final FloatingArea floating = findFloating(leaf);
        if (floating != null) {
            closeWindow(floating);
            active.floating.remove(floating);
        } else if (leaf == active.tree.getRoot()) {
            leaf.group = null;
            leaf.page = null;
        } else if (active.tree.contains(leaf)) {
            if (active.maximised == leaf) {
                active.maximised = null;
            }
            active.tree.remove(leaf, null);
        }
        refresh();
    }

    void swapAreas(Leaf a, Leaf b) {
        if (a == b) {
            return;
        }
        final String aGroup = a.group, aPage = display.get(a);
        final String bPage = display.get(b);
        a.group = b.group;
        a.page = bPage;
        b.group = aGroup;
        b.page = aPage;
        refresh();
    }

    /**
     * Show a page in an area. If it is shown in another area, that area shows the page this one showed instead.
     */
    void showPageIn(Leaf leaf, String key) {
        final DockableFrame page = pages.get(key);
        if (page == null) {
            return;
        }
        final Leaf current = assignment.get(key);
        if (current == leaf) {
            return;
        }
        final String previous = display.get(leaf);
        leaf.group = page.getGroup();
        leaf.page = key;
        if (current != null) {
            if (previous != null) {
                current.group = pages.get(previous).getGroup();
                current.page = previous;
            } else {
                current.page = null;
            }
        }
        refresh();
    }

    /**
     * Move an area next to another one.
     *
     * @param side The side of the target on which to put it, or {@code null} to swap the two.
     */
    void moveArea(Leaf leaf, Leaf target, Side side) {
        if ((leaf == target) || (target == null)) {
            return;
        }
        if (side == null) {
            swapAreas(leaf, target);
            return;
        }
        final int size = dockSize(leaf, target, side);
        final FloatingArea floating = findFloating(leaf);
        if (floating != null) {
            closeWindow(floating);
            active.floating.remove(floating);
        } else if (active.tree.contains(leaf)) {
            if (active.tree.leaves().size() < 2) {
                return;
            }
            active.tree.remove(leaf, null);
        }
        active.maximised = null;
        active.tree.insert(leaf, target, side, size);
        activeLeaf = leaf;
        refresh();
    }

    /**
     * Show a page in a new area next to another one. The area which showed it shows another page of the same kind.
     */
    void movePageToNewArea(String key, Leaf target, Side side) {
        final DockableFrame page = pages.get(key);
        if ((page == null) || (! active.tree.contains(target))) {
            return;
        }
        final Leaf current = assignment.get(key);
        final int size = (current != null) ? dockSize(current, target, side) : ((side.isHorizontal() ? target.bounds.width : target.bounds.height) - divider) / 2;
        if (current != null) {
            current.page = null;
        }
        final Leaf leaf = new Leaf(page.getGroup(), key);
        active.maximised = null;
        active.tree.insert(leaf, target, side, size);
        activeLeaf = leaf;
        refresh();
    }

    /**
     * Move an area out of the main window into a window of its own.
     *
     * @param bounds The bounds of the new window on the screen, or {@code null} for those of the area.
     */
    void detach(Leaf leaf, Rectangle bounds) {
        if (findFloating(leaf) != null) {
            return;
        }
        if ((! active.tree.contains(leaf)) || (active.tree.leaves().size() < 2)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final FloatingArea floating = new FloatingArea(leaf);
        // Remember where it was, to put it back there
        final Split parent = leaf.parent;
        final int index = parent.children.indexOf(leaf);
        final Node neighbour = parent.children.get((index > 0) ? index - 1 : index + 1);
        floating.returnLeaf = closestLeaf(neighbour, leaf.bounds);
        floating.returnSide = parent.horizontal ? ((index > 0) ? Side.EAST : Side.WEST) : ((index > 0) ? Side.SOUTH : Side.NORTH);
        floating.returnSize = leaf.actualSize;
        if (bounds == null) {
            final AreaPanel panel = areaPanels.get(leaf);
            final Point location = ((panel != null) && panel.isShowing()) ? panel.getLocationOnScreen() : owner.getLocation();
            bounds = new Rectangle(location.x + 24, location.y + 24, Math.max(leaf.bounds.width, 320), Math.max(leaf.bounds.height, 240));
        }
        floating.bounds = bounds;
        if (active.maximised == leaf) {
            active.maximised = null;
        }
        active.tree.remove(leaf, null);
        active.floating.add(floating);
        refresh();
    }

    /**
     * Put a floating area back in the main window, where it came from if possible.
     */
    void dockBack(Leaf leaf) {
        final FloatingArea floating = findFloating(leaf);
        if (floating == null) {
            return;
        }
        closeWindow(floating);
        active.floating.remove(floating);
        Leaf target = ((floating.returnLeaf != null) && active.tree.contains(floating.returnLeaf)) ? floating.returnLeaf : getMainLeaf();
        if (target == null) {
            active.tree = new AreaTree(leaf, divider);
        } else {
            final Side side = (floating.returnSide != null) ? floating.returnSide : Side.EAST;
            final int across = side.isHorizontal() ? target.bounds.width : target.bounds.height;
            final int size = Math.max(Math.min(floating.returnSize, across / 2), Math.min(Math.round(160 * getUIScale()), across / 2));
            active.tree.insert(leaf, target, side, size);
        }
        activeLeaf = leaf;
        refresh();
    }

    void toggleMaximise(Leaf leaf) {
        if ((leaf == null) || (active == null) || (! active.tree.contains(leaf))) {
            return;
        }
        if (active.maximised == leaf) {
            active.maximised = null;
        } else if (active.tree.leaves().size() > 1) {
            active.maximised = leaf;
        }
        refresh();
    }

    void setOnTop(Leaf leaf, boolean onTop) {
        final FloatingArea floating = findFloating(leaf);
        if (floating != null) {
            floating.onTop = onTop;
            if (floating.window != null) {
                floating.window.setAlwaysOnTop(onTop);
            }
            fireChanged();
        }
    }

    boolean isOnTop(Leaf leaf) {
        final FloatingArea floating = findFloating(leaf);
        return (floating != null) && floating.onTop;
    }

    boolean isFloating(Leaf leaf) {
        return findFloating(leaf) != null;
    }

    boolean isMaximised(Leaf leaf) {
        return active.maximised == leaf;
    }

    boolean canDetach(Leaf leaf) {
        return active.tree.contains(leaf) && (active.tree.leaves().size() > 1);
    }

    boolean canClose(Leaf leaf) {
        return isFloating(leaf) || (active.tree.contains(leaf) && (active.tree.leaves().size() > 1));
    }

    /**
     * The neighbours with which an area could be joined, by side.
     */
    Map<Side, Leaf> joinableNeighbours(Leaf leaf) {
        final Map<Side, Leaf> result = new EnumMap<>(Side.class);
        if ((leaf.parent != null) && active.tree.contains(leaf)) {
            for (Node sibling: leaf.parent.children) {
                if ((sibling instanceof Leaf) && active.tree.canJoin(leaf, (Leaf) sibling)) {
                    result.put(AreaTree.sideOf(leaf, (Leaf) sibling), (Leaf) sibling);
                }
            }
        }
        return result;
    }

    /**
     * The area the user last clicked in, or else the map.
     */
    Leaf getActiveLeaf() {
        if (active == null) {
            return null;
        }
        if ((activeLeaf != null) && (active.tree.contains(activeLeaf) || isFloating(activeLeaf))) {
            return activeLeaf;
        }
        return getMainLeaf();
    }

    /**
     * The area under the mouse in the main window, or else the one the user last clicked in.
     */
    Leaf getLeafUnderMouse() {
        if (active == null) {
            return null;
        }
        final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if ((pointerInfo != null) && screen.isShowing()) {
            final Point point = pointerInfo.getLocation();
            SwingUtilities.convertPointFromScreen(point, screen);
            for (Leaf leaf: active.tree.leaves()) {
                final AreaPanel panel = areaPanels.get(leaf);
                if ((panel != null) && panel.isVisible() && panel.getBounds().contains(point)) {
                    return leaf;
                }
            }
        }
        return getActiveLeaf();
    }

    /**
     * The area which shows the map, or else the 3D view, or else the largest one.
     */
    Leaf getMainLeaf() {
        if (active == null) {
            return null;
        }
        Leaf best = null;
        int bestScore = -1;
        for (Leaf leaf: active.tree.leaves()) {
            final String shown = display.get(leaf);
            final int score = MAIN_PAGE.equals(shown) ? Integer.MAX_VALUE : MAIN_PAGE_2.equals(shown) ? Integer.MAX_VALUE - 1 : leaf.bounds.width * leaf.bounds.height;
            if (score > bestScore) {
                best = leaf;
                bestScore = score;
            }
        }
        return best;
    }

    // Menus

    /**
     * A menu with all the pages, to show and hide them.
     */
    public JMenu createPanelsMenu(String text) {
        final JMenu menu = new JMenu(text);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                String lastCategory = null;
                for (String category: categories) {
                    for (DockableFrame page: pagesIn(category)) {
                        if ((lastCategory != null) && (! category.equals(lastCategory))) {
                            menu.addSeparator();
                        }
                        lastCategory = category;
                        final String key = page.getKey();
                        final JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(page.getTitle(), page.getFrameIcon(), isPageShown(key));
                        menuItem.addActionListener(event -> {
                            if (menuItem.isSelected()) {
                                showPage(key);
                            } else {
                                hidePage(key);
                            }
                        });
                        menu.add(menuItem);
                    }
                }
            }

            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        return menu;
    }

    /**
     * A menu with the workspaces, to switch between them and manage them.
     */
    public JMenu createWorkspacesMenu(String text) {
        final JMenu menu = new JMenu(text);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                if (active == null) {
                    return;
                }
                final ButtonGroup buttonGroup = new ButtonGroup();
                for (int i = 0; i < workspaces.size(); i++) {
                    final int index = i;
                    final JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(workspaces.get(i).name, workspaces.get(i) == active);
                    menuItem.addActionListener(event -> activateWorkspace(index));
                    buttonGroup.add(menuItem);
                    menu.add(menuItem);
                }
                menu.addSeparator();
                menu.add(createNewWorkspaceMenu());
                final int index = getActiveWorkspaceIndex();
                JMenuItem menuItem = new JMenuItem("Duplicate “" + active.name + "”");
                menuItem.addActionListener(event -> duplicateWorkspace(index));
                menu.add(menuItem);
                menuItem = new JMenuItem("Rename…");
                menuItem.addActionListener(event -> promptRename(index));
                menu.add(menuItem);
                menuItem = new JMenuItem("Delete");
                menuItem.setEnabled(workspaces.size() > 1);
                menuItem.addActionListener(event -> deleteWorkspace(index));
                menu.add(menuItem);
                menu.addSeparator();
                final String templateName = getTemplateOf(index);
                menuItem = new JMenuItem((templateName != null) ? "Reset to “" + templateName + "”" : "Reset");
                menuItem.addActionListener(event -> resetWorkspace(index));
                menu.add(menuItem);
                menuItem = new JMenuItem("Reset all workspaces");
                menuItem.addActionListener(event -> {
                    if (JOptionPane.showConfirmDialog(owner, "Put back the built-in workspaces, removing the ones you added and undoing your changes?", "Reset All Workspaces", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        resetAllWorkspaces();
                    }
                });
                menu.add(menuItem);
            }

            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        return menu;
    }

    /**
     * A menu with the commands for the area the user last clicked in, like the Area menu of Blender.
     */
    public JMenu createAreaMenu(String text) {
        final JMenu menu = new JMenu(text);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                fillAreaMenu(menu, getActiveLeaf());
            }

            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        return menu;
    }

    JMenu createNewWorkspaceMenu() {
        final JMenu menu = new JMenu("New workspace");
        for (Template template: templates.values()) {
            final JMenuItem menuItem = new JMenuItem(template.name, Icons.get(template.icon, 16));
            menuItem.setToolTipText(template.description);
            menuItem.addActionListener(event -> newWorkspace(template.id));
            menu.add(menuItem);
        }
        return menu;
    }

    JPopupMenu createWorkspacePopupMenu(int index) {
        final JPopupMenu menu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem("Rename…");
        menuItem.addActionListener(event -> promptRename(index));
        menu.add(menuItem);
        menuItem = new JMenuItem("Duplicate");
        menuItem.addActionListener(event -> duplicateWorkspace(index));
        menu.add(menuItem);
        final String templateName = getTemplateOf(index);
        menuItem = new JMenuItem((templateName != null) ? "Reset to “" + templateName + "”" : "Reset");
        menuItem.addActionListener(event -> resetWorkspace(index));
        menu.add(menuItem);
        menu.addSeparator();
        menuItem = new JMenuItem("Move left");
        menuItem.setEnabled(index > 0);
        menuItem.addActionListener(event -> moveWorkspace(index, index - 1));
        menu.add(menuItem);
        menuItem = new JMenuItem("Move right");
        menuItem.setEnabled(index < workspaces.size() - 1);
        menuItem.addActionListener(event -> moveWorkspace(index, index + 1));
        menu.add(menuItem);
        menu.addSeparator();
        menuItem = new JMenuItem("Delete");
        menuItem.setEnabled(workspaces.size() > 1);
        menuItem.addActionListener(event -> deleteWorkspace(index));
        menu.add(menuItem);
        return menu;
    }

    JPopupMenu createTemplatePopupMenu() {
        final JPopupMenu menu = new JPopupMenu();
        final JLabel label = new JLabel("New workspace");
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        label.putClientProperty("FlatLaf.styleClass", "semibold");
        menu.add(label);
        for (Template template: templates.values()) {
            final JMenuItem menuItem = new JMenuItem("<html><b>" + template.name + "</b><br><span style=\"color: #a1a1aa\">" + template.description + "</span></html>", Icons.get(template.icon, 18));
            menuItem.setIconTextGap(10);
            menuItem.addActionListener(event -> newWorkspace(template.id));
            menu.add(menuItem);
        }
        if (active != null) {
            menu.addSeparator();
            final int index = getActiveWorkspaceIndex();
            final JMenuItem menuItem = new JMenuItem("Duplicate “" + active.name + "”", Icons.get("copy", 16));
            menuItem.addActionListener(event -> duplicateWorkspace(index));
            menu.add(menuItem);
        }
        return menu;
    }

    void promptRename(int index) {
        final Object name = JOptionPane.showInputDialog(owner, "Name of the workspace:", "Rename Workspace", JOptionPane.QUESTION_MESSAGE, null, null, workspaces.get(index).name);
        if (name != null) {
            renameWorkspace(index, name.toString());
        }
    }

    /**
     * The commands for an area: split, maximise, detach, close.
     */
    void fillAreaMenu(JComponent menu, Leaf leaf) {
        if (leaf == null) {
            return;
        }
        final boolean floating = isFloating(leaf);
        JMenuItem menuItem = new JMenuItem("Split vertically", Icons.get("columns-2", 16));
        menuItem.setToolTipText("Divide the area into two, side by side. You can also drag from a corner of the area into it");
        menuItem.setEnabled(! floating);
        menuItem.addActionListener(e -> split(leaf, Side.EAST, -1));
        menu.add(menuItem);
        menuItem = new JMenuItem("Split horizontally", Icons.get("rows-2", 16));
        menuItem.setToolTipText("Divide the area into two, one above the other. You can also drag from a corner of the area into it");
        menuItem.setEnabled(! floating);
        menuItem.addActionListener(e -> split(leaf, Side.SOUTH, -1));
        menu.add(menuItem);
        final Map<Side, Leaf> neighbours = joinableNeighbours(leaf);
        if (! neighbours.isEmpty()) {
            final JMenu joinMenu = new JMenu("Join with");
            for (Map.Entry<Side, Leaf> entry: neighbours.entrySet()) {
                final Leaf neighbour = entry.getValue();
                final String shown = display.get(neighbour);
                final String name = (shown != null) ? pages.get(shown).getTitle() : "Empty area";
                menuItem = new JMenuItem(name + " (" + SIDE_NAMES.get(entry.getKey()) + ")");
                menuItem.addActionListener(e -> join(leaf, neighbour));
                joinMenu.add(menuItem);
            }
            menu.add(joinMenu);
        }
        if (menu instanceof JMenu) {
            ((JMenu) menu).addSeparator();
        } else {
            ((JPopupMenu) menu).addSeparator();
        }
        if (floating) {
            menuItem = new JCheckBoxMenuItem("Keep on top of other applications", isOnTop(leaf));
            menuItem.addActionListener(e -> setOnTop(leaf, ! isOnTop(leaf)));
            menu.add(menuItem);
            menuItem = new JMenuItem("Put back in the main window", Icons.get("picture-in-picture", 16));
            menuItem.addActionListener(e -> dockBack(leaf));
            menu.add(menuItem);
        } else {
            menuItem = new JMenuItem(isMaximised(leaf) ? "Restore area" : "Maximise area", Icons.get(isMaximised(leaf) ? "minimize-2" : "maximize-2", 16));
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK));
            menuItem.setEnabled(isMaximised(leaf) || (active.tree.leaves().size() > 1));
            menuItem.addActionListener(e -> toggleMaximise(leaf));
            menu.add(menuItem);
            menuItem = new JMenuItem("Open in a new window", Icons.get("picture-in-picture-2", 16));
            menuItem.setToolTipText("Detach the area into a window of its own, which floats over the main window. You can also drag its header out of the window");
            menuItem.setEnabled(canDetach(leaf));
            menuItem.addActionListener(e -> detach(leaf, null));
            menu.add(menuItem);
        }
        menuItem = new JMenuItem("Close area", Icons.get("x", 16));
        menuItem.setEnabled(canClose(leaf));
        menuItem.addActionListener(e -> closeArea(leaf));
        menu.add(menuItem);
    }

    /**
     * Tabs for the header of the window to switch between the workspaces.
     */
    public JComponent createWorkspaceTabs() {
        final WorkspaceTabs tabs = new WorkspaceTabs(this);
        addChangeListener(tabs::update);
        return tabs;
    }

    // Persistence

    /**
     * Store the workspaces and their arrangements.
     */
    public byte[] saveState() {
        final StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\t').append(FORMAT_VERSION).append('\n');
        for (Workspace workspace: workspaces) {
            final StringBuilder collapsed = new StringBuilder();
            workspace.collapsed.forEach(side -> collapsed.append((collapsed.length() > 0) ? "," : "").append(side.name()));
            sb.append("workspace\t").append(encode(workspace.name)).append('\t').append(encode(workspace.template)).append('\t')
                    .append(collapsed.length() > 0 ? collapsed : "-").append('\t').append(workspace.tree.toString()).append('\n');
            final List<Leaf> leaves = workspace.tree.leaves();
            for (FloatingArea floating: workspace.floating) {
                if ((floating.window != null) && floating.window.isShowing()) {
                    floating.bounds = floating.window.getBounds();
                }
                final Rectangle b = floating.bounds;
                sb.append("float\t").append(AreaTree.toString(floating.leaf)).append('\t')
                        .append(b.x).append('\t').append(b.y).append('\t').append(b.width).append('\t').append(b.height).append('\t')
                        .append(leaves.indexOf(floating.returnLeaf)).append('\t').append((floating.returnSide != null) ? floating.returnSide.name() : "-").append('\t')
                        .append(floating.returnSize).append('\t').append(floating.onTop).append('\n');
            }
        }
        sb.append("active\t").append(Math.max(0, workspaces.indexOf(active))).append('\n');
        return sb.toString().getBytes(UTF_8);
    }

    /**
     * Restore the workspaces stored with {@link #saveState()}. Data in another format, such as layouts stored by
     * previous versions of WorldPainter, is ignored.
     *
     * @return {@code true} if the workspaces were restored.
     */
    public boolean loadState(byte[] data) {
        if (data == null) {
            return false;
        }
        final String[] lines = new String(data, UTF_8).split("\n");
        if ((lines.length == 0) || (! lines[0].startsWith(MAGIC + '\t'))) {
            return false;
        }
        final List<Workspace> loaded = new ArrayList<>();
        int activeIndex = 0;
        try {
            for (int i = 1; i < lines.length; i++) {
                final String[] tokens = lines[i].split("\t");
                switch (tokens[0]) {
                    case "workspace":
                        final Workspace workspace = new Workspace(decode(tokens[1]), decode(tokens[2]), new AreaTree(AreaTree.parse(tokens[4], 1.0f), divider));
                        if (! tokens[3].equals("-")) {
                            for (String side: tokens[3].split(",")) {
                                workspace.collapsed.add(Side.valueOf(side));
                            }
                        }
                        loaded.add(workspace);
                        break;
                    case "float":
                        final Workspace target = loaded.get(loaded.size() - 1);
                        final Node node = AreaTree.parse(tokens[1], 1.0f);
                        if (node instanceof Leaf) {
                            final FloatingArea floating = new FloatingArea((Leaf) node);
                            floating.bounds = new Rectangle(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[4]), Integer.parseInt(tokens[5]));
                            final int returnIndex = Integer.parseInt(tokens[6]);
                            final List<Leaf> leaves = target.tree.leaves();
                            floating.returnLeaf = ((returnIndex >= 0) && (returnIndex < leaves.size())) ? leaves.get(returnIndex) : null;
                            floating.returnSide = tokens[7].equals("-") ? null : Side.valueOf(tokens[7]);
                            floating.returnSize = Integer.parseInt(tokens[8]);
                            floating.onTop = Boolean.parseBoolean(tokens[9]);
                            target.floating.add(floating);
                        }
                        break;
                    case "active":
                        activeIndex = Integer.parseInt(tokens[1]);
                        break;
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Ignoring corrupt workspace data", e);
            return false;
        }
        if (loaded.isEmpty()) {
            return false;
        }
        deactivate();
        workspaces.clear();
        workspaces.addAll(loaded);
        active = null;
        activate(workspaces.get(Math.max(0, Math.min(activeIndex, workspaces.size() - 1))));
        return true;
    }

    // Package private, for the components of the areas

    JFrame getOwner() {
        return owner;
    }

    int getDividerSize() {
        return divider;
    }

    Workspace getActive() {
        return active;
    }

    AreaDrag getDrag() {
        return drag;
    }

    AreaPanel getAreaPanel(Leaf leaf) {
        return areaPanels.get(leaf);
    }

    Collection<AreaPanel> getAreaPanels() {
        return areaPanels.values();
    }

    Collection<FloatingWindow> getFloatingWindows() {
        final List<FloatingWindow> windows = new ArrayList<>();
        for (FloatingArea floating: active.floating) {
            if (floating.window != null) {
                windows.add(floating.window);
            }
        }
        return windows;
    }

    /**
     * The key of the page an area shows, or {@code null} if it is empty.
     */
    String getShownPage(Leaf leaf) {
        return display.get(leaf);
    }

    /**
     * The area in which a page is shown, or {@code null}.
     */
    Leaf getLeafOf(String key) {
        return assignment.get(key);
    }

    EditorGroup getGroup(String id) {
        return groups.get(id);
    }

    List<String> getCategories() {
        return categories;
    }

    List<DockableFrame> pagesOf(String group) {
        final List<DockableFrame> result = new ArrayList<>();
        for (DockableFrame page: pages.values()) {
            if (page.getGroup().equals(group)) {
                result.add(page);
            }
        }
        return result;
    }

    List<DockableFrame> pagesIn(String category) {
        final List<DockableFrame> result = new ArrayList<>();
        for (DockableFrame page: pages.values()) {
            if (category.equals(categoryOf(page))) {
                result.add(page);
            }
        }
        return result;
    }

    String categoryOf(DockableFrame page) {
        return (page.getCategory() != null) ? page.getCategory() : groups.get(page.getGroup()).category;
    }

    /**
     * Lay out the areas of the current workspace in the screen.
     */
    void layoutScreen() {
        if (active == null) {
            return;
        }
        final Insets insets = screen.getInsets();
        final Rectangle bounds = new Rectangle(insets.left, insets.top, screen.getWidth() - insets.left - insets.right, screen.getHeight() - insets.top - insets.bottom);
        dividers.clear();
        if ((active.maximised != null) && active.tree.contains(active.maximised)) {
            for (Leaf leaf: active.tree.leaves()) {
                final AreaPanel panel = areaPanels.get(leaf);
                if (panel != null) {
                    if (leaf == active.maximised) {
                        panel.setBounds(bounds);
                        panel.setVisible(true);
                    } else {
                        panel.setVisible(false);
                    }
                }
            }
            return;
        }
        collapsedNodes = collapsedNodes();
        active.tree.layout(bounds, sizes);
        for (Leaf leaf: active.tree.leaves()) {
            final AreaPanel panel = areaPanels.get(leaf);
            if (panel != null) {
                final boolean visible = (! leaf.bounds.isEmpty()) && (! isInCollapsed(leaf));
                panel.setVisible(visible);
                if (visible) {
                    panel.setBounds(leaf.bounds);
                }
            }
        }
        if (active.tree.getRoot() != null) {
            AreaTree.visit(active.tree.getRoot(), node -> {
                if ((node instanceof Split) && (! isInCollapsed(node))) {
                    dividers.addAll(((Split) node).dividers);
                }
            });
        }
    }

    List<AreaTree.Divider> getDividers() {
        return dividers;
    }

    /**
     * Move the divider between two areas.
     *
     * @return How far it actually moved.
     */
    int moveDivider(AreaTree.Divider divider, int delta) {
        final int moved = active.tree.moveDivider(divider.before, divider.after, delta, sizes);
        if (moved != 0) {
            screen.revalidate();
            screen.repaint();
            scheduleSave();
        }
        return moved;
    }

    /**
     * The size an area gets when it is put next to another one: as wide (or high) as it is now, but at least a
     * quarter and at most half of the area next to which it is put.
     */
    int dockSize(Leaf leaf, Leaf target, Side side) {
        final int across = (side.isHorizontal() ? target.bounds.width : target.bounds.height) - divider;
        int current = side.isHorizontal() ? leaf.bounds.width : leaf.bounds.height;
        final FloatingArea floating = findFloating(leaf);
        if ((floating != null) && (floating.window != null)) {
            final Container contentPane = floating.window.getContentPane();
            current = side.isHorizontal() ? contentPane.getWidth() : contentPane.getHeight();
        }
        if (current <= 0) {
            current = across / 2;
        }
        return Math.max(across / 4, Math.min(across / 2, current));
    }

    // Implementation

    private Workspace createWorkspace(Template template, String name) {
        final Workspace workspace = new Workspace(name, template.id, new AreaTree(AreaTree.parse(template.layout, getUIScale()), divider));
        workspace.collapsed.addAll(template.collapsed);
        return workspace;
    }

    private Workspace copy(Workspace original, String name) {
        if (original == active) {
            // Make sure the bounds of the floating windows are up to date
            for (FloatingArea floating: original.floating) {
                if ((floating.window != null) && floating.window.isShowing()) {
                    floating.bounds = floating.window.getBounds();
                }
            }
        }
        final Workspace copy = new Workspace(name, original.template, new AreaTree(AreaTree.parse(original.tree.toString(), 1.0f), divider));
        copy.collapsed.addAll(original.collapsed);
        // Pages can only be in one place at a time, and floating windows hold only one, so the copy starts without
        // them; their pages are free to be shown elsewhere
        return copy;
    }

    private String uniqueName(String name) {
        final String base = name.replaceAll("\\.\\d{3}$", "");
        final Set<String> names = new HashSet<>(getWorkspaceNames());
        if (! names.contains(base)) {
            return base;
        }
        for (int i = 1; ; i++) {
            final String candidate = String.format("%s.%03d", base, i);
            if (! names.contains(candidate)) {
                return candidate;
            }
        }
    }

    private void activate(Workspace workspace) {
        if (workspace == active) {
            return;
        }
        deactivate();
        active = workspace;
        refresh();
    }

    /**
     * Take down the areas and windows of the current workspace, to switch to another one.
     */
    private void deactivate() {
        if (active == null) {
            return;
        }
        for (FloatingArea floating: active.floating) {
            closeWindow(floating);
        }
        for (AreaPanel panel: areaPanels.values()) {
            panel.dispose();
        }
        areaPanels.clear();
        screen.removeAll();
        final Set<String> wasShown = new HashSet<>(assignment.keySet());
        assignment.clear();
        display.clear();
        for (String key: wasShown) {
            fireVisibility(key, false);
        }
    }

    /**
     * Bring the components up to date with the arrangement of the current workspace: decide which page each area
     * shows, create and remove the area components and floating windows, and put the pages in them.
     */
    void refresh() {
        if (active == null) {
            return;
        }
        // Decide which page each area shows
        final Map<String, Leaf> oldAssignment = new HashMap<>(assignment);
        assignPages();

        // The areas of the main window
        final List<Leaf> leaves = active.tree.leaves();
        final Set<Leaf> wanted = new HashSet<>(leaves);
        for (FloatingArea floating: active.floating) {
            wanted.add(floating.leaf);
        }
        for (Iterator<Map.Entry<Leaf, AreaPanel>> i = areaPanels.entrySet().iterator(); i.hasNext(); ) {
            final Map.Entry<Leaf, AreaPanel> entry = i.next();
            final Leaf leaf = entry.getKey();
            final AreaPanel panel = entry.getValue();
            final boolean floating = isFloating(leaf);
            if ((! wanted.contains(leaf)) || (panel.isFloating() != floating)) {
                if (panel.getParent() == screen) {
                    screen.remove(panel);
                }
                panel.dispose();
                i.remove();
            }
        }
        for (Leaf leaf: leaves) {
            AreaPanel panel = areaPanels.get(leaf);
            if (panel == null) {
                panel = new AreaPanel(this, leaf, false);
                areaPanels.put(leaf, panel);
            }
            if (panel.getParent() != screen) {
                screen.add(panel);
            }
        }
        // The floating windows
        for (FloatingArea floating: active.floating) {
            AreaPanel panel = areaPanels.get(floating.leaf);
            if (panel == null) {
                panel = new AreaPanel(this, floating.leaf, true);
                areaPanels.put(floating.leaf, panel);
            }
            if (floating.window == null) {
                floating.window = new FloatingWindow(this, floating, panel);
                floating.window.open();
            }
        }
        // Put the pages in their areas
        for (Map.Entry<Leaf, AreaPanel> entry: areaPanels.entrySet()) {
            final String key = display.get(entry.getKey());
            entry.getValue().update((key != null) ? pages.get(key) : null);
        }
        for (FloatingArea floating: active.floating) {
            floating.window.update();
        }

        screen.revalidate();
        screen.repaint();

        // Tell who is interested which pages appeared and disappeared
        for (String key: oldAssignment.keySet()) {
            if (! assignment.containsKey(key)) {
                fireVisibility(key, false);
            }
        }
        for (String key: assignment.keySet()) {
            if (! oldAssignment.containsKey(key)) {
                fireVisibility(key, true);
            }
        }
        fireChanged();
    }

    private void assignPages() {
        assignment.clear();
        display.clear();
        final List<Leaf> leaves = allLeaves(active);
        // First the areas which can show the page they want
        for (Leaf leaf: leaves) {
            if ((leaf.page != null) && pages.containsKey(leaf.page) && (! assignment.containsKey(leaf.page))) {
                if (leaf.group == null) {
                    leaf.group = pages.get(leaf.page).getGroup();
                }
                assignment.put(leaf.page, leaf);
                display.put(leaf, leaf.page);
            }
        }
        // Then the others show another page of the same kind, if there is one which is not shown yet
        for (Leaf leaf: leaves) {
            if ((! display.containsKey(leaf)) && (leaf.group != null)) {
                for (DockableFrame page: pagesOf(leaf.group)) {
                    if (! assignment.containsKey(page.getKey())) {
                        assignment.put(page.getKey(), leaf);
                        display.put(leaf, page.getKey());
                        break;
                    }
                }
            }
        }
    }

    private List<Leaf> allLeaves(Workspace workspace) {
        final List<Leaf> leaves = workspace.tree.leaves();
        for (FloatingArea floating: workspace.floating) {
            leaves.add(floating.leaf);
        }
        return leaves;
    }

    private FloatingArea findFloating(Leaf leaf) {
        if (active != null) {
            for (FloatingArea floating: active.floating) {
                if (floating.leaf == leaf) {
                    return floating;
                }
            }
        }
        return null;
    }

    private void closeWindow(FloatingArea floating) {
        if (floating.window != null) {
            if (floating.window.isShowing()) {
                floating.bounds = floating.window.getBounds();
            }
            final FloatingWindow window = floating.window;
            floating.window = null;
            window.close();
        }
        final AreaPanel panel = areaPanels.remove(floating.leaf);
        if (panel != null) {
            panel.dispose();
        }
    }

    private Rectangle defaultFloatingBounds(DockableFrame page) {
        final Rectangle window = owner.getBounds();
        final Dimension preferred = page.getPreferredSize();
        final int width = Math.max(Math.round(320 * getUIScale()), Math.min(preferred.width + 40, window.width / 2));
        final int height = Math.max(Math.round(240 * getUIScale()), Math.min(preferred.height + 60, window.height * 2 / 3));
        return new Rectangle(window.x + window.width - width - Math.round(48 * getUIScale()), window.y + Math.round(96 * getUIScale()), width, height);
    }

    private static Leaf closestLeaf(Node node, Rectangle bounds) {
        final Point centre = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        Leaf best = null;
        double bestDistance = Double.MAX_VALUE;
        final List<Leaf> leaves = new ArrayList<>();
        AreaTree.visit(node, n -> {
            if (n instanceof Leaf) {
                leaves.add((Leaf) n);
            }
        });
        for (Leaf leaf: leaves) {
            final double distance = centre.distance(leaf.bounds.getCenterX(), leaf.bounds.getCenterY());
            if (distance < bestDistance) {
                best = leaf;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Which side of the map each node around it is on: the other children of the splits which hold the map.
     */
    private Map<Node, Side> sidesAroundMain() {
        final Map<Node, Side> sides = new HashMap<>();
        final Leaf main = getMainLeaf();
        if (main == null) {
            return sides;
        }
        Node node = main;
        while (node.parent != null) {
            final Split split = node.parent;
            boolean before = true;
            for (Node child: split.children) {
                if (child == node) {
                    before = false;
                } else {
                    sides.put(child, split.horizontal ? (before ? Side.WEST : Side.EAST) : (before ? Side.NORTH : Side.SOUTH));
                }
            }
            node = split;
        }
        return sides;
    }

    private Set<Node> collapsedNodes() {
        if (active.collapsed.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<Node> result = new HashSet<>();
        sidesAroundMain().forEach((node, side) -> {
            if (active.collapsed.contains(side)) {
                result.add(node);
            }
        });
        return result;
    }

    private boolean isInCollapsed(Node node) {
        while (node != null) {
            if (collapsedNodes.contains(node)) {
                return true;
            }
            node = node.parent;
        }
        return false;
    }

    private void fireVisibility(String key, boolean shown) {
        for (BiConsumer<String, Boolean> listener: visibilityListeners) {
            listener.accept(key, shown);
        }
    }

    private void fireChanged() {
        for (Runnable listener: changeListeners) {
            listener.run();
        }
        scheduleSave();
    }

    void scheduleSave() {
        saveTimer.restart();
    }

    private void fireStateChanged() {
        if (stateListener != null) {
            stateListener.run();
        }
    }

    /**
     * Set something to run, a little after the arrangement last changed, to store it.
     */
    public void setStateListener(Runnable stateListener) {
        this.stateListener = stateListener;
    }

    /**
     * Keep the areas in sync when the key, title or icon of a page changes.
     */
    private void pageChanged(PropertyChangeEvent event) {
        final DockableFrame page = (DockableFrame) event.getSource();
        switch (event.getPropertyName()) {
            case "key":
                final String oldKey = (String) event.getOldValue(), newKey = page.getKey();
                pages.remove(oldKey);
                pages.put(newKey, page);
                for (Workspace workspace: workspaces) {
                    for (Leaf leaf: allLeaves(workspace)) {
                        if (oldKey.equals(leaf.page)) {
                            leaf.page = newKey;
                        }
                    }
                }
                refresh();
                break;
            case "title":
            case "frameIcon":
                for (AreaPanel panel: areaPanels.values()) {
                    panel.pageChanged(page);
                }
                for (FloatingArea floating: active.floating) {
                    if (floating.window != null) {
                        floating.window.update();
                    }
                }
                break;
        }
    }

    private static String encode(String str) {
        return (str != null) ? URLEncoder.encode(str, UTF_8) : "";
    }

    private static String decode(String str) {
        return str.isEmpty() ? null : URLDecoder.decode(str, UTF_8);
    }

    private final JFrame owner;
    private final int divider;
    private final ScreenPanel screen;
    private final AreaDrag drag;
    private final Timer saveTimer;
    private final List<String> categories = new ArrayList<>();
    private final Map<String, EditorGroup> groups = new LinkedHashMap<>();
    private final Map<String, DockableFrame> pages = new LinkedHashMap<>();
    private final Map<String, Template> templates = new LinkedHashMap<>();
    private final List<Workspace> workspaces = new ArrayList<>();
    private final Map<Leaf, AreaPanel> areaPanels = new HashMap<>();
    private final Map<String, Leaf> assignment = new HashMap<>();
    private final Map<Leaf, String> display = new HashMap<>();
    private final List<AreaTree.Divider> dividers = new ArrayList<>();
    private final List<BiConsumer<String, Boolean>> visibilityListeners = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final PropertyChangeListener pageListener = this::pageChanged;
    private Workspace active;
    private Leaf activeLeaf;
    private Set<Node> collapsedNodes = Collections.emptySet();
    private Runnable stateListener;

    private final AreaTree.Sizes sizes = new AreaTree.Sizes() {
        @Override
        public int getPreferredSize(Leaf leaf, boolean horizontal) {
            final AreaPanel panel = areaPanels.get(leaf);
            if (panel == null) {
                return 0;
            }
            final Dimension size = panel.getPreferredSize();
            return horizontal ? size.width : size.height;
        }

        @Override
        public int getMinimumSize(Leaf leaf, boolean horizontal) {
            final AreaPanel panel = areaPanels.get(leaf);
            if (panel == null) {
                return 0;
            }
            final Dimension size = panel.getMinimumSize();
            return horizontal ? size.width : size.height;
        }

        @Override
        public boolean isCollapsed(Node node) {
            return collapsedNodes.contains(node);
        }
    };

    /**
     * The page which is the heart of the window; the panels on either side of it are those which can be folded away.
     */
    public static final String MAIN_PAGE = "map";
    /**
     * The page which is the heart of the window if the {@link #MAIN_PAGE} is not shown.
     */
    public static final String MAIN_PAGE_2 = "view3d";

    private static final String MAGIC = "WPAREAS";
    private static final int FORMAT_VERSION = 1;
    private static final Map<Side, String> SIDE_NAMES = new EnumMap<>(Map.of(Side.WEST, "left", Side.EAST, "right", Side.NORTH, "above", Side.SOUTH, "below"));
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AreaManager.class);

    /**
     * A kind of editor.
     */
    static final class EditorGroup {
        EditorGroup(String id, String title, String icon, String category) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.category = category;
        }

        final String id, title, icon, category;
    }

    static final class Template {
        Template(String id, String name, String icon, String description, String layout, Side... collapsed) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.layout = layout;
            this.collapsed = Arrays.asList(collapsed);
        }

        final String id, name, icon, description, layout;
        final List<Side> collapsed;
    }

    static final class Workspace {
        Workspace(String name, String template, AreaTree tree) {
            this.name = name;
            this.template = template;
            this.tree = tree;
        }

        String name;
        final String template;
        AreaTree tree;
        final List<FloatingArea> floating = new ArrayList<>();
        final EnumSet<Side> collapsed = EnumSet.noneOf(Side.class);
        Leaf maximised;
    }

    static final class FloatingArea {
        FloatingArea(Leaf leaf) {
            this.leaf = leaf;
        }

        final Leaf leaf;
        Rectangle bounds;
        Leaf returnLeaf;
        Side returnSide;
        int returnSize;
        boolean onTop;
        FloatingWindow window;
    }
}
