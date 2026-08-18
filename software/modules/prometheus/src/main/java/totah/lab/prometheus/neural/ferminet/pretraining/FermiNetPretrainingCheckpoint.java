package totah.lab.prometheus.neural.ferminet.pretraining;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/** Bit-exact, atomic persistence for resumable HF-pretraining campaigns. */
public final class FermiNetPretrainingCheckpoint {
    private static final long MAGIC = 0x50524d5054524e31L;

    private FermiNetPretrainingCheckpoint() {}

    public static void write(
            Path path,
            ReferenceFermiNetPretrainer.PretrainingState state)
            throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        boolean completed = false;
        try {
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeLong(MAGIC);
                writeConfiguration(output, state.protocol());
                output.writeInt(state.completedIterations());
                output.writeLong(state.proposed());
                output.writeLong(state.accepted());
                output.writeDouble(state.bestLoss());
                output.writeInt(state.bestIteration());
                writeDoubles(output, FermiNetStateAccess.parameterSnapshot(state.currentState()));
                writeDoubles(output, state.firstMoment());
                writeDoubles(output, state.secondMoment());
                writeBytes(output, state.randomState());
                writeWalkers(output, state.walkers());
                output.writeBoolean(state.bestState() != null);
                if (state.bestState() != null) {
                    writeDoubles(output, FermiNetStateAccess.parameterSnapshot(state.bestState()));
                    writeWalkers(output, state.bestWalkers());
                }
                output.writeInt(state.lossHistory().size());
                for (double loss : state.lossHistory()) output.writeDouble(loss);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            completed = true;
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    public static ReferenceFermiNetPretrainer.PretrainingState read(
            Path path,
            FermiNetV1State template)
            throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readLong() != MAGIC) throw new IOException("invalid pretraining checkpoint");
            var protocol = readConfiguration(input);
            int completed = input.readInt();
            long proposed = input.readLong();
            long accepted = input.readLong();
            double bestLoss = input.readDouble();
            int bestIteration = input.readInt();
            FermiNetV1State current = FermiNetStateAccess.replaceParameters(template, readDoubles(input));
            double[] firstMoment = readDoubles(input);
            double[] secondMoment = readDoubles(input);
            byte[] random = readBytes(input);
            List<QuantumCoordinates> walkers = readWalkers(input);
            FermiNetV1State best = null;
            List<QuantumCoordinates> bestWalkers = List.of();
            if (input.readBoolean()) {
                best = FermiNetStateAccess.replaceParameters(template, readDoubles(input));
                bestWalkers = readWalkers(input);
            }
            int historyLength = input.readInt();
            List<Double> history = new ArrayList<>(historyLength);
            for (int i = 0; i < historyLength; i++) history.add(input.readDouble());
            if (input.read() != -1) throw new IOException("trailing pretraining checkpoint data");
            return new ReferenceFermiNetPretrainer.PretrainingState(
                    current, walkers, firstMoment, secondMoment, completed, random,
                    best, bestWalkers, bestLoss, bestIteration, history,
                    proposed, accepted, protocol);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid pretraining checkpoint content", exception);
        }
    }

    private static void writeConfiguration(DataOutputStream out, ReferenceFermiNetPretrainer.Configuration c) throws IOException {
        out.writeInt(c.iterations());out.writeInt(c.walkers());out.writeDouble(c.learningRate());
        out.writeDouble(c.moveWidthBohr());out.writeDouble(c.initialWidthBohr());
        out.writeDouble(c.scfFraction());out.writeLong(c.seed());
    }
    private static ReferenceFermiNetPretrainer.Configuration readConfiguration(DataInputStream in) throws IOException {
        return new ReferenceFermiNetPretrainer.Configuration(in.readInt(),in.readInt(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readLong());
    }
    private static void writeDoubles(DataOutputStream out,double[] v)throws IOException{out.writeInt(v.length);for(double x:v)out.writeLong(Double.doubleToRawLongBits(x));}
    private static double[] readDoubles(DataInputStream in)throws IOException{int n=in.readInt();if(n<0)throw new IOException("negative array length");double[]v=new double[n];for(int i=0;i<n;i++)v[i]=Double.longBitsToDouble(in.readLong());return v;}
    private static void writeBytes(DataOutputStream out,byte[]v)throws IOException{out.writeInt(v.length);out.write(v);}
    private static byte[] readBytes(DataInputStream in)throws IOException{int n=in.readInt();if(n<0)throw new IOException("negative byte length");return in.readNBytes(n);}
    private static void writeWalkers(DataOutputStream out,List<QuantumCoordinates>w)throws IOException{out.writeInt(w.size());for(var c:w){out.writeInt(c.particles().size());for(var p:c.particles()){out.writeInt(p.particleIndex());out.writeLong(Double.doubleToRawLongBits(p.xBohr()));out.writeLong(Double.doubleToRawLongBits(p.yBohr()));out.writeLong(Double.doubleToRawLongBits(p.zBohr()));out.writeUTF(p.spin().name());}}}
    private static List<QuantumCoordinates> readWalkers(DataInputStream in)throws IOException{int n=in.readInt();if(n<0)throw new IOException("negative walker count");List<QuantumCoordinates>w=new ArrayList<>(n);for(int i=0;i<n;i++){int m=in.readInt();List<QuantumCoordinates.ParticleCoordinate>p=new ArrayList<>(m);for(int j=0;j<m;j++)p.add(new QuantumCoordinates.ParticleCoordinate(in.readInt(),Double.longBitsToDouble(in.readLong()),Double.longBitsToDouble(in.readLong()),Double.longBitsToDouble(in.readLong()),SpinProjection.valueOf(in.readUTF())));w.add(new QuantumCoordinates(p));}return List.copyOf(w);}
}
