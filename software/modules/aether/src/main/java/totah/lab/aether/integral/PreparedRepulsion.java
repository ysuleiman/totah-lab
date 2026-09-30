package totah.lab.aether.integral;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.CartesianAngularMomentum;

/** Thread-confined exact ERI evaluator. Retains primitive pair expansions, never quartet integrals. */
public final class PreparedRepulsion {
    private final List<ContractedGaussian> basis;
    private final CartesianIntegrals.Pair[][] pairs;
    private final double[][] normalizations;
    private final ElectronRepulsionIntegral.PreparedPair[][] sPairs;
    private final CartesianIntegrals.CoulombWorkspace workspace=new CartesianIntegrals.CoulombWorkspace();
    private final int[][] pairContextIds;
    private final long[] contextKeys=new long[8192];
    private final int[] contextStamps=new int[8192],contextBuffers=new int[8192];
    private int contextGeneration=1,contextCount;
    private boolean blockActive;
    private final java.util.List<CartesianIntegrals.CoulombWorkspace> buffers=new java.util.ArrayList<>();
    private int nextBuffer;
    private record ProductContext(double exponentSum,double x,double y,double z) {}
    private PreparedRepulsion(PreparedRepulsion shared) {
        basis=shared.basis;pairs=shared.pairs;normalizations=shared.normalizations;sPairs=shared.sPairs;pairContextIds=shared.pairContextIds;
    }
    /** Shares immutable quadratic pair data; owns an independent bounded recurrence workspace. */
    public PreparedRepulsion newSession(){return new PreparedRepulsion(this);}
    /** Starts one shell block; exact auxiliary values may be reused only inside this bounded block. */
    public void beginBlock(){clearContexts();blockActive=true;}
    public void endBlock(){clearContexts();blockActive=false;}
    private void clearContexts() {
        if(++contextGeneration==0){java.util.Arrays.fill(contextStamps,0);contextGeneration=1;}
        contextCount=0;nextBuffer=0;
    }
    private int contextSlot(long key) {
        long mixed=key*0x9E3779B97F4A7C15L;int slot=(int)(mixed^(mixed>>>32))&(contextKeys.length-1);
        while(contextStamps[slot]==contextGeneration&&contextKeys[slot]!=key)slot=(slot+1)&(contextKeys.length-1);
        return slot;
    }
    private CartesianIntegrals.CoulombWorkspace auxiliary(int left,int right) {
        if(!blockActive){workspace.reset();return workspace;}
        long key=((long)left<<32)|(right&0xffffffffL);int slot=contextSlot(key);
        if(contextStamps[slot]==contextGeneration)return buffers.get(contextBuffers[slot]);
        // Exact key lookup, bounded to the historical 4096 contexts at load factor <= 1/2.
        if(contextCount==4096){clearContexts();slot=contextSlot(key);}
        if(nextBuffer==buffers.size())buffers.add(new CartesianIntegrals.CoulombWorkspace());
        var found=buffers.get(nextBuffer);found.reset();
        contextKeys[slot]=key;contextStamps[slot]=contextGeneration;contextBuffers[slot]=nextBuffer++;
        contextCount++;return found;
    }
    public PreparedRepulsion(List<ContractedGaussian> functions) {
        basis=List.copyOf(functions);int n=basis.size();
        pairs=new CartesianIntegrals.Pair[Math.toIntExact((long)n*(n+1)/2)][];
        normalizations=new double[n][];sPairs=new ElectronRepulsionIntegral.PreparedPair[pairs.length][];
        pairContextIds=new int[pairs.length][];
        var identities=new java.util.HashMap<ProductContext,Integer>();
        for(int i=0;i<n;i++) {
            var a=basis.get(i);var la=a.angularMomentum();
            if(la.x()+la.y()+la.z()>2)throw new IllegalArgumentException("Only s/p/d supported");
            normalizations[i]=new double[a.terms().size()];
            for(int p=0;p<normalizations[i].length;p++)normalizations[i][p]=la.normalization(a.terms().get(p).primitive());
            for(int j=0;j<=i;j++) {
                var b=basis.get(j);var list=new CartesianIntegrals.Pair[a.terms().size()*b.terms().size()];
                for(int p=0;p<a.terms().size();p++)for(int q=0;q<b.terms().size();q++)
                    list[p*b.terms().size()+q]=new CartesianIntegrals.Pair(a.terms().get(p).primitive(),CartesianIntegrals.powers(la),b.terms().get(q).primitive(),CartesianIntegrals.powers(b.angularMomentum()));
                pairs[index(i,j)]=list;var ids=new int[list.length];
                for(int k=0;k<list.length;k++) {
                    var pair=list[k];var key=new ProductContext(pair.p,pair.center[0],pair.center[1],pair.center[2]);
                    ids[k]=identities.computeIfAbsent(key,unused->identities.size());
                }
                pairContextIds[index(i,j)]=ids;
                if(la==CartesianAngularMomentum.S&&b.angularMomentum()==CartesianAngularMomentum.S) {
                    var sp=new ElectronRepulsionIntegral.PreparedPair[list.length];
                    for(int p=0;p<a.terms().size();p++)for(int q=0;q<b.terms().size();q++)sp[p*b.terms().size()+q]=new ElectronRepulsionIntegral.PreparedPair(a.terms().get(p).primitive(),b.terms().get(q).primitive());
                    sPairs[index(i,j)]=sp;
                }
            }
        }
    }
    /** Requires the same canonical quartet ordering as packed reference storage. */
    public double get(int i,int j,int k,int l) {
        if(i<j||k<l||index(i,j)<index(k,l))throw new IllegalArgumentException("Noncanonical quartet");
        var a=basis.get(i);var b=basis.get(j);var c=basis.get(k);var d=basis.get(l);
        // Preserve the historical logarithmic/underflow-safe all-s formula exactly.
        if(a.angularMomentum()==CartesianAngularMomentum.S&&b.angularMomentum()==CartesianAngularMomentum.S
                &&c.angularMomentum()==CartesianAngularMomentum.S&&d.angularMomentum()==CartesianAngularMomentum.S)
            return GaussianContraction.normalizedIndexed(a,b,c,d,(p,q,r,s)->ElectronRepulsionIntegral.between(sPairs[index(i,j)][p*b.terms().size()+q],sPairs[index(k,l)][r*d.terms().size()+s]));
        var ab=pairs[index(i,j)];var cd=pairs[index(k,l)];
        var abIds=pairContextIds[index(i,j)];var cdIds=pairContextIds[index(k,l)];
        return GaussianContraction.normalizedIndexed(a,b,c,d,(p,q,r,s)->CartesianIntegrals.repulsion(
                ab[p*b.terms().size()+q],cd[r*d.terms().size()+s],normalizations[i][p],normalizations[j][q],normalizations[k][r],normalizations[l][s],auxiliary(abIds[p*b.terms().size()+q],cdIds[r*d.terms().size()+s])));
    }
    private static int index(int i,int j){return Math.toIntExact((long)i*(i+1)/2+j);}
}
