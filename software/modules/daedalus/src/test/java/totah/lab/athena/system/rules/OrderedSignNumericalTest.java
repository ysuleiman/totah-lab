package totah.lab.athena.system.rules;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class OrderedSignNumericalTest {
 @ParameterizedTest @ValueSource(strings={"unit-positive","unit-negative","swap-ligands","translation","zero","negative-zero","tiny","underflow","nonfinite","overflow-subtraction","overflow-cross","overflow-dot","wrong-size"})
 void exactOrderedProfile(String name){double[][] p={{0,0,0},{1,0,0},{0,1,0},{0,0,1}};Double expected=1.;switch(name){case "unit-negative"->{p[3][2]=-1;expected=-1.;}case "swap-ligands"->{var x=p[1];p[1]=p[2];p[2]=x;expected=-1.;}case "translation"->{for(var a:p)for(int j=0;j<3;j++)a[j]+=8;}case "zero"->{p[3][2]=0;expected=0.;}case "negative-zero"->{p[3][2]=-0.;expected=0.;}case "tiny"->{p[3][2]=Double.MIN_VALUE;expected=Double.MIN_VALUE;}case "underflow"->{p[1][0]=1e-200;p[2][1]=1e-200;expected=0.;}case "nonfinite"->{p[1][0]=Double.NaN;expected=null;}case "overflow-subtraction"->{p[0][0]=-Double.MAX_VALUE;p[1][0]=Double.MAX_VALUE;expected=null;}case "overflow-cross"->{p[2][1]=Double.MAX_VALUE;p[3][2]=2;expected=null;}case "overflow-dot"->{p[1][0]=Double.MAX_VALUE;p[3][2]=2;expected=null;}case "wrong-size"->{p=new double[3][3];expected=null;}}Double got=OrderedChiralSign.volume(p);if(expected==null){assertNull(got);return;}assertNotNull(got);assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(got));assertEquals(expected>0?"POSITIVE":expected<0?"NEGATIVE":"ZERO",OrderedChiralSign.sign(got));}
}
