package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.ParameterVector;

/**
 * Pure-Java dense network with a scalar input/output. Input first and second
 * derivatives share the forward pass; parameter gradients share its activations.
 */
public final class FeedForwardNetwork {
    private final List<DenseLayer> layers;

    public FeedForwardNetwork(List<DenseLayer> layers) {
        this.layers=List.copyOf(Objects.requireNonNull(layers,"layers"));
        if (this.layers.isEmpty() || this.layers.getFirst().inputSize()!=1
                || this.layers.getLast().outputSize()!=1) {
            throw new IllegalArgumentException("network must have scalar input and output");
        }
        for (int i=1;i<this.layers.size();i++) {
            if (this.layers.get(i-1).outputSize()!=this.layers.get(i).inputSize()) {
                throw new IllegalArgumentException("adjacent layer dimensions disagree");
            }
        }
    }

    public NetworkEvaluation evaluate(double input) {
        List<LayerTrace> traces=new ArrayList<>();
        double[] a={input}, first={1.0}, second={0.0};
        for (DenseLayer layer:layers) {
            double[] z=new double[layer.outputSize()], next=new double[z.length];
            double[] nextFirst=new double[z.length], nextSecond=new double[z.length];
            for(int o=0;o<z.length;o++) {
                z[o]=layer.bias(o); double dz=0.0,d2z=0.0;
                for(int i=0;i<a.length;i++) {
                    double weight=layer.weights().get(o,i); z[o]+=weight*a[i];
                    dz+=weight*first[i]; d2z+=weight*second[i];
                }
                next[o]=layer.activation().value(z[o]);
                double d1=layer.activation().firstDerivative(z[o]);
                nextFirst[o]=d1*dz;
                nextSecond[o]=layer.activation().secondDerivative(z[o])*dz*dz+d1*d2z;
            }
            traces.add(new LayerTrace(a,z,next)); a=next; first=nextFirst; second=nextSecond;
        }
        return new NetworkEvaluation(a[0],first[0],second[0],parameterGradient(traces));
    }

    private List<Double> parameterGradient(List<LayerTrace> traces) {
        double[][] deltas=new double[layers.size()][];
        int last=layers.size()-1; deltas[last]=new double[layers.get(last).outputSize()];
        deltas[last][0]=layers.get(last).activation().firstDerivative(traces.get(last).z()[0]);
        for(int layerIndex=last-1;layerIndex>=0;layerIndex--) {
            DenseLayer layer=layers.get(layerIndex), next=layers.get(layerIndex+1);
            deltas[layerIndex]=new double[layer.outputSize()];
            for(int i=0;i<layer.outputSize();i++) {
                double propagated=0.0;
                for(int o=0;o<next.outputSize();o++) propagated+=next.weights().get(o,i)*deltas[layerIndex+1][o];
                deltas[layerIndex][i]=propagated*layer.activation().firstDerivative(traces.get(layerIndex).z()[i]);
            }
        }
        List<Double> gradient=new ArrayList<>();
        for(int layerIndex=0;layerIndex<layers.size();layerIndex++) {
            DenseLayer layer=layers.get(layerIndex); double[] inputs=traces.get(layerIndex).inputs();
            for(int o=0;o<layer.outputSize();o++) {
                for(double input:inputs) gradient.add(deltas[layerIndex][o]*input);
            }
            for(int o=0;o<layer.outputSize();o++) gradient.add(deltas[layerIndex][o]);
        }
        return List.copyOf(gradient);
    }

    public ParameterVector parameters() {
        List<Double> values=new ArrayList<>();
        for(DenseLayer layer:layers) {
            for(double value:layer.weights().toArray()) values.add(value);
            for(double value:layer.biases()) values.add(value);
        }
        return new ParameterVector(values);
    }

    public FeedForwardNetwork withParameters(ParameterVector parameters) {
        if(parameters.values().size()!=parameterCount()) throw new IllegalArgumentException("parameter count mismatch");
        int offset=0; List<DenseLayer> replacement=new ArrayList<>();
        for(DenseLayer layer:layers) {
            int count=layer.inputSize()*layer.outputSize(); double[] weights=new double[count];
            for(int i=0;i<count;i++) weights[i]=parameters.values().get(offset++);
            double[] biases=new double[layer.outputSize()];
            for(int i=0;i<biases.length;i++) biases[i]=parameters.values().get(offset++);
            replacement.add(new DenseLayer(ParameterTensor.of(layer.outputSize(),layer.inputSize(),weights),
                    biases,layer.activation()));
        }
        return new FeedForwardNetwork(replacement);
    }

    public int parameterCount() { return layers.stream().mapToInt(l->l.inputSize()*l.outputSize()+l.outputSize()).sum(); }

    private record LayerTrace(double[] inputs,double[] z,double[] outputs) { }
}
