package org.pepsoft.worldpainter;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.KeyBindings;
import org.pepsoft.worldpainter.util.KeyBindings.Command;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * Choose the keyboard shortcut of every command: those of the menus, the tools, the brush and the map, and some which
 * have none by default.
 */
public class KeyBindingsDialog extends JDialog {
    public KeyBindingsDialog(Window parent) {
        super(parent, "Keyboard Shortcuts", ModalityType.APPLICATION_MODAL);
        final JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        final JPanel top = new JPanel(new BorderLayout(0, 6));
        final JLabel intro = new JLabel("<html>Double-click a command (or choose Change) and press the keys you want for it. "
                + "Commands in <b>bold</b> have other shortcuts than they come with.</html>");
        intro.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground");
        top.add(intro, BorderLayout.NORTH);
        searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search commands or shortcuts");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.muted("search", 14));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        top.add(searchField, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);

        model = new Model();
        table = new JTable(model);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(330);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.setDefaultRenderer(Object.class, new Renderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ((e.getClickCount() == 2) && (selected() != null)) {
                    capture(selected(), false);
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        final JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new java.awt.Dimension(560, 460));
        content.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new BorderLayout());
        final JPanel rowButtons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        changeButton = new JButton("Change…");
        changeButton.setToolTipText("Give the command another shortcut instead of the ones it has");
        changeButton.addActionListener(e -> capture(selected(), false));
        addButton = new JButton("Add…");
        addButton.setToolTipText("Give the command one more shortcut, keeping the ones it has");
        addButton.addActionListener(e -> capture(selected(), true));
        removeButton = new JButton("Remove");
        removeButton.setToolTipText("Leave the command without a shortcut");
        removeButton.addActionListener(e -> {
            final Command command = selected();
            KeyBindings.getInstance().setKeyStrokes(command, Collections.emptyList());
            refresh();
        });
        resetButton = new JButton("Reset");
        resetButton.setToolTipText("Give the command back the shortcuts it comes with");
        resetButton.addActionListener(e -> {
            final Command command = selected();
            KeyBindings.getInstance().setKeyStrokes(command, command.getDefaults());
            refresh();
        });
        rowButtons.add(changeButton);
        rowButtons.add(addButton);
        rowButtons.add(removeButton);
        rowButtons.add(resetButton);
        buttons.add(rowButtons, BorderLayout.WEST);
        final JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        final JButton resetAllButton = new JButton("Reset All");
        resetAllButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Give all the commands back the shortcuts they come with?", "Reset All", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                KeyBindings.getInstance().resetAll();
                refresh();
            }
        });
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        dialogButtons.add(resetAllButton);
        dialogButtons.add(closeButton);
        buttons.add(dialogButtons, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(closeButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        filter();
        pack();
        setLocationRelativeTo(parent);
    }

    private Command selected() {
        final int row = table.getSelectedRow();
        return ((row >= 0) && (rows.get(row) instanceof Command)) ? (Command) rows.get(row) : null;
    }

    private void updateButtons() {
        final Command command = selected();
        changeButton.setEnabled(command != null);
        addButton.setEnabled(command != null);
        removeButton.setEnabled((command != null) && (! command.getKeyStrokes().isEmpty()));
        resetButton.setEnabled((command != null) && (! command.isDefault()));
    }

    /**
     * The commands which match the search, under the headings of their categories.
     */
    private void filter() {
        final String query = searchField.getText().trim().toLowerCase();
        final Map<String, List<Command>> byCategory = new TreeMap<>(Comparator.comparingInt(KeyBindingsDialog::categoryOrder).thenComparing(Comparator.naturalOrder()));
        for (Command command: KeyBindings.getInstance().getCommands()) {
            if (query.isEmpty() || command.getName().toLowerCase().contains(query) || command.getCategory().toLowerCase().contains(query)
                    || KeyBindings.toText(command.getKeyStrokes()).toLowerCase().contains(query)) {
                byCategory.computeIfAbsent(command.getCategory(), c -> new ArrayList<>()).add(command);
            }
        }
        rows.clear();
        for (Map.Entry<String, List<Command>> entry: byCategory.entrySet()) {
            rows.add(entry.getKey());
            entry.getValue().sort(Comparator.comparing(Command::getName));
            rows.addAll(entry.getValue());
        }
        model.fireTableDataChanged();
        updateButtons();
    }

    private static int categoryOrder(String category) {
        final int index = Arrays.asList("Painting and sculpting tools", "Brush and map", "Map", "File", "Edit", "View", "Tools", "Help").indexOf(category);
        return (index >= 0) ? index : 50;
    }

    private void refresh() {
        final Command command = selected();
        filter();
        if (command != null) {
            final int row = rows.indexOf(command);
            if (row >= 0) {
                table.getSelectionModel().setSelectionInterval(row, row);
            }
        }
        table.repaint();
    }

    /**
     * Ask the user to press the keys for a command, and give it them.
     */
    private void capture(Command command, boolean add) {
        if (command == null) {
            return;
        }
        final JDialog dialog = new JDialog(this, add ? "Add a Shortcut" : "Change the Shortcut", ModalityType.APPLICATION_MODAL);
        final JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        final JLabel prompt = new JLabel("<html>Press the keys for <b>" + escape(command.getName()) + "</b></html>");
        panel.add(prompt, BorderLayout.NORTH);
        final JLabel keys = new JLabel("…", SwingConstants.CENTER);
        keys.putClientProperty(FlatClientProperties.STYLE, "font: +6 semibold");
        keys.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        panel.add(keys, BorderLayout.CENTER);
        final JButton cancel = new JButton("Cancel");
        cancel.setFocusable(false);
        cancel.addActionListener(e -> dialog.dispose());
        final JPanel south = new JPanel(new BorderLayout());
        final JLabel hint = new JLabel("Hold Ctrl, Alt, Shift or ⌘ with a key for a combination");
        hint.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        south.add(hint, BorderLayout.WEST);
        south.add(cancel, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        final KeyAdapter listener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                final int code = e.getKeyCode();
                e.consume();
                if ((code == KeyEvent.VK_SHIFT) || (code == KeyEvent.VK_CONTROL) || (code == KeyEvent.VK_ALT) || (code == KeyEvent.VK_META) || (code == KeyEvent.VK_ALT_GRAPH) || (code == KeyEvent.VK_UNDEFINED)) {
                    // Only a modifier so far
                    keys.setText(KeyBindings.toText(KeyStroke.getKeyStroke(KeyEvent.VK_UNDEFINED, e.getModifiersEx())).replace(KeyEvent.getKeyText(KeyEvent.VK_UNDEFINED), "…"));
                    return;
                }
                final KeyStroke keyStroke = KeyStroke.getKeyStroke(code, e.getModifiersEx() & (KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK));
                keys.setText(KeyBindings.toText(keyStroke));
                dialog.dispose();
                assign(command, keyStroke, add);
            }
        };
        dialog.addKeyListener(listener);
        panel.setFocusable(true);
        panel.addKeyListener(listener);
        // Keys like Tab and Escape should be captured too
        panel.setFocusTraversalKeysEnabled(false);
        dialog.setFocusTraversalKeysEnabled(false);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 380), dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(panel::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private void assign(Command command, KeyStroke keyStroke, boolean add) {
        final KeyBindings bindings = KeyBindings.getInstance();
        final Command other = bindings.findCommand(keyStroke);
        if ((other != null) && (other != command)) {
            if (JOptionPane.showConfirmDialog(this, "<html>" + escape(KeyBindings.toText(keyStroke)) + " is the shortcut of <b>" + escape(other.getName()) + "</b> (" + escape(other.getCategory())
                    + ").<br>Give it to <b>" + escape(command.getName()) + "</b> instead?</html>", "Shortcut in Use", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            final List<KeyStroke> remaining = new ArrayList<>(other.getKeyStrokes());
            remaining.remove(keyStroke);
            bindings.setKeyStrokes(other, remaining);
        }
        final List<KeyStroke> keyStrokes = add ? new ArrayList<>(command.getKeyStrokes()) : new ArrayList<>();
        if (! keyStrokes.contains(keyStroke)) {
            keyStrokes.add(keyStroke);
        }
        bindings.setKeyStrokes(command, keyStrokes);
        refresh();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private final JTextField searchField;
    private final JTable table;
    private final Model model;
    private final JButton changeButton, addButton, removeButton, resetButton;
    private final List<Object> rows = new ArrayList<>();

    private static final long serialVersionUID = 1L;

    private final class Model extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return (column == 0) ? "Command" : "Shortcut";
        }

        @Override
        public Object getValueAt(int row, int column) {
            final Object value = rows.get(row);
            if (value instanceof Command) {
                return (column == 0) ? ((Command) value).getName() : KeyBindings.toText(((Command) value).getKeyStrokes());
            } else {
                return (column == 0) ? value : "";
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final Object item = rows.get(row);
            final boolean heading = ! (item instanceof Command);
            super.getTableCellRendererComponent(table, value, isSelected && (! heading), false, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, heading ? 6 : 16, 0, 6));
            if (heading) {
                setFont(table.getFont().deriveFont(Font.BOLD, table.getFont().getSize2D() - 1f));
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setText((column == 0) ? String.valueOf(value).toUpperCase() : "");
            } else {
                final Command command = (Command) item;
                setFont(command.isDefault() ? table.getFont() : table.getFont().deriveFont(Font.BOLD));
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                if ((column == 1) && command.getKeyStrokes().isEmpty() && (! isSelected)) {
                    setForeground(UIManager.getColor("Label.disabledForeground"));
                    setText("none");
                }
            }
            return this;
        }

        private static final long serialVersionUID = 1L;
    }
}
