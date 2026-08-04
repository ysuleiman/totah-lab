package totah.lab.web.service;

import totah.lab.gaia.geometry.Point3D;

/**
 * Web view of the retained rigid alignment transform: it maps the
 * original candidate coordinates into the query coordinate system.
 */
public record TransformView(
        double[][] rotation,
        Point3D translation
) {
}
