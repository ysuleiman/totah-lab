package totah.lab.aether.matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;

/** Bounded M17.1 pilot: fresh reference file, mapped contractions, optional ghost SCF. */
public final class M171CacheProbe {
    public static void main(String[] args) throws Exception {
        var lines=Files.readAllLines(Path.of(args[0]));var header=lines.getFirst().split(",");
        int cut=Integer.parseInt(header[0]);var nuclei=new ArrayList<NuclearCenter>();
        for(var line:lines.subList(1,lines.size())) {
            var c=line.split(",");nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),Double.parseDouble(c[2]),Double.parseDouble(c[3])),Double.parseDouble(c[0])));
        }
        var a=new QuantumSystem(nuclei.subList(0,cut),Integer.parseInt(header[1]),1);
        var b=new QuantumSystem(nuclei.subList(cut,nuclei.size()),Integer.parseInt(header[2]),1);
        var ab=new QuantumSystem(nuclei,a.molecularCharge()+b.molecularCharge(),1);
        var basis=BasisFamily.DEF2_SVP.forSystem(ab);var ghost=GhostBasis.withDonor(b,a,BasisFamily.DEF2_SVP);
        Path out=Path.of(args[1]),root=Path.of(args[2]);Files.createDirectories(out);
        if(args.length>3&&args[3].equals("MAP_ONLY")) {
            boolean bijection=PermutedCacheContractions.bijection(basis,ghost.functions()).isPresent();
            long slots=EriDiskCache.triangle(EriDiskCache.pairCount(basis.size()));
            Files.writeString(out.resolve("map-audit.txt"),"SCREENING_ONLY\nBIJECTION="+bijection
                    +"\nCANONICAL="+EriDiskCache.identity(basis)+"\nREQUESTED="+EriDiskCache.identity(ghost.functions())
                    +"\nAO_COUNT="+basis.size()+"\nUNIQUE_SLOTS="+slots+"\nPAYLOAD_BYTES="+8*slots+"\n");
            if(!bijection)throw new AssertionError("M17 basis permutation was not proved");
            return;
        }
        long start=System.nanoTime();
        EriDiskCache canonical;
        Path historical=Path.of("software/modules/aether/validation/milestone-16/cache").resolve(EriDiskCache.identity(basis));
        if(Files.isDirectory(historical)) canonical=EriDiskCache.open(historical,basis);
        else canonical=EriDiskCache.openOrCreate(root,basis,8).cache();
        System.out.println("CANONICAL_READY ao="+basis.size());
        var fresh=EriDiskCache.openOrCreate(root,ghost.functions(),8);
        System.out.println("FRESH_READY seconds="+fresh.generationNanos()/1e9);
        int n=basis.size();var values=new ArrayList<Double>();
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add((i==j?.5:.02)/(i+j+1));
        var density=DensityMatrix.fromRowMajor(b,ghost.functions(),values);
        double eriError=0,jError=0,kError=0;long referenceTime,reuseTime;
        var report=new StringBuilder("SCREENING_ONLY\nAO_COUNT="+n+"\n");
        try(var view=new PermutedCacheContractions(b,canonical,ghost.functions(),false);
            var reference=new SemiDirectJk(b,fresh.cache());
            var cf=FileChannel.open(canonical.directory().resolve("eri.bin"),StandardOpenOption.READ);
            var rf=FileChannel.open(fresh.cache().directory().resolve("eri.bin"),StandardOpenOption.READ)) {
            for(int q=0;q<512;q++) {
                int i=(q*7)%n,j=(q*13+1)%n,k=(q*19+2)%n,l=(q*29+3)%n;
                eriError=Math.max(eriError,Math.abs(read(cf,view.canonicalOffset(i,j,k,l))-read(rf,EriDiskCache.offset(i,j,k,l))));
            }
            var expected=reference.calculate(density);var actual=view.calculateJk(density);
            referenceTime=expected.elapsedNanos();reuseTime=actual.elapsedNanos();
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                jError=Math.max(jError,Math.abs(expected.coulomb().get(i,j)-actual.coulomb().get(i,j)));
                kError=Math.max(kError,Math.abs(expected.exchange().get(i,j)-actual.exchange().get(i,j)));
            }
            Files.writeString(out.resolve("permutation-provenance.txt"),view.provenance()+"\n");
            Files.writeString(out.resolve("contraction.receipt"),actual.receiptHash()+"\n");
        }
        if(eriError>1e-12||jError>1e-10||kError>1e-10)throw new AssertionError("Permutation numerical gate");
        report.append("MAX_ERI_ERROR=").append(eriError).append("\nMAX_J_ERROR=").append(jError).append("\nMAX_K_ERROR=").append(kError)
            .append("\nUNIQUE_SLOTS=").append(canonical.uniqueSlots()).append("\nDISK_BYTES_AVOIDED_PER_REUSE=").append(canonical.payloadBytes())
            .append("\nFRESH_GENERATED=").append(fresh.generated()).append("\nFRESH_GENERATION_SECONDS=").append(fresh.generationNanos()/1e9)
            .append("\nREFERENCE_JK_SECONDS=").append(referenceTime/1e9).append("\nREUSE_JK_SECONDS=").append(reuseTime/1e9).append('\n');
        Files.writeString(out.resolve("measurements.txt"),report);
        if(args.length>3&&args[3].equals("SCF")) {
            var ro=new SemiDirectScf.Options(root);
            var rr=SemiDirectScf.solve(ghost,ghost.functions(),ro,System.out::println);
            var rv=SemiDirectScf.solveWithCache(ghost,ghost.functions(),ro,System.out::println,canonical);
            var po=new PbeScf.Options(root);
            var pr=PbeScf.solve(ghost,po,System.out::println);
            var pv=PbeScf.solveWithCache(ghost,po,System.out::println,canonical);
            Files.writeString(out.resolve("RHF-fresh.receipt"),rr.receiptHash()+"\n"+rr.iterations()+"\n");
            Files.writeString(out.resolve("PBE-fresh.receipt"),pr.receiptHash()+"\n"+pr.iterations()+"\n");
            double re=Math.abs(rr.convergedState().orElseThrow().totalHartree()-rv.convergedState().orElseThrow().totalHartree());
            double pe=Math.abs(pr.convergedState().orElseThrow().totalHartree()-pv.convergedState().orElseThrow().totalHartree());
            if(re>1e-9||pe>1e-9)throw new AssertionError("SCF solution changed");
            report.append("MAX_RHF_ENERGY_ERROR=").append(re).append("\nMAX_PBE_ENERGY_ERROR=").append(pe)
                .append("\nCP_ERROR_FOR_REPLACED_B_COMPONENT_RHF=").append(re).append("\nCP_ERROR_FOR_REPLACED_B_COMPONENT_PBE=").append(pe).append('\n');
            Files.writeString(out.resolve("RHF.receipt"),rv.receiptHash()+"\n"+rv.iterations()+"\n");
            Files.writeString(out.resolve("PBE.receipt"),pv.receiptHash()+"\n"+pv.iterations()+"\n");
        }
        report.append("ELAPSED_SECONDS=").append((System.nanoTime()-start)/1e9).append('\n');
        Files.writeString(out.resolve("measurements.txt"),report);
        System.out.print(report);
    }
    private static double read(FileChannel channel,long offset)throws Exception {
        var buffer=ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        while(buffer.hasRemaining())if(channel.read(buffer,offset+buffer.position())<0)throw new java.io.EOFException();
        buffer.flip();return buffer.getDouble();
    }
}
