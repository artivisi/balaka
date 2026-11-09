package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.enums.TextExtractionStatus;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Searchable text of an archived file. PDF text comes from OpenPDF's extractor (already on the classpath for PDF
 * export). Images have no text layer and no OCR is run, so they are searchable by metadata only.
 */
@Component
public class DocumentTextExtractor {

    /** PostgreSQL limits a tsvector to 1 MB; text beyond this many characters is not indexed. */
    public static final int MAX_INDEXED_CHARS = 500_000;

    public record Extraction(TextExtractionStatus status, String text) {}

    /** Throws IllegalArgumentException when a PDF cannot be parsed. */
    public Extraction extract(byte[] content, String contentType) {
        if (!"application/pdf".equals(contentType)) {
            return new Extraction(TextExtractionStatus.NOT_SUPPORTED, null);
        }
        StringBuilder text = new StringBuilder();
        PdfReader reader = null;
        try {
            reader = new PdfReader(content);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages() && text.length() < MAX_INDEXED_CHARS; page++) {
                String pageText = extractor.getTextFromPage(page);
                if (pageText != null && !pageText.isBlank()) {
                    text.append(pageText.strip()).append('\n');
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("PDF tidak dapat dibaca: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        String result = text.toString().replace('\u0000', ' ').strip();
        if (result.isEmpty()) {
            return new Extraction(TextExtractionStatus.NO_TEXT, null);
        }
        return new Extraction(TextExtractionStatus.EXTRACTED,
                result.length() > MAX_INDEXED_CHARS ? result.substring(0, MAX_INDEXED_CHARS) : result);
    }
}
