package totah.lab.hermes.file.pdbqt.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidationException;

class PdbqtFlexibilityWriterTest {
    @TempDir Path directory;

    @Test
    void writesBalancedDeterministicFragmentTreeAndExactPartition() throws Exception {
        var root = fragment("root", atom(1,"CA","A",10,null), null);
        var child = fragment("side", atom(2,"CB","A",10,null), "root");
        var residue = new PdbqtFlexibleResidue("VAL","A",10,null,1,
                List.of(root, child), List.of(new PdbqtRotatableBond(1,2,"root","side")));
        var input = new PdbqtFlexibleReceptor(
                List.of(new PdbqtRigidAtom(atom(0,"N","A",10,null))), List.of(residue), 3);

        PdbqtWriteResult result = new PdbqtFlexibilityWriter().write(
                input, directory.resolve("rigid.pdbqt"), directory.resolve("flex.pdbqt"));
        List<String> flex = Files.readAllLines(result.flexibleOutput());

        assertEquals(List.of("BEGIN_RES VAL A 10 ", "ROOT"), flex.subList(0,2));
        assertEquals(1, flex.stream().filter("ROOT"::equals).count());
        assertEquals(1, flex.stream().filter("ENDROOT"::equals).count());
        assertEquals(1, flex.stream().filter(line -> line.startsWith("BRANCH ")).count());
        assertEquals(1, flex.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());
        assertEquals(1, result.rigidAtomCount()); assertEquals(2, result.flexibleAtomCount());
        assertEquals(1, result.flexibleResidueCount()); assertEquals(1, result.torsionCount());
    }

    @Test
    void writesFlexibleResiduesInSuppliedCrossChainOrder() throws Exception {
        var first = flexible("GLY","B",5,'A',0);
        var second = flexible("ALA","A",7,null,1);
        var input = new PdbqtFlexibleReceptor(List.of(), List.of(first,second), 2);
        Path flex = directory.resolve("multi-flex.pdbqt");
        new PdbqtFlexibilityWriter().write(input, directory.resolve("multi-rigid.pdbqt"), flex);
        List<String> begin = Files.readAllLines(flex).stream()
                .filter(line -> line.startsWith("BEGIN_RES")).toList();
        assertEquals(List.of("BEGIN_RES GLY B 5A", "BEGIN_RES ALA A 7 "), begin);
    }

    @Test
    void numbersAtomSerialsContinuouslyAcrossFlexibleResidues() throws Exception {
        var first = new PdbqtFlexibleResidue("VAL","A",10,null,0,
                List.of(fragment("root-0", atom(0,"CA","A",10,null), null),
                        fragment("side-0", atom(1,"CB","A",10,null), "root-0")),
                List.of(new PdbqtRotatableBond(0,1,"root-0","side-0")));
        var second = new PdbqtFlexibleResidue("LEU","A",11,null,2,
                List.of(fragment("root-2", atom(2,"CA","A",11,null), null),
                        fragment("side-2", atom(3,"CB","A",11,null), "root-2")),
                List.of(new PdbqtRotatableBond(2,3,"root-2","side-2")));
        var input = new PdbqtFlexibleReceptor(List.of(), List.of(first, second), 4);
        Path flex = directory.resolve("serials-flex.pdbqt");
        new PdbqtFlexibilityWriter().write(input, directory.resolve("serials-rigid.pdbqt"), flex);
        List<String> lines = Files.readAllLines(flex);

        List<Integer> serials = lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .map(line -> Integer.valueOf(line.substring(6, 11).trim()))
                .toList();
        assertEquals(List.of(1, 2, 3, 4), serials);
        assertEquals(List.of("BRANCH 1 2", "BRANCH 3 4"), lines.stream()
                .filter(line -> line.startsWith("BRANCH ")).toList());
        assertEquals(List.of("ENDBRANCH 1 2", "ENDBRANCH 3 4"), lines.stream()
                .filter(line -> line.startsWith("ENDBRANCH ")).toList());
    }

    @Test
    void rejectsAtomPresentInRigidAndFlexibleOutputs() {
        PdbqtAtomReference atom = atom(0,"CA","A",1,null);
        var input = new PdbqtFlexibleReceptor(List.of(new PdbqtRigidAtom(atom)),
                List.of(new PdbqtFlexibleResidue("ALA","A",1,null,0,
                        List.of(fragment("root",atom,null)),List.of())),1);
        assertThrows(PdbqtValidationException.class, () -> new PdbqtFlexibilityWriter().write(
                input,directory.resolve("r"),directory.resolve("f")));
    }

    @Test
    void leavesNoOutputsBehindWhenFlexibleWritingFails() throws Exception {
        var root = fragment("root", atom(0,"CA","A",1,null), null);
        var orphan = fragment("orphan", atom(1,"CB","A",1,null), null);
        var residue = new PdbqtFlexibleResidue("ALA","A",1,null,0,
                List.of(root, orphan), List.of());
        var input = new PdbqtFlexibleReceptor(List.of(), List.of(residue), 2);
        Path rigid = directory.resolve("rigid.pdbqt");
        Path flex = directory.resolve("flex.pdbqt");

        assertThrows(IllegalArgumentException.class,
                () -> new PdbqtFlexibilityWriter().write(input, rigid, flex));

        assertFalse(Files.exists(rigid));
        assertFalse(Files.exists(flex));
        try (var entries = Files.list(directory)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    private PdbqtFlexibleResidue flexible(String name,String chain,int number,Character insertion,int index) {
        return new PdbqtFlexibleResidue(name,chain,number,insertion,index,
                List.of(fragment("root-"+index,atom(index,"CA",chain,number,insertion),null)),List.of());
    }
    private PdbqtFragment fragment(String id,PdbqtAtomReference atom,String parent) {
        return new PdbqtFragment(id,List.of(atom),atom.canonicalAtomIndex(),parent);
    }
    private PdbqtAtomReference atom(int index,String name,String chain,int number,Character insertion) {
        return new PdbqtAtomReference(index,index+1,name,"ALA",chain,number,insertion,
                new Point3D(index,0,0),1,0,0,"C");
    }
}
