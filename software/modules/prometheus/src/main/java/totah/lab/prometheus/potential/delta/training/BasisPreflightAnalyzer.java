package totah.lab.prometheus.potential.delta.training;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import totah.lab.prometheus.potential.delta.training.BasisPreflightResult.Classification;
import totah.lab.prometheus.potential.delta.training.BasisPreflightResult.ColumnAssessment;

/** Geometry/derivative-only structural classifier used before any target fit. */
public final class BasisPreflightAnalyzer {
    public static final double ENERGY_NULL_TOLERANCE=1e-12,FORCE_NULL_TOLERANCE=1e-10,DUPLICATE_TOLERANCE=1e-10;
    public BasisPreflightResult analyze(double[][] energyColumns,double[][] derivativeColumns,boolean observableRankValid,boolean derivativeConsistencyPass,boolean invariancePass,int dimensionCeiling){
        requireRectangular(energyColumns);requireRectangular(derivativeColumns);int columns=energyColumns[0].length;if(derivativeColumns[0].length!=columns)throw new IllegalArgumentException("matrix column counts differ");
        double[] maxEnergy=new double[columns],maxDerivative=new double[columns];boolean[] nulls=new boolean[columns];Map<Integer,Integer>duplicates=new HashMap<>();Classification[]classification=new Classification[columns];
        for(int j=0;j<columns;j++){maxEnergy[j]=maximum(energyColumns,j);maxDerivative[j]=maximum(derivativeColumns,j);nulls[j]=maxEnergy[j]<=ENERGY_NULL_TOLERANCE&&maxDerivative[j]<=FORCE_NULL_TOLERANCE;classification[j]=nulls[j]?Classification.STRUCTURALLY_NULL:Classification.OBSERVABLE;}
        for(int j=0;j<columns;j++)if(!nulls[j])for(int prior=0;prior<j;prior++)if(!nulls[prior]&&!duplicates.containsKey(prior)&&scalarEquivalent(energyColumns,derivativeColumns,prior,j)){duplicates.put(j,prior);classification[j]=Classification.STRUCTURALLY_DUPLICATE;break;}
        for(int j=0;j<columns;j++)if(classification[j]==Classification.OBSERVABLE)for(int k=j+1;k<columns;k++)if(classification[k]==Classification.OBSERVABLE&&Math.abs(correlation(energyColumns,derivativeColumns,j,k))>.995){classification[j]=Classification.NONZERO_BUT_COLLINEAR;classification[k]=Classification.NONZERO_BUT_COLLINEAR;}
        List<ColumnAssessment>assessments=new ArrayList<>();for(int j=0;j<columns;j++)assessments.add(new ColumnAssessment(j,classification[j],maxEnergy[j],maxDerivative[j]));return new BasisPreflightResult(assessments,duplicates,observableRankValid,derivativeConsistencyPass,invariancePass,columns<=dimensionCeiling);
    }
    private static boolean scalarEquivalent(double[][]e,double[][]f,int a,int b){double pivotA=0,pivotB=0;for(double[][]m:new double[][][]{e,f})for(double[]row:m)if(Math.abs(row[a])>Math.abs(pivotA)){pivotA=row[a];pivotB=row[b];}if(Math.abs(pivotA)<=ENERGY_NULL_TOLERANCE)return false;double scale=pivotB/pivotA,max=0,den=0;for(double[][]m:new double[][][]{e,f})for(double[]row:m){max=Math.max(max,Math.abs(row[b]-scale*row[a]));den=Math.max(den,Math.max(Math.abs(row[b]),Math.abs(scale*row[a])));}return max/Math.max(den,Double.MIN_NORMAL)<=DUPLICATE_TOLERANCE;}
    private static double correlation(double[][]e,double[][]f,int a,int b){int n=e.length+f.length;double ma=0,mb=0;for(double[][]m:new double[][][]{e,f})for(double[]r:m){ma+=r[a];mb+=r[b];}ma/=n;mb/=n;double aa=0,bb=0,ab=0;for(double[][]m:new double[][][]{e,f})for(double[]r:m){double x=r[a]-ma,y=r[b]-mb;aa+=x*x;bb+=y*y;ab+=x*y;}return aa==0||bb==0?0:ab/Math.sqrt(aa*bb);}
    private static double maximum(double[][]m,int c){double max=0;for(double[]r:m)max=Math.max(max,Math.abs(r[c]));return max;}private static void requireRectangular(double[][]m){if(m==null||m.length==0||m[0].length==0)throw new IllegalArgumentException("nonempty matrix required");int n=m[0].length;for(double[]r:m)if(r==null||r.length!=n)throw new IllegalArgumentException("rectangular matrix required");}
}
