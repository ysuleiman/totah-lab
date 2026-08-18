package totah.lab.prometheus.neural;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FeedForwardNetworkTest {
    @Test
    void sharedEvaluationMatchesFiniteDifferenceFirstAndSecondDerivatives() {
        FeedForwardNetwork network=new FeedForwardNetwork(List.of(
                new DenseLayer(ParameterTensor.of(2,1,0.7,-1.2),new double[]{0.2,-0.3},new TanhActivation()),
                new DenseLayer(ParameterTensor.of(1,2,1.1,-0.4),new double[]{0.1},new IdentityActivation())));
        double x=0.37,h=1e-5; NetworkEvaluation evaluation=network.evaluate(x);
        double minus=network.evaluate(x-h).value(),plus=network.evaluate(x+h).value();
        double first=(plus-minus)/(2*h),second=(plus-2*evaluation.value()+minus)/(h*h);
        assertThat(evaluation.inputFirstDerivative()).isCloseTo(first,org.assertj.core.data.Offset.offset(1e-9));
        assertThat(evaluation.inputSecondDerivative()).isCloseTo(second,org.assertj.core.data.Offset.offset(2e-6));
    }

    @Test
    void reverseParameterGradientMatchesFiniteDifference() {
        FeedForwardNetwork network=new FeedForwardNetwork(List.of(
                new DenseLayer(ParameterTensor.of(1,1,0.8),new double[]{-0.2},new TanhActivation()),
                new DenseLayer(ParameterTensor.of(1,1,1.3),new double[]{0.4},new IdentityActivation())));
        double x=0.23,h=1e-6; NetworkEvaluation evaluation=network.evaluate(x);
        for(int i=0;i<network.parameterCount();i++) {
            var plus=new java.util.ArrayList<>(network.parameters().values()); plus.set(i,plus.get(i)+h);
            var minus=new java.util.ArrayList<>(network.parameters().values()); minus.set(i,minus.get(i)-h);
            double numeric=(network.withParameters(new totah.lab.prometheus.variational.ParameterVector(plus)).evaluate(x).value()
                    -network.withParameters(new totah.lab.prometheus.variational.ParameterVector(minus)).evaluate(x).value())/(2*h);
            assertThat(evaluation.parameterGradient().get(i)).isCloseTo(numeric,
                    org.assertj.core.data.Offset.offset(1e-8));
        }
    }
}
