package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final FeeRepository feeRepository;
    private final ReviewRepository reviewRepository;

    public DashboardController(StudentRepository studentRepository,
                               UserRepository userRepository,
                               AttendanceRepository attendanceRepository,
                               StaffAttendanceRepository staffAttendanceRepository,
                               FeeRepository feeRepository,
                               ReviewRepository reviewRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
        this.feeRepository = feeRepository;
        this.reviewRepository = reviewRepository;
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

    /**
     * Student dashboard: only the logged-in student's remaining fees, their
     * attendance, and their own reviews / suggestions.
     */
    private String studentDashboard(Model model, User user, LocalDate today) {
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            return "student-dashboard";
        }

        List<Fee> myFees = feeRepository.findByStudent(student);
        List<Fee> pendingFees = myFees.stream()
                .filter(f -> f.getOutstanding() > 0)
                .sorted(Comparator.comparing(Fee::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        double remaining = myFees.stream().mapToDouble(Fee::getOutstanding).filter(v -> v > 0).sum();
        long overdueCount = myFees.stream()
                .filter(f -> f.getOutstanding() > 0 && f.getDueDate() != null && f.getDueDate().isBefore(today))
                .count();

        List<Attendance> myAttendance = attendanceRepository.findByStudent(student);
        myAttendance.sort(Comparator.comparing(Attendance::getDate).reversed());
        long presentCount = count(myAttendance, AttendanceStatus.present);
        long absentCount = count(myAttendance, AttendanceStatus.absent);
        long lateCount = count(myAttendance, AttendanceStatus.late);
        long excusedCount = count(myAttendance, AttendanceStatus.excused);
        long markedDays = myAttendance.size();
        int attendanceRate = markedDays == 0 ? 0
                : (int) Math.round(100.0 * presentCount / markedDays);

        List<Review> myReviews = reviewRepository.findByStudentOrderByCreatedAtDesc(student);

        model.addAttribute("today", today);
        model.addAttribute("student", student);
        model.addAttribute("myFees", myFees);
        model.addAttribute("pendingFees", pendingFees);
        model.addAttribute("remaining", remaining);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("myAttendance", myAttendance);
        model.addAttribute("attendanceRate", attendanceRate);
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount", absentCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("excusedCount", excusedCount);
        model.addAttribute("myReviews", myReviews);
        model.addAttribute("isStudent", true);
        model.addAttribute("activePage", "dashboard");
        return "student-dashboard";
    }

    private String adminDashboard(Model model, LocalDate today) {
        long totalStudents = studentRepository.count();
        long totalTeachers = userRepository.countByRole(Role.teacher);

        List<Attendance> todayAttendance = attendanceRepository.findByDate(today);
        long presentStudents = count(todayAttendance, AttendanceStatus.present);
        long absentStudents = count(todayAttendance, AttendanceStatus.absent);
        long lateStudents = count(todayAttendance, AttendanceStatus.late);
        long excusedStudents = count(todayAttendance, AttendanceStatus.excused);
        long markedToday = todayAttendance.size();
        int attendanceRate = markedToday == 0 ? 0 : (int) Math.round(100.0 * presentStudents / markedToday);

        List<StaffAttendance> staffToday = staffAttendanceRepository.findByDate(today);
        long staffPresent = staffToday.stream()
                .filter(sa -> sa.getStatus() == AttendanceStatus.present)
                .count();

        List<String> absentToday = new ArrayList<>();
        for (Attendance a : todayAttendance) {
            if (a.getStatus() == AttendanceStatus.absent) absentToday.add(a.getStudent().getFullName());
        }
        for (StaffAttendance sa : staffToday) {
            if (sa.getStatus() == AttendanceStatus.absent) absentToday.add(sa.getStaff().getName() + " (staff)");
        }

        List<Fee> fees = feeRepository.findAllByOrderByDueDateAsc();
        double totalCollected = fees.stream().mapToDouble(Fee::getAmountPaid).sum();
        double outstanding = fees.stream().mapToDouble(Fee::getOutstanding).filter(v -> v > 0).sum();
        long overdueCount = fees.stream()
                .filter(f -> f.getOutstanding() > 0 && f.getDueDate() != null && f.getDueDate().isBefore(today))
                .count();
        List<Fee> pendingFees = fees.stream().filter(f -> f.getOutstanding() > 0).limit(5).toList();

        model.addAttribute("today", today);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalTeachers", totalTeachers);
        model.addAttribute("presentStudents", presentStudents);
        model.addAttribute("absentStudents", absentStudents);
        model.addAttribute("lateStudents", lateStudents);
        model.addAttribute("excusedStudents", excusedStudents);
        model.addAttribute("attendanceRate", attendanceRate);
        model.addAttribute("staffPresent", staffPresent);
        model.addAttribute("absentToday", absentToday);
        model.addAttribute("totalCollected", totalCollected);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("pendingFees", pendingFees);
        model.addAttribute("activePage", "dashboard");
        return "dashboard";
    }

    /**
     * Teacher dashboard: focused on the class they are class teacher of -
     * student count, today's attendance percentage, present/absent today.
     * Fee figures are intentionally not shown to teachers.
     */
    private String teacherDashboard(Model model, User user, LocalDate today) {
        String classTeacherOf = user.getClassTeacherOf();
        String className = "";
        String section = "";
        if (classTeacherOf != null && !classTeacherOf.isBlank()) {
            String[] parts = classTeacherOf.split("-", 2);
            className = parts[0].trim();
            section = parts.length > 1 ? parts[1].trim() : "";
        }

        List<Student> students;
        if (className.isBlank()) {
            students = List.of();
        } else if (section.isBlank()) {
            students = studentRepository.findByClassNameOrderByLastNameAsc(className);
        } else {
            students = studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
        }

        Map<Long, Attendance> todayRecords = new HashMap<>();
        for (Attendance a : attendanceRepository.findByDate(today)) {
            todayRecords.put(a.getStudent().getId(), a);
        }

        long presentToday = 0, absentToday = 0, lateToday = 0, excusedToday = 0;
        List<String> absentNames = new ArrayList<>();
        int markedToday = 0;
        for (Student s : students) {
            Attendance rec = todayRecords.get(s.getId());
            if (rec == null) continue;
            markedToday++;
            switch (rec.getStatus()) {
                case present -> presentToday++;
                case absent -> { absentToday++; absentNames.add(s.getFullName()); }
                case late -> lateToday++;
                case excused -> excusedToday++;
                default -> { }
            }
        }
        int attendanceRate = markedToday == 0 ? 0 : (int) Math.round(100.0 * presentToday / markedToday);

        model.addAttribute("today", today);
        model.addAttribute("teacher", user);
        model.addAttribute("className", classTeacherOf != null && !classTeacherOf.isBlank() ? classTeacherOf : "—");
        model.addAttribute("totalStudents", (long) students.size());
        model.addAttribute("attendanceRate", attendanceRate);
        model.addAttribute("presentToday", presentToday);
        model.addAttribute("absentToday", absentToday);
        model.addAttribute("lateToday", lateToday);
        model.addAttribute("excusedToday", excusedToday);
        model.addAttribute("absentNames", absentNames);
        model.addAttribute("markedToday", markedToday);
        model.addAttribute("activePage", "dashboard");
        return "teacher-dashboard";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private long count(List<Attendance> records, AttendanceStatus status) {
        return records.stream().filter(a -> a.getStatus() == status).count();
    }
}
