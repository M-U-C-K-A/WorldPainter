package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of settings which belong together: a title, what the settings do in a sentence, badges for what is important
 * to know about them, and the settings themselves, as rows of a label and controls.
 *
 * <p>In the Classic theme the card is a box set off from the window, with the controls after their labels. In the
 * Modern theme the title and the sentence stand above a raised surface with rounded corners, on which the rows are
 * separated by hairlines and the controls line up on the right. In the Glass theme, which builds on the Modern one,
 * the whole card is a pane of frosted glass with large rounded corners. The card follows the theme when it is
 * switched.
 */
public final class SettingsCard extends JPanel {
    public SettingsCard(String title, String description, Badge.Kind... badges) {
        super(new BorderLayout());
        setOpaque(false);
        header = transparent(new JPanel(new BorderLayout(0, UIScale.scale(3))));
        final JPanel titleRow = transparent(new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(6), 0)));
        titleLabel = new JLabel(title);
        titleRow.add(titleLabel);
        for (Badge.Kind badge: badges) {
            titleRow.add(new Badge(badge));
        }
        // The flow layout adds its gap before the first component too
        titleRow.setBorder(BorderFactory.createEmptyBorder(0, -UIScale.scale(6), 0, 0));
        header.add(titleRow, BorderLayout.NORTH);
        if (description != null) {
            descriptionArea = wrapping(description);
            header.add(descriptionArea, BorderLayout.CENTER);
        } else {
            descriptionArea = null;
        }
        add(header, BorderLayout.NORTH);
        body = transparent(new JPanel(new GridBagLayout()));
        add(body, BorderLayout.CENTER);
        applyStyle();
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
     * setting). A label without text continues the setting above.
     */
    public SettingsCard field(JLabel label, JComponent... controls) {
        final boolean continuation = (label.getText() == null) || label.getText().isBlank();
        add(label, Kind.LABEL, 0, continuation);
        add(flow(controls), Kind.CONTROLS, 1, continuation);
        row++;
        return this;
    }

    /**
     * A setting with a label in front of it which stretches to the full width, such as a text field, with optionally
     * more controls after it. The label may also be a radio button or check box which switches the setting on.
     */
    public SettingsCard stretched(JComponent label, JComponent main, JComponent... after) {
        add(label, Kind.LABEL, 0, false);
        final JPanel panel = transparent(new JPanel(new BorderLayout(UIScale.scale(6), 0)));
        panel.add(main, BorderLayout.CENTER);
        if (after.length > 0) {
            panel.add(flow(after), BorderLayout.LINE_END);
        }
        add(panel, Kind.STRETCHED, 1, false);
        row++;
        return this;
    }

    /**
     * Controls taking up the whole width, such as a check box.
     */
    public SettingsCard row(JComponent... controls) {
        add(flow(controls), Kind.ROW, 0, false);
        row++;
        return this;
    }

    /**
     * Controls which belong to the one above, such as the settings of something which can be switched on. If the first
     * one is a label, it is laid out like the label of a {@link #field(JLabel, JComponent...) field}, indented.
     */
    public SettingsCard indented(JComponent... controls) {
        if ((controls.length > 1) && (controls[0] instanceof JLabel)) {
            add(controls[0], Kind.INDENTED_LABEL, 0, true);
            add(flow(java.util.Arrays.copyOfRange(controls, 1, controls.length)), Kind.CONTROLS, 1, true);
        } else {
            add(flow(controls), Kind.INDENTED_ROW, 0, true);
        }
        row++;
        return this;
    }

    /**
     * A remark in a smaller, quieter font.
     */
    public SettingsCard note(String text) {
        final JTextArea area = wrapping(text);
        notes.add(area);
        add(area, Kind.NOTE, 0, true);
        row++;
        applyStyle();
        return this;
    }

    /**
     * Something which has its own layout, such as a panel of related settings, taking up the whole width.
     */
    public SettingsCard whole(JComponent component) {
        add(component, Kind.WHOLE, 0, false);
        row++;
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(false);
        if (body != null) {
            applyStyle();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (! modern) {
            g.setColor(Design.cardBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Design.border());
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            final Rectangle bounds = body.getBounds();
            if (glass) {
                final int margin = UIScale.scale(GLASS_MARGIN);
                Glass.paintSurface(g2, margin, margin, getWidth() - 2 * margin, getHeight() - 2 * margin, UIScale.scale(22f));
            } else {
                final float arc = UIScale.scale(12f);
                final RoundRectangle2D surface = new RoundRectangle2D.Float(bounds.x + 0.5f, bounds.y + 0.5f, bounds.width - 1, bounds.height - 1, arc, arc);
                g2.setColor(Design.surface());
                g2.fill(surface);
                g2.setColor(Design.surfaceBorder());
                g2.draw(surface);
            }
            // A hairline between the settings, but not between a setting and what belongs to it
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Design.separator());
            final Insets padding = body.getInsets();
            boolean first = true;
            for (int r = 0; r < row; r++) {
                int top = Integer.MAX_VALUE;
                boolean continuation = false;
                for (Entry entry: entries) {
                    if ((entry.row == r) && entry.component.isVisible()) {
                        top = Math.min(top, entry.component.getY() - ((GridBagLayout) body.getLayout()).getConstraints(entry.component).insets.top);
                        continuation = entry.continuation;
                    }
                }
                if (top == Integer.MAX_VALUE) {
                    continue;
                }
                if ((! first) && (! continuation)) {
                    final int y = bounds.y + top;
                    g2.drawLine(bounds.x + padding.left, y, bounds.x + bounds.width - padding.right - 1, y);
                }
                first = false;
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Lay the card out and colour it for the theme which is in use.
     */
    private void applyStyle() {
        modern = Design.isModern();
        glass = Design.isGlass();
        if (glass) {
            // Room around the glass for its shadow
            final int margin = UIScale.scale(GLASS_MARGIN);
            setBorder(BorderFactory.createEmptyBorder(margin + UIScale.scale(14), margin + UIScale.scale(18), margin + UIScale.scale(8), margin + UIScale.scale(18)));
            ((BorderLayout) getLayout()).setVgap(UIScale.scale(8));
            header.setBorder(null);
            body.setBorder(null);
            titleLabel.setFont(Design.semibold(1f));
        } else if (modern) {
            setBorder(null);
            ((BorderLayout) getLayout()).setVgap(UIScale.scale(8));
            header.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(4), 0, UIScale.scale(4)));
            body.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(14), UIScale.scale(4), UIScale.scale(14)));
            titleLabel.setFont(Design.semibold(0f));
        } else {
            setBorder(BorderFactory.createEmptyBorder(UIScale.scale(12), UIScale.scale(14), UIScale.scale(14), UIScale.scale(14)));
            ((BorderLayout) getLayout()).setVgap(UIScale.scale(8));
            header.setBorder(null);
            body.setBorder(null);
            titleLabel.setFont(Design.font(1f, Font.BOLD));
        }
        titleLabel.setForeground(Design.foreground());
        if (descriptionArea != null) {
            descriptionArea.setFont(Design.font(-1f, Font.PLAIN));
            descriptionArea.setForeground(Design.muted());
        }
        for (JTextArea note: notes) {
            note.setFont(Design.font(-1f, Font.PLAIN));
            note.setForeground(Design.muted());
        }
        final GridBagLayout layout = (GridBagLayout) body.getLayout();
        for (Entry entry: entries) {
            layout.setConstraints(entry.component, constraints(entry));
            if ((entry.kind == Kind.CONTROLS) && (entry.component instanceof JPanel) && (((JPanel) entry.component).getLayout() instanceof FlowLayout)) {
                ((FlowLayout) ((JPanel) entry.component).getLayout()).setAlignment(modern ? FlowLayout.TRAILING : FlowLayout.LEADING);
                // The flow layout adds its gap before the first component, and after the last one
                entry.component.setBorder(modern ? BorderFactory.createEmptyBorder(0, 0, 0, -UIScale.scale(6)) : BorderFactory.createEmptyBorder(0, -UIScale.scale(6), 0, 0));
            }
        }
        body.revalidate();
        repaint();
    }

    private void add(JComponent component, Kind kind, int column, boolean continuation) {
        final Entry entry = new Entry(prepare(component), kind, column, row, continuation);
        entries.add(entry);
        body.add(entry.component, constraints(entry));
        if ((kind == Kind.CONTROLS) && (component instanceof JPanel) && (((JPanel) component).getLayout() instanceof FlowLayout)) {
            ((FlowLayout) ((JPanel) component).getLayout()).setAlignment(modern ? FlowLayout.TRAILING : FlowLayout.LEADING);
            component.setBorder(modern ? BorderFactory.createEmptyBorder(0, 0, 0, -UIScale.scale(6)) : BorderFactory.createEmptyBorder(0, -UIScale.scale(6), 0, 0));
        }
    }

    private GridBagConstraints constraints(Entry entry) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = entry.column;
        constraints.gridy = entry.row;
        constraints.anchor = GridBagConstraints.LINE_START;
        // Rows which span both columns take the spare width too, so that a card without labelled settings still lines
        // up on the left
        constraints.weightx = (entry.column == 1) ? 1.0 : 0.0;
        // Modern rows are taller, with room for the hairlines between them
        final int vertical = UIScale.scale(modern ? (entry.continuation ? 2 : 6) : 2);
        final int bottom = UIScale.scale(modern ? 6 : 2);
        constraints.insets = new Insets(vertical, 0, bottom, 0);
        switch (entry.kind) {
            case LABEL:
                constraints.insets.top = UIScale.scale(modern ? (entry.continuation ? 2 : 6) : 4);
                constraints.insets.right = UIScale.scale(12);
                break;
            case INDENTED_LABEL:
                constraints.insets.left = UIScale.scale(modern ? 0 : 24);
                constraints.insets.right = UIScale.scale(12);
                break;
            case CONTROLS:
                if (modern) {
                    constraints.anchor = GridBagConstraints.LINE_END;
                }
                break;
            case STRETCHED:
                constraints.fill = GridBagConstraints.HORIZONTAL;
                break;
            case INDENTED_ROW:
                constraints.gridwidth = 2;
                constraints.weightx = 1.0;
                constraints.insets.left = UIScale.scale(24);
                break;
            case ROW:
                constraints.gridwidth = 2;
                constraints.weightx = 1.0;
                break;
            case NOTE:
            case WHOLE:
                constraints.gridwidth = 2;
                constraints.weightx = 1.0;
                constraints.fill = GridBagConstraints.HORIZONTAL;
                break;
        }
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

    private static JTextArea wrapping(String text) {
        final JTextArea area = new JTextArea(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
                setBorder(null);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setFont(Design.font(-1f, Font.PLAIN));
        area.setForeground(Design.muted());
        // Wraps to the width it gets, rather than asking for the width of the whole text on one line
        area.setColumns(1);
        return area;
    }

    private final JPanel header, body;
    private final JLabel titleLabel;
    private final JTextArea descriptionArea;
    private final List<JTextArea> notes = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private int row;
    private boolean modern, glass;

    private enum Kind {LABEL, INDENTED_LABEL, CONTROLS, STRETCHED, ROW, INDENTED_ROW, NOTE, WHOLE}

    private record Entry(JComponent component, Kind kind, int column, int row, boolean continuation) {}

    /**
     * The room around a card of glass for its shadow.
     */
    static final int GLASS_MARGIN = 8;
}
