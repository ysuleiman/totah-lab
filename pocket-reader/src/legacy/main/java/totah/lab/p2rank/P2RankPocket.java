package totah.lab.p2rank;

import totah.lab.pocket.AlphaSphere;
import totah.lab.pocket.AlphaSphereGeometry;
import totah.lab.pocket.ChemicalProperties;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.Residue;
import totah.lab.pocket.Sasa;

import java.util.ArrayList;
import java.util.List;

public class P2RankPocket implements Pocket {

    private long id;
    private String pocketName;
    private double score;
    private double druggabilityScore;
    private int sasPoints;
    private int surfaceAtoms;
    private double[] center;
    private List<String> residueIds = new ArrayList<>();
    private List<Integer> surfaceAtomIds = new ArrayList<>();

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return pocketName;
    }

    public String getPocketName() {
        return pocketName;
    }

    public void setPocketName(String pocketName) {
        this.pocketName = pocketName;
    }

    @Override
    public double getDruggabilityScore() {
        return druggabilityScore;
    }

    public void setDruggabilityScore(double druggabilityScore) {
        this.druggabilityScore = druggabilityScore;
    }

    @Override
    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public double getVolume() {
        return 0.0;
    }

    @Override
    public double getVolumeScore() {
        return 0.0;
    }

    @Override
    public AlphaSphereGeometry getGeometry() {
        return null;
    }

    @Override
    public Sasa getSasa() {
        return null;
    }

    @Override
    public ChemicalProperties getChemistry() {
        return null;
    }

    @Override
    public List<Residue> getResidues() {
        return List.of();
    }

    @Override
    public List<AlphaSphere> getAlphaSpheres() {
        return List.of();
    }

    @Override
    public void add(Residue residue) {
    }

    @Override
    public void addResidues(List<Residue> residues) {
    }

    @Override
    public void addAlphaSpheres(List<AlphaSphere> spheres) {
    }

    public int getSasPoints() {
        return sasPoints;
    }

    public void setSasPoints(int sasPoints) {
        this.sasPoints = sasPoints;
    }

    public int getSurfaceAtoms() {
        return surfaceAtoms;
    }

    public void setSurfaceAtoms(int surfaceAtoms) {
        this.surfaceAtoms = surfaceAtoms;
    }

    public double[] getCenter() {
        return center;
    }

    public void setCenter(double[] center) {
        this.center = center;
    }

    public List<String> getResidueIds() {
        return residueIds;
    }

    public void setResidueIds(List<String> residueIds) {
        this.residueIds = residueIds != null ? new ArrayList<>(residueIds) : new ArrayList<>();
    }

    public List<Integer> getSurfaceAtomIds() {
        return surfaceAtomIds;
    }

    public void setSurfaceAtomIds(List<Integer> surfaceAtomIds) {
        this.surfaceAtomIds = surfaceAtomIds != null ? new ArrayList<>(surfaceAtomIds) : new ArrayList<>();
    }
}
