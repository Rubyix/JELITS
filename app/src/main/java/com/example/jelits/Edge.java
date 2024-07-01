package com.example.jelits;

import com.mapbox.mapboxsdk.geometry.LatLng;

public class Edge {
    private Node node1;
    private Node node2;
    private double weight;

    public Edge(Node node1, Node node2) {
        this.node1 = node1;
        this.node2 = node2;
        this.weight = calculateWeight(node1.position, node2.position);
    }

    public Node getNode1() {
        return node1;
    }

    public Node getNode2() {
        return node2;
    }

    public double getWeight() {
        return weight;
    }

    private double calculateWeight(LatLng p1, LatLng p2) {
        return p1.distanceTo(p2);
    }
}
