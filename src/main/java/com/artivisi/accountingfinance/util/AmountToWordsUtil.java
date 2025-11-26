package com.artivisi.accountingfinance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountToWordsUtil {

    private static final String[] SATUAN = {
            "", "satu", "dua", "tiga", "empat", "lima",
            "enam", "tujuh", "delapan", "sembilan", "sepuluh", "sebelas"
    };

    private AmountToWordsUtil() {
        // Utility class
    }

    public static String toWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "nol rupiah";
        }

        long value = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        String result = convertToWords(value);
        return capitalize(result.trim()) + " rupiah";
    }

    private static String convertToWords(long n) {
        if (n < 12) {
            return SATUAN[(int) n];
        } else if (n < 20) {
            return SATUAN[(int) (n - 10)] + " belas";
        } else if (n < 100) {
            return SATUAN[(int) (n / 10)] + " puluh " + convertToWords(n % 10);
        } else if (n < 200) {
            return "seratus " + convertToWords(n - 100);
        } else if (n < 1000) {
            return SATUAN[(int) (n / 100)] + " ratus " + convertToWords(n % 100);
        } else if (n < 2000) {
            return "seribu " + convertToWords(n - 1000);
        } else if (n < 1_000_000) {
            return convertToWords(n / 1000) + " ribu " + convertToWords(n % 1000);
        } else if (n < 1_000_000_000) {
            return convertToWords(n / 1_000_000) + " juta " + convertToWords(n % 1_000_000);
        } else if (n < 1_000_000_000_000L) {
            return convertToWords(n / 1_000_000_000) + " miliar " + convertToWords(n % 1_000_000_000);
        } else {
            return convertToWords(n / 1_000_000_000_000L) + " triliun " + convertToWords(n % 1_000_000_000_000L);
        }
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Clean up multiple spaces
        text = text.replaceAll("\\s+", " ").trim();
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
