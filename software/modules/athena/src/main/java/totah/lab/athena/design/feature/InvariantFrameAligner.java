package totah.lab.athena.design.feature;

import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aligns a conformer by configured invariant correspondences; applies no target threshold. */
public final class InvariantFrameAligner {
    private static final double MINIMUM_NORMALIZED_TRIANGLE_AREA_SQUARED = 1.0e-12;
    public Result align(Map<String, Point3D> candidate, Map<String, Point3D> template) {
        List<String> ids = candidate.keySet().stream().filter(template::containsKey).sorted().toList();
        if (ids.size() < 3) return new Result(false, null, null, ids, false,
                "at least three invariant correspondences required");
        List<Point3D> source=new ArrayList<>(),target=new ArrayList<>();
        ids.forEach(id->{source.add(candidate.get(id));target.add(template.get(id));});
        if (!hasNonCollinearTriple(source) || !hasNonCollinearTriple(target)) {
            return new Result(false, null, null, ids, true,
                    "invariant correspondences are geometrically rank-deficient");
        }
        RigidTransform transform=new KabschRigidPointAligner().align(source,target);double sum=0;
        for(int i=0;i<source.size();i++)sum+=transform.apply(source.get(i)).distanceSquared(target.get(i));
        return new Result(true,Math.sqrt(sum/source.size()),transform,ids,false,
                "unique configured atom-lineage correspondence; symmetry alternatives must be supplied separately");
    }
    public record Result(boolean aligned,Double rmsd,RigidTransform transform,List<String> alignmentAtomIds,
                         boolean symmetryAmbiguous,String reason){public Result{alignmentAtomIds=List.copyOf(alignmentAtomIds);}}

    private static boolean hasNonCollinearTriple(List<Point3D> points) {
        for (int i = 0; i < points.size() - 2; i++) for (int j = i + 1; j < points.size() - 1; j++)
            for (int k = j + 1; k < points.size(); k++) {
                Point3D a = points.get(i), b = points.get(j), c = points.get(k);
                double ux=b.x()-a.x(), uy=b.y()-a.y(), uz=b.z()-a.z();
                double vx=c.x()-a.x(), vy=c.y()-a.y(), vz=c.z()-a.z();
                double cx=uy*vz-uz*vy, cy=uz*vx-ux*vz, cz=ux*vy-uy*vx;
                double areaSquared=cx*cx+cy*cy+cz*cz;
                double scale=Math.max(ux*ux+uy*uy+uz*uz, vx*vx+vy*vy+vz*vz);
                if (scale > 0.0 && areaSquared/(scale*scale) > MINIMUM_NORMALIZED_TRIANGLE_AREA_SQUARED) return true;
            }
        return false;
    }
}
