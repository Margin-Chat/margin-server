package org.margin.server.unittest.utils;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.users.models.User;

public class StoredFileTestUtils {

    private static StoredFile base(Long id, StoredFileScope scope, Margin margin, User uploadedBy) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setScope(scope);
        file.setMargin(margin);
        file.setUploadedBy(uploadedBy);
        file.setContentType("text/plain");
        file.setSizeBytes(10L);
        return file;
    }

    public static StoredFile marginFile(Long id, String fileName, Margin margin, User uploadedBy) {
        StoredFile file = base(id, StoredFileScope.MARGIN, margin, uploadedBy);
        file.setFileName(fileName);
        file.setStorageUrl("/api/files/stored-files/" + id + "_" + fileName);
        return file;
    }

    public static StoredFile channelFile(Long id, Margin margin, Channel channel, User uploadedBy) {
        StoredFile file = base(id, StoredFileScope.CHANNEL, margin, uploadedBy);
        file.setChannel(channel);
        file.setFileName("file.txt");
        file.setStorageUrl("/api/files/stored-files/uuid_file.txt");
        return file;
    }

    public static StoredFile conversationFile(Long id, String fileName, Conversation conversation, User uploadedBy) {
        StoredFile file = base(id, StoredFileScope.CONVERSATION, null, uploadedBy);
        file.setConversation(conversation);
        file.setFileName(fileName);
        file.setStorageUrl("/api/files/stored-files/" + id + "_" + fileName);
        return file;
    }
}
