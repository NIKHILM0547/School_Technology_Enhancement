package com.eduadmin.school.repository;

import com.eduadmin.school.model.Attendance;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByDate(LocalDate date);
    List<Attendance> findByStudent(Student student);
    Optional<Attendance> findByStudentAndDate(Student student, LocalDate date);
}
