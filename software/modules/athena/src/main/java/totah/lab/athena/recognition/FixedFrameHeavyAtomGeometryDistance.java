package totah.lab.athena.recognition;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/** Fixed-frame heavy-atom RMSD with explicit canonical correspondence and no fitted alignment. */
public final class FixedFrameHeavyAtomGeometryDistance implements RecognitionGeometryDistance {
    public record PointAngstrom(double x,double y,double z) { }
    public record PoseGeometry(Map<String,PointAngstrom> canonicalHeavyAtoms,String mappingHash,String provenance) {
        public PoseGeometry { canonicalHeavyAtoms=Map.copyOf(canonicalHeavyAtoms);if(mappingHash==null||mappingHash.isBlank()||provenance==null||provenance.isBlank())throw new IllegalArgumentException("geometry provenance required"); }
    }
    private final Map<String,PoseGeometry> poses;
    public FixedFrameHeavyAtomGeometryDistance(Map<String,PoseGeometry> poses){this.poses=Map.copyOf(poses);}
    @Override public OptionalDouble distanceAngstroms(RecognitionStateObservation first,RecognitionStateObservation second){
        PoseGeometry a=poses.get(first.poseId()),b=poses.get(second.poseId());if(a==null||b==null||a.canonicalHeavyAtoms().isEmpty())return OptionalDouble.empty();
        Set<String> keys=new LinkedHashSet<>(a.canonicalHeavyAtoms().keySet());if(!keys.equals(b.canonicalHeavyAtoms().keySet()))return OptionalDouble.empty();
        double sum=0;for(String key:keys){PointAngstrom p=a.canonicalHeavyAtoms().get(key),q=b.canonicalHeavyAtoms().get(key);sum+=sq(p.x-q.x)+sq(p.y-q.y)+sq(p.z-q.z);}return OptionalDouble.of(Math.sqrt(sum/keys.size()));
    }
    private static double sq(double value){return value*value;}
}
