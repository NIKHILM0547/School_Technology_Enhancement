package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.service.AttendanceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String date,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String type,
                       Model model) {
        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentAttendance(model, user);
        }
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        LocalDate selectedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();

        List<AttendanceService.AttendanceRow> rows = attendanceService.getAttendanceRowsWithUnmarked(
                selectedDate, type, classFilter, name, status, user, isTeacher);

        model.addAttribute("rows", rows);
        model.addAttribute("classes", attendanceService.getClassNamesForFilter(user, isTeacher));
        model.addAttribute("statusNames", attendanceService.getStatusNames());
        model.addAttribute("selectedDate", selectedDate.toString());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedClass", classFilter != null && !classFilter.isBlank() ? classFilter.trim() : "");
        model.addAttribute("selectedType", type != null && !type.isBlank() ? type.trim() : "");
        model.addAttribute("name", name != null && !name.isBlank() ? name.trim() : "");
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("activePage", "attendance");
        return "attendance";
    }

    @GetMapping("/rollcall")
    public String rollCall(@RequestParam(required = false) String date, Model model) {
        LocalDate selected = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        List<Student> students = attendanceService.getStudentsForRollCall(user, isTeacher);

        Map<Long, String> currentMarks = attendanceService.getCurrentStudentMarks(students, selected);

        model.addAttribute("students", students);
        model.addAttribute("selectedDate", selected.toString());
        model.addAttribute("currentMarks", currentMarks);
        model.addAttribute("statuses", attendanceService.getStatusNames());
        model.addAttribute("activePage", "attendance");
        return "attendance-rollcall";
    }

    @GetMapping("/rollcall-staff")
    public String staffRollCall(@RequestParam(required = false) String date, Model model) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/attendance";
        }
        LocalDate selected = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<User> staff = attendanceService.getAllStaff();
        List<Student> students = attendanceService.getAllStudents();

        Map<Long, String> staffMarks = attendanceService.getCurrentStaffMarks(staff, selected);
        Map<Long, String> studentMarks = attendanceService.getCurrentStudentMarks(students, selected);

        model.addAttribute("staff", staff);
        model.addAttribute("students", students);
        model.addAttribute("selectedDate", selected.toString());
        model.addAttribute("staffMarks", staffMarks);
        model.addAttribute("studentMarks", studentMarks);
        model.addAttribute("statuses", attendanceService.getStatusNames());
        model.addAttribute("activePage", "attendance");
        return "attendance-staff";
    }

    @PostMapping("/save")
    public String save(@RequestParam String date,
                       @RequestParam List<Long> studentIds,
                       @RequestParam List<String> statuses) {
        LocalDate parsedDate = LocalDate.parse(date);
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        attendanceService.saveStudentAttendance(parsedDate, studentIds, statuses, user, isTeacher);
        return "redirect:/attendance/rollcall?date=" + date + "&saved=true";
    }

    @PostMapping("/save-staff")
    public String saveStaff(@RequestParam String date,
                            @RequestParam(required = false) List<Long> staffIds,
                            @RequestParam(required = false) List<String> staffStatuses,
                            @RequestParam(required = false) List<Long> studentIds,
                            @RequestParam(required = false) List<String> studentStatuses) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/attendance";
        }
        LocalDate parsedDate = LocalDate.parse(date);
        attendanceService.saveStaffAttendance(parsedDate, staffIds, staffStatuses, studentIds, studentStatuses);
        return "redirect:/attendance/rollcall-staff?date=" + date + "&saved=true";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String type,
                       @RequestParam String status,
                       @RequestParam(required = false) String remarks) {
        AttendanceStatus newStatus;
        try {
            newStatus = AttendanceStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return "redirect:/attendance";
        }
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        if (!attendanceService.canEditAttendance(user, isTeacher, type, id)) {
            return "redirect:/attendance";
        }
        attendanceService.editAttendance(id, type, newStatus, remarks);
        return "redirect:/attendance";
    }

    private String studentAttendance(Model model, User user) {
        Student student = attendanceService.getStudentAttendance(user);
        if (student == null) {
            model.addAttribute("activePage", "attendance");
            return "my-attendance";
        }
        List<Attendance> records = attendanceService.getStudentAttendance(student);

        long present = records.stream().filter(a -> a.getStatus() == AttendanceStatus.present).count();
        long absent = records.stream().filter(a -> a.getStatus() == AttendanceStatus.absent).count();
        long late = records.stream().filter(a -> a.getStatus() == AttendanceStatus.late).count();
        int rate = records.isEmpty() ? 0 : (int) Math.round(100.0 * present / records.size());

        model.addAttribute("student", student);
        model.addAttribute("records", records);
        model.addAttribute("presentCount", present);
        model.addAttribute("absentCount", absent);
        model.addAttribute("lateCount", late);
        model.addAttribute("attendanceRate", rate);
        model.addAttribute("activePage", "attendance");
        return "my-attendance";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return attendanceService.getUserByEmail(auth.getName());
    }
}