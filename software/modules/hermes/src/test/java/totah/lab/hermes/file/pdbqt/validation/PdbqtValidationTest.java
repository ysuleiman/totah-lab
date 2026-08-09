package totah.lab.hermes.file.pdbqt.validation;

import totah.lab.hermes.file.pdbqt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.geometry.Point3D;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtValidationTest {
    @TempDir Path directory;

    @Test
    void serializerValidatorAggregatesIndependentProblems() {
        PdbqtAtomReference bad=new PdbqtAtomReference(0,1,"CA","ALA","A",1,null,
                new Point3D(0,0,0),1,0,0,"Xx");
        var input=new PdbqtFlexibleReceptor(
                List.of(new PdbqtRigidAtom(bad),new PdbqtRigidAtom(bad)),List.of(),3);
        PdbqtValidationReport report=new PdbqtSerializerValidator().validate(input);
        assertTrue(report.hasErrors());
        assertTrue(report.issues().size()>=4);
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.DUPLICATE_ATOM));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.MISSING_AD4_TYPE));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.PARTITION_INCOMPLETE));
    }

    @Test
    void fileValidatorAggregatesLexicalAndStructuralProblems() throws Exception {
        Path file=directory.resolve("bad.pdbqt");
        Files.write(file,List.of("ATOM bad","REMARK unsupported","ATOM      X malformed"));
        PdbqtValidationReport report=new PdbqtValidator().validatePdbqt(file);
        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.MALFORMED_RECORD));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.UNSUPPORTED_RECORD));
    }

    @Test
    void flexibleFileValidatorChecksBalancedBranchesAndOverlap() throws Exception {
        Path rigid=directory.resolve("rigid.pdbqt"),flex=directory.resolve("flex.pdbqt");
        String atom="ATOM      1 CA   ALA A   1       0.000   0.000   0.000  1.00  0.00    +0.0000  C";
        Files.write(rigid,List.of(atom));
        Files.write(flex,List.of("BEGIN_RES ALA A 1 ","ROOT",atom,"BRANCH 1 2","ENDROOT","END_RES ALA A 1 "));
        PdbqtValidationReport report=new PdbqtValidator().validateFlexiblePdbqt(rigid,flex);
        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.BRANCH_UNBALANCED));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.RIGID_FLEXIBLE_OVERLAP));
    }

    @Test
    void ligandFileValidatorAcceptsBalancedLigandPdbqt() throws Exception {
        Path file=directory.resolve("ligand.pdbqt");
        String first="ATOM      1 C1   LIG L   1       0.000   0.000   0.000  1.00  0.00    +0.0000  C";
        String second="ATOM      2 C2   LIG L   1       1.500   0.000   0.000  1.00  0.00    +0.0000  C";
        Files.write(file,List.of("ROOT",first,"ENDROOT","BRANCH 1 2",second,"ENDBRANCH 1 2","TORSDOF 1"));
        PdbqtValidationReport report=new PdbqtValidator().validateLigandPdbqt(file);
        assertTrue(report.valid());
    }

    @Test
    void ligandFileValidatorFlagsTorsdofMismatchAndUnbalancedBranches() throws Exception {
        Path file=directory.resolve("ligand-bad.pdbqt");
        String first="ATOM      1 C1   LIG L   1       0.000   0.000   0.000  1.00  0.00    +0.0000  C";
        String second="ATOM      2 C2   LIG L   1       1.500   0.000   0.000  1.00  0.00    +0.0000  C";
        Files.write(file,List.of("ROOT",first,"ENDROOT","BRANCH 1 2",second,"ENDBRANCH 1 2","TORSDOF 3"));
        PdbqtValidationReport report=new PdbqtValidator().validateLigandPdbqt(file);
        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(i->i.code()==PdbqtValidationCode.TORSDOF_MISMATCH));
    }
}
