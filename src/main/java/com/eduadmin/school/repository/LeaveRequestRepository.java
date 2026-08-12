package com.eduadmin.school.repository;

import com.eduadmin.school.model.LeaveRequest;
import com.eduadmin.school.model.LeaveStatus;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findAllByOrderByAppliedAtDesc();
    List<LeaveRequest> findByApplicantOrderByAppliedAtDesc(User applicant);
    List<LeaveRequest> findByStudentOrderByAppliedAtDesc(Student student);
    List<LeaveRequest> findByStatusOrderByAppliedAtDesc(LeaveStatus status);
}
