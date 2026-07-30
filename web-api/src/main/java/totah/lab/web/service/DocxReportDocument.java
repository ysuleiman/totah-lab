package totah.lab.web.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.List;

final class DocxReportDocument implements Closeable {

    private static final String FONT = "Arial";
    private static final String BODY_STYLE = "TotahBody";
    private static final String HEADING_ONE_STYLE = "TotahHeading1";
    private static final String HEADING_TWO_STYLE = "TotahHeading2";
    private static final int CONTENT_WIDTH_DXA = 9360;

    private final XWPFDocument document = new XWPFDocument();
    private final OutputStream output;

    DocxReportDocument(OutputStream output) {
        this.output = output;
        configurePage();
        configureStyles();
    }

    void title(String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(60);
        XWPFRun run = paragraph.createRun();
        format(run, 26, false, "000000");
        run.setText(clean(text));
    }

    void metadata(String label, String value) {
        XWPFParagraph paragraph = bodyParagraph();
        paragraph.setSpacingAfter(40);
        XWPFRun labelRun = paragraph.createRun();
        format(labelRun, 11, true, "000000");
        labelRun.setText(clean(label) + ": ");
        XWPFRun valueRun = paragraph.createRun();
        format(valueRun, 11, false, "000000");
        valueRun.setText(clean(value));
    }

    void headingOne(String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(HEADING_ONE_STYLE);
        XWPFRun run = paragraph.createRun();
        format(run, 20, false, "000000");
        run.setText(clean(text));
    }

    void headingTwo(String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(HEADING_TWO_STYLE);
        XWPFRun run = paragraph.createRun();
        format(run, 16, false, "000000");
        run.setText(clean(text));
    }

    void paragraph(String text) {
        XWPFParagraph paragraph = bodyParagraph();
        XWPFRun run = paragraph.createRun();
        format(run, 11, false, "000000");
        run.setText(clean(text));
    }

    void labeledParagraph(String label, String text) {
        XWPFParagraph paragraph = bodyParagraph();
        XWPFRun labelRun = paragraph.createRun();
        format(labelRun, 11, true, "000000");
        labelRun.setText(clean(label) + ": ");
        XWPFRun valueRun = paragraph.createRun();
        format(valueRun, 11, false, "000000");
        valueRun.setText(clean(text));
    }

    void compactLabeledParagraph(String label, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(80);
        paragraph.setSpacingBetween(1.0);
        XWPFRun labelRun = paragraph.createRun();
        format(labelRun, 10, true, "000000");
        labelRun.setText(clean(label) + ": ");
        XWPFRun valueRun = paragraph.createRun();
        format(valueRun, 10, false, "000000");
        valueRun.setText(clean(text));
    }

    void pageBreak() {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setPageBreak(true);
        paragraph.setSpacingAfter(0);
    }

    void table(
            int[] widthsDxa,
            List<String> headings,
            List<List<String>> rows
    ) {
        if (headings.size() != widthsDxa.length) {
            throw new IllegalArgumentException(
                    "Table heading and width counts differ");
        }
        XWPFTable table = document.createTable(1, headings.size());
        configureTable(table, widthsDxa);
        XWPFTableRow header = table.getRow(0);
        header.setRepeatHeader(true);
        for (int index = 0; index < headings.size(); index++) {
            writeCell(
                    header.getCell(index),
                    headings.get(index),
                    true,
                    index == 0 || index == headings.size() - 1
            );
        }
        for (List<String> values : rows) {
            if (values.size() != widthsDxa.length) {
                throw new IllegalArgumentException(
                        "Table row and width counts differ");
            }
            XWPFTableRow row = table.createRow();
            for (int index = 0; index < values.size(); index++) {
                setCellWidth(row.getCell(index), widthsDxa[index]);
                writeCell(
                        row.getCell(index),
                        values.get(index),
                        false,
                        index == 0 || index == values.size() - 1
                );
            }
        }
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(40);
    }

    void finish() throws IOException {
        document.write(output);
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    private void configurePage() {
        var section = document.getDocument()
                .getBody()
                .addNewSectPr();
        var size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(12240));
        size.setH(BigInteger.valueOf(15840));
        var margin = section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1440));
        margin.setRight(BigInteger.valueOf(1440));
        margin.setBottom(BigInteger.valueOf(1440));
        margin.setLeft(BigInteger.valueOf(1440));
        margin.setHeader(BigInteger.valueOf(708));
        margin.setFooter(BigInteger.valueOf(708));
    }

    private void configureStyles() {
        var styles = document.createStyles();
        styles.addStyle(style(
                BODY_STYLE,
                "Totah body",
                0,
                160,
                276
        ));
        styles.addStyle(style(
                HEADING_ONE_STYLE,
                "Totah heading 1",
                400,
                120,
                240
        ));
        styles.addStyle(style(
                HEADING_TWO_STYLE,
                "Totah heading 2",
                360,
                120,
                240
        ));
    }

    private org.apache.poi.xwpf.usermodel.XWPFStyle style(
            String id,
            String name,
            int before,
            int after,
            int line
    ) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.setType(STStyleType.PARAGRAPH);
        style.addNewName().setVal(name);
        var properties = style.addNewPPr();
        var spacing = properties.addNewSpacing();
        spacing.setBefore(BigInteger.valueOf(before));
        spacing.setAfter(BigInteger.valueOf(after));
        spacing.setLine(BigInteger.valueOf(line));
        spacing.setLineRule(
                org.openxmlformats.schemas.wordprocessingml.x2006.main
                        .STLineSpacingRule.AUTO
        );
        return new org.apache.poi.xwpf.usermodel.XWPFStyle(style);
    }

    private XWPFParagraph bodyParagraph() {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(BODY_STYLE);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBetween(1.15);
        return paragraph;
    }

    private void configureTable(XWPFTable table, int[] widthsDxa) {
        int total = java.util.Arrays.stream(widthsDxa).sum();
        if (total != CONTENT_WIDTH_DXA) {
            throw new IllegalArgumentException(
                    "Table widths must total " + CONTENT_WIDTH_DXA);
        }
        table.setWidth(CONTENT_WIDTH_DXA);
        table.setTableAlignment(TableRowAlign.LEFT);
        table.setCellMargins(80, 120, 80, 120);
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblBorders borders = properties.isSetTblBorders()
                ? properties.getTblBorders()
                : properties.addNewTblBorders();
        applyBorder(borders.addNewTop());
        applyBorder(borders.addNewBottom());
        applyBorder(borders.addNewInsideH());
        applyBorder(borders.addNewInsideV());
        applyBorder(borders.addNewLeft());
        applyBorder(borders.addNewRight());

        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int width : widthsDxa) {
            CTTblGridCol column = grid.addNewGridCol();
            column.setW(BigInteger.valueOf(width));
        }
        XWPFTableRow firstRow = table.getRow(0);
        for (int index = 0; index < widthsDxa.length; index++) {
            setCellWidth(firstRow.getCell(index), widthsDxa[index]);
        }
    }

    private void writeCell(
            XWPFTableCell cell,
            String value,
            boolean header,
            boolean leftAligned
    ) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph paragraph = cell.getParagraphs().getFirst();
        paragraph.setAlignment(leftAligned
                ? ParagraphAlignment.LEFT
                : ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        format(run, header ? 8 : 8.5, header, "000000");
        run.setText(clean(value));
    }

    private void setCellWidth(XWPFTableCell cell, int widthDxa) {
        CTTcPr properties = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr()
                : cell.getCTTc().addNewTcPr();
        var width = properties.isSetTcW()
                ? properties.getTcW()
                : properties.addNewTcW();
        width.setW(BigInteger.valueOf(widthDxa));
        width.setType(
                org.openxmlformats.schemas.wordprocessingml.x2006.main
                        .STTblWidth.DXA
        );
        properties.addNewVAlign().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main
                        .STVerticalJc.CENTER
        );
    }

    private void applyBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setColor("DADCE0");
        border.setSz(BigInteger.valueOf(4));
    }

    private void format(
            XWPFRun run,
            double size,
            boolean bold,
            String color
    ) {
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        run.setUnderline(
                org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
        );
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value.replace('\u00A0', ' ').trim();
    }
}
