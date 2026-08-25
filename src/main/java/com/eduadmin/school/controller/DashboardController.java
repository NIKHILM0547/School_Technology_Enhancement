package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.User;
import com.eduadmin.school.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();

        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentDashboard(model, user, today);
        }
        if (user != null && user.getRole() == Role.teacher) {
            return teacherDashboard(model, user, today);
        }
        return adminDashboard(model, today);
    }

    private String studentDashboard(Model model, User user, LocalDate today) {
        var data = dashboardService.getStudentDashboardData(user, today);
        if (data.student() == null) {
            return "student-dashboard";
        }

        model.addAttribute("today", today);
        model.addAttribute("student", data.student());
        model.addAttribute("myFees", data.myFees());
        model.addAttribute("pendingFees", data.pendingFees());
        model.addAttribute("remaining", data.remaining());
        model.addAttribute("overdueCount", data.overdueCount());
        model.addAttribute("upcomingCount", data.upcomingCount());
        model.addAttribute("fullyPaidCount", data.fullyPaidCount());
        model.addAttribute("myAttendance", data.myAttendance());
        model.addAttribute("attendanceRate", data.attendanceRate());
        model.addAttribute("presentCount", data.presentCount());
        model.addAttribute("absentCount", data.absentCount());
        model.addAttribute("lateCount", data.lateCount());
        model.addAttribute("myReviews", data.myReviews());
        model.addAttribute("recentAnnouncements", data.recentAnnouncements());
        model.addAttribute("isStudent", true);
        model.addAttribute("activePage", "dashboard");
        return "student-dashboard";
    }

    private String adminDashboard(Model model, LocalDate today) {
        var data = dashboardService.getAdminDashboardData(today);

        model.addAttribute("today", today);
        model.addAttribute("totalStudents", data.totalStudents());
        model.addAttribute("totalTeachers", data.totalTeachers());
        model.addAttribute("presentStudents", data.presentStudents());
        model.addAttribute("absentStudents", data.absentStudents());
        model.addAttribute("lateStudents", data.lateStudents());
        model.addAttribute("attendanceRate", data.attendanceRate());
        model.addAttribute("staffPresent", data.staffPresent());
        model.addAttribute("absentToday", data.absentToday());
        model.addAttribute("totalCollected", data.totalCollected());
        model.addAttribute("outstanding", data.outstanding());
        model.addAttribute("overdueCount", data.overdueCount());
        model.addAttribute("pendingFees", data.pendingFees());
        model.addAttribute("recentAnnouncements", data.recentAnnouncements());
        model.addAttribute("activePage", "dashboard");
        return "dashboard";
    }

    private String teacherDashboard(Model model, User user, LocalDate today) {
        var data = dashboardService.getTeacherDashboardData(user, today);

        model.addAttribute("today", today);
        model.addAttribute("teacher", data.teacher());
        model.addAttribute("className", data.className());
        model.addAttribute("totalStudents", data.totalStudents());
        model.addAttribute("attendanceRate", data.attendanceRate());
        model.addAttribute("presentToday", data.presentToday());
        model.addAttribute("absentToday", data.absentToday());
        model.addAttribute("lateToday", data.lateToday());
        model.addAttribute("absentNames", data.absentNames());
        model.addAttribute("markedToday", data.markedToday());
        model.addAttribute("recentAnnouncements", data.recentAnnouncements());
        model.addAttribute("activePage", "dashboard");
        return "teacher-dashboard";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return dashboardService.getUserByEmail(auth.getName());
    }
}