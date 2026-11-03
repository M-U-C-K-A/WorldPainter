package org.pepsoft.worldpainter.docking;

import javax.swing.*;
import java.awt.*;

/**
 * A page which can be shown in an area of the window by an {@link AreaManager}: the tools, the brushes, the terrain,
 * the map, the 3D view, etc. Each page is shown in at most one area at a time.
 *
 * <p>Pages are identified by a unique {@link #getKey() key}, which is used to store the layout. Pages of the same
 * {@link #getGroup() group} (kind of editor) can be switched between with the tabs down the side of the area which
 * shows them, like the tabs of the Properties editor of Blender. The {@link #getCategory() category} is the column of
 * the editor type menu in which the page is listed; by default that of its group.
 */
public class DockableFrame extends JPanel {
    public DockableFrame(String key) {
        super(new BorderLayout());
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        final String oldKey = this.key;
        this.key = key;
        firePropertyChange("key", oldKey, key);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        final String oldTitle = this.title;
        this.title = title;
        firePropertyChange("title", oldTitle, title);
    }

    public Icon getFrameIcon() {
        return frameIcon;
    }

    public void setFrameIcon(Icon frameIcon) {
        final Icon oldFrameIcon = this.frameIcon;
        this.frameIcon = frameIcon;
        firePropertyChange("frameIcon", oldFrameIcon, frameIcon);
    }

    /**
     * The kind of editor this page belongs to. Pages of the same group share the tabs of the area which shows them.
     */
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * The column of the editor type menu in which the page is listed, or {@code null} for that of its group.
     */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * A sentence or two about what the page is for, for its tab and its entry in the editor type menu.
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private String key, title, group, category, description;
    private Icon frameIcon;

    private static final long serialVersionUID = 1L;
}
