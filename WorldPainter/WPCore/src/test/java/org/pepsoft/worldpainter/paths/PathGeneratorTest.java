package org.pepsoft.worldpainter.paths;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class PathGeneratorTest {
    @Test
    public void testResampleSpacing() {
        final List<double[]> line = new ArrayList<>();
        line.add(new double[] {0, 0});
        line.add(new double[] {10, 0});
        line.add(new double[] {10, 10});
        final List<double[]> points = PathGenerator.resample(line, 1.0);
        assertEquals(21, points.size());
        for (int i = 1; i < points.size(); i++) {
            final double d = Math.hypot(points.get(i)[0] - points.get(i - 1)[0], points.get(i)[1] - points.get(i - 1)[1]);
            assertTrue("spacing " + d, d <= 1.0001);
        }
    }

    @Test
    public void testGradeIsLimited() {
        final float[] profile = {64, 64, 90, 90, 64, 64, 64};
        PathGenerator.limitGrade(profile, 2);
        for (int i = 1; i < profile.length; i++) {
            assertTrue(Math.abs(profile[i] - profile[i - 1]) <= 2.0001f);
        }
    }

    @Test
    public void testValleyBecomesBridge() {
        // Flat land at 70 with a 30 block wide ravine down to 40
        final int n = 120;
        final float[] terrain = new float[n], water = new float[n];
        Arrays.fill(water, Float.NaN);
        for (int i = 0; i < n; i++) {
            terrain[i] = ((i >= 45) && (i < 75)) ? 40 : 70;
        }
        final PathSettings settings = new PathSettings();
        settings.setBridges(true);
        settings.setTunnels(false);
        final float[] profile = PathGenerator.profile(terrain, water, settings);
        final byte[] types = PathGenerator.segments(terrain, water, profile, settings);
        assertEquals(PathPlan.BRIDGE, types[60]);
        assertEquals(PathPlan.GROUND, types[10]);
        assertTrue("deck height " + profile[60], profile[60] > 60);
    }

    @Test
    public void testHillBecomesTunnel() {
        final int n = 120;
        final float[] terrain = new float[n], water = new float[n];
        Arrays.fill(water, Float.NaN);
        for (int i = 0; i < n; i++) {
            terrain[i] = ((i >= 45) && (i < 75)) ? 110 : 70;
        }
        final PathSettings settings = new PathSettings();
        settings.setBridges(false);
        settings.setTunnels(true);
        final float[] profile = PathGenerator.profile(terrain, water, settings);
        final byte[] types = PathGenerator.segments(terrain, water, profile, settings);
        assertEquals(PathPlan.TUNNEL, types[60]);
        assertTrue("tunnel floor " + profile[60], profile[60] < 80);
    }

    @Test
    public void testNaturalPathFollowsLand() {
        final int n = 100;
        final float[] terrain = new float[n], water = new float[n];
        Arrays.fill(water, Float.NaN);
        for (int i = 0; i < n; i++) {
            terrain[i] = 64 + i * 0.1f;
        }
        final PathSettings settings = new PathSettings();
        settings.setBridges(false);
        settings.setTunnels(false);
        final float[] profile = PathGenerator.profile(terrain, water, settings);
        for (int i = 0; i < n; i++) {
            assertEquals(terrain[i], profile[i], 1.5f);
        }
        final byte[] types = PathGenerator.segments(terrain, water, profile, settings);
        for (byte type: types) {
            assertEquals(PathPlan.GROUND, type);
        }
    }
}
