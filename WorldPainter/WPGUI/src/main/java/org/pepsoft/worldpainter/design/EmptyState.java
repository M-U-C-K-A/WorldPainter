package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.util.Icons;

import javax.swing.*;
import java.awt.*;

/**
 * What a panel shows when it has nothing to show yet: a muted icon, a title and a sentence saying what would appear
 * there and how to get it, centred near the top, rather than a blank space which looks broken.
 */
public final class EmptyState extends JPanel {
    /**
     * @param icon The name of the {@link Icons icon}.
     * @param title What is missing, in a few words.
     * @param text How to get something to show, in a sentence.
     */
    public EmptyState(String icon, String title, String text) {
        super(new GridBagLayout());
        setOpaque(false);
        iconLabel = new JLabel(Icons.muted(icon, 28));
        titleLabel = new JLabel(title, SwingConstants.CENTER) {
            @Override
            public void updateUI() {
                super.updateUI();
                // Bold, set as a font rather than a style so that the label is measured with it
                setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
            }
        };
        textLabel = new JLabel();
        textLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground");
        setText(text);
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.insets = new Insets(0, 12, 6, 12);
        add(iconLabel, constraints);
        add(titleLabel, constraints);
        constraints.insets = new Insets(0, 12, 0, 12);
        add(textLabel, constraints);
        // Keep it near the top, where the settings would start, rather than in the middle of a tall area
        constraints.weighty = 1.0;
        add(Box.createVerticalStrut(0), constraints);
        setBorder(BorderFactory.createEmptyBorder(24, 0, 12, 0));
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setText(String text) {
        // Wrapped to a narrow column, centred
        textLabel.setText("<html><div style=\"text-align: center; width: 170px\">" + text + "</div></html>");
    }

    private final JLabel iconLabel, titleLabel, textLabel;

    private static final long serialVersionUID = 1L;
}
