package totah.lab.aether;

import java.lang.reflect.Modifier;
import java.util.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;

/** Test-only accounting of distinct primitive arrays reachable from completed evidence. */
final class RetainedScientificArrays {
    private RetainedScientificArrays(){}
    static Map<String,Long> bytes(Object evidence) throws ReflectiveOperationException {
        var totals=new TreeMap<String,Long>();
        visit(evidence,"SCF_MATRIX_BYTES",Collections.newSetFromMap(new IdentityHashMap<>()),totals);
        return totals;
    }
    private static void visit(Object value,String category,Set<Object> seen,Map<String,Long> totals) throws ReflectiveOperationException {
        if(value==null||!seen.add(value))return;
        if(value instanceof double[] array){totals.merge(category,8L*array.length,Long::sum);return;}
        if(value instanceof Object[] array){for(Object item:array)visit(item,category,seen,totals);return;}
        if(value instanceof Optional<?> optional){if(optional.isPresent())visit(optional.get(),category,seen,totals);return;}
        if(value instanceof Iterable<?> items){for(Object item:items)visit(item,category,seen,totals);return;}
        if(value instanceof Array2DRowRealMatrix matrix){visit(matrix.getDataRef(),category,seen,totals);return;}
        var type=value.getClass();
        if(type.isEnum()||!type.getPackageName().equals("totah.lab.aether.matrix"))return;
        for(var field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
            field.setAccessible(true);
            String next=switch(type.getSimpleName()) {
                case "AoGrid","MolecularGrid"->"GRID_STORAGE_BYTES";
                case "ElectronRepulsionTensor"->"ERI_PACKED_BYTES";
                default->category;
            };
            if(type.getName().equals("totah.lab.aether.matrix.XcIntegration$Result")&&field.getName().equals("rho"))next="GRID_DENSITY_BYTES";
            visit(field.get(value),next,seen,totals);
        }
    }
}
