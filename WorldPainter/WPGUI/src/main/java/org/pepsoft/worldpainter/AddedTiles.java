package org.pepsoft.worldpainter;

import org.pepsoft.util.AttributeKey;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Undo and redo for the tiles which an operation adds to a dimension, such as blending the edges of the map into the
 * land around it. The undo history of tiles cannot do that by itself, since the tiles did not exist before the
 * operation (undoing it would leave them behind, empty). Instead each such operation is numbered with an undoable
 * attribute of the dimension, and a copy of the tiles it added is kept, so that when undo takes the number back the
 * tiles are removed, and when redo puts it back they are added again.
 */
public final class AddedTiles {
    private AddedTiles() {
        // Prevent instantiation
    }

    /**
     * Remember the tiles which an operation added (those which are not in {@code tilesBefore}), as part of the undo
     * step of the operation. Must be called before the save point of the operation is armed.
     */
    public static void record(Dimension dimension, Set<Point> tilesBefore) {
        final List<Point> added = dimension.getTileCoords().stream().filter(coords -> ! tilesBefore.contains(coords)).collect(Collectors.toList());
        if (added.isEmpty()) {
            return;
        }
        final Map<Point, byte[]> copies = added.parallelStream().collect(Collectors.toConcurrentMap(coords -> coords, coords -> serialise(dimension.getTile(coords))));
        final int number = getCount(dimension) + 1;
        ADDITIONS.computeIfAbsent(dimension, d -> new HashMap<>()).put(number, copies);
        dimension.setAttribute(ATTRIBUTE_COUNT, number, true);
    }

    /**
     * Remove the tiles which an operation added before it was cancelled (and its other changes undone).
     */
    public static void remove(Dimension dimension, Set<Point> tilesBefore) {
        for (Point coords: new ArrayList<>(dimension.getTileCoords())) {
            if (! tilesBefore.contains(coords)) {
                dimension.removeTile(coords);
            }
        }
    }

    /**
     * The number of the most recent operation which added tiles and which has not been undone.
     */
    public static int getCount(Dimension dimension) {
        return ((dimension != null) && ADDITIONS.containsKey(dimension)) ? dimension.getAttribute(ATTRIBUTE_COUNT) : 0;
    }

    /**
     * Remove the tiles of the operations which an undo took back.
     *
     * @param countBefore The count before the undo.
     */
    public static void undone(Dimension dimension, int countBefore) {
        final Map<Integer, Map<Point, byte[]>> additions = (dimension != null) ? ADDITIONS.get(dimension) : null;
        if (additions == null) {
            return;
        }
        final int countAfter = dimension.getAttribute(ATTRIBUTE_COUNT);
        for (int number = countBefore; number > countAfter; number--) {
            final Map<Point, byte[]> copies = additions.get(number);
            if (copies != null) {
                for (Point coords: copies.keySet()) {
                    if (dimension.isTilePresent(coords.x, coords.y)) {
                        dimension.removeTile(coords);
                    }
                }
            }
        }
    }

    /**
     * Put back the tiles of the operations which a redo put back.
     *
     * @param countBefore The count before the redo.
     */
    public static void redone(Dimension dimension, int countBefore) {
        final Map<Integer, Map<Point, byte[]>> additions = (dimension != null) ? ADDITIONS.get(dimension) : null;
        if (additions == null) {
            return;
        }
        final int countAfter = dimension.getAttribute(ATTRIBUTE_COUNT);
        for (int number = countBefore + 1; number <= countAfter; number++) {
            final Map<Point, byte[]> copies = additions.get(number);
            if (copies != null) {
                final List<Tile> tiles = copies.entrySet().parallelStream()
                        .filter(entry -> ! dimension.isTilePresent(entry.getKey().x, entry.getKey().y))
                        .map(entry -> deserialise(entry.getValue()))
                        .collect(Collectors.toList());
                for (Tile tile: tiles) {
                    dimension.addTile(tile);
                    // The tile starts out with writable buffers, which would let the next edit bypass the undo history
                    tile.savePointArmed();
                }
            }
        }
    }

    private static byte[] serialise(Tile tile) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(new DeflaterOutputStream(bytes, new Deflater(Deflater.BEST_SPEED)))) {
            out.writeObject(tile);
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error copying tile", e);
        }
        return bytes.toByteArray();
    }

    private static Tile deserialise(byte[] bytes) {
        try (ObjectInputStream in = new ObjectInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
            return (Tile) in.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error copying tile", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The copies of the added tiles, per dimension and per operation.
     */
    private static final Map<Dimension, Map<Integer, Map<Point, byte[]>>> ADDITIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AttributeKey<Integer> ATTRIBUTE_COUNT = new AttributeKey<>("org.pepsoft.worldpainter.addedTilesCount", 0);
}
