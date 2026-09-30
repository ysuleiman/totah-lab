"""Physical-error summaries; all inputs/outputs use kcal/mol, no fitted score."""
import math
from statistics import mean

NEUTRAL = 0.01

def diagnosed_iteration_cap(statuses):
    """Distinguish a fully attempted six-component CP case from a harness failure."""
    return len(statuses)==6 and all(s in ('CONVERGED','MAX_ITERATIONS') for s in statuses) and 'MAX_ITERATIONS' in statuses

def ranks(values):
    ordered=sorted(range(len(values)),key=values.__getitem__);result=[0.]*len(values)
    i=0
    while i<len(values):
        j=i+1
        while j<len(values) and values[ordered[j]]==values[ordered[i]]:j+=1
        for k in range(i,j):result[ordered[k]]=(i+j-1)/2+1
        i=j
    return result

def spearman(reference, predicted):
    if len(reference)<2:return None
    a=ranks(reference);b=ranks(predicted);ma=mean(a);mb=mean(b)
    scale=math.sqrt(sum((x-ma)**2 for x in a)*sum((x-mb)**2 for x in b))
    return sum((x-ma)*(y-mb) for x,y in zip(a,b))/scale if scale else None

def sign_correct(reference,predicted):
    if abs(reference)<=NEUTRAL or abs(predicted)<=NEUTRAL:return None
    return reference*predicted>0

def summarize(reference,predicted):
    if len(reference)!=len(predicted):raise ValueError('Mismatched observations')
    if not reference:return {'n':0}
    if not all(math.isfinite(x) for x in reference+predicted):raise ValueError('Nonfinite evidence')
    errors=[p-r for r,p in zip(reference,predicted)]
    signs=[sign_correct(r,p) for r,p in zip(reference,predicted)]
    pairs=[(predicted[i]-predicted[j])*(reference[i]-reference[j])>0
           for i in range(len(reference)) for j in range(i)
           if abs(reference[i]-reference[j])>NEUTRAL]
    return {'n':len(errors),'MAE':mean([abs(e) for e in errors]),
            'RMSE':math.sqrt(mean([e*e for e in errors])),
            'MAX_ERROR':max(abs(e) for e in errors),'BIAS':mean(errors),
            'SPEARMAN':spearman(reference,predicted),'spearman_limitation':'Exploratory; n=2 gives a trivial +/-1, not robust ranking validation; undefined for n<2 or constant ranks',
            'sign_correct_count':sum(x is True for x in signs),'sign_indeterminate_count':sum(x is None for x in signs),
            'pairwise_rank_correct':sum(pairs),'pairwise_rank_comparisons':len(pairs)}

def classify(summary,reference,independent_count,missing):
    if missing or independent_count<2:return 'INSUFFICIENT_EVIDENCE'
    all_signs=summary['sign_correct_count']==summary['n']-summary['sign_indeterminate_count'] and summary['sign_correct_count']>0
    if independent_count>=3 and all_signs and summary['MAE']<=1 and summary['MAX_ERROR']<=2 and (summary['SPEARMAN'] is not None and summary['SPEARMAN']>=.8):
        return 'VALIDATED_USEFUL'
    if all_signs and summary['MAE']<=max(2,.3*mean([abs(x) for x in reference])):return 'QUALITATIVE_ONLY'
    return 'UNRELIABLE'

def d3_change(reference,pbe,pbe_d3):
    delta=abs(pbe_d3-reference)-abs(pbe-reference)
    return 'NEUTRAL' if abs(delta)<=NEUTRAL else 'IMPROVES' if delta<0 else 'WORSENS'
