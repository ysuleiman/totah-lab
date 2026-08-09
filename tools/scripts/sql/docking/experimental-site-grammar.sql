-- Orthogonal experimental site-grammar dimensions with no combined score.

CREATE TABLE IF NOT EXISTS docking.experimental_site_grammar_residue (
    alignment_id bigint NOT NULL
        REFERENCES docking.experimental_target_alignment(id) ON DELETE CASCADE,
    query_uniprot_position integer NOT NULL,
    candidate_uniprot_position integer NOT NULL,
    query_residue char(1) NOT NULL,
    candidate_residue char(1) NOT NULL,
    identical boolean NOT NULL,
    substitution_similarity double precision NOT NULL,
    query_chemistry varchar(30) NOT NULL,
    candidate_chemistry varchar(30) NOT NULL,
    chemistry_relationship varchar(30) NOT NULL,
    query_contact_role varchar(30) NOT NULL,
    candidate_contact_role varchar(30) NOT NULL,
    query_direct_observation_count integer NOT NULL,
    query_shell_observation_count integer NOT NULL,
    candidate_direct_observation_count integer NOT NULL,
    candidate_shell_observation_count integer NOT NULL,
    query_structure_observation_count integer NOT NULL,
    candidate_structure_observation_count integer NOT NULL,
    query_structural_status varchar(30) NOT NULL,
    candidate_structural_status varchar(30) NOT NULL,
    query_ca_rmsf double precision,
    candidate_ca_rmsf double precision,
    query_side_chain_rmsf double precision,
    candidate_side_chain_rmsf double precision,
    structural_method varchar(100) NOT NULL,
    structural_method_version varchar(50) NOT NULL,
    query_structural_reason text NOT NULL,
    candidate_structural_reason text NOT NULL,
    origin varchar(30) NOT NULL,
    method varchar(100) NOT NULL,
    method_version varchar(50) NOT NULL,
    derived_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (alignment_id,query_uniprot_position,
                 candidate_uniprot_position,method_version),
    CHECK (substitution_similarity BETWEEN 0 AND 1),
    CHECK (query_contact_role IN ('DIRECT','NEAR_SHELL','NONE')),
    CHECK (candidate_contact_role IN ('DIRECT','NEAR_SHELL','NONE')),
    CHECK (chemistry_relationship IN
           ('IDENTICAL','CONSERVATIVE','CHEMISTRY_COMPATIBLE','DIFFERENT')),
    CHECK (query_structural_status IN
           ('PRESENT','EMPTY','NOT_EVALUATED','NOT_APPLICABLE','FAILED')),
    CHECK (candidate_structural_status IN
           ('PRESENT','EMPTY','NOT_EVALUATED','NOT_APPLICABLE','FAILED')),
    CHECK (origin='DERIVED')
);

CREATE TABLE IF NOT EXISTS docking.experimental_site_grammar_summary (
    alignment_id bigint NOT NULL
        REFERENCES docking.experimental_target_alignment(id) ON DELETE CASCADE,
    aligned_site_residue_count integer NOT NULL,
    exact_identity_fraction double precision,
    mean_substitution_similarity double precision,
    median_substitution_similarity double precision,
    chemistry_match_fraction double precision,
    direct_contact_residue_coverage double precision,
    direct_contact_conservation_fraction double precision,
    shell_conservation_fraction double precision,
    direct_to_shell_shift_count integer NOT NULL,
    chemistry_changing_contact_substitution_count integer NOT NULL,
    structurally_stable_conserved_count integer NOT NULL,
    structurally_variable_conserved_count integer NOT NULL,
    evaluable_structural_position_count integer NOT NULL,
    unavailable_structural_position_count integer NOT NULL,
    structural_stability_threshold double precision NOT NULL,
    method varchar(100) NOT NULL,
    method_version varchar(50) NOT NULL,
    derived_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (alignment_id,method_version),
    CHECK (exact_identity_fraction IS NULL OR
           exact_identity_fraction BETWEEN 0 AND 1),
    CHECK (chemistry_match_fraction IS NULL OR
           chemistry_match_fraction BETWEEN 0 AND 1),
    CHECK (structural_stability_threshold > 0)
);

CREATE INDEX IF NOT EXISTS experimental_site_grammar_role_idx
    ON docking.experimental_site_grammar_residue
       (query_contact_role,candidate_contact_role);
