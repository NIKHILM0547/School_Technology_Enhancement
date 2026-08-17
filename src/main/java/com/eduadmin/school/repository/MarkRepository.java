package com.eduadmin.school.repository;

import com.eduadmin.school.model.Mark;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarkRepository extends JpaRepository<Mark, Long> {

    Optional<Mark> findByStudentAndSubjectAndTerm(Student student, String subject, String term);

    @Query("SELECT m FROM Mark m WHERE m.student = :student AND m.term = :term ORDER BY m.subject")
    List<Mark> findByStudentAndTermOrderBySubject(@Param("student") Student student, @Param("term") String term);

    @Query("SELECT m FROM Mark m WHERE m.student = :student ORDER BY m.term, m.subject")
    List<Mark> findByStudentOrderByTermSubject(@Param("student") Student student);

    @Query("SELECT m FROM Mark m WHERE m.term = :term ORDER BY m.student.lastName, m.subject")
    List<Mark> findByTermOrderByStudentLastNameAscSubjectAsc(@Param("term") String term);

    @Query("SELECT m FROM Mark m WHERE m.student.id IN :studentIds AND m.term = :term "
            + "ORDER BY m.student.lastName, m.subject")
    List<Mark> findByStudentIdsAndTerm(@Param("studentIds") Collection<Long> studentIds,
                                       @Param("term") String term);

    @Query("SELECT DISTINCT m.term FROM Mark m ORDER BY m.term")
    List<String> findDistinctTerms();

    @Query("SELECT DISTINCT m.subject FROM Mark m ORDER BY m.subject")
    List<String> findDistinctSubjects();
}
