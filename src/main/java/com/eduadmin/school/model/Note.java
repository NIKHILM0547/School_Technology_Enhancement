package com.eduadmin.school.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notes")
public class Note {

    public enum FileKind { video, image, pdf }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    private String description;

    /** Original file name as uploaded (for display). */
    @Column(nullable = false)
    private String originalFileName;

    /** Unique name stored on disk (never exposed). */
    @Column(nullable = false, unique = true)
    private String storedFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileKind fileKind;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @ManyToOne
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    /** Classes this note is visible to, e.g. "6-A", "7-B". */
    @ElementCollection
    @CollectionTable(name = "note_target_classes", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "class_name")
    private Set<String> targetClasses = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Note() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

    public FileKind getFileKind() { return fileKind; }
    public void setFileKind(FileKind fileKind) { this.fileKind = fileKind; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }

    public Set<String> getTargetClasses() { return targetClasses; }
    public void setTargetClasses(Set<String> targetClasses) { this.targetClasses = targetClasses; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Human-readable list of target classes, e.g. "6-A, 7-B". */
    public String getTargetClassesDisplay() {
        return String.join(", ", targetClasses);
    }

    /** Icon label for the kind of file. */
    public String getKindLabel() {
        return fileKind == null ? "file" : fileKind.name();
    }
}
