package com.bonitasoft.connectors.emailreader;

import com.bonitasoft.connectors.emailreader.model.AttachmentMeta;
import com.bonitasoft.connectors.emailreader.model.EmailSummary;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Parses Jakarta Mail {@link Message} objects into structured data.
 * Handles multipart traversal, charset conversion, and attachment extraction.
 */
@Slf4j
public class MimeMessageParser {

    static final int MAX_BODY_SIZE = 100 * 1024; // 100 KB

    /**
     * Result of parsing a MIME message body.
     */
    public record ParsedContent(String plainText, String html, List<AttachmentMeta> attachments) {
    }

    /**
     * Parses the body content of a message, extracting plain text, HTML, and attachment metadata.
     */
    public ParsedContent parseContent(Message message) throws MessagingException, IOException {
        String plainText = "";
        String html = "";
        List<AttachmentMeta> attachments = new ArrayList<>();

        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            var result = parseMultipart(multipart);
            plainText = result.plainText;
            html = result.html;
            attachments = result.attachments;
        } else if (message.isMimeType("text/plain")) {
            plainText = truncate(content.toString(), MAX_BODY_SIZE);
        } else if (message.isMimeType("text/html")) {
            html = truncate(content.toString(), MAX_BODY_SIZE);
        }

        return new ParsedContent(plainText, html, attachments);
    }

    /**
     * Builds an {@link EmailSummary} from a Jakarta Mail message.
     */
    public EmailSummary toEmailSummary(Message message, long uid) throws MessagingException, IOException {
        ParsedContent content = parseContent(message);

        return new EmailSummary(
                getMessageId(message),
                uid,
                getFirstAddress(message.getFrom()),
                getAddresses(message.getRecipients(Message.RecipientType.TO)),
                getAddresses(message.getRecipients(Message.RecipientType.CC)),
                message.getSubject(),
                toInstantString(message.getReceivedDate()),
                content.plainText(),
                content.html(),
                !content.attachments().isEmpty(),
                content.attachments().size(),
                message.getSize()
        );
    }

    /**
     * Downloads attachment content as Base64-encoded bytes.
     *
     * @param part               the MIME body part
     * @param maxAttachmentBytes maximum size in bytes
     * @return Base64-encoded content, or null if the part exceeds the limit
     */
    public String downloadAttachmentBase64(BodyPart part, long maxAttachmentBytes)
            throws MessagingException, IOException {
        try (InputStream is = part.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > maxAttachmentBytes) {
                    return null; // Exceeds limit
                }
                baos.write(buffer, 0, bytesRead);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Extracts attachment body parts from a message.
     */
    public List<BodyPart> extractAttachmentParts(Message message) throws MessagingException, IOException {
        List<BodyPart> parts = new ArrayList<>();
        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            collectAttachmentParts(multipart, parts);
        }
        return parts;
    }

    // ---- Private helpers ----

    private ParsedContent parseMultipart(Multipart multipart) throws MessagingException, IOException {
        String plainText = "";
        String html = "";
        List<AttachmentMeta> attachments = new ArrayList<>();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();

            if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || Part.INLINE.equalsIgnoreCase(disposition)) {
                String fileName = part.getFileName();
                if (fileName != null) {
                    try {
                        fileName = MimeUtility.decodeText(fileName);
                    } catch (Exception ignored) {
                        // Use original filename
                    }
                }
                if (Part.INLINE.equalsIgnoreCase(disposition) && fileName != null) {
                    fileName = fileName + " (inline)";
                }
                attachments.add(new AttachmentMeta(
                        fileName != null ? fileName : "unnamed",
                        part.getContentType(),
                        part.getSize()
                ));
            } else if (part.getContent() instanceof Multipart nested) {
                var nestedResult = parseMultipart(nested);
                if (plainText.isEmpty() && !nestedResult.plainText.isEmpty()) {
                    plainText = nestedResult.plainText;
                }
                if (html.isEmpty() && !nestedResult.html.isEmpty()) {
                    html = nestedResult.html;
                }
                attachments.addAll(nestedResult.attachments);
            } else if (part.isMimeType("text/plain") && plainText.isEmpty()) {
                Object content = part.getContent();
                plainText = truncate(content != null ? content.toString() : "", MAX_BODY_SIZE);
            } else if (part.isMimeType("text/html") && html.isEmpty()) {
                Object content = part.getContent();
                html = truncate(content != null ? content.toString() : "", MAX_BODY_SIZE);
            }
        }

        return new ParsedContent(plainText, html, attachments);
    }

    private void collectAttachmentParts(Multipart multipart, List<BodyPart> parts)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || Part.INLINE.equalsIgnoreCase(disposition)) {
                parts.add(part);
            } else if (part.getContent() instanceof Multipart nested) {
                collectAttachmentParts(nested, parts);
            }
        }
    }

    static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        log.warn("Body truncated from {} to {} characters", text.length(), maxLength);
        return text.substring(0, maxLength);
    }

    private String getMessageId(Message message) throws MessagingException {
        String[] headers = message.getHeader("Message-ID");
        return (headers != null && headers.length > 0) ? headers[0] : "";
    }

    private String getFirstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        if (addresses[0] instanceof InternetAddress ia) {
            return ia.getAddress();
        }
        return addresses[0].toString();
    }

    private List<String> getAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Address addr : addresses) {
            if (addr instanceof InternetAddress ia) {
                result.add(ia.getAddress());
            } else {
                result.add(addr.toString());
            }
        }
        return result;
    }

    private String toInstantString(Date date) {
        if (date == null) return "";
        return date.toInstant().toString();
    }

    /**
     * Extracts display name from the first address in the array.
     */
    public String getFromName(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        if (addresses[0] instanceof InternetAddress ia) {
            return ia.getPersonal() != null ? ia.getPersonal() : "";
        }
        return "";
    }

    /**
     * Gets the Reply-To address from a message.
     */
    public String getReplyTo(Message message) throws MessagingException {
        Address[] replyTo = message.getReplyTo();
        return getFirstAddress(replyTo);
    }

    /**
     * Converts all headers to a simple key-value JSON string.
     */
    public String headersToJson(Message message) throws MessagingException {
        var headers = message.getAllHeaders();
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        while (headers.hasMoreElements()) {
            var header = headers.nextElement();
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(header.getName())).append("\":\"")
                    .append(escapeJson(header.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
