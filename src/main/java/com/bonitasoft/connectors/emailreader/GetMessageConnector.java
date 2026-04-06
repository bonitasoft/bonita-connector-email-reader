package com.bonitasoft.connectors.emailreader;

import com.bonitasoft.connectors.emailreader.model.AttachmentMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.HeaderTerm;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

/**
 * [BETA] Get Message connector — retrieves full content of a specific email by message ID or UID.
 */
@Slf4j
public class GetMessageConnector extends AbstractEmailReaderConnector {

    private static final String INPUT_MESSAGE_ID = "messageId";
    private static final String INPUT_UID = "uid";

    private static final String OUTPUT_MESSAGE_ID = "messageIdOutput";
    private static final String OUTPUT_UID = "uidOutput";
    private static final String OUTPUT_FROM = "from";
    private static final String OUTPUT_FROM_NAME = "fromName";
    private static final String OUTPUT_TO = "to";
    private static final String OUTPUT_CC = "cc";
    private static final String OUTPUT_REPLY_TO = "replyTo";
    private static final String OUTPUT_SUBJECT = "subject";
    private static final String OUTPUT_RECEIVED_DATE = "receivedDate";
    private static final String OUTPUT_SENT_DATE = "sentDate";
    private static final String OUTPUT_BODY_PLAIN_TEXT = "bodyPlainText";
    private static final String OUTPUT_BODY_HTML = "bodyHtml";
    private static final String OUTPUT_HEADERS = "headers";
    private static final String OUTPUT_HAS_ATTACHMENTS = "hasAttachments";
    private static final String OUTPUT_ATTACHMENTS = "attachments";
    private static final String OUTPUT_IS_READ = "isRead";

    @Override
    protected void validateOperationParameters(List<String> errors) {
        String messageId = readStringInput(INPUT_MESSAGE_ID);
        Long uid = readLongInput(INPUT_UID);

        if ((messageId == null || messageId.isBlank()) && uid == null) {
            errors.add("Either messageId or uid must be provided");
        }
    }

    @Override
    protected void doExecute() throws EmailReaderException {
        String messageId = readStringInput(INPUT_MESSAGE_ID);
        Long uid = readLongInput(INPUT_UID);
        String folderName = readStringInput(INPUT_FOLDER_NAME, "INBOX");

        try {
            Folder folder = connectionManager.openFolder(folderName, Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;

            Message message = null;
            long resolvedUid;

            if (uid != null) {
                message = uidFolder.getMessageByUID(uid);
                resolvedUid = uid;
            } else {
                // Search by Message-ID header
                Message[] found = folder.search(new HeaderTerm("Message-ID", messageId));
                if (found.length > 0) {
                    message = found[0];
                    resolvedUid = uidFolder.getUID(message);
                } else {
                    throw new EmailReaderException(
                            "Message not found in folder " + folderName);
                }
            }

            if (message == null) {
                throw new EmailReaderException(
                        "Message not found: UID " + uid + " in folder " + folderName);
            }

            MimeMessageParser.ParsedContent content = mimeParser.parseContent(message);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            resolvedUid = uidFolder.getUID(message);
            String[] msgIdHeaders = message.getHeader("Message-ID");
            String resolvedMessageId = (msgIdHeaders != null && msgIdHeaders.length > 0)
                    ? msgIdHeaders[0] : "";

            setOutputParameter(OUTPUT_MESSAGE_ID, resolvedMessageId);
            setOutputParameter(OUTPUT_UID, resolvedUid);
            setOutputParameter(OUTPUT_FROM,
                    message.getFrom() != null && message.getFrom().length > 0
                            ? ((jakarta.mail.internet.InternetAddress) message.getFrom()[0]).getAddress() : "");
            setOutputParameter(OUTPUT_FROM_NAME, mimeParser.getFromName(message.getFrom()));
            setOutputParameter(OUTPUT_TO,
                    mapper.writeValueAsString(mimeParser.toEmailSummary(message, resolvedUid).to()));
            setOutputParameter(OUTPUT_CC,
                    mapper.writeValueAsString(mimeParser.toEmailSummary(message, resolvedUid).cc()));
            setOutputParameter(OUTPUT_REPLY_TO, mimeParser.getReplyTo(message));
            setOutputParameter(OUTPUT_SUBJECT, message.getSubject());
            setOutputParameter(OUTPUT_RECEIVED_DATE, toIsoString(message.getReceivedDate()));
            setOutputParameter(OUTPUT_SENT_DATE, toIsoString(message.getSentDate()));
            setOutputParameter(OUTPUT_BODY_PLAIN_TEXT, content.plainText());
            setOutputParameter(OUTPUT_BODY_HTML, content.html());
            setOutputParameter(OUTPUT_HEADERS, mimeParser.headersToJson(message));
            setOutputParameter(OUTPUT_HAS_ATTACHMENTS, !content.attachments().isEmpty());
            setOutputParameter(OUTPUT_ATTACHMENTS, mapper.writeValueAsString(content.attachments()));
            setOutputParameter(OUTPUT_IS_READ, message.isSet(Flags.Flag.SEEN));

            log.info("Retrieved message UID={} from folder {}", resolvedUid, folderName);

        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailReaderException("Failed to get message: " + e.getMessage(), e);
        }
    }

    private String toIsoString(Date date) {
        if (date == null) return "";
        return date.toInstant().toString();
    }
}
