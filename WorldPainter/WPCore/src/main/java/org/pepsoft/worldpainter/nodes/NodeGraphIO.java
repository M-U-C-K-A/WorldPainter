package org.pepsoft.worldpainter.nodes;

import java.io.*;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Saves node graphs to files and loads them, so that they can be reused in other worlds and shared. Loading only
 * accepts the classes a graph consists of, so that a file from someone else cannot instantiate anything else.
 */
public final class NodeGraphIO {
    private NodeGraphIO() {
        // Prevent instantiation
    }

    public static void save(NodeGraph graph, File file) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            out.writeObject(graph);
        }
    }

    public static NodeGraph load(File file) throws IOException {
        try (ObjectInputStream in = new RestrictedObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            final Object object = in.readObject();
            if (! (object instanceof NodeGraph)) {
                throw new IOException(file.getName() + " does not contain a node graph");
            }
            return (NodeGraph) object;
        } catch (ClassNotFoundException e) {
            throw new IOException(file.getName() + " was created by an incompatible version of WorldPainter", e);
        }
    }

    /**
     * The file extension for node graphs, without the dot.
     */
    public static final String EXTENSION = "wpgraph";

    private static final class RestrictedObjectInputStream extends ObjectInputStream {
        RestrictedObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass description) throws IOException, ClassNotFoundException {
            final String name = description.getName();
            if (name.startsWith(NodeGraph.class.getPackageName() + ".") || ALLOWED_CLASSES.contains(name)) {
                return super.resolveClass(description);
            }
            throw new InvalidClassException(name, "Not allowed in a node graph");
        }
    }

    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "java.lang.String", "java.lang.Number", "java.lang.Double", "java.lang.Float", "java.lang.Integer",
            "java.lang.Long", "java.lang.Boolean", "java.util.ArrayList", "java.util.HashMap",
            "[F", "[C", "[B", "[I", "[D", "[J");
}
