package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class FoundationArchitectureTest {
    @Test void productionCodeIsJdkOnlyAndHasNoDomainOrTargetConcepts() throws Exception {
        Path base = Path.of(System.getProperty("basedir"));
        try (var files = Files.walk(base.resolve("src/main/java"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                for (var line : text.lines().filter(l -> l.startsWith("import ")).toList())
                    assertTrue(line.startsWith("import java.") || line.startsWith("import static totah.lab.mnemosyne.")
                            || line.startsWith("import totah.lab.mnemosyne."), file + ": " + line);
                for (var forbidden : List.of("mettl7", "netarsudil", "dcmb", "designstate", "moleculargraph", "quantumevidence", "openchemlib", "daedalus"))
                    assertFalse(text.toLowerCase().contains(forbidden), file + ": " + forbidden);
            }
        }
        try (var files = Files.walk(base.resolve("target/classes"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                for (var forbidden : List.of("athena", "prometheus", "mettl7", "daedalus", "gaia", "aether", "hermes", "hephaestus"))
                    assertFalse(bytes.contains("totah/lab/" + forbidden + "/"), file + ": dependency on " + forbidden);
                assertFalse(bytes.contains("com/actelion/"));
            }
        }
    }
    @Test void domainClassesAreNotOnFoundationClasspath() {
        for (var name : List.of("totah.lab.athena.design.reasoning.DesignKnowledge",
                "totah.lab.prometheus.evidence.QuantumEvidence", "totah.lab.gaia.structure.Structure",
                "totah.lab.aether.provenance.ContentHash", "com.actelion.research.chem.StereoMolecule"))
            assertThrows(ClassNotFoundException.class, () -> Class.forName(name), name);
    }
    @Test void foundationPomHasNoProductionDependencies() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(Path.of(System.getProperty("basedir"), "pom.xml").toFile());
        var dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = (org.w3c.dom.Element) dependencies.item(i);
            assertEquals("test", dependency.getElementsByTagName("scope").item(0).getTextContent());
        }
    }
}
