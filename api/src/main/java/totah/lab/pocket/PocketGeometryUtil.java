package totah.lab.pocket;

import java.util.List;
public final class PocketGeometryUtil {


        private PocketGeometryUtil() {
        }

        /**
         * Calculate centroid of alpha sphere centers.
         */
        public static double[] calculateCenter(List<AlphaSphere> spheres) {
            if (spheres == null || spheres.isEmpty()) {
                return new double[]{0.0, 0.0, 0.0};
            }
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            for (AlphaSphere sphere : spheres) {
                x += sphere.getX();
                y += sphere.getY();
                z += sphere.getZ();
            }
            int count = spheres.size();
            return new double[]{
                    x / count,
                    y / count,
                    z / count
            };
        }


        /**
         * Calculate mean alpha sphere radius.
         */
        public static double calculateMeanRadius(List<AlphaSphere> spheres) {
            if (spheres == null || spheres.isEmpty()) {
                return 0.0;
            }
            return spheres.stream()
                    .mapToDouble(AlphaSphere::getRadius)
                    .average()
                    .orElse(0.0);
        }


        /**
         * Calculate maximum distance of any alpha sphere center
         * from the alpha sphere centroid.
         */
        public static double calculateMaxCentroidDistance(List<AlphaSphere> spheres) {
            if (spheres == null || spheres.isEmpty()) {
                return 0.0;
            }
            double[] center = calculateCenter(spheres);
            double maxDistance = 0.0;
            for (AlphaSphere sphere : spheres) {
                double dx = sphere.getX() - center[0];
                double dy = sphere.getY() - center[1];
                double dz = sphere.getZ() - center[2];
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance > maxDistance) {
                    maxDistance = distance;
                }
            }
            return maxDistance;
        }


        /**
         * Calculate bounding radius from centroid.
         *
         * Includes alpha sphere radius, so it represents
         * the outer pocket extent.
         */
        public static double calculateBoundingRadius(List<AlphaSphere> spheres) {
            if (spheres == null || spheres.isEmpty()) {
                return 0.0;
            }
            double[] center = calculateCenter(spheres);
            double maxRadius = 0.0;
            for (AlphaSphere sphere : spheres) {
                double dx = sphere.getX() - center[0];
                double dy = sphere.getY() - center[1];
                double dz = sphere.getZ() - center[2];

                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                // distance to sphere surface
                double outerDistance = distance + sphere.getRadius();
                if (outerDistance > maxRadius) {
                    maxRadius = outerDistance;
                }
            }
            return maxRadius;
        }

    public static PocketBox calculateBoundingBox(List<AlphaSphere> spheres, double padding) {
        if (spheres == null || spheres.isEmpty()) {
            return new PocketBox(new double[]{0,0,0},0,0, 0);
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;

        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (AlphaSphere s : spheres) {
            minX = Math.min(minX, s.getX() - s.getRadius());
            minY = Math.min(minY, s.getY() - s.getRadius());
            minZ = Math.min(minZ, s.getZ() - s.getRadius());

            maxX = Math.max(maxX, s.getX() + s.getRadius());
            maxY = Math.max(maxY, s.getY() + s.getRadius());
            maxZ = Math.max(maxZ, s.getZ() + s.getRadius());
        }

        double sizeX = (maxX - minX) + padding;
        double sizeY = (maxY - minY) + padding;
        double sizeZ = (maxZ - minZ) + padding;

        double[] center = new double[]{
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0
        };
        return new PocketBox(center, sizeX, sizeY, sizeZ);
    }
}

