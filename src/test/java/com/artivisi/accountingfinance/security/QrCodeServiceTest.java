package com.artivisi.accountingfinance.security;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QR code of the TOTP enrolment URI")
class QrCodeServiceTest {

    private static final String URI =
            "otpauth://totp/Balaka:budi?secret=JBSWY3DPEHPK3PXP&issuer=Balaka&algorithm=SHA1&digits=6&period=30";
    private static final int SCALE = 4;

    private final QrCodeService service = new QrCodeService();

    @Test
    @DisplayName("the matrix decodes back to the URI an authenticator app scans")
    void decodesToTheUri() throws Exception {
        BitMatrix matrix = service.matrix(URI);
        int width = matrix.getWidth() * SCALE;
        int height = matrix.getHeight() * SCALE;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x / SCALE, y / SCALE) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(URI);
    }

    @Test
    @DisplayName("the data URI holds an SVG drawn from the same matrix")
    void svgDataUri() {
        String dataUri = service.svgDataUri(URI);

        assertThat(dataUri).startsWith("data:image/svg+xml;base64,");
        String svg = new String(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        int size = service.matrix(URI).getWidth();
        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size + "\"")
                .contains("<path fill=\"#000\" d=\"M");
    }
}
