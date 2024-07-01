package com.example.jelits;

import android.util.Log;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class Graph {
    private Map<LatLng, Node> nodes = new HashMap<>();
    private List<Edge> edges = new ArrayList<>();

    public void addEdge(LatLng p1, LatLng p2) {
        Node node1 = nodes.computeIfAbsent(p1, key -> new Node(key.toString(), p1));
        Node node2 = nodes.computeIfAbsent(p2, key -> new Node(key.toString(), p2));
        Edge edge = new Edge(node1, node2);
        edges.add(edge);
        node1.neighbors.add(node2);
        node2.neighbors.add(node1);

        // Debugging log
        Log.d("Graph", "Edge added between " + p1 + " and " + p2);
    }

    public Node getNode(LatLng point) {
        return nodes.get(point);
    }

    public List<Node> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public boolean areNodesConnected(Node startNode, Node goalNode) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.equals(goalNode)) {
                return true;
            }
            visited.add(current);
            for (Node neighbor : current.neighbors) {
                if (!visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return false;
    }

    public List<Edge> getEdges() {
        return edges;
    }
    public void printNodeConnections() {
        for (Node node : nodes.values()) {
            StringBuilder neighbors = new StringBuilder();
            for (Node neighbor : node.neighbors) {
                neighbors.append(neighbor.position).append(", ");
            }
            Log.d("Graph", "Node " + node.position + " has neighbors: " + neighbors);
        }
    }


}

