package totah.lab.prometheus.molecular;

import java.util.Objects;
import totah.lab.prometheus.identity.CanonicalHashing;

/** Scientific identity extension for the general molecular Hamiltonian/state/optimizer stack. */
public record GeneralMolecularRequestIdentity(Molecule molecule,String hamiltonianProtocol,String wavefunctionArchitecture,String optimizerProtocol){public GeneralMolecularRequestIdentity{Objects.requireNonNull(molecule);if(Objects.requireNonNull(hamiltonianProtocol).isBlank()||Objects.requireNonNull(wavefunctionArchitecture).isBlank()||Objects.requireNonNull(optimizerProtocol).isBlank())throw new IllegalArgumentException("scientific protocol identifiers must be non-blank");}public String sha256(){return CanonicalHashing.sha256Hex(molecule.scientificIdentity()+"|"+hamiltonianProtocol+"|"+wavefunctionArchitecture+"|"+optimizerProtocol);}}
