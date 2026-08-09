package totah.lab.web.assembly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.component.ExperimentalBindingSiteGroup;
import totah.lab.athena.pocket.component.PocketPairComparison;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Idempotently persists one occurrence's validated canonical-site derivation. */
@Service
public class ExperimentalBindingSitePersistenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExperimentalBindingSiteAnalysisService analysisService;
    private final String schema;

    public ExperimentalBindingSitePersistenceService(JdbcTemplate jdbc,
            ObjectMapper mapper,
            ExperimentalBindingSiteAnalysisService analysisService,
            @Value("${totah.persistence.docking-schema:docking}") String schema) {
        this.jdbc=jdbc; this.mapper=mapper; this.analysisService=analysisService;
        if(!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
            throw new IllegalArgumentException("Invalid persistence schema");
        this.schema=schema;
    }

    @Transactional
    public PersistResult persist(
            ExperimentalBindingSiteAnalysisService.OccurrenceRef occurrence) {
        jdbc.execute("SET LOCAL search_path TO "+schema+", public");
        var analysis=analysisService.analyze(occurrence);
        UUID runToken=UUID.randomUUID();
        String ruleJson=ruleJson();
        Map<Integer,Long> siteIds=new LinkedHashMap<>();
        for(var site:analysis.grouping().sites()) {
            long siteId=jdbc.queryForObject("""
                    INSERT INTO experimental_binding_site
                        (occurrence_id,site_number,localization_status,
                         ligand_centroid_x,ligand_centroid_y,ligand_centroid_z,
                         contributing_pocket_count,direct_contact_residue_count,
                         near_shell_residue_count,covered_ligand_atom_count,
                         contacted_ligand_atom_count,method,method_version,
                         grouping_rule,run_token)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
                    ON CONFLICT (occurrence_id,site_number) DO UPDATE SET
                        localization_status=EXCLUDED.localization_status,
                        ligand_centroid_x=EXCLUDED.ligand_centroid_x,
                        ligand_centroid_y=EXCLUDED.ligand_centroid_y,
                        ligand_centroid_z=EXCLUDED.ligand_centroid_z,
                        contributing_pocket_count=EXCLUDED.contributing_pocket_count,
                        direct_contact_residue_count=EXCLUDED.direct_contact_residue_count,
                        near_shell_residue_count=EXCLUDED.near_shell_residue_count,
                        covered_ligand_atom_count=EXCLUDED.covered_ligand_atom_count,
                        contacted_ligand_atom_count=EXCLUDED.contacted_ligand_atom_count,
                        method=EXCLUDED.method,method_version=EXCLUDED.method_version,
                        grouping_rule=EXCLUDED.grouping_rule,run_token=EXCLUDED.run_token,
                        evaluated_at=now()
                    RETURNING id
                    """,Long.class,occurrence.id(),site.groupNumber(),
                    site.weaklyLocalized()?"WEAK":"STRONG",
                    analysis.ligandCentroid().x(),analysis.ligandCentroid().y(),
                    analysis.ligandCentroid().z(),site.contributingPocketIds().size(),
                    site.directContactResidues().size(),site.nearShellResidues().size(),
                    site.coveredLigandAtoms().size(),site.contactingLigandAtoms().size(),
                    ExperimentalBindingSiteAnalysisService.METHOD,
                    ExperimentalBindingSiteAnalysisService.METHOD_VERSION,
                    ruleJson,runToken);
            siteIds.put(site.groupNumber(),siteId);
            replaceSiteDetails(siteId,site);
        }
        jdbc.update("DELETE FROM experimental_binding_site WHERE occurrence_id=? AND run_token<>?",
                occurrence.id(),runToken);
        persistCandidates(occurrence.id(),analysis.grouping().sites(),siteIds,
                Set.copyOf(analysis.grouping().incidentalPocketIds()));
        persistPairs(occurrence.id(),analysis.grouping().pairComparisons());
        return new PersistResult(occurrence.id(),analysis.grouping().sites().size(),
                analysis.candidates().size(),analysis.grouping().incidentalPocketIds().size());
    }

    private void replaceSiteDetails(long siteId,ExperimentalBindingSiteGroup site) {
        jdbc.update("DELETE FROM experimental_binding_site_residue WHERE site_id=?",siteId);
        for(String residue:site.directContactResidues()) insertResidue(siteId,residue,"DIRECT");
        for(String residue:site.nearShellResidues()) insertResidue(siteId,residue,"NEAR_SHELL");
        jdbc.update("DELETE FROM experimental_binding_site_chain WHERE site_id=?",siteId);
        for(String chain:site.chains()) jdbc.update("INSERT INTO experimental_binding_site_chain VALUES (?,?)",siteId,chain);
        jdbc.update("DELETE FROM experimental_binding_site_target WHERE site_id=?",siteId);
        for(String accession:site.humanTargets()) jdbc.update("""
                INSERT INTO experimental_binding_site_target(site_id,target_id)
                SELECT ?,id FROM public.targets WHERE uniprot_id=?
                ON CONFLICT DO NOTHING
                """,siteId,accession);
        jdbc.update("DELETE FROM experimental_binding_site_ligand_atom WHERE site_id=?",siteId);
        for(String atom:site.coveredLigandAtoms()) jdbc.update("INSERT INTO experimental_binding_site_ligand_atom VALUES (?,?,?)",siteId,atom,"SPHERE_COVERED");
        for(String atom:site.contactingLigandAtoms()) jdbc.update("INSERT INTO experimental_binding_site_ligand_atom VALUES (?,?,?)",siteId,atom,"PROTEIN_CONTACT");
    }

    private void insertResidue(long siteId,String identity,String band) {
        String[] parts=identity.split(":",-1);
        jdbc.update("INSERT INTO experimental_binding_site_residue VALUES (?,?,?,?,?,?)",
                siteId,parts[0],Integer.parseInt(parts[1]),parts[2],parts[3],band);
    }

    private void persistCandidates(long occurrence,List<ExperimentalBindingSiteGroup> sites,
            Map<Integer,Long> siteIds,Set<Long> incidental) {
        jdbc.update("DELETE FROM experimental_binding_site_candidate WHERE occurrence_id=?",occurrence);
        for(var site:sites) for(long pocket:site.contributingPocketIds())
            jdbc.update("INSERT INTO experimental_binding_site_candidate VALUES (?,?,?,?)",
                    occurrence,pocket,siteIds.get(site.groupNumber()),"CONTRIBUTING");
        for(long pocket:incidental) jdbc.update("INSERT INTO experimental_binding_site_candidate VALUES (?,?,NULL,?)",
                occurrence,pocket,"INCIDENTAL");
    }

    private void persistPairs(long occurrence,List<PocketPairComparison> pairs) {
        jdbc.update("DELETE FROM experimental_binding_site_pocket_pair WHERE occurrence_id=?",occurrence);
        for(var pair:pairs) jdbc.update("""
                INSERT INTO experimental_binding_site_pocket_pair VALUES
                    (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,occurrence,pair.firstPocketId(),pair.secondPocketId(),
                pair.sharedResidues(),pair.residueJaccard(),finite(pair.minimumSphereSurfaceGap()),
                pair.centroidDistance(),pair.sharedCoveredLigandAtoms(),
                pair.coveredLigandAtomJaccard(),pair.sharedContactedLigandAtoms(),
                pair.contactedLigandAtomJaccard(),finite(pair.minimumEngagedLigandAtomDistance()),
                pair.sameChainContext(),pair.sameHumanTargetContext(),pair.samePhysicalSite(),
                ExperimentalBindingSiteAnalysisService.METHOD,
                ExperimentalBindingSiteAnalysisService.METHOD_VERSION);
    }

    private String ruleJson(){try{return mapper.writeValueAsString(analysisService.rule());}
        catch(JsonProcessingException exception){throw new IllegalStateException(exception);}}
    private static Double finite(double value){return Double.isFinite(value)?value:null;}
    public record PersistResult(long occurrenceId,int sites,int candidates,int incidental){}
}
