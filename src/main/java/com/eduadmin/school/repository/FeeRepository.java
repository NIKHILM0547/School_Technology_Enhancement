package com.eduadmin.school.repository;

import com.eduadmin.school.model.Fee;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    List<Fee> findAllByOrderByDueDateAsc();
    List<Fee> findByStatus(Fee.Status status);
    List<Fee> findByStudent(Student student);
    List<Fee> findByStudent_Id(Long studentId);
    List<Fee> findByStudent_ClassName(String className);
    List<Fee> findByStudent_IdAndStudent_ClassName(Long studentId, String className);

    /** Filters by optional studentId/classFilter and searches by student name. */
    @Query("SELECT f FROM Fee f WHERE "
            + "(:studentId = 0 OR f.student.id = :studentId) "
            + "AND (:classFilter = '' OR ((f.student.section IS NULL OR f.student.section = '') "
            + "     AND f.student.className = :classFilter) "
            + "     OR CONCAT(f.student.className, '-', f.student.section) = :classFilter) "
            + "AND (:name = '' OR LOWER(f.student.firstName) LIKE LOWER(CONCAT('%', :name, '%')) "
            + "     OR LOWER(f.student.lastName) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "ORDER BY f.dueDate")
    List<Fee> search(@Param("studentId") Long studentId,
                     @Param("classFilter") String classFilter,
                     @Param("name") String name);
}
