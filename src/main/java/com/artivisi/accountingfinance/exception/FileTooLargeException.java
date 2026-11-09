package com.artivisi.accountingfinance.exception;

/**
 * Thrown when an uploaded document exceeds {@code app.storage.documents.max-file-size}.
 *
 * The servlet multipart limit is set higher than the per-document limit so the archive import ZIP fits,
 * so oversized single-document uploads no longer fail with {@code MaxUploadSizeExceededException}. This
 * exception keeps their response at 413. It is not an {@link IllegalArgumentException}, so controllers
 * that render validation messages inline do not turn it into a 200.
 */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
