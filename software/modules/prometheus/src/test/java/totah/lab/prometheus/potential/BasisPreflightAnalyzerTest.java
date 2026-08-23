package totah.lab.prometheus.potential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.delta.training.BasisPreflightAnalyzer;
import totah.lab.prometheus.potential.delta.training.BasisPreflightResult.Classification;
import totah.lab.prometheus.potential.delta.training.DeltaModelTrainer;

class BasisPreflightAnalyzerTest {
 @Test void distinguishesNullDuplicateCorrelatedAndObservableDirections(){
  double[][]e={{0,1,2,1,1},{0,2,4,2.001,0},{0,3,6,2.999,1},{0,4,8,4.002,0}};double[][]f={{0,0,0,0,1},{0,0,0,.001,0},{0,0,0,-.001,-1}};
  var result=new BasisPreflightAnalyzer().analyze(e,f,true,true,true,640);
  assertThat(result.columns().get(0).classification()).isEqualTo(Classification.STRUCTURALLY_NULL);
  assertThat(result.columns().get(1).classification()).isEqualTo(Classification.NONZERO_BUT_COLLINEAR);
  assertThat(result.columns().get(2).classification()).isEqualTo(Classification.STRUCTURALLY_DUPLICATE);
  assertThat(result.columns().get(3).classification()).isEqualTo(Classification.NONZERO_BUT_COLLINEAR);
  assertThat(result.columns().get(4).classification()).isEqualTo(Classification.OBSERVABLE);
  assertThat(result.duplicateMap()).containsEntry(2,1);assertThat(result.pass()).isFalse();
  assertThatThrownBy(()->new DeltaModelTrainer().requirePreflight(result)).isInstanceOf(IllegalStateException.class).hasMessageContaining("fitting is prohibited");
 }
 @Test void permitsAnIndependentObservableBasis(){double[][]e={{1,0},{0,1},{1,1}};double[][]f={{1,-1},{2,1}};var result=new BasisPreflightAnalyzer().analyze(e,f,true,true,true,640);assertThat(result.pass()).isTrue();new DeltaModelTrainer().requirePreflight(result);}
}
