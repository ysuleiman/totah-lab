package totah.lab.aether;

import java.nio.file.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;

/** Fresh-JVM CP component evidence; real nuclei define the grid, electrons and D3. */
public final class D3CpProbe {
    public static void main(String[] args) throws Exception {
        String name = args[0]; Path out = Path.of(args[1]), cache = Path.of(args[2]);
        Files.createDirectories(out);
        var f = D3TestSupport.fragments(name); var d3 = D3Dispersion.load();
        var components = new PbeD3Energy[2];
        for (boolean first : new boolean[]{true, false}) {
            String role = first ? "A_GHOST_B" : "B_GHOST_A";
            var real = first ? f.a().system() : f.b().system();
            var donor = first ? f.b().system() : f.a().system();
            var ghost = GhostBasis.withDonor(real, donor, BasisFamily.DEF2_SVP);
            var r = PbeScf.solve(ghost, new PbeScf.Options(cache), x -> System.out.println(name+" "+role+" "+x));
            Path stem = out.resolve(name+"-"+role);
            Files.writeString(Path.of(stem+".receipt"), r.receiptHash()+"\n"+r.iterations()+"\n");
            if (r.convergedState().isEmpty()) throw new AssertionError(name+" "+role+" "+r.convergenceStatus());
            var energy = PbeD3Energy.combine(real, r, d3.calculate(real));
            if (args.length > 3) components[first ? 0 : 1] = energy;
            Files.writeString(Path.of(stem+".txt"), "PBE="+energy.pbeHartree()+"\nD3="+energy.dispersionHartree()
                    +"\nPBE_D3="+energy.totalHartree()+"\nENUC="+r.convergedState().orElseThrow().nuclearHartree()
                    +"\nghostIdentity="+ghost.identity()+"\nreceipt="+energy.receiptHash()+"\nSCREENING_ONLY\n");
        }
        if (args.length > 3) {
            var full = PbeScf.solve(f.complex(), BasisFamily.DEF2_SVP.forSystem(f.complex()),
                    new PbeScf.Options(cache), x -> System.out.println(name+" AB "+x));
            var ab = PbeD3Energy.combine(f.complex(), full, d3.calculate(f.complex()));
            var cp = PbeD3Counterpoise.combine(f, ab, components[0], components[1]);
            Files.writeString(out.resolve(name+"-CP.txt"), "PBE_CP="+cp.pbeHartree()+"\nD3="+cp.dispersionHartree()
                    +"\nPBE_D3_CP="+cp.totalHartree()+"\nreceipt="+cp.receiptHash()+"\n"+cp.counterpoiseConvention()+"\nSCREENING_ONLY\n");
        }
    }
}
