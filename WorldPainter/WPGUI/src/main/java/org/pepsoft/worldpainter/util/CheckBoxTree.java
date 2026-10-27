package org.pepsoft.worldpainter.util;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tree with a check box in front of each node. Checking or unchecking a node does the same to all its descendants,
 * and a node is automatically checked when all its children are. Nodes of which only some descendants are checked are
 * shown in the mixed state.
 */
public class CheckBoxTree extends JTree {
    public CheckBoxTree() {
        setCellRenderer(new CheckBoxTreeCellRenderer());
        setSelectionModel(null);
        setToggleClickCount(0);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                final int row = getRowForLocation(e.getX(), e.getY());
                if (row != -1) {
                    final Rectangle bounds = getRowBounds(row);
                    if (e.getX() < bounds.x + checkBoxWidth()) {
                        toggle(getPathForRow(row));
                    }
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_SPACE) && (getLeadSelectionPath() != null)) {
                    toggle(getLeadSelectionPath());
                }
            }
        });
    }

    @Override
    public void setModel(TreeModel newModel) {
        if (checkedNodes != null) {
            checkedNodes.clear();
        }
        super.setModel(newModel);
    }

    /**
     * Set the renderer used to render the node itself, after the check box.
     */
    public void setActualRenderer(TreeCellRenderer actualRenderer) {
        ((CheckBoxTreeCellRenderer) getCellRenderer()).actualRenderer = actualRenderer;
        repaint();
    }

    public boolean isChecked(TreePath path) {
        return checkedNodes.contains(path.getLastPathComponent());
    }

    public void setChecked(TreePath path, boolean checked) {
        setSubtreeChecked(path.getLastPathComponent(), checked);
        updateAncestors(path);
        repaint();
        fireStateChanged();
    }

    /**
     * Get the minimal set of checked paths: if a node and all its descendants are checked only the path to that node
     * is included.
     */
    public TreePath[] getCheckedPaths() {
        final List<TreePath> paths = new ArrayList<>();
        final Object root = getModel().getRoot();
        if (root != null) {
            collectCheckedPaths(new TreePath(root), paths);
        }
        return paths.toArray(new TreePath[0]);
    }

    public int getCheckedCount() {
        return getCheckedPaths().length;
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(ChangeListener.class, listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(ChangeListener.class, listener);
    }

    private void toggle(TreePath path) {
        setChecked(path, ! isChecked(path));
    }

    private void collectCheckedPaths(TreePath path, List<TreePath> paths) {
        final Object node = path.getLastPathComponent();
        if (checkedNodes.contains(node)) {
            paths.add(path);
        } else {
            final TreeModel model = getModel();
            for (int i = 0; i < model.getChildCount(node); i++) {
                collectCheckedPaths(path.pathByAddingChild(model.getChild(node, i)), paths);
            }
        }
    }

    private void setSubtreeChecked(Object node, boolean checked) {
        if (checked) {
            checkedNodes.add(node);
        } else {
            checkedNodes.remove(node);
        }
        final TreeModel model = getModel();
        for (int i = 0; i < model.getChildCount(node); i++) {
            setSubtreeChecked(model.getChild(node, i), checked);
        }
    }

    private void updateAncestors(TreePath path) {
        final TreeModel model = getModel();
        for (TreePath parentPath = path.getParentPath(); parentPath != null; parentPath = parentPath.getParentPath()) {
            final Object parent = parentPath.getLastPathComponent();
            boolean allChecked = true;
            for (int i = 0; i < model.getChildCount(parent); i++) {
                if (! checkedNodes.contains(model.getChild(parent, i))) {
                    allChecked = false;
                    break;
                }
            }
            if (allChecked) {
                checkedNodes.add(parent);
            } else {
                checkedNodes.remove(parent);
            }
        }
    }

    private boolean isPartiallyChecked(Object node) {
        final TreeModel model = getModel();
        for (int i = 0; i < model.getChildCount(node); i++) {
            final Object child = model.getChild(node, i);
            if (checkedNodes.contains(child) || isPartiallyChecked(child)) {
                return true;
            }
        }
        return false;
    }

    private int checkBoxWidth() {
        return ((CheckBoxTreeCellRenderer) getCellRenderer()).checkBox.getPreferredSize().width;
    }

    private void fireStateChanged() {
        final ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener: listeners.getListeners(ChangeListener.class)) {
            listener.stateChanged(event);
        }
    }

    private final Set<Object> checkedNodes = new HashSet<>();
    private final EventListenerList listeners = new EventListenerList();

    private static final long serialVersionUID = 1L;

    private final class CheckBoxTreeCellRenderer extends JPanel implements TreeCellRenderer {
        CheckBoxTreeCellRenderer() {
            super(new BorderLayout());
            setOpaque(false);
            checkBox.setOpaque(false);
            add(checkBox, BorderLayout.WEST);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            final boolean checked = checkedNodes.contains(value);
            checkBox.setSelected(checked);
            checkBox.setMixed((! checked) && isPartiallyChecked(value));
            if (actualComponent != null) {
                remove(actualComponent);
            }
            actualComponent = actualRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            add(actualComponent, BorderLayout.CENTER);
            return this;
        }

        final TristateCheckBox checkBox = new TristateCheckBox();
        TreeCellRenderer actualRenderer = new DefaultTreeCellRenderer();
        private Component actualComponent;

        private static final long serialVersionUID = 1L;
    }
}
