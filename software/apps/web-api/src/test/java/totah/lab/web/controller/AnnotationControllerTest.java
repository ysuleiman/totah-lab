package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.web.service.AnnotatedProtein;
import totah.lab.web.service.AnnotationEnrichment;
import totah.lab.web.service.AnnotationFlags;
import totah.lab.web.service.AnnotationReport;
import totah.lab.web.service.FlagTally;
import totah.lab.web.service.ProteinAnnotationService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnotationControllerTest {

    @Test
    void returnsCsvDownload() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(post("/api/annotations/top-hits")
                        .param("format", "csv")
                        .contentType("application/json")
                        .content("{\"accessions\":[\"P11111\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("top_hits_annotation.csv")
                ))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(
                        containsString("accession,found,protein_name")
                ))
                .andExpect(content().string(containsString("P11111")));
    }

    @Test
    void returnsMarkdownDownload() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(post("/api/annotations/top-hits")
                        .param("format", "md")
                        .contentType("application/json")
                        .content("{\"accessions\":[\"P11111\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("top_hits_annotation.md")
                ))
                .andExpect(content().string(
                        containsString("# Top Hits Annotation")
                ));
    }

    @Test
    void rejectsUnsupportedFormatAndEmptyBody() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(post("/api/annotations/top-hits")
                        .param("format", "xml")
                        .contentType("application/json")
                        .content("{\"accessions\":[\"P11111\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/annotations/top-hits")
                        .contentType("application/json")
                        .content("{\"accessions\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private static MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(
                new AnnotationController(new StubAnnotationService())
        ).build();
    }

    private static final class StubAnnotationService
            extends ProteinAnnotationService {

        private StubAnnotationService() {
            super(org.mockito.Mockito.mock(
                    totah.lab.web.persistence.ReceptorRepository.class
            ));
        }

        @Override
        public AnnotationReport annotateTopHits(List<String> accessions) {
            assertEquals(List.of("P11111"), accessions);

            return new AnnotationReport(
                    List.of(new AnnotatedProtein(
                            "P11111",
                            true,
                            "Protein-lysine methyltransferase",
                            "METTL7A",
                            "Homo sapiens",
                            true,
                            List.of("2.1.1.43"),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of("8ABC"),
                            List.of(),
                            new AnnotationFlags(
                                    true, true, true, false, false,
                                    false, true, false, false
                            )
                    )),
                    1,
                    1,
                    new FlagTally(1, 1, 1, 1, 0, 0, 0, 1, 0, 0),
                    new FlagTally(4, 1, 1, 0, 1, 1, 0, 0, 0, 1),
                    List.of(new AnnotationEnrichment(
                            "Enzymes",
                            1, 1, 1, 4, 4.0, 0.25
                    ))
            );
        }
    }
}
