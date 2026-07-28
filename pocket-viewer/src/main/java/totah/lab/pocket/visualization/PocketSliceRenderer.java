package totah.lab.pocket.visualization;

import totah.lab.pocket.Sphere;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.Sphere;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PocketSliceRenderer {

    private static final Color BACKGROUND =
            Color.WHITE;

    private static final Color PROTEIN_ATOM_COLOR =
            new Color(70, 70, 70, 80);

    private static final Color PROTEIN_ATOM_BORDER =
            new Color(55, 55, 55, 130);

    private static final Color ALPHA_SPHERE_COLOR =
            new Color(30, 90, 190, 170);

    private static final Color LABEL_COLOR =
            new Color(20, 20, 20, 220);

    private static final Color AXIS_COLOR =
            new Color(100, 100, 100, 100);

    private static final double DEFAULT_PADDING_ANGSTROM = 3.0;

    public BufferedImage renderProjection(
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            RenderOptions options) {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(plane, "plane");
        Objects.requireNonNull(options, "options");

        Bounds bounds = calculateBounds(
                pocket,
                plane,
                false,
                options.slabThickness(),
                options.paddingAngstrom());

        CoordinateTransform transform =
                CoordinateTransform.fit(
                        bounds,
                        options.width(),
                        options.height(),
                        options.imagePaddingPixels());

        BufferedImage image = createImage(options);
        Graphics2D graphics = image.createGraphics();

        try {
            prepareGraphics(graphics);
            fillBackground(graphics, options);

            if (options.drawAxes()) {
                drawAxes(graphics, transform);
            }

            drawAllAtomsProjected(
                    graphics,
                    pocket,
                    plane,
                    transform,
                    options);

            if (options.drawAlphaSpheres()) {
                drawAllAlphaSpheresProjected(
                        graphics,
                        pocket,
                        plane,
                        transform,
                        options);
            }

            if (options.drawResidueLabels()) {
                drawResidueLabels(
                        graphics,
                        pocket,
                        plane,
                        transform,
                        options,
                        false);
            }

            drawTitle(graphics, options);

        } finally {
            graphics.dispose();
        }

        return image;
    }

    public BufferedImage renderCrossSection(
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            RenderOptions options) {

        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(plane, "plane");
        Objects.requireNonNull(options, "options");

        Bounds bounds = calculateBounds(
                pocket,
                plane,
                true,
                options.slabThickness(),
                options.paddingAngstrom());

        CoordinateTransform transform =
                CoordinateTransform.fit(
                        bounds,
                        options.width(),
                        options.height(),
                        options.imagePaddingPixels());

        BufferedImage image = createImage(options);
        Graphics2D graphics = image.createGraphics();

        try {
            prepareGraphics(graphics);
            fillBackground(graphics, options);

            if (options.drawAxes()) {
                drawAxes(graphics, transform);
            }

            drawAtomCrossSections(
                    graphics,
                    pocket,
                    plane,
                    transform,
                    options);

            if (options.drawAlphaSpheres()) {
                drawAlphaSphereCrossSections(
                        graphics,
                        pocket,
                        plane,
                        transform,
                        options);
            }

            if (options.drawResidueLabels()) {
                drawResidueLabels(
                        graphics,
                        pocket,
                        plane,
                        transform,
                        options,
                        true);
            }

            drawTitle(graphics, options);

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private void drawAllAtomsProjected(
            Graphics2D graphics,
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            CoordinateTransform transform,
            RenderOptions options) {

        graphics.setColor(PROTEIN_ATOM_COLOR);

        for (Residue residue : pocket.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                PocketProjection.ProjectedPoint projected =
                        PocketProjection.project(atom.getPosition(),
                                plane);

                double radius = options.projectedAtomRadius();

                drawFilledCircle(
                        graphics,
                        transform.toPixelX(projected.x()),
                        transform.toPixelY(projected.y()),
                        transform.toPixelLength(radius),
                        PROTEIN_ATOM_COLOR,
                        PROTEIN_ATOM_BORDER);
            }
        }
    }

    private void drawAllAlphaSpheresProjected(
            Graphics2D graphics,
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            CoordinateTransform transform,
            RenderOptions options) {

        graphics.setStroke(
                new BasicStroke(options.alphaSphereStrokeWidth()));

        List<Sphere>spheres = pocket.getSpheres();
        for (Sphere sphere : spheres) {
            PocketProjection.ProjectedPoint projected =
                    PocketProjection.project(sphere.getPoint(),
                            plane);

            double radius = sphereRadius(sphere);

            drawOutlineCircle(
                    graphics,
                    transform.toPixelX(projected.x()),
                    transform.toPixelY(projected.y()),
                    transform.toPixelLength(radius),
                    ALPHA_SPHERE_COLOR);
        }
    }

    private void drawAtomCrossSections(
            Graphics2D graphics,
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            CoordinateTransform transform,
            RenderOptions options) {

        /*
         * Draw atoms farther from the center plane first. Atoms nearest the
         * center plane are then drawn on top.
         */
        List<SectionCircle> circles = new ArrayList<>();

        for (Residue residue : pocket.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                PocketProjection.ProjectedPoint projected =
                        PocketProjection.project(atom.getPosition(),
                                plane);

                double radius = vanDerWaalsRadius(
                        atom.getElement());

                double sectionRadius =
                        PocketProjection.slabSectionRadius(
                                radius,
                                projected.distanceFromPlane(),
                                options.slabThickness());

                if (sectionRadius <= 0.0) {
                    continue;
                }

                circles.add(new SectionCircle(
                        projected.x(),
                        projected.y(),
                        sectionRadius,
                        Math.abs(projected.distanceFromPlane())));
            }
        }

        circles.sort(
                Comparator.comparingDouble(
                                SectionCircle::planeDistance)
                        .reversed());

        for (SectionCircle circle : circles) {
            double distanceOpacity =
                    Math.max(
                            0.15,
                            1.0 - circle.planeDistance()
                                    / Math.max(
                                    0.01,
                                    options.slabThickness() / 2.0 + 3.0));

            Color fill = withOpacity(
                    PROTEIN_ATOM_COLOR,
                    distanceOpacity);

            Color border = withOpacity(
                    PROTEIN_ATOM_BORDER,
                    distanceOpacity);

            drawFilledCircle(
                    graphics,
                    transform.toPixelX(circle.x()),
                    transform.toPixelY(circle.y()),
                    transform.toPixelLength(circle.radius()),
                    fill,
                    border);
        }
    }

    private void drawAlphaSphereCrossSections(
            Graphics2D graphics,
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            CoordinateTransform transform,
            RenderOptions options) {

        graphics.setStroke(
                new BasicStroke(options.alphaSphereStrokeWidth()));

        List<Sphere>spheres = pocket.getSpheres();
        for (Sphere sphere : spheres) {
            PocketProjection.ProjectedPoint projected =
                    PocketProjection.project(sphere.getPoint(),
                            plane);

            double radius =
                    PocketProjection.sectionRadius(
                            sphereRadius(sphere),
                            projected.distanceFromPlane());

            if (radius <= 0.0) {
                continue;
            }

            drawOutlineCircle(
                    graphics,
                    transform.toPixelX(projected.x()),
                    transform.toPixelY(projected.y()),
                    transform.toPixelLength(radius),
                    ALPHA_SPHERE_COLOR);
        }
    }

    private void drawResidueLabels(
            Graphics2D graphics,
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            CoordinateTransform transform,
            RenderOptions options,
            boolean restrictToSlab) {

        Map<String, ResidueLabelAccumulator> labels =
                new HashMap<>();

        for (Residue residue : pocket.getResidues()) {
            String key = residueKey(residue);

            for (Atom atom : residue.getAtoms()) {
                PocketProjection.ProjectedPoint projected =
                        PocketProjection.project(atom.getPosition(),
                                plane);

                if (restrictToSlab) {
                    double labelDistance =
                            options.slabThickness() / 2.0
                                    + options.labelDistanceTolerance();

                    if (Math.abs(projected.distanceFromPlane())
                            > labelDistance) {
                        continue;
                    }
                }

                labels.computeIfAbsent(
                                key,
                                ignored -> new ResidueLabelAccumulator())
                        .add(projected.x(), projected.y());
            }
        }

        graphics.setFont(new Font(
                Font.SANS_SERIF,
                Font.PLAIN,
                options.labelFontSize()));

        graphics.setColor(LABEL_COLOR);

        for (Map.Entry<String, ResidueLabelAccumulator> entry
                : labels.entrySet()) {

            ResidueLabelAccumulator accumulator =
                    entry.getValue();

            if (accumulator.count == 0) {
                continue;
            }

            int x = (int) Math.round(
                    transform.toPixelX(accumulator.meanX()));

            int y = (int) Math.round(
                    transform.toPixelY(accumulator.meanY()));

            graphics.drawString(entry.getKey(), x + 3, y - 3);
        }
    }

    private static Bounds calculateBounds(
            Pocket pocket,
            PocketProjection.SlicePlane plane,
            boolean crossSection,
            double slabThickness,
            double padding) {

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Residue residue : pocket.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                PocketProjection.ProjectedPoint projected =
                        PocketProjection.project(atom.getPosition(),
                                plane);

                double radius;

                if (crossSection) {
                    radius =
                            PocketProjection.slabSectionRadius(
                                    vanDerWaalsRadius(
                                            atom.getElement()),
                                    projected.distanceFromPlane(),
                                    slabThickness);

                    if (radius <= 0.0) {
                        continue;
                    }
                } else {
                    radius = 1.0;
                }

                minX = Math.min(minX, projected.x() - radius);
                maxX = Math.max(maxX, projected.x() + radius);
                minY = Math.min(minY, projected.y() - radius);
                maxY = Math.max(maxY, projected.y() + radius);
            }
        }

        List<Sphere> spheres = pocket.getSpheres();
        for (Sphere sphere : spheres) {
            PocketProjection.ProjectedPoint projected =
                    PocketProjection.project(sphere.getPoint(),
                            plane);

            double radius;

            if (crossSection) {
                radius =
                        PocketProjection.sectionRadius(
                                sphereRadius(sphere),
                                projected.distanceFromPlane());

                if (radius <= 0.0) {
                    continue;
                }
            } else {
                radius = sphereRadius(sphere);
            }

            minX = Math.min(minX, projected.x() - radius);
            maxX = Math.max(maxX, projected.x() + radius);
            minY = Math.min(minY, projected.y() - radius);
            maxY = Math.max(maxY, projected.y() + radius);
        }

        if (!Double.isFinite(minX)) {
            return new Bounds(-10.0, 10.0, -10.0, 10.0);
        }

        double appliedPadding =
                padding > 0.0
                        ? padding
                        : DEFAULT_PADDING_ANGSTROM;

        return new Bounds(
                minX - appliedPadding,
                maxX + appliedPadding,
                minY - appliedPadding,
                maxY + appliedPadding);
    }

    private static void drawFilledCircle(
            Graphics2D graphics,
            double centerX,
            double centerY,
            double radius,
            Color fill,
            Color border) {

        Ellipse2D.Double circle = new Ellipse2D.Double(
                centerX - radius,
                centerY - radius,
                radius * 2.0,
                radius * 2.0);

        graphics.setColor(fill);
        graphics.fill(circle);

        graphics.setColor(border);
        graphics.draw(circle);
    }

    private static void drawOutlineCircle(
            Graphics2D graphics,
            double centerX,
            double centerY,
            double radius,
            Color color) {

        graphics.setColor(color);

        graphics.draw(new Ellipse2D.Double(
                centerX - radius,
                centerY - radius,
                radius * 2.0,
                radius * 2.0));
    }

    private static void drawAxes(
            Graphics2D graphics,
            CoordinateTransform transform) {

        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(AXIS_COLOR);

        int horizontalY =
                (int) Math.round(transform.toPixelY(0.0));

        int verticalX =
                (int) Math.round(transform.toPixelX(0.0));

        graphics.drawLine(
                0,
                horizontalY,
                transform.imageWidth(),
                horizontalY);

        graphics.drawLine(
                verticalX,
                0,
                verticalX,
                transform.imageHeight());
    }

    private static void drawTitle(
            Graphics2D graphics,
            RenderOptions options) {

        if (options.title() == null
                || options.title().isBlank()) {
            return;
        }

        graphics.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                options.titleFontSize()));

        graphics.setColor(LABEL_COLOR);

        graphics.drawString(
                options.title(),
                options.imagePaddingPixels(),
                options.titleFontSize()
                        + options.imagePaddingPixels() / 2);
    }

    private static BufferedImage createImage(
            RenderOptions options) {

        return new BufferedImage(
                options.width(),
                options.height(),
                BufferedImage.TYPE_INT_ARGB);
    }

    private static void fillBackground(
            Graphics2D graphics,
            RenderOptions options) {

        graphics.setColor(
                options.backgroundColor() == null
                        ? BACKGROUND
                        : options.backgroundColor());

        graphics.fillRect(
                0,
                0,
                options.width(),
                options.height());
    }

    private static void prepareGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }


    private static double sphereRadius(Sphere sphere) {
        double radius = sphere.radius();

        return Double.isFinite(radius) && radius > 0.0
                ? radius
                : 1.5;
    }

    private static String residueKey(Residue residue) {
        String chain =
                residue.getChain() == null
                        ? ""
                        : residue.getChain().toString();

        return String.format(
                Locale.ROOT,
                "%s%s%s",
                residue.getName(),
                residue.getNumber(),
                chain.isBlank() ? "" : ":" + chain);
    }

    private static double vanDerWaalsRadius(Element element) {
        return element == null
                ? 1.70
                : element.getVanDerWaalsRadiusOrDefault(1.70);
    }

    private static Color withOpacity(
            Color color,
            double opacityMultiplier) {

        double bounded =
                Math.max(0.0, Math.min(1.0, opacityMultiplier));

        int alpha = (int) Math.round(
                color.getAlpha() * bounded);

        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                alpha);
    }

    public record RenderOptions(
            int width,
            int height,
            double slabThickness,
            double projectedAtomRadius,
            double paddingAngstrom,
            int imagePaddingPixels,
            boolean drawAlphaSpheres,
            boolean drawResidueLabels,
            boolean drawAxes,
            double labelDistanceTolerance,
            float alphaSphereStrokeWidth,
            int labelFontSize,
            int titleFontSize,
            Color backgroundColor,
            String title) {

        public RenderOptions {
            if (width <= 0) {
                throw new IllegalArgumentException(
                        "width must be positive");
            }

            if (height <= 0) {
                throw new IllegalArgumentException(
                        "height must be positive");
            }

            if (slabThickness < 0.0) {
                throw new IllegalArgumentException(
                        "slabThickness cannot be negative");
            }

            if (imagePaddingPixels < 0) {
                throw new IllegalArgumentException(
                        "imagePaddingPixels cannot be negative");
            }
        }

        public static RenderOptions defaults() {
            return new RenderOptions(
                    1200,
                    1200,
                    3.0,
                    0.35,
                    3.0,
                    50,
                    true,
                    true,
                    false,
                    2.0,
                    1.2f,
                    12,
                    22,
                    Color.WHITE,
                    null);
        }

        public RenderOptions withTitle(String newTitle) {
            return new RenderOptions(
                    width,
                    height,
                    slabThickness,
                    projectedAtomRadius,
                    paddingAngstrom,
                    imagePaddingPixels,
                    drawAlphaSpheres,
                    drawResidueLabels,
                    drawAxes,
                    labelDistanceTolerance,
                    alphaSphereStrokeWidth,
                    labelFontSize,
                    titleFontSize,
                    backgroundColor,
                    newTitle);
        }

        public RenderOptions withSlabThickness(
                double newThickness) {

            return new RenderOptions(
                    width,
                    height,
                    newThickness,
                    projectedAtomRadius,
                    paddingAngstrom,
                    imagePaddingPixels,
                    drawAlphaSpheres,
                    drawResidueLabels,
                    drawAxes,
                    labelDistanceTolerance,
                    alphaSphereStrokeWidth,
                    labelFontSize,
                    titleFontSize,
                    backgroundColor,
                    title);
        }
    }

    private record Bounds(
            double minX,
            double maxX,
            double minY,
            double maxY) {

        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }

    private record CoordinateTransform(
            double minX,
            double maxY,
            double scale,
            int imageWidth,
            int imageHeight,
            int padding) {

        static CoordinateTransform fit(
                Bounds bounds,
                int imageWidth,
                int imageHeight,
                int padding) {

            double drawableWidth =
                    Math.max(1.0, imageWidth - 2.0 * padding);

            double drawableHeight =
                    Math.max(1.0, imageHeight - 2.0 * padding);

            double scaleX =
                    drawableWidth / Math.max(0.0001, bounds.width());

            double scaleY =
                    drawableHeight / Math.max(0.0001, bounds.height());

            double scale = Math.min(scaleX, scaleY);

            double renderedWidth = bounds.width() * scale;
            double renderedHeight = bounds.height() * scale;

            double extraX =
                    (drawableWidth - renderedWidth) / 2.0;

            double extraY =
                    (drawableHeight - renderedHeight) / 2.0;

            double adjustedMinX =
                    bounds.minX() - extraX / scale;

            double adjustedMaxY =
                    bounds.maxY() + extraY / scale;

            return new CoordinateTransform(
                    adjustedMinX,
                    adjustedMaxY,
                    scale,
                    imageWidth,
                    imageHeight,
                    padding);
        }

        double toPixelX(double coordinate) {
            return padding
                    + (coordinate - minX) * scale;
        }

        double toPixelY(double coordinate) {
            return padding
                    + (maxY - coordinate) * scale;
        }

        double toPixelLength(double length) {
            return length * scale;
        }
    }

    private record SectionCircle(
            double x,
            double y,
            double radius,
            double planeDistance) {
    }

    private static final class ResidueLabelAccumulator {

        private double sumX;
        private double sumY;
        private int count;

        void add(double x, double y) {
            sumX += x;
            sumY += y;
            count++;
        }

        double meanX() {
            return count == 0 ? 0.0 : sumX / count;
        }

        double meanY() {
            return count == 0 ? 0.0 : sumY / count;
        }
    }
}