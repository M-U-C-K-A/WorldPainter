/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.panels;

import org.pepsoft.util.ObservableBoolean;
import org.pepsoft.util.swing.BetterJPopupMenu;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.biomeschemes.BiomeHelper;
import org.pepsoft.worldpainter.biomeschemes.CustomBiome;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.help.OptionCards;
import org.pepsoft.worldpainter.help.ToolCard;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.operations.Filter;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;
import org.pepsoft.worldpainter.panels.DefaultFilter.LayerValue;
import org.pepsoft.worldpainter.panels.DefaultFilter.LevelType;
import org.pepsoft.worldpainter.tools.Eyedropper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.JSpinner.NumberEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.Math.abs;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.pepsoft.util.GUIUtils.getUIScale;
import static org.pepsoft.util.IconUtils.createScaledColourIcon;
import static org.pepsoft.util.IconUtils.loadScaledIcon;
import static org.pepsoft.worldpainter.Platform.Capability.NAMED_BIOMES;
import static org.pepsoft.worldpainter.Terrain.STAINED_TERRACOTTAS;
import static org.pepsoft.worldpainter.util.BiomeUtils.getBiomeScheme;

/**
 *
 * @author pepijn
 */
@SuppressWarnings("unused") // Managed by NetBeans
public class BrushOptions extends javax.swing.JPanel implements Observer {
    /**
     * Creates new form BrushOptions
     */
    public BrushOptions() {
        initComponents();
        
        // Eliminate thousands separators to make spinners smaller:
        spinnerAbove.setEditor(new NumberEditor(spinnerAbove, "0"));
        spinnerBelow.setEditor(new NumberEditor(spinnerBelow, "0"));

        heightScale.setListener(this::heightScaleChanged);
        updateHeightScale();

        // A value which has been typed counts, however the user goes on to do something else. Clicking on the map doesn't
        // even move the focus, so also watch for that
        for (JSpinner spinner: new JSpinner[] {spinnerAbove, spinnerBelow, spinnerSlope, spinnerCurvature}) {
            final JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            field.setFocusLostBehavior(JFormattedTextField.PERSIST);
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commit(spinner);
                }
            });
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(this::commitOnClickElsewhere, AWTEvent.MOUSE_EVENT_MASK);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this::dispatchUndoKey);
        currentOptions = currentOptions();

        buildLayout();
        // The selection can change at any time, in many ways
        new javax.swing.Timer(1000, e -> updateNoSelectionHint()).start();
    }

    public ColourScheme getColourScheme() {
        return colourScheme;
    }

    public void setColourScheme(ColourScheme colourScheme) {
        this.colourScheme = colourScheme;
    }

    public CustomBiomeManager getCustomBiomeManager() {
        return customBiomeManager;
    }

    public void setCustomBiomeManager(CustomBiomeManager customBiomeManager) {
        this.customBiomeManager = customBiomeManager;
    }

    public void setSelectionState(ObservableBoolean selectionState) {
        if (this.selectionState != null) {
            throw new IllegalStateException("selectionState already set");
        }
        this.selectionState = selectionState;
        selectionState.addObserver(this);
        comboBoxMapArea.setEnabled(selectionState.getValue());
    }

    public DefaultFilter getFilter() {
        if ((comboBoxMapArea.getSelectedItem() != MapArea.WHOLE_MAP) || checkBoxAbove.isSelected()
                || checkBoxBelow.isSelected() || (checkBoxReplace.isSelected() && (onlyOn != null))
                || (checkBoxExceptOn.isSelected() && (exceptOn != null)) || checkBoxAboveSlope.isSelected()
                || checkBoxBelowSlope.isSelected() || checkBoxHollows.isSelected() || checkBoxBumps.isSelected()) {
            return new DefaultFilter(App.getInstance().getDimension(),
                    comboBoxMapArea.getSelectedItem() == MapArea.INSIDE_SELECTION,
                    comboBoxMapArea.getSelectedItem() == MapArea.OUTSIDE_SELECTION,
                    checkBoxAbove.isSelected() ? (Integer) spinnerAbove.getValue() : Integer.MIN_VALUE,
                    checkBoxBelow.isSelected() ? (Integer) spinnerBelow.getValue() : Integer.MIN_VALUE,
                    checkBoxFeather.isSelected(),
                    checkBoxReplace.isSelected() && (onlyOn != null),
                    onlyOn,
                    checkBoxExceptOn.isSelected() && (exceptOn != null),
                    exceptOn,
                    (checkBoxAboveSlope.isSelected() || checkBoxBelowSlope.isSelected()) ? (Integer) spinnerSlope.getValue() : -1,
                    checkBoxAboveSlope.isSelected())
                    .withCurvature(curvatureMode(), ((Number) spinnerCurvature.getValue()).floatValue());
        } else {
            return null;
        }
    }
    
    public void setFilter(DefaultFilter filter) {
        // Not something the user did here, and it replaces the options whose changes were being remembered
        historyPaused++;
        try {
            applyFilter(filter);
        } finally {
            historyPaused--;
        }
        undoHistory.clear();
        redoHistory.clear();
        currentOptions = currentOptions();
    }

    private void applyFilter(DefaultFilter filter) {
        if (filter == null) {
            comboBoxMapArea.setSelectedItem(MapArea.WHOLE_MAP);
            checkBoxAbove.setSelected(false);
            checkBoxBelow.setSelected(false);
            checkBoxReplace.setSelected(false);
            checkBoxExceptOn.setSelected(false);
            checkBoxAboveSlope.setSelected(false);
            checkBoxBelowSlope.setSelected(false);
            checkBoxHollows.setSelected(false);
            checkBoxBumps.setSelected(false);
            onlyOn = exceptOn = null;
        } else {
            comboBoxMapArea.setSelectedItem(filter.inSelection ? MapArea.INSIDE_SELECTION : (filter.outsideSelection ? MapArea.OUTSIDE_SELECTION : MapArea.WHOLE_MAP));
            checkBoxAbove.setSelected(filter.levelType == LevelType.ABOVE || filter.levelType == LevelType.BETWEEN || filter.levelType == LevelType.OUTSIDE);
            if (filter.aboveLevel != Integer.MIN_VALUE) {
                spinnerAbove.setValue(filter.aboveLevel);
            }
            checkBoxBelow.setSelected(filter.levelType == LevelType.BELOW || filter.levelType == LevelType.BETWEEN || filter.levelType == LevelType.OUTSIDE);
            if (filter.belowLevel != Integer.MIN_VALUE) {
                spinnerBelow.setValue(filter.belowLevel);
            }
            checkBoxAboveSlope.setSelected(filter.checkSlope && filter.slopeIsAbove);
            checkBoxBelowSlope.setSelected(filter.checkSlope && (! filter.slopeIsAbove));
            if (filter.degrees >= 0) {
                spinnerSlope.setValue(filter.degrees);
            }
            checkBoxHollows.setSelected(filter.getCurvatureMode() == DefaultFilter.CURVATURE_HOLLOWS);
            checkBoxBumps.setSelected(filter.getCurvatureMode() == DefaultFilter.CURVATURE_BUMPS);
            if (filter.getCurvatureMode() != DefaultFilter.CURVATURE_NONE) {
                spinnerCurvature.setValue((double) filter.getCurvatureDepth());
            }
            checkBoxReplace.setSelected(filter.onlyOn);
            checkBoxExceptOn.setSelected(filter.exceptOn);
            final App app = App.getInstance();
            if (filter.onlyOnFilter instanceof OnlyOnTerrainOrLayerFilter) {
                onlyOn = toPaint((OnlyOnTerrainOrLayerFilter) filter.onlyOnFilter);
            } else if (filter.onlyOnFilter instanceof AnyOfFilter) {
                onlyOn = ((AnyOfFilter) filter.onlyOnFilter).getFilters().stream()
                        .map(f -> toPaint((OnlyOnTerrainOrLayerFilter) f))
                        .collect(Collectors.toCollection(ArrayList::new));
            } else if (filter.onlyOnFilter != null) {
                throw new UnsupportedOperationException("Unsupported onlyOn filter type " + filter.onlyOnFilter.getClass());
            }
            if (filter.exceptOnFilter instanceof ExceptOnTerrainOrLayerFilter) {
                exceptOn = toPaint((ExceptOnTerrainOrLayerFilter) filter.exceptOnFilter);
            } else if (filter.exceptOnFilter instanceof CombinedFilter) {
                exceptOn = ((CombinedFilter) filter.exceptOnFilter).getFilters().stream()
                        .map(f -> toPaint((ExceptOnTerrainOrLayerFilter) f))
                        .collect(Collectors.toCollection(ArrayList::new));
            } else if (filter.exceptOnFilter != null) {
                throw new UnsupportedOperationException("Unsupported exceptOn filter type " + filter.exceptOnFilter.getClass());
            }
        }
        installPaint(onlyOn, buttonReplace);
        installPaint(exceptOn, buttonExceptOn);
        setControlStates();
    }

    public void setMinHeight(int minHeight) {
        historyPaused++;
        boolean updateFilter = false;
        final Object oldMinHeight = ((SpinnerNumberModel) spinnerAbove.getModel()).getMinimum();
        ((SpinnerNumberModel) spinnerAbove.getModel()).setMinimum(minHeight);
        // A limit which is off and was at the end of the scale stays there
        if ((! checkBoxAbove.isSelected()) && spinnerAbove.getValue().equals(oldMinHeight)) {
            spinnerAbove.setValue(minHeight);
        }
        if ((Integer) spinnerAbove.getValue() < minHeight) {
            spinnerAbove.setValue(minHeight);
            updateFilter = true;
        }
        ((SpinnerNumberModel) spinnerBelow.getModel()).setMinimum(minHeight);
        if ((Integer) spinnerBelow.getValue() < minHeight) {
            spinnerBelow.setValue(minHeight);
            updateFilter = true;
        }
        updateHeightScale();
        if (updateFilter) {
            filterChanged();
        }
        historyPaused--;
    }

    public void setMaxHeight(int maxHeight) {
        historyPaused++;
        boolean updateFilter = false;
        final Object oldMaxHeight = ((SpinnerNumberModel) spinnerBelow.getModel()).getMaximum();
        ((SpinnerNumberModel) spinnerAbove.getModel()).setMaximum(maxHeight - 1);
        if ((Integer) spinnerAbove.getValue() >= maxHeight) {
            spinnerAbove.setValue(maxHeight - 1);
            updateFilter = true;
        }
        ((SpinnerNumberModel) spinnerBelow.getModel()).setMaximum(maxHeight - 1);
        if ((! checkBoxBelow.isSelected()) && spinnerBelow.getValue().equals(oldMaxHeight)) {
            spinnerBelow.setValue(maxHeight - 1);
        }
        if ((Integer) spinnerBelow.getValue() >= maxHeight) {
            spinnerBelow.setValue(maxHeight - 1);
            updateFilter = true;
        }
        updateHeightScale();
        if (updateFilter) {
            filterChanged();
        }
        historyPaused--;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public MapSelectionListener getMapSelectionListener() {
        return mapSelectionListener;
    }

    public void setMapSelectionListener(MapSelectionListener mapSelectionListener) {
        this.mapSelectionListener = mapSelectionListener;
    }
    
    public void setOnlyOn(Terrain terrain) {
        onlyOn = terrain;
        installPaint(onlyOn, buttonReplace);
    }

    public void setOnlyOn(Layer layer, int value) {
        onlyOn = toPaint(layer, value);
        installPaint(onlyOn, buttonReplace);
    }

    public void addOnlyOn(Terrain terrain) {
        onlyOn = addToSelection(onlyOn, terrain);
        installPaint(onlyOn, buttonReplace);
    }

    public void addOnlyOn(Layer layer, int value) {
        onlyOn = addToSelection(onlyOn, toPaint(layer, value));
        installPaint(onlyOn, buttonReplace);
    }

    public void setExceptOn(Terrain terrain) {
        exceptOn = terrain;
        installPaint(exceptOn, buttonExceptOn);
    }

    public void setExceptOn(Layer layer, int value) {
        exceptOn = toPaint(layer, value);
        installPaint(exceptOn, buttonExceptOn);
    }

    public void addExceptOn(Terrain terrain) {
        exceptOn = addToSelection(exceptOn, terrain);
        installPaint(exceptOn, buttonExceptOn);
    }

    public void addExceptOn(Layer layer, int value) {
        exceptOn = addToSelection(exceptOn, toPaint(layer, value));
        installPaint(exceptOn, buttonExceptOn);
    }

    // Observer

    @Override
    public void update(Observable o, Object selectionMayBePresent) {
        comboBoxMapArea.setEnabled((boolean) selectionMayBePresent);
    }

    /**
     * The new selection with an item added, as a new list so that the previous one can still be gone back to.
     */
    private Object addToSelection(Object currentSelection, Object item) {
        final List<Object> selection = itemsOf(currentSelection);
        if (selection.contains(item)) {
            return currentSelection;
        }
        selection.add(item);
        return (selection.size() == 1) ? item : selection;
    }

    private Object removeFromSelection(Object currentSelection, Object item) {
        final List<Object> selection = itemsOf(currentSelection);
        selection.remove(item);
        return selection.isEmpty() ? null : ((selection.size() == 1) ? selection.get(0) : selection);
    }

    @SuppressWarnings("unchecked") // Guaranteed by code
    private static List<Object> itemsOf(Object selection) {
        if (selection == null) {
            return new ArrayList<>();
        } else if (selection instanceof List) {
            return new ArrayList<>((List<Object>) selection);
        } else {
            return new ArrayList<>(singleton(selection));
        }
    }

    /**
     * Show what has been chosen as a list, each with a button to remove it, and a button to add more.
     */
    private void installPaint(Object paint, AbstractButton button) {
        final boolean replace = button == buttonReplace;
        final SelectionList list = replace ? listReplace : listExceptOn;
        list.removeAll();
        for (Object item: itemsOf(paint)) {
            final JPanel row = new JPanel(new BorderLayout(4, 0));
            final String text = getText(item);
            final JLabel label = new JLabel(text, getIcon(item), SwingConstants.LEADING);
            label.setFont(label.getFont().deriveFont(label.getFont().getSize() - 1f));
            label.setToolTipText(text);
            row.add(label, BorderLayout.CENTER);
            final JButton removeButton = new JButton("\u00d7");
            removeButton.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE, com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            removeButton.setMargin(new Insets(0, 4, 0, 4));
            removeButton.setFocusable(false);
            removeButton.setToolTipText("Remove " + text);
            removeButton.addActionListener(e -> {
                initialiseIfNecessary();
                if (replace) {
                    onlyOn = removeFromSelection(onlyOn, item);
                    installPaint(onlyOn, buttonReplace);
                } else {
                    exceptOn = removeFromSelection(exceptOn, item);
                    installPaint(exceptOn, buttonExceptOn);
                }
            });
            row.add(removeButton, BorderLayout.LINE_END);
            row.setAlignmentX(LEFT_ALIGNMENT);
            list.add(row);
        }
        button.setText((paint == null) ? "Choose\u2026" : "Add another\u2026");
        list.revalidate();
        list.repaint();
        filterChanged();
    }

    private String getText(Object paint) {
        if (paint instanceof String) {
            switch ((String) paint) {
                case TerrainOrLayerFilter.WATER:
                    return "Water";
                case TerrainOrLayerFilter.LAVA:
                    return "Lava";
                case TerrainOrLayerFilter.LAND:
                    return "Land";
                case TerrainOrLayerFilter.AUTO_BIOMES:
                    return "All Auto Biomes";
                default:
                    throw new UnsupportedOperationException("Paint of type \"" + paint + "\" not supported");
            }
        } else if (paint instanceof Terrain) {
            return ((Terrain) paint).getName();
        } else if (paint instanceof LayerValue) {
            final LayerValue layerValue = (LayerValue) paint;
            if (layerValue.layer instanceof Biome) {
                return new BiomeHelper(colourScheme, customBiomeManager, platform).getBiomeName(abs(layerValue.value));
            } else if (layerValue.layer instanceof Annotations) {
                if (layerValue.value == -1) {
                    return "All Annotations";
                } else {
                    return Annotations.getColourName(layerValue.value) + " Annotations";
                }
            } else {
                throw new UnsupportedOperationException("Paint of layer type " + layerValue.layer.getClass() + " and value " + layerValue.value + " not supported");
            }
        } else if (paint instanceof Layer) {
            return ((Layer) paint).getName();
        } else {
            throw new UnsupportedOperationException("Paint of type " + paint.getClass() + " not supported");
        }
    }

    private Icon getIcon(Object paint) {
        if (paint instanceof String) {
            switch ((String) paint) {
                case TerrainOrLayerFilter.WATER:
                    return ICON_WATER;
                case TerrainOrLayerFilter.LAVA:
                    return ICON_LAVA;
                case TerrainOrLayerFilter.LAND:
                case TerrainOrLayerFilter.AUTO_BIOMES:
                    return null;
                default:
                    throw new UnsupportedOperationException("Paint of type \"" + paint + "\" not supported");
            }
        } else if (paint instanceof Terrain) {
            return new ImageIcon(((Terrain) paint).getScaledIcon(16, colourScheme));
        } else if (paint instanceof LayerValue) {
            final LayerValue layerValue = (LayerValue) paint;
            if (layerValue.layer instanceof Biome) {
                return new BiomeHelper(colourScheme, customBiomeManager, platform).getBiomeIcon(abs(layerValue.value));
            } else if (layerValue.layer instanceof Annotations) {
                if (layerValue.value == -1) {
                    return null;
                } else {
                    return createScaledColourIcon(Annotations.getColour(layerValue.value, colourScheme));
                }
            } else {
                throw new UnsupportedOperationException("Paint of layer type " + layerValue.layer.getClass() + " and value " + layerValue.value + " not supported");
            }
        } else if (paint instanceof Layer) {
            return new ImageIcon(((Layer) paint).getIcon());
        } else {
            throw new UnsupportedOperationException("Paint of type " + paint.getClass() + " not supported");
        }
    }

    private Object toPaint(Layer layer, int value) {
        if (layer instanceof Biome) {
            return new LayerValue(Biome.INSTANCE, value);
        } else if (layer instanceof Annotations) {
            return new LayerValue(Annotations.INSTANCE, value);
        } else if (layer.discrete) {
            throw new UnsupportedOperationException("Discrete layers not supported");
        } else {
            return layer;
        }
    }

    private Object toPaint(TerrainOrLayerFilter filter) {
        switch (filter.objectType) {
            case BIOME:
                if (filter.value == 255) {
                    return TerrainOrLayerFilter.AUTO_BIOMES;
                } else {
                    return new LayerValue(Biome.INSTANCE, filter.value);
                }
            case AUTO_BIOME:
                return new LayerValue(Biome.INSTANCE, -filter.value);
            case BIT_LAYER:
            case INT_LAYER_ANY:
                return filter.layer;
            case INT_LAYER_EQUAL:
                return new LayerValue(filter.layer, filter.value);
            case TERRAIN:
                return filter.terrain;
            case WATER:
                return TerrainOrLayerFilter.WATER;
            case LAND:
                return TerrainOrLayerFilter.LAND;
            case LAVA:
                return TerrainOrLayerFilter.LAVA;
            case ANNOTATION:
                return new LayerValue(Annotations.INSTANCE, filter.value);
            case ANNOTATION_ANY:
                return new LayerValue(Annotations.INSTANCE);
            default:
                throw new UnsupportedOperationException("Unsupported onlyOn filter type " + filter.objectType);
        }
    }

    /**
     * Make a value which has been typed into a spinner count, bringing it within the range if necessary, or go back to
     * the previous value if it isn't a number.
     */
    private void commit(JSpinner spinner) {
        final JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        final SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        try {
            if (model.getValue() instanceof Integer) {
                final int value = Integer.parseInt(field.getText().trim());
                spinner.setValue(Math.max((Integer) model.getMinimum(), Math.min((Integer) model.getMaximum(), value)));
            } else {
                final double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
                spinner.setValue(Math.max(((Number) model.getMinimum()).doubleValue(), Math.min(((Number) model.getMaximum()).doubleValue(), value)));
            }
        } catch (NumberFormatException e) {
            // Not a number, so show the value which is still in effect again
        }
        field.setValue(spinner.getValue());
    }

    /**
     * "Inside selection" with nothing selected lets the brush paint nowhere at all, which looks like nothing is
     * working, so say why.
     */
    private void updateNoSelectionHint() {
        boolean nothingSelected = false;
        if ((comboBoxMapArea.getSelectedItem() == MapArea.INSIDE_SELECTION) && isShowing()) {
            final App app = App.getInstanceIfExists();
            final Dimension dimension = (app != null) ? app.getDimension() : null;
            nothingSelected = (dimension != null) && (! dimension.containsOneOf(SelectionChunk.INSTANCE, SelectionBlock.INSTANCE));
        }
        labelNoSelection.setVisible(nothingSelected);
    }

    private void commitOnClickElsewhere(AWTEvent event) {
        if ((event.getID() == MouseEvent.MOUSE_PRESSED) && (event.getSource() instanceof Component)) {
            final Component clicked = (Component) event.getSource();
            for (JSpinner spinner: new JSpinner[] {spinnerAbove, spinnerBelow, spinnerSlope, spinnerCurvature}) {
                if (((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().isFocusOwner() && (! SwingUtilities.isDescendingFrom(clicked, spinner))) {
                    commit(spinner);
                    if (! clicked.isFocusable()) {
                        // Otherwise the keyboard shortcuts would keep going to the spinner
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                    }
                }
            }
        }
    }

    /**
     * Ctrl+Z and Ctrl+Y take back changes to these options as long as nothing has been done to the map since. After that
     * they are left to the undo and redo of the map.
     */
    private boolean dispatchUndoKey(KeyEvent event) {
        if ((event.getID() != KeyEvent.KEY_PRESSED) || (! (event.isControlDown() || event.isMetaDown())) || event.isAltDown()
                || (! isShowing()) || (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() instanceof Dialog)) {
            return false;
        } else if ((event.getKeyCode() == KeyEvent.VK_Z) && (! event.isShiftDown())) {
            return undo();
        } else if ((event.getKeyCode() == KeyEvent.VK_Y) || ((event.getKeyCode() == KeyEvent.VK_Z) && event.isShiftDown())) {
            return redo();
        } else {
            return false;
        }
    }

    private boolean undo() {
        // First take back a value which is still being typed
        for (JSpinner spinner: new JSpinner[] {spinnerAbove, spinnerBelow, spinnerSlope, spinnerCurvature}) {
            final JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            if (field.isFocusOwner() && (! field.getText().trim().equals(spinner.getValue().toString()))) {
                field.setValue(spinner.getValue());
                return true;
            }
        }
        return moveThroughHistory(undoHistory, redoHistory);
    }

    private boolean redo() {
        return moveThroughHistory(redoHistory, undoHistory);
    }

    private boolean moveThroughHistory(Deque<HistoryEntry> from, Deque<HistoryEntry> to) {
        forgetOutdatedHistory();
        if (from.isEmpty()) {
            return false;
        }
        to.push(new HistoryEntry(currentOptions, mapState()));
        restore(from.pop().options);
        return true;
    }

    private void rememberChange() {
        final Options options = currentOptions();
        if (historyPaused == 0 && (! options.equals(currentOptions))) {
            forgetOutdatedHistory();
            // Everything which belongs together counts as one change, such as a whole drag of a bar
            final long now = System.currentTimeMillis();
            final int dragNumber = heightScale.getDragNumber();
            final boolean sameGesture = (! undoHistory.isEmpty()) && (((dragNumber != 0) && (dragNumber == lastDragNumber)) || (now - lastChangeTime < SAME_GESTURE_TIME));
            if (! sameGesture) {
                undoHistory.push(new HistoryEntry(currentOptions, mapState()));
                while (undoHistory.size() > MAX_HISTORY) {
                    undoHistory.removeLast();
                }
            }
            redoHistory.clear();
            lastChangeTime = now;
            lastDragNumber = dragNumber;
        }
        currentOptions = options;
    }

    /**
     * Once the map has changed, changes to the options from before that can no longer be undone, as Ctrl+Z goes to the
     * map from then on.
     */
    private void forgetOutdatedHistory() {
        final Object mapState = mapState();
        undoHistory.removeIf(entry -> ! entry.mapState.equals(mapState));
        redoHistory.removeIf(entry -> ! entry.mapState.equals(mapState));
    }

    private Object mapState() {
        final App app = App.getInstanceIfExists();
        final Dimension dimension = (app != null) ? app.getDimension() : null;
        return (dimension != null) ? List.of(System.identityHashCode(dimension), dimension.getChangeNo()) : List.of();
    }

    private Options currentOptions() {
        return new Options((MapArea) comboBoxMapArea.getSelectedItem(),
                checkBoxAbove.isSelected(), (Integer) spinnerAbove.getValue(), checkBoxBelow.isSelected(), (Integer) spinnerBelow.getValue(),
                checkBoxFeather.isSelected(), checkBoxReplace.isSelected(), onlyOn, checkBoxExceptOn.isSelected(), exceptOn,
                checkBoxAboveSlope.isSelected(), checkBoxBelowSlope.isSelected(), (Integer) spinnerSlope.getValue(),
                curvatureMode(), ((Number) spinnerCurvature.getValue()).doubleValue());
    }

    private int curvatureMode() {
        return checkBoxHollows.isSelected() ? DefaultFilter.CURVATURE_HOLLOWS : (checkBoxBumps.isSelected() ? DefaultFilter.CURVATURE_BUMPS : DefaultFilter.CURVATURE_NONE);
    }

    private void restore(Options options) {
        historyPaused++;
        try {
            comboBoxMapArea.setSelectedItem(options.mapArea);
            checkBoxAbove.setSelected(options.above);
            spinnerAbove.setValue(options.aboveLevel);
            checkBoxBelow.setSelected(options.below);
            spinnerBelow.setValue(options.belowLevel);
            checkBoxFeather.setSelected(options.feather);
            checkBoxReplace.setSelected(options.replace);
            onlyOn = options.onlyOn;
            checkBoxExceptOn.setSelected(options.exceptOnEnabled);
            exceptOn = options.exceptOn;
            checkBoxAboveSlope.setSelected(options.aboveSlope);
            checkBoxBelowSlope.setSelected(options.belowSlope);
            spinnerSlope.setValue(options.slope);
            checkBoxHollows.setSelected(options.curvatureMode == DefaultFilter.CURVATURE_HOLLOWS);
            checkBoxBumps.setSelected(options.curvatureMode == DefaultFilter.CURVATURE_BUMPS);
            spinnerCurvature.setValue(options.curvatureDepth);
            installPaint(onlyOn, buttonReplace);
            installPaint(exceptOn, buttonExceptOn);
            setControlStates();
            updateNoSelectionHint();
            filterChanged();
        } finally {
            historyPaused--;
        }
        currentOptions = options;
        lastChangeTime = 0;
        lastDragNumber = 0;
    }

    private void heightScaleChanged(boolean aboveActive, int above, boolean belowActive, int below) {
        initialiseIfNecessary();
        updatingHeightScale = true;
        try {
            checkBoxAbove.setSelected(aboveActive);
            spinnerAbove.setValue(above);
            checkBoxBelow.setSelected(belowActive);
            spinnerBelow.setValue(below);
        } finally {
            updatingHeightScale = false;
        }
        setControlStates();
        filterChanged();
    }

    private void updateHeightScale() {
        final SpinnerNumberModel model = (SpinnerNumberModel) spinnerAbove.getModel();
        heightScale.setRange((Integer) model.getMinimum(), (Integer) model.getMaximum());
        heightScale.setLevels(checkBoxAbove.isSelected(), (Integer) spinnerAbove.getValue(), checkBoxBelow.isSelected(), (Integer) spinnerBelow.getValue());
    }

    private void setControlStates() {
        updateHeightScale();
        spinnerAbove.setEnabled(checkBoxAbove.isSelected());
        spinnerBelow.setEnabled(checkBoxBelow.isSelected());
        spinnerSlope.setEnabled(checkBoxAboveSlope.isSelected() || checkBoxBelowSlope.isSelected());
        spinnerCurvature.setEnabled(checkBoxHollows.isSelected() || checkBoxBumps.isSelected());
        // The buttons to choose what from only appear when they are needed
        buttonReplace.setVisible(checkBoxReplace.isSelected());
        listReplace.setVisible(checkBoxReplace.isSelected());
        buttonExceptOn.setVisible(checkBoxExceptOn.isSelected());
        listExceptOn.setVisible(checkBoxExceptOn.isSelected());
        checkBoxFeather.setEnabled(checkBoxAbove.isSelected() || checkBoxBelow.isSelected());
    }

    private JPopupMenu createReplaceMenu() {
        final JMenu menu = createObjectSelectionMenu(MENU_ONLY_ON, (object, name, icon) -> {
            onlyOn = addToSelection(onlyOn, object);
            installPaint(onlyOn, buttonReplace);
        }, true, onlyOn);
        final JPopupMenu popupMenu = new BetterJPopupMenu();
        Arrays.stream(menu.getMenuComponents()).forEach(popupMenu::add);
        return popupMenu;
    }

    private JPopupMenu createExceptOnMenu() {
        final JMenu menu = createObjectSelectionMenu(MENU_EXCEPT_ON, (object, name, icon) -> {
            exceptOn = addToSelection(exceptOn, object);
            installPaint(exceptOn, buttonExceptOn);
        }, true, exceptOn);
        final JPopupMenu popupMenu = new BetterJPopupMenu();
        Arrays.stream(menu.getMenuComponents()).forEach(popupMenu::add);
        return popupMenu;
    }
    
    @SuppressWarnings("unchecked") // Guaranteed by code
    private JMenu createObjectSelectionMenu(final String descriptor, final ObjectSelectionListener listener, final boolean addAnother, final Object currentSelection) {
        final JMenuItem waterItem = new JMenuItem("Water", ICON_WATER);
        waterItem.addActionListener(e -> listener.objectSelected(TerrainOrLayerFilter.WATER, "Water", null));
        JMenu popupMenu = new JMenu();
        popupMenu.add(waterItem);

        final JMenuItem lavaItem = new JMenuItem("Lava", ICON_LAVA);
        lavaItem.addActionListener(e -> listener.objectSelected(TerrainOrLayerFilter.LAVA, "Lava", null));
        popupMenu.add(lavaItem);

        final JMenuItem landItem = new JMenuItem("Land");
        landItem.addActionListener(e -> listener.objectSelected(TerrainOrLayerFilter.LAND, "Land", null));
        popupMenu.add(landItem);

        final JMenuItem eyedropperItem = new JMenuItem("Select on Map", ICON_EYEDROPPER);
        eyedropperItem.setToolTipText("Select a paint from the map");
        final App app = App.getInstance();
        final ColourScheme colourScheme = app.getColourScheme();
        if (mapSelectionListener != null) {
            eyedropperItem.addActionListener(e -> mapSelectionListener.mapSelectionRequested(descriptor, addAnother));
        } else {
            eyedropperItem.addActionListener(e -> App.getInstance().selectPaintOnMap(null, new Eyedropper.SelectionListener() {
                @Override
                public void terrainSelected(Terrain terrain) {
                    switch (descriptor) {
                        case MENU_ONLY_ON:
                            if (addAnother) {
                                addOnlyOn(terrain);
                            } else {
                                setOnlyOn(terrain);
                            }
                            break;
                        case MENU_EXCEPT_ON:
                            if (addAnother) {
                                addExceptOn(terrain);
                            } else {
                                setExceptOn(terrain);
                            }
                            break;
                    }
                }

                @Override
                public void layerSelected(Layer layer, int value) {
                    switch (descriptor) {
                        case MENU_ONLY_ON:
                            if (addAnother) {
                                addOnlyOn(layer, value);
                            } else {
                                setOnlyOn(layer, value);
                            }
                            break;
                        case MENU_EXCEPT_ON:
                            if (addAnother) {
                                addExceptOn(layer, value);
                            } else {
                                setExceptOn(layer, value);
                            }
                            break;
                    }
                }

                @Override public void selectionCancelled(boolean byUser) {}
            }));
        }
        popupMenu.add(eyedropperItem);

        final JMenu terrainMenu = new JMenu("Terrain");
        final JMenu customTerrainMenu = new JMenu("Custom");
        final JMenu stainedClayTerrainMenu = new JMenu("Stained Terracotta");
        for (Terrain terrain: Terrain.getConfiguredValues()) {
            final Terrain selectedTerrain = terrain;
            final String name = terrain.getName();
            final Icon icon = new ImageIcon(terrain.getScaledIcon(16, colourScheme));
            final JMenuItem menuItem = new JMenuItem(name, icon);
            menuItem.addActionListener(e -> listener.objectSelected(selectedTerrain, name, icon));
            if (terrain.isCustom()) {
                customTerrainMenu.add(menuItem);
            } else if (STAINED_TERRACOTTAS.contains(terrain)) {
                stainedClayTerrainMenu.add(menuItem);
            } else {
                terrainMenu.add(menuItem);
            }
        }
        terrainMenu.add(stainedClayTerrainMenu);
        if (customTerrainMenu.getMenuComponentCount() > 0) {
            terrainMenu.add(customTerrainMenu);
        }
        popupMenu.add(terrainMenu);
        
        final JMenu layerMenu = new JMenu("Layer");
        LayerManager.getInstance().getLayers().stream()
            .filter(layer -> ! layer.equals(Biome.INSTANCE))
            .forEach(layer -> {
                JMenuItem menuItem = new JMenuItem(layer.getName(), new ImageIcon(layer.getIcon()));
                menuItem.addActionListener(e -> listener.objectSelected(layer, layer.getName(), new ImageIcon(layer.getIcon())));
                layerMenu.add(menuItem);
            });
        final List<CustomLayer> customLayers = app.getCustomLayers();
        if (customLayers.size() > 15) {
            // If there are fifteen or more custom layers, split them by palette and move them to separate submenus to
            // try and conserve screen space
            app.getCustomLayersByPalette().entrySet().stream()
                .map((entry) -> {
                    String palette = entry.getKey();
                    JMenu paletteMenu = new JMenu(palette != null ? palette : "Hidden Layers");
                    entry.getValue().forEach(layer -> {
                        JMenuItem menuItem = new JMenuItem(layer.getName(), new ImageIcon(layer.getIcon()));
                        menuItem.addActionListener(e -> listener.objectSelected(layer, layer.getName(), new ImageIcon(layer.getIcon())));
                        paletteMenu.add(menuItem);
                    });
                    return paletteMenu;
                }).forEach(layerMenu::add);
        } else {
            customLayers.forEach(layer -> {
                JMenuItem menuItem = new JMenuItem(layer.getName(), new ImageIcon(layer.getIcon()));
                menuItem.addActionListener(e -> listener.objectSelected(layer, layer.getName(), new ImageIcon(layer.getIcon())));
                layerMenu.add(menuItem);
            });
        }
        popupMenu.add(layerMenu);

        final JMenu biomeMenu = new JMenu("Biome");
        final CustomBiomeManager customBiomeManager = app.getCustomBiomeManager();
        final BiomeHelper biomeHelper = new BiomeHelper(colourScheme, customBiomeManager, platform);
        List<CustomBiome> customBiomes = customBiomeManager.getCustomBiomes();
        if (! customBiomes.isEmpty()) {
            JMenu customBiomeMenu = new JMenu("Custom");
            for (CustomBiome customBiome: customBiomes) {
                final int selectedBiome = customBiome.getId();
                final String name = biomeHelper.getBiomeName(selectedBiome);
                final Icon icon = biomeHelper.getBiomeIcon(selectedBiome);
                final JMenuItem menuItem = new JMenuItem(name, icon);
                menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Biome.INSTANCE, selectedBiome), name, icon));
                customBiomeMenu.add(menuItem);
            }
            biomeMenu.add(customBiomeMenu);
        }
        final BiomeScheme biomeScheme = getBiomeScheme(platform);
        if (platform.capabilities.contains(NAMED_BIOMES)) {
            final Map<String, Integer> biomes = new TreeMap<>();
            for (int i = 0; i < biomeScheme.getBiomeCount(); i++) {
                if (biomeScheme.isBiomePresent(i)) {
                    biomes.put(biomeHelper.getBiomeName(i), i);
                }
            }
            biomes.forEach((name, id) -> {
                final Icon icon = biomeHelper.getBiomeIcon(id);
                final JMenuItem menuItem = new JMenuItem(name, icon);
                menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Biome.INSTANCE, id), name, icon));
                biomeMenu.add(menuItem);
            });
        } else {
            for (int i = 0; i < biomeScheme.getBiomeCount(); i++) {
                if (biomeScheme.isBiomePresent(i)) {
                    final int selectedBiome = i;
                    final String name = biomeHelper.getBiomeName(i);
                    final Icon icon = biomeHelper.getBiomeIcon(i);
                    final JMenuItem menuItem = new JMenuItem(name, icon);
                    menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Biome.INSTANCE, selectedBiome), name, icon));
                    biomeMenu.add(menuItem);
                }
            }
        }
        final JMenu autoBiomeSubMenu = new JMenu("Auto Biomes");
        final JMenuItem autoBiomesMenuItem = new JMenuItem("All Auto Biomes");
        autoBiomesMenuItem.addActionListener(e -> listener.objectSelected(TerrainOrLayerFilter.AUTO_BIOMES, "All Auto Biomes", null));
        autoBiomeSubMenu.add(autoBiomesMenuItem);
        for (int autoBiome: Dimension.POSSIBLE_AUTO_BIOMES) {
            final int selectedBiome = -autoBiome;
            final String name = biomeHelper.getBiomeName(autoBiome);
            final Icon icon = biomeHelper.getBiomeIcon(autoBiome);
            final JMenuItem menuItem = new JMenuItem(name, icon);
            menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Biome.INSTANCE, selectedBiome), name, icon));
            autoBiomeSubMenu.add(menuItem);
        }
        biomeMenu.add(autoBiomeSubMenu);
        popupMenu.add(biomeMenu);

        final JMenu annotationsMenu = new JMenu("Annotations");
        JMenuItem menuItem = new JMenuItem("All Annotations");
        menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Annotations.INSTANCE), "All Annotations", null));
        annotationsMenu.add(menuItem);
        for (int i = 1; i < 16; i++) {
            final int layerValue = i;
            final Icon icon  = createScaledColourIcon(Annotations.getColour(layerValue, colourScheme));
            final String colourName = Annotations.getColourName(layerValue);
            menuItem = new JMenuItem(colourName, icon);
            menuItem.addActionListener(e -> listener.objectSelected(new LayerValue(Annotations.INSTANCE, layerValue), colourName + " Annotations", icon));
            annotationsMenu.add(menuItem);
        }
        popupMenu.add(annotationsMenu);

        popupMenu = breakUpLongMenus(popupMenu, 25);

        return popupMenu;
    }

    /**
     * Recursively break up any long menus by moving any excess items to
     * submenus called "More".
     *
     * @param menu The menu hierarchy to break up.
     * @param maxLength The maximum number of items in each submenu.
     * @return The broken up menu hierarchy.
     */
    private JMenu breakUpLongMenus(JMenu menu, int maxLength) {
        if (menu.getMenuComponentCount() > maxLength) {
            JMenu replacementMenu = new JMenu(menu.getText());
            replacementMenu.setToolTipText(menu.getToolTipText());

            // First gather all submenus, which go on the first page, breaking
            // them up on the go if necessary
            for (Component menuItem: menu.getMenuComponents()) {
                if (menuItem instanceof JMenu) {
                    menu.remove(menuItem);
                    replacementMenu.add(breakUpLongMenus((JMenu) menuItem, maxLength));
                }
            }

            // Then gather the menu items which will still fit on the first page
            // and insert them before the submenus
            int index = 0;
            while ((replacementMenu.getMenuComponentCount() < (maxLength - 1)) && (menu.getMenuComponentCount() > 0)) {
                Component menuItem = menu.getMenuComponent(0);
                menu.remove(0);
                replacementMenu.add(menuItem, index++);
            }

            // Lastly, if there are still items left, create a "more" submenu
            // from the rest of the menu items and insert it between the menu
            // items and the submenus
            if (menu.getMenuComponentCount() > 0) {
                menu.setText("More");
                menu.setToolTipText(null);
                replacementMenu.add(breakUpLongMenus(menu, maxLength), index);
            }

            return replacementMenu;
        } else {
            for (int i = 0; i < menu.getMenuComponentCount(); i++) {
                if (menu.getMenuComponent(i) instanceof JMenu) {
                    JMenu subMenu = (JMenu) menu.getMenuComponent(i);
                    menu.remove(i);
                    menu.add(breakUpLongMenus(subMenu, maxLength), i);
                }
            }
            return menu;
        }
    }

    private void showReplaceMenu() {
        App.getInstance().cancelPaintSelection();
        JPopupMenu menu = createReplaceMenu();
        menu.show(checkBoxReplace, 0, checkBoxReplace.getHeight());
    }
    
    private void showExceptOnMenu() {
        App.getInstance().cancelPaintSelection();
        JPopupMenu menu = createExceptOnMenu();
        menu.show(checkBoxExceptOn, 0, checkBoxExceptOn.getHeight());
    }
    
    private void filterChanged() {
        rememberChange();
        if (listener != null) {
            Filter filter = getFilter();
            if (logger.isTraceEnabled()) {
                logger.trace("Reporting new filter " + filter + " to listener " + listener);
            }
            listener.filterChanged(filter);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef"}) // Managed by NetBeans
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        heightScale = new org.pepsoft.worldpainter.panels.HeightFilterScale();
        labelNoSelection = new javax.swing.JLabel();
        checkBoxAbove = new javax.swing.JCheckBox();
        spinnerAbove = new javax.swing.JSpinner();
        checkBoxBelow = new javax.swing.JCheckBox();
        spinnerBelow = new javax.swing.JSpinner();
        checkBoxReplace = new javax.swing.JCheckBox();
        buttonReplace = new javax.swing.JButton();
        checkBoxFeather = new javax.swing.JCheckBox();
        checkBoxExceptOn = new javax.swing.JCheckBox();
        buttonExceptOn = new javax.swing.JButton();
        checkBoxAboveSlope = new javax.swing.JCheckBox();
        checkBoxBelowSlope = new javax.swing.JCheckBox();
        spinnerSlope = new javax.swing.JSpinner();
        jLabel1 = new javax.swing.JLabel();
        comboBoxMapArea = new javax.swing.JComboBox<>(MapArea.values());

        checkBoxAbove.setFont(checkBoxAbove.getFont().deriveFont(checkBoxAbove.getFont().getSize()-1f));
        checkBoxAbove.setText("at or above");
        checkBoxAbove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxAboveActionPerformed(evt);
            }
        });

        spinnerAbove.setFont(spinnerAbove.getFont().deriveFont(spinnerAbove.getFont().getSize()-1f));
        spinnerAbove.setModel(new javax.swing.SpinnerNumberModel(0, 0, 255, 1));
        spinnerAbove.setEnabled(false);
        spinnerAbove.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerAboveStateChanged(evt);
            }
        });

        checkBoxBelow.setFont(checkBoxBelow.getFont().deriveFont(checkBoxBelow.getFont().getSize()-1f));
        checkBoxBelow.setText("at or below");
        checkBoxBelow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxBelowActionPerformed(evt);
            }
        });

        spinnerBelow.setFont(spinnerBelow.getFont().deriveFont(spinnerBelow.getFont().getSize()-1f));
        spinnerBelow.setModel(new javax.swing.SpinnerNumberModel(255, 0, 255, 1));
        spinnerBelow.setEnabled(false);
        spinnerBelow.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerBelowStateChanged(evt);
            }
        });

        checkBoxReplace.setFont(checkBoxReplace.getFont().deriveFont(checkBoxReplace.getFont().getSize()-1f));
        checkBoxReplace.setText("only on");
        checkBoxReplace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxReplaceActionPerformed(evt);
            }
        });

        buttonReplace.setFont(buttonReplace.getFont().deriveFont(buttonReplace.getFont().getSize()-1f));
        buttonReplace.setText("Choose\u2026");
        buttonReplace.setVisible(false);
        buttonReplace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonReplaceActionPerformed(evt);
            }
        });

        checkBoxFeather.setFont(checkBoxFeather.getFont().deriveFont(checkBoxFeather.getFont().getSize()-1f));
        checkBoxFeather.setText("fade at the limits");
        checkBoxFeather.setEnabled(false);
        checkBoxFeather.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxFeatherActionPerformed(evt);
            }
        });

        checkBoxExceptOn.setFont(checkBoxExceptOn.getFont().deriveFont(checkBoxExceptOn.getFont().getSize()-1f));
        checkBoxExceptOn.setText("except on");
        checkBoxExceptOn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxExceptOnActionPerformed(evt);
            }
        });

        buttonExceptOn.setFont(buttonExceptOn.getFont().deriveFont(buttonExceptOn.getFont().getSize()-1f));
        buttonExceptOn.setText("Choose\u2026");
        buttonExceptOn.setVisible(false);
        buttonExceptOn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonExceptOnActionPerformed(evt);
            }
        });

        checkBoxAboveSlope.setFont(checkBoxAboveSlope.getFont().deriveFont(checkBoxAboveSlope.getFont().getSize()-1f));
        checkBoxAboveSlope.setText("above");
        checkBoxAboveSlope.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxAboveSlopeActionPerformed(evt);
            }
        });

        checkBoxBelowSlope.setFont(checkBoxBelowSlope.getFont().deriveFont(checkBoxBelowSlope.getFont().getSize()-1f));
        checkBoxBelowSlope.setText("below");
        checkBoxBelowSlope.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxBelowSlopeActionPerformed(evt);
            }
        });

        spinnerSlope.setFont(spinnerSlope.getFont().deriveFont(spinnerSlope.getFont().getSize()-1f));
        spinnerSlope.setModel(new javax.swing.SpinnerNumberModel(45, 0, 89, 1));
        spinnerSlope.setEnabled(false);
        spinnerSlope.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerSlopeStateChanged(evt);
            }
        });

        jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getSize()-1f));
        jLabel1.setText("degrees");

        comboBoxMapArea.setFont(comboBoxMapArea.getFont().deriveFont(comboBoxMapArea.getFont().getSize()-1f));
        comboBoxMapArea.addActionListener(evt -> comboBoxMapAreaActionPerformed());

        labelNoSelection.setFont(labelNoSelection.getFont().deriveFont(labelNoSelection.getFont().getSize()-2f));
        labelNoSelection.setText("<html>Nothing is selected on<br>the map, so this brush<br>won't paint anywhere</html>");
        labelNoSelection.setVisible(false);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * One column in a narrow panel; in a wider one the fields which belong together go next to each other.
     */
    private void buildLayout() {
        setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        final JLabel mapAreaTitle = sectionTitle("Where on the map");
        final ToolCard mapAreaCard = OptionCards.whereOnTheMap();
        ToolCard.showOnHover(mapAreaTitle, mapAreaCard);
        ToolCard.showOnHover(comboBoxMapArea, mapAreaCard);
        add(mapAreaTitle, constraints);
        // Not as wide as its longest item, which would make the whole panel wider; it gets the width of the panel anyway
        comboBoxMapArea.setPreferredSize(new java.awt.Dimension(1, comboBoxMapArea.getPreferredSize().height));
        constraints.insets = new Insets(2, 0, 0, 0);
        add(comboBoxMapArea, constraints);
        add(labelNoSelection, constraints);

        constraints.insets = new Insets(10, 0, 0, 0);
        add(heightScale, constraints);
        final JPanel heightFields = new JPanel(new org.pepsoft.worldpainter.util.ResponsiveColumnsLayout(scaled(COLUMN_WIDTH), scaled(4), 2));
        heightFields.add(withCard(block(checkBoxAbove, spinnerAbove), OptionCards.atOrAbove()));
        heightFields.add(withCard(block(checkBoxBelow, spinnerBelow), OptionCards.atOrBelow()));
        constraints.insets = new Insets(4, 0, 0, 0);
        add(heightFields, constraints);
        constraints.insets = new Insets(0, 0, 0, 0);
        add(withCard(checkBoxFeather, OptionCards.fade()), constraints);

        constraints.insets = new Insets(10, 0, 0, 0);
        add(sectionTitle("What is on the map"), constraints);
        final JPanel otherFields = new JPanel(new org.pepsoft.worldpainter.util.ResponsiveColumnsLayout(scaled(COLUMN_WIDTH), scaled(4), 3));
        otherFields.add(withCard(block(checkBoxReplace, listReplace, buttonReplace), OptionCards.onlyOn()));
        otherFields.add(withCard(block(checkBoxExceptOn, listExceptOn, buttonExceptOn), OptionCards.exceptOn()));
        final JLabel slopeLabel = new JLabel("slope");
        slopeLabel.setFont(slopeLabel.getFont().deriveFont(slopeLabel.getFont().getSize() - 1f));
        otherFields.add(withCard(block(slopeLabel, row(checkBoxAboveSlope, checkBoxBelowSlope), row(spinnerSlope, jLabel1)), OptionCards.slope()));
        final JLabel curvatureLabel = new JLabel("curvature");
        curvatureLabel.setFont(curvatureLabel.getFont().deriveFont(curvatureLabel.getFont().getSize() - 1f));
        final JLabel blocksLabel = new JLabel("blocks");
        blocksLabel.setFont(blocksLabel.getFont().deriveFont(blocksLabel.getFont().getSize() - 1f));
        for (JCheckBox checkBox: new JCheckBox[] {checkBoxHollows, checkBoxBumps}) {
            checkBox.setFont(checkBox.getFont().deriveFont(checkBox.getFont().getSize() - 1f));
            checkBox.addActionListener(e -> {
                initialiseIfNecessary();
                if (checkBox.isSelected()) {
                    ((checkBox == checkBoxHollows) ? checkBoxBumps : checkBoxHollows).setSelected(false);
                }
                setControlStates();
                filterChanged();
            });
        }
        spinnerCurvature.setFont(spinnerCurvature.getFont().deriveFont(spinnerCurvature.getFont().getSize() - 1f));
        spinnerCurvature.setEnabled(false);
        spinnerCurvature.addChangeListener(e -> filterChanged());
        otherFields.add(withCard(block(curvatureLabel, row(checkBoxHollows, checkBoxBumps), row(spinnerCurvature, blocksLabel)), OptionCards.curvature()));
        constraints.insets = new Insets(2, 0, 0, 0);
        add(otherFields, constraints);
    }

    private static <T extends JComponent> T withCard(T component, ToolCard card) {
        ToolCard.showOnHover(component, card);
        return component;
    }

    private static int scaled(int size) {
        return com.formdev.flatlaf.util.UIScale.scale(size);
    }

    private static JLabel sectionTitle(String text) {
        final JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize() - 2f));
        return label;
    }

    /**
     * Components under each other, as wide as there is room for if they are buttons, and otherwise as wide as they want
     * to be.
     */
    private static JPanel block(JComponent... components) {
        final JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        for (JComponent component: components) {
            component.setAlignmentX(LEFT_ALIGNMENT);
            if (! (component instanceof SelectionList)) {
                final java.awt.Dimension size = component.getPreferredSize();
                component.setMaximumSize(new java.awt.Dimension((component instanceof JButton) ? Short.MAX_VALUE : size.width, size.height));
            }
            block.add(component);
        }
        return block;
    }

    private static JPanel row(JComponent... components) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        for (JComponent component: components) {
            row.add(component);
        }
        return row;
    }

    private void initialiseIfNecessary() {
        if (! initialised) {
            // Prevent the intensity being changed when somebody tries to type a
            // value:
            Action nullAction = new AbstractAction("Do nothing") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Do nothing
                }

                private static final long serialVersionUID = 1L;
            };
            getActionMap().put("doNothing", nullAction);
            InputMap inputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            App app = App.getInstance();
            inputMap.put(app.ACTION_INTENSITY_10_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_20_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_30_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_40_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_50_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_60_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_70_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_80_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_90_PERCENT.getAcceleratorKey(), "doNothing");
            inputMap.put(app.ACTION_INTENSITY_100_PERCENT.getAcceleratorKey(), "doNothing");

            initialised = true;
        }
    }

    private void checkBoxAboveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxAboveActionPerformed
        initialiseIfNecessary();
        setControlStates();
        filterChanged();
    }//GEN-LAST:event_checkBoxAboveActionPerformed

    private void checkBoxBelowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxBelowActionPerformed
        initialiseIfNecessary();
        setControlStates();
        filterChanged();
    }//GEN-LAST:event_checkBoxBelowActionPerformed

    private void checkBoxReplaceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxReplaceActionPerformed
        initialiseIfNecessary();
        setControlStates();
        if (checkBoxReplace.isSelected() && (onlyOn == null)) {
            showReplaceMenu();
        } else {
            filterChanged();
        }
    }//GEN-LAST:event_checkBoxReplaceActionPerformed

    private void buttonReplaceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonReplaceActionPerformed
        showReplaceMenu();
    }//GEN-LAST:event_buttonReplaceActionPerformed

    private void spinnerAboveStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerAboveStateChanged
        if (! updatingHeightScale) {
            updateHeightScale();
            filterChanged();
        }
    }//GEN-LAST:event_spinnerAboveStateChanged

    private void spinnerBelowStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerBelowStateChanged
        if (! updatingHeightScale) {
            updateHeightScale();
            filterChanged();
        }
    }//GEN-LAST:event_spinnerBelowStateChanged

    private void checkBoxFeatherActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxFeatherActionPerformed
        filterChanged();
    }//GEN-LAST:event_checkBoxFeatherActionPerformed

    private void checkBoxExceptOnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxExceptOnActionPerformed
        initialiseIfNecessary();
        setControlStates();
        if (checkBoxExceptOn.isSelected() && (exceptOn == null)) {
            showExceptOnMenu();
        } else {
            filterChanged();
        }
    }//GEN-LAST:event_checkBoxExceptOnActionPerformed

    private void buttonExceptOnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonExceptOnActionPerformed
        showExceptOnMenu();
    }//GEN-LAST:event_buttonExceptOnActionPerformed

    private void checkBoxAboveSlopeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxAboveSlopeActionPerformed
        initialiseIfNecessary();
        if (checkBoxAboveSlope.isSelected()) {
            checkBoxBelowSlope.setSelected(false);
        }
        setControlStates();
        filterChanged();
    }//GEN-LAST:event_checkBoxAboveSlopeActionPerformed

    private void checkBoxBelowSlopeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxBelowSlopeActionPerformed
        initialiseIfNecessary();
        if (checkBoxBelowSlope.isSelected()) {
            checkBoxAboveSlope.setSelected(false);
        }
        setControlStates();
        filterChanged();
    }//GEN-LAST:event_checkBoxBelowSlopeActionPerformed

    private void spinnerSlopeStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerSlopeStateChanged
        filterChanged();
    }//GEN-LAST:event_spinnerSlopeStateChanged

    private void comboBoxMapAreaActionPerformed() {
        initialiseIfNecessary();
        updateNoSelectionHint();
        filterChanged();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.pepsoft.worldpainter.panels.HeightFilterScale heightScale;
    private javax.swing.JLabel labelNoSelection;
    private javax.swing.JButton buttonExceptOn;
    private javax.swing.JButton buttonReplace;
    private javax.swing.JCheckBox checkBoxAbove;
    private javax.swing.JCheckBox checkBoxAboveSlope;
    private javax.swing.JCheckBox checkBoxBelow;
    private javax.swing.JCheckBox checkBoxBelowSlope;
    private javax.swing.JCheckBox checkBoxExceptOn;
    private javax.swing.JCheckBox checkBoxFeather;
    private javax.swing.JComboBox<MapArea> comboBoxMapArea;
    private javax.swing.JCheckBox checkBoxReplace;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JSpinner spinnerAbove;
    private javax.swing.JSpinner spinnerBelow;
    private javax.swing.JSpinner spinnerSlope;
    // End of variables declaration//GEN-END:variables

    private ColourScheme colourScheme;
    private CustomBiomeManager customBiomeManager;
    private Object onlyOn, exceptOn;
    private Listener listener;
    private boolean initialised;
    private ObservableBoolean selectionState;
    private Platform platform;
    private MapSelectionListener mapSelectionListener;

    public static final String MENU_ONLY_ON = "onlyOn";
    public static final String MENU_EXCEPT_ON = "exceptOn";
    
    private static final Icon ICON_WATER = loadScaledIcon("org/pepsoft/worldpainter/icons/flood.png");
    private final SelectionList listReplace = new SelectionList(), listExceptOn = new SelectionList();
    private boolean updatingHeightScale;
    private final Deque<HistoryEntry> undoHistory = new ArrayDeque<>(), redoHistory = new ArrayDeque<>();
    private Options currentOptions;
    private int historyPaused, lastDragNumber;
    private long lastChangeTime;
    private static final int MAX_HISTORY = 100, COLUMN_WIDTH = 135;
    private static final long SAME_GESTURE_TIME = 300;
    private static final Icon ICON_LAVA = loadScaledIcon("org/pepsoft/worldpainter/icons/flood_with_lava.png");
    private static final Icon ICON_EYEDROPPER = loadScaledIcon("org/pepsoft/worldpainter/icons/eyedropper.png");
    private static final Icon ICON_PLUS = loadScaledIcon("org/pepsoft/worldpainter/icons/plus.png");
    private static final Logger logger = LoggerFactory.getLogger(BrushOptions.class);
    private static final long serialVersionUID = 1L;
    
    /**
     * The terrain types and layers which have been chosen, under each other, as high as they need to be.
     */
    private static final class SelectionList extends JPanel {
        SelectionList() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setVisible(false);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * The part of the map a brush can be limited to, by the selection which was made on it.
     */
    private enum MapArea {
        WHOLE_MAP("Whole map"), INSIDE_SELECTION("Inside selection"), OUTSIDE_SELECTION("Outside selection");

        MapArea(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    /**
     * The state of all the options, to go back to.
     */
    private record Options(MapArea mapArea, boolean above, int aboveLevel, boolean below, int belowLevel,
                           boolean feather, boolean replace, Object onlyOn, boolean exceptOnEnabled, Object exceptOn,
                           boolean aboveSlope, boolean belowSlope, int slope, int curvatureMode, double curvatureDepth) {}

    private final JCheckBox checkBoxHollows = new JCheckBox("hollows"), checkBoxBumps = new JCheckBox("bumps");
    private final JSpinner spinnerCurvature = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 20.0, 0.5));

    /**
     * Options to go back to, and the state of the map when they were left, since they only apply to that.
     */
    private record HistoryEntry(Options options, Object mapState) {}

    public interface Listener {
        void filterChanged(Filter newFilter);
    }
    
    public interface ObjectSelectionListener {
        void objectSelected(Object object, String name, Icon icon);
    }
    
    public interface MapSelectionListener {
        /**
         * The user has requested to select a value from the map.
         * 
         * @param descriptor The descriptor associated with the menu from which the request is being made.
         * @param addAnother Whether the request is being made from the "Add Another" submenu.
         */
        void mapSelectionRequested(String descriptor, boolean addAnother);
    }
}