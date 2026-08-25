package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.service.LeaveService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/attendance/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

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

        List<LeaveRequest> requests = leaveService.getLeaveRequests(user, filter);

        model.addAttribute("requests", requests);
        model.addAttribute("statusFilter", status);
        model.addAttribute("statuses", LeaveStatus.values());
        model.addAttribute("isAdmin", user.getRole() == Role.admin);
        model.addAttribute("isStudent", user.getRole() == Role.student);
        model.addAttribute("isClassTeacher", user.getRole() == Role.teacher && leaveService.classTeacherOf(user) != null);
        model.addAttribute("currentUser", user);
        model.addAttribute("activePage", "leave");
        return "leave";
    }

    @PostMapping("/apply")
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

        List<String> errors = leaveService.validateLeaveApplication(fromDate, toDate, reason);

        if (!errors.isEmpty()) {
            List<LeaveRequest> requests = leaveService.getLeaveRequestsForUser(user);
            model.addAttribute("requests", requests);
            model.addAttribute("statusFilter", null);
            model.addAttribute("statuses", LeaveStatus.values());
            model.addAttribute("isAdmin", false);
            model.addAttribute("isStudent", user.getRole() == Role.student);
            model.addAttribute("isClassTeacher", user.getRole() == Role.teacher && leaveService.classTeacherOf(user) != null);
            model.addAttribute("currentUser", user);
            model.addAttribute("errorMessages", errors);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("reason", reason != null ? reason.trim() : "");
            model.addAttribute("activePage", "leave");
            return "leave";
        }

        leaveService.applyLeave(user, fromDate, toDate, reason);
        return "redirect:/attendance/leave?saved=true";
    }

    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @RequestParam String action,
                         @RequestParam(required = false) String reviewComment) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/attendance/leave";
        }
        leaveService.reviewLeaveRequest(id, action, reviewComment, user);
        return "redirect:/attendance/leave";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return leaveService.getUserByEmail(auth.getName());
    }
}