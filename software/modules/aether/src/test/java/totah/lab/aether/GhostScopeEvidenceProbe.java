package totah.lab.aether;

import java.nio.file.*;
import java.util.List;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;

/** Small independent expectation table for auditing every saved CP iteration. */
public final class GhostScopeEvidenceProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        try (var writer = Files.newBufferedWriter(output)) {
            writer.write("system,role,real_atoms,ghost_centers,electrons,occupied_orbitals,occupation_hash,nuclear_repulsion\n");
            for (String name : List.of("water_dimer", "water_ammonia", "hcl_water", "methanethiol_water", "ammonium_benzene", "chlorobenzene_water")) {
                var f = D3TestSupport.fragments(name);
                for (boolean first : new boolean[]{true, false}) {
                    var real = first ? f.a().system() : f.b().system();
                    var donor = first ? f.b().system() : f.a().system();
                    var ghost = GhostBasis.withDonor(real, donor, BasisFamily.DEF2_SVP);
                    var occupation = OccupiedDensityCalculator.occupation(real, ghost.functions().size());
                    writer.write(name+","+(first?"A_GHOST_B":"B_GHOST_A")+","+real.nuclei().size()+","+ghost.ghosts().size()
                            +","+occupation.electrons()+","+occupation.occupiedOrbitals()+","+occupation.receiptHash()
                            +","+NuclearRepulsion.calculate(real).hartree()+"\n");
                }
            }
        }
    }
}
