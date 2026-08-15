package totah.lab.prometheus.execution.quantum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.molecular.GeneralMolecularRequestIdentity;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.planning.CalculationSpecification;

/** Adds the complete general molecular/state/optimizer identity to a Step-0 runtime request. */
public record GeneralMolecularExecutionRequest(Molecule molecule,QuantumExecutionRequest runtimeRequest,String hamiltonianProtocol,String wavefunctionArchitecture,String optimizerProtocol){
    public GeneralMolecularExecutionRequest{Objects.requireNonNull(molecule);Objects.requireNonNull(runtimeRequest);if(molecule.nuclei().size()!=runtimeRequest.geometry().atoms().size())throw new IllegalArgumentException("general molecule/runtime geometry atom count mismatch");if(molecule.charge().elementaryCharges()!=runtimeRequest.specification().formalCharge()||molecule.spin().multiplicity()!=runtimeRequest.specification().multiplicity())throw new IllegalArgumentException("general molecule/runtime electronic state mismatch");new GeneralMolecularRequestIdentity(molecule,hamiltonianProtocol,wavefunctionArchitecture,optimizerProtocol);}
    public QuantumExecutionRequest identityCompleteRequest(){var old=runtimeRequest.specification();List<String> outputs=new ArrayList<>(old.requiredOutputs());outputs.add("generalMolecule="+molecule.scientificIdentity());outputs.add("hamiltonian="+hamiltonianProtocol);outputs.add("wavefunction="+wavefunctionArchitecture);outputs.add("optimizer="+optimizerProtocol);outputs.sort(String::compareTo);var specification=new CalculationSpecification(old.specificationId(),old.scientificPurpose(),old.molecule(),old.geometry(),old.formalCharge(),old.multiplicity(),old.protocol(),old.constraints(),old.calculationType(),outputs,old.acceptanceGates(),old.role(),old.estimatedCost());return new QuantumExecutionRequest(specification,runtimeRequest.geometry(),runtimeRequest.canonicalAtomMapHash(),runtimeRequest.solverMode(),runtimeRequest.requiredObservables(),runtimeRequest.options());}
}
