package totah.lab.aether.matrix;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** M18 assembly only: frozen physical methods, separate CP features, explicit cache reuse. */
public final class FragmentInteractionCalculator {
    public static final String PROTOCOL = "aether-fragment-interface-18-1;def2-SVP;frozen-RHF-PBE-D3;"
            + "grid=120x590;block=256;workers=8;core-guess;DIIS-start=32;DIIS-history=8;energy=1e-12;density=1e-10;cap=128;"
            + "electronic-CP;physical-atoms-only-D3;M17.1-explicit-permutation;SCREENING_ONLY";
    private FragmentInteractionCalculator() {}

    public record Component(String method, String role, String scfStatus, OptionalDouble energyHartree,
                            String receiptHash, String systemHash, String requestedCacheIdentity,
                            String canonicalCacheIdentity, boolean permutationReuse, boolean cacheHit,
                            String permutationProvenance, List<ExactScf.Iteration> iterations, String failure) {
        public Component { iterations=List.copyOf(iterations); }
    }
    public record Result(OptionalDouble rhfCpHartree, OptionalDouble pbeCpHartree,
                         OptionalDouble d3DeltaHartree, OptionalDouble pbeD3CpHartree,
                         List<Component> components, List<String> dispersionReceipts,
                         List<String> failures, String receiptHash) {
        public Result { components=List.copyOf(components);dispersionReceipts=List.copyOf(dispersionReceipts);failures=List.copyOf(failures); }
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    }

    public static Result calculate(FragmentPair pair, Path cacheDirectory, Consumer<String> progress) throws IOException {
        var failures=new ArrayList<String>();var d3Receipts=new ArrayList<String>();
        OptionalDouble dispersion=OptionalDouble.empty();
        try {
            var d3=D3Dispersion.load();var ab=d3.calculate(pair.complex());
            var a=d3.calculate(pair.a().system());var b=d3.calculate(pair.b().system());
            dispersion=OptionalDouble.of(InteractionEnergyCalculator.subtract(ab.totalHartree(),a.totalHartree(),b.totalHartree()));
            d3Receipts.add(ab.receiptHash());d3Receipts.add(a.receiptHash());d3Receipts.add(b.receiptHash());
        } catch(ArithmeticException | IllegalArgumentException | IOException exception) {
            failures.add("D3_UNAVAILABLE:"+exception.getMessage());
        }
        List<totah.lab.aether.basis.ContractedGaussian> basis;
        EriDiskCache.Creation canonical;
        try {
            basis=BasisFamily.DEF2_SVP.forSystem(pair.complex());
            // Execution budget only: it does not screen or approximate an integral.
            if(basis.size()>240)throw new IllegalArgumentException("RESOURCE_LIMIT: M18 pair exceeds 240 AOs");
            canonical=EriDiskCache.openOrCreate(cacheDirectory,basis,8);
        } catch(IOException | IllegalArgumentException exception) {
            String status=exception instanceof IOException?"IO_FAILURE":"UNSUPPORTED_OR_RESOURCE_LIMIT";
            String failure=status+":"+exception.getMessage();failures.add(failure);
            var unavailable=new ArrayList<Component>();
            for(String method:List.of("RHF","PBE"))for(int role=0;role<3;role++) {
                var system=role==0?pair.complex():role==1?pair.a().system():pair.b().system();
                unavailable.add(new Component(method,List.of("AB","A_GHOST_B","B_GHOST_A").get(role),status,OptionalDouble.empty(),ContentHash.sha256(failure),
                        IntegralMatrixData.systemHash(system),"UNAVAILABLE","UNAVAILABLE",false,false,"NOT_EVALUATED",List.of(),failure));
            }
            return assemble(pair,unavailable,dispersion,d3Receipts,failures);
        }
        var components=new ArrayList<Component>();
        for(String method:List.of("RHF","PBE"))for(int role=0;role<3;role++) {
            String label=List.of("AB","A_GHOST_B","B_GHOST_A").get(role);
            var physical=role==0?pair.complex():role==1?pair.a().system():pair.b().system();
            var ghost=role==0?null:GhostBasis.withDonor(physical,role==1?pair.b().system():pair.a().system(),BasisFamily.DEF2_SVP);
            var requested=ghost==null?basis:ghost.functions();
            boolean permutation=!EriDiskCache.identity(requested).equals(canonical.cache().identity());
            String mapping="IDENTICAL_ORDER";
            if(ghost!=null)try(var view=new PermutedCacheContractions(physical,canonical.cache(),requested,true)) { mapping=view.provenance(); }
            try {
                String status,receipt;OptionalDouble energy;List<ExactScf.Iteration> iterations;
                if(method.equals("RHF")) {
                    var options=new SemiDirectScf.Options(cacheDirectory);
                    var run=ghost==null?SemiDirectScf.solve(physical,basis,options,x->progress.accept(method+" "+label+" "+x))
                            :SemiDirectScf.solveWithCache(ghost,requested,options,x->progress.accept(method+" "+label+" "+x),canonical.cache());
                    status=run.convergenceStatus().name();receipt=run.receiptHash();iterations=run.iterations();
                    energy=run.convergedState().isPresent()?OptionalDouble.of(run.convergedState().orElseThrow().totalHartree()):OptionalDouble.empty();
                } else {
                    var options=new PbeScf.Options(cacheDirectory);
                    var run=ghost==null?PbeScf.solve(physical,basis,options,x->progress.accept(method+" "+label+" "+x))
                            :PbeScf.solveWithCache(ghost,options,x->progress.accept(method+" "+label+" "+x),canonical.cache());
                    status=run.convergenceStatus().name();receipt=run.receiptHash();iterations=run.iterations();
                    energy=run.convergedState().isPresent()?OptionalDouble.of(run.convergedState().orElseThrow().totalHartree()):OptionalDouble.empty();
                }
                components.add(new Component(method,label,status,energy,receipt,IntegralMatrixData.systemHash(physical),
                        EriDiskCache.identity(requested),canonical.cache().identity(),permutation,
                        !(method.equals("RHF")&&role==0&&canonical.generated()),mapping,iterations,""));
            } catch(ArithmeticException | IllegalArgumentException | IOException exception) {
                String status=exception instanceof IOException?"IO_FAILURE":exception instanceof ArithmeticException?"NUMERICAL_FAILURE":"UNSUPPORTED_SYSTEM";
                String reason=method+"/"+label+":"+status+":"+exception.getMessage();failures.add(reason);
                components.add(new Component(method,label,status,OptionalDouble.empty(),ContentHash.sha256(reason),
                        IntegralMatrixData.systemHash(physical),EriDiskCache.identity(requested),canonical.cache().identity(),permutation,true,mapping,List.of(),reason));
            }
        }
        return assemble(pair,components,dispersion,d3Receipts,failures);
    }

    static Result assemble(FragmentPair pair,List<Component> components,OptionalDouble dispersion,
                           List<String> d3Receipts,List<String> failures) {
        if(components.size()!=6)throw new IllegalArgumentException("Six explicit CP components required");
        OptionalDouble[] cp={OptionalDouble.empty(),OptionalDouble.empty()};
        for(int method=0;method<2;method++) {
            boolean available=true;
            for(int role=0;role<3;role++) {
                var c=components.get(method*3+role);
                if(!c.method().equals(method==0?"RHF":"PBE")||!c.role().equals(List.of("AB","A_GHOST_B","B_GHOST_A").get(role)))throw new IllegalArgumentException("CP component order");
                var system=role==0?pair.complex():role==1?pair.a().system():pair.b().system();
                OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(system),c.systemHash(),"fragment component system");
                available&=c.scfStatus().equals("CONVERGED")&&c.energyHartree().isPresent();
                if(c.energyHartree().isPresent()&&!Double.isFinite(c.energyHartree().getAsDouble()))throw new IllegalArgumentException("Nonfinite component energy");
            }
            if(available)cp[method]=OptionalDouble.of(InteractionEnergyCalculator.subtract(components.get(method*3).energyHartree().orElseThrow(),
                    components.get(method*3+1).energyHartree().orElseThrow(),components.get(method*3+2).energyHartree().orElseThrow()));
        }
        if(dispersion.isPresent()&&!Double.isFinite(dispersion.getAsDouble()))throw new IllegalArgumentException("Nonfinite D3 delta");
        var total=cp[1].isPresent()&&dispersion.isPresent()?OptionalDouble.of(cp[1].getAsDouble()+dispersion.getAsDouble()):OptionalDouble.empty();
        var hash=ContentHash.accumulator().line(PROTOCOL).line(pair.a().id()).line(pair.b().id());
        for(var c:components)hash.line(c.method()).line(c.role()).line(c.scfStatus()).line(c.receiptHash()).line(c.permutationProvenance());
        for(String receipt:d3Receipts)hash.line(receipt);
        for(var energy:List.of(cp[0],cp[1],dispersion,total))hash.line(energy.isPresent()?ContentHash.number(energy.getAsDouble()):"UNAVAILABLE");
        return new Result(cp[0],cp[1],dispersion,total,components,d3Receipts,failures,hash.finish());
    }
}
