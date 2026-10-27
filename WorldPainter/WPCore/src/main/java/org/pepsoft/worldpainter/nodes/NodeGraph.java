package org.pepsoft.worldpainter.nodes;

import org.pepsoft.util.AttributeKey;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A graph of {@link Node nodes} connected by {@link Connection wires}, which together describe a height map, ending in
 * a single {@link OutputNode}. Graphs are acyclic: {@link #connect(Node, Node, int)} refuses connections which would
 * create a loop. Use {@link #compile(Rectangle)} to evaluate a graph.
 */
public class NodeGraph implements Serializable, Cloneable {
    /**
     * Create a graph containing only an output node.
     */
    public NodeGraph() {
        final OutputNode output = add(new OutputNode());
        output.setLocation(640, 160);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The seed from which the seeds of all generators are derived.
     */
    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * The nodes, in the order in which they were added (which is also the order in which they are drawn).
     */
    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public Node getNode(int id) {
        for (Node node: nodes) {
            if (node.getId() == id) {
                return node;
            }
        }
        return null;
    }

    public OutputNode getOutputNode() {
        for (Node node: nodes) {
            if (node instanceof OutputNode) {
                return (OutputNode) node;
            }
        }
        throw new IllegalStateException("Graph has no output node");
    }

    /**
     * Add a node to the graph, assigning it a new ID.
     *
     * @return The node.
     */
    public <N extends Node> N add(N node) {
        if (nodes.contains(node)) {
            throw new IllegalArgumentException("Node already in graph");
        }
        if ((node instanceof OutputNode) && nodes.stream().anyMatch(n -> n instanceof OutputNode)) {
            throw new IllegalArgumentException("Graph already has an output node");
        }
        node.setId(nextId++);
        nodes.add(node);
        return node;
    }

    /**
     * Remove a node and all its connections from the graph. The output node cannot be removed.
     */
    public void remove(Node node) {
        if (node instanceof OutputNode) {
            throw new IllegalArgumentException("The output node cannot be removed");
        }
        if (nodes.remove(node)) {
            connections.removeIf(connection -> (connection.getFromNodeId() == node.getId()) || (connection.getToNodeId() == node.getId()));
        }
    }

    /**
     * Move a node to the end of the list, so that it is drawn on top.
     */
    public void bringToFront(Node node) {
        if (nodes.remove(node)) {
            nodes.add(node);
        }
    }

    /**
     * Indicates whether the output of {@code from} may be connected to input {@code input} of {@code to}: both must be
     * in the graph, {@code from} must have an output, the input must exist and the connection may not create a loop.
     */
    public boolean canConnect(Node from, Node to, int input) {
        return nodes.contains(from) && nodes.contains(to) && (from != to) && from.hasOutput()
                && (input >= 0) && (input < to.getInputNames().size()) && (! dependsOn(from, to));
    }

    /**
     * Connect the output of {@code from} to input {@code input} of {@code to}, replacing any existing connection to
     * that input.
     *
     * @throws IllegalArgumentException If the connection is not {@link #canConnect(Node, Node, int) allowed}.
     */
    public void connect(Node from, Node to, int input) {
        if (! canConnect(from, to, input)) {
            throw new IllegalArgumentException("Cannot connect " + from + " to input " + input + " of " + to);
        }
        disconnect(to, input);
        connections.add(new Connection(from.getId(), to.getId(), input));
    }

    /**
     * Remove the connection to an input, if any.
     */
    public void disconnect(Node to, int input) {
        connections.removeIf(connection -> (connection.getToNodeId() == to.getId()) && (connection.getToInput() == input));
    }

    /**
     * Get the node connected to an input, or {@code null} if the input is not connected.
     */
    public Node getInput(Node to, int input) {
        for (Connection connection: connections) {
            if ((connection.getToNodeId() == to.getId()) && (connection.getToInput() == input)) {
                return getNode(connection.getFromNodeId());
            }
        }
        return null;
    }

    /**
     * Indicates whether the value of {@code node} depends, directly or indirectly, on the output of {@code other}, or
     * is {@code other}.
     */
    public boolean dependsOn(Node node, Node other) {
        if (node == other) {
            return true;
        }
        final int inputCount = node.getInputNames().size();
        for (int i = 0; i < inputCount; i++) {
            final Node input = getInput(node, i);
            if ((input != null) && dependsOn(input, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compile the graph into a thread safe evaluator.
     *
     * @param mapBounds The extent of the map in blocks, used by nodes which can fit themselves to the map, or
     *                  {@code null} if not known.
     */
    public CompiledGraph compile(Rectangle mapBounds) {
        return compile(mapBounds, null);
    }

    /**
     * Compile the graph into a thread safe evaluator.
     *
     * @param mapBounds The extent of the map in blocks, used by nodes which can fit themselves to the map, or
     *                  {@code null} if not known.
     * @param mapSource The existing map, for nodes which build on it, or {@code null} if not available.
     */
    public CompiledGraph compile(Rectangle mapBounds, MapSource mapSource) {
        return new CompiledGraph(this, mapBounds, mapSource);
    }

    /**
     * Indicates whether the graph contains a node of a particular type.
     */
    public boolean contains(Class<? extends Node> type) {
        return nodes.stream().anyMatch(type::isInstance);
    }

    /**
     * Create a deep copy of the graph. The nodes keep their IDs.
     */
    @Override
    public NodeGraph clone() {
        try {
            final NodeGraph clone = (NodeGraph) super.clone();
            clone.nodes = new ArrayList<>(nodes.size());
            nodes.forEach(node -> clone.nodes.add(node.clone()));
            clone.connections = new ArrayList<>(connections);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public String toString() {
        return (name != null) ? name : "Node graph";
    }

    private String name;
    private long seed;
    private List<Node> nodes = new ArrayList<>();
    private List<Connection> connections = new ArrayList<>();
    private int nextId = 1;

    /**
     * The world attribute under which the node graph of a world is stored.
     */
    public static final AttributeKey<NodeGraph> ATTRIBUTE_NODE_GRAPH = new AttributeKey<>("org.pepsoft.worldpainter.nodeGraph");

    private static final long serialVersionUID = 1L;
}
