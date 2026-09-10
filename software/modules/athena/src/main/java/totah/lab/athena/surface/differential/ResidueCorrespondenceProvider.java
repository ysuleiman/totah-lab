package totah.lab.athena.surface.differential;

import java.util.List;

/** Pluggable owner of correspondence; independent of the scoring kernel. */
@FunctionalInterface
public interface ResidueCorrespondenceProvider {
    ExplicitResidueCorrespondence correspond(
            List<SurfaceResidue> query,
            List<SurfaceResidue> subject);
}
