package totah.lab.mettl7.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;
import totah.lab.athena.ligand.screening.Mettl7bEnrichmentGate;
import totah.lab.mettl7.triage.Mettl7LigandTriageService;
import totah.lab.mettl7.triage.Mettl7bEnrichmentPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7DependencyBoundaryTest {
    @Test
    void moduleReusesAthenaInsteadOfDeclaringReplacementGenericFilters() throws Exception {
        assertThat(Mettl7LigandTriageService.class.getDeclaredConstructors())
                .anySatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(ChemicalLiabilityGate.class));
        assertThat(Mettl7bEnrichmentPolicy.class.getMethod("evaluate",
                        Mettl7bEnrichmentGate.Cohort.class, Mettl7bEnrichmentGate.Evidence.class)
                .getReturnType()).isEqualTo(Mettl7bEnrichmentGate.Result.class);
    }
}
