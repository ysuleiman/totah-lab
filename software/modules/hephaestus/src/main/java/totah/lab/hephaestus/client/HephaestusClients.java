package totah.lab.hephaestus.client;

import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hephaestus.receptor.ReceptorPreparer;
import totah.lab.hephaestus.receptor.ReceptorPreparerBuilder;
import totah.lab.hephaestus.receptor.hydrogen.ReceptorHydrogenator;
import totah.lab.hephaestus.receptor.operation.AD4AtomTypingOperation;
import totah.lab.hephaestus.receptor.operation.AlphaFoldFilterOperation;
import totah.lab.hephaestus.receptor.operation.ChargeAssignmentOperation;
import totah.lab.hephaestus.receptor.operation.HydrogenOptimizationOperation;
import totah.lab.hephaestus.receptor.operation.ReceptorHydrogenationOperation;
import totah.lab.hephaestus.receptor.operation.ResidueStateAssignmentOperation;
import totah.lab.hephaestus.receptor.operation.StructureCleanupOperation;
import totah.lab.hephaestus.receptor.operation.TopologyBuilderOperation;
import totah.lab.hermes.file.reader.BioJavaStructureReader;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriter;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidator;

public final class HephaestusClients {
    private HephaestusClients() {
    }

    public static HephaestusClient createDefault() {
        ReceptorPreparer preparer = new ReceptorPreparerBuilder()
                .add(new StructureCleanupOperation())
                .add(new AlphaFoldFilterOperation())
                .add(new ResidueStateAssignmentOperation())
                .add(new ReceptorHydrogenationOperation(
                        new ReceptorHydrogenator()))
                .add(new HydrogenOptimizationOperation())
                .add(new TopologyBuilderOperation())
                .add(new ChargeAssignmentOperation())
                .add(new AD4AtomTypingOperation())
                .build();
        return new DefaultHephaestusClient(
                new BioJavaStructureReader(),
                new ProteinFactory(),
                preparer,
                new PdbqtWriter(),
                new PdbqtValidator());
    }
}
