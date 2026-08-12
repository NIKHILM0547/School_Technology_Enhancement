package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.LeaveRequestRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/attendance/leave")
public class LeaveController {

    private final LeaveRequestRepository leaveRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public LeaveController(LeaveRequestRepository leaveRepository,
                           StudentRepository studentRepository,
                           UserRepository userRepository) {
        this.leaveRepository = leaveRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /** Lists leave requests: students/teachers see their own, admins see all. */
    @GetMapping
    public String list(@RequestParam(required = false) String status, Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }

        LeaveStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = LeaveStatus.valueOf(status);
            } catch (IllegalArgumentException ignored) {
                status = null;
            }
        }

        List<LeaveRequest> requests = new ArrayList<>();
        if (user.getRole() == Role.admin) {
            requests = filter != null
                    ? leaveRepository.findByStatusOrderByAppliedAtDesc(filter)
                    : leaveRepository.findAllByOrderByAppliedAtDesc();
        } else if (user.getRole() == Role.student) {
            Student me = studentRepository.findByUser(user).orElse(null);
            requests = me != null ? leaveRepository.findByStudentOrderByAppliedAtDesc(me) : List.of();
        } else {
            requests = leaveRepository.findByApplicantOrderByAppliedAtDesc(user);
        }

        model.addAttribute("requests", requests);
        model.addAttribute("statusFilter", status);
        model.addAttribute("statuses", LeaveStatus.values());
        model.addAttribute("isAdmin", user.getRole() == Role.admin);
        model.addAttribute("isStudent", user.getRole() == Role.student);
        model.addAttribute("currentUser", user);
        model.addAttribute("activePage", "leave");
        return "leave";
    }

    /** Applies for leave. Students apply for their linked student record, teachers for themselves. */
    @PostMapping("/apply")
    @Transactional
    public String apply(@RequestParam String fromDate,
                        @RequestParam String toDate,
                        @RequestParam String reason,
                        Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/login";
        }
        if (user.getRole() == Role.admin) {
            return "redirect:/attendance/leave";
        }

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

        if (!errors.isEmpty()) {
            List<LeaveRequest> requests = listFor(user);
            model.addAttribute("requests", requests);
            model.addAttribute("statusFilter", null);
            model.addAttribute("statuses", LeaveStatus.values());
            model.addAttribute("isAdmin", false);
            model.addAttribute("isStudent", user.getRole() == Role.student);
            model.addAttribute("currentUser", user);
            model.addAttribute("errorMessages", errors);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("reason", cleanReason);
            model.addAttribute("activePage", "leave");
            return "leave";
        }

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
        return "redirect:/attendance/leave?saved=true";
    }

    /** Approves or rejects a pending request. Admin only. */
    @PostMapping("/{id}/review")
    @Transactional
    public String review(@PathVariable Long id,
                         @RequestParam String action,
                         @RequestParam(required = false) String reviewComment) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/attendance/leave";
        }
        LeaveRequest request = leaveRepository.findById(id).orElse(null);
        if (request == null || request.getStatus() != LeaveStatus.pending) {
            return "redirect:/attendance/leave";
        }
        if ("approve".equals(action)) {
            request.setStatus(LeaveStatus.approved);
        } else if ("reject".equals(action)) {
            request.setStatus(LeaveStatus.rejected);
        } else {
            return "redirect:/attendance/leave";
        }
        request.setReviewComment(reviewComment != null ? reviewComment.trim() : "");
        request.setReviewedBy(user);
        request.setReviewedAt(LocalDateTime.now());
        leaveRepository.save(request);
        return "redirect:/attendance/leave";
    }

    private List<LeaveRequest> listFor(User user) {
        if (user.getRole() == Role.student) {
            Student me = studentRepository.findByUser(user).orElse(null);
            return me != null ? leaveRepository.findByStudentOrderByAppliedAtDesc(me) : List.of();
        }
        return leaveRepository.findByApplicantOrderByAppliedAtDesc(user);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
