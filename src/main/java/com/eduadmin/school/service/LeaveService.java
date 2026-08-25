package com.eduadmin.school.service;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public LeaveService(LeaveRequestRepository leaveRepository,
                        StudentRepository studentRepository,
                        UserRepository userRepository) {
        this.leaveRepository = leaveRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    public List<LeaveRequest> getLeaveRequests(User user, LeaveStatus statusFilter) {
        List<LeaveRequest> requests;
        if (user.getRole() == Role.admin) {
            requests = statusFilter != null
                    ? leaveRepository.findByStatusOrderByAppliedAtDesc(statusFilter)
                    : leaveRepository.findAllByOrderByAppliedAtDesc();
        } else if (user.getRole() == Role.student) {
            Student me = studentRepository.findByUser(user).orElse(null);
            requests = me != null ? leaveRepository.findByStudentOrderByAppliedAtDesc(me) : List.of();
        } else {
            List<LeaveRequest> own = leaveRepository.findByApplicantOrderByAppliedAtDesc(user);
            String classTeacherOf = classTeacherOf(user);
            List<LeaveRequest> classStudents = classTeacherOf != null
                    ? leaveRepository.findByStudentInOrderByAppliedAtDesc(studentsOfClass(classTeacherOf))
                    : List.of();
            requests = new ArrayList<>();
            requests.addAll(classStudents);
            requests.addAll(own);
            requests.sort(Comparator.comparing(LeaveRequest::getAppliedAt, Comparator.reverseOrder()));
        }

        if (statusFilter != null) {
            requests = requests.stream().filter(r -> r.getStatus() == statusFilter).collect(Collectors.toList());
        }
        return requests;
    }

    @Transactional
    public List<String> validateLeaveApplication(String fromDate, String toDate, String reason) {
        List<String> errors = new ArrayList<>();
        LocalDate from = null;
        LocalDate to = null;
        try {
            from = LocalDate.parse(fromDate);
        } catch (Exception e) {
            errors.add("Please choose a valid from date.");
        }
        try {
            to = LocalDate.parse(toDate);
        } catch (Exception e) {
            errors.add("Please choose a valid to date.");
        }
        if (from != null && to != null && to.isBefore(from)) {
            errors.add("The to date must be on or after the from date.");
        }
        String cleanReason = reason != null ? reason.trim() : "";
        if (cleanReason.isEmpty()) {
            errors.add("Please provide a reason for the leave.");
        }
        return errors;
    }

    @Transactional
    public void applyLeave(User user, String fromDate, String toDate, String reason) {
        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);
        String cleanReason = reason.trim();

        LeaveRequest request = new LeaveRequest();
        request.setApplicant(user);
        if (user.getRole() == Role.student) {
            studentRepository.findByUser(user).ifPresent(request::setStudent);
        }
        request.setFromDate(from);
        request.setToDate(to);
        request.setReason(cleanReason);
        request.setStatus(LeaveStatus.pending);
        request.setAppliedAt(LocalDateTime.now());
        leaveRepository.save(request);
    }

    @Transactional
    public void reviewLeaveRequest(Long id, String action, String reviewComment, User user) {
        LeaveRequest request = leaveRepository.findById(id).orElse(null);
        if (request == null || request.getStatus() != LeaveStatus.pending) {
            return;
        }
        boolean isAdmin = user.getRole() == Role.admin;
        boolean isClassTeacher = user.getRole() == Role.teacher && classTeacherOf(user) != null
                && request.getStudent() != null
                && request.getStudent().getClassDisplay().equals(classTeacherOf(user));
        if (!isAdmin && !isClassTeacher) {
            return;
        }
        if ("approve".equals(action)) {
            request.setStatus(LeaveStatus.approved);
        } else if ("reject".equals(action)) {
            request.setStatus(LeaveStatus.rejected);
        } else {
            return;
        }
        request.setReviewComment(reviewComment != null ? reviewComment.trim() : "");
        request.setReviewedBy(user);
        request.setReviewedAt(LocalDateTime.now());
        leaveRepository.save(request);
    }

    public String classTeacherOf(User user) {
        if (user == null || user.getRole() != Role.teacher) return null;
        String cls = user.getClassTeacherOf();
        return (cls != null && !cls.isBlank()) ? cls.trim() : null;
    }

    public List<Student> studentsOfClass(String cls) {
        String[] parts = cls.split("-", 2);
        String className = parts[0].trim();
        String section = parts.length > 1 ? parts[1].trim() : "";
        return section.isBlank()
                ? studentRepository.findByClassNameOrderByLastNameAsc(className)
                : studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
    }

    public List<LeaveRequest> getLeaveRequestsForUser(User user) {
        if (user.getRole() == Role.student) {
            Student me = studentRepository.findByUser(user).orElse(null);
            return me != null ? leaveRepository.findByStudentOrderByAppliedAtDesc(me) : List.of();
        }
        return leaveRepository.findByApplicantOrderByAppliedAtDesc(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}