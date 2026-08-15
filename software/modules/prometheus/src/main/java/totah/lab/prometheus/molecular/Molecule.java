package totah.lab.prometheus.molecular;

import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.identity.CanonicalHashing;

/** Immutable ordered Born-Oppenheimer molecule with validated charge and spin state. */
public record Molecule(String moleculeId,List<NuclearCenter> nuclei,MolecularCharge charge,ElectronCount electrons,SpinSector spin){
    public static final int MAX_SUPPORTED_ELECTRONS=16,MAX_SUPPORTED_NUCLEI=32;
    public Molecule{if(Objects.requireNonNull(moleculeId).isBlank())throw new IllegalArgumentException("blank molecule id");nuclei=List.copyOf(Objects.requireNonNull(nuclei));Objects.requireNonNull(charge);Objects.requireNonNull(electrons);Objects.requireNonNull(spin);if(nuclei.isEmpty()||nuclei.size()>MAX_SUPPORTED_NUCLEI)throw new IllegalArgumentException("unsupported nuclear count");LengthUnit unit=nuclei.getFirst().position().unit();for(int i=0;i<nuclei.size();i++){if(nuclei.get(i).orderedIndex()!=i)throw new IllegalArgumentException("nuclei must preserve contiguous atom order");if(nuclei.get(i).position().unit()!=unit)throw new IllegalArgumentException("molecular geometry must use one explicit length unit");}int expected=nuclei.stream().mapToInt(n->n.charge().atomicNumber()).sum()-charge.elementaryCharges();if(expected!=electrons.value())throw new IllegalArgumentException("Ne != sum(Z)-Q");if(spin.electronCount()!=electrons.value())throw new IllegalArgumentException("spin populations do not sum to electron count");if(electrons.value()>MAX_SUPPORTED_ELECTRONS)throw new IllegalArgumentException("electron count exceeds explicit Step-1 resource limit");}
    public AtomOrdering atomOrdering(){return AtomOrdering.canonical(nuclei.size());}
    public CartesianGeometry geometry(){LengthUnit unit=nuclei.getFirst().position().unit();return new CartesianGeometry(nuclei.stream().map(NuclearCenter::position).toList(),unit);}
    public String scientificIdentity(){StringBuilder b=new StringBuilder(moleculeId).append('|').append(charge.elementaryCharges()).append('|').append(electrons.value()).append('|').append(spin);for(NuclearCenter n:nuclei){CartesianPosition p=n.position().inBohr();b.append('|').append(n.orderedIndex()).append(':').append(n.charge().atomicNumber()).append(':').append(Double.toHexString(p.x())).append(':').append(Double.toHexString(p.y())).append(':').append(Double.toHexString(p.z()));}return CanonicalHashing.sha256Hex(b.toString());}
}
