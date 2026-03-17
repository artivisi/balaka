package com.artivisi.accountingfinance.entity;

import java.math.BigDecimal;

/**
 * TER (Tarif Efektif Rata-rata) categories per PMK 168/2023.
 * Maps PTKP status to TER category for monthly PPh 21 withholding.
 *
 * Category A: TK_0, TK_1, K_0
 * Category B: TK_2, TK_3, K_1, K_2
 * Category C: K_3, K_I_0, K_I_1, K_I_2, K_I_3
 */
public enum TerCategory {

    A(new long[]{
            5_400_000L, 5_650_000L, 5_950_000L, 6_300_000L, 6_750_000L,
            7_500_000L, 8_550_000L, 9_650_000L, 10_050_000L, 10_350_000L,
            10_700_000L, 11_050_000L, 11_600_000L, 12_500_000L, 13_750_000L,
            15_100_000L, 16_950_000L, 19_750_000L, 24_150_000L, 26_450_000L,
            28_000_000L, 30_050_000L, 32_400_000L, 35_400_000L, 39_100_000L,
            43_850_000L, 47_800_000L, 51_400_000L, 56_300_000L, 62_200_000L,
            68_600_000L, 77_500_000L, 89_000_000L, 103_000_000L, 125_000_000L,
            157_000_000L, 206_000_000L, 337_000_000L, 454_000_000L, 550_000_000L,
            695_000_000L, 910_000_000L, 1_400_000_000L
    }, new BigDecimal[]{
            BigDecimal.ZERO,
            new BigDecimal("0.25"), new BigDecimal("0.5"), new BigDecimal("0.75"),
            new BigDecimal("1"), new BigDecimal("1.25"), new BigDecimal("1.5"),
            new BigDecimal("1.75"), new BigDecimal("2"), new BigDecimal("2.25"),
            new BigDecimal("2.5"), new BigDecimal("3"), new BigDecimal("3.5"),
            new BigDecimal("4"), new BigDecimal("4.5"), new BigDecimal("5"),
            new BigDecimal("5.5"), new BigDecimal("6"), new BigDecimal("7"),
            new BigDecimal("8"), new BigDecimal("9"), new BigDecimal("10"),
            new BigDecimal("11"), new BigDecimal("12"), new BigDecimal("13"),
            new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("16"),
            new BigDecimal("17"), new BigDecimal("18"), new BigDecimal("19"),
            new BigDecimal("20"), new BigDecimal("21"), new BigDecimal("22"),
            new BigDecimal("23"), new BigDecimal("24"), new BigDecimal("25"),
            new BigDecimal("26"), new BigDecimal("27"), new BigDecimal("28"),
            new BigDecimal("29"), new BigDecimal("30"), new BigDecimal("31"),
            new BigDecimal("32"), new BigDecimal("34")
    }),

    B(new long[]{
            6_200_000L, 6_500_000L, 6_850_000L, 7_300_000L, 9_200_000L,
            10_750_000L, 11_250_000L, 11_600_000L, 12_600_000L, 13_600_000L,
            14_950_000L, 16_400_000L, 18_450_000L, 21_850_000L, 26_000_000L,
            27_700_000L, 29_350_000L, 31_450_000L, 33_950_000L, 37_100_000L,
            41_100_000L, 45_800_000L, 49_500_000L, 53_800_000L, 58_500_000L,
            64_000_000L, 71_000_000L, 80_000_000L, 93_000_000L, 109_000_000L,
            129_000_000L, 163_000_000L, 211_000_000L, 374_000_000L, 459_000_000L,
            555_000_000L, 704_000_000L, 957_000_000L, 1_405_000_000L
    }, new BigDecimal[]{
            BigDecimal.ZERO,
            new BigDecimal("0.25"), new BigDecimal("0.5"), new BigDecimal("0.75"),
            new BigDecimal("1"), new BigDecimal("1.5"), new BigDecimal("2"),
            new BigDecimal("2.5"), new BigDecimal("3"), new BigDecimal("3.5"),
            new BigDecimal("4"), new BigDecimal("4.5"), new BigDecimal("5"),
            new BigDecimal("5.5"), new BigDecimal("6"), new BigDecimal("7"),
            new BigDecimal("8"), new BigDecimal("9"), new BigDecimal("10"),
            new BigDecimal("11"), new BigDecimal("12"), new BigDecimal("13"),
            new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("16"),
            new BigDecimal("17"), new BigDecimal("18"), new BigDecimal("19"),
            new BigDecimal("20"), new BigDecimal("21"), new BigDecimal("22"),
            new BigDecimal("23"), new BigDecimal("24"), new BigDecimal("25"),
            new BigDecimal("26"), new BigDecimal("27"), new BigDecimal("28"),
            new BigDecimal("29"), new BigDecimal("30"), new BigDecimal("31"),
            new BigDecimal("32"), new BigDecimal("34")
    }),

    C(new long[]{
            6_600_000L, 6_950_000L, 7_350_000L, 7_800_000L, 8_850_000L,
            9_800_000L, 10_950_000L, 11_200_000L, 12_050_000L, 12_950_000L,
            14_150_000L, 15_550_000L, 17_050_000L, 19_500_000L, 22_700_000L,
            26_600_000L, 28_100_000L, 30_100_000L, 32_600_000L, 35_400_000L,
            38_900_000L, 43_000_000L, 47_400_000L, 51_200_000L, 55_800_000L,
            60_400_000L, 66_700_000L, 74_500_000L, 83_200_000L, 95_600_000L,
            110_000_000L, 134_000_000L, 169_000_000L, 221_000_000L, 390_000_000L,
            463_000_000L, 561_000_000L, 709_000_000L, 965_000_000L, 1_419_000_000L
    }, new BigDecimal[]{
            BigDecimal.ZERO,
            new BigDecimal("0.25"), new BigDecimal("0.5"), new BigDecimal("0.75"),
            new BigDecimal("1"), new BigDecimal("1.25"), new BigDecimal("1.5"),
            new BigDecimal("1.75"), new BigDecimal("2"), new BigDecimal("2.25"),
            new BigDecimal("2.5"), new BigDecimal("3"), new BigDecimal("3.5"),
            new BigDecimal("4"), new BigDecimal("4.5"), new BigDecimal("5"),
            new BigDecimal("5.5"), new BigDecimal("6"), new BigDecimal("7"),
            new BigDecimal("8"), new BigDecimal("9"), new BigDecimal("10"),
            new BigDecimal("11"), new BigDecimal("12"), new BigDecimal("13"),
            new BigDecimal("14"), new BigDecimal("15"), new BigDecimal("16"),
            new BigDecimal("17"), new BigDecimal("18"), new BigDecimal("19"),
            new BigDecimal("20"), new BigDecimal("21"), new BigDecimal("22"),
            new BigDecimal("23"), new BigDecimal("24"), new BigDecimal("25"),
            new BigDecimal("26"), new BigDecimal("27"), new BigDecimal("28"),
            new BigDecimal("29"), new BigDecimal("30"), new BigDecimal("31"),
            new BigDecimal("32"), new BigDecimal("34")
    });

    private final long[] upperBounds;
    private final BigDecimal[] rates;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    TerCategory(long[] upperBounds, BigDecimal[] rates) {
        this.upperBounds = upperBounds;
        this.rates = rates;
    }

    /**
     * Lookup TER rate for a given monthly gross income.
     * Returns the rate as a percentage (e.g., 2.5 for 2.5%).
     */
    public BigDecimal lookupRate(BigDecimal monthlyGross) {
        long grossLong = monthlyGross.longValue();
        for (int i = 0; i < upperBounds.length; i++) {
            if (grossLong <= upperBounds[i]) {
                return rates[i];
            }
        }
        // Above highest bracket
        return rates[rates.length - 1];
    }

    /**
     * Get TER category for a given PTKP status.
     */
    public static TerCategory fromPtkpStatus(PtkpStatus ptkpStatus) {
        return switch (ptkpStatus) {
            case TK_0, TK_1, K_0 -> A;
            case TK_2, TK_3, K_1, K_2 -> B;
            case K_3, K_I_0, K_I_1, K_I_2, K_I_3 -> C;
        };
    }
}
