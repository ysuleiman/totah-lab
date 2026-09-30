package totah.lab.web.research;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Mettl7FunctionalContactControllerTest {
    @Test
    void exposesDatabaseScopeAndDedicatedDcmbContacts() throws Exception {
        Mettl7FunctionalContactService service =
                new Mettl7FunctionalContactService(null) {
                    @Override
                    public ReportView report() {
                        return new ReportView(
                                RUN_KEY,
                                new DatabaseScope(
                                        713, 680, 540289, 467025,
                                        1971, 74, 16),
                                new HeadlineView(
                                        "ether-dominant", "phenyl-dominant",
                                        "WEAK", "PARTIAL", "PARTIAL"),
                                List.of(new ResidueFunctionalGroupView(
                                        "7A", "LYS151", 151,
                                        "ether oxygen", 23, 23,
                                        23.0 / 68.0, 68,
                                        "ADEQUATE_FOR_SCREENING")),
                                List.of(new DcmbContactView(
                                        "DCMB-R", "7A", "7A_WT_APO",
                                        "R", "PHE43", 43,
                                        "halogen atom", 3.4079))
                        );
                    }
                };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new Mettl7FunctionalContactController(service)).build();

        mvc.perform(get("/api/reports/mettl7-functional-contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseScope.dockingRuns")
                        .value(713))
                .andExpect(jsonPath("$.databaseScope.cohortLigands")
                        .value(74))
                .andExpect(jsonPath("$.residueFunctionalGroups[0].residue")
                        .value("LYS151"))
                .andExpect(jsonPath("$.dcmbContacts[0].ligand")
                        .value("DCMB-R"));
    }
}
