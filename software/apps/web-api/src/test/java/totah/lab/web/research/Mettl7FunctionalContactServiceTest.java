package totah.lab.web.research;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7FunctionalContactServiceTest {
    @Test void headlinesAreDerivedFromCurrentRows() {
        var rows = List.of(row("7A", "LYS151", 151, "ether oxygen", 2),
                row("7A", "LYS151", 151, "alkyl carbon", 2),
                row("7B", "PHE36", 36, "phenyl", 3),
                row("7B", "MET40", 40, "amide carbonyl", 1));
        var result = Mettl7FunctionalContactService.evidenceDerivedHeadlines(rows);
        assertThat(result.mettl7aLys151Preference()).isEqualTo(
                "oxygen-facing 2/4 (50.0%); ether oxygen is 2/2 (100.0%)");
        assertThat(result.mettl7bHydrophobicFacePreference()).isEqualTo(
                "carbon groups 3/4 (75.0%); phenyl is 3/3 (100.0%)");
        assertThat(result.functionalGroupSelectivityMechanism()).isEqualTo("NOT_EVALUATED_BY_THIS_QUERY");
    }

    @Test void emptyEvidenceDoesNotPublishHistoricalCounts() {
        var result = Mettl7FunctionalContactService.evidenceDerivedHeadlines(List.of());
        assertThat(result.mettl7aLys151Preference()).contains("0/0").contains("UNAVAILABLE");
        assertThat(result.mettl7bHydrophobicFacePreference()).contains("0/0").contains("UNAVAILABLE");
    }

    private static Mettl7FunctionalContactService.ResidueFunctionalGroupView row(
            String paralog, String residue, int number, String group, long count) {
        return new Mettl7FunctionalContactService.ResidueFunctionalGroupView(
                paralog, residue, number, group, count, count, 1.0, count, "ADEQUATE_FOR_SCREENING");
    }
}
