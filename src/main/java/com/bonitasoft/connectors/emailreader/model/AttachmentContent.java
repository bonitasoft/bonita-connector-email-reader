package com.bonitasoft.connectors.emailreader.model;

/**
 * Full attachment with Base64-encoded content.
 */
public record AttachmentContent(
        String fileName,
        String contentType,
        long size,
        String contentBase64
) {
}
