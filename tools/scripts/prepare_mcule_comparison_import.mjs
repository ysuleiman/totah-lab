import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const dockingDirectory = path.resolve(process.argv[2] ?? "");
const workspace = path.resolve(process.argv[3] ?? ".");
const includeGeometry = process.argv.includes("--include-geometry");
if (!fs.existsSync(dockingDirectory)) {
    throw new Error(`Docking directory does not exist: ${dockingDirectory}`);
}

const outputDirectory = fs.mkdtempSync(
    path.join(os.tmpdir(), "totah-mcule-import-")
);
const posesFile = path.join(outputDirectory, "poses.tsv");
const atomsFile = path.join(outputDirectory, "atoms.tsv");
const pocketAtomsFile = path.join(outputDirectory, "pocket-atoms.tsv");
const sqlFile = path.join(outputDirectory, "import.sql");

function field(value) {
    return String(value ?? "\\N")
        .replaceAll("\\", "\\\\")
        .replaceAll("\t", "\\t")
        .replaceAll("\n", "\\n");
}

function row(values) {
    return `${values.map(field).join("\t")}\n`;
}

function parseScore(logFile) {
    const match = fs.readFileSync(logFile, "utf8").match(
        /^\s+1\s+(-?\d+(?:\.\d+)?)\s+/m
    );
    if (!match) {
        throw new Error(`No mode-1 score in ${logFile}`);
    }
    return Number.parseFloat(match[1]);
}

function parseLigandId(pdbqtFile) {
    const match = fs.readFileSync(pdbqtFile, "utf8").match(
        /^REMARK\s+Name\s*=\s*(\S+)/m
    );
    if (!match) {
        throw new Error(`No MCULE identifier in ${pdbqtFile}`);
    }
    return match[1];
}

function parsePdbqtAtom(line) {
    const autodockType = line.slice(77).trim();
    return {
        atomIndex: Number.parseInt(line.slice(6, 11).trim(), 10),
        atomName: line.slice(12, 16).trim(),
        element: autodockType,
        x: Number.parseFloat(line.slice(30, 38).trim()),
        y: Number.parseFloat(line.slice(38, 46).trim()),
        z: Number.parseFloat(line.slice(46, 54).trim()),
        charge: Number.parseFloat(line.slice(70, 76).trim()),
        autodockType
    };
}

function residueKeys(pdbFile) {
    const keys = new Set();
    for (const line of fs.readFileSync(pdbFile, "utf8").split(/\r?\n/)) {
        if (line.startsWith("ATOM")) {
            keys.add(`${line.slice(21, 22).trim() || "A"}|`
                + `${Number.parseInt(line.slice(22, 26).trim(), 10)}`);
        }
    }
    return keys;
}

const outputNames = fs.readdirSync(dockingDirectory)
    .filter(name => /^out\d+_out\.pdbqt$/.test(name))
    .sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));

const poseStream = fs.createWriteStream(posesFile);
const atomStream = includeGeometry ? fs.createWriteStream(atomsFile) : null;
for (const outputName of outputNames) {
    const sourceKey = outputName.replace(/_out\.pdbqt$/, "");
    const outputFile = path.join(dockingDirectory, outputName);
    const inputFile = path.join(dockingDirectory, `${sourceKey}.pdbqt`);
    const logFile = path.join(dockingDirectory, `${sourceKey}_log.log`);
    const ligandId = parseLigandId(inputFile);
    poseStream.write(row([
        sourceKey,
        ligandId,
        parseScore(logFile),
        outputFile
    ]));

    if (!includeGeometry) {
        continue;
    }
    for (const line of fs.readFileSync(outputFile, "utf8").split(/\r?\n/)) {
        if (!line.startsWith("ATOM") && !line.startsWith("HETATM")) {
            continue;
        }
        const atom = parsePdbqtAtom(line);
        atomStream.write(row([
            sourceKey,
            atom.atomIndex,
            atom.atomName,
            atom.element,
            atom.x,
            atom.y,
            atom.z,
            atom.charge,
            atom.autodockType
        ]));
    }
}
await Promise.all([
    new Promise(resolve => poseStream.end(resolve)),
    ...(atomStream
        ? [new Promise(resolve => atomStream.end(resolve))]
        : [])
]);

if (includeGeometry) {
    const structureFile = path.join(
        workspace,
        "shared-resources/src/main/resources/Q9H8H3/"
            + "Q9H8H3_TMT1A_HUMAN.pdb"
    );
    const pocketFile = path.join(
        workspace,
        "shared-resources/src/main/resources/Q9H8H3/fpocket/pockets/"
            + "pocket13_atm.pdb"
    );
    const selectedResidues = residueKeys(pocketFile);
    const pocketAtomStream = fs.createWriteStream(pocketAtomsFile);
    for (const line of fs.readFileSync(structureFile, "utf8").split(/\r?\n/)) {
        if (!line.startsWith("ATOM")) {
            continue;
        }
        const chain = line.slice(21, 22).trim() || "A";
        const residueNumber = Number.parseInt(line.slice(22, 26).trim(), 10);
        if (!selectedResidues.has(`${chain}|${residueNumber}`)) {
            continue;
        }
        pocketAtomStream.write(row([
            chain,
            residueNumber,
            line.slice(12, 16).trim(),
            Number.parseFloat(line.slice(30, 38).trim()),
            Number.parseFloat(line.slice(38, 46).trim()),
            Number.parseFloat(line.slice(46, 54).trim()),
            line.slice(76, 78).trim()
        ]));
    }
    await new Promise(resolve => pocketAtomStream.end(resolve));
}

const escaped = value => value.replaceAll("'", "''");
const fullSql = String.raw`\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM docking.docking_run WHERE receptor_id = 2
    ) THEN
        RAISE EXCEPTION 'METTL7A already has a docking run';
    END IF;
END $$;

CREATE TEMP TABLE import_pose (
    source_key text PRIMARY KEY,
    ligand_id varchar NOT NULL,
    vina_score double precision NOT NULL,
    pose_file text NOT NULL
);
\copy import_pose FROM '${escaped(posesFile)}'

DO $$
DECLARE missing_count bigint;
BEGIN
    SELECT count(*) INTO missing_count
    FROM import_pose source
    WHERE NOT EXISTS (
        SELECT 1
        FROM docking.docking_pose existing
        WHERE existing.run_id = 1
          AND existing.ligand_id = source.ligand_id
    );
    IF missing_count <> 0 THEN
        RAISE EXCEPTION '% METTL7A ligands are absent from METTL7B run 1',
            missing_count;
    END IF;
END $$;

INSERT INTO docking.docking_run (
    receptor_id,
    grid_center_x,
    grid_center_y,
    grid_center_z,
    grid_size_x,
    grid_size_y,
    grid_size_z,
    vina_version
) VALUES (2, 0.352, 1.309, -0.464, 27, 27, 27, 'AutoDock Vina');

INSERT INTO docking.docking_pose (
    ligand_id,
    vina_score,
    pose_file,
    receptor_id,
    run_id
)
SELECT
    ligand_id,
    vina_score,
    pose_file,
    NULL,
    currval('docking.docking_run_id_seq')
FROM import_pose;

CREATE TEMP TABLE import_atom (
    source_key text NOT NULL,
    atom_index integer,
    atom_name varchar,
    element varchar,
    x double precision NOT NULL,
    y double precision NOT NULL,
    z double precision NOT NULL,
    charge double precision,
    autodock_type varchar
);
\copy import_atom FROM '${escaped(atomsFile)}'

INSERT INTO docking.pose_atom (
    pose_id,
    atom_index,
    atom_name,
    element,
    x,
    y,
    z,
    charge,
    autodock_type
)
SELECT
    pose.id,
    atom.atom_index,
    atom.atom_name,
    atom.element,
    atom.x,
    atom.y,
    atom.z,
    atom.charge,
    atom.autodock_type
FROM import_atom atom
JOIN import_pose source ON source.source_key = atom.source_key
JOIN docking.docking_pose pose
  ON pose.run_id = currval('docking.docking_run_id_seq')
 AND pose.pose_file = source.pose_file;

CREATE TEMP TABLE import_pocket_atom (
    chain char(1) NOT NULL,
    residue_number integer NOT NULL,
    atom_name varchar,
    x double precision,
    y double precision,
    z double precision,
    element varchar
);
\copy import_pocket_atom FROM '${escaped(pocketAtomsFile)}'

INSERT INTO docking.pocket_atom (
    pocket_residue_id,
    atom_name,
    x,
    y,
    z,
    element
)
SELECT
    residue.id,
    atom.atom_name,
    atom.x,
    atom.y,
    atom.z,
    atom.element
FROM import_pocket_atom atom
JOIN docking.pocket_residue residue
  ON residue.pocket_id = 31
 AND residue.chain = atom.chain
 AND residue.residue_number = atom.residue_number
WHERE NOT EXISTS (
    SELECT 1
    FROM docking.pocket_atom existing
    WHERE existing.pocket_residue_id = residue.id
      AND existing.atom_name = atom.atom_name
);

INSERT INTO docking.pose_atom_contact (
    pose_id,
    pose_atom_id,
    pocket_atom_id,
    pocket_residue_id,
    distance_angstroms
)
SELECT
    ligand_atom.pose_id,
    ligand_atom.id,
    pocket_atom.id,
    pocket_atom.pocket_residue_id,
    sqrt(
        power(ligand_atom.x - pocket_atom.x, 2)
        + power(ligand_atom.y - pocket_atom.y, 2)
        + power(ligand_atom.z - pocket_atom.z, 2)
    )
FROM docking.pose_atom ligand_atom
JOIN docking.docking_pose pose ON pose.id = ligand_atom.pose_id
CROSS JOIN docking.pocket_atom pocket_atom
JOIN docking.pocket_residue pocket_residue
  ON pocket_residue.id = pocket_atom.pocket_residue_id
 AND pocket_residue.pocket_id = 31
WHERE pose.run_id = currval('docking.docking_run_id_seq')
  AND abs(ligand_atom.x - pocket_atom.x) <= 4.0
  AND abs(ligand_atom.y - pocket_atom.y) <= 4.0
  AND abs(ligand_atom.z - pocket_atom.z) <= 4.0
  AND power(ligand_atom.x - pocket_atom.x, 2)
      + power(ligand_atom.y - pocket_atom.y, 2)
      + power(ligand_atom.z - pocket_atom.z, 2) <= 16.0;

COMMIT;
`;
const geometryMarker = "CREATE TEMP TABLE import_atom";
fs.writeFileSync(
    sqlFile,
    includeGeometry
        ? fullSql
        : `${fullSql.slice(0, fullSql.indexOf(geometryMarker))}COMMIT;\n`
);

process.stdout.write(JSON.stringify({
    outputDirectory,
    sqlFile,
    dockingOutputs: outputNames.length,
    includeGeometry
}, null, 2) + "\n");
