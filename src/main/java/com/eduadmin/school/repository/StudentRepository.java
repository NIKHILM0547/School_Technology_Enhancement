package com.eduadmin.school.repository;

import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByClassNameOrderByLastNameAsc(String className);
    List<Student> findByClassNameAndSectionOrderByLastNameAsc(String className, String section);
    List<Student> findAllByOrderByLastNameAsc();
    boolean existsByAdmissionNoIgnoreCase(String admissionNo);
    Optional<Student> findByAdmissionNoIgnoreCase(String admissionNo);
    Optional<Student> findByUser(User user);
    @Query("SELECT DISTINCT s.className FROM Student s ORDER BY s.className")
    List<String> findDistinctClassNames();
    @Query("SELECT DISTINCT c FROM ("
            + "SELECT CASE WHEN s.section IS NULL OR s.section = '' THEN s.className "
            + "ELSE CONCAT(s.className, '-', s.section) END AS c FROM Student s) sub ORDER BY c")
    List<String> findDistinctClassDisplay();
    @Query("SELECT s.admissionNo FROM Student s")
    List<String> findAllAdmissionNos();
}
