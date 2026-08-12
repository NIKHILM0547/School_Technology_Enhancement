package com.eduadmin.school.repository;

import com.eduadmin.school.model.Review;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findAllByOrderByCreatedAtDesc();
    List<Review> findByStudentOrderByCreatedAtDesc(Student student);
    List<Review> findByStudent_ClassNameOrderByCreatedAtDesc(String className);
    List<Review> findByStudent_ClassNameAndStudent_SectionOrderByCreatedAtDesc(String className, String section);
}
