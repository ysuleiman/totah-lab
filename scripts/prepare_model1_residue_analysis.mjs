import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const workspace = path.resolve(process.argv[2] ?? ".");
const mettl7bContacts = path.resolve(process.argv[3] ?? "");
const mettl7aContacts = path.resolve(process.argv[4] ?? "");
for (const contactFile of [mettl7bContacts, mettl7aContacts]) {
    if (!fs.existsSync(contactFile)) {
        throw new Error(`Contact file does not exist: ${contactFile}`);
    }
}

const template = fs.readFileSync(
    path.join(workspace, "sql/docking/model1-residue-analysis.sql"),
    "utf8"
);
const sql = template
    .replaceAll(
        "__METTL7B_CONTACTS__",
        mettl7bContacts.replaceAll("'", "''")
    )
    .replaceAll(
        "__METTL7A_CONTACTS__",
        mettl7aContacts.replaceAll("'", "''")
    );
const outputDirectory = fs.mkdtempSync(
    path.join(os.tmpdir(), "totah-residue-analysis-")
);
const outputFile = path.join(outputDirectory, "import.sql");
fs.writeFileSync(outputFile, sql);
process.stdout.write(`${outputFile}\n`);
