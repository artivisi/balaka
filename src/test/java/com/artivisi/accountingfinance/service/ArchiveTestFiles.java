package com.artivisi.accountingfinance.service;

import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Real PDF, PNG and ZIP bytes for archive import and disclosure export tests. */
public final class ArchiveTestFiles {

    private ArchiveTestFiles() {
    }

    /** A one-page PDF whose text layer holds the given lines. */
    public static byte[] pdf(String... lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        for (String line : lines) {
            document.add(new Paragraph(line));
        }
        document.close();
        return out.toByteArray();
    }

    public static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            image.setRGB(1, 1, 0xFF0000);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ZIP of the entries in order; a String value is written as UTF-8. */
    public static byte[] zip(Map<String, Object> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, Object> e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue() instanceof String s ? s.getBytes(StandardCharsets.UTF_8) : (byte[]) e.getValue());
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, byte[]> unzip(byte[] bytes) {
        try {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    entries.put(entry.getName(), in.readAllBytes());
                }
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
