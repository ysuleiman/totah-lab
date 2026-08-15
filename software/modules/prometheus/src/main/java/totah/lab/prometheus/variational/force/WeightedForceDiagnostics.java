package totah.lab.prometheus.variational.force;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exact weighted scalar force statistics and Qian-style 3-IQR diagnostic. */
public final class WeightedForceDiagnostics {
    private WeightedForceDiagnostics() { }

    public static Result summarize(List<Sample> input) {
        List<Sample> samples=List.copyOf(input);
        if(samples.isEmpty())throw new IllegalArgumentException("force samples required");
        List<Sample> sorted=new ArrayList<>(samples);sorted.sort(Comparator.comparingDouble(Sample::value));
        double q1=quantile(sorted,.25),q3=quantile(sorted,.75),iqr=q3-q1,lower=q1-3*iqr,upper=q3+3*iqr;
        Moments raw=new Moments(),clipped=new Moments();long affected=0;
        for(Sample sample:samples){raw.add(sample.weight(),sample.value());double value=Math.max(lower,Math.min(upper,sample.value()));
            if(value!=sample.value())affected++;clipped.add(sample.weight(),value);}
        return new Result(raw.finish(),clipped.finish(),q1,q3,lower,upper,affected,samples.size());
    }

    private static double quantile(List<Sample> sorted,double fraction){double total=sorted.stream().mapToDouble(Sample::weight).sum();
        double target=fraction*total,cumulative=0;for(Sample sample:sorted){cumulative+=sample.weight();if(cumulative>=target)return sample.value();}
        return sorted.getLast().value();}

    public record Sample(double weight,double value){public Sample{if(!Double.isFinite(weight)||weight<0||!Double.isFinite(value))
        throw new IllegalArgumentException("finite nonnegative weighted sample required");}}
    public record Statistics(double mean,double variance,double standardDeviation,double standardError,double effectiveSampleSize){ }
    public record Result(Statistics raw,Statistics clipped,double q1,double q3,double lowerFence,double upperFence,
            long clippedSamples,long samples){ }
    private static final class Moments{double w,w2,x,x2;void add(double weight,double value){w+=weight;w2+=weight*weight;x+=weight*value;x2+=weight*value*value;}
        Statistics finish(){if(!(w>0)||!(w2>0))throw new IllegalArgumentException("positive weight required");double mean=x/w;
            double variance=Math.max(0,x2/w-mean*mean),ess=w*w/w2;return new Statistics(mean,variance,Math.sqrt(variance),Math.sqrt(variance/ess),ess);}}
}
