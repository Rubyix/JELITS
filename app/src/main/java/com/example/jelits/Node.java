package com.example.jelits;

import com.mapbox.mapboxsdk.geometry.LatLng;
import java.util.ArrayList;
import java.util.List;

public class Node {
    public String id;
    public LatLng position;
    public double g; // Cost from start to this node
    public double h; // Heuristic cost to goal
    public double f; // Total cost (g + h)
    public Node parent; // Parent node in path
    public List<Node> neighbors; // Neighbors

    public Node(String id, LatLng position) {
        this.id = id;
        this.position = position;
        this.g = 0;
        this.h = 0;
        this.f = 0;
        this.parent = null;
        this.neighbors = new ArrayList<>();
    }
}
