package org.pepsoft.worldpainter.brushes;

import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.*;

/**
 * A pack of procedurally generated "terrain stamp" brushes: height map shaped brushes for sculpting recognisable
 * landforms (peaks, ranges, volcanoes, mesas, ...) in one stroke with the height tools. The stamps are generated
 * deterministically, so they look the same every time.
 */
public final class TerrainStamps {
    private TerrainStamps() {
        // Prevent instantiation
    }

    public static List<Brush> createBrushes() {
        final List<Brush> brushes = new ArrayList<>();
        for (Stamp stamp: Stamp.values()) {
            brushes.add(new BitmapBrush(render(stamp), stamp.displayName));
        }
        return brushes;
    }

    /**
     * The stamps by family (mountains, hills and plateaus, ...), in the order in which to show them.
     */
    public static Map<String, List<Brush>> createBrushesByFamily() {
        final Map<String, List<Brush>> families = new LinkedHashMap<>();
        for (Family family: Family.values()) {
            families.put(family.displayName, new ArrayList<>());
        }
        Arrays.stream(Stamp.values()).parallel()
                .map(stamp -> Map.entry(stamp, (Brush) new BitmapBrush(render(stamp), stamp.displayName)))
                .sequential()
                .forEachOrdered(entry -> families.get(entry.getKey().family.displayName).add(entry.getValue()));
        return families;
    }

    static BufferedImage render(Stamp stamp) {
        final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);
        final WritableRaster raster = image.getRaster();
        final SimplexNoise noise = new SimplexNoise(stamp.ordinal() * 7919L + 17);
        final float[] values = new float[SIZE * SIZE];
        float max = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Normalised coordinates, -1 to 1
                final double u = (x + 0.5) / SIZE * 2 - 1, v = (y + 0.5) / SIZE * 2 - 1;
                final float value = (float) Math.max(0, height(stamp, noise, u, v));
                values[y * SIZE + x] = value;
                max = Math.max(max, value);
            }
        }
        for (int i = 0; i < values.length; i++) {
            raster.setSample(i % SIZE, i / SIZE, 0, Math.round(Math.min(1, values[i] / max) * 255));
        }
        return image;
    }

    private static double height(Stamp stamp, SimplexNoise noise, double u, double v) {
        final double r = Math.sqrt(u * u + v * v);
        // Fade everything out towards the edge of the brush
        final double edge = smoothStep(1.0, 0.82, r);
        switch (stamp) {
            case PEAK: {
                final double warpedR = r + 0.12 * fbm(noise, u * 2, v * 2, 3);
                final double cone = Math.pow(Math.max(0, 1 - warpedR), 1.6);
                return cone * (0.75 + 0.35 * ridged(noise, u * 3, v * 3, 4)) * edge;
            }
            case RANGE: {
                // A ridge along a slightly curved line, with spurs
                final double centre = 0.18 * Math.sin(u * 2.4) + 0.08 * fbm(noise, u * 2, 3.7, 2);
                final double across = Math.abs(v - centre);
                final double along = smoothStep(1.0, 0.55, Math.abs(u));
                final double profile = Math.pow(Math.max(0, 1 - across / 0.42), 1.5);
                return profile * along * (0.65 + 0.45 * ridged(noise, u * 4, v * 4, 4)) * edge;
            }
            case MASSIF: {
                double h = 0;
                final double[][] peaks = {{-0.3, -0.2, 0.55, 1.0}, {0.28, -0.1, 0.5, 0.85}, {0.0, 0.32, 0.45, 0.75}, {-0.05, -0.45, 0.35, 0.6}};
                for (double[] peak: peaks) {
                    final double d = Math.hypot(u - peak[0], v - peak[1]) / peak[2];
                    h = Math.max(h, peak[3] * Math.pow(Math.max(0, 1 - d), 1.4));
                }
                return h * (0.8 + 0.3 * ridged(noise, u * 3.5, v * 3.5, 4)) * edge;
            }
            case VOLCANO: {
                final double cone = Math.pow(Math.max(0, 1 - r), 1.3) * (0.92 + 0.08 * fbm(noise, u * 6, v * 6, 3));
                // Crater at the top
                final double crater = 0.42 * smoothStep(0.22, 0.08, r);
                return Math.max(0, cone - crater) * edge;
            }
            case CRATER: {
                // A raised rim around a bowl; use the lower tool to dig it
                final double rim = Math.exp(-Math.pow((r - 0.62) / 0.13, 2));
                final double bowl = 0.25 * smoothStep(0.62, 0.0, r);
                return (rim + bowl * 0.3) * (0.85 + 0.2 * fbm(noise, u * 5, v * 5, 3)) * edge;
            }
            case MESA: {
                final double warpedR = r + 0.14 * fbm(noise, u * 2.5, v * 2.5, 4);
                final double plateau = smoothStep(0.78, 0.58, warpedR);
                // Terraced flanks
                final double flank = Math.floor(plateau * 4) / 4 + smoothStep(0.0, 1.0, (plateau * 4) % 1) / 4;
                return (0.85 * flank + 0.15 * plateau) * edge;
            }
            case DUNES: {
                final double phase = u * 3.2 + 0.35 * fbm(noise, u * 1.5, v * 1.5, 2);
                final double f = phase - Math.floor(phase);
                final double profile = (f < 0.75) ? smoothStep(0, 0.75, f) : smoothStep(1, 0.75, f);
                return profile * (0.7 + 0.3 * fbm(noise, u * 2, v * 2 + 9, 2)) * smoothStep(1.0, 0.4, r);
            }
            case HILLS: {
                final double h = 0.5 + 0.5 * fbm(noise, u * 2.2, v * 2.2, 3);
                return Math.pow(h, 1.8) * smoothStep(1.0, 0.3, r);
            }
            case GULLIES: {
                // A dome cut by dendritic erosion channels
                final double dome = Math.pow(Math.max(0, 1 - r), 0.9);
                final double channels = 1 - Math.pow(1 - ridged(noise, u * 3, v * 3, 5), 3);
                return dome * (0.55 + 0.45 * channels) * edge;
            }
            case ALPINE_PEAK: {
                // A pyramidal horn with three sharp arêtes running down from the summit
                final double theta = Math.atan2(v, u) + 0.35 * fbm(noise, u * 2, v * 2, 2);
                final double arms = Math.pow(0.5 + 0.5 * Math.cos(3 * theta), 3);
                final double cone = Math.pow(Math.max(0, 1 - r), 1.15);
                return cone * (0.6 + 0.4 * arms * smoothStep(0.0, 0.25, r) + 0.4 * smoothStep(0.25, 0.0, r)) * (0.85 + 0.25 * ridged(noise, u * 4, v * 4, 4)) * edge;
            }
            case TWIN_PEAKS: {
                final double first = Math.pow(Math.max(0, 1 - Math.hypot(u + 0.32, v - 0.05) / 0.58), 1.3);
                final double second = 0.85 * Math.pow(Math.max(0, 1 - Math.hypot(u - 0.34, v + 0.08) / 0.52), 1.3);
                final double saddle = 0.35 * Math.exp(-Math.pow(v + 0.015 * u, 2) / 0.03) * smoothStep(0.75, 0.2, Math.abs(u));
                return Math.max(Math.max(first, second), saddle) * (0.8 + 0.3 * ridged(noise, u * 3.5, v * 3.5, 4)) * edge;
            }
            case CRAG: {
                // A steep, jagged spire on a scree apron
                final double warpedR = r + 0.1 * fbm(noise, u * 3, v * 3, 3);
                final double spire = Math.pow(Math.max(0, 1 - warpedR / 0.5), 0.75) * (0.75 + 0.35 * ridged(noise, u * 6, v * 6, 4));
                final double apron = 0.22 * Math.pow(Math.max(0, 1 - r), 2);
                return Math.max(spire, apron) * edge;
            }
            case MOUNTAIN_CHAIN: {
                // A line of peaks of different heights joined by a lower ridge
                final double centre = 0.15 * Math.sin(u * 2.0 + 0.6) + 0.06 * fbm(noise, u * 2.5, 5.3, 2);
                final double across = Math.abs(v - centre);
                double h = 0.45 * Math.pow(Math.max(0, 1 - across / 0.3), 1.4) * smoothStep(1.0, 0.6, Math.abs(u));
                final double[][] peaks = {{-0.62, 0.55}, {-0.3, 0.9}, {0.02, 0.7}, {0.32, 1.0}, {0.62, 0.6}};
                for (double[] peak: peaks) {
                    final double peakV = 0.15 * Math.sin(peak[0] * 2.0 + 0.6);
                    final double d = Math.hypot(u - peak[0], v - peakV) / 0.3;
                    h = Math.max(h, peak[1] * Math.pow(Math.max(0, 1 - d), 1.3));
                }
                return h * (0.75 + 0.35 * ridged(noise, u * 4, v * 4, 4)) * edge;
            }
            case ERODED_RIDGE: {
                final double centre = 0.12 * Math.sin(u * 2.8) + 0.06 * fbm(noise, u * 2, 1.3, 2);
                final double across = Math.abs(v - centre);
                final double profile = Math.pow(Math.max(0, 1 - across / 0.5), 1.2) * smoothStep(1.0, 0.5, Math.abs(u));
                final double channels = 1 - Math.pow(1 - ridged(noise, u * 4, v * 4, 5), 3);
                return profile * (0.45 + 0.55 * channels) * edge;
            }
            case CIRQUE: {
                // A horseshoe of steep walls around a glacial basin, open towards the bottom of the brush
                final double ring = Math.exp(-Math.pow((r - 0.55) / 0.17, 2));
                final double opening = smoothStep(0.55, -0.1, v);
                final double backWall = 0.4 * smoothStep(0.2, -0.6, v) * smoothStep(0.85, 0.5, r);
                final double basin = 0.12 * smoothStep(0.5, 0.0, r);
                return (ring * (0.4 + 0.6 * opening) + backWall + basin) * (0.8 + 0.3 * ridged(noise, u * 4, v * 4, 4)) * edge;
            }
            case PLATEAU: {
                final double warpedR = r + 0.18 * fbm(noise, u * 2, v * 2, 4);
                final double top = smoothStep(0.82, 0.66, warpedR);
                return (0.88 * top + 0.04 * fbm(noise, u * 5, v * 5, 3) * top + 0.12 * Math.pow(Math.max(0, 1 - r), 2)) * edge;
            }
            case BUTTE: {
                final double warpedR = r + 0.1 * fbm(noise, u * 3, v * 3, 3);
                final double top = smoothStep(0.46, 0.36, warpedR);
                final double talus = 0.3 * Math.pow(Math.max(0, 1 - r / 0.8), 1.5);
                return Math.max(top * (0.95 + 0.05 * fbm(noise, u * 6, v * 6, 2)), talus) * edge;
            }
            case ESCARPMENT: {
                // High ground on one side of a steep, irregular cliff
                final double cliffLine = u + 0.18 * fbm(noise, v * 2.2, 3.1, 4);
                final double high = smoothStep(-0.06, 0.06, cliffLine);
                return (0.25 + 0.7 * high + 0.05 * fbm(noise, u * 4, v * 4, 3)) * smoothStep(1.0, 0.7, r);
            }
            case KARST_TOWERS: {
                // Steep limestone towers scattered over low ground
                double h = 0.08 * (0.5 + 0.5 * fbm(noise, u * 3, v * 3, 2));
                final double[][] towers = {{-0.45, -0.3, 0.17, 0.85}, {-0.1, -0.5, 0.13, 0.6}, {0.3, -0.35, 0.16, 1.0}, {-0.5, 0.2, 0.14, 0.7},
                        {-0.05, 0.05, 0.2, 0.95}, {0.42, 0.12, 0.13, 0.65}, {-0.22, 0.48, 0.15, 0.8}, {0.2, 0.5, 0.12, 0.55}};
                for (double[] tower: towers) {
                    final double d = Math.hypot(u - tower[0], v - tower[1]) + 0.04 * fbm(noise, u * 7 + tower[0] * 9, v * 7, 2);
                    h = Math.max(h, tower[3] * smoothStep(tower[2], tower[2] * 0.6, d) * (0.92 + 0.08 * fbm(noise, u * 9, v * 9, 2)));
                }
                return h * edge;
            }
            case SHIELD_VOLCANO: {
                // A broad, gentle dome with a small caldera
                final double dome = Math.pow(Math.max(0, 1 - r * r), 1.6) * (0.95 + 0.05 * fbm(noise, u * 5, v * 5, 3));
                final double caldera = 0.1 * smoothStep(0.14, 0.05, r);
                return Math.max(0, dome - caldera) * edge;
            }
            case VALLEY: {
                // A winding V shaped valley with side gullies; use it with the Lower tool to carve
                final double centre = 0.28 * Math.sin(u * 2.2 + 0.4) + 0.08 * fbm(noise, u * 2, 7.7, 2);
                final double across = Math.abs(v - centre);
                final double profile = Math.pow(Math.max(0, 1 - across / 0.32), 1.4) * smoothStep(1.0, 0.65, Math.abs(u));
                final double gullies = 0.3 * Math.pow(ridged(noise, u * 5, v * 5, 3), 4) * smoothStep(0.55, 0.2, across);
                return (profile + gullies * profile) * smoothStep(1.0, 0.85, r);
            }
            case TERRACES: {
                // A hill cut into terraced fields
                final double hill = Math.pow(Math.max(0, 1 - (r + 0.08 * fbm(noise, u * 2, v * 2, 3))), 1.1);
                final double steps = 7;
                final double level = Math.floor(hill * steps), fraction = hill * steps - level;
                return ((level + smoothStep(0.8, 1.0, fraction)) / steps) * edge;
            }
            default:
                throw new InternalError();
        }
    }

    private static double fbm(SimplexNoise noise, double x, double y, int octaves) {
        double sum = 0, amplitude = 1, total = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise.noise(x, y) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            x *= 2;
            y *= 2;
        }
        return sum / total;
    }

    private static double ridged(SimplexNoise noise, double x, double y, int octaves) {
        double sum = 0, amplitude = 1, total = 0;
        for (int i = 0; i < octaves; i++) {
            final double n = 1 - Math.abs(noise.noise(x + 31.7 * i, y - 17.3 * i));
            sum += n * n * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            x *= 2;
            y *= 2;
        }
        return sum / total;
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        final double t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }

    private static final int SIZE = 256;

    /**
     * The stamps. New ones must be added at the end, since the noise of each stamp depends on its position.
     */
    enum Stamp {
        PEAK("Peak", Family.MOUNTAINS), RANGE("Mountain range", Family.MOUNTAINS), MASSIF("Massif", Family.MOUNTAINS),
        VOLCANO("Volcano", Family.VOLCANOES), CRATER("Crater rim", Family.VOLCANOES), MESA("Mesa", Family.HILLS),
        DUNES("Dunes", Family.HILLS), HILLS("Hills", Family.HILLS), GULLIES("Eroded dome", Family.HILLS),
        ALPINE_PEAK("Alpine peak", Family.MOUNTAINS), TWIN_PEAKS("Twin peaks", Family.MOUNTAINS), CRAG("Crag", Family.MOUNTAINS),
        MOUNTAIN_CHAIN("Mountain chain", Family.MOUNTAINS), ERODED_RIDGE("Eroded ridge", Family.MOUNTAINS),
        CIRQUE("Glacial cirque", Family.MOUNTAINS), PLATEAU("Plateau", Family.HILLS), BUTTE("Butte", Family.HILLS),
        ESCARPMENT("Escarpment", Family.HILLS), KARST_TOWERS("Karst towers", Family.HILLS),
        SHIELD_VOLCANO("Shield volcano", Family.VOLCANOES), VALLEY("Valley (use with Lower)", Family.VALLEYS),
        TERRACES("Terraced hill", Family.HILLS);

        Stamp(String displayName, Family family) {
            this.displayName = displayName;
            this.family = family;
        }

        final String displayName;
        final Family family;
    }

    enum Family {
        MOUNTAINS("Mountains"), HILLS("Hills & plateaus"), VOLCANOES("Volcanoes & craters"), VALLEYS("Valleys");

        Family(String displayName) {
            this.displayName = displayName;
        }

        final String displayName;
    }
}
