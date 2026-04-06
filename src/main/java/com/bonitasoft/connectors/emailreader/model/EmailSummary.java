package com.bonitasoft.connectors.emailreader.model;

import java.util.List;

/**
 * Represents a summary of an email message as returned by read-inbox and search operations.
 */
public record EmailSummary(
        String messageId,
        long uid,
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String receivedDate,
        String bodyPlainText,
        String bodyHtml,
        boolean hasAttachments,
        int attachmentCount,
        long size
) {
}
