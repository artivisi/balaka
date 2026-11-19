package com.artivisi.accountingfinance.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Renders a QR code as an SVG data URI for an {@code <img>} element. The image is built on the
 * server, so the secret it carries never reaches a third-party service, and the CSP's
 * {@code img-src data:} already admits it.
 */
@Service
public class QrCodeService {

    /** Modules of white border on each side, as the QR specification requires. */
    private static final int QUIET_ZONE = 4;

    public String svgDataUri(String content) {
        String svg = svg(matrix(content));
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    BitMatrix matrix(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, QUIET_ZONE,
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
        } catch (WriterException e) {
            throw new IllegalStateException("QR code could not be encoded", e);
        }
    }

    private static String svg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }
}
