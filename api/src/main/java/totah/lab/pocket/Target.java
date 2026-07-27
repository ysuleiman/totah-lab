package totah.lab.pocket;

import java.util.List;

public interface Target {
    public Long getId();
    public String getUniprotId();
    public String getName();
    public List<String> getOtherNames();
}
