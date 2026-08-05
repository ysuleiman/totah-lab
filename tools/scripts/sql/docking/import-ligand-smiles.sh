#!/usr/bin/env bash
# Backfills docking.ligand (SMILES per ligand_id) in totah_lab_db from the
# chemflow3 compounds table. Idempotent: the table is rebuilt on each run.
set -euo pipefail

PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-admin}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
SOURCE_DB="${CHEMFLOW_DB_NAME:-chemflow3}"
DEST_DB="${TOTAH_DB_NAME:-totah_lab_db}"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DEST_DB" \
    -v ON_ERROR_STOP=1 -f "$(dirname "$0")/ligand.sql"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DEST_DB" \
    -v ON_ERROR_STOP=1 -c "TRUNCATE docking.ligand"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$SOURCE_DB" -c \
    "COPY (
         SELECT replace(id::text, '-', ''), smiles, external_id
         FROM compounds
         WHERE smiles IS NOT NULL AND smiles <> ''
     ) TO STDOUT" \
| psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DEST_DB" \
    -v ON_ERROR_STOP=1 -c \
    "COPY docking.ligand (id, smiles, label) FROM STDIN"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DEST_DB" -t -c \
    "SELECT count(*) FROM docking.ligand"
