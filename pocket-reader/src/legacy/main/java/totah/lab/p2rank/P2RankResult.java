package totah.lab.p2rank;

import lombok.Getter;
import lombok.Setter;
import totah.lab.pocket.Pocket;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class P2RankResult {

    private String structure;
    private List<Pocket> pockets;
    private Map<String, P2RankResidueScore> residues;
    private Map<String,Object> metadata;
}
