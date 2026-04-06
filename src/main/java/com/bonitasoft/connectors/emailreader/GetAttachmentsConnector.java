package com.bonitasoft.connectors.emailreader;

import com.bonitasoft.connectors.emailreader.model.AttachmentContent;
import com.bonitasoft.connectors.emailreader.model.SkippedAttachment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * [BETA] Get Attachments connector — downloads all attachments from a specific email by UID.
 */
@Slf4j
public class GetAttachmentsConnector extends AbstractEmailReaderConnector {

    private static final String INPUT_UID = "uid";
    private static final String INPUT_MAX_ATTACHMENT_SIZE_MB = "maxAttachmentSizeMb";

    private static final String OUTPUT_ATTACHMENTS = "attachments";
    private static final String OUTPUT_ATTACHMENT_COUNT = "attachmentCount";
    private static final String OUTPUT_TOTAL_SIZE = "totalSize";
    private static final String OUTPUT_SKIPPED_ATTACHMENTS = "skippedAttachments";

    @Override
    protected void validateOperationParameters(List<String> errors) {
        Long uid = readLongInput(INPUT_UID);
        if (uid == null) {
            errors.add("uid is required");
        }

        Integer maxSizeMb = readIntegerInput(INPUT_MAX_ATTACHMENT_SIZE_MB, 25);
        if (maxSizeMb < 1 || maxSizeMb > 100) {
            errors.add("maxAttachmentSizeMb must be between 1 and 100");
        }
    }

    @Override
    protected void doExecute() throws EmailReaderException {
        Long uid = readLongInput(INPUT_UID);
        String folderName = readStringInput(INPUT_FOLDER_NAME, "INBOX");
        int maxSizeMb = readIntegerInput(INPUT_MAX_ATTACHMENT_SIZE_MB, 25);
        long maxBytes = (long) maxSizeMb * 1024 * 1024;

        try {
            Folder folder = connectionManager.openFolder(folderName, Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;

            Message message = uidFolder.getMessageByUID(uid);
            if (message == null) {
                throw new EmailReaderException(
                        "Message not found: UID " + uid + " in folder " + folderName);
            }

            List<BodyPart> parts = mimeParser.extractAttachmentParts(message);

            List<AttachmentContent> downloaded = new ArrayList<>();
            List<SkippedAttachment> skipped = new ArrayList<>();
            long totalSize = 0;

            for (BodyPart part : parts) {
                String fileName = part.getFileName();
                if (fileName != null) {
                    try {
                        fileName = MimeUtility.decodeText(fileName);
                    } catch (Exception ignored) {
                        // use original
                    }
                }
                if (fileName == null) {
                    fileName = "unnamed";
                }

                long partSize = part.getSize();
                if (partSize > maxBytes) {
                    skipped.add(new SkippedAttachment(
                            fileName, part.getContentType(), partSize,
                            "Exceeds maximum attachment size of " + maxSizeMb + " MB"));
                    log.warn("Skipped attachment '{}' ({} bytes) — exceeds {} MB limit",
                            fileName, partSize, maxSizeMb);
                    continue;
                }

                String base64 = mimeParser.downloadAttachmentBase64(part, maxBytes);
                if (base64 == null) {
                    skipped.add(new SkippedAttachment(
                            fileName, part.getContentType(), partSize,
                            "Actual content exceeds maximum attachment size"));
                    continue;
                }

                downloaded.add(new AttachmentContent(
                        fileName, part.getContentType(), partSize, base64));
                totalSize += partSize;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            setOutputParameter(OUTPUT_ATTACHMENTS, mapper.writeValueAsString(downloaded));
            setOutputParameter(OUTPUT_ATTACHMENT_COUNT, downloaded.size());
            setOutputParameter(OUTPUT_TOTAL_SIZE, totalSize);
            setOutputParameter(OUTPUT_SKIPPED_ATTACHMENTS, mapper.writeValueAsString(skipped));

            log.info("Downloaded {} attachments ({} bytes) from UID={} in folder {}",
                    downloaded.size(), totalSize, uid, folderName);

        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailReaderException("Failed to get attachments: " + e.getMessage(), e);
        }
    }
}
