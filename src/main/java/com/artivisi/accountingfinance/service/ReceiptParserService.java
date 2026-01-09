package com.artivisi.accountingfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptParserService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptParserService.class);
    private static final String PATTERN_RUPIAH_AMOUNT = "Rp\\s*([\\d.,]+)";
    private static final String PATTERN_DATE_DMY = "(\\d{1,2}\\s+\\w+\\s+\\d{4})";

    // Receipt type constants
    private static final String RECEIPT_TYPE_JAGO = "jago";
    private static final String RECEIPT_TYPE_CIMB = "cimb";
    private static final String RECEIPT_TYPE_GOPAY = "gopay";
    private static final String RECEIPT_TYPE_BYOND = "byond";
    private static final String RECEIPT_TYPE_UNKNOWN = "unknown";

    private static final Map<String, Integer> INDONESIAN_MONTHS = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("jan", 1),
            Map.entry("februari", 2), Map.entry("feb", 2),
            Map.entry("maret", 3), Map.entry("mar", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("mei", 5), Map.entry("may", 5),
            Map.entry("juni", 6), Map.entry("jun", 6),
            Map.entry("juli", 7), Map.entry("jul", 7),
            Map.entry("agustus", 8), Map.entry("agu", 8), Map.entry("aug", 8),
            Map.entry("september", 9), Map.entry("sep", 9),
            Map.entry("oktober", 10), Map.entry("okt", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("desember", 12), Map.entry("des", 12), Map.entry("dec", 12)
    );

    public record ParsedReceipt(
            String receiptType,
            String merchantName,
            BigDecimal amount,
            LocalDate transactionDate,
            String reference,
            String rawText,
            BigDecimal merchantConfidence,
            BigDecimal amountConfidence,
            BigDecimal dateConfidence,
            BigDecimal overallConfidence
    ) {}

    public ParsedReceipt parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return null;
        }

        String receiptType = detectReceiptType(ocrText);
        log.debug("Detected receipt type: {}", receiptType);

        return switch (receiptType) {
            case RECEIPT_TYPE_JAGO -> parseJago(ocrText);
            case RECEIPT_TYPE_CIMB -> parseCimb(ocrText);
            case RECEIPT_TYPE_GOPAY -> parseGopay(ocrText);
            case RECEIPT_TYPE_BYOND -> parseByond(ocrText);
            default -> parseGeneric(ocrText);
        };
    }

    private String detectReceiptType(String text) {
        String textLower = text.toLowerCase();

        if (textLower.contains(RECEIPT_TYPE_BYOND)) return RECEIPT_TYPE_BYOND;
        if (textLower.contains(RECEIPT_TYPE_JAGO) && textLower.contains("syariah")) return RECEIPT_TYPE_JAGO;
        if (textLower.contains("octo")) return RECEIPT_TYPE_CIMB;
        if (textLower.contains(RECEIPT_TYPE_GOPAY)) return RECEIPT_TYPE_GOPAY;
        if (textLower.contains(RECEIPT_TYPE_JAGO)) return RECEIPT_TYPE_JAGO;
        if (textLower.contains(RECEIPT_TYPE_CIMB)) return RECEIPT_TYPE_CIMB;
        if (textLower.contains("bsi")) return RECEIPT_TYPE_BYOND;

        return RECEIPT_TYPE_UNKNOWN;
    }

    private ParsedReceipt parseJago(String text) {
        String merchant = extractPattern(text, "Acquirer Name\\s*\\n?\\s*([A-Za-z\\s]+?)(?:\\n|Fee)");
        BigDecimal amount = extractAmount(text, PATTERN_RUPIAH_AMOUNT);
        LocalDate date = extractDate(text, PATTERN_DATE_DMY);
        String reference = extractPattern(text, "Reference Number\\s*\\n?\\s*([a-z0-9]+)");

        BigDecimal merchantConf = merchant != null ? new BigDecimal("0.85") : BigDecimal.ZERO;
        BigDecimal amountConf = amount != null ? new BigDecimal("0.95") : BigDecimal.ZERO;
        BigDecimal dateConf = date != null ? new BigDecimal("0.90") : BigDecimal.ZERO;

        return new ParsedReceipt(
                RECEIPT_TYPE_JAGO, merchant, amount, date, reference, text,
                merchantConf, amountConf, dateConf,
                calculateOverallConfidence(merchantConf, amountConf, dateConf)
        );
    }

    private ParsedReceipt parseCimb(String text) {
        String merchant = extractRecipientFromCimb(text);
        BigDecimal amount = extractAmount(text, "IDR\\s*([\\d,\\.]+)");
        LocalDate date = extractDate(text, "Transaction Time\\s*\\n?\\s*(\\d{1,2}\\s+\\w+\\s+\\d{4})");
        String reference = extractPattern(text, "(\\d{12})\\s*$");

        BigDecimal merchantConf = merchant != null ? new BigDecimal("0.80") : BigDecimal.ZERO;
        BigDecimal amountConf = amount != null ? new BigDecimal("0.90") : BigDecimal.ZERO;
        BigDecimal dateConf = date != null ? new BigDecimal("0.85") : BigDecimal.ZERO;

        return new ParsedReceipt(
                RECEIPT_TYPE_CIMB, merchant, amount, date, reference, text,
                merchantConf, amountConf, dateConf,
                calculateOverallConfidence(merchantConf, amountConf, dateConf)
        );
    }

    private ParsedReceipt parseGopay(String text) {
        String merchant = extractPattern(text, "Ditransfer ke\\s+(.+)\\n");
        BigDecimal amount = extractAmount(text, PATTERN_RUPIAH_AMOUNT);
        LocalDate date = extractDate(text, "Tanggal\\s*\\n?\\s*" + PATTERN_DATE_DMY);
        String reference = extractPattern(text, "ID transaksi\\s*\\n?\\s*([a-z0-9]+)");

        BigDecimal merchantConf = merchant != null ? new BigDecimal("0.90") : BigDecimal.ZERO;
        BigDecimal amountConf = amount != null ? new BigDecimal("0.95") : BigDecimal.ZERO;
        BigDecimal dateConf = date != null ? new BigDecimal("0.90") : BigDecimal.ZERO;

        return new ParsedReceipt(
                RECEIPT_TYPE_GOPAY, merchant, amount, date, reference, text,
                merchantConf, amountConf, dateConf,
                calculateOverallConfidence(merchantConf, amountConf, dateConf)
        );
    }

    private ParsedReceipt parseByond(String text) {
        String merchant = extractPattern(text, "Nama Merchant\\s*\\n?\\s*(.+)\\n");
        BigDecimal amount = extractAmount(text, PATTERN_RUPIAH_AMOUNT);
        LocalDate date = extractDate(text, PATTERN_DATE_DMY);
        String reference = extractPattern(text, "Nomor Transaksi\\s*\\n?\\s*(FT[A-Z0-9]+)");

        BigDecimal merchantConf = merchant != null ? new BigDecimal("0.85") : BigDecimal.ZERO;
        BigDecimal amountConf = amount != null ? new BigDecimal("0.90") : BigDecimal.ZERO;
        BigDecimal dateConf = date != null ? new BigDecimal("0.85") : BigDecimal.ZERO;

        return new ParsedReceipt(
                RECEIPT_TYPE_BYOND, merchant, amount, date, reference, text,
                merchantConf, amountConf, dateConf,
                calculateOverallConfidence(merchantConf, amountConf, dateConf)
        );
    }

    private ParsedReceipt parseGeneric(String text) {
        // Try common patterns
        BigDecimal amount = extractAmount(text, "(?:Rp|IDR)\\s*([\\d.,]+)");
        if (amount == null) {
            amount = extractAmount(text, "TOTAL\\s*(?:Rp|IDR)?\\s*([\\d.,]+)");
        }

        LocalDate date = extractDate(text, "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})");
        if (date == null) {
            date = extractDate(text, PATTERN_DATE_DMY);
        }

        // Try to extract merchant from first non-empty line
        String merchant = extractFirstMerchantLine(text);

        BigDecimal merchantConf = merchant != null ? new BigDecimal("0.50") : BigDecimal.ZERO;
        BigDecimal amountConf = amount != null ? new BigDecimal("0.70") : BigDecimal.ZERO;
        BigDecimal dateConf = date != null ? new BigDecimal("0.60") : BigDecimal.ZERO;

        return new ParsedReceipt(
                RECEIPT_TYPE_UNKNOWN, merchant, amount, date, null, text,
                merchantConf, amountConf, dateConf,
                calculateOverallConfidence(merchantConf, amountConf, dateConf)
        );
    }

    private String extractPattern(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            log.debug("Pattern extraction failed: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractAmount(String text, String regex) {
        String amountStr = extractPattern(text, regex);
        if (amountStr == null) return null;

        try {
            // Remove thousands separators and normalize decimal
            String normalized = amountStr
                    .replace(".", "")  // Remove thousand separator (Indonesian)
                    .replace(",", "."); // Convert decimal separator

            // Handle case like "10,000,00000" -> remove trailing zeros after comma
            if (normalized.contains(".") && normalized.length() - normalized.indexOf(".") > 3) {
                normalized = normalized.substring(0, normalized.indexOf(".") + 3);
            }

            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            // Try alternative parsing
            try {
                String cleaned = amountStr.replaceAll("[^\\d]", "");
                return new BigDecimal(cleaned);
            } catch (NumberFormatException e2) {
                log.debug("Amount parsing failed for: {}", amountStr);
                return null;
            }
        }
    }

    private LocalDate extractDate(String text, String regex) {
        String dateStr = extractPattern(text, regex);
        if (dateStr == null) return null;

        try {
            // Try Indonesian format: "26 Nov 2025"
            Pattern indoPattern = Pattern.compile("(\\d{1,2})\\s+(\\w+)\\s+(\\d{4})");
            Matcher m = indoPattern.matcher(dateStr);
            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                String monthStr = m.group(2).toLowerCase();
                int year = Integer.parseInt(m.group(3));

                Integer month = INDONESIAN_MONTHS.get(monthStr);
                if (month != null) {
                    return LocalDate.of(year, month, day);
                }
            }

            // Try standard formats
            for (String pattern : new String[]{"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd"}) {
                try {
                    return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException ignored) {}
            }

        } catch (Exception e) {
            log.debug("Date parsing failed for: {}", dateStr);
        }
        return null;
    }

    private String extractRecipientFromCimb(String text) {
        // Look for name pattern after IDR amount
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("IDR") && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (nextLine.matches("^[A-Z][A-Z\\s]+$")) {
                    return nextLine;
                }
            }
        }
        return null;
    }

    private String extractFirstMerchantLine(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 3 && !trimmed.matches("^[\\d\\s/\\-:]+$")) {
                return trimmed;
            }
        }
        return null;
    }

    private BigDecimal calculateOverallConfidence(BigDecimal merchant, BigDecimal amount, BigDecimal date) {
        // Weighted average: amount (40%), date (30%), merchant (30%)
        BigDecimal weighted = amount.multiply(new BigDecimal("0.40"))
                .add(date.multiply(new BigDecimal("0.30")))
                .add(merchant.multiply(new BigDecimal("0.30")));

        return weighted.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
