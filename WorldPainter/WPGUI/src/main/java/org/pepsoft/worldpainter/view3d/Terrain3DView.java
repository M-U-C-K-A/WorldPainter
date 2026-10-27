package org.pepsoft.worldpainter.view3d;

import org.pepsoft.worldpainter.util.ThemeUtils;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * An interactive 3D view of a {@link TerrainModel}. The height field is turned into a triangle mesh (with walls down to
 * a base, like a diorama) which is projected with a true perspective (or orthographic) camera and rasterised in software
 * with a depth buffer, on all cores. The depth buffer also tells exactly which location is under the mouse.
 */
class Terrain3DView extends JComponent {
    Terrain3DView() {
        setOpaque(true);
        setFocusable(true);
        final MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
                requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                final int dx = e.getX() - lastX, dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                if (model == null) {
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e) || e.isShiftDown()) {
                    // Pan: drag the ground along with the mouse
                    final double scale = distance / focal(getWidth());
                    final double rx = Math.cos(yaw), ry = Math.sin(yaw); // Right
                    final double gx = Math.sin(yaw), gy = -Math.cos(yaw); // Forward along the ground
                    final double along = 1 / Math.max(0.25, Math.abs(Math.sin(pitch)));
                    targetX += (-dx * rx + dy * gx * along) * scale;
                    targetY += (-dx * ry + dy * gy * along) * scale;
                    targetX = Math.max(0, Math.min(model.width, targetX));
                    targetY = Math.max(0, Math.min(model.height, targetY));
                } else {
                    // Orbit
                    yaw += dx * 0.008;
                    pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dy * 0.006));
                }
                interacting = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                interacting = false;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverListener != null) {
                    hoverListener.accept(-1);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                distance = Math.max(MIN_DISTANCE, Math.min(maxDistance, distance * Math.pow(1.12, e.getPreciseWheelRotation())));
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Focus on the clicked location
                    final double[] point = worldPointAt(e.getX(), e.getY());
                    if (point != null) {
                        targetX = point[0];
                        targetY = point[1];
                        targetH = point[2] / exaggeration;
                        distance = Math.max(MIN_DISTANCE, distance * 0.6);
                        repaint();
                    }
                }
            }

            private int lastX, lastY;
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    void setModel(TerrainModel model) {
        final boolean first = this.model == null;
        this.model = model;
        maxDistance = Math.max(model.width, model.height) * 4.0;
        if (first || (targetX > model.width) || (targetY > model.height)) {
            resetView();
        }
        invalidateMesh();
        repaint();
    }

    TerrainModel getModel() {
        return model;
    }

    void resetView() {
        if (model == null) {
            return;
        }
        targetX = model.width / 2.0;
        targetY = model.height / 2.0;
        targetH = (model.highestHeight - model.lowestHeight) / 3.0 / model.step;
        yaw = Math.toRadians(-35);
        pitch = Math.toRadians(35);
        // Far enough for the whole model to fit in the view
        final double radius = Math.sqrt(model.width * model.width + model.height * model.height) / 2;
        final int w = getWidth(), h = getHeight();
        distance = ((w > 0) && (h > 0)) ? (radius * 1.1 * focal(w) / (Math.min(w, h) / 2.0)) : (radius * 2.8);
        distance = Math.min(maxDistance, distance);
        repaint();
    }

    void rotateBy(double degrees) {
        yaw += Math.toRadians(degrees);
        repaint();
    }

    void setYawDegrees(double degrees) {
        yaw = Math.toRadians(degrees);
        repaint();
    }

    void setPitchDegrees(double degrees) {
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, Math.toRadians(degrees)));
        repaint();
    }

    Mode getMode() {
        return mode;
    }

    void setMode(Mode mode) {
        this.mode = mode;
        invalidateMesh();
        repaint();
    }

    void setShowWater(boolean showWater) {
        this.showWater = showWater;
        invalidateMesh();
        repaint();
    }

    void setShowContours(boolean showContours) {
        this.showContours = showContours;
        repaint();
    }

    void setExaggeration(float exaggeration) {
        this.exaggeration = exaggeration;
        repaint();
    }

    void setBlocks(boolean blocks) {
        this.blocks = blocks;
        invalidateMesh();
        repaint();
    }

    void setOrthographic(boolean orthographic) {
        this.orthographic = orthographic;
        repaint();
    }

    /**
     * Set a listener which is notified of the sample under the mouse (or -1).
     */
    void setHoverListener(IntConsumer hoverListener) {
        this.hoverListener = hoverListener;
    }

    // Colours

    /**
     * Calculate the colour of every sample for the current mode, including shading.
     */
    private int[] calculateColours() {
        final int n = model.width * model.height;
        final int[] result = new int[n];
        final float range = Math.max(1, model.highestHeight - model.lowestHeight);
        for (int i = 0; i < n; i++) {
            if (! model.present[i]) {
                continue;
            }
            final float h = model.heights[i];
            final float shade = model.normalShades[i];
            int colour;
            switch (mode) {
                case HEIGHT:
                    colour = shade(ramp(HEIGHT_RAMP, (h - model.lowestHeight) / range), 0.55f + 0.45f * shade);
                    break;
                case SLOPE:
                    colour = shade(ramp(SLOPE_RAMP, Math.min(1, model.slopes[i] / 60f)), 0.7f + 0.3f * shade);
                    break;
                case BIOME:
                    colour = shade((model.biomes[i] >= 0) ? VanillaBiomes.getColour(VanillaBiomes.fromWorldPainterBiome(model.biomes[i])) : 0x808080, 0.5f + 0.5f * shade);
                    break;
                case RELIEF:
                    colour = shade(0xd6d3d1, 0.2f + 0.85f * shade);
                    break;
                default:
                    colour = shade(model.colours[i], 0.45f + 0.6f * shade);
                    break;
            }
            if (isWater(i)) {
                final float depth = Math.min(1, (model.waterLevels[i] - h) / 24f);
                final int water = blend(0x4a8fe7, 0x1d3f8f, depth);
                colour = (mode == Mode.TERRAIN) ? water : blend(colour, water, 0.55f + 0.35f * depth);
            }
            result[i] = colour;
        }
        return result;
    }

    private boolean isWater(int sample) {
        return showWater && (mode != Mode.SLOPE) && (model.waterLevels[sample] > model.heights[sample]);
    }

    /**
     * The height of the surface shown for a sample, in blocks: the terrain, or the water surface.
     */
    private float surfaceHeight(int sample) {
        return isWater(sample) ? model.waterLevels[sample] : model.heights[sample];
    }

    // Mesh

    private void invalidateMesh() {
        mesh = null;
        coarseMesh = null;
    }

    private Mesh getMesh(boolean coarse) {
        if (coarse) {
            if (coarseMesh == null) {
                coarseMesh = blocks ? buildBlockMesh(COARSE_BLOCK_BUDGET) : buildMesh(COARSE_CELL_BUDGET);
            }
            return coarseMesh;
        } else {
            if (mesh == null) {
                mesh = blocks ? buildBlockMesh(BLOCK_BUDGET) : buildMesh(CELL_BUDGET);
            }
            return mesh;
        }
    }

    /**
     * Build a mesh of the height field as Minecraft would show it: every column a flat topped block at a whole number
     * of blocks of height, with vertical sides down to its lower neighbours (or to the base of the model at the edges).
     * Large models use columns of two, four, ... samples so that the number of columns stays within a budget.
     */
    private Mesh buildBlockMesh(int columnBudget) {
        final int[] colours = calculateColours();
        int stride = 1;
        while (((long) (model.width / stride)) * (model.height / stride) > columnBudget) {
            stride *= 2;
        }
        final int nx = (model.width + stride - 1) / stride, ny = (model.height + stride - 1) / stride;
        // The height of the top of each column in blocks above the lowest point, or NaN where there is no land
        final float[] tops = new float[nx * ny];
        final int[] samples = new int[nx * ny];
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final int sample = (gy * stride) * model.width + gx * stride;
                samples[gy * nx + gx] = sample;
                tops[gy * nx + gx] = model.present[sample] ? ((float) Math.floor(surfaceHeight(sample)) + 1 - (float) Math.floor(model.lowestHeight)) : Float.NaN;
            }
        }
        // Neighbours: north, east, south, west, with the light falling on sides facing that way (from the north west)
        final int[] dxs = {0, 1, 0, -1}, dys = {-1, 0, 1, 0};
        final float[] lights = {0.82f, 0.62f, 0.55f, 0.75f};
        int columns = 0, sides = 0;
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final float top = tops[gy * nx + gx];
                if (Float.isNaN(top)) {
                    continue;
                }
                columns++;
                for (int d = 0; d < 4; d++) {
                    if (sideBelow(tops, nx, ny, gx + dxs[d], gy + dys[d], top)) {
                        sides++;
                    }
                }
            }
        }
        final Mesh mesh = new Mesh((columns + sides) * 4, (columns + sides) * 2);
        int v = 0, t = 0;
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final float top = tops[gy * nx + gx];
                if (Float.isNaN(top)) {
                    continue;
                }
                final float x0 = gx * stride, y0 = gy * stride, x1 = Math.min(model.width, x0 + stride), y1 = Math.min(model.height, y0 + stride);
                final int colour = colours[samples[gy * nx + gx]];
                v = addQuad(mesh, v, t, x0, y0, top, x1, y0, top, x1, y1, top, x0, y1, top, colour, colour);
                t += 2;
            }
        }
        mesh.wallTriangleStart = t;
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final float top = tops[gy * nx + gx];
                if (Float.isNaN(top)) {
                    continue;
                }
                final float x0 = gx * stride, y0 = gy * stride, x1 = Math.min(model.width, x0 + stride), y1 = Math.min(model.height, y0 + stride);
                final int colour = colours[samples[gy * nx + gx]];
                for (int d = 0; d < 4; d++) {
                    final int ngx = gx + dxs[d], ngy = gy + dys[d];
                    if (! sideBelow(tops, nx, ny, ngx, ngy, top)) {
                        continue;
                    }
                    final boolean edge = (ngx < 0) || (ngy < 0) || (ngx >= nx) || (ngy >= ny) || Float.isNaN(tops[ngy * nx + ngx]);
                    final float bottom = edge ? Float.NaN : tops[ngy * nx + ngx];
                    final int upper = shade(colour, lights[d]), lower = edge ? shade(EARTH, 0.55f * lights[d] / 0.82f) : shade(colour, lights[d] * 0.85f);
                    switch (d) {
                        case 0: v = addQuad(mesh, v, t, x0, y0, top, x1, y0, top, x1, y0, bottom, x0, y0, bottom, upper, lower); break;
                        case 1: v = addQuad(mesh, v, t, x1, y0, top, x1, y1, top, x1, y1, bottom, x1, y0, bottom, upper, lower); break;
                        case 2: v = addQuad(mesh, v, t, x1, y1, top, x0, y1, top, x0, y1, bottom, x1, y1, bottom, upper, lower); break;
                        default: v = addQuad(mesh, v, t, x0, y1, top, x0, y0, top, x0, y0, bottom, x0, y1, bottom, upper, lower); break;
                    }
                    t += 2;
                }
            }
        }
        mesh.floor = -Math.max(6f, Math.max(model.width, model.height) * 0.025f) * model.step;
        return mesh;
    }

    /**
     * Whether a column needs a side towards a neighbour: when the neighbour is lower, or there is no land there.
     */
    private static boolean sideBelow(float[] tops, int nx, int ny, int gx, int gy, float top) {
        if ((gx < 0) || (gy < 0) || (gx >= nx) || (gy >= ny)) {
            return true;
        }
        final float neighbour = tops[gy * nx + gx];
        return Float.isNaN(neighbour) || (neighbour < top);
    }

    /**
     * Add a quad as two triangles; the first two corners get the upper colour, the last two the lower colour.
     */
    private static int addQuad(Mesh mesh, int v, int t, float xa, float ya, float ha, float xb, float yb, float hb, float xc, float yc, float hc, float xd, float yd, float hd, int upper, int lower) {
        final float[] xs = {xa, xb, xc, xd}, ys = {ya, yb, yc, yd}, hs = {ha, hb, hc, hd};
        for (int i = 0; i < 4; i++) {
            mesh.x[v + i] = xs[i];
            mesh.y[v + i] = ys[i];
            mesh.h[v + i] = hs[i];
            mesh.colour[v + i] = (i < 2) ? upper : lower;
        }
        mesh.setTriangle(t, v, v + 1, v + 2);
        mesh.setTriangle(t + 1, v, v + 2, v + 3);
        return v + 4;
    }

    /**
     * Build a triangle mesh of the height field, using every sample or (for large models) every second, fourth, ...
     * sample so that the number of cells stays within a budget, plus walls from the edges of the surface down to the
     * base of the model.
     */
    private Mesh buildMesh(int cellBudget) {
        final int[] colours = calculateColours();
        int stride = 1;
        while (((long) (model.width / stride)) * (model.height / stride) > cellBudget) {
            stride *= 2;
        }
        // The sample coordinates of the grid lines, always including the last row and column
        final int[] xs = gridLines(model.width, stride), ys = gridLines(model.height, stride);
        final int nx = xs.length, ny = ys.length;
        final int gridVertices = nx * ny;
        final int[] samples = new int[gridVertices];
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final int sample = ys[gy] * model.width + xs[gx];
                samples[gy * nx + gx] = model.present[sample] ? sample : -1;
            }
        }

        // Count the cells and the wall segments, to size the arrays
        int cells = 0, walls = 0;
        for (int gy = 0; gy < ny - 1; gy++) {
            for (int gx = 0; gx < nx - 1; gx++) {
                if (isCell(samples, nx, gx, gy)) {
                    cells++;
                }
            }
        }
        // Wall segments: grid edges with a cell on one side only
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx - 1; gx++) {
                if (isCell(samples, nx, gx, gy - 1, ny) != isCell(samples, nx, gx, gy, ny)) {
                    walls++;
                }
            }
        }
        for (int gx = 0; gx < nx; gx++) {
            for (int gy = 0; gy < ny - 1; gy++) {
                if (isCell(samples, nx, gx - 1, gy, ny) != isCell(samples, nx, gx, gy, ny)) {
                    walls++;
                }
            }
        }

        final Mesh mesh = new Mesh(gridVertices + walls * 4, (cells + walls) * 2);
        // Surface vertices
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx; gx++) {
                final int v = gy * nx + gx, sample = samples[v];
                mesh.x[v] = xs[gx] + 0.5f;
                mesh.y[v] = ys[gy] + 0.5f;
                if (sample >= 0) {
                    mesh.h[v] = surfaceHeight(sample) - model.lowestHeight;
                    mesh.colour[v] = colours[sample];
                }
            }
        }
        int t = 0;
        for (int gy = 0; gy < ny - 1; gy++) {
            for (int gx = 0; gx < nx - 1; gx++) {
                if (isCell(samples, nx, gx, gy)) {
                    final int a = gy * nx + gx, b = a + 1, c = a + nx + 1, d = a + nx;
                    // Split the cell along the diagonal that best follows the surface
                    if (Math.abs(mesh.h[a] - mesh.h[c]) <= Math.abs(mesh.h[b] - mesh.h[d])) {
                        mesh.setTriangle(t++, a, b, c);
                        mesh.setTriangle(t++, a, c, d);
                    } else {
                        mesh.setTriangle(t++, a, b, d);
                        mesh.setTriangle(t++, b, c, d);
                    }
                }
            }
        }
        mesh.wallTriangleStart = t;
        // Walls, with their own vertices so that they can be coloured as earth and lit by their direction. Faces
        // turned towards the light (from the north west) are lighter
        int v = gridVertices;
        for (int gy = 0; gy < ny; gy++) {
            for (int gx = 0; gx < nx - 1; gx++) {
                final boolean above = isCell(samples, nx, gx, gy - 1, ny), below = isCell(samples, nx, gx, gy, ny);
                if (above != below) {
                    v = addWall(mesh, v, t, gy * nx + gx, gy * nx + gx + 1, above ? 0.6f : 0.95f);
                    t += 2;
                }
            }
        }
        for (int gx = 0; gx < nx; gx++) {
            for (int gy = 0; gy < ny - 1; gy++) {
                final boolean left = isCell(samples, nx, gx - 1, gy, ny), right = isCell(samples, nx, gx, gy, ny);
                if (left != right) {
                    v = addWall(mesh, v, t, gy * nx + gx, (gy + 1) * nx + gx, left ? 0.7f : 0.9f);
                    t += 2;
                }
            }
        }
        mesh.floor = -Math.max(6f, Math.max(model.width, model.height) * 0.025f) * model.step;
        return mesh;
    }

    private int addWall(Mesh mesh, int v, int t, int top1, int top2, float light) {
        for (int i = 0; i < 2; i++) {
            final int top = (i == 0) ? top1 : top2;
            final int surface = mesh.colour[top];
            final int wallTop = shade(blend(surface, EARTH, 0.35f), 0.85f * light), wallBottom = shade(EARTH, 0.55f * light);
            mesh.x[v + i * 2] = mesh.x[top];
            mesh.y[v + i * 2] = mesh.y[top];
            mesh.h[v + i * 2] = mesh.h[top];
            mesh.colour[v + i * 2] = wallTop;
            mesh.x[v + i * 2 + 1] = mesh.x[top];
            mesh.y[v + i * 2 + 1] = mesh.y[top];
            mesh.h[v + i * 2 + 1] = Float.NaN; // The base of the model
            mesh.colour[v + i * 2 + 1] = wallBottom;
        }
        mesh.setTriangle(t, v, v + 2, v + 3);
        mesh.setTriangle(t + 1, v, v + 3, v + 1);
        return v + 4;
    }

    private static int[] gridLines(int size, int stride) {
        final int count = (size - 1) / stride + 1 + (((size - 1) % stride != 0) ? 1 : 0);
        final int[] lines = new int[count];
        for (int i = 0; i < count; i++) {
            lines[i] = Math.min(size - 1, i * stride);
        }
        return lines;
    }

    private static boolean isCell(int[] samples, int nx, int gx, int gy) {
        final int a = gy * nx + gx;
        return (samples[a] >= 0) && (samples[a + 1] >= 0) && (samples[a + nx] >= 0) && (samples[a + nx + 1] >= 0);
    }

    private static boolean isCell(int[] samples, int nx, int gx, int gy, int ny) {
        return (gx >= 0) && (gy >= 0) && (gx < nx - 1) && (gy < ny - 1) && isCell(samples, nx, gx, gy);
    }

    // Rendering

    @Override
    protected void paintComponent(Graphics g) {
        final int width = getWidth(), height = getHeight();
        if ((width <= 0) || (height <= 0)) {
            return;
        }
        if (model == null) {
            g.setColor(ThemeUtils.getCanvasBackground());
            g.fillRect(0, 0, width, height);
            return;
        }
        // Render at the resolution of the screen (which may be higher than that of the component on high DPI displays),
        // except while the user is dragging, when a lower resolution and a coarser mesh keep the view responsive
        final Graphics2D g2 = (Graphics2D) g;
        final double scale = interacting ? 1.0 : Math.max(1.0, Math.min(2.0, g2.getTransform().getScaleX()));
        final int w = Math.max(1, (int) Math.round(width * scale)), h = Math.max(1, (int) Math.round(height * scale));
        if ((image == null) || (image.getWidth() != w) || (image.getHeight() != h)) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            depthBuffer = new float[w * h];
            heightBuffer = new float[w * h];
        }
        camera = new Camera(w, h);
        render(getMesh(interacting), camera, ((DataBufferInt) image.getRaster().getDataBuffer()).getData(), w, h);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, 0, 0, width, height, null);
    }

    /**
     * The focal length in pixels for a view of a particular width: a horizontal field of view of about 45 degrees,
     * which looks natural without distorting the land at the edges of the view.
     */
    private static double focal(int width) {
        return Math.max(width, 1) * 1.2;
    }

    private void render(Mesh mesh, Camera camera, int[] pixels, int w, int h) {
        final boolean dark = ThemeUtils.isDarkTheme();
        final int skyTop = dark ? 0x0b1220 : 0xdbeafe, skyBottom = dark ? 0x18181b : 0xf8fafc;
        for (int y = 0; y < h; y++) {
            Arrays.fill(pixels, y * w, (y + 1) * w, blend(skyTop, skyBottom, (float) y / h));
        }
        Arrays.fill(depthBuffer, 0);
        Arrays.fill(heightBuffer, Float.NaN);

        // Transform all vertices to the camera, and project them
        final int n = mesh.vertexCount;
        final float[] cz = new float[n], sx = new float[n], sy = new float[n], depth = new float[n], q = new float[n];
        final float[] cx = new float[n], cy = new float[n];
        final int[] colour = new int[n];
        final float vertical = exaggeration / model.step, floor = mesh.floor / model.step;
        final double fogStart = distance * 0.85, fogEnd = distance * 2.6;
        final int fogColour = skyBottom;
        IntStream.range(0, (n + 4095) / 4096).parallel().forEach(chunk -> {
            for (int v = chunk * 4096; v < Math.min(n, (chunk + 1) * 4096); v++) {
                final double wx = mesh.x[v], wy = mesh.y[v], wh = Float.isNaN(mesh.h[v]) ? floor : mesh.h[v] * vertical;
                final double dx = wx - camera.x, dy = wy - camera.y, dh = wh - camera.h;
                final double xc = dx * camera.rx + dy * camera.ry;
                final double yc = dx * camera.ux + dy * camera.uy + dh * camera.uh;
                final double zc = dx * camera.fx + dy * camera.fy + dh * camera.fh;
                cx[v] = (float) xc;
                cy[v] = (float) yc;
                cz[v] = (float) zc;
                if (orthographic) {
                    sx[v] = (float) (camera.centreX + camera.scale * xc);
                    sy[v] = (float) (camera.centreY - camera.scale * yc);
                    depth[v] = (float) (ORTHO_DEPTH - zc);
                    q[v] = 1;
                } else if (zc > NEAR) {
                    sx[v] = (float) (camera.centreX + camera.focal * xc / zc);
                    sy[v] = (float) (camera.centreY - camera.focal * yc / zc);
                    depth[v] = (float) (1 / zc);
                    q[v] = depth[v];
                }
                final float fog = (float) Math.max(0, Math.min(0.6, (zc - fogStart) / (fogEnd - fogStart)));
                colour[v] = (fog > 0) ? blend(mesh.colour[v], fogColour, fog) : mesh.colour[v];
            }
        });

        // Sort the triangles into horizontal bands of the screen, then rasterise the bands in parallel
        final int bandCount = Math.max(1, Math.min(h / 8, Runtime.getRuntime().availableProcessors() * 3));
        final int bandHeight = (h + bandCount - 1) / bandCount;
        final int triangles = mesh.triangleCount;
        final int chunkCount = Math.max(1, Math.min(64, triangles / 20000 + 1));
        final IntList[][] bins = new IntList[chunkCount][bandCount];
        final IntList[] clipped = new IntList[chunkCount];
        IntStream.range(0, chunkCount).parallel().forEach(chunk -> {
            final IntList[] chunkBins = bins[chunk];
            for (int b = 0; b < bandCount; b++) {
                chunkBins[b] = new IntList();
            }
            clipped[chunk] = new IntList();
            final int from = (int) ((long) triangles * chunk / chunkCount), to = (int) ((long) triangles * (chunk + 1) / chunkCount);
            for (int t = from; t < to; t++) {
                final int a = mesh.triangles[t * 3], b = mesh.triangles[t * 3 + 1], c = mesh.triangles[t * 3 + 2];
                if (! orthographic) {
                    final boolean inA = cz[a] > NEAR, inB = cz[b] > NEAR, inC = cz[c] > NEAR;
                    if (! (inA || inB || inC)) {
                        continue;
                    } else if (! (inA && inB && inC)) {
                        clipped[chunk].add(t);
                        continue;
                    }
                }
                final float minX = Math.min(sx[a], Math.min(sx[b], sx[c])), maxX = Math.max(sx[a], Math.max(sx[b], sx[c]));
                final float minY = Math.min(sy[a], Math.min(sy[b], sy[c])), maxY = Math.max(sy[a], Math.max(sy[b], sy[c]));
                if ((maxX < 0) || (minX > w) || (maxY < 0) || (minY > h)) {
                    continue;
                }
                final int firstBand = Math.max(0, (int) minY / bandHeight), lastBand = Math.min(bandCount - 1, (int) maxY / bandHeight);
                for (int band = firstBand; band <= lastBand; band++) {
                    chunkBins[band].add(t);
                }
            }
        });
        final int wallStart = mesh.wallTriangleStart;
        IntStream.range(0, bandCount).parallel().forEach(band -> {
            final int y0 = band * bandHeight, y1 = Math.min(h, y0 + bandHeight);
            final Rasteriser rasteriser = new Rasteriser(pixels, w, y0, y1);
            for (int chunk = 0; chunk < chunkCount; chunk++) {
                final IntList list = bins[chunk][band];
                for (int i = 0; i < list.size; i++) {
                    final int t = list.values[i];
                    final int a = mesh.triangles[t * 3], b = mesh.triangles[t * 3 + 1], c = mesh.triangles[t * 3 + 2];
                    final boolean wall = t >= wallStart;
                    rasteriser.triangle(sx[a], sy[a], depth[a], q[a], colour[a], wall ? Float.NaN : mesh.h[a],
                            sx[b], sy[b], depth[b], q[b], colour[b], wall ? Float.NaN : mesh.h[b],
                            sx[c], sy[c], depth[c], q[c], colour[c], wall ? Float.NaN : mesh.h[c]);
                }
                // Triangles crossing the near plane: clip them, then draw the resulting polygon
                final IntList list2 = clipped[chunk];
                for (int i = 0; i < list2.size; i++) {
                    final int t = list2.values[i];
                    rasteriser.clippedTriangle(mesh, t, t >= wallStart, cx, cy, cz, colour, camera);
                }
            }
        });

        if (showContours) {
            drawContours(pixels, w, h);
        }
    }

    /**
     * Darken the pixels where the height crosses a multiple of the contour interval.
     */
    private void drawContours(int[] pixels, int w, int h) {
        final float[] heights = heightBuffer;
        final float offset = model.lowestHeight;
        IntStream.range(0, h - 1).parallel().forEach(y -> {
            for (int x = 0; x < w - 1; x++) {
                final int i = y * w + x;
                final float here = heights[i];
                if (Float.isNaN(here)) {
                    continue;
                }
                final int band = (int) Math.floor((here + offset) / CONTOUR_INTERVAL);
                final float right = heights[i + 1], below = heights[i + w];
                if ((! Float.isNaN(right) && ((int) Math.floor((right + offset) / CONTOUR_INTERVAL) != band))
                        || (! Float.isNaN(below) && ((int) Math.floor((below + offset) / CONTOUR_INTERVAL) != band))) {
                    pixels[i] = shade(pixels[i], 0.6f);
                }
            }
        });
    }

    // Picking

    /**
     * The point of the model (x and y in samples, height in exaggerated samples) shown at a location in the component,
     * or {@code null}.
     */
    private double[] worldPointAt(int x, int y) {
        final Camera camera = this.camera;
        if ((image == null) || (camera == null) || (getWidth() <= 0) || (getHeight() <= 0)) {
            return null;
        }
        final int px = x * image.getWidth() / getWidth(), py = y * image.getHeight() / getHeight();
        if ((px < 0) || (py < 0) || (px >= image.getWidth()) || (py >= image.getHeight())) {
            return null;
        }
        final float d = depthBuffer[py * image.getWidth() + px];
        if (d <= 0) {
            return null;
        }
        final double xc, yc, zc;
        if (camera.orthographic) {
            zc = ORTHO_DEPTH - d;
            xc = (px + 0.5 - camera.centreX) / camera.scale;
            yc = (camera.centreY - (py + 0.5)) / camera.scale;
        } else {
            zc = 1 / d;
            xc = (px + 0.5 - camera.centreX) * zc / camera.focal;
            yc = (camera.centreY - (py + 0.5)) * zc / camera.focal;
        }
        return new double[] {
                camera.x + camera.rx * xc + camera.ux * yc + camera.fx * zc,
                camera.y + camera.ry * xc + camera.uy * yc + camera.fy * zc,
                camera.h + camera.uh * yc + camera.fh * zc};
    }

    /**
     * The sample shown at a location in the component, or -1.
     */
    int sampleAt(int x, int y) {
        final double[] point = worldPointAt(x, y);
        if ((point == null) || (model == null)) {
            return -1;
        }
        final int sx = Math.max(0, Math.min(model.width - 1, (int) Math.floor(point[0])));
        final int sy = Math.max(0, Math.min(model.height - 1, (int) Math.floor(point[1])));
        final int sample = sy * model.width + sx;
        return model.present[sample] ? sample : -1;
    }

    private void updateHover(int x, int y) {
        if (hoverListener != null) {
            hoverListener.accept(sampleAt(x, y));
        }
    }

    // Utilities

    static int ramp(int[] stops, float t) {
        t = Math.max(0, Math.min(1, t));
        final float position = t * (stops.length - 1);
        final int index = Math.min(stops.length - 2, (int) position);
        return blend(stops[index], stops[index + 1], position - index);
    }

    static int blend(int colour1, int colour2, float t) {
        final int r = (int) (((colour1 >> 16) & 0xff) * (1 - t) + ((colour2 >> 16) & 0xff) * t);
        final int g = (int) (((colour1 >> 8) & 0xff) * (1 - t) + ((colour2 >> 8) & 0xff) * t);
        final int b = (int) ((colour1 & 0xff) * (1 - t) + (colour2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int colour, float factor) {
        final int r = Math.min(255, (int) (((colour >> 16) & 0xff) * factor));
        final int g = Math.min(255, (int) (((colour >> 8) & 0xff) * factor));
        final int b = Math.min(255, (int) ((colour & 0xff) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private TerrainModel model;
    private Mesh mesh, coarseMesh;
    private volatile Camera camera;
    private float[] depthBuffer, heightBuffer;
    private BufferedImage image;
    private Mode mode = Mode.TERRAIN;
    private boolean showWater = true, showContours, interacting, orthographic, blocks;
    private float exaggeration = 1.0f;
    private double targetX, targetY, targetH, yaw, pitch, distance = 200, maxDistance = 1000;
    private IntConsumer hoverListener;

    static final int[] HEIGHT_RAMP = {0x2e6b3f, 0x5f9e4a, 0xa9c56b, 0xe6d79a, 0xc39a68, 0x8b6a52, 0xf5f5f4};
    static final int[] SLOPE_RAMP = {0x3b82f6, 0x22c55e, 0xeab308, 0xf97316, 0xdc2626};
    static final int CONTOUR_INTERVAL = 8;
    private static final int EARTH = 0x6b5442;
    private static final int CELL_BUDGET = 1_100_000, COARSE_CELL_BUDGET = 150_000, BLOCK_BUDGET = 600_000, COARSE_BLOCK_BUDGET = 100_000;
    private static final double MIN_PITCH = Math.toRadians(-45), MAX_PITCH = Math.toRadians(89.5), MIN_DISTANCE = 4;
    private static final float NEAR = 0.5f, ORTHO_DEPTH = 1.0e6f;
    private static final long serialVersionUID = 1L;

    enum Mode {
        TERRAIN("Terrain", "The colours of the terrain types, as on the map"),
        HEIGHT("Height", "A colour gradient from the lowest to the highest point, to read the altitude at a glance"),
        SLOPE("Slope", "From blue (flat) to red (steep): find cliffs and buildable flat land"),
        BIOME("Biome", "The biome of each column, painted or automatic"),
        RELIEF("Relief", "Plain shaded relief, to judge the shape of the land");

        Mode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        final String displayName, description;
    }

    /**
     * The camera for one frame: its position and orientation in model coordinates (x and y in samples, height in
     * exaggerated samples above the lowest point) and its projection onto the image.
     */
    private final class Camera {
        Camera(int w, int h) {
            orthographic = Terrain3DView.this.orthographic;
            centreX = w / 2.0;
            centreY = h / 2.0;
            focal = focal(w);
            scale = focal / distance;
            final double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw), sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
            // Right, forward and up vectors
            rx = cosYaw;
            ry = sinYaw;
            fx = cosPitch * sinYaw;
            fy = -cosPitch * cosYaw;
            fh = -sinPitch;
            ux = sinPitch * sinYaw;
            uy = -sinPitch * cosYaw;
            uh = cosPitch;
            final double th = targetH * exaggeration;
            x = targetX - fx * distance;
            y = targetY - fy * distance;
            // Below the horizontal the camera may end up under the land: keep it above the surface instead
            double cameraH = th - fh * distance;
            final int csx = (int) Math.floor(x), csy = (int) Math.floor(y);
            if ((csx >= 0) && (csy >= 0) && (csx < model.width) && (csy < model.height) && model.present[csy * model.width + csx]) {
                cameraH = Math.max(cameraH, (surfaceHeight(csy * model.width + csx) - model.lowestHeight + 2) * exaggeration / model.step);
            }
            this.h = cameraH;
        }

        final boolean orthographic;
        final double centreX, centreY, focal, scale, rx, ry, fx, fy, fh, ux, uy, uh, x, y, h;
    }

    /**
     * A triangle mesh. Vertex heights are in blocks above the lowest point of the model, or NaN for vertices on the
     * base of the model.
     */
    private static final class Mesh {
        Mesh(int vertexCount, int triangleCount) {
            this.vertexCount = vertexCount;
            this.triangleCount = triangleCount;
            x = new float[vertexCount];
            y = new float[vertexCount];
            h = new float[vertexCount];
            colour = new int[vertexCount];
            triangles = new int[triangleCount * 3];
        }

        void setTriangle(int t, int a, int b, int c) {
            triangles[t * 3] = a;
            triangles[t * 3 + 1] = b;
            triangles[t * 3 + 2] = c;
        }

        final int vertexCount, triangleCount;
        final float[] x, y, h;
        final int[] colour, triangles;
        int wallTriangleStart;
        float floor;
    }

    /**
     * Rasterises triangles into one horizontal band of the image, with a depth test. Colours are interpolated linearly
     * on the screen; heights (for the contour lines) with perspective correction.
     */
    private final class Rasteriser {
        Rasteriser(int[] pixels, int width, int y0, int y1) {
            this.pixels = pixels;
            this.width = width;
            this.y0 = y0;
            this.y1 = y1;
        }

        void triangle(float x0, float y0, float d0, float q0, int c0, float h0,
                      float x1, float y1, float d1, float q1, int c1, float h1,
                      float x2, float y2, float d2, float q2, int c2, float h2) {
            final float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            if (Math.abs(area) < 1e-6f) {
                return;
            }
            final int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2)))), maxX = Math.min(width - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
            final int minY = Math.max(this.y0, (int) Math.floor(Math.min(y0, Math.min(y1, y2)))), maxY = Math.min(this.y1 - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
            if ((minX > maxX) || (minY > maxY)) {
                return;
            }
            final float inverseArea = 1 / area;
            // Edge functions (barycentric weights times the area) and their steps per pixel
            final float e0dx = (y1 - y2) * inverseArea, e1dx = (y2 - y0) * inverseArea;
            final float r0 = (c0 >> 16) & 0xff, g0 = (c0 >> 8) & 0xff, b0 = c0 & 0xff;
            final float r1 = (c1 >> 16) & 0xff, g1 = (c1 >> 8) & 0xff, b1 = c1 & 0xff;
            final float r2 = (c2 >> 16) & 0xff, g2 = (c2 >> 8) & 0xff, b2 = c2 & 0xff;
            final boolean heights = ! Float.isNaN(h0);
            final float hq0 = h0 * q0, hq1 = h1 * q1, hq2 = h2 * q2;
            final float epsilon = -1e-5f;
            for (int y = minY; y <= maxY; y++) {
                final float py = y + 0.5f, px0 = minX + 0.5f;
                // Barycentric weights at the first pixel of the row
                float l0 = ((x1 - px0) * (y2 - py) - (x2 - px0) * (y1 - py)) * inverseArea;
                float l1 = ((x2 - px0) * (y0 - py) - (x0 - px0) * (y2 - py)) * inverseArea;
                int i = y * width + minX;
                for (int x = minX; x <= maxX; x++, i++, l0 += e0dx, l1 += e1dx) {
                    final float l2 = 1 - l0 - l1;
                    if ((l0 < epsilon) || (l1 < epsilon) || (l2 < epsilon)) {
                        continue;
                    }
                    final float d = l0 * d0 + l1 * d1 + l2 * d2;
                    if (d <= depthBuffer[i]) {
                        continue;
                    }
                    depthBuffer[i] = d;
                    pixels[i] = (((int) (l0 * r0 + l1 * r1 + l2 * r2)) << 16) | (((int) (l0 * g0 + l1 * g1 + l2 * g2)) << 8) | ((int) (l0 * b0 + l1 * b1 + l2 * b2));
                    heightBuffer[i] = heights ? (l0 * hq0 + l1 * hq1 + l2 * hq2) / (l0 * q0 + l1 * q1 + l2 * q2) : Float.NaN;
                }
            }
        }

        /**
         * Draw a triangle which crosses the near plane of the camera, by clipping it against that plane.
         */
        void clippedTriangle(Mesh mesh, int t, boolean wall, float[] cx, float[] cy, float[] cz, int[] colour, Camera camera) {
            final float[] px = new float[4], py = new float[4], pz = new float[4], ph = new float[4];
            final int[] pc = new int[4];
            int count = 0;
            for (int k = 0; k < 3; k++) {
                final int a = mesh.triangles[t * 3 + k], b = mesh.triangles[t * 3 + (k + 1) % 3];
                final boolean inA = cz[a] > NEAR, inB = cz[b] > NEAR;
                final float ha = wall ? Float.NaN : mesh.h[a], hb = wall ? Float.NaN : mesh.h[b];
                if (inA) {
                    px[count] = cx[a];
                    py[count] = cy[a];
                    pz[count] = cz[a];
                    ph[count] = ha;
                    pc[count++] = colour[a];
                }
                if (inA != inB) {
                    final float f = (NEAR - cz[a]) / (cz[b] - cz[a]);
                    px[count] = cx[a] + (cx[b] - cx[a]) * f;
                    py[count] = cy[a] + (cy[b] - cy[a]) * f;
                    pz[count] = NEAR;
                    ph[count] = ha + (hb - ha) * f;
                    pc[count++] = blend(colour[a], colour[b], f);
                }
            }
            if (count < 3) {
                return;
            }
            final float[] sx = new float[count], sy = new float[count], d = new float[count];
            for (int k = 0; k < count; k++) {
                sx[k] = (float) (camera.centreX + camera.focal * px[k] / pz[k]);
                sy[k] = (float) (camera.centreY - camera.focal * py[k] / pz[k]);
                d[k] = 1 / pz[k];
            }
            for (int k = 1; k < count - 1; k++) {
                triangle(sx[0], sy[0], d[0], d[0], pc[0], ph[0], sx[k], sy[k], d[k], d[k], pc[k], ph[k], sx[k + 1], sy[k + 1], d[k + 1], d[k + 1], pc[k + 1], ph[k + 1]);
            }
        }

        private final int[] pixels;
        private final int width, y0, y1;
    }

    /**
     * A growable list of ints.
     */
    private static final class IntList {
        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        int[] values = new int[64];
        int size;
    }
}
