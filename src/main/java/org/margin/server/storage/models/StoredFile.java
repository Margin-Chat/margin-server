package org.margin.server.storage.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.users.models.User;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stored_files")
@SQLRestriction("deleted_at IS NULL")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private StoredFileScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margin_id")
    private Margin margin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "message_id")
    private Long messageId;

    @NotNull
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @NotNull
    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @NotNull
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @NotNull
    @Column(name = "storage_url", nullable = false, length = 1024)
    private String storageUrl;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "inline", nullable = false)
    private boolean inline = false;
}
