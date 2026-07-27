package totah.lab.pocket.fpocket;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSearch;
import totah.lab.pocket.Residue;
import totah.lab.fpocket.FPocket;
import totah.lab.fpocket.FPocketParser;
import totah.lab.fpocket.FPocketSearch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class FPocketSearchTest {

    @Test
    public void findSER()throws Exception{
        Path folder = Paths.get(
                Objects.requireNonNull(
                                getClass().getResource("/AF-Q6UX53-F1-model_v6_out"))
                        .toURI());
        List<Pocket> pockets = FPocketParser.parse(folder);
        assertEquals(15, pockets.size());

        PocketSearch search = new PocketSearch(pockets);
        search.search(new PocketSearch.PocketCriteria());
        List<Pocket>result = search.findByResidue("SER", 110);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        Pocket pocket = result.get(0);
        System.out.println(pocket.getResidues());

        result = search.findByResidue("SER");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(6, result.size());
        pocket = result.get(0);
        System.out.println(pocket.getResidues());

        result = search.findByResidue("CYS");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        pocket = result.get(0);
        System.out.println(pocket.getResidues());
        pocket = result.get(1);
        System.out.println(pocket.getResidues());
        System.out.println("-------------------");
        List<Pocket> hits =
                search.search(
                        new PocketSearch.PocketCriteria()
                                .minScore(0.1)
                                .containsResidue("CYS")
                );
        for(Pocket p:hits){
            System.out.println(p.getResidues());
        }
        FPocketSearch fsearch = new FPocketSearch(pockets);
        hits =
                fsearch.search(
                        new FPocketSearch.PocketCriteria()
                                .minScore(0.1)
                                .containsResidue("CYS")
                );
        for(Pocket p:hits){
            System.out.println(p.getResidues());
        }
        Map<Pocket, List<Residue>> residues=fsearch.findResidues("CYS");
        System.out.println(residues);
        List<FPocket> list = fsearch.rankByDruggability();
        for(Pocket p:list){
            System.out.println(p);
        }
    }
}
