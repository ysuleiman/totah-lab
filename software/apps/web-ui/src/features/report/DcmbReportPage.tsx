const compounds = [
  {
    name: 'Benzylamine',
    label: 'Historical comparator',
    evidence: 'Historical benzylamine/PNMT SAR comparator. No direct METTL7 activity is assigned.',
    score7a: '−6.017 ± 0.010',
    score7b: '−4.411 ± 0.001',
    conflict7a: '58.3%',
    escape7b: '11.1%',
  },
  {
    name: 'DCMB / LY-78335',
    label: 'Experimentally anchored',
    evidence: 'Reported recombinant human METTL7A inhibitor (IC₅₀ 1.17 µM), with lack of METTL7B inhibition reported in the modern study.',
    score7a: '−6.168 ± 0.002',
    score7b: '−5.262 ± 0.008',
    conflict7a: '44.4%',
    escape7b: '18.3%',
  },
  {
    name: '2,4-dichloro isomer',
    label: 'Positional comparator',
    evidence: 'Historical benzylamine/PNMT SAR comparator. No direct METTL7 activity is assigned.',
    score7a: '−6.628 ± 0.010',
    score7b: '−5.298 ± 0.037',
    conflict7a: '37.5%',
    escape7b: '22.5%',
  },
  {
    name: 'CONH / UK-1187A',
    label: 'Historical inhibitor comparator',
    evidence: 'Historical TMT/PNMT inhibitor comparator. No direct METTL7A-versus-METTL7B experiment was located; no METTL7 selectivity is assigned.',
    score7a: '−6.523 ± 0.035',
    score7b: '−5.453 ± 0.004',
    conflict7a: '27.8%',
    escape7b: '37.5%',
  },
]

const questions = [
  ['Does DCMB retain a distinctive structural phenotype?', 'Partly. Its recurrent 7A leading family conflicts with productive TSL states and redirects in 7B, but neither behavior is unique.'],
  ['Does CONH reproduce the phenotype?', 'Partly. It reproduces strong leading-pose TSL-volume occlusion, but its full ensemble is more bimodal and has more escape poses.'],
  ['Is productive-TSL interference enriched among inhibitors?', 'No. Rank-1 direct conflict occurs for every parent, and the all-pose ensemble does not enrich DCMB and CONH.'],
  ['Is 7B escape specific to DCMB?', 'No. It is shared by the 2,4 isomer and is strongest for CONH.'],
  ['Is the geometry ready for a top-100 screen?', 'No. The model is physically informative but not sufficiently discriminating.'],
]

export function DcmbReportPage() {
  return (
    <article className="dcmb-report">
      <header className="dcmb-report-hero">
        <div>
          <p className="dcmb-kicker">Saved analysis · WT METTL7A / METTL7B + SAM</p>
          <h1>DCMB mechanism validation</h1>
          <p className="dcmb-lede">
            A literature-anchored, matched docking and dynamics checkpoint for
            benzylamine, DCMB, the 2,4 positional isomer, and CONH.
          </p>
        </div>
        <div className="dcmb-gate-stack" aria-label="Readiness verdicts">
          <span className="dcmb-gate partial">Static geometry: PARTIAL</span>
          <span className="dcmb-gate insufficient">Dynamics: INSUFFICIENT SAMPLING</span>
        </div>
      </header>

      <section className="dcmb-callout">
        <strong>Decision</strong>
        <p>
          DCMB and chemically distinct CONH can converge on productive-TSL-conflicting
          leading poses in METTL7A. The controls reproduce the same broad phenotype,
          so current static docking does not discriminate inhibitor behavior well
          enough to authorize the top-100 campaign.
        </p>
      </section>

      <section className="dcmb-section">
        <div className="dcmb-section-heading">
          <p>Matched panel</p>
          <h2>What was compared</h2>
        </div>
        <div className="dcmb-compound-grid">
          {compounds.map((compound) => (
            <article className="dcmb-compound-card" key={compound.name}>
              <span>{compound.label}</span>
              <h3>{compound.name}</h3>
              <p>{compound.evidence}</p>
              <dl>
                <div><dt>7A score</dt><dd>{compound.score7a}</dd></div>
                <div><dt>7B score</dt><dd>{compound.score7b}</dd></div>
                <div><dt>7A TSL conflict</dt><dd>{compound.conflict7a}</dd></div>
                <div><dt>7B escape</dt><dd>{compound.escape7b}</dd></div>
              </dl>
            </article>
          ))}
        </div>
        <p className="dcmb-footnote">
          Scores are reproducible Vina means ± population SD in kcal/mol, not
          measured activities. Conflict and escape values summarize all retained poses.
        </p>
      </section>

      <section className="dcmb-section dcmb-pocket-section">
        <div className="dcmb-section-heading">
          <p>Reference correction</p>
          <h2>METTL7B pocket identity</h2>
        </div>
        <div className="dcmb-pocket-stat"><strong>2</strong><span>FPOCKET pocket</span></div>
        <div className="dcmb-pocket-stat"><strong>1,690.538 Å³</strong><span>volume</span></div>
        <div className="dcmb-pocket-stat"><strong>197</strong><span>alpha spheres</span></div>
        <p>
          The retained <code>pocket1_vert.pqr</code> name is an indexing artifact;
          it does not change the biological pocket number.
        </p>
      </section>

      <section className="dcmb-section">
        <div className="dcmb-section-heading">
          <p>Interpretation</p>
          <h2>Questions answered</h2>
        </div>
        <div className="dcmb-answer-list">
          {questions.map(([question, answer], index) => (
            <div key={question}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <div><h3>{question}</h3><p>{answer}</p></div>
            </div>
          ))}
        </div>
      </section>

      <section className="dcmb-section dcmb-dynamics">
        <div className="dcmb-section-heading">
          <p>Anchor / exit / rebinding study</p>
          <h2>Dynamics checkpoint</h2>
        </div>
        <div className="dcmb-dynamics-grid">
          <div><strong>4</strong><span>systems prepared</span></div>
          <div><strong>12</strong><span>replicas planned</span></div>
          <div><strong>0</strong><span>production frames</span></div>
          <div><strong>0</strong><span>events evaluated</span></div>
        </div>
        <p>
          All four explicit-solvent systems were constructed and minimized. This
          host had no compatible OpenCL device, and CPU propagation did not reach
          production within the bounded run. Exit, re-entry, recapture contacts,
          and dynamic 7A-versus-7B differences therefore remain unevaluated.
        </p>
      </section>

      <footer className="dcmb-report-footer">
        <strong>Current action gate</strong>
        <p>
          Do not start the top-100 screen. Run the preserved systems as longer,
          matched unbiased replicas on GPU hardware; consider enhanced sampling
          only as a separately approved follow-up.
        </p>
      </footer>
    </article>
  )
}
