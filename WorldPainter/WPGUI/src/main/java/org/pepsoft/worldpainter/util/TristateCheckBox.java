package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;

/**
 * A check box with a third, "mixed" state, for editing a property of multiple objects at once which does not have the
 * same value for all of them. In tristate mode clicking cycles through unselected, selected and mixed; otherwise it
 * toggles between unselected and selected and the mixed state can only be set programmatically.
 */
public final class TristateCheckBox extends JCheckBox {
    public TristateCheckBox() {
        this(null);
    }

    public TristateCheckBox(String text) {
        this(text, null);
    }

    public TristateCheckBox(String text, Icon icon) {
        super(text, icon);
        setModel(new TristateModel());
    }

    public boolean isMixed() {
        return mixed;
    }

    public void setMixed(boolean mixed) {
        if (mixed != this.mixed) {
            this.mixed = mixed;
            if (mixed) {
                getModel().setSelected(false);
            }
            // Supported by FlatLaf; other look and feels will show the mixed state as unselected
            putClientProperty("JButton.selectedState", mixed ? "indeterminate" : null);
            repaint();
        }
    }

    @Override
    public void setSelected(boolean b) {
        setMixed(false);
        super.setSelected(b);
    }

    public void setTristateMode(boolean tristateMode) {
        this.tristateMode = tristateMode;
        if ((! tristateMode) && mixed) {
            setSelected(false);
        }
    }

    public boolean isTristateMode() {
        return tristateMode;
    }

    private void advanceState() {
        if (mixed) {
            setSelected(false);
        } else if (isSelected()) {
            if (tristateMode) {
                setMixed(true);
            } else {
                setSelected(false);
            }
        } else {
            setSelected(true);
        }
    }

    private boolean mixed, tristateMode = true;

    private static final long serialVersionUID = 1L;

    private final class TristateModel extends JToggleButton.ToggleButtonModel {
        @Override
        public void setPressed(boolean b) {
            if ((isPressed() == b) || (! isEnabled())) {
                return;
            }
            final boolean fire = (! b) && isArmed();
            if (fire) {
                advanceState();
            }
            if (b) {
                stateMask |= PRESSED;
            } else {
                stateMask &= ~PRESSED;
            }
            fireStateChanged();
            if (fire) {
                int modifiers = 0;
                final AWTEvent currentEvent = EventQueue.getCurrentEvent();
                if (currentEvent instanceof InputEvent) {
                    modifiers = ((InputEvent) currentEvent).getModifiersEx();
                } else if (currentEvent instanceof ActionEvent) {
                    modifiers = ((ActionEvent) currentEvent).getModifiers();
                }
                fireActionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(), EventQueue.getMostRecentEventTime(), modifiers));
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
