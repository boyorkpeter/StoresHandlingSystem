package com.registry.storesapp;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PDFGenerator {

    public static void generateVoucher(TransactionLog log, File outputFile) throws Exception {
        // Setup standard document dimensions with professional page margins
        Document document = new Document(PageSize.A4, 45, 45, 45, 45);
        PdfWriter.getInstance(document, new FileOutputStream(outputFile));

        document.open();

        // 1. Typography Definition Setup
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, java.awt.Color.GRAY);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC, java.awt.Color.LIGHT_GRAY);

        // 2. DYNAMIC LETTERHEAD IMAGE INSERTION
        try {
            // Locates the graphic image out of your compiled target directory resources folder
            String imagePath = PDFGenerator.class.getResource("/com/registry/storesapp/background.jpg").getPath();
            Image letterheadLogo = Image.getInstance(imagePath);

            letterheadLogo.setAlignment(Element.ALIGN_CENTER);
            // Scale to look like a centered top banner
            letterheadLogo.scaleToFit(120, 60);
            letterheadLogo.setSpacingAfter(10);

            document.add(letterheadLogo);
        } catch (Exception imgEx) {
            // System fallback: if image is missing, it skips smoothly to typography without crashing
            System.out.println("Letterhead graphic image resource asset not found, applying text fallback.");
        }

        // 3. Centered Professional Institutional Text Header
        Paragraph title = new Paragraph("BIRTHS AND DEATHS REGISTRY", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(2);
        document.add(title);

        Paragraph subTitle = new Paragraph("STORES MANAGEMENT SYSTEM - VOUCHER AUDIT", subtitleFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        subTitle.setSpacingAfter(15);
        document.add(subTitle);

        // 4. Crisp Geometric Accent Separator Line
        PdfPTable accentBar = new PdfPTable(1);
        accentBar.setWidthPercentage(100);
        PdfPCell barCell = new PdfPCell();
        barCell.setBorder(Rectangle.BOTTOM);
        barCell.setBorderWidthBottom(1.5f);
        barCell.setBorderColor(java.awt.Color.DARK_GRAY);
        barCell.setPadding(0);
        accentBar.addCell(barCell);
        accentBar.setSpacingAfter(25);
        document.add(accentBar);

        // 5. Dynamic Voucher Subheading Determination
        boolean isStockIn = "STOCK IN".equalsIgnoreCase(log.getType());
        String voucherType = isStockIn ? "GOODS RECEIVED NOTE (GRN)" : "STORE ISSUE VOUCHER (SIV)";

        Paragraph docHeading = new Paragraph(voucherType, sectionFont);
        docHeading.setAlignment(Element.ALIGN_LEFT);
        docHeading.setSpacingAfter(12);
        document.add(docHeading);

        // 6. Symmetrical Metadata Info Grid
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{50f, 50f});
        metaTable.setSpacingAfter(30);

        // Standardized document reference processing using date/time fallbacks safely
        String docRefId = (log.getType() != null ? log.getType().replace(" ", "") : "STORES") + "-" + (log.getDate() != null ? log.getDate().replaceAll("[^0-9]", "") : "0000");
        String compiledTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        metaTable.addCell(createMetaCell("Document Ref: " + docRefId, thFont));
        metaTable.addCell(createMetaCell("Transaction Date: " + log.getDate(), tdFont));
        metaTable.addCell(createMetaCell("Compiled On: " + compiledTimestamp, tdFont));
        metaTable.addCell(createMetaCell("Status: Verified Official", tdFont));

        document.add(metaTable);

        // 7. CONDITIONAL DATATABLE INTERFACE DESIGN (Hides unused columns)
        PdfPTable dataTable = new PdfPTable(3);
        dataTable.setWidthPercentage(100);
        dataTable.setWidths(new float[]{45f, 15f, 40f});
        dataTable.setSpacingAfter(75);

        if (isStockIn) {
            // STOCK IN (GRN): Stationery Description, Qty, Authorized Supplier
            dataTable.addCell(createHeaderCell("Stationery Description", thFont));
            dataTable.addCell(createHeaderCell("Qty Allocated", thFont));
            dataTable.addCell(createHeaderCell("Authorized Supplier", thFont));

            String supplierText = (log.getSupplier() == null || log.getSupplier().trim().isEmpty()) ? "-" : log.getSupplier();

            dataTable.addCell(createDataCell(log.getItemName(), tdFont, Element.ALIGN_LEFT));
            dataTable.addCell(createDataCell(String.valueOf(log.getQuantity()), tdFont, Element.ALIGN_CENTER));
            dataTable.addCell(createDataCell(supplierText, tdFont, Element.ALIGN_LEFT));
        } else {
            // STOCK OUT (SIV): Stationery Description, Qty, Recipient Officer/Dept
            dataTable.addCell(createHeaderCell("Stationery Description", thFont));
            dataTable.addCell(createHeaderCell("Qty Allocated", thFont));
            dataTable.addCell(createHeaderCell("Recipient Officer/Dept", thFont));

            String recipientText = (log.getRecipient() == null || log.getRecipient().trim().isEmpty()) ? "-" : log.getRecipient();

            dataTable.addCell(createDataCell(log.getItemName(), tdFont, Element.ALIGN_LEFT));
            dataTable.addCell(createDataCell(String.valueOf(log.getQuantity()), tdFont, Element.ALIGN_CENTER));
            dataTable.addCell(createDataCell(recipientText, tdFont, Element.ALIGN_LEFT));
        }

        document.add(dataTable);

        // 8. Symmetrical Sign-Off Authorization Row
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{50f, 50f});

        PdfPCell signCell1 = new PdfPCell(new Paragraph("Issued By: ___________________________ \n(Stores Officer Sign)", tdFont));
        signCell1.setBorder(Rectangle.NO_BORDER);
        signCell1.setHorizontalAlignment(Element.ALIGN_LEFT);

        String counterSignTitle = isStockIn ? "Delivered By: ___________________________ \n(Vendor/Carrier Sign)" : "Received By: ___________________________ \n(Recipient Officer Sign)";
        PdfPCell signCell2 = new PdfPCell(new Paragraph(counterSignTitle, tdFont));
        signCell2.setBorder(Rectangle.NO_BORDER);
        signCell2.setHorizontalAlignment(Element.ALIGN_RIGHT);

        signTable.addCell(signCell1);
        signTable.addCell(signCell2);
        signTable.setSpacingAfter(45);
        document.add(signTable);

        // 9. System Verification Footer Notes
        Paragraph footer = new Paragraph(
                "This is an automated systemic physical paper trail output securely compiled by the Registry Stores Management Platform. " +
                        "Manual adjustments invalidate this verification.", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }

    // --- NEW COMPILE METHOD: COMPILING BULK FILTERED SUMMARY RECORD LEDGERS ---
    // --- UPDATED METHOD: DYNAMIC COMPILING OF FILTERED SUMMARY LEDGER REPORT ---
    public static void generateSummaryLedger(ObservableList<TransactionLog> data, String filterProfileScope, File outputFile) throws Exception {
        // Landscape rotation optimized for data column density
        Document document = new Document(PageSize.A4.rotate(), 35, 35, 35, 35);
        PdfWriter.getInstance(document, new FileOutputStream(outputFile));

        document.open();

        // 1. Typography Setup
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, java.awt.Color.GRAY);
        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, java.awt.Color.DARK_GRAY);
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC, java.awt.Color.LIGHT_GRAY);

        // 2. DYNAMIC HEADER BANNER GRAPHIC INSERTION
        try {
            String imagePath = PDFGenerator.class.getResource("/com/registry/storesapp/background.jpg").getPath();
            Image letterheadLogo = Image.getInstance(imagePath);
            letterheadLogo.setAlignment(Element.ALIGN_CENTER);
            letterheadLogo.scaleToFit(140, 70);
            letterheadLogo.setSpacingAfter(8);
            document.add(letterheadLogo);
        } catch (Exception imgEx) {
            System.out.println("Summary Header asset not accessible, using typography layout fallback.");
        }

        // 3. Header Title Blocks
        Paragraph title = new Paragraph("BIRTHS AND DEATHS REGISTRY", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(2);
        document.add(title);

        Paragraph subTitle = new Paragraph("STORES MANAGEMENT SYSTEM - TRANSACTION AUDIT LEDGER SUMMARY", subtitleFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        subTitle.setSpacingAfter(6);
        document.add(subTitle);

        String normalizedScope = filterProfileScope != null ? filterProfileScope.trim().toUpperCase() : "ALL TRANSACTIONS";
        String runningMetaString = String.format("Scope Profile Filter: %s  |  Report Compilation Date: %s",
                normalizedScope,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        Paragraph metaParagraph = new Paragraph(runningMetaString, subtitleFont);
        metaParagraph.setAlignment(Element.ALIGN_CENTER);
        metaParagraph.setSpacingAfter(15);
        document.add(metaParagraph);

        // Decorative Structural Accent Bar Separator
        PdfPTable accentBar = new PdfPTable(1);
        accentBar.setWidthPercentage(100);
        PdfPCell barCell = new PdfPCell();
        barCell.setBorder(Rectangle.BOTTOM);
        barCell.setBorderWidthBottom(1.5f);
        barCell.setBorderColor(java.awt.Color.DARK_GRAY);
        barCell.setPadding(0);
        accentBar.addCell(barCell);
        accentBar.setSpacingAfter(20);
        document.add(accentBar);

        // 4. CONDITIONAL MATRIX COLUMN DETERMINATION
        boolean isOnlyStockIn = "STOCK IN".equalsIgnoreCase(normalizedScope);
        boolean isOnlyStockOut = "STOCK OUT".equalsIgnoreCase(normalizedScope);

        PdfPTable gridTable;
        if (isOnlyStockIn) {
            // Drop Recipient column entirely: Flow Type, Item Name, Qty, Supplier, Timestamp (5 Cols)
            gridTable = new PdfPTable(5);
            gridTable.setWidths(new float[]{15f, 42f, 10f, 18f, 15f});
        } else if (isOnlyStockOut) {
            // Drop Supplier column entirely: Flow Type, Item Name, Qty, Recipient, Timestamp (5 Cols)
            gridTable = new PdfPTable(5);
            gridTable.setWidths(new float[]{15f, 42f, 10f, 18f, 15f});
        } else {
            // General mixed ledger format (All 6 columns displayed)
            gridTable = new PdfPTable(6);
            gridTable.setWidths(new float[]{13f, 32f, 8f, 17f, 17f, 13f});
        }
        gridTable.setWidthPercentage(100);
        gridTable.setSpacingAfter(35);

        // 5. RENDER DYNAMIC HEADERS
        gridTable.addCell(createHeaderCell("Flow Type", thFont));
        gridTable.addCell(createHeaderCell("Item Description Name", thFont));
        gridTable.addCell(createHeaderCell("Qty", thFont));

        if (!isOnlyStockOut) {
            gridTable.addCell(createHeaderCell("Supplier Source", thFont));
        }
        if (!isOnlyStockIn) {
            gridTable.addCell(createHeaderCell("Recipient Destination", thFont));
        }
        gridTable.addCell(createHeaderCell("Ledger Timestamp", thFont));

        // 6. POPULATE DATA CELLS CONDITIONALLY
        for (TransactionLog log : data) {
            String supplierText = (log.getSupplier() == null || log.getSupplier().trim().isEmpty()) ? "-" : log.getSupplier();
            String recipientText = (log.getRecipient() == null || log.getRecipient().trim().isEmpty()) ? "-" : log.getRecipient();

            gridTable.addCell(createDataCell(log.getType(), tdFont, Element.ALIGN_CENTER));
            gridTable.addCell(createDataCell(log.getItemName(), tdFont, Element.ALIGN_LEFT));
            gridTable.addCell(createDataCell(String.valueOf(log.getQuantity()), tdFont, Element.ALIGN_CENTER));

            if (!isOnlyStockOut) {
                gridTable.addCell(createDataCell(supplierText, tdFont, Element.ALIGN_LEFT));
            }
            if (!isOnlyStockIn) {
                gridTable.addCell(createDataCell(recipientText, tdFont, Element.ALIGN_LEFT));
            }
            gridTable.addCell(createDataCell(log.getDate(), tdFont, Element.ALIGN_CENTER));
        }

        document.add(gridTable);

        // Verification Footer
        Paragraph footer = new Paragraph(
                "This report compiles active live historical entries filtered directly from the repository system logs. Generated automatically.", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }
    // --- STRUCTURAL UTILITY METHODS FOR COMPONENT CELL DESIGN ---

    private static PdfPCell createMetaCell(String content, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(content, font));
        cell.setPadding(8);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        return cell;
    }

    private static PdfPCell createHeaderCell(String headerTitle, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(headerTitle, font));
        cell.setBackgroundColor(new java.awt.Color(235, 238, 242));
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        return cell;
    }

    private static PdfPCell createDataCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(8);
        cell.setPaddingRight(8);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        return cell;
    }
}