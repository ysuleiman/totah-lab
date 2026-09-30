package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.model.GhostCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Checked complete real-atom STO-3G basis followed by ordered ghost-center shells. */
public final class GhostBasis {
    private final QuantumSystem system;
    private final List<GhostCenter> ghosts;
    private final List<ContractedGaussian> functions;
    private final String identity;
    private GhostBasis(QuantumSystem system,List<GhostCenter> ghosts,totah.lab.aether.basis.BasisFamily family) throws IOException {
        this.system=Objects.requireNonNull(system);this.ghosts=List.copyOf(ghosts);
        if(this.ghosts.isEmpty())throw new IllegalArgumentException("Ghost basis requires at least one ghost center");
        var library=Objects.requireNonNull(family);var basis=new ArrayList<>(library.forSystem(system));
        var positions=new ArrayList<>(system.nuclei().stream().map(n->n.centerBohr()).toList());
        var canonical=new StringBuilder("aether-ghost-basis-v1\nreal-first;ghost-order;STO-3G;s/p;bohr;ghost-Z=0;ghost-electrons=0\n")
                .append(IntegralMatrixData.systemHash(system)).append('\n');
        for(var ghost:this.ghosts) {
            var p=ghost.centerBohr();
            if(positions.stream().anyMatch(q->q.x()==p.x()&&q.y()==p.y()&&q.z()==p.z()))
                throw new IllegalArgumentException("Coincident real/ghost or ghost/ghost centers");
            positions.add(p);basis.addAll(library.atBohr(ghost.basisAtomicNumber(),p));
            canonical.append(ghost.basisAtomicNumber()).append('\n').append(ContentHash.number(p.x())).append('\n')
                    .append(ContentHash.number(p.y())).append('\n').append(ContentHash.number(p.z())).append('\n');
        }
        functions=List.copyOf(basis);
        if(family==totah.lab.aether.basis.BasisFamily.DEF2_SVP)canonical=new StringBuilder(canonical.toString().replace("STO-3G;s/p;","def2-SVP;s/p/d-Cartesian;"));
        canonical.append(IntegralMatrixData.basisGeometryHash(functions));
        identity=ContentHash.sha256(canonical.toString());
    }
    public static GhostBasis of(QuantumSystem realSystem,List<GhostCenter> ghosts) throws IOException {
        return new GhostBasis(realSystem,ghosts,totah.lab.aether.basis.BasisFamily.STO_3G);
    }
    /** Only donor element/position defines the ghost basis; donor charge/spin never changes the real system. */
    public static GhostBasis withDonor(QuantumSystem realSystem,QuantumSystem donor) throws IOException {
        return of(realSystem,donor.nuclei().stream().map(n->new GhostCenter((int)n.charge(),n.centerBohr())).toList());
    }
    public static GhostBasis of(QuantumSystem realSystem,List<GhostCenter> ghosts,totah.lab.aether.basis.BasisFamily family) throws IOException {
        return new GhostBasis(realSystem,ghosts,family);
    }
    public static GhostBasis withDonor(QuantumSystem realSystem,QuantumSystem donor,totah.lab.aether.basis.BasisFamily family) throws IOException {
        return of(realSystem,donor.nuclei().stream().map(n->new GhostCenter((int)n.charge(),n.centerBohr())).toList(),family);
    }
    public QuantumSystem system() { return system; }
    public List<GhostCenter> ghosts() { return ghosts; }
    public List<ContractedGaussian> functions() { return functions; }
    public String identity() { return identity; }
    public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    void validate(QuantumSystem suppliedSystem,List<ContractedGaussian> suppliedFunctions) {
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(system),IntegralMatrixData.systemHash(suppliedSystem),"ghost real system");
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.basisGeometryHash(functions),IntegralMatrixData.basisGeometryHash(suppliedFunctions),"ghost ordered basis");
        OccupiedDensityCalculator.occupation(system,functions.size());
    }
}
