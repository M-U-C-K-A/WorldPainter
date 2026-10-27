package org.pepsoft.worldpainter.docking;

import javax.swing.*;
import java.awt.*;

/**
 * A panel which can be managed by a {@link DockingManager}: docked to the left or right of the workspace (on its own
 * or grouped with other frames as tabs), floated in its own window, or hidden.
 *
 * <p>Frames are identified by a unique {@link #getKey() key}, which is used to persist the layout. The initial
 * {@link #getInitSide() side} and {@link #getInitIndex() index} determine where the frame is docked by default; frames
 * with the same side and index are grouped together as tabs.
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

    public Side getInitSide() {
        return initSide;
    }

    public void setInitSide(Side initSide) {
        this.initSide = initSide;
    }

    public int getInitIndex() {
        return initIndex;
    }

    public void setInitIndex(int initIndex) {
        this.initIndex = initIndex;
    }

    private String key, title;
    private Icon frameIcon;
    private Side initSide = Side.WEST;
    private int initIndex;

    private static final long serialVersionUID = 1L;
}
