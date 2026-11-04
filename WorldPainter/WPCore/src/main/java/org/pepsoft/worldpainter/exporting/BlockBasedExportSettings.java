package org.pepsoft.worldpainter.exporting;

public abstract class BlockBasedExportSettings extends ExportSettings {
    public abstract boolean isCalculateSkyLight();

    public abstract boolean isCalculateBlockLight();

    public abstract boolean isCalculateLeafDistance();

    public abstract boolean isRemoveFloatingLeaves();

    /**
     * The serial version the class always had, written down so that changing the class does not change it, which would
     * make every world with export settings unloadable. See {@link org.pepsoft.worldpainter.WorldIO#tolerate}.
     */
    private static final long serialVersionUID = 3392734375000318011L;
}