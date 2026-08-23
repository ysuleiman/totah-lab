package totah.lab.prometheus.potential;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.delta.basis.CompositeManyBodyBasis;
import totah.lab.prometheus.potential.delta.basis.FourBodyBasis;
import totah.lab.prometheus.potential.delta.basis.ManyBodyBasis;
import totah.lab.prometheus.potential.delta.basis.ThreeBodyBasis;
import totah.lab.prometheus.potential.delta.basis.TwoBodyBasis;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironment;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironmentBuilder;
import totah.lab.prometheus.potential.delta.environment.SpeciesChannel;
import totah.lab.prometheus.potential.delta.model.DeltaModelIdentity;
import totah.lab.prometheus.potential.delta.model.DeltaModelParameters;
import totah.lab.prometheus.potential.delta.model.LinearDeltaModel;

class ConservativePotentialTest {
    @Test void analyticForcesMatchEnergyDifferencesAndRespectRigidMotions() {
        LocalEnvironment environment=new LocalEnvironmentBuilder().types(SpeciesChannel.S_THIOL,SpeciesChannel.C_SP3,SpeciesChannel.H_S).bond(0,1).bond(0,2).build();
        TwoBodyBasis two=new TwoBodyBasis(environment,List.of(new TwoBodyBasis.Channel(SpeciesChannel.C_SP3,SpeciesChannel.S_THIOL,TwoBodyBasis.TopologyClass.ONE_TWO),new TwoBodyBasis.Channel(SpeciesChannel.S_THIOL,SpeciesChannel.H_S,TwoBodyBasis.TopologyClass.ONE_TWO)));
        ThreeBodyBasis.Neighbor carbon=new ThreeBodyBasis.Neighbor(SpeciesChannel.C_SP3,1),hydrogen=new ThreeBodyBasis.Neighbor(SpeciesChannel.H_S,1);
        ThreeBodyBasis three=new ThreeBodyBasis(environment,List.of(new ThreeBodyBasis.Channel(SpeciesChannel.S_THIOL,carbon,hydrogen)));
        ManyBodyBasis basis=new CompositeManyBodyBasis(List.of(two,three));double[] coefficients=new double[basis.dimension()];for(int i=0;i<coefficients.length;i++)coefficients[i]=(i+1)*0.031;
        LinearDeltaModel model=new LinearDeltaModel(basis,new DeltaModelParameters(coefficients),new DeltaModelIdentity("TEST","b","t","f","test"));
        double[][] xyz={{0.2,-0.3,0.1},{1.7,0.1,-0.2},{-0.4,1.1,0.3}};PotentialEvaluation reference=model.evaluate(new QuantumCoordinates(xyz));double h=1e-5,max=0,sum=0;int n=0;
        for(int atom=0;atom<3;atom++)for(int axis=0;axis<3;axis++){double[][] plus=copy(xyz),minus=copy(xyz);plus[atom][axis]+=h;minus[atom][axis]-=h;double finite=-(model.evaluate(new QuantumCoordinates(plus)).energy()-model.evaluate(new QuantumCoordinates(minus)).energy())/(2*h);double difference=Math.abs(finite-reference.forces()[atom][axis]);max=Math.max(max,difference);sum+=difference*difference;n++;}
        assertThat(max).isLessThanOrEqualTo(1e-4);assertThat(Math.sqrt(sum/n)).isLessThanOrEqualTo(1e-5);
        double[][] translated=copy(xyz);for(double[] row:translated){row[0]+=1.1;row[1]-=.7;row[2]+=2.3;}PotentialEvaluation moved=model.evaluate(new QuantumCoordinates(translated));assertThat(moved.energy()).isCloseTo(reference.energy(),org.assertj.core.data.Offset.offset(1e-10));
        double[] net=new double[3];for(double[] force:reference.forces())for(int axis=0;axis<3;axis++)net[axis]+=force[axis];assertThat(Math.sqrt(net[0]*net[0]+net[1]*net[1]+net[2]*net[2])).isLessThanOrEqualTo(1e-7);
        for(double[] angles:new double[][]{{17,31,53},{71,11,29},{113,47,5}}){double[][] rotation=rotation(angles);PotentialEvaluation rotated=model.evaluate(new QuantumCoordinates(transform(xyz,rotation)));assertThat(rotated.energy()).isCloseTo(reference.energy(),org.assertj.core.data.Offset.offset(1e-10));double[][] expected=transform(reference.forces(),rotation),actual=rotated.forces();for(int atom=0;atom<3;atom++)for(int axis=0;axis<3;axis++)assertThat(actual[atom][axis]).isCloseTo(expected[atom][axis],org.assertj.core.data.Offset.offset(1e-8));}
    }
    @Test void fourBodyAnalyticGradientMatchesFiniteDifference() {
        ManyBodyBasis basis=new FourBodyBasis(List.of(new FourBodyBasis.Motif(FourBodyBasis.Kind.TORSION_FOURIER,0,1,2,3),new FourBodyBasis.Motif(FourBodyBasis.Kind.ANGLE_PAIR,0,1,2,3)));
        double[] coefficients=new double[basis.dimension()];for(int i=0;i<coefficients.length;i++)coefficients[i]=.013*(i+1);LinearDeltaModel model=new LinearDeltaModel(basis,new DeltaModelParameters(coefficients),new DeltaModelIdentity("4B","b","t","f","test"));double[][] xyz={{0,0,0},{1.2,.1,0},{1.8,1.1,.3},{2.4,1.4,1.2}};PotentialEvaluation evaluation=model.evaluate(new QuantumCoordinates(xyz));double h=1e-5,max=0,sum=0;int n=0;
        for(int atom=0;atom<4;atom++)for(int axis=0;axis<3;axis++){double[][]plus=copy(xyz),minus=copy(xyz);plus[atom][axis]+=h;minus[atom][axis]-=h;double fd=-(model.evaluate(new QuantumCoordinates(plus)).energy()-model.evaluate(new QuantumCoordinates(minus)).energy())/(2*h);double d=Math.abs(fd-evaluation.forces()[atom][axis]);max=Math.max(max,d);sum+=d*d;n++;}assertThat(max).isLessThanOrEqualTo(1e-4);assertThat(Math.sqrt(sum/n)).isLessThanOrEqualTo(1e-5);
    }
    private static double[][] copy(double[][] value){return java.util.Arrays.stream(value).map(double[]::clone).toArray(double[][]::new);}
    private static double[][] transform(double[][] points,double[][]r){double[][]out=new double[points.length][3];for(int i=0;i<points.length;i++)for(int a=0;a<3;a++)for(int b=0;b<3;b++)out[i][a]+=r[a][b]*points[i][b];return out;}
    private static double[][] rotation(double[] degrees){double a=Math.toRadians(degrees[0]),b=Math.toRadians(degrees[1]),c=Math.toRadians(degrees[2]);double[][]rx={{1,0,0},{0,Math.cos(a),-Math.sin(a)},{0,Math.sin(a),Math.cos(a)}},ry={{Math.cos(b),0,Math.sin(b)},{0,1,0},{-Math.sin(b),0,Math.cos(b)}},rz={{Math.cos(c),-Math.sin(c),0},{Math.sin(c),Math.cos(c),0},{0,0,1}};return multiply(rz,multiply(ry,rx));}
    private static double[][] multiply(double[][]a,double[][]b){double[][]out=new double[3][3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++)out[i][j]+=a[i][k]*b[k][j];return out;}
}
