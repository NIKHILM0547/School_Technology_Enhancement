package com.eduadmin.school.repository;

import com.eduadmin.school.model.NoteFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteFileRepository extends JpaRepository<NoteFile, Long> {
}