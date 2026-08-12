package com.eduadmin.school.repository;

import com.eduadmin.school.model.Note;
import com.eduadmin.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findAllByOrderByCreatedAtDesc();
    List<Note> findByUploadedByOrderByCreatedAtDesc(User uploadedBy);

    /** Notes visible to a specific class (e.g. "6-A"). */
    @Query("SELECT DISTINCT n FROM Note n JOIN n.targetClasses t "
            + "WHERE t = :className ORDER BY n.createdAt DESC")
    List<Note> findByTargetClass(@Param("className") String className);
}
