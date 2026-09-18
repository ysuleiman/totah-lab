package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.model.MolecularFragment;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.AtomReference;

/** Original/cap geometry is retained in angstrom, separately from the optional bohr quantum input. */
public record PreparedQuantumFragment(String id,String sourceComponent,String environmentHash,
                                      String sourceStateId,String sourceStructureHash,Map<String,String> sourceProvenance,
                                      List<OriginalAtom> retained,List<OriginalAtom> deleted,List<CapAtom> caps,
                                      QuantumEnvironment.ChargeStatus chargeStatus,OptionalInt formalCharge,
                                      int multiplicity,String protonationAssignment,String chargeSource,
                                      Optional<MolecularFragment> quantum,List<String> unavailableReasons,String receiptHash) {
    public record OriginalAtom(AtomReference reference,String residueName,int sourceOrder,int sourceSerial,int atomicNumber,Point3D originalAngstrom,OptionalInt formalCharge) {}
    public record CapAtom(String id,AtomReference retainedBoundary,AtomReference deletedBoundary,
                          Point3D retainedOriginalAngstrom,Point3D deletedOriginalAngstrom,
                          Point3D capAngstrom,int formalCharge,String constructionRule) {}
    public PreparedQuantumFragment {
        retained=List.copyOf(retained);deleted=List.copyOf(deleted);caps=List.copyOf(caps);
        sourceProvenance=Map.copyOf(sourceProvenance);
        Objects.requireNonNull(quantum);Objects.requireNonNull(formalCharge);unavailableReasons=List.copyOf(unavailableReasons);
        if(quantum.isPresent()&&(!unavailableReasons.isEmpty()||chargeStatus==QuantumEnvironment.ChargeStatus.AMBIGUOUS))
            throw new IllegalArgumentException("Unavailable fragment cannot carry accepted quantum input");
    }
}
