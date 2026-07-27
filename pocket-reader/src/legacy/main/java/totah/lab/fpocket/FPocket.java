package totah.lab.fpocket;

import lombok.*;

import totah.lab.pocket.AlphaSphere;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.Residue;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@ToString
public class FPocket implements Pocket {

    private long id;
    private String name; // e.g., "Catalytic Site", "Allosteric Pocket A"

    private double score;

    private double druggabilityScore;

    @Builder.Default
    private FPocketGeometry geometry = new FPocketGeometry();

    private double volume;
    private double volumeScore;

    @Builder.Default
    private List<AlphaSphere> alphaSpheres = new ArrayList<>();

    @Builder.Default
    private FPocketSasa sasa = new FPocketSasa();

    @Builder.Default
    private FPocketChemicalProperties chemistry = new FPocketChemicalProperties();

    @Builder.Default
    private List<Residue> residues = new ArrayList<>();


    @Override
    public void add(Residue residue) {
        if(residue==null){
            return;
        }
        this.residues.add(residue);
    }

    @Override
    public void addResidues(List<Residue> residues) {
        if(residues==null||residues.isEmpty()){
            return;
        }
        this.residues.addAll(residues);
    }

    @Override
    public void addAlphaSpheres(List<AlphaSphere> spheres) {
        if(spheres==null||spheres.isEmpty()){
            return;
        }
        this.alphaSpheres.addAll(spheres);
    }

    public void set(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        // --- GEOMETRY MATCHING BLOCK ---
        if (key.equalsIgnoreCase("Cent. of mass - Alpha Sphere max dist")) {
            if (this.geometry == null) this.geometry = new FPocketGeometry();
            this.geometry.setCentOfMassAlphaSphereMaxDist(Double.parseDouble(value));
        } else if (key.equalsIgnoreCase("Volume")) {
            this.volume = Double.parseDouble(value); // Set nested metric field
        } else if (key.equalsIgnoreCase("Volume score")) {
            this.volumeScore = Double.parseDouble(value);
        }

        // --- CHEMISTRY MATCHING BLOCK ---
        else if (key.equalsIgnoreCase("Hydrophobicity score")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.hydrophobicityScore = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Polarity score")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.polarityScore = Integer.parseInt(value);
        } else if (key.equalsIgnoreCase("Charge score")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.chargeScore = Integer.parseInt(value);
        } else if (key.equalsIgnoreCase("Proportion of polar atoms")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.proportionOfPolarAtoms = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Mean local hydrophobic density")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.meanLocalHydrophobicDensity = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Flexibility")) {
            if (this.chemistry == null) this.chemistry = new FPocketChemicalProperties();
            this.chemistry.flexibility = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Alpha sphere density")) {
            if (this.geometry == null) this.geometry = new FPocketGeometry();
            this.geometry.setAlphaSphereDensity(Double.parseDouble(value));
        }

        // --- GLOBAL ROOT SCORE PATHWAYS ---
        else if (key.equalsIgnoreCase("Score")) {
            this.score = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Druggability Score")) {
            this.druggabilityScore = Double.parseDouble(value);
        }

        // --- SASA MATCHING BLOCK ---
        else if (key.equalsIgnoreCase("Total SASA")) {
            if (this.sasa == null) this.sasa = new FPocketSasa();
            this.sasa.total = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Apolar SASA")) {
            if (this.sasa == null) this.sasa = new FPocketSasa();
            this.sasa.apolar = Double.parseDouble(value);
        } else if (key.equalsIgnoreCase("Polar SASA")) {
            if (this.sasa == null) this.sasa = new FPocketSasa();
            this.sasa.polar = Double.parseDouble(value);
        }

        // --- SPHERES MATCHING BLOCK ---
        else if (key.equalsIgnoreCase("Mean alpha sphere radius")) {
            if (this.geometry == null) this.geometry = new FPocketGeometry();
            this.geometry.setMeanAlphaSphereRadius(Double.parseDouble(value));
        } else if (key.equalsIgnoreCase("Mean alp. sph. solvent access")) {
            if (this.geometry == null) this.geometry = new FPocketGeometry();
            this.geometry.setMeanAlphaSphereSolventAccess(
                    Double.parseDouble(value));
        }else if (key.equalsIgnoreCase("Apolar alpha sphere proportion")) {
            if (this.geometry == null) this.geometry = new FPocketGeometry();
            this.geometry.setApolarAlphaSphereProportion(
                    Double.parseDouble(value)
            );
        }
    }

}
