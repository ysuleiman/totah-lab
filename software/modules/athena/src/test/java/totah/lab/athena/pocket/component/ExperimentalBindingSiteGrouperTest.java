package totah.lab.athena.pocket.component;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalBindingSiteGrouperTest {
    private final ExperimentalBindingSiteGrouper grouper =
            new ExperimentalBindingSiteGrouper(
                    ExperimentalBindingSiteGroupingRule.defaults());

    @Test
    void collapsesOverlappingCavitiesAroundTheSameLigandAtoms() {
        var first = pocket(1, ComponentPocketRelationshipClass.OCCUPIES_POCKET,
                0, Set.of("A:10", "A:11"), Set.of("C1", "C2"),
                Set.of("C1"), Set.of("P1"));
        var second = pocket(2, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                4, Set.of("A:11", "A:12"), Set.of("C2", "C3"),
                Set.of("C1"), Set.of("P1"));

        var result = grouper.group(List.of(first, second));

        assertEquals(1, result.sites().size());
        assertEquals(List.of(1L, 2L), result.sites().getFirst()
                .contributingPocketIds());
        assertTrue(result.pairComparisons().getFirst().samePhysicalSite());
    }

    @Test
    void joinsAdjacentComplementarySubpocketsInTheSameTargetContext() {
        var first = pocket(1, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                0, Set.of("A:10"), Set.of("C1"), Set.of("C1"), Set.of("P1"));
        var second = pocket(2, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                5, Set.of("A:30"), Set.of("C9"), Set.of("C9"), Set.of("P1"));

        var result = grouper.group(List.of(first, second));

        assertEquals(1, result.sites().size());
        assertEquals(2, result.sites().getFirst().contributingPocketIds().size());
    }

    @Test
    void preservesDistinctTargetLocalizedSites() {
        var first = pocket(1, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                0, Set.of("A:10"), Set.of("C1"), Set.of("C1"), Set.of("P1"));
        var second = pocket(2, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                30, Set.of("B:10"), Set.of("C9"), Set.of("C9"), Set.of("P2"));

        var result = grouper.group(List.of(first, second));

        assertEquals(2, result.sites().size());
        assertFalse(result.pairComparisons().getFirst().samePhysicalSite());
    }

    @Test
    void elongatedLigandAnchorsSeparatedContactsOnOneTargetAsOneSite() {
        var first = pocket(1, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                0, Set.of("A:10"), Set.of(), Set.of("C1"), Set.of("P1"));
        var second = pocket(2, ComponentPocketRelationshipClass.CONTACTS_POCKET,
                30, Set.of("A:90"), Set.of(), Set.of("C9"), Set.of("P1"));

        var result = grouper.group(List.of(first, second));

        assertEquals(1, result.sites().size());
        assertEquals(2, result.sites().getFirst().contributingPocketIds().size());
    }

    @Test
    void nearOnlyEvidenceCreatesOneWeakSiteWithoutMergingIncidentalPockets() {
        var nearest = pocket(1, ComponentPocketRelationshipClass.NEAR_POCKET,
                0, Set.of("A:10"), Set.of(), Set.of(), Set.of("P1"));
        var incidental = pocket(2, ComponentPocketRelationshipClass.NEAR_POCKET,
                30, Set.of("A:90"), Set.of(), Set.of(), Set.of("P1"));

        var result = grouper.group(List.of(nearest, incidental));

        assertEquals(1, result.sites().size());
        assertTrue(result.sites().getFirst().weaklyLocalized());
        assertEquals(List.of(2L), result.incidentalPocketIds());
    }

    private static ExperimentalSitePocket pocket(long id,
            ComponentPocketRelationshipClass relationship, double x,
            Set<String> residues, Set<String> covered, Set<String> contacted,
            Set<String> targets) {
        Point3D center = new Point3D(x, 0, 0);
        Map<String, Point3D> positions = new java.util.LinkedHashMap<>();
        covered.forEach(atom -> positions.put(atom, center));
        contacted.forEach(atom -> positions.put(atom, center));
        return new ExperimentalSitePocket(id, (int) id, (int) id,
                relationship, 0.5, x, 0, x, center,
                List.of(new PocketSphere(center, 2)), residues, residues,
                residues, covered, covered, contacted, positions,
                Set.of("A"), targets);
    }
}
