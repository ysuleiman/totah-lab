package totah.lab.euclid.spatial;


import java.util.*;

public class SimpleKDTree<T> {
    private Node<T> root;
    private final int dimensions;

    private static class Node<T> {
        double[] point;
        T value;
        Node<T> left, right;
        int axis;

        Node(double[] point, T value, int axis) {
            this.point = point.clone();
            this.value = value;
            this.axis = axis;
        }
    }

    public SimpleKDTree(int dimensions) {
        this.dimensions = dimensions;
    }

    public void build(List<double[]> points, List<T> values) {
        if (points.size() != values.size()) {
            throw new IllegalArgumentException("points and values must have the same size: "
                    + points.size() + " points vs " + values.size() + " values");
        }
        List<Item<T>> items = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            items.add(new Item<>(points.get(i), values.get(i)));
        }
        root = buildRecursive(items, 0);
    }

    private record Item<T>(double[] point, T value) {}

    private Node<T> buildRecursive(List<Item<T>> items, int depth) {
        if (items.isEmpty()) return null;

        int axis = depth % dimensions;
        items.sort(Comparator.comparingDouble(a -> a.point[axis]));

        int mid = items.size() / 2;
        Item<T> median = items.get(mid);

        Node<T> node = new Node<>(median.point, median.value, axis);
        node.left = buildRecursive(items.subList(0, mid), depth + 1);
        node.right = buildRecursive(items.subList(mid + 1, items.size()), depth + 1);

        return node;
    }

    public List<Result<T>> rangeSearch(double[] query, double radius) {
        List<Result<T>> results = new ArrayList<>();
        rangeSearch(root, query, radius, results);
        return results;
    }

    private void rangeSearch(Node<T> node, double[] query, double radius, List<Result<T>> results) {
        if (node == null) return;

        double distSq = distanceSq(node.point, query);
        if (distSq <= radius * radius) {
            results.add(new Result<>(node.value, Math.sqrt(distSq)));
        }

        int axis = node.axis;
        double diff = query[axis] - node.point[axis];

        Node<T> near = diff < 0 ? node.left : node.right;
        Node<T> far = diff < 0 ? node.right : node.left;

        rangeSearch(near, query, radius, results);

        // Check if we need to search the far side
        if (Math.abs(diff) <= radius) {
            rangeSearch(far, query, radius, results);
        }
    }

    private double distanceSq(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < dimensions; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    public record Result<T>(T value, double distance) {}
}
