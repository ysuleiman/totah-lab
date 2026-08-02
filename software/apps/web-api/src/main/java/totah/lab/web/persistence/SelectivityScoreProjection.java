package totah.lab.web.persistence;

public interface SelectivityScoreProjection {

    String getLigandId();

    String getLigandLabel();

    Double getScore7b();

    Double getScore7a();

    Double getDelta();

    Long getRunId7b();

    Long getRunId7a();

    Long getPoseId7b();

    Long getPoseId7a();

    Long getTotalCount();
}
