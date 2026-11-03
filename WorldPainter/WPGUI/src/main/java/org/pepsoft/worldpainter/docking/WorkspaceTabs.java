package org.pepsoft.worldpainter.docking;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The tabs in the header of the window to switch between the workspaces, like those at the top of Blender, with a
 * button to add one. A right click on a tab renames, duplicates, resets, moves or deletes it; a double click renames
 * it.
 */
final class WorkspaceTabs extends JPanel {
    WorkspaceTabs(AreaManager manager) {
        super(new FlowLayout(FlowLayout.LEADING, 2, 0));
        this.manager = manager;
        setOpaque(false);
        addButton = new JButton(Icons.muted("plus", 14));
        addButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        addButton.setFocusable(false);
        addButton.setToolTipText("<html><b>New workspace</b><br>Add a workspace, made from one of the built-in arrangements or a copy of this one</html>");
        addButton.addActionListener(e -> manager.createTemplatePopupMenu().show(addButton, 0, addButton.getHeight()));
        update();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    void update() {
        final List<String> names = manager.getWorkspaceNames();
        final int active = manager.getActiveWorkspaceIndex();
        if (names.equals(this.names) && (active == this.active)) {
            return;
        }
        this.names = names;
        this.active = active;
        removeAll();
        final ButtonGroup buttonGroup = new ButtonGroup();
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            final JToggleButton tab = new JToggleButton(names.get(i), i == active);
            tab.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            tab.setFocusable(false);
            tab.setMargin(new Insets(3, 8, 3, 8));
            if (i == active) {
                tab.putClientProperty("FlatLaf.styleClass", "semibold");
            }
            final String template = manager.getTemplateOf(i);
            tab.setToolTipText("<html><b>" + names.get(i) + "</b>" + (((template != null) && (! template.equals(names.get(i)))) ? " <font color=\"#a1a1aa\">(" + template + ")</font>" : "")
                    + "<br>Switch to this workspace. Each one keeps its own arrangement of areas<br><font color=\"#a1a1aa\">Right click for more, double click to rename</font></html>");
            tab.addActionListener(e -> manager.activateWorkspace(index));
            tab.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowMenu(e);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
                        manager.promptRename(index);
                    }
                }

                private void maybeShowMenu(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        manager.createWorkspacePopupMenu(index).show(tab, e.getX(), e.getY());
                    }
                }
            });
            buttonGroup.add(tab);
            add(tab);
        }
        add(addButton);
        revalidate();
        repaint();
    }

    private final AreaManager manager;
    private final JButton addButton;
    private List<String> names;
    private int active = -1;

    private static final long serialVersionUID = 1L;
}
