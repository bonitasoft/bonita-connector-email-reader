package com.bonitasoft.connectors.emailreader;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * [BETA] Mark as Read connector — marks one or more emails as read (SEEN flag) by UIDs.
 */
@Slf4j
public class MarkAsReadConnector extends AbstractEmailReaderConnector {

    private static final String INPUT_UIDS = "uids";

    private static final String OUTPUT_MARKED_COUNT = "markedCount";
    private static final String OUTPUT_FAILED_UIDS = "failedUids";

    @Override
    protected void validateOperationParameters(List<String> errors) {
        String uids = readStringInput(INPUT_UIDS);
        if (uids == null || uids.isBlank()) {
            errors.add("uids is required");
        } else {
            String[] parts = uids.split(",");
            if (parts.length > 100) {
                errors.add("Maximum 100 UIDs per call");
            }
            for (String part : parts) {
                try {
                    Long.parseLong(part.trim());
                } catch (NumberFormatException e) {
                    errors.add("Invalid UID value: " + part.trim());
                }
            }
        }
    }

    @Override
    protected void doExecute() throws EmailReaderException {
        String uidsStr = readStringInput(INPUT_UIDS);
        String folderName = readStringInput(INPUT_FOLDER_NAME, "INBOX");

        try {
            Folder folder = connectionManager.openFolder(folderName, Folder.READ_WRITE);
            UIDFolder uidFolder = (UIDFolder) folder;

            String[] uidParts = uidsStr.split(",");
            int markedCount = 0;
            List<String> failedUids = new ArrayList<>();

            for (String uidStr : uidParts) {
                long uid = Long.parseLong(uidStr.trim());
                try {
                    Message message = uidFolder.getMessageByUID(uid);
                    if (message != null) {
                        message.setFlag(Flags.Flag.SEEN, true);
                        markedCount++;
                    } else {
                        failedUids.add(String.valueOf(uid));
                        log.warn("Message not found for UID {} in folder {}", uid, folderName);
                    }
                } catch (Exception e) {
                    failedUids.add(String.valueOf(uid));
                    log.warn("Failed to mark UID {} as read: {}", uid, e.getMessage());
                }
            }

            setOutputParameter(OUTPUT_MARKED_COUNT, markedCount);
            setOutputParameter(OUTPUT_FAILED_UIDS, String.join(",", failedUids));

            if (!failedUids.isEmpty()) {
                setOutputParameter(OUTPUT_SUCCESS, false);
                setOutputParameter(OUTPUT_ERROR_MESSAGE,
                        "Failed to mark " + failedUids.size() + " UIDs: " + String.join(",", failedUids));
            }

            log.info("Marked {} messages as read in folder {} ({} failed)",
                    markedCount, folderName, failedUids.size());

        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailReaderException("Failed to mark messages as read: " + e.getMessage(), e);
        }
    }
}
