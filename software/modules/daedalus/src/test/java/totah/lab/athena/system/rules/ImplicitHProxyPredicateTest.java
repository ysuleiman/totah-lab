package totah.lab.athena.system.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric engineering oracle only; no assertion of qualified SP3 chemistry. */
class ImplicitHProxyPredicateTest {
    @ParameterizedTest @CsvSource({"3,0,0,true","3.5,0,0,true","3,25,0,true","3,0,90,true","3.5,25,90,true","3.5000000000000004,0,0,false","3,25.000000000000004,0,false","3,0,90.00000000000001,false","3,26,0,false","3,0,91,false"})
    void inclusiveSourceBounds(double r,double d,double a,boolean expected){assertEquals(expected,ImplicitHProxyGeometry.predicate(r,d,a));}
    @Test void minimumNotMaximum(){assertEquals(0,ImplicitHProxyGeometry.minimum(List.of(0.0,109.5)));assertEquals(0,ImplicitHProxyGeometry.minimum(List.of(109.5,0.0)));}
    @Test void missingOrInvalidAnglesUnknown(){for(var angles:List.of(List.<Double>of(),Arrays.asList(109.5,null),List.of(Double.NaN),List.of(Double.POSITIVE_INFINITY),List.of(-1.0),List.of(181.0)))assertNull(ImplicitHProxyGeometry.minimum(angles));}
    @Test void invalidQuantitiesUnknown(){for(Double x:Arrays.asList(null,Double.NaN,Double.POSITIVE_INFINITY,-1.0)){assertNull(ImplicitHProxyGeometry.predicate(x,0.0,0.0));assertNull(ImplicitHProxyGeometry.predicate(3.0,x,0.0));assertNull(ImplicitHProxyGeometry.predicate(3.0,0.0,x));}assertNull(ImplicitHProxyGeometry.predicate(0.0,0.0,0.0));}
}
