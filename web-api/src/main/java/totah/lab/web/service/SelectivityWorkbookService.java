package totah.lab.web.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public final class SelectivityWorkbookService {

    public byte[] create(
            List<DockingAnalysisService.SelectivityScore> scores
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream workbook = new ZipOutputStream(output)) {
            write(workbook, "[Content_Types].xml", contentTypes());
            write(workbook, "_rels/.rels", packageRelationships());
            write(workbook, "xl/workbook.xml", workbook());
            write(
                    workbook,
                    "xl/_rels/workbook.xml.rels",
                    workbookRelationships()
            );
            write(workbook, "xl/styles.xml", styles());
            write(
                    workbook,
                    "xl/worksheets/sheet1.xml",
                    worksheet(scores)
            );
        }
        return output.toByteArray();
    }

    private static String worksheet(
            List<DockingAnalysisService.SelectivityScore> scores
    ) {
        int lastRow = scores.size() + 4;
        StringBuilder xml = new StringBuilder(1024 + scores.size() * 700);
        xml.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetViews>
                    <sheetView workbookViewId="0">
                      <pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/>
                    </sheetView>
                  </sheetViews>
                  <cols>
                    <col min="1" max="1" width="25" customWidth="1"/>
                    <col min="2" max="2" width="35" customWidth="1"/>
                    <col min="3" max="5" width="14" customWidth="1"/>
                    <col min="6" max="9" width="14" customWidth="1"/>
                  </cols>
                  <sheetData>
                """);
        xml.append(row(1, textCell("A1", "METTL7B / METTL7A Selectivity", 1)));
        xml.append(row(
                2,
                textCell(
                        "A2",
                        "Best observed Chemflow pose per ligand; WH-prefixed warheads excluded",
                        2
                )
        ));
        xml.append(row(
                3,
                textCell("A3", "Generated " + LocalDate.now(), 2),
                textCell("C3", "Delta = METTL7A − METTL7B", 2),
                textCell("E3", "Positive delta favors METTL7B", 2)
        ));
        xml.append(row(
                4,
                textCell("A4", "Ligand", 3),
                textCell("B4", "Internal ligand ID", 3),
                textCell("C4", "METTL7B score", 3),
                textCell("D4", "METTL7A score", 3),
                textCell("E4", "Delta", 3),
                textCell("F4", "7B run", 3),
                textCell("G4", "7B pose", 3),
                textCell("H4", "7A run", 3),
                textCell("I4", "7A pose", 3)
        ));

        int rowNumber = 5;
        for (DockingAnalysisService.SelectivityScore score : scores) {
            xml.append(row(
                    rowNumber,
                    textCell("A" + rowNumber, score.ligandLabel(), 0),
                    textCell("B" + rowNumber, score.ligandId(), 4),
                    numberCell("C" + rowNumber, score.score7b(), 5),
                    numberCell("D" + rowNumber, score.score7a(), 5),
                    numberCell("E" + rowNumber, score.delta(), 5),
                    numberCell("F" + rowNumber, score.runId7b(), 6),
                    numberCell("G" + rowNumber, score.poseId7b(), 6),
                    numberCell("H" + rowNumber, score.runId7a(), 6),
                    numberCell("I" + rowNumber, score.poseId7a(), 6)
            ));
            rowNumber++;
        }
        xml.append("</sheetData>");
        xml.append("<mergeCells count=\"1\"><mergeCell ref=\"A1:I1\"/></mergeCells>");
        xml.append("<autoFilter ref=\"A4:I").append(lastRow).append("\"/>");
        xml.append("""
                <conditionalFormatting sqref="E5:E1048576">
                  <cfRule type="cellIs" dxfId="0" priority="1" operator="greaterThanOrEqual">
                    <formula>0</formula>
                  </cfRule>
                  <cfRule type="cellIs" dxfId="1" priority="2" operator="lessThan">
                    <formula>0</formula>
                  </cfRule>
                </conditionalFormatting>
                </worksheet>
                """);
        return xml.toString();
    }

    private static String row(int number, String... cells) {
        return "<row r=\"" + number + "\">"
                + String.join("", cells)
                + "</row>";
    }

    private static String textCell(
            String reference,
            String value,
            int style
    ) {
        return "<c r=\"" + reference + "\" s=\"" + style
                + "\" t=\"inlineStr\"><is><t>"
                + escape(value)
                + "</t></is></c>";
    }

    private static String numberCell(
            String reference,
            double value,
            int style
    ) {
        return "<c r=\"" + reference + "\" s=\"" + style + "\"><v>"
                + value + "</v></c>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void write(
            ZipOutputStream workbook,
            String path,
            String content
    ) throws IOException {
        workbook.putNextEntry(new ZipEntry(path));
        workbook.write(content.getBytes(StandardCharsets.UTF_8));
        workbook.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private static String packageRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbook() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Selectivity" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """;
    }

    private static String workbookRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="1"><numFmt numFmtId="164" formatCode="0.000"/></numFmts>
                  <fonts count="4">
                    <font><sz val="11"/><name val="Aptos"/></font>
                    <font><b/><sz val="18"/><color rgb="FFD7FF5F"/><name val="Aptos Display"/></font>
                    <font><sz val="10"/><color rgb="FF6D7A75"/><name val="Aptos"/></font>
                    <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Aptos"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF172421"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="2">
                    <border/>
                    <border><bottom style="thin"><color rgb="FFD9DFD8"/></bottom></border>
                  </borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="7">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                    <xf numFmtId="0" fontId="3" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                    <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1"/>
                    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
                  </cellXfs>
                  <dxfs count="2">
                    <dxf><fill><patternFill patternType="solid"><fgColor rgb="FFDFF0D8"/></patternFill></fill><font><color rgb="FF245C3C"/></font></dxf>
                    <dxf><fill><patternFill patternType="solid"><fgColor rgb="FFF5DFDC"/></patternFill></fill><font><color rgb="FF8A3E36"/></font></dxf>
                  </dxfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """;
    }
}
