package com.eduadmin.school.repository;

import com.eduadmin.school.model.ClassTeacherRemark;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRemarkRepository extends JpaRepository<ClassTeacherRemark, Long> {
    Optional<ClassTeacherRemark> findByStudent(Student student);
    List<ClassTeacherRemark> findByStudentIn(List<Student> students);
}
