package org.pepsoft.worldpainter.brushes;

import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

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

    enum Stamp {
        PEAK("Peak"), RANGE("Mountain range"), MASSIF("Massif"), VOLCANO("Volcano"), CRATER("Crater rim"), MESA("Mesa"),
        DUNES("Dunes"), HILLS("Hills"), GULLIES("Eroded dome");

        Stamp(String displayName) {
            this.displayName = displayName;
        }

        final String displayName;
    }
}
