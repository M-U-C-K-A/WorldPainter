package org.pepsoft.worldpainter.docking;

import java.awt.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * How the space of a workspace is divided into areas, as in Blender: splits put their children side by side or one
 * above the other, and the leaves are the areas, each of which shows one page.
 *
 * <p>The size of each node along the axis of its parent split is either a number of pixels, a {@link #FRACTION} of the
 * split, {@link #AUTO} for the preferred size of what it shows, or {@link #FILL} for the space the other children
 * leave. When there is more or less
 * room, as when the window is resized, the child which fills (or, if none does, the largest one) takes up the
 * difference, so that the panels around the map keep their size.
 *
 * <p>The tree is kept normalised: a split has at least two children, and none of them is a split in the same direction.
 * Its text form, used to store layouts and to describe the built-in ones, is:
 * <pre>
 * node  = leaf | split
 * leaf  = "L(" group "," page ["," "s"] ")"  (both URL encoded, and empty for an empty area; "s" when the area
 *                                             shows all the pages of its kind at once)
 * split = ("H" | "V") "[" child (";" child)* "]"   (H: side by side; V: one above the other)
 * child = size "=" node
 * size  = "a" (preferred size) | "*" (fill) | pixels | percentage "%"
 * </pre>
 */
final class AreaTree {
    AreaTree(Node root, int divider) {
        this.root = root;
        this.divider = divider;
        if (root != null) {
            root.parent = null;
        }
    }

    Node getRoot() {
        return root;
    }

    int getDivider() {
        return divider;
    }

    /**
     * All the areas, from left to right and top to bottom.
     */
    List<Leaf> leaves() {
        final List<Leaf> leaves = new ArrayList<>();
        if (root != null) {
            visit(root, node -> {
                if (node instanceof Leaf) {
                    leaves.add((Leaf) node);
                }
            });
        }
        return leaves;
    }

    boolean contains(Node node) {
        while ((node != null) && (node.parent != null)) {
            node = node.parent;
        }
        return (node != null) && (node == root);
    }

    /**
     * Put a node next to an area, on one side of it, taking the space from that area.
     *
     * @param node The node to insert. It must not be in the tree.
     * @param target The area next to which to put it.
     * @param side The side of the target on which to put it.
     * @param size The size of the new node across the split, in pixels.
     */
    void insert(Node node, Leaf target, Side side, int size) {
        final boolean horizontal = side.isHorizontal(), before = side.isBefore();
        final Split parent = target.parent;
        node.size = size;
        if ((parent != null) && (parent.horizontal == horizontal)) {
            // Take the space from the target, unless it fills, in which case it shrinks by itself
            if (target.size >= 0) {
                target.size = Math.max(0, target.size - size - divider);
            } else if (target.size != FILL) {
                target.size = Math.max(0, target.actualSize - size - divider);
            }
            final int index = parent.children.indexOf(target) + (before ? 0 : 1);
            parent.children.add(index, node);
            node.parent = parent;
        } else {
            final Split split = new Split(horizontal);
            replace(target, split);
            target.size = FILL;
            split.add(before ? node : target);
            split.add(before ? target : node);
        }
        if (node instanceof Split) {
            flattenIfAligned((Split) node);
        }
    }

    /**
     * Take a node out of the tree, giving its space to one of its neighbours.
     *
     * @param node The node to take out.
     * @param heir The neighbour which gets the space, or {@code null} for the one before it (or else after it).
     */
    void remove(Node node, Node heir) {
        if (node == root) {
            root = null;
            return;
        }
        final Split parent = node.parent;
        final int index = parent.children.indexOf(node);
        if ((heir == null) || (heir.parent != parent)) {
            heir = parent.children.get((index > 0) ? index - 1 : index + 1);
        }
        parent.children.remove(index);
        node.parent = null;
        if (node.size == FILL) {
            heir.size = FILL;
        } else if (heir.size >= 0) {
            heir.size += node.actualSize + divider;
        } else if ((heir.size == AUTO) || (heir.size == FRACTION)) {
            heir.size = heir.actualSize + node.actualSize + divider;
        }
        normalise(parent);
    }

    /**
     * Whether two areas can be joined into one: they must be next to each other in the same split, so that together
     * they make a rectangle.
     */
    boolean canJoin(Leaf a, Leaf b) {
        return (a != b) && (a.parent != null) && (a.parent == b.parent) && (Math.abs(a.parent.children.indexOf(a) - a.parent.children.indexOf(b)) == 1);
    }

    /**
     * Join two areas: the first one takes the space of the second one, which is taken out.
     */
    void join(Leaf keep, Leaf absorbed) {
        if (! canJoin(keep, absorbed)) {
            throw new IllegalArgumentException("Areas cannot be joined");
        }
        remove(absorbed, keep);
    }

    /**
     * On which side of an area another one is, if they share a whole edge, or {@code null}.
     */
    static Side sideOf(Leaf leaf, Leaf other) {
        if ((leaf.parent == null) || (leaf.parent != other.parent)) {
            return null;
        }
        final int delta = leaf.parent.children.indexOf(other) - leaf.parent.children.indexOf(leaf);
        if (delta == 1) {
            return leaf.parent.horizontal ? Side.EAST : Side.SOUTH;
        } else if (delta == -1) {
            return leaf.parent.horizontal ? Side.WEST : Side.NORTH;
        } else {
            return null;
        }
    }

    /**
     * Resize two neighbouring children of a split by moving the divider between them.
     *
     * @return The distance the divider actually moved, which may be less than asked because of the minimum sizes.
     */
    int moveDivider(Node before, Node after, int delta, Sizes sizes) {
        final Split split = before.parent;
        if ((split == null) || (after.parent != split)) {
            return 0;
        }
        final int minBefore = minimumSize(before, split.horizontal, sizes), minAfter = minimumSize(after, split.horizontal, sizes);
        delta = Math.max(delta, minBefore - before.actualSize);
        delta = Math.min(delta, after.actualSize - minAfter);
        if (delta == 0) {
            return 0;
        }
        final int total = Math.max(1, (split.horizontal ? split.bounds.width : split.bounds.height) - split.dividers.size() * divider);
        if (before.size == FILL) {
            resize(after, after.actualSize - delta, total);
        } else if (after.size == FILL) {
            resize(before, before.actualSize + delta, total);
        } else {
            resize(before, before.actualSize + delta, total);
            resize(after, after.actualSize - delta, total);
        }
        before.actualSize += delta;
        after.actualSize -= delta;
        return delta;
    }

    /**
     * Give a node a new size, keeping it a fraction of its split if it is one.
     */
    private static void resize(Node node, int size, int total) {
        if (node.size == FRACTION) {
            node.fraction = (float) size / total;
        } else {
            node.size = size;
        }
    }

    /**
     * Work out where everything goes within some bounds, setting the {@link Node#bounds} and {@link Node#actualSize}
     * of every node, and the dividers of every split. Collapsed nodes get no room.
     */
    void layout(Rectangle bounds, Sizes sizes) {
        if (root != null) {
            layout(root, bounds, sizes);
        }
    }

    // Text form

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (root != null) {
            write(root, sb);
        }
        return sb.toString();
    }

    static String toString(Node node) {
        final StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    /**
     * Read a tree from its text form.
     *
     * @param scale The factor by which to multiply the sizes in pixels.
     * @throws IllegalArgumentException If the text is not a valid tree.
     */
    static Node parse(String text, float scale) {
        final Parser parser = new Parser(text, scale);
        final Node node = parser.node();
        if (parser.pos != text.length()) {
            throw new IllegalArgumentException("Unexpected text at " + parser.pos + ": " + text);
        }
        return node;
    }

    // Implementation

    private void replace(Node old, Node replacement) {
        final Split parent = old.parent;
        replacement.size = old.size;
        replacement.fraction = old.fraction;
        replacement.parent = parent;
        if (parent == null) {
            root = replacement;
        } else {
            parent.children.set(parent.children.indexOf(old), replacement);
        }
        old.parent = null;
    }

    private void normalise(Split split) {
        if (split.children.size() == 1) {
            final Node only = split.children.get(0);
            replace(split, only);
            if (only instanceof Split) {
                flattenIfAligned((Split) only);
            }
        }
    }

    /**
     * If a split is in the same direction as the one it is in, move its children into that one.
     */
    private void flattenIfAligned(Split inner) {
        final Split outer = inner.parent;
        if ((outer == null) || (outer.horizontal != inner.horizontal)) {
            return;
        }
        int index = outer.children.indexOf(inner);
        outer.children.remove(index);
        final boolean outerFills = outer.children.stream().anyMatch(child -> child.size == FILL);
        final boolean innerFills = inner.children.stream().anyMatch(child -> child.size == FILL);
        if ((inner.size == FILL) && (! innerFills)) {
            // One of its children must take over filling the space
            Node largest = inner.children.get(inner.children.size() - 1);
            for (Node child: inner.children) {
                if (child.actualSize > largest.actualSize) {
                    largest = child;
                }
            }
            largest.size = FILL;
        }
        for (Node child: inner.children) {
            if (((child.size == FILL) && (outerFills || (inner.size != FILL))) || (child.size == FRACTION)) {
                child.size = Math.max(child.actualSize, 1);
            }
            child.parent = outer;
            outer.children.add(index++, child);
        }
        inner.children.clear();
        inner.parent = null;
    }

    private void layout(Node node, Rectangle bounds, Sizes sizes) {
        node.bounds.setBounds(bounds);
        if (! (node instanceof Split)) {
            return;
        }
        final Split split = (Split) node;
        split.dividers.clear();
        final List<Node> visible = new ArrayList<>(split.children.size());
        for (Node child: split.children) {
            if (sizes.isCollapsed(child)) {
                child.actualSize = 0;
                visit(child, descendant -> descendant.bounds.setBounds(0, 0, 0, 0));
            } else {
                visible.add(child);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        final int count = visible.size();
        final int total = Math.max(0, (split.horizontal ? bounds.width : bounds.height) - (count - 1) * divider);
        final int[] childSizes = distribute(split.horizontal, visible, total, sizes);
        int position = split.horizontal ? bounds.x : bounds.y;
        for (int i = 0; i < count; i++) {
            final Node child = visible.get(i);
            child.actualSize = childSizes[i];
            final Rectangle childBounds = split.horizontal
                    ? new Rectangle(position, bounds.y, childSizes[i], bounds.height)
                    : new Rectangle(bounds.x, position, bounds.width, childSizes[i]);
            layout(child, childBounds, sizes);
            position += childSizes[i];
            if (i < count - 1) {
                final Rectangle dividerBounds = split.horizontal
                        ? new Rectangle(position, bounds.y, divider, bounds.height)
                        : new Rectangle(bounds.x, position, bounds.width, divider);
                split.dividers.add(new Divider(split, child, visible.get(i + 1), dividerBounds));
                position += divider;
            }
        }
    }

    /**
     * Share out the room in a split. The child which fills (or else the largest one) gets what the others leave; if
     * that is less than its minimum size the others give up room, in proportion to how much they have above their own.
     */
    private int[] distribute(boolean horizontal, List<Node> children, int total, Sizes sizes) {
        final int count = children.size();
        final int[] result = new int[count], minimum = new int[count];
        int fill = -1;
        for (int i = 0; i < count; i++) {
            final Node child = children.get(i);
            minimum[i] = minimumSize(child, horizontal, sizes);
            if ((child.size == FILL) && (fill == -1)) {
                fill = i;
            } else if ((child.size == AUTO) || (child.size == FILL)) {
                result[i] = Math.max(minimum[i], preferredSize(child, horizontal, sizes));
            } else if (child.size == FRACTION) {
                result[i] = Math.max(minimum[i], Math.round(child.fraction * total));
            } else {
                result[i] = Math.max(minimum[i], child.size);
            }
        }
        if (fill == -1) {
            // Nobody fills; the largest takes up the difference
            fill = count - 1;
            for (int i = count - 2; i >= 0; i--) {
                if (result[i] > result[fill]) {
                    fill = i;
                }
            }
        }
        int others = 0;
        for (int i = 0; i < count; i++) {
            if (i != fill) {
                others += result[i];
            }
        }
        int fillSize = total - others;
        if (fillSize < minimum[fill]) {
            final int deficit = minimum[fill] - fillSize;
            int slack = 0;
            for (int i = 0; i < count; i++) {
                if (i != fill) {
                    slack += result[i] - minimum[i];
                }
            }
            if (slack > 0) {
                final int toGive = Math.min(deficit, slack);
                int given = 0;
                for (int i = 0; i < count; i++) {
                    if (i != fill) {
                        final int give = (int) ((long) (result[i] - minimum[i]) * toGive / slack);
                        result[i] -= give;
                        given += give;
                    }
                }
                // What rounding left over, a pixel at a time
                for (int i = 0; (i < count) && (given < toGive); i++) {
                    if ((i != fill) && (result[i] > minimum[i])) {
                        result[i]--;
                        given++;
                    }
                }
                others = 0;
                for (int i = 0; i < count; i++) {
                    if (i != fill) {
                        others += result[i];
                    }
                }
            }
            fillSize = Math.max(0, total - others);
        }
        result[fill] = fillSize;
        return result;
    }

    int preferredSize(Node node, boolean horizontal, Sizes sizes) {
        if (node instanceof Leaf) {
            return sizes.getPreferredSize((Leaf) node, horizontal);
        }
        final Split split = (Split) node;
        int result = 0, count = 0;
        for (Node child: split.children) {
            if (sizes.isCollapsed(child)) {
                continue;
            }
            final int size = preferredSize(child, horizontal, sizes);
            if (split.horizontal == horizontal) {
                result += size;
                count++;
            } else {
                result = Math.max(result, size);
            }
        }
        return result + ((count > 1) ? (count - 1) * divider : 0);
    }

    int minimumSize(Node node, boolean horizontal, Sizes sizes) {
        if (node instanceof Leaf) {
            return sizes.getMinimumSize((Leaf) node, horizontal);
        }
        final Split split = (Split) node;
        int result = 0, count = 0;
        for (Node child: split.children) {
            if (sizes.isCollapsed(child)) {
                continue;
            }
            final int size = minimumSize(child, horizontal, sizes);
            if (split.horizontal == horizontal) {
                result += size;
                count++;
            } else {
                result = Math.max(result, size);
            }
        }
        return result + ((count > 1) ? (count - 1) * divider : 0);
    }

    static void visit(Node node, Consumer<Node> visitor) {
        visitor.accept(node);
        if (node instanceof Split) {
            for (Node child: new ArrayList<>(((Split) node).children)) {
                visit(child, visitor);
            }
        }
    }

    private static void write(Node node, StringBuilder sb) {
        if (node instanceof Leaf) {
            final Leaf leaf = (Leaf) node;
            sb.append("L(").append(encode(leaf.group)).append(',').append(encode(leaf.page));
            if (leaf.stacked) {
                sb.append(",s");
            }
            sb.append(')');
        } else {
            final Split split = (Split) node;
            sb.append(split.horizontal ? 'H' : 'V').append('[');
            boolean first = true;
            for (Node child: split.children) {
                if (! first) {
                    sb.append(';');
                }
                first = false;
                sb.append(sizeText(child)).append('=');
                write(child, sb);
            }
            sb.append(']');
        }
    }

    private static String sizeText(Node node) {
        switch (node.size) {
            case AUTO:
                return "a";
            case FILL:
                return "*";
            case FRACTION:
                final float percentage = Math.round(node.fraction * 1000) / 10f;
                return ((percentage == (int) percentage) ? Integer.toString((int) percentage) : Float.toString(percentage)) + "%";
            default:
                return Integer.toString(node.size);
        }
    }

    private static String encode(String str) {
        return (str != null) ? URLEncoder.encode(str, UTF_8) : "";
    }

    private static String decode(String str) {
        return str.isEmpty() ? null : URLDecoder.decode(str, UTF_8);
    }

    private Node root;
    private final int divider;

    static final int AUTO = -1, FILL = -2, FRACTION = -3;

    /**
     * What the tree needs to know about what the areas show to lay them out.
     */
    interface Sizes {
        int getPreferredSize(Leaf leaf, boolean horizontal);
        int getMinimumSize(Leaf leaf, boolean horizontal);
        boolean isCollapsed(Node node);
    }

    abstract static class Node {
        Split parent;
        /**
         * The size along the axis of the parent split: a number of pixels, {@link #AUTO}, {@link #FILL} or
         * {@link #FRACTION}.
         */
        int size = FILL;
        /**
         * The share of the split, if the size is a {@link #FRACTION}.
         */
        float fraction;
        /**
         * The size along the axis of the parent split at the last layout.
         */
        int actualSize;
        /**
         * The bounds at the last layout.
         */
        final Rectangle bounds = new Rectangle();
    }

    /**
     * An area, which shows a page of one kind of editor. Both are {@code null} for an empty area.
     */
    static final class Leaf extends Node {
        Leaf(String group, String page) {
            this.group = group;
            this.page = page;
        }

        @Override
        public String toString() {
            return "Leaf[" + group + "/" + page + "]";
        }

        String group, page;
        /**
         * Whether the area shows all the pages of its kind at once, one below the other, instead of one at a time with
         * tabs for the others.
         */
        boolean stacked;
    }

    static final class Split extends Node {
        Split(boolean horizontal) {
            this.horizontal = horizontal;
        }

        void add(Node child) {
            child.parent = this;
            children.add(child);
        }

        /**
         * Whether the children are side by side (or else one above the other).
         */
        final boolean horizontal;
        final List<Node> children = new ArrayList<>();
        /**
         * The dividers between the children which are not collapsed, at the last layout.
         */
        final List<Divider> dividers = new ArrayList<>();
    }

    /**
     * The gap between two neighbouring children of a split, which can be dragged to resize them.
     */
    static final class Divider {
        Divider(Split split, Node before, Node after, Rectangle bounds) {
            this.split = split;
            this.before = before;
            this.after = after;
            this.bounds = bounds;
        }

        final Split split;
        final Node before, after;
        final Rectangle bounds;
    }

    private static final class Parser {
        Parser(String text, float scale) {
            this.text = text;
            this.scale = scale;
        }

        Node node() {
            final char c = next();
            switch (c) {
                case 'L':
                    expect('(');
                    final String group = until(',');
                    expect(',');
                    final String page = untilEither(',', ')');
                    final Leaf leaf = new Leaf(decode(group), decode(page));
                    if (text.charAt(pos) == ',') {
                        expect(',');
                        leaf.stacked = until(')').equals("s");
                    }
                    expect(')');
                    return leaf;
                case 'H':
                case 'V':
                    final Split split = new Split(c == 'H');
                    expect('[');
                    do {
                        final String size = until('=');
                        expect('=');
                        final Node child = node();
                        if (size.equals("a")) {
                            child.size = AUTO;
                        } else if (size.equals("*")) {
                            child.size = FILL;
                        } else if (size.endsWith("%")) {
                            child.size = FRACTION;
                            child.fraction = Math.max(0.01f, Math.min(0.99f, Float.parseFloat(size.substring(0, size.length() - 1)) / 100));
                        } else {
                            child.size = Math.round(Integer.parseInt(size) * scale);
                        }
                        split.add(child);
                    } while (accept(';'));
                    expect(']');
                    if (split.children.size() < 2) {
                        throw new IllegalArgumentException("Split with fewer than two children");
                    }
                    return split;
                default:
                    throw new IllegalArgumentException("Unexpected '" + c + "' at " + (pos - 1) + ": " + text);
            }
        }

        private char next() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unexpected end: " + text);
            }
            return text.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + (pos - 1) + ": " + text);
            }
        }

        private boolean accept(char c) {
            if ((pos < text.length()) && (text.charAt(pos) == c)) {
                pos++;
                return true;
            }
            return false;
        }

        private String untilEither(char c1, char c2) {
            int end = text.indexOf(c1, pos);
            final int end2 = text.indexOf(c2, pos);
            if ((end == -1) || ((end2 != -1) && (end2 < end))) {
                end = end2;
            }
            if (end == -1) {
                throw new IllegalArgumentException("Expected '" + c1 + "' or '" + c2 + "' after " + pos + ": " + text);
            }
            final String result = text.substring(pos, end);
            pos = end;
            return result;
        }

        private String until(char c) {
            final int end = text.indexOf(c, pos);
            if (end == -1) {
                throw new IllegalArgumentException("Expected '" + c + "' after " + pos + ": " + text);
            }
            final String result = text.substring(pos, end);
            pos = end;
            return result;
        }

        private final String text;
        private final float scale;
        private int pos;
    }
}
