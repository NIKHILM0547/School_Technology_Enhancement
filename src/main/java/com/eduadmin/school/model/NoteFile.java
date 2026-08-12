package com.eduadmin.school.model;

import jakarta.persistence.*;

/**
 * Stores the raw bytes of an uploaded note in the database (LONGBLOB),
 * keeping files out of the project directory. One row per note,
 * keyed 1:1 by the note id.
 */
@Entity
@Table(name = "note_files")
public class NoteFile {

    @Id
    @Column(name = "note_id")
    private Long noteId;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id")
    private Note note;

    public NoteFile() {}

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }
}