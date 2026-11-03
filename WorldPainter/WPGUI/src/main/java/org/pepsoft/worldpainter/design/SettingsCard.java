package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;

/**
 * A group of settings which belong together, set off from the window: a title, what the settings do in a sentence,
 * badges for what is important to know about them, and the settings themselves, as rows of a label and controls.
 */
public final class SettingsCard extends JPanel {
    public SettingsCard(String title, String description, Badge.Kind... badges) {
        super(new BorderLayout(0, UIScale.scale(8)));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(12), UIScale.scale(14), UIScale.scale(14), UIScale.scale(14)));
        final JPanel header = transparent(new JPanel(new BorderLayout(0, UIScale.scale(3))));
        final JPanel titleRow = transparent(new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(6), 0)));
        final JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Design.font(1f, Font.BOLD));
        titleRow.add(titleLabel);
        for (Badge.Kind badge: badges) {
            titleRow.add(new Badge(badge));
        }
        // The flow layout adds its gap before the first component too
        titleRow.setBorder(BorderFactory.createEmptyBorder(0, -UIScale.scale(6), 0, 0));
        header.add(titleRow, BorderLayout.NORTH);
        if (description != null) {
            header.add(wrapping(description, Design.muted(), -1f), BorderLayout.CENTER);
        }
        add(header, BorderLayout.NORTH);
        body = transparent(new JPanel(new GridBagLayout()));
        add(body, BorderLayout.CENTER);
    }

    /**
     * Make the card take up the whole width of the page, for settings which need more room than one column.
     */
    public SettingsCard wide() {
        putClientProperty(SettingsPage.KEY_WIDE, Boolean.TRUE);
        return this;
    }

    /**
     * A setting with a label in front of it.
     */
    public SettingsCard field(String label, JComponent... controls) {
        return field(new JLabel(label), controls);
    }

    /**
     * A setting with a label in front of it, using an existing label (which may for instance be disabled along with the
     * setting).
     */
    public SettingsCard field(JLabel label, JComponent... controls) {
        final GridBagConstraints constraints = constraints(0);
        constraints.insets = new Insets(UIScale.scale(4), 0, UIScale.scale(2), UIScale.scale(12));
        body.add(prepare(label), constraints);
        final GridBagConstraints controlConstraints = constraints(1);
        body.add(flow(controls), controlConstraints);
        row++;
        return this;
    }

    /**
     * A setting with a label in front of it which stretches to the full width, such as a text field, with optionally
     * more controls after it. The label may also be a radio button or check box which switches the setting on.
     */
    public SettingsCard stretched(JComponent label, JComponent main, JComponent... after) {
        final GridBagConstraints constraints = constraints(0);
        constraints.insets = new Insets(UIScale.scale(4), 0, UIScale.scale(2), UIScale.scale(12));
        body.add(prepare(label), constraints);
        final JPanel panel = transparent(new JPanel(new BorderLayout(UIScale.scale(6), 0)));
        panel.add(main, BorderLayout.CENTER);
        if (after.length > 0) {
            panel.add(flow(after), BorderLayout.LINE_END);
        }
        final GridBagConstraints controlConstraints = constraints(1);
        controlConstraints.fill = GridBagConstraints.HORIZONTAL;
        body.add(panel, controlConstraints);
        row++;
        return this;
    }

    /**
     * Controls taking up the whole width, such as a check box.
     */
    public SettingsCard row(JComponent... controls) {
        final GridBagConstraints constraints = constraints(0);
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        body.add(flow(controls), constraints);
        row++;
        return this;
    }

    /**
     * Controls which belong to the one above, such as the settings of something which can be switched on.
     */
    public SettingsCard indented(JComponent... controls) {
        final GridBagConstraints constraints = constraints(0);
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.insets.left = UIScale.scale(24);
        body.add(flow(controls), constraints);
        row++;
        return this;
    }

    /**
     * A remark in a smaller, quieter font.
     */
    public SettingsCard note(String text) {
        final GridBagConstraints constraints = constraints(0);
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        body.add(wrapping(text, Design.muted(), -1f), constraints);
        row++;
        return this;
    }

    /**
     * Something which has its own layout, such as a panel of related settings, taking up the whole width.
     */
    public SettingsCard whole(JComponent component) {
        final GridBagConstraints constraints = constraints(0);
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        body.add(prepare(component), constraints);
        row++;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Design.cardBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Design.border());
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(false);
    }

    private GridBagConstraints constraints(int column) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        // Rows which span both columns take the spare width too, so that a card without labelled settings still lines
        // up on the left
        constraints.weightx = (column == 1) ? 1.0 : 0.0;
        constraints.insets = new Insets(UIScale.scale(2), 0, UIScale.scale(2), 0);
        return constraints;
    }

    private static JComponent flow(JComponent... controls) {
        if (controls.length == 1) {
            return prepare(controls[0]);
        }
        final JPanel panel = transparent(new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(6), 0)));
        panel.setBorder(BorderFactory.createEmptyBorder(0, -UIScale.scale(6), 0, 0));
        for (JComponent control: controls) {
            panel.add(prepare(control));
        }
        return panel;
    }

    /**
     * Let the background of the card show through what doesn't have a background of its own.
     */
    static JComponent prepare(JComponent component) {
        if ((component instanceof JLabel) || (component instanceof AbstractButton && ! (component instanceof JButton)) || (component instanceof JPanel)) {
            component.setOpaque(false);
        }
        if (component instanceof JPanel) {
            for (Component child: component.getComponents()) {
                if (child instanceof JComponent) {
                    prepare((JComponent) child);
                }
            }
        }
        return component;
    }

    private static <T extends JComponent> T transparent(T component) {
        component.setOpaque(false);
        return component;
    }

    private static JComponent wrapping(String text, Color colour, float relativeSize) {
        final JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(Design.font(relativeSize, Font.PLAIN));
        area.setForeground(colour);
        // Wraps to the width it gets, rather than asking for the width of the whole text on one line
        area.setColumns(1);
        return area;
    }

    private final JPanel body;
    private int row;
}
