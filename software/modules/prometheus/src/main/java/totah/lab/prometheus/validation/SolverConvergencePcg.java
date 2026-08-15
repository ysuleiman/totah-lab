package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.numerics.LinearOperator;
import totah.lab.prometheus.numerics.Preconditioner;

/** Instrumented fixed-preconditioner CG used only by the locked convergence study. */
final class SolverConvergencePcg {
    enum Mode { BASELINE_RECURSIVE_PCG, PCG_TRUE_RESIDUAL, PCG_TRUE_RESIDUAL_COMPENSATED }

    Result solve(LinearOperator operator,Preconditioner preconditioner,double[] rhs,Mode mode){
        int n=rhs.length;if(operator.dimension()!=n||preconditioner.dimension()!=n)throw new IllegalArgumentException("dimension mismatch");
        boolean compensated=mode==Mode.PCG_TRUE_RESIDUAL_COMPENSATED;double[] x=new double[n],recursive=rhs.clone(),z=preconditioner.apply(recursive),p=z.clone();double rz=dot(recursive,z,compensated),rhsNorm=norm(rhs,compensated),threshold=Math.max(1e-14,1e-12*rhsNorm);List<Trace> trace=new ArrayList<>();List<double[]> directions=new ArrayList<>(),actions=new ArrayList<>();double maxConjugacy=0;int applications=0;
        for(int iteration=1;iteration<=200;iteration++){
            double[] ap=operator.apply(p);applications++;double curvature=dot(p,ap,compensated);if(!(curvature>0)||!Double.isFinite(curvature))return new Result(x,iteration-1,applications,false,true,Double.NaN,Double.NaN,Double.NaN,maxConjugacy,List.copyOf(trace));
            for(int j=0;j<directions.size();j++){double cross=Math.abs(dot(p,actions.get(j),compensated));double scale=Math.sqrt(curvature*dot(directions.get(j),actions.get(j),compensated));if(scale>0)maxConjugacy=Math.max(maxConjugacy,cross/scale);}
            directions.add(p.clone());actions.add(ap.clone());double alpha=rz/curvature;for(int i=0;i<n;i++){x[i]+=alpha*p[i];recursive[i]-=alpha*ap[i];}
            double recursiveNorm=norm(recursive,compensated);double[] ax=operator.apply(x);applications++;double[] trueResidual=new double[n],gap=new double[n];for(int i=0;i<n;i++){trueResidual[i]=rhs[i]-ax[i];gap[i]=recursive[i]-trueResidual[i];}double trueNorm=norm(trueResidual,compensated),gapNorm=norm(gap,compensated);trace.add(new Trace(iteration,recursiveNorm,trueNorm,gapNorm));
            double stopping=mode==Mode.BASELINE_RECURSIVE_PCG?recursiveNorm:trueNorm;if(stopping<=threshold)return new Result(x,iteration,applications,true,false,recursiveNorm,trueNorm,gapNorm,maxConjugacy,List.copyOf(trace));
            double[] active=mode==Mode.BASELINE_RECURSIVE_PCG?recursive:trueResidual;if(mode!=Mode.BASELINE_RECURSIVE_PCG)recursive=trueResidual;
            double[] nextZ=preconditioner.apply(active);double nextRz=dot(active,nextZ,compensated);if(!(nextRz>=0)||!Double.isFinite(nextRz))return new Result(x,iteration,applications,false,true,recursiveNorm,trueNorm,gapNorm,maxConjugacy,List.copyOf(trace));double beta=nextRz/rz;for(int i=0;i<n;i++)p[i]=nextZ[i]+beta*p[i];z=nextZ;rz=nextRz;
        }
        Trace last=trace.getLast();return new Result(x,200,applications,false,false,last.recursiveResidual,last.trueResidual,last.residualGap,maxConjugacy,List.copyOf(trace));
    }

    static double dot(double[] a,double[] b,boolean compensated){if(!compensated){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}double sum=0,correction=0;for(int i=0;i<a.length;i++){double value=a[i]*b[i],next=sum+value;if(Math.abs(sum)>=Math.abs(value))correction+=(sum-next)+value;else correction+=(value-next)+sum;sum=next;}return sum+correction;}
    static double norm(double[] values,boolean compensated){return Math.sqrt(Math.max(0,dot(values,values,compensated)));}
    record Trace(int iteration,double recursiveResidual,double trueResidual,double residualGap){}
    record Result(double[] solution,int iterations,int operatorApplications,boolean converged,boolean breakdown,double recursiveResidual,double trueResidual,double residualGap,double maximumConjugacyLoss,List<Trace> trace){Result{solution=solution.clone();trace=List.copyOf(trace);}}
}
