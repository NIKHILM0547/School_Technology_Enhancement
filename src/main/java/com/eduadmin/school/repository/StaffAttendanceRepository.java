package com.eduadmin.school.repository;

import com.eduadmin.school.model.StaffAttendance;
import com.eduadmin.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {
    List<StaffAttendance> findByDate(LocalDate date);
    Optional<StaffAttendance> findByStaffAndDate(User staff, LocalDate date);
}
