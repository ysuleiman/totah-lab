package totah.lab.web.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.Closeable;
import java.io.OutputStream;
import java.util.List;

final class PdfReportDocument implements Closeable {

    private static final Color INK = new Color(23, 36, 33);
    private static final Color MUTED = new Color(92, 108, 101);
    private static final Color ACCENT = new Color(215, 255, 95);
    private static final Color PALE_GREEN = new Color(240, 246, 232);
    private static final Color PALE_RED = new Color(255, 239, 235);
    private static final Color LINE = new Color(218, 224, 216);
    private static final Color PALE_GRAY = new Color(244, 246, 241);

    private final Document document;
    private final Font regular = font(FontFactory.HELVETICA, 10, INK);
    private final Font bold = font(
            FontFactory.HELVETICA_BOLD,
            10,
            INK
    );
    private final Font mono = font(FontFactory.COURIER, 7, INK);
    private PdfPTable activeTable;

    PdfReportDocument(OutputStream output)
            throws DocumentException {
        document = new Document(PageSize.LETTER, 48, 48, 54, 48);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new ReportPageEvent());
        document.open();
    }

    void newPage() throws DocumentException {
        flushTable();
        document.newPage();
    }

    void title(String text) throws DocumentException {
        flushTable();
        Paragraph paragraph = new Paragraph(
                clean(text),
                font(FontFactory.HELVETICA_BOLD, 25, INK)
        );
        paragraph.setLeading(29);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    void sectionTitle(String eyebrow, String title)
            throws DocumentException {
        flushTable();
        Paragraph label = new Paragraph(
                clean(eyebrow).toUpperCase(),
                font(FontFactory.HELVETICA_BOLD, 7, MUTED)
        );
        label.setSpacingBefore(10);
        label.setSpacingAfter(4);
        document.add(label);
        Paragraph heading = new Paragraph(
                clean(title),
                font(FontFactory.HELVETICA_BOLD, 16, INK)
        );
        heading.setLeading(19);
        heading.setSpacingAfter(8);
        heading.setKeepTogether(true);
        document.add(heading);
    }

    void paragraph(String text) throws DocumentException {
        flushTable();
        Paragraph paragraph = new Paragraph(clean(text), regular);
        paragraph.setLeading(15);
        paragraph.setSpacingAfter(9);
        document.add(paragraph);
    }

    void callout(String label, String text) throws DocumentException {
        flushTable();
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSpacingAfter(10);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(PALE_GREEN);
        cell.setPadding(12);
        Paragraph title = new Paragraph(
                clean(label).toUpperCase(),
                font(FontFactory.HELVETICA_BOLD, 7, MUTED)
        );
        title.setSpacingAfter(5);
        cell.addElement(title);
        Paragraph body = new Paragraph(clean(text), font(
                FontFactory.HELVETICA,
                9,
                INK
        ));
        body.setLeading(13);
        cell.addElement(body);
        table.addCell(cell);
        document.add(table);
    }

    void metrics(List<Metric> metrics)
            throws DocumentException {
        flushTable();
        PdfPTable table = new PdfPTable(metrics.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(12);
        for (Metric metric : metrics) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(Color.WHITE);
            cell.setBorderWidth(3);
            cell.setBackgroundColor(PALE_GRAY);
            cell.setPadding(9);
            Paragraph label = new Paragraph(
                    clean(metric.label()).toUpperCase(),
                    font(FontFactory.HELVETICA_BOLD, 6, MUTED)
            );
            label.setSpacingAfter(4);
            cell.addElement(label);
            cell.addElement(new Paragraph(clean(metric.value()), font(
                    FontFactory.COURIER,
                    10,
                    INK
            )));
            table.addCell(cell);
        }
        document.add(table);
    }

    void residueSequence(List<String> residues)
            throws DocumentException {
        flushTable();
        Paragraph sequence = new Paragraph();
        sequence.setLeading(12);
        sequence.setSpacingAfter(8);
        for (int index = 0; index < residues.size(); index++) {
            if (index > 0) {
                sequence.add(new Chunk("   ", mono));
            }
            sequence.add(new Chunk(clean(residues.get(index)), mono));
        }
        document.add(sequence);
    }

    void tableHeader(float[] widths, String... labels)
            throws DocumentException {
        flushTable();
        activeTable = new PdfPTable(widths.length);
        activeTable.setWidthPercentage(100);
        activeTable.setWidths(widths);
        activeTable.setHeaderRows(1);
        activeTable.setSpacingBefore(4);
        activeTable.setSpacingAfter(10);
        for (String label : labels) {
            PdfPCell cell = textCell(
                    clean(label).toUpperCase(),
                    font(FontFactory.HELVETICA_BOLD, 6, MUTED),
                    PALE_GREEN
            );
            cell.setPaddingTop(7);
            cell.setPaddingBottom(7);
            activeTable.addCell(cell);
        }
    }

    void tableRow(
            float[] widths,
            boolean highlighted,
            String... values
    ) {
        Color background = highlighted ? PALE_RED : Color.WHITE;
        for (String value : values) {
            PdfPCell cell = textCell(clean(value), mono, background);
            cell.setPaddingTop(3);
            cell.setPaddingBottom(3);
            activeTable.addCell(cell);
        }
    }

    void finishTable() throws DocumentException {
        flushTable();
    }

    void finish() throws DocumentException {
        flushTable();
        if (document.isOpen()) {
            document.close();
        }
    }

    private PdfPCell textCell(
            String value,
            Font cellFont,
            Color background
    ) {
        PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.4f);
        cell.setBackgroundColor(background);
        cell.setPaddingLeft(5);
        cell.setPaddingRight(5);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private void flushTable() throws DocumentException {
        if (activeTable != null) {
            document.add(activeTable);
            activeTable = null;
        }
    }

    private static Font font(
            String family,
            float size,
            Color color
    ) {
        return FontFactory.getFont(family, size, Font.NORMAL, color);
    }

    private String clean(String value) {
        return value
                .replace("\u00c5", "Angstrom")
                .replace("\u2264", "<=")
                .replace("\u2013", "-")
                .replace("\u2014", "-")
                .replace("\u00b7", "/");
    }

    @Override
    public void close() {
        if (document.isOpen()) {
            document.close();
        }
    }

    record Metric(String label, String value) {
    }

    private static final class ReportPageEvent
            extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.saveState();
            canvas.setColorStroke(LINE);
            canvas.setLineWidth(0.5f);
            canvas.moveTo(document.left(), document.top() + 20);
            canvas.lineTo(document.right(), document.top() + 20);
            canvas.moveTo(document.left(), document.bottom() - 16);
            canvas.lineTo(document.right(), document.bottom() - 16);
            canvas.stroke();
            canvas.restoreState();

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_LEFT,
                    new Phrase(
                            "TOTAH LAB  /  POCKET REPORT",
                            font(FontFactory.HELVETICA_BOLD, 7, MUTED)
                    ),
                    document.left(),
                    document.top() + 27,
                    0
            );
            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_RIGHT,
                    new Phrase(
                            "Page " + writer.getPageNumber(),
                            font(FontFactory.COURIER, 7, MUTED)
                    ),
                    document.right(),
                    document.bottom() - 27,
                    0
            );
        }
    }
}
