--
-- PostgreSQL database dump
--

\restrict TB6nSjh4p0W4PeOdtbg6jcfTYgOQm1FgvkyWvAKcBMhN7ofZta8mXkhgt1PdCTi

-- Dumped from database version 15.3
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: docking; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA docking;


--
-- Name: cube; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS cube WITH SCHEMA public;


--
-- Name: EXTENSION cube; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION cube IS 'data type for multidimensional cubes';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: docking_pose; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.docking_pose (
    id bigint NOT NULL,
    ligand_id character varying(32) NOT NULL,
    vina_score double precision NOT NULL,
    pose_file text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    receptor_id character varying(50),
    run_id bigint NOT NULL
);


--
-- Name: pocket_atom; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pocket_atom (
    id bigint NOT NULL,
    pocket_residue_id bigint NOT NULL,
    atom_name character varying(8),
    x double precision,
    y double precision,
    z double precision,
    element character varying(4),
    coords public.cube
);


--
-- Name: pose_atom_contact; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pose_atom_contact (
    id bigint NOT NULL,
    pose_id bigint NOT NULL,
    pose_atom_id bigint NOT NULL,
    pocket_atom_id bigint NOT NULL,
    pocket_residue_id bigint NOT NULL,
    distance_angstroms double precision NOT NULL
);


--
-- Name: pose_residue_contact; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.pose_residue_contact AS
 SELECT pac.pose_id,
    pa.pocket_residue_id,
    count(*) AS atom_contact_count,
    min(pac.distance_angstroms) AS min_distance
   FROM (docking.pose_atom_contact pac
     JOIN docking.pocket_atom pa ON ((pa.id = pac.pocket_atom_id)))
  GROUP BY pac.pose_id, pa.pocket_residue_id
  WITH NO DATA;


--
-- Name: bad_pose_residue_pair; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.bad_pose_residue_pair AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    count(*) AS pose_count
   FROM ((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  WHERE (dp.vina_score >= ('-6'::integer)::double precision)
  GROUP BY a.pocket_residue_id, b.pocket_residue_id
  WITH NO DATA;


--
-- Name: bad_pose_residue_triplet; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.bad_pose_residue_triplet AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    c.pocket_residue_id AS residue_id_3,
    count(*) AS pose_count
   FROM (((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.pose_residue_contact c ON (((c.pose_id = a.pose_id) AND (c.pocket_residue_id > b.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  WHERE (dp.vina_score >= ('-6'::integer)::double precision)
  GROUP BY a.pocket_residue_id, b.pocket_residue_id, c.pocket_residue_id
  WITH NO DATA;


--
-- Name: docking_pose_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.docking_pose_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: docking_pose_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.docking_pose_id_seq OWNED BY docking.docking_pose.id;


--
-- Name: docking_run; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.docking_run (
    id bigint NOT NULL,
    receptor_id bigint,
    grid_center_x double precision,
    grid_center_y double precision,
    grid_center_z double precision,
    grid_size_x double precision,
    grid_size_y double precision,
    grid_size_z double precision,
    vina_version character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: docking_run_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.docking_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: docking_run_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.docking_run_id_seq OWNED BY docking.docking_run.id;


--
-- Name: good_pose_residue_pair; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.good_pose_residue_pair AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    count(*) AS pose_count
   FROM ((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  WHERE (dp.vina_score < ('-6'::integer)::double precision)
  GROUP BY a.pocket_residue_id, b.pocket_residue_id
  WITH NO DATA;


--
-- Name: good_pose_residue_triplet; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.good_pose_residue_triplet AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    c.pocket_residue_id AS residue_id_3,
    count(*) AS pose_count
   FROM (((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.pose_residue_contact c ON (((c.pose_id = a.pose_id) AND (c.pocket_residue_id > b.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  WHERE (dp.vina_score < ('-6'::integer)::double precision)
  GROUP BY a.pocket_residue_id, b.pocket_residue_id, c.pocket_residue_id
  WITH NO DATA;


--
-- Name: pocket; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pocket (
    id bigint NOT NULL,
    receptor_id bigint,
    pocket_number integer,
    fpocket_file text,
    volume double precision,
    druggability_score double precision
);


--
-- Name: pocket_atom_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.pocket_atom_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pocket_atom_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.pocket_atom_id_seq OWNED BY docking.pocket_atom.id;


--
-- Name: pocket_atom_residue_backup_20260727; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pocket_atom_residue_backup_20260727 (
    id bigint,
    pocket_residue_id bigint
);


--
-- Name: pocket_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.pocket_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pocket_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.pocket_id_seq OWNED BY docking.pocket.id;


--
-- Name: pocket_residue; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pocket_residue (
    pocket_id bigint NOT NULL,
    chain character(1) NOT NULL,
    residue_number integer NOT NULL,
    residue_name character varying(3),
    id bigint NOT NULL
);


--
-- Name: pocket_residue_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.pocket_residue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pocket_residue_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.pocket_residue_id_seq OWNED BY docking.pocket_residue.id;


--
-- Name: pose_atom; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pose_atom (
    id bigint NOT NULL,
    pose_id bigint NOT NULL,
    atom_index integer,
    atom_name character varying(8),
    element character varying(2),
    x double precision NOT NULL,
    y double precision NOT NULL,
    z double precision NOT NULL,
    charge double precision,
    autodock_type character varying(8),
    coords public.cube
);


--
-- Name: pose_atom_contact_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

ALTER TABLE docking.pose_atom_contact ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME docking.pose_atom_contact_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pose_atom_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.pose_atom_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pose_atom_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.pose_atom_id_seq OWNED BY docking.pose_atom.id;


--
-- Name: pose_contact; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pose_contact (
    pose_id bigint NOT NULL,
    chain character(1) NOT NULL,
    residue_number integer NOT NULL,
    residue_name character varying(3),
    min_distance double precision
);


--
-- Name: pose_contact_residue; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.pose_contact_residue (
    id bigint NOT NULL,
    pose_id bigint NOT NULL,
    chain_id character varying(4),
    residue_name character varying(8) NOT NULL,
    residue_number integer NOT NULL,
    min_distance double precision
);


--
-- Name: pose_contact_residue_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.pose_contact_residue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pose_contact_residue_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.pose_contact_residue_id_seq OWNED BY docking.pose_contact_residue.id;


--
-- Name: receptor; Type: TABLE; Schema: docking; Owner: -
--

CREATE TABLE docking.receptor (
    id bigint NOT NULL,
    target_name character varying(100),
    pdb_file text,
    pdbqt_file text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: receptor_id_seq; Type: SEQUENCE; Schema: docking; Owner: -
--

CREATE SEQUENCE docking.receptor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: receptor_id_seq; Type: SEQUENCE OWNED BY; Schema: docking; Owner: -
--

ALTER SEQUENCE docking.receptor_id_seq OWNED BY docking.receptor.id;


--
-- Name: residue_contact_by_score_band; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_contact_by_score_band AS
 WITH pose_residue AS (
         SELECT pac.pose_id,
            pa.pocket_residue_id,
            count(*) AS atom_contact_count,
            min(pac.distance_angstroms) AS min_distance
           FROM (docking.pose_atom_contact pac
             JOIN docking.pocket_atom pa ON ((pa.id = pac.pocket_atom_id)))
          GROUP BY pac.pose_id, pa.pocket_residue_id
        ), scored AS (
         SELECT pr_1.pose_id,
            pr_1.pocket_residue_id,
            pr_1.atom_contact_count,
            pr_1.min_distance,
            ((floor(((dp.vina_score + (6.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (6.0)::double precision) AS score_lower
           FROM (pose_residue pr_1
             JOIN docking.docking_pose dp ON ((dp.id = pr_1.pose_id)))
        ), pose_bands AS (
         SELECT ((floor(((docking_pose.vina_score + (6.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (6.0)::double precision) AS score_lower,
            count(*) AS pose_count
           FROM docking.docking_pose
          GROUP BY ((floor(((docking_pose.vina_score + (6.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (6.0)::double precision)
        ), contacts AS (
         SELECT scored.score_lower,
            scored.pocket_residue_id,
            count(*) AS contacting_pose_count,
            (sum(scored.atom_contact_count))::bigint AS atom_contact_count,
            min(scored.min_distance) AS closest_distance,
            avg(scored.min_distance) AS avg_pose_min_distance
           FROM scored
          GROUP BY scored.score_lower, scored.pocket_residue_id
        )
 SELECT pb.score_lower,
    (pb.score_lower + (2.0)::double precision) AS score_upper,
    (((('['::text || (pb.score_lower)::text) || ','::text) || ((pb.score_lower + (2.0)::double precision))::text) || ')'::text) AS score_band,
    pr.id AS pocket_residue_id,
    pr.chain,
    pr.residue_number,
    pr.residue_name,
    pb.pose_count,
    COALESCE(c.contacting_pose_count, (0)::bigint) AS contacting_pose_count,
    COALESCE(c.atom_contact_count, (0)::bigint) AS atom_contact_count,
    ((COALESCE(c.contacting_pose_count, (0)::bigint))::double precision / (NULLIF(pb.pose_count, 0))::double precision) AS contacting_pose_fraction,
    c.closest_distance,
    c.avg_pose_min_distance
   FROM ((pose_bands pb
     CROSS JOIN docking.pocket_residue pr)
     LEFT JOIN contacts c ON (((c.score_lower = pb.score_lower) AND (c.pocket_residue_id = pr.id))))
  WITH NO DATA;


--
-- Name: residue_contact_score_band_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_contact_score_band_summary AS
 SELECT ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision) AS score_lower,
    ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision) AS score_upper,
    prc.pocket_residue_id,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM (docking.pose_residue_contact prc
     JOIN docking.docking_pose dp ON ((dp.id = prc.pose_id)))
  GROUP BY ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision), ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision), prc.pocket_residue_id
  WITH NO DATA;


--
-- Name: residue_contact_score_band_named; Type: VIEW; Schema: docking; Owner: -
--

CREATE VIEW docking.residue_contact_score_band_named AS
 SELECT s.score_lower,
    s.score_upper,
    (((('['::text || (s.score_lower)::text) || ','::text) || (s.score_upper)::text) || ')'::text) AS score_band,
    pr.residue_name,
    pr.chain,
    pr.residue_number,
    s.pose_count,
    s.avg_score,
    s.median_score,
    s.best_score,
    s.worst_score
   FROM (docking.residue_contact_score_band_summary s
     JOIN docking.pocket_residue pr ON ((pr.id = s.pocket_residue_id)));


--
-- Name: residue_contact_score_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_contact_score_summary AS
 SELECT prc.pocket_residue_id,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM (docking.pose_residue_contact prc
     JOIN docking.docking_pose dp ON ((dp.id = prc.pose_id)))
  GROUP BY prc.pocket_residue_id
  WITH NO DATA;


--
-- Name: residue_enrichment_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_enrichment_summary AS
 WITH totals AS (
         SELECT count(*) FILTER (WHERE (docking_pose.vina_score <= ('-9'::integer)::double precision)) AS total_good,
            count(*) FILTER (WHERE (docking_pose.vina_score >= ('-6'::integer)::double precision)) AS total_bad,
            count(*) AS total_poses
           FROM docking.docking_pose
        ), counts AS (
         SELECT prc.pocket_residue_id,
            count(*) AS total_contacts,
            count(*) FILTER (WHERE (dp.vina_score <= ('-9'::integer)::double precision)) AS good_contacts,
            count(*) FILTER (WHERE (dp.vina_score >= ('-6'::integer)::double precision)) AS bad_contacts,
            avg(dp.vina_score) AS avg_score,
            percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score
           FROM (docking.pose_residue_contact prc
             JOIN docking.docking_pose dp ON ((dp.id = prc.pose_id)))
          GROUP BY prc.pocket_residue_id
        )
 SELECT pr.id AS pocket_residue_id,
    pr.residue_name,
    pr.chain,
    pr.residue_number,
    c.total_contacts,
    c.good_contacts,
    c.bad_contacts,
    round((c.avg_score)::numeric, 3) AS avg_score,
    round((c.median_score)::numeric, 3) AS median_score,
    round(((c.total_contacts)::numeric / (NULLIF(t.total_poses, 0))::numeric), 5) AS overall_contact_fraction,
    round(((c.good_contacts)::numeric / (NULLIF(t.total_good, 0))::numeric), 5) AS good_contact_fraction,
    round(((c.bad_contacts)::numeric / (NULLIF(t.total_bad, 0))::numeric), 5) AS bad_contact_fraction,
    round(((((c.good_contacts)::numeric + 0.5) / ((t.total_good)::numeric + 1.0)) / (((c.bad_contacts)::numeric + 0.5) / ((t.total_bad)::numeric + 1.0))), 4) AS enrichment_ratio,
    round(log((2)::numeric, ((((c.good_contacts)::numeric + 0.5) / ((t.total_good)::numeric + 1.0)) / (((c.bad_contacts)::numeric + 0.5) / ((t.total_bad)::numeric + 1.0)))), 4) AS log2_enrichment
   FROM ((counts c
     JOIN docking.pocket_residue pr ON ((pr.id = c.pocket_residue_id)))
     CROSS JOIN totals t)
  WITH NO DATA;


--
-- Name: residue_pair_enrichment_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_pair_enrichment_summary AS
 WITH score_totals AS (
         SELECT count(*) FILTER (WHERE (docking_pose.vina_score <= ('-9'::integer)::double precision)) AS total_good_poses,
            count(*) FILTER (WHERE (docking_pose.vina_score >= ('-6'::integer)::double precision)) AS total_bad_poses
           FROM docking.docking_pose
        ), relevant_contacts AS (
         SELECT DISTINCT prc.pose_id,
            prc.pocket_residue_id,
                CASE
                    WHEN (dp.vina_score <= ('-9'::integer)::double precision) THEN 'GOOD'::text
                    WHEN (dp.vina_score >= ('-6'::integer)::double precision) THEN 'BAD'::text
                    ELSE NULL::text
                END AS score_class
           FROM (docking.pose_residue_contact prc
             JOIN docking.docking_pose dp ON ((dp.id = prc.pose_id)))
          WHERE ((dp.vina_score <= ('-9'::integer)::double precision) OR (dp.vina_score >= ('-6'::integer)::double precision))
        ), pair_counts AS (
         SELECT a.pocket_residue_id AS residue1_id,
            b.pocket_residue_id AS residue2_id,
            count(*) AS classified_pair_poses,
            count(*) FILTER (WHERE (a.score_class = 'GOOD'::text)) AS good_pair_poses,
            count(*) FILTER (WHERE (a.score_class = 'BAD'::text)) AS bad_pair_poses
           FROM (relevant_contacts a
             JOIN relevant_contacts b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
          GROUP BY a.pocket_residue_id, b.pocket_residue_id
        ), pair_statistics AS (
         SELECT pc.residue1_id,
            pc.residue2_id,
            pc.classified_pair_poses,
            pc.good_pair_poses,
            pc.bad_pair_poses,
            ((((pc.good_pair_poses)::numeric + 0.5) / ((st.total_good_poses)::numeric + 1.0)) / (((pc.bad_pair_poses)::numeric + 0.5) / ((st.total_bad_poses)::numeric + 1.0))) AS pair_enrichment,
            ((pc.good_pair_poses)::numeric / (NULLIF(st.total_good_poses, 0))::numeric) AS good_pair_fraction,
            ((pc.bad_pair_poses)::numeric / (NULLIF(st.total_bad_poses, 0))::numeric) AS bad_pair_fraction
           FROM (pair_counts pc
             CROSS JOIN score_totals st)
        )
 SELECT ps.residue1_id,
    r1.residue_name AS residue1_name,
    r1.chain AS residue1_chain,
    r1.residue_number AS residue1_number,
    ps.residue2_id,
    r2.residue_name AS residue2_name,
    r2.chain AS residue2_chain,
    r2.residue_number AS residue2_number,
    ps.classified_pair_poses,
    ps.good_pair_poses,
    ps.bad_pair_poses,
    round(ps.good_pair_fraction, 6) AS good_pair_fraction,
    round(ps.bad_pair_fraction, 6) AS bad_pair_fraction,
    round(ps.pair_enrichment, 4) AS pair_enrichment,
    round(e1.enrichment_ratio, 4) AS residue1_enrichment,
    round(e2.enrichment_ratio, 4) AS residue2_enrichment,
    round((e1.enrichment_ratio * e2.enrichment_ratio), 4) AS expected_pair_enrichment,
    round((ps.pair_enrichment / NULLIF((e1.enrichment_ratio * e2.enrichment_ratio), (0)::numeric)), 4) AS synergy_ratio,
    round(log((2)::numeric, (ps.pair_enrichment / NULLIF((e1.enrichment_ratio * e2.enrichment_ratio), (0)::numeric))), 4) AS log2_synergy
   FROM ((((pair_statistics ps
     JOIN docking.pocket_residue r1 ON ((r1.id = ps.residue1_id)))
     JOIN docking.pocket_residue r2 ON ((r2.id = ps.residue2_id)))
     JOIN docking.residue_enrichment_summary e1 ON ((e1.pocket_residue_id = ps.residue1_id)))
     JOIN docking.residue_enrichment_summary e2 ON ((e2.pocket_residue_id = ps.residue2_id)))
  WITH NO DATA;


--
-- Name: residue_pair_score_band_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_pair_score_band_summary AS
 SELECT ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision) AS score_lower,
    ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision) AS score_upper,
    a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM ((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  GROUP BY ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision), ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision), a.pocket_residue_id, b.pocket_residue_id
  WITH NO DATA;


--
-- Name: residue_pair_score_band_named; Type: VIEW; Schema: docking; Owner: -
--

CREATE VIEW docking.residue_pair_score_band_named AS
 SELECT s.score_lower,
    s.score_upper,
    (((('['::text || (s.score_lower)::text) || ','::text) || (s.score_upper)::text) || ')'::text) AS score_band,
    p1.residue_name AS residue_name_1,
    p1.chain AS chain_1,
    p1.residue_number AS residue_number_1,
    p2.residue_name AS residue_name_2,
    p2.chain AS chain_2,
    p2.residue_number AS residue_number_2,
    s.pose_count,
    s.avg_score,
    s.median_score,
    s.best_score,
    s.worst_score
   FROM ((docking.residue_pair_score_band_summary s
     JOIN docking.pocket_residue p1 ON ((p1.id = s.residue_id_1)))
     JOIN docking.pocket_residue p2 ON ((p2.id = s.residue_id_2)));


--
-- Name: residue_pair_score_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_pair_score_summary AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM ((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  GROUP BY a.pocket_residue_id, b.pocket_residue_id
  WITH NO DATA;


--
-- Name: residue_triplet_score_band_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_triplet_score_band_summary AS
 SELECT ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision) AS score_lower,
    ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision) AS score_upper,
    a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    c.pocket_residue_id AS residue_id_3,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM (((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.pose_residue_contact c ON (((c.pose_id = a.pose_id) AND (c.pocket_residue_id > b.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  GROUP BY ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (5.0)::double precision), ((floor(((dp.vina_score + (5.0)::double precision) / (2.0)::double precision)) * (2.0)::double precision) - (3.0)::double precision), a.pocket_residue_id, b.pocket_residue_id, c.pocket_residue_id
  WITH NO DATA;


--
-- Name: residue_triplet_score_band_named; Type: VIEW; Schema: docking; Owner: -
--

CREATE VIEW docking.residue_triplet_score_band_named AS
 SELECT s.score_lower,
    s.score_upper,
    (((('['::text || (s.score_lower)::text) || ','::text) || (s.score_upper)::text) || ')'::text) AS score_band,
    p1.residue_name AS residue_name_1,
    p1.chain AS chain_1,
    p1.residue_number AS residue_number_1,
    p2.residue_name AS residue_name_2,
    p2.chain AS chain_2,
    p2.residue_number AS residue_number_2,
    p3.residue_name AS residue_name_3,
    p3.chain AS chain_3,
    p3.residue_number AS residue_number_3,
    s.pose_count,
    s.avg_score,
    s.median_score,
    s.best_score,
    s.worst_score
   FROM (((docking.residue_triplet_score_band_summary s
     JOIN docking.pocket_residue p1 ON ((p1.id = s.residue_id_1)))
     JOIN docking.pocket_residue p2 ON ((p2.id = s.residue_id_2)))
     JOIN docking.pocket_residue p3 ON ((p3.id = s.residue_id_3)));


--
-- Name: residue_triplet_score_summary; Type: MATERIALIZED VIEW; Schema: docking; Owner: -
--

CREATE MATERIALIZED VIEW docking.residue_triplet_score_summary AS
 SELECT a.pocket_residue_id AS residue_id_1,
    b.pocket_residue_id AS residue_id_2,
    c.pocket_residue_id AS residue_id_3,
    count(*) AS pose_count,
    avg(dp.vina_score) AS avg_score,
    percentile_cont((0.5)::double precision) WITHIN GROUP (ORDER BY dp.vina_score) AS median_score,
    min(dp.vina_score) AS best_score,
    max(dp.vina_score) AS worst_score
   FROM (((docking.pose_residue_contact a
     JOIN docking.pose_residue_contact b ON (((b.pose_id = a.pose_id) AND (b.pocket_residue_id > a.pocket_residue_id))))
     JOIN docking.pose_residue_contact c ON (((c.pose_id = a.pose_id) AND (c.pocket_residue_id > b.pocket_residue_id))))
     JOIN docking.docking_pose dp ON ((dp.id = a.pose_id)))
  GROUP BY a.pocket_residue_id, b.pocket_residue_id, c.pocket_residue_id
  WITH NO DATA;


--
-- Name: artifacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifacts (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    filename character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    storage_location character varying(1024) NOT NULL,
    pipeline_run_id bigint NOT NULL,
    target_id bigint NOT NULL
);


--
-- Name: artifacts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.artifacts ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.artifacts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: compound_ligands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compound_ligands (
    id bigint NOT NULL,
    charge integer NOT NULL,
    denticity integer NOT NULL,
    formula character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: compound_ligands_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.compound_ligands ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.compound_ligands_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: docked_poses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.docked_poses (
    id bigint NOT NULL,
    binding_affinity double precision NOT NULL,
    output_file_path character varying(255) NOT NULL,
    pose_number integer NOT NULL,
    docking_run_id bigint NOT NULL,
    ligand_id bigint NOT NULL,
    pocket_id bigint NOT NULL
);


--
-- Name: docked_poses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.docked_poses ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.docked_poses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: docking_run_pockets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.docking_run_pockets (
    docking_run_id bigint NOT NULL,
    pocket_id bigint NOT NULL
);


--
-- Name: docking_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.docking_runs (
    id bigint NOT NULL,
    exhaustiveness integer NOT NULL,
    run_date timestamp(6) without time zone,
    software_name character varying(255) NOT NULL,
    software_version character varying(255)
);


--
-- Name: docking_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.docking_runs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.docking_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pipeline_run_inputs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pipeline_run_inputs (
    pipeline_run_id bigint NOT NULL,
    input_value character varying(255),
    input_key character varying(255) NOT NULL
);


--
-- Name: pipeline_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pipeline_runs (
    id bigint NOT NULL,
    end_time timestamp(6) without time zone,
    start_time timestamp(6) without time zone,
    status character varying(255) NOT NULL
);


--
-- Name: pipeline_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pipeline_runs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.pipeline_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pockets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pockets (
    id bigint NOT NULL,
    gridx double precision NOT NULL,
    gridy double precision NOT NULL,
    gridz double precision NOT NULL,
    pocket_name character varying(255) NOT NULL,
    sizex double precision NOT NULL,
    sizey double precision NOT NULL,
    sizez double precision NOT NULL,
    target_id bigint NOT NULL
);


--
-- Name: pockets_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pockets ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.pockets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pose_atoms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pose_atoms (
    id bigint NOT NULL,
    atom_name character varying(255) NOT NULL,
    element character varying(2) NOT NULL,
    partial_charge double precision NOT NULL,
    x double precision NOT NULL,
    y double precision NOT NULL,
    z double precision NOT NULL,
    pose_id bigint NOT NULL
);


--
-- Name: pose_atoms_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pose_atoms ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.pose_atoms_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pose_contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pose_contacts (
    id bigint NOT NULL,
    angle double precision,
    chain_id character varying(255),
    contact_type character varying(255) NOT NULL,
    distance double precision NOT NULL,
    residue_name character varying(255) NOT NULL,
    residue_number integer NOT NULL,
    pose_id bigint NOT NULL,
    CONSTRAINT pose_contacts_contact_type_check CHECK (((contact_type)::text = ANY ((ARRAY['HYDROGEN_BOND'::character varying, 'HYDROPHOBIC'::character varying, 'PI_STACKING'::character varying, 'SALT_BRIDGE'::character varying, 'HALOGEN_BOND'::character varying])::text[])))
);


--
-- Name: pose_contacts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pose_contacts ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.pose_contacts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: stage_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stage_runs (
    id bigint NOT NULL,
    end_time timestamp(6) without time zone,
    stage_name character varying(255) NOT NULL,
    start_time timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    pipeline_run_id bigint NOT NULL
);


--
-- Name: stage_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.stage_runs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.stage_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: target_other_names; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.target_other_names (
    target_id bigint NOT NULL,
    alternative_name character varying(255)
);


--
-- Name: targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.targets (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    uniprot_id character varying(255) NOT NULL
);


--
-- Name: targets_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.targets ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.targets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: docking_pose id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_pose ALTER COLUMN id SET DEFAULT nextval('docking.docking_pose_id_seq'::regclass);


--
-- Name: docking_run id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_run ALTER COLUMN id SET DEFAULT nextval('docking.docking_run_id_seq'::regclass);


--
-- Name: pocket id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket ALTER COLUMN id SET DEFAULT nextval('docking.pocket_id_seq'::regclass);


--
-- Name: pocket_atom id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_atom ALTER COLUMN id SET DEFAULT nextval('docking.pocket_atom_id_seq'::regclass);


--
-- Name: pocket_residue id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_residue ALTER COLUMN id SET DEFAULT nextval('docking.pocket_residue_id_seq'::regclass);


--
-- Name: pose_atom id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom ALTER COLUMN id SET DEFAULT nextval('docking.pose_atom_id_seq'::regclass);


--
-- Name: pose_contact_residue id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_contact_residue ALTER COLUMN id SET DEFAULT nextval('docking.pose_contact_residue_id_seq'::regclass);


--
-- Name: receptor id; Type: DEFAULT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.receptor ALTER COLUMN id SET DEFAULT nextval('docking.receptor_id_seq'::regclass);


--
-- Name: docking_pose docking_pose_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_pose
    ADD CONSTRAINT docking_pose_pkey PRIMARY KEY (id);


--
-- Name: docking_run docking_run_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_run
    ADD CONSTRAINT docking_run_pkey PRIMARY KEY (id);


--
-- Name: pocket_atom pocket_atom_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_atom
    ADD CONSTRAINT pocket_atom_pkey PRIMARY KEY (id);


--
-- Name: pocket pocket_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket
    ADD CONSTRAINT pocket_pkey PRIMARY KEY (id);


--
-- Name: pocket_residue pocket_residue_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_residue
    ADD CONSTRAINT pocket_residue_pkey PRIMARY KEY (id);


--
-- Name: pocket_residue pocket_residue_uk; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_residue
    ADD CONSTRAINT pocket_residue_uk UNIQUE (pocket_id, chain, residue_number);


--
-- Name: pose_atom_contact pose_atom_contact_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom_contact
    ADD CONSTRAINT pose_atom_contact_pkey PRIMARY KEY (id);


--
-- Name: pose_atom pose_atom_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom
    ADD CONSTRAINT pose_atom_pkey PRIMARY KEY (id);


--
-- Name: pose_contact pose_contact_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_contact
    ADD CONSTRAINT pose_contact_pkey PRIMARY KEY (pose_id, chain, residue_number);


--
-- Name: pose_contact_residue pose_contact_residue_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_contact_residue
    ADD CONSTRAINT pose_contact_residue_pkey PRIMARY KEY (id);


--
-- Name: receptor receptor_pkey; Type: CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.receptor
    ADD CONSTRAINT receptor_pkey PRIMARY KEY (id);


--
-- Name: artifacts artifacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT artifacts_pkey PRIMARY KEY (id);


--
-- Name: compound_ligands compound_ligands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compound_ligands
    ADD CONSTRAINT compound_ligands_pkey PRIMARY KEY (id);


--
-- Name: docked_poses docked_poses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docked_poses
    ADD CONSTRAINT docked_poses_pkey PRIMARY KEY (id);


--
-- Name: docking_runs docking_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docking_runs
    ADD CONSTRAINT docking_runs_pkey PRIMARY KEY (id);


--
-- Name: pipeline_run_inputs pipeline_run_inputs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pipeline_run_inputs
    ADD CONSTRAINT pipeline_run_inputs_pkey PRIMARY KEY (pipeline_run_id, input_key);


--
-- Name: pipeline_runs pipeline_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pipeline_runs
    ADD CONSTRAINT pipeline_runs_pkey PRIMARY KEY (id);


--
-- Name: pockets pockets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pockets
    ADD CONSTRAINT pockets_pkey PRIMARY KEY (id);


--
-- Name: pose_atoms pose_atoms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pose_atoms
    ADD CONSTRAINT pose_atoms_pkey PRIMARY KEY (id);


--
-- Name: pose_contacts pose_contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pose_contacts
    ADD CONSTRAINT pose_contacts_pkey PRIMARY KEY (id);


--
-- Name: stage_runs stage_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_runs
    ADD CONSTRAINT stage_runs_pkey PRIMARY KEY (id);


--
-- Name: targets targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targets
    ADD CONSTRAINT targets_pkey PRIMARY KEY (id);


--
-- Name: targets ukjy6ya5h5eotiah5ouc243dk1v; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targets
    ADD CONSTRAINT ukjy6ya5h5eotiah5ouc243dk1v UNIQUE (uniprot_id);


--
-- Name: bad_pose_residue_pair_count_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX bad_pose_residue_pair_count_idx ON docking.bad_pose_residue_pair USING btree (pose_count DESC);


--
-- Name: bad_pose_residue_pair_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX bad_pose_residue_pair_uk ON docking.bad_pose_residue_pair USING btree (residue_id_1, residue_id_2);


--
-- Name: bad_pose_residue_triplet_count_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX bad_pose_residue_triplet_count_idx ON docking.bad_pose_residue_triplet USING btree (pose_count DESC);


--
-- Name: bad_pose_residue_triplet_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX bad_pose_residue_triplet_uk ON docking.bad_pose_residue_triplet USING btree (residue_id_1, residue_id_2, residue_id_3);


--
-- Name: docking_pose_score_id_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX docking_pose_score_id_idx ON docking.docking_pose USING btree (vina_score, id);


--
-- Name: good_pose_residue_pair_count_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX good_pose_residue_pair_count_idx ON docking.good_pose_residue_pair USING btree (pose_count DESC);


--
-- Name: good_pose_residue_pair_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX good_pose_residue_pair_uk ON docking.good_pose_residue_pair USING btree (residue_id_1, residue_id_2);


--
-- Name: good_pose_residue_triplet_count_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX good_pose_residue_triplet_count_idx ON docking.good_pose_residue_triplet USING btree (pose_count DESC);


--
-- Name: good_pose_residue_triplet_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX good_pose_residue_triplet_uk ON docking.good_pose_residue_triplet USING btree (residue_id_1, residue_id_2, residue_id_3);


--
-- Name: idx_docking_pose_ligand; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX idx_docking_pose_ligand ON docking.docking_pose USING btree (ligand_id);


--
-- Name: idx_docking_pose_score; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX idx_docking_pose_score ON docking.docking_pose USING btree (vina_score);


--
-- Name: idx_pose_atom_pose_id; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX idx_pose_atom_pose_id ON docking.pose_atom USING btree (pose_id);


--
-- Name: pose_residue_contact_pose_residue_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX pose_residue_contact_pose_residue_idx ON docking.pose_residue_contact USING btree (pose_id, pocket_residue_id);


--
-- Name: pose_residue_contact_residue_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX pose_residue_contact_residue_idx ON docking.pose_residue_contact USING btree (pocket_residue_id, pose_id);


--
-- Name: pose_residue_contact_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX pose_residue_contact_uk ON docking.pose_residue_contact USING btree (pose_id, pocket_residue_id);


--
-- Name: residue_contact_by_score_band_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_contact_by_score_band_uk ON docking.residue_contact_by_score_band USING btree (score_lower, pocket_residue_id);


--
-- Name: residue_contact_score_band_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_contact_score_band_summary_uk ON docking.residue_contact_score_band_summary USING btree (score_lower, pocket_residue_id);


--
-- Name: residue_contact_score_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_contact_score_summary_uk ON docking.residue_contact_score_summary USING btree (pocket_residue_id);


--
-- Name: residue_enrichment_summary_ratio_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX residue_enrichment_summary_ratio_idx ON docking.residue_enrichment_summary USING btree (enrichment_ratio DESC);


--
-- Name: residue_enrichment_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_enrichment_summary_uk ON docking.residue_enrichment_summary USING btree (pocket_residue_id);


--
-- Name: residue_pair_enrichment_pair_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX residue_pair_enrichment_pair_idx ON docking.residue_pair_enrichment_summary USING btree (pair_enrichment DESC);


--
-- Name: residue_pair_enrichment_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_pair_enrichment_summary_uk ON docking.residue_pair_enrichment_summary USING btree (residue1_id, residue2_id);


--
-- Name: residue_pair_enrichment_synergy_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX residue_pair_enrichment_synergy_idx ON docking.residue_pair_enrichment_summary USING btree (synergy_ratio DESC);


--
-- Name: residue_pair_score_band_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_pair_score_band_summary_uk ON docking.residue_pair_score_band_summary USING btree (score_lower, residue_id_1, residue_id_2);


--
-- Name: residue_pair_score_summary_avg_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX residue_pair_score_summary_avg_idx ON docking.residue_pair_score_summary USING btree (avg_score);


--
-- Name: residue_pair_score_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_pair_score_summary_uk ON docking.residue_pair_score_summary USING btree (residue_id_1, residue_id_2);


--
-- Name: residue_triplet_score_band_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_triplet_score_band_summary_uk ON docking.residue_triplet_score_band_summary USING btree (score_lower, residue_id_1, residue_id_2, residue_id_3);


--
-- Name: residue_triplet_score_summary_avg_idx; Type: INDEX; Schema: docking; Owner: -
--

CREATE INDEX residue_triplet_score_summary_avg_idx ON docking.residue_triplet_score_summary USING btree (avg_score);


--
-- Name: residue_triplet_score_summary_uk; Type: INDEX; Schema: docking; Owner: -
--

CREATE UNIQUE INDEX residue_triplet_score_summary_uk ON docking.residue_triplet_score_summary USING btree (residue_id_1, residue_id_2, residue_id_3);


--
-- Name: idx_residue_query; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_residue_query ON public.pose_contacts USING btree (residue_name, residue_number);


--
-- Name: docking_run docking_run_receptor_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_run
    ADD CONSTRAINT docking_run_receptor_id_fkey FOREIGN KEY (receptor_id) REFERENCES docking.receptor(id);


--
-- Name: docking_pose fk_docking_pose_run; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.docking_pose
    ADD CONSTRAINT fk_docking_pose_run FOREIGN KEY (run_id) REFERENCES docking.docking_run(id);


--
-- Name: pocket_atom pocket_atom_pocket_residue_fk; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_atom
    ADD CONSTRAINT pocket_atom_pocket_residue_fk FOREIGN KEY (pocket_residue_id) REFERENCES docking.pocket_residue(id);


--
-- Name: pocket_residue pocket_residue_pocket_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pocket_residue
    ADD CONSTRAINT pocket_residue_pocket_id_fkey FOREIGN KEY (pocket_id) REFERENCES docking.pocket(id);


--
-- Name: pose_atom_contact pose_atom_contact_pocket_atom_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom_contact
    ADD CONSTRAINT pose_atom_contact_pocket_atom_id_fkey FOREIGN KEY (pocket_atom_id) REFERENCES docking.pocket_atom(id);


--
-- Name: pose_atom_contact pose_atom_contact_pocket_residue_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom_contact
    ADD CONSTRAINT pose_atom_contact_pocket_residue_id_fkey FOREIGN KEY (pocket_residue_id) REFERENCES docking.pocket_residue(id);


--
-- Name: pose_atom_contact pose_atom_contact_pose_atom_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom_contact
    ADD CONSTRAINT pose_atom_contact_pose_atom_id_fkey FOREIGN KEY (pose_atom_id) REFERENCES docking.pose_atom(id) ON DELETE CASCADE;


--
-- Name: pose_atom_contact pose_atom_contact_pose_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom_contact
    ADD CONSTRAINT pose_atom_contact_pose_id_fkey FOREIGN KEY (pose_id) REFERENCES docking.docking_pose(id) ON DELETE CASCADE;


--
-- Name: pose_atom pose_atom_pose_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_atom
    ADD CONSTRAINT pose_atom_pose_id_fkey FOREIGN KEY (pose_id) REFERENCES docking.docking_pose(id) ON DELETE CASCADE;


--
-- Name: pose_contact pose_contact_pose_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_contact
    ADD CONSTRAINT pose_contact_pose_id_fkey FOREIGN KEY (pose_id) REFERENCES docking.docking_pose(id);


--
-- Name: pose_contact_residue pose_contact_residue_pose_id_fkey; Type: FK CONSTRAINT; Schema: docking; Owner: -
--

ALTER TABLE ONLY docking.pose_contact_residue
    ADD CONSTRAINT pose_contact_residue_pose_id_fkey FOREIGN KEY (pose_id) REFERENCES docking.docking_pose(id) ON DELETE CASCADE;


--
-- Name: docked_poses fk7rc5umj9v0o18r65wbgauwiqb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docked_poses
    ADD CONSTRAINT fk7rc5umj9v0o18r65wbgauwiqb FOREIGN KEY (docking_run_id) REFERENCES public.docking_runs(id);


--
-- Name: docking_run_pockets fk8n4g6d5bl7htyl5ormuxru3fd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docking_run_pockets
    ADD CONSTRAINT fk8n4g6d5bl7htyl5ormuxru3fd FOREIGN KEY (pocket_id) REFERENCES public.pockets(id);


--
-- Name: artifacts fkb40io011nnadpsa634euppy4e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT fkb40io011nnadpsa634euppy4e FOREIGN KEY (pipeline_run_id) REFERENCES public.pipeline_runs(id);


--
-- Name: docked_poses fkb4tffpmq4gsip9gbnwpbnr1rf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docked_poses
    ADD CONSTRAINT fkb4tffpmq4gsip9gbnwpbnr1rf FOREIGN KEY (pocket_id) REFERENCES public.pockets(id);


--
-- Name: docked_poses fkdx9y5jddd02d1mvx51cqxrsnn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docked_poses
    ADD CONSTRAINT fkdx9y5jddd02d1mvx51cqxrsnn FOREIGN KEY (ligand_id) REFERENCES public.compound_ligands(id);


--
-- Name: pose_atoms fkfhq2m6suj2cly9e5xew5twpgw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pose_atoms
    ADD CONSTRAINT fkfhq2m6suj2cly9e5xew5twpgw FOREIGN KEY (pose_id) REFERENCES public.docked_poses(id);


--
-- Name: artifacts fkgpoq4ehbddy7rfwo980sckak7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT fkgpoq4ehbddy7rfwo980sckak7 FOREIGN KEY (target_id) REFERENCES public.targets(id);


--
-- Name: pipeline_run_inputs fkh00nsnskwbfsltx1jx8x17cv3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pipeline_run_inputs
    ADD CONSTRAINT fkh00nsnskwbfsltx1jx8x17cv3 FOREIGN KEY (pipeline_run_id) REFERENCES public.pipeline_runs(id);


--
-- Name: stage_runs fkj5e66cxu77vuj5vlp5bf30agt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_runs
    ADD CONSTRAINT fkj5e66cxu77vuj5vlp5bf30agt FOREIGN KEY (pipeline_run_id) REFERENCES public.pipeline_runs(id);


--
-- Name: pose_contacts fkjdn05yh7nveq32a82ewitw6un; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pose_contacts
    ADD CONSTRAINT fkjdn05yh7nveq32a82ewitw6un FOREIGN KEY (pose_id) REFERENCES public.docked_poses(id);


--
-- Name: docking_run_pockets fkkfqevyboutllmrb2cwy4bf1pu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.docking_run_pockets
    ADD CONSTRAINT fkkfqevyboutllmrb2cwy4bf1pu FOREIGN KEY (docking_run_id) REFERENCES public.docking_runs(id);


--
-- Name: target_other_names fktc2te6c2t5od653o5n9q8bu4x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.target_other_names
    ADD CONSTRAINT fktc2te6c2t5od653o5n9q8bu4x FOREIGN KEY (target_id) REFERENCES public.targets(id);


--
-- PostgreSQL database dump complete
--

\unrestrict TB6nSjh4p0W4PeOdtbg6jcfTYgOQm1FgvkyWvAKcBMhN7ofZta8mXkhgt1PdCTi

