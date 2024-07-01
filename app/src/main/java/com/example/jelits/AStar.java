package com.example.jelits;

import android.util.Log;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class AStar {
    private Graph graph;

    public AStar(Graph graph) {
        this.graph = graph;
    }

    public List<LatLng> findShortestPath(LatLng start, LatLng goal) {
        Node startNode = graph.getNode(start);
        Node goalNode = graph.getNode(goal);

        if (startNode == null || goalNode == null) {
            Log.d("AStar", "Start or goal node not in the graph");
            return null; // Start or goal node not in the graph
        }

        // Reset all nodes
        for (Node node : graph.getAllNodes()) {
            node.g = Double.MAX_VALUE;
            node.h = 0;
            node.f = 0;
            node.parent = null;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Set<Node> closedSet = new HashSet<>();

        startNode.g = 0;
        startNode.h = heuristic(startNode.position, goalNode.position);
        startNode.f = startNode.g + startNode.h;
        openSet.add(startNode);

        Log.d("AStar", "Starting pathfinding from " + start + " to " + goal);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            Log.d("AStar", "Current node: " + current.position);

            if (current.equals(goalNode)) {
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (Node neighbor : current.neighbors) {
                if (closedSet.contains(neighbor)) continue;

                double tentativeG = current.g + current.position.distanceTo(neighbor.position);
                Log.d("AStar", "Checking neighbor: " + neighbor.position + " with tentative G: " + tentativeG);

                if (tentativeG < neighbor.g) {
                    neighbor.parent = current;
                    neighbor.g = tentativeG;
                    neighbor.h = heuristic(neighbor.position, goalNode.position);
                    neighbor.f = neighbor.g + neighbor.h;
                    Log.d("AStar", "Updated neighbor: " + neighbor.position + " with G: " + neighbor.g + " H: " + neighbor.h + " F: " + neighbor.f);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                        Log.d("AStar", "Added neighbor to open set: " + neighbor.position);
                    }
                }
            }
        }

        Log.d("AStar", "No path found from " + start + " to " + goal);
        return null; // No path found
    }



    private double heuristic(LatLng start, LatLng goal) {
        return start.distanceTo(goal);
    }

    private List<LatLng> reconstructPath(Node current) {
        List<LatLng> path = new ArrayList<>();
        while (current != null) {
            path.add(current.position);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
}
