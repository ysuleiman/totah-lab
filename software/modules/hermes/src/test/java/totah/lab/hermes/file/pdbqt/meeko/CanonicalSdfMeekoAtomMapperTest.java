package totah.lab.hermes.file.pdbqt.meeko;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalSdfMeekoAtomMapperTest {
    @TempDir Path temporary;
    private final Path root = Path.of("../../..").toAbsolutePath().normalize();
    private final CanonicalSdfMeekoAtomMapper mapper = new CanonicalSdfMeekoAtomMapper();

    @Test void reorderedAndRenamedDcmbAtomsMapByChemistry() throws Exception {
        var receipt = map("R", "R");
        assertThat(receipt.successful()).isTrue();
        assertThat(receipt.meekoSerialToSdfIndexOrOrbit()).hasSize(13);
    }

    @Test void symmetricAtomsProduceOrbitCorrespondenceRatherThanFalseAmbiguity() throws Exception {
        var receipt = map("R", "R");
        assertThat(receipt.status()).isEqualTo(CanonicalSdfMeekoAtomMapper.Status.SYMMETRY_EQUIVALENT_MAPPING);
        assertThat(receipt.symmetryEquivalentGroups()).isNotEmpty();
    }

    @Test void stereochemicalInversionIsRejected() throws Exception {
        assertThat(map("R", "S").status())
                .isEqualTo(CanonicalSdfMeekoAtomMapper.Status.STEREOCHEMISTRY_INCOMPATIBLE);
    }

    @Test void chargeStateMismatchIsRejected() throws Exception {
        Path charged = root.resolve("analysis/dcmb/sar_experiment/ligands/DCMB_R.sdf");
        PdbqtModel pose = pose("R");
        assertThat(mapper.map(charged, posePath("R"), pose).status())
                .isEqualTo(CanonicalSdfMeekoAtomMapper.Status.CHEMISTRY_INCONSISTENT);
    }

    @Test void connectivityOrBondOrderMismatchIsRejected() throws Exception {
        Path changed = temporary.resolve("changed.sdf");
        String text = Files.readString(sdf("R"));
        Files.writeString(changed, text.replaceFirst("(?m)^  1  2  1  0$", "  1  2  2  0"));
        assertThat(mapper.map(changed, posePath("R"), pose("R")).status())
                .isEqualTo(CanonicalSdfMeekoAtomMapper.Status.CHEMISTRY_INCONSISTENT);
    }

    @Test void missingMeekoTopologyIsReportedAsAbsent() throws Exception {
        Path sdf = root.resolve("software/modules/athena/src/test/resources/mettl7-v2-regression/netarsudil/netarsudil_CID66599893_neutral.sdf");
        Path pdbqt = root.resolve("research/mettl7-netarsudil-sam-mechanism/vina-matched/raw/7B_neutral_seed172904.pdbqt");
        assertThat(mapper.map(sdf, pdbqt, new PdbqtReader().read(pdbqt).firstModel()).status())
                .isEqualTo(CanonicalSdfMeekoAtomMapper.Status.TOPOLOGY_ABSENT);
    }

    @Test void corruptSmilesIndexIsNotBlindlyTrusted() throws Exception {
        PdbqtModel pose = pose("R");
        var remarks = new ArrayList<>(pose.remarks());
        for (int i=0;i<remarks.size();i++) if (remarks.get(i).startsWith("REMARK SMILES IDX ")) {
            remarks.set(i, remarks.get(i).replaceFirst("2 1", "2 999")); break;
        }
        PdbqtModel corrupt = new PdbqtModel(pose.modelNumber(), pose.atoms(), pose.torsionTree(), remarks);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.map(sdf("R"), posePath("R"), corrupt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void roundTripOrbitCorrespondenceCoversEveryExplicitPoseAtom() throws Exception {
        var receipt=map("S","S");
        assertThat(receipt.meekoSerialToSdfIndexOrOrbit()).hasSize(pose("S").atoms().size());
        assertThat(receipt.meekoSerialToSdfIndexOrOrbit().values()).allSatisfy(indices -> assertThat(indices).isNotEmpty());
    }

    @Test void mappingHashIsStableAcrossModelsWithTheSameChemistry() throws Exception {
        Path posePath=posePath("R"); var models=new PdbqtReader().read(posePath).models();
        assertThat(mapper.map(sdf("R"),posePath,models.get(0)).mappingSha256())
                .isEqualTo(mapper.map(sdf("R"),posePath,models.get(1)).mappingSha256());
    }

    @Test void everyExplicitHydrogenRequiresExactlyOneParentMapping() throws Exception {
        PdbqtModel pose = pose("R");
        var withoutParents = pose.remarks().stream()
                .filter(remark -> !remark.startsWith("REMARK H PARENT ")).toList();
        PdbqtModel incomplete = new PdbqtModel(
                pose.modelNumber(), pose.atoms(), pose.torsionTree(), withoutParents);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> mapper.map(sdf("R"), posePath("R"), incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every explicit PDBQT hydrogen");
    }

    private CanonicalSdfMeekoAtomMapper.Receipt map(String sdfStereo,String poseStereo)throws Exception{
        return mapper.map(sdf(sdfStereo),posePath(poseStereo),pose(poseStereo));
    }
    private Path sdf(String stereo){return root.resolve("research/mettl7-selectivity-forensics/dcmb-analog-program/sah-campaign-v1/ligands/DCMB_"+stereo+"_NEUTRAL.sdf");}
    private Path posePath(String stereo){return root.resolve("analysis/dcmb/controlled_campaign/raw/7A_WT_SAM_BOUND_"+stereo+"_s1.pdbqt");}
    private PdbqtModel pose(String stereo)throws Exception{return new PdbqtReader().read(posePath(stereo)).firstModel();}
}
