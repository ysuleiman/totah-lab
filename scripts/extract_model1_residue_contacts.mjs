import fs from "node:fs";
import path from "node:path";

const CUTOFF = 4.0;
const CUTOFF_SQUARED = CUTOFF * CUTOFF;

function parseAtom(line) {
    return {
        atomName: line.slice(12, 16).trim(),
        residueName: line.slice(17, 20).trim(),
        chain: line.slice(21, 22).trim() || "A",
        residueNumber: Number.parseInt(line.slice(22, 26).trim(), 10),
        x: Number.parseFloat(line.slice(30, 38).trim()),
        y: Number.parseFloat(line.slice(38, 46).trim()),
        z: Number.parseFloat(line.slice(46, 54).trim()),
        autodockType: line.slice(77).trim()
    };
}

function isHeavyAtom(atom) {
    return !atom.autodockType.toUpperCase().startsWith("H");
}

function cellCoordinate(value) {
    return Math.floor(value / CUTOFF);
}

function cellKey(x, y, z) {
    return `${x}|${y}|${z}`;
}

function buildReceptorGrid(receptorFile) {
    const grid = new Map();
    let heavyAtomCount = 0;
    for (const line of fs.readFileSync(receptorFile, "utf8").split(/\r?\n/)) {
        if (!line.startsWith("ATOM") && !line.startsWith("HETATM")) {
            continue;
        }
        const atom = parseAtom(line);
        if (!isHeavyAtom(atom)) {
            continue;
        }
        const key = cellKey(
            cellCoordinate(atom.x),
            cellCoordinate(atom.y),
            cellCoordinate(atom.z)
        );
        const cell = grid.get(key) ?? [];
        cell.push(atom);
        grid.set(key, cell);
        heavyAtomCount++;
    }
    return {grid, heavyAtomCount};
}

function model1Atoms(outputFile) {
    const atoms = [];
    let inModel1 = false;
    for (const line of fs.readFileSync(outputFile, "utf8").split(/\r?\n/)) {
        if (line.startsWith("MODEL ")) {
            inModel1 = line.trim() === "MODEL 1";
            continue;
        }
        if (line.startsWith("ENDMDL")) {
            if (inModel1) {
                break;
            }
            continue;
        }
        if (inModel1
            && (line.startsWith("ATOM") || line.startsWith("HETATM"))) {
            const atom = parseAtom(line);
            if (isHeavyAtom(atom)) {
                atoms.push(atom);
            }
        }
    }
    return atoms;
}

function residueContacts(ligandAtoms, receptorGrid) {
    const contacts = new Map();
    for (const ligandAtom of ligandAtoms) {
        const cellX = cellCoordinate(ligandAtom.x);
        const cellY = cellCoordinate(ligandAtom.y);
        const cellZ = cellCoordinate(ligandAtom.z);
        for (let dx = -1; dx <= 1; dx++) {
            for (let dy = -1; dy <= 1; dy++) {
                for (let dz = -1; dz <= 1; dz++) {
                    const receptorAtoms = receptorGrid.get(cellKey(
                        cellX + dx,
                        cellY + dy,
                        cellZ + dz
                    )) ?? [];
                    for (const receptorAtom of receptorAtoms) {
                        const x = ligandAtom.x - receptorAtom.x;
                        const y = ligandAtom.y - receptorAtom.y;
                        const z = ligandAtom.z - receptorAtom.z;
                        const distanceSquared = x * x + y * y + z * z;
                        if (distanceSquared > CUTOFF_SQUARED) {
                            continue;
                        }
                        const residueKey = `${receptorAtom.chain}|`
                            + `${receptorAtom.residueNumber}`;
                        const current = contacts.get(residueKey);
                        contacts.set(residueKey, {
                            chain: receptorAtom.chain,
                            residueNumber: receptorAtom.residueNumber,
                            residueName: receptorAtom.residueName,
                            atomContactCount:
                                (current?.atomContactCount ?? 0) + 1,
                            minDistanceSquared: Math.min(
                                current?.minDistanceSquared ?? Infinity,
                                distanceSquared
                            )
                        });
                    }
                }
            }
        }
    }
    return [...contacts.values()];
}

function tsv(values) {
    return `${values.map(value => String(value)
        .replaceAll("\\", "\\\\")
        .replaceAll("\t", "\\t")
        .replaceAll("\n", "\\n")).join("\t")}\n`;
}

async function extractRun(runId, dockingDirectory, outputStream) {
    const receptorFile = path.join(dockingDirectory, "receptor.pdbqt");
    const {grid, heavyAtomCount} = buildReceptorGrid(receptorFile);
    const outputNames = fs.readdirSync(dockingDirectory)
        .filter(name => /^out\d+_out\.pdbqt$/.test(name))
        .sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));

    let contactRows = 0;
    let model1HeavyAtoms = 0;
    for (let index = 0; index < outputNames.length; index++) {
        const outputFile = path.join(dockingDirectory, outputNames[index]);
        const ligandAtoms = model1Atoms(outputFile);
        model1HeavyAtoms += ligandAtoms.length;
        for (const contact of residueContacts(ligandAtoms, grid)) {
            outputStream.write(tsv([
                runId,
                outputFile,
                contact.chain,
                contact.residueNumber,
                contact.residueName,
                contact.atomContactCount,
                Math.sqrt(contact.minDistanceSquared)
            ]));
            contactRows++;
        }
        if ((index + 1) % 10_000 === 0) {
            process.stderr.write(JSON.stringify({
                runId,
                filesProcessed: index + 1,
                outputFiles: outputNames.length,
                contactRows
            }) + "\n");
        }
    }
    return {
        runId,
        dockingDirectory,
        outputFiles: outputNames.length,
        receptorHeavyAtoms: heavyAtomCount,
        model1HeavyAtoms,
        contactRows
    };
}

const outputFile = path.resolve(process.argv[2] ?? "");
const runArguments = process.argv.slice(3);
if (!outputFile || runArguments.length === 0) {
    throw new Error(
        "Usage: node extract_model1_residue_contacts.mjs "
        + "<output.tsv> <run-id>=<docking-directory> [...]"
    );
}

const outputStream = fs.createWriteStream(outputFile);
const summaries = [];
for (const argument of runArguments) {
    const separator = argument.indexOf("=");
    if (separator < 1) {
        throw new Error(`Invalid run argument: ${argument}`);
    }
    const runId = Number.parseInt(argument.slice(0, separator), 10);
    const dockingDirectory = path.resolve(argument.slice(separator + 1));
    summaries.push(await extractRun(runId, dockingDirectory, outputStream));
}
await new Promise(resolve => outputStream.end(resolve));
process.stdout.write(JSON.stringify({outputFile, runs: summaries}, null, 2)
    + "\n");
