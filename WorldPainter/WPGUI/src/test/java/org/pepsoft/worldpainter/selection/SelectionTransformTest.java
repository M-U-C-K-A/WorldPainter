package org.pepsoft.worldpainter.selection;

import org.junit.Test;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SelectionTransformTest {
    /**
     * A selection of 3 by 2 turned and mirrored: every column lands inside the turned bounds, on a column of its own,
     * and the corners go where they should.
     */
    @Test
    public void testTurnsAndMirror() {
        final int width = 3, height = 2;
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean mirror: new boolean[] {false, true}) {
                final java.awt.Dimension size = SelectionHelper.transformedSize(width, height, rotation);
                final Set<Point> seen = new HashSet<>();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        final Point p = SelectionHelper.transform(x, y, width, height, rotation, mirror);
                        assertEquals(true, (p.x >= 0) && (p.y >= 0) && (p.x < size.width) && (p.y < size.height));
                        seen.add(p);
                    }
                }
                assertEquals(width * height, seen.size());
            }
        }
        // A quarter turn clockwise: the top left corner goes to the top right, the top right to the bottom right
        assertEquals(new Point(1, 0), SelectionHelper.transform(0, 0, 3, 2, 1, false));
        assertEquals(new Point(1, 2), SelectionHelper.transform(2, 0, 3, 2, 1, false));
        // Mirrored: the top left corner goes to the top right
        assertEquals(new Point(2, 0), SelectionHelper.transform(0, 0, 3, 2, 0, true));
        // Half a turn: the top left corner goes to the bottom right
        assertEquals(new Point(2, 1), SelectionHelper.transform(0, 0, 3, 2, 2, false));
    }
}
