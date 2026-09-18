package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.matrix.FragmentInteractionCalculator;
import totah.lab.aether.provenance.ScientificStatus;
import static totah.lab.athena.fragment.quantum.QuantumEnvironment.InteractionClass;

/** Separate interpretable descriptors. No total/master binding score exists. */
public record FragmentPhysicalFeatures(FragmentCalculationPlan plan,OptionalDouble rhfCp,OptionalDouble pbeCp,
                                      OptionalDouble d3Delta,OptionalDouble pbeD3Cp,Validity validity,
                                      Optional<FragmentInteractionCalculator.Result> calculation,
                                      List<String> unavailableReasons,String receiptHash) {
    public enum Validity { QUALITATIVE_ONLY, INSUFFICIENT_EVIDENCE, UNRELIABLE, OUT_OF_VALIDATED_DOMAIN }
    public FragmentPhysicalFeatures {
        unavailableReasons=List.copyOf(unavailableReasons);
        for(var value:List.of(rhfCp,pbeCp,d3Delta,pbeD3Cp))if(value.isPresent()&&!Double.isFinite(value.getAsDouble()))throw new IllegalArgumentException("Nonfinite feature");
        if(pbeD3Cp.isPresent()&&(!pbeCp.isPresent()||!d3Delta.isPresent()))throw new IllegalArgumentException("Combined PBE-D3 requires both independent terms");
        if(calculation.isEmpty()&&(rhfCp.isPresent()||pbeCp.isPresent()||d3Delta.isPresent()||pbeD3Cp.isPresent()))throw new IllegalArgumentException("Available features require calculation evidence");
        if(calculation.isPresent()) {
            var evidence=calculation.orElseThrow();
            if(!rhfCp.equals(evidence.rhfCpHartree())||!pbeCp.equals(evidence.pbeCpHartree())||!d3Delta.equals(evidence.d3DeltaHartree())||!pbeD3Cp.equals(evidence.pbeD3CpHartree()))
                throw new IllegalArgumentException("Feature values disagree with calculation evidence");
        }
        if(validity!=validity(plan))throw new IllegalArgumentException("Validity cannot be upgraded or replaced");
    }
    public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    public InteractionClass interactionClass(){return plan.constituentClasses().size()==1?plan.constituentClasses().getFirst():InteractionClass.mixed;}
    static Validity validity(FragmentCalculationPlan plan) {
        if(plan.left().quantum().isEmpty()||plan.right().quantum().isEmpty()||plan.constituentClasses().contains(InteractionClass.unclassified))return Validity.OUT_OF_VALIDATED_DOMAIN;
        // Mixed annotations cannot erase a constituent M17 limitation.
        if(plan.constituentClasses().contains(InteractionClass.pi_pi)||plan.constituentClasses().contains(InteractionClass.sulfur_aromatic))return Validity.INSUFFICIENT_EVIDENCE;
        return Validity.QUALITATIVE_ONLY;
    }
}
