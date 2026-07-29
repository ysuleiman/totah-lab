import fs from "node:fs";
import path from "node:path";

const workspace = path.resolve(process.argv[2] ?? ".");
const resourceRoot = path.join(
    workspace,
    "shared-resources",
    "src",
    "main",
    "resources"
);

const targets = [
    {
        accession: "Q6UX53",
        proteinName: "TMT1B",
        receptorName: "METTL7B",
        structureAccession: "AF-Q6UX53-F1-model_v6"
    },
    {
        accession: "Q9H8H3",
        proteinName: "TMT1A",
        receptorName: "METTL7A",
        structureAccession: "AF-Q9H8H3-F1-model_v6"
    }
];

function sqlString(value) {
    if (value === null || value === undefined) {
        return "NULL";
    }
    return `'${String(value).replaceAll("'", "''")}'`;
}

function resourcePath(target, ...parts) {
    return path.join(resourceRoot, target.accession, ...parts);
}

function parseStructureResidues(file) {
    const residues = new Map();
    for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
        if (!line.startsWith("ATOM")) {
            continue;
        }
        const residueName = line.slice(17, 20).trim().toUpperCase();
        const chain = line.slice(21, 22).trim() || "A";
        const residueNumber = Number.parseInt(line.slice(22, 26).trim(), 10);
        const insertionCode = line.slice(26, 27).trim();
        const key = `${chain}|${residueNumber}|${insertionCode}`;
        residues.set(key, {
            chain,
            residueNumber,
            insertionCode,
            residueName
        });
    }
    return [...residues.values()];
}

function parseFpocketMetrics(file) {
    const pockets = [];
    let current = null;
    for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
        const pocketMatch = line.match(/^Pocket\s+(\d+)\s+:/);
        if (pocketMatch) {
            current = {number: Number.parseInt(pocketMatch[1], 10)};
            pockets.push(current);
            continue;
        }
        if (!current) {
            continue;
        }
        const scoreMatch = line.match(/^\s*Score\s*:\s*([-.\d]+)/);
        const druggabilityMatch = line.match(
            /^\s*Druggability Score\s*:\s*([-.\d]+)/
        );
        const volumeMatch = line.match(/^\s*Volume\s*:\s*([-.\d]+)/);
        if (scoreMatch) {
            current.score = Number.parseFloat(scoreMatch[1]);
        } else if (druggabilityMatch) {
            current.druggability = Number.parseFloat(
                druggabilityMatch[1]
            );
        } else if (volumeMatch) {
            current.volume = Number.parseFloat(volumeMatch[1]);
        }
    }
    return pockets;
}

function parsePocketResidues(file) {
    return parseStructureResidues(file);
}

function parseCsvLine(line) {
    return line.split(",").map(value => value.trim());
}

function parseP2RankPockets(file) {
    const lines = fs.readFileSync(file, "utf8")
        .split(/\r?\n/)
        .filter(Boolean);
    const headers = parseCsvLine(lines[0]);
    const index = Object.fromEntries(
        headers.map((header, position) => [header, position])
    );
    return lines.slice(1).map(line => {
        const values = parseCsvLine(line);
        const name = values[index.name];
        const residueIds = values[index.residue_ids]
            .split(/\s+/)
            .filter(Boolean)
            .map(identifier => {
                const separator = identifier.indexOf("_");
                return {
                    chain: identifier.slice(0, separator) || "A",
                    residueNumber: Number.parseInt(
                        identifier.slice(separator + 1),
                        10
                    ),
                    insertionCode: ""
                };
            });
        return {
            number: Number.parseInt(name.replace(/\D+/g, ""), 10),
            score: Number.parseFloat(values[index.score]),
            probability: Number.parseFloat(values[index.probability]),
            residues: residueIds
        };
    });
}

function artifactValues(target) {
    const values = [];
    const pdb = resourcePath(
        target,
        `${target.accession}_${target.proteinName}_HUMAN.pdb`
    );
    values.push([path.basename(pdb), "RAW_PDB_FILE", pdb]);

    const info = resourcePath(
        target,
        "fpocket",
        `AF-${target.accession}-F1-model_v6_info.txt`
    );
    values.push([path.basename(info), "FPOCKET_REPORT", info]);

    const pocketDirectory = resourcePath(target, "fpocket", "pockets");
    for (const filename of fs.readdirSync(pocketDirectory)
        .filter(name => /^pocket\d+_atm\.pdb$/.test(name))
        .sort((a, b) => a.localeCompare(b, undefined, {numeric: true}))) {
        values.push([
            filename,
            "FPOCKET_POCKET",
            path.join(pocketDirectory, filename)
        ]);
    }

    const p2rank = resourcePath(
        target,
        "prank",
        "structure.pdb_predictions.csv"
    );
    values.push([path.basename(p2rank), "P2RANK_PREDICTIONS", p2rank]);
    return values;
}

function emit(line = "") {
    process.stdout.write(`${line}\n`);
}

emit("\\set ON_ERROR_STOP on");
emit("BEGIN;");

for (const target of targets) {
    emit(`
INSERT INTO public.targets (name, uniprot_id)
SELECT ${sqlString(target.receptorName)}, ${sqlString(target.accession)}
WHERE NOT EXISTS (
    SELECT 1 FROM public.targets
    WHERE uniprot_id = ${sqlString(target.accession)}
);

UPDATE public.targets
SET name = ${sqlString(target.receptorName)}
WHERE uniprot_id = ${sqlString(target.accession)}
  AND COALESCE(name, '') = '';

INSERT INTO docking.receptor (target_name, pdb_file)
SELECT
    ${sqlString(target.receptorName)},
    ${sqlString(resourcePath(
        target,
        `${target.accession}_${target.proteinName}_HUMAN.pdb`
    ))}
WHERE NOT EXISTS (
    SELECT 1 FROM docking.receptor
    WHERE target_name = ${sqlString(target.receptorName)}
);
`);

    const artifactRows = artifactValues(target);
    const structurePath = artifactRows[0][2];
    emit(`
WITH existing_run AS (
    SELECT a.pipeline_run_id AS id
    FROM docking.artifacts a
    JOIN public.targets t ON t.id = a.target_id
    WHERE t.uniprot_id = ${sqlString(target.accession)}
      AND a.storage_location = ${sqlString(structurePath)}
    LIMIT 1
),
new_run AS (
    INSERT INTO public.pipeline_runs (
        start_time,
        end_time,
        status
    )
    SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'FINISHED'
    WHERE NOT EXISTS (SELECT 1 FROM existing_run)
    RETURNING id
),
run_row AS (
    SELECT id FROM existing_run
    UNION ALL
    SELECT id FROM new_run
),
artifact_rows (filename, label, storage_location) AS (
    VALUES
${artifactRows.map(row => `        (${row.map(sqlString).join(", ")})`).join(",\n")}
)
INSERT INTO docking.artifacts (
    filename,
    label,
    storage_location,
    pipeline_run_id,
    target_id,
    created_at
)
SELECT
    ar.filename,
    ar.label,
    ar.storage_location,
    rr.id,
    t.id,
    CURRENT_TIMESTAMP
FROM artifact_rows ar
CROSS JOIN run_row rr
JOIN public.targets t
    ON t.uniprot_id = ${sqlString(target.accession)}
WHERE NOT EXISTS (
    SELECT 1
    FROM docking.artifacts existing
    WHERE existing.storage_location = ar.storage_location
      AND existing.target_id = t.id
);
`);

    emit(`
INSERT INTO docking.structure (
    receptor_id,
    source,
    source_accession,
    chain,
    model_number,
    artifact_id,
    preparation_state
)
SELECT
    r.id,
    'ALPHAFOLD',
    ${sqlString(target.structureAccession)},
    'A',
    1,
    a.id,
    'RAW'
FROM docking.receptor r
JOIN public.targets t
    ON t.uniprot_id = ${sqlString(target.accession)}
JOIN docking.artifacts a
    ON a.target_id = t.id
   AND a.storage_location = ${sqlString(structurePath)}
WHERE r.target_name = ${sqlString(target.receptorName)}
  AND NOT EXISTS (
      SELECT 1
      FROM docking.structure existing
      WHERE existing.receptor_id = r.id
        AND existing.artifact_id = a.id
        AND existing.preparation_state = 'RAW'
  );
`);

    const structureResidues = parseStructureResidues(structurePath);
    emit(`
INSERT INTO docking.residue (
    structure_id,
    chain,
    residue_number,
    insertion_code,
    residue_name
)
SELECT
    s.id,
    source.chain,
    source.residue_number,
    source.insertion_code,
    source.residue_name
FROM (
    VALUES
${structureResidues.map(residue =>
        `        (${sqlString(residue.chain)}, ${residue.residueNumber}, `
        + `${sqlString(residue.insertionCode)}, ${sqlString(residue.residueName)})`
    ).join(",\n")}
) AS source (
    chain,
    residue_number,
    insertion_code,
    residue_name
)
JOIN docking.structure s
    ON s.source_accession = ${sqlString(target.structureAccession)}
ON CONFLICT (
    structure_id,
    chain,
    residue_number,
    insertion_code
)
DO UPDATE SET
    residue_name = EXCLUDED.residue_name;
`);

    const fpocketInfo = resourcePath(
        target,
        "fpocket",
        `AF-${target.accession}-F1-model_v6_info.txt`
    );
    const fpocketPockets = parseFpocketMetrics(fpocketInfo);
    for (const pocket of fpocketPockets) {
        const pocketFile = resourcePath(
            target,
            "fpocket",
            "pockets",
            `pocket${pocket.number}_atm.pdb`
        );
        emit(`
UPDATE docking.pocket existing
SET
    structure_id = s.id,
    artifact_id = a.id,
    source = 'FPOCKET'
FROM docking.structure s
JOIN public.targets t
    ON t.uniprot_id = ${sqlString(target.accession)}
JOIN docking.artifacts a
    ON a.target_id = t.id
   AND a.storage_location = ${sqlString(pocketFile)}
WHERE existing.receptor_id = s.receptor_id
  AND existing.pocket_number = ${pocket.number}
  AND existing.source = 'FPOCKET'
  AND existing.structure_id IS NULL
  AND s.source_accession = ${sqlString(target.structureAccession)};

INSERT INTO docking.pocket (
    receptor_id,
    structure_id,
    pocket_number,
    source,
    fpocket_file,
    volume,
    score,
    druggability_score,
    probability,
    artifact_id
)
SELECT
    s.receptor_id,
    s.id,
    ${pocket.number},
    'FPOCKET',
    ${sqlString(path.basename(pocketFile))},
    ${pocket.volume},
    ${pocket.score},
    ${pocket.druggability},
    NULL,
    a.id
FROM docking.structure s
JOIN public.targets t
    ON t.uniprot_id = ${sqlString(target.accession)}
JOIN docking.artifacts a
    ON a.target_id = t.id
   AND a.storage_location = ${sqlString(pocketFile)}
WHERE s.source_accession = ${sqlString(target.structureAccession)}
ON CONFLICT (
    structure_id,
    source,
    pocket_number
)
DO UPDATE SET
    receptor_id = EXCLUDED.receptor_id,
    fpocket_file = EXCLUDED.fpocket_file,
    volume = EXCLUDED.volume,
    score = EXCLUDED.score,
    druggability_score = EXCLUDED.druggability_score,
    probability = EXCLUDED.probability,
    artifact_id = EXCLUDED.artifact_id;
`);

        const pocketResidues = parsePocketResidues(pocketFile);
        emit(`
INSERT INTO docking.pocket_residue (
    pocket_id,
    chain,
    residue_number,
    residue_name,
    residue_id
)
SELECT
    p.id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    residue.id
FROM docking.pocket p
JOIN docking.structure s
    ON s.id = p.structure_id
JOIN docking.residue residue
    ON residue.structure_id = s.id
JOIN (
    VALUES
${pocketResidues.map(residue =>
        `        (${sqlString(residue.chain)}, ${residue.residueNumber}, `
        + `${sqlString(residue.insertionCode)})`
    ).join(",\n")}
) AS membership (chain, residue_number, insertion_code)
    ON membership.chain = residue.chain
   AND membership.residue_number = residue.residue_number
   AND membership.insertion_code = residue.insertion_code
WHERE s.source_accession = ${sqlString(target.structureAccession)}
  AND p.source = 'FPOCKET'
  AND p.pocket_number = ${pocket.number}
ON CONFLICT (pocket_id, chain, residue_number)
DO UPDATE SET
    residue_name = EXCLUDED.residue_name,
    residue_id = EXCLUDED.residue_id;
`);
    }

    const p2rankFile = resourcePath(
        target,
        "prank",
        "structure.pdb_predictions.csv"
    );
    for (const pocket of parseP2RankPockets(p2rankFile)) {
        emit(`
INSERT INTO docking.pocket (
    receptor_id,
    structure_id,
    pocket_number,
    source,
    fpocket_file,
    volume,
    score,
    druggability_score,
    probability,
    artifact_id
)
SELECT
    s.receptor_id,
    s.id,
    ${pocket.number},
    'P2RANK',
    NULL,
    NULL,
    ${pocket.score},
    NULL,
    ${pocket.probability},
    a.id
FROM docking.structure s
JOIN public.targets t
    ON t.uniprot_id = ${sqlString(target.accession)}
JOIN docking.artifacts a
    ON a.target_id = t.id
   AND a.storage_location = ${sqlString(p2rankFile)}
WHERE s.source_accession = ${sqlString(target.structureAccession)}
ON CONFLICT (
    structure_id,
    source,
    pocket_number
)
DO UPDATE SET
    receptor_id = EXCLUDED.receptor_id,
    fpocket_file = NULL,
    volume = NULL,
    score = EXCLUDED.score,
    druggability_score = NULL,
    probability = EXCLUDED.probability,
    artifact_id = EXCLUDED.artifact_id;
`);

        emit(`
INSERT INTO docking.pocket_residue (
    pocket_id,
    chain,
    residue_number,
    residue_name,
    residue_id
)
SELECT
    p.id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    residue.id
FROM docking.pocket p
JOIN docking.structure s
    ON s.id = p.structure_id
JOIN docking.residue residue
    ON residue.structure_id = s.id
JOIN (
    VALUES
${pocket.residues.map(residue =>
        `        (${sqlString(residue.chain)}, ${residue.residueNumber}, `
        + `${sqlString(residue.insertionCode)})`
    ).join(",\n")}
) AS membership (chain, residue_number, insertion_code)
    ON membership.chain = residue.chain
   AND membership.residue_number = residue.residue_number
   AND membership.insertion_code = residue.insertion_code
WHERE s.source_accession = ${sqlString(target.structureAccession)}
  AND p.source = 'P2RANK'
  AND p.pocket_number = ${pocket.number}
ON CONFLICT (pocket_id, chain, residue_number)
DO UPDATE SET
    residue_name = EXCLUDED.residue_name,
    residue_id = EXCLUDED.residue_id;
`);
    }
}

emit(`
ALTER TABLE docking.structure
    ALTER COLUMN artifact_id SET NOT NULL;

ALTER TABLE docking.pocket
    ALTER COLUMN structure_id SET NOT NULL;

ALTER TABLE docking.pocket
    ALTER COLUMN artifact_id SET NOT NULL;

ALTER TABLE docking.pocket_residue
    ALTER COLUMN residue_id SET NOT NULL;

COMMIT;
`);
