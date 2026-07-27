package totah.lab.topology;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock AMBER database for unit testing HydrogenOptimizer.
 * Uses real ResidueTemplate and AtomTemplate classes.
 */
public class MockAmberDatabase {

    public static ResidueTemplateProvider createMockLibrary() {
        return new MockProvider();
    }

    public static AmberParameterSet createMockLJParams() {
        AmberParameterSet set = AmberParameterSet.getInstance();
        set.addParameter("CT", 1.908, 0.109);
        set.addParameter("C",  1.908, 0.086);
        set.addParameter("O",  1.661, 0.210);
        set.addParameter("N",  1.824, 0.170);
        set.addParameter("OH", 1.721, 0.210);
        set.addParameter("H",  0.600, 0.016);
        set.addParameter("HO", 0.000, 0.000);
        set.addParameter("HA", 0.600, 0.016);
        set.addParameter("S",  2.000, 0.250);
        return set;
    }

    private static class MockProvider implements ResidueTemplateProvider {
        private final Map<String, ResidueTemplate> templates = new HashMap<>();

        MockProvider() {
            buildAsn();
            buildGln();
            buildSer();
            buildThr();
            buildTyr();
            buildHis("HID");
            buildHis("HIE");
            buildHis("HIP");
            buildAla();
            buildVal();
            buildLeu();
            buildIle();
            buildMet();
            buildEnv();
        }

        private void addAtom(ResidueTemplate t, String name, String type, double charge) {
            t.addAtom(AtomTemplate.builder()
                    .name(name)
                    .amberType(type)
                    .charge(charge)
                    .build());
        }

        private void buildAsn() {
            ResidueTemplate t = new ResidueTemplate("ASN");
            addAtom(t, "CA",   "CT",  0.03);
            addAtom(t, "CB",   "CT", -0.18);
            addAtom(t, "CG",   "C",   0.55);
            addAtom(t, "OD1",  "O",  -0.55);
            addAtom(t, "ND2",  "N",  -0.62);
            addAtom(t, "HD21", "H",   0.31);
            addAtom(t, "HD22", "H",   0.31);
            templates.put("ASN", t);
        }

        private void buildGln() {
            ResidueTemplate t = new ResidueTemplate("GLN");
            addAtom(t, "CA",   "CT",  0.03);
            addAtom(t, "CB",   "CT", -0.18);
            addAtom(t, "CG",   "CT", -0.18);
            addAtom(t, "CD",   "C",   0.55);
            addAtom(t, "OE1",  "O",  -0.55);
            addAtom(t, "NE2",  "N",  -0.62);
            addAtom(t, "HE21", "H",   0.31);
            addAtom(t, "HE22", "H",   0.31);
            templates.put("GLN", t);
        }

        private void buildSer() {
            ResidueTemplate t = new ResidueTemplate("SER");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT",  0.05);
            addAtom(t, "OG",  "OH", -0.66);
            addAtom(t, "HG",  "HO",  0.42);
            templates.put("SER", t);
        }

        private void buildThr() {
            ResidueTemplate t = new ResidueTemplate("THR");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT",  0.05);
            addAtom(t, "OG1", "OH", -0.66);
            addAtom(t, "HG1", "HO",  0.42);
            addAtom(t, "CG2", "CT", -0.18);
            templates.put("THR", t);
        }

        private void buildTyr() {
            ResidueTemplate t = new ResidueTemplate("TYR");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT", -0.18);
            addAtom(t, "CG",  "C",   0.05);
            addAtom(t, "CD1", "C",  -0.10);
            addAtom(t, "CD2", "C",  -0.10);
            addAtom(t, "CE1", "C",  -0.10);
            addAtom(t, "CE2", "C",  -0.10);
            addAtom(t, "CZ",  "C",   0.15);
            addAtom(t, "OH",  "OH", -0.55);
            addAtom(t, "HH",  "HO",  0.42);
            templates.put("TYR", t);
        }

        private void buildHis(String name) {
            ResidueTemplate t = new ResidueTemplate(name);
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT", -0.18);
            addAtom(t, "CG",  "C",   0.05);
            addAtom(t, "ND1", "N",  -0.36);
            addAtom(t, "CD2", "C",  -0.10);
            addAtom(t, "CE1", "C",  -0.10);
            addAtom(t, "NE2", "N",  -0.36);
            addAtom(t, "HD2", "H",   0.10);
            addAtom(t, "HE1", "H",   0.10);
            if ("HID".equals(name) || "HIP".equals(name)) {
                addAtom(t, "HD1", "H", 0.32);
            }
            if ("HIE".equals(name) || "HIP".equals(name)) {
                addAtom(t, "HE2", "H", 0.32);
            }
            templates.put(name, t);
        }

        private void buildAla() {
            ResidueTemplate t = new ResidueTemplate("ALA");
            addAtom(t, "CA", "CT",  0.03);
            addAtom(t, "CB", "CT", -0.18);
            templates.put("ALA", t);
        }

        private void buildVal() {
            ResidueTemplate t = new ResidueTemplate("VAL");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT", -0.18);
            addAtom(t, "CG1", "CT", -0.18);
            addAtom(t, "CG2", "CT", -0.18);
            templates.put("VAL", t);
        }

        private void buildLeu() {
            ResidueTemplate t = new ResidueTemplate("LEU");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT", -0.18);
            addAtom(t, "CG",  "CT", -0.18);
            addAtom(t, "CD1", "CT", -0.18);
            addAtom(t, "CD2", "CT", -0.18);
            templates.put("LEU", t);
        }

        private void buildIle() {
            ResidueTemplate t = new ResidueTemplate("ILE");
            addAtom(t, "CA",  "CT",  0.03);
            addAtom(t, "CB",  "CT", -0.18);
            addAtom(t, "CG1", "CT", -0.18);
            addAtom(t, "CG2", "CT", -0.18);
            addAtom(t, "CD1", "CT", -0.18);
            templates.put("ILE", t);
        }

        private void buildMet() {
            ResidueTemplate t = new ResidueTemplate("MET");
            addAtom(t, "CA", "CT",  0.03);
            addAtom(t, "CB", "CT", -0.18);
            addAtom(t, "CG", "CT", -0.18);
            addAtom(t, "SD", "S",   0.00);
            addAtom(t, "CE", "CT", -0.18);
            templates.put("MET", t);
        }

        private void buildEnv() {
            ResidueTemplate gly = new ResidueTemplate("GLY");
            addAtom(gly, "O", "O", -0.50);
            templates.put("GLY", gly);

            ResidueTemplate lys = new ResidueTemplate("LYS");
            addAtom(lys, "NZ", "N", 1.0);
            addAtom(lys, "HZ1", "H", 0.33);
            addAtom(lys, "HZ2", "H", 0.33);
            addAtom(lys, "HZ3", "H", 0.33);
            templates.put("LYS", lys);

            ResidueTemplate asp = new ResidueTemplate("ASP");
            addAtom(asp, "OD1", "O", -0.55);
            addAtom(asp, "OD2", "O", -0.55);
            templates.put("ASP", asp);

            ResidueTemplate glu = new ResidueTemplate("GLU");
            addAtom(glu, "OE1", "O", -0.55);
            addAtom(glu, "OE2", "O", -0.55);
            templates.put("GLU", glu);
        }

        @Override
        public ResidueTemplate getTemplate(String name) {
            return templates.get(name);
        }
    }
}