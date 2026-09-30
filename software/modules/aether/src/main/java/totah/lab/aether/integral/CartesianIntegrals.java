package totah.lab.aether.integral;

import java.util.List;
import totah.lab.aether.basis.CartesianAngularMomentum;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.model.NuclearCenter;

/** McMurchie–Davidson Hermite expansion and Coulomb recurrence, restricted to Cartesian s/p AOs. */
public final class CartesianIntegrals {
    private CartesianIntegrals() {}
    public static double overlap(PrimitiveGaussian a, CartesianAngularMomentum la, PrimitiveGaussian b, CartesianAngularMomentum lb) {
        return finite(la.normalization(a)*lb.normalization(b)*rawOverlap(a,powers(la),b,powers(lb)));
    }
    public static double unnormalizedOverlap(List<GaussianTerm> a, CartesianAngularMomentum la,
                                             List<GaussianTerm> b, CartesianAngularMomentum lb) {
        return GaussianContraction.sum(a,b,(x,y)->overlap(x,la,y,lb));
    }
    public static double overlap(ContractedGaussian a, ContractedGaussian b) {
        return GaussianContraction.normalized(a,b,(x,y)->overlap(x,a.angularMomentum(),y,b.angularMomentum()));
    }
    public static double kinetic(ContractedGaussian a, ContractedGaussian b) {
        return GaussianContraction.normalized(a,b,(x,y)->kinetic(x,a.angularMomentum(),y,b.angularMomentum()));
    }
    public static double kinetic(PrimitiveGaussian a, CartesianAngularMomentum la, PrimitiveGaussian b, CartesianAngularMomentum lb) {
        int[] i=powers(la), j=powers(lb); double beta=b.exponent();
        double value=beta*(2*(j[0]+j[1]+j[2])+3)*rawOverlap(a,i,b,j);
        for(int axis=0;axis<3;axis++) {
            int old=j[axis]; j[axis]=old+2;
            value-=2*beta*beta*rawOverlap(a,i,b,j);
            if(old>=2) { j[axis]=old-2; value-=0.5*old*(old-1)*rawOverlap(a,i,b,j); }
            j[axis]=old;
        }
        return finite(value*la.normalization(a)*lb.normalization(b));
    }
    public static double attraction(ContractedGaussian a, ContractedGaussian b, List<NuclearCenter> nuclei) {
        return GaussianContraction.normalized(a,b,(x,y)->attraction(x,a.angularMomentum(),y,b.angularMomentum(),nuclei));
    }
    public static double attraction(PrimitiveGaussian a, CartesianAngularMomentum la, PrimitiveGaussian b,
                                    CartesianAngularMomentum lb, List<NuclearCenter> nuclei) {
        if(nuclei.isEmpty()) throw new IllegalArgumentException("At least one nucleus is required");
        var pair=new Pair(a,powers(la),b,powers(lb)); double value=0;
        for(var nucleus:nuclei) {
            var c=nucleus.centerBohr(); double[] pc={pair.center[0]-c.x(),pair.center[1]-c.y(),pair.center[2]-c.z()};
            double sum=0;
            for(int t=0;t<pair.e[0].length;t++) for(int u=0;u<pair.e[1].length;u++) for(int v=0;v<pair.e[2].length;v++)
                sum+=pair.e[0][t]*pair.e[1][u]*pair.e[2][v]*r(t,u,v,0,pair.p,pc);
            value-=nucleus.charge()*2*StrictMath.PI/pair.p*sum;
        }
        return finite(value*la.normalization(a)*lb.normalization(b));
    }
    public static double repulsion(ContractedGaussian a, ContractedGaussian b, ContractedGaussian c, ContractedGaussian d) {
        return GaussianContraction.normalized(a,b,c,d,(w,x,y,z)->repulsion(w,a.angularMomentum(),x,b.angularMomentum(),y,c.angularMomentum(),z,d.angularMomentum()));
    }
    public static double repulsion(PrimitiveGaussian a, CartesianAngularMomentum la, PrimitiveGaussian b, CartesianAngularMomentum lb,
                                   PrimitiveGaussian c, CartesianAngularMomentum lc, PrimitiveGaussian d, CartesianAngularMomentum ld) {
        var ab=new Pair(a,powers(la),b,powers(lb)); var cd=new Pair(c,powers(lc),d,powers(ld));
        return repulsion(ab,cd,la.normalization(a),lb.normalization(b),lc.normalization(c),ld.normalization(d),null);
    }
    static double repulsion(Pair ab,Pair cd,double na,double nb,double nc,double nd,CoulombWorkspace workspace) {
        double rho=finite(ab.p*cd.p/(ab.p+cd.p));
        double[] pq=workspace==null?new double[3]:workspace.offset;
        pq[0]=ab.center[0]-cd.center[0];pq[1]=ab.center[1]-cd.center[1];pq[2]=ab.center[2]-cd.center[2];
        double sum=0;
        for(int t=0;t<ab.e[0].length;t++) for(int u=0;u<ab.e[1].length;u++) for(int v=0;v<ab.e[2].length;v++)
            for(int x=0;x<cd.e[0].length;x++) for(int y=0;y<cd.e[1].length;y++) for(int z=0;z<cd.e[2].length;z++) {
                double coefficient=ab.e[0][t]*ab.e[1][u]*ab.e[2][v]*cd.e[0][x]*cd.e[1][y]*cd.e[2][z];
                sum+=coefficient*((x+y+z)%2==0?1:-1)*r(t+x,u+y,v+z,0,rho,pq,workspace);
            }
        double prefactor=2*StrictMath.pow(StrictMath.PI,2.5)/(ab.p*cd.p*StrictMath.sqrt(ab.p+cd.p));
        return finite(prefactor*sum*na*nb*nc*nd);
    }
    private static double rawOverlap(PrimitiveGaussian a,int[] la,PrimitiveGaussian b,int[] lb) {
        var pair=new Pair(a,la,b,lb);
        return finite(pair.e[0][0]*pair.e[1][0]*pair.e[2][0]*StrictMath.pow(StrictMath.PI/pair.p,1.5));
    }
    static int[] powers(CartesianAngularMomentum l) { return new int[]{l.x(),l.y(),l.z()}; }
    private static double finite(double value) { return GaussianContraction.requireFinite(value); }
    static final class Pair {
        final double p;
        final double[] center=new double[3];
        final double[][] e=new double[3][];
        Pair(PrimitiveGaussian a,int[] la,PrimitiveGaussian b,int[] lb) {
            p=finite(a.exponent()+b.exponent());
            double[] ac={a.centerBohr().x(),a.centerBohr().y(),a.centerBohr().z()};
            double[] bc={b.centerBohr().x(),b.centerBohr().y(),b.centerBohr().z()};
            for(int axis=0;axis<3;axis++) {
                center[axis]=finite((a.exponent()/p)*ac[axis]+(b.exponent()/p)*bc[axis]);
                e[axis]=new double[la[axis]+lb[axis]+1];
                for(int t=0;t<e[axis].length;t++) e[axis][t]=hermite(la[axis],lb[axis],t,ac[axis]-bc[axis],a.exponent(),b.exponent());
            }
        }
    }
    private static double hermite(int i,int j,int t,double q,double a,double b) {
        if(t<0 || t>i+j) return 0;
        double p=a+b, mu=a*b/p;
        if(i==0 && j==0) return StrictMath.exp(-mu*q*q);
        if(j==0) return hermite(i-1,j,t-1,q,a,b)/(2*p)-b*q/p*hermite(i-1,j,t,q,a,b)+(t+1)*hermite(i-1,j,t+1,q,a,b);
        return hermite(i,j-1,t-1,q,a,b)/(2*p)+a*q/p*hermite(i,j-1,t,q,a,b)+(t+1)*hermite(i,j-1,t+1,q,a,b);
    }
    /** R^n_000=(-2p)^n F_n(p|PC|²); successive Cartesian derivatives raise n. */
    private static double r(int t,int u,int v,int n,double p,double[] pc) {
        return r(t,u,v,n,p,pc,null);
    }
    private static double r(int t,int u,int v,int n,double p,double[] pc,CoulombWorkspace workspace) {
        int slot=workspace==null?0:CoulombWorkspace.SLOTS[(((t*9+u)*9+v)*9+n)];
        if(workspace!=null&&workspace.stamps[slot]==workspace.generation)return workspace.values[slot];
        double value;
        if(t==0 && u==0 && v==0) value=StrictMath.pow(-2*p,n)*BoysFunction.value(n,finite(p*(pc[0]*pc[0]+pc[1]*pc[1]+pc[2]*pc[2])));
        else if(t>0) value=(t>1?(t-1)*r(t-2,u,v,n+1,p,pc,workspace):0)+pc[0]*r(t-1,u,v,n+1,p,pc,workspace);
        else if(u>0) value=(u>1?(u-1)*r(t,u-2,v,n+1,p,pc,workspace):0)+pc[1]*r(t,u-1,v,n+1,p,pc,workspace);
        else value=(v>1?(v-1)*r(t,u,v-2,n+1,p,pc,workspace):0)+pc[2]*r(t,u,v-1,n+1,p,pc,workspace);
        if(workspace!=null){workspace.values[slot]=value;workspace.stamps[slot]=workspace.generation;}
        return value;
    }
    /** Fixed workspace covers four Cartesian d functions (total degree at most eight). */
    static final class CoulombWorkspace {
        static final int[] SLOTS=slots();
        private static int[] slots(){
            int[] slots=new int[9*9*9*9];java.util.Arrays.fill(slots,-1);int next=0;
            for(int t=0;t<=8;t++)for(int u=0;u<=8-t;u++)for(int v=0;v<=8-t-u;v++)for(int n=0;n<=8-t-u-v;n++)slots[((t*9+u)*9+v)*9+n]=next++;
            return slots;
        }
        final double[] offset=new double[3];
        final double[] values=new double[495];final int[] stamps=new int[values.length];int generation=1;
        void reset(){if(++generation==0){java.util.Arrays.fill(stamps,0);generation=1;}}
    }
}
