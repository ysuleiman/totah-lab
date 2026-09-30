package totah.lab.aether.matrix;

import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;

/** M17 measurement harness only; delegates all physics to frozen production APIs. */
public final class M17InteractionProbe {
    public static void main(String[] args) throws Exception {
        var lines = Files.readAllLines(Path.of(args[0]));
        var header = lines.getFirst().split(",");
        int cut = Integer.parseInt(header[0]);
        var nuclei = new ArrayList<NuclearCenter>();
        for (var line : lines.subList(1, lines.size())) {
            var c = line.split(",");
            nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]), Double.parseDouble(c[3])), Double.parseDouble(c[0])));
        }
        var a = new QuantumSystem(nuclei.subList(0, cut), Integer.parseInt(header[1]), 1);
        var b = new QuantumSystem(nuclei.subList(cut, nuclei.size()), Integer.parseInt(header[2]), 1);
        var pair = new FragmentPair(new MolecularFragment("A", a), new MolecularFragment("B", b));
        Path out = Path.of(args[1]), cache = Path.of(args[2]);
        Files.createDirectories(out);
        var family = BasisFamily.DEF2_SVP;
        var basis = family.forSystem(pair.complex());
        var d3 = D3Dispersion.load();
        if (args.length > 3 && args[3].equals("D3_ONLY")) {
            var abD3 = d3.calculate(pair.complex());
            var aD3 = d3.calculate(a); var bD3 = d3.calculate(b);
            Files.writeString(out.resolve("dispersion.txt"), "D3_ENERGY="+abD3.totalHartree()
                    +"\nD3_A="+aD3.totalHartree()+"\nD3_B="+bD3.totalHartree()
                    +"\nD3_DELTA="+InteractionEnergyCalculator.subtract(abD3.totalHartree(),aD3.totalHartree(),bD3.totalHartree())
                    +"\nAB_RECEIPT="+abD3.receiptHash()+"\nA_RECEIPT="+aD3.receiptHash()
                    +"\nB_RECEIPT="+bD3.receiptHash()+"\nSCREENING_ONLY\n");
            return;
        }
        var pbe = new PbeD3Energy[3];
        double[] rhf = new double[3];
        Arrays.fill(rhf, Double.NaN);
        String[] roles = {"AB", "A_GHOST_B", "B_GHOST_A"};
        for (int i = 0; i < 3; i++) {
            final String role = roles[i];
            var real = i == 0 ? pair.complex() : i == 1 ? a : b;
            var ghost = i == 0 ? null : GhostBasis.withDonor(real, i == 1 ? b : a, family);
            var opts = new SemiDirectScf.Options(cache);
            var r = ghost == null ? SemiDirectScf.solve(real, basis, opts, x -> System.out.println(role+" RHF "+x))
                    : SemiDirectScf.solve(ghost, basis, opts, x -> System.out.println(role+" RHF "+x));
            Files.writeString(out.resolve(role+"-RHF.receipt"), r.receiptHash()+"\n"+r.iterations()+"\n");
            if (r.convergedState().isPresent()) rhf[i] = r.convergedState().orElseThrow().totalHartree();
            Files.writeString(out.resolve(role+"-RHF.txt"), "status="+r.convergenceStatus()+"\nenergy="+rhf[i]+"\n");
            var po = new PbeScf.Options(cache);
            var p = ghost == null ? PbeScf.solve(real, basis, po, x -> System.out.println(role+" PBE "+x))
                    : PbeScf.solve(ghost, po, x -> System.out.println(role+" PBE "+x));
            Files.writeString(out.resolve(role+"-PBE.receipt"), p.receiptHash()+"\n"+p.iterations()+"\n");
            Files.writeString(out.resolve(role+"-PBE.txt"), "status="+p.convergenceStatus()+"\nenergy="
                    +(p.convergedState().isPresent() ? p.convergedState().orElseThrow().totalHartree() : "UNAVAILABLE")+"\n");
            if (p.convergedState().isPresent()) pbe[i] = PbeD3Energy.combine(real, p, d3.calculate(real));
        }
        var result = new StringBuilder("SCREENING_ONLY\n");
        if (Arrays.stream(rhf).allMatch(Double::isFinite))
            result.append("RHF_CP=").append(InteractionEnergyCalculator.subtract(rhf[0], rhf[1], rhf[2])).append('\n');
        if (Arrays.stream(pbe).allMatch(Objects::nonNull)) {
            var cp = PbeD3Counterpoise.combine(pair, pbe[0], pbe[1], pbe[2]);
            result.append("PBE_CP=").append(cp.pbeHartree()).append("\nD3_DELTA=").append(cp.dispersionHartree())
                    .append("\nPBE_D3_CP=").append(cp.totalHartree()).append("\nD3_ENERGY=").append(pbe[0].dispersionHartree())
                    .append("\nreceipt=").append(cp.receiptHash()).append('\n');
        }
        Files.writeString(out.resolve("interaction.txt"), result);
        if (!Arrays.stream(rhf).allMatch(Double::isFinite) || !Arrays.stream(pbe).allMatch(Objects::nonNull))
            throw new AssertionError("Incomplete convergence; component evidence retained");
    }
}
