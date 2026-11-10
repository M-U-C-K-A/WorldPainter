package org.pepsoft.worldpainter.util;

import org.pepsoft.worldpainter.Configuration;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The keyboard shortcuts of the commands of WorldPainter, which the user can change: each command has its default
 * shortcuts (possibly none) and the ones the user chose instead, which are kept in {@code keybindings.properties} in
 * the configuration directory. The shortcuts are installed in the input map of the main window, and those of commands
 * in the menus are shown there too (as the accelerator of their action).
 */
public final class KeyBindings {
    private KeyBindings() {
        load();
    }

    public static KeyBindings getInstance() {
        return INSTANCE;
    }

    /**
     * Add a command.
     *
     * @param id       A unique, stable identifier, under which the chosen shortcuts are saved.
     * @param name     The name of the command, as shown to the user.
     * @param category The group it is listed under (the menu it is in, Tools, Brush…).
     * @param action   What it does.
     * @param defaults The default shortcuts, if any.
     * @return The command.
     */
    public synchronized Command register(String id, String name, String category, Action action, KeyStroke... defaults) {
        Command command = commands.get(id);
        if (command == null) {
            // The same action registered under another name (in a menu and in the input map, say) is one command
            for (Command existing: commands.values()) {
                if (existing.action == action) {
                    command = existing;
                    break;
                }
            }
        }
        if (command == null) {
            command = new Command(id, name, category, action, Arrays.asList(defaults));
            commands.put(id, command);
        } else {
            // Registered again (from another place): add the defaults
            for (KeyStroke keyStroke: defaults) {
                if (! command.defaults.contains(keyStroke)) {
                    command.defaults.add(keyStroke);
                }
            }
        }
        command.keyStrokes = overrides.containsKey(command.id) ? new ArrayList<>(overrides.get(command.id)) : new ArrayList<>(command.defaults);
        if (rootPane != null) {
            apply(command);
        }
        return command;
    }

    /**
     * Take over the shortcuts which were put in an input map by hand, for the actions with the given names: they become
     * the defaults of commands with those names.
     *
     * @param names The names of the actions in the action map, mapped to their name to show to the user.
     */
    public void adopt(InputMap inputMap, ActionMap actionMap, Map<String, String> names, String category) {
        final KeyStroke[] keys = inputMap.keys();
        if (keys == null) {
            return;
        }
        final Map<String, List<KeyStroke>> found = new LinkedHashMap<>();
        for (KeyStroke keyStroke: keys) {
            final Object name = inputMap.get(keyStroke);
            if ((name instanceof String) && names.containsKey(name)) {
                found.computeIfAbsent((String) name, n -> new ArrayList<>()).add(keyStroke);
                inputMap.remove(keyStroke);
            }
        }
        for (Map.Entry<String, String> entry: names.entrySet()) {
            final Action action = actionMap.get(entry.getKey());
            if (action != null) {
                register(entry.getKey(), entry.getValue(), category, action, found.getOrDefault(entry.getKey(), Collections.emptyList()).toArray(new KeyStroke[0]));
            }
        }
    }

    /**
     * Install the shortcuts in a window, now and whenever they change.
     */
    public synchronized void install(JRootPane rootPane) {
        this.rootPane = rootPane;
        for (Command command: commands.values()) {
            apply(command);
        }
    }

    public synchronized List<Command> getCommands() {
        return new ArrayList<>(commands.values());
    }

    /**
     * The command which has a shortcut, if any.
     */
    public synchronized Command findCommand(KeyStroke keyStroke) {
        for (Command command: commands.values()) {
            if (command.keyStrokes.contains(keyStroke)) {
                return command;
            }
        }
        return null;
    }

    /**
     * Change the shortcuts of a command, and save them.
     */
    public synchronized void setKeyStrokes(Command command, List<KeyStroke> keyStrokes) {
        unapply(command);
        command.keyStrokes = new ArrayList<>(keyStrokes);
        if (command.keyStrokes.equals(command.defaults)) {
            overrides.remove(command.id);
        } else {
            overrides.put(command.id, new ArrayList<>(keyStrokes));
        }
        apply(command);
        save();
    }

    /**
     * Put the default shortcuts of all commands back.
     */
    public synchronized void resetAll() {
        for (Command command: commands.values()) {
            unapply(command);
            command.keyStrokes = new ArrayList<>(command.defaults);
        }
        overrides.clear();
        for (Command command: commands.values()) {
            apply(command);
        }
        save();
    }

    /**
     * A shortcut as text, as macOS or the other systems write it.
     */
    public static String toText(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return "";
        }
        final int modifiers = keyStroke.getModifiers();
        final StringBuilder sb = new StringBuilder();
        if (MAC) {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) sb.append('⌃');
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) sb.append('⌥');
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) sb.append('⇧');
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) sb.append('⌘');
        } else {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) sb.append("Ctrl+");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) sb.append("Alt+");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) sb.append("Meta+");
        }
        final int code = keyStroke.getKeyCode();
        switch (code) {
            case KeyEvent.VK_OPEN_BRACKET: sb.append('['); break;
            case KeyEvent.VK_CLOSE_BRACKET: sb.append(']'); break;
            case KeyEvent.VK_MINUS: sb.append('-'); break;
            case KeyEvent.VK_EQUALS: sb.append('='); break;
            case KeyEvent.VK_SUBTRACT: sb.append("Num -"); break;
            case KeyEvent.VK_ADD: sb.append("Num +"); break;
            default: sb.append(KeyEvent.getKeyText(code));
        }
        return sb.toString();
    }

    /**
     * The shortcuts of a command as text, or an empty string if it has none.
     */
    public static String toText(List<KeyStroke> keyStrokes) {
        final StringJoiner joiner = new StringJoiner(",  ");
        for (KeyStroke keyStroke: keyStrokes) {
            joiner.add(toText(keyStroke));
        }
        return joiner.toString();
    }

    private void apply(Command command) {
        if (rootPane == null) {
            return;
        }
        final InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final ActionMap actionMap = rootPane.getActionMap();
        actionMap.put(ACTION_PREFIX + command.id, command.action);
        for (KeyStroke keyStroke: command.keyStrokes) {
            inputMap.put(keyStroke, ACTION_PREFIX + command.id);
        }
        // Shown in the menus
        if (command.menu) {
            command.action.putValue(Action.ACCELERATOR_KEY, command.keyStrokes.isEmpty() ? null : command.keyStrokes.get(0));
        }
    }

    private void unapply(Command command) {
        if (rootPane == null) {
            return;
        }
        final InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        for (KeyStroke keyStroke: command.keyStrokes) {
            if ((ACTION_PREFIX + command.id).equals(inputMap.get(keyStroke))) {
                inputMap.remove(keyStroke);
            }
        }
    }

    private void load() {
        final File file = getFile();
        if ((file == null) || (! file.isFile())) {
            return;
        }
        final Properties properties = new Properties();
        try (Reader in = new InputStreamReader(new FileInputStream(file), UTF_8)) {
            properties.load(in);
        } catch (IOException e) {
            logger.error("I/O error reading {}", file, e);
            return;
        }
        for (String id: properties.stringPropertyNames()) {
            final List<KeyStroke> keyStrokes = new ArrayList<>();
            for (String text: properties.getProperty(id).split(";")) {
                if (! text.trim().isEmpty()) {
                    final KeyStroke keyStroke = KeyStroke.getKeyStroke(text.trim());
                    if (keyStroke != null) {
                        keyStrokes.add(keyStroke);
                    }
                }
            }
            overrides.put(id, keyStrokes);
        }
    }

    private void save() {
        final File file = getFile();
        if (file == null) {
            return;
        }
        final Properties properties = new Properties();
        for (Map.Entry<String, List<KeyStroke>> entry: overrides.entrySet()) {
            final StringJoiner joiner = new StringJoiner(";");
            for (KeyStroke keyStroke: entry.getValue()) {
                joiner.add(keyStroke.toString());
            }
            properties.setProperty(entry.getKey(), joiner.toString());
        }
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), UTF_8)) {
            properties.store(out, "WorldPainter keyboard shortcuts (only those which differ from the defaults)");
        } catch (IOException e) {
            logger.error("I/O error writing {}", file, e);
        }
    }

    private static File getFile() {
        final File dir = Configuration.getConfigDir();
        return (dir != null) ? new File(dir, "keybindings.properties") : null;
    }

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, List<KeyStroke>> overrides = new HashMap<>();
    private JRootPane rootPane;

    private static final KeyBindings INSTANCE = new KeyBindings();
    private static final String ACTION_PREFIX = "keyBinding.";
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KeyBindings.class);

    /**
     * A command which can have keyboard shortcuts.
     */
    public static final class Command {
        Command(String id, String name, String category, Action action, List<KeyStroke> defaults) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.action = action;
            this.defaults = new ArrayList<>(defaults);
            menu = action.getValue(Action.ACCELERATOR_KEY) != null;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public List<KeyStroke> getKeyStrokes() {
            return Collections.unmodifiableList(keyStrokes);
        }

        public List<KeyStroke> getDefaults() {
            return Collections.unmodifiableList(defaults);
        }

        public boolean isDefault() {
            return keyStrokes.equals(defaults);
        }

        /**
         * Mark the command as one which is in a menu, so that its first shortcut is shown there.
         */
        public Command inMenu() {
            menu = true;
            return this;
        }

        final String id, name, category;
        final Action action;
        final List<KeyStroke> defaults;
        List<KeyStroke> keyStrokes = new ArrayList<>();
        boolean menu;
    }

    /**
     * An action which runs some code.
     */
    public static Action action(String name, Runnable runnable) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                runnable.run();
            }
        };
    }
}
