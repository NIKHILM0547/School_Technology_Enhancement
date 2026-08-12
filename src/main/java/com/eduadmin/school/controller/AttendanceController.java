package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.AttendanceRepository;
import com.eduadmin.school.repository.StaffAttendanceRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private static final List<String> STATUS_NAMES = Arrays.stream(AttendanceStatus.values())
            .map(AttendanceStatus::name)
            .collect(Collectors.toList());

    public record AttendanceRow(Long id, String type, String name, String className,
                                LocalDate date, AttendanceStatus status, String remarks) {}

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;

    public AttendanceController(StudentRepository studentRepository,
                                AttendanceRepository attendanceRepository,
                                UserRepository userRepository,
                                StaffAttendanceRepository staffAttendanceRepository) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String date,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       Model model) {
        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentAttendance(model, user);
        }
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        LocalDate selectedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();

        AttendanceStatus selectedStatus = null;
        boolean anyStatus = true;
        if (status != null && !status.isBlank()) {
            try {
                selectedStatus = AttendanceStatus.valueOf(status);
                anyStatus = false;
            } catch (IllegalArgumentException ignored) {
                status = null;
            }
        }

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();

        // Teachers see only their own class-teacher class; admins see everyone.
        List<Student> visibleStudents = isTeacher
                ? studentsOfClass(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        Map<Long, Attendance> studentRecords = new HashMap<>();
        for (Attendance a : attendanceRepository.findByDate(selectedDate)) {
            studentRecords.put(a.getStudent().getId(), a);
        }
        Map<Long, StaffAttendance> staffRecords = new HashMap<>();
        for (StaffAttendance sa : staffAttendanceRepository.findByDate(selectedDate)) {
            staffRecords.put(sa.getStaff().getId(), sa);
        }

        List<AttendanceRow> rows = new ArrayList<>();
        boolean classFiltered = !classQuery.isEmpty();
        for (Student s : visibleStudents) {
            if (classFiltered && !classQuery.equals(s.getClassDisplay())) continue;
            if (!nameQuery.isEmpty() && !s.getFullName().toLowerCase().contains(nameQuery)) continue;
            Attendance rec = studentRecords.get(s.getId());
            AttendanceStatus st = (rec != null) ? rec.getStatus() : AttendanceStatus.present;
            if (!anyStatus && st != selectedStatus) continue;
            rows.add(new AttendanceRow(s.getId(), "student", s.getFullName(), s.getClassDisplay(),
                    selectedDate, st, rec != null ? rec.getRemarks() : null));
        }
        // Staff rows are only shown to admins, and only when not class-filtered.
        if (!isTeacher && !classFiltered) {
            for (User u : userRepository.findByRole(Role.teacher)) {
                if (!nameQuery.isEmpty() && !u.getName().toLowerCase().contains(nameQuery)) continue;
                StaffAttendance rec = staffRecords.get(u.getId());
                AttendanceStatus st = (rec != null) ? rec.getStatus() : AttendanceStatus.present;
                if (!anyStatus && st != selectedStatus) continue;
                rows.add(new AttendanceRow(u.getId(), "staff", u.getName(), "Staff",
                        selectedDate, st, rec != null ? rec.getRemarks() : null));
            }
        }
        rows.sort(Comparator.comparing(AttendanceRow::name));

        model.addAttribute("rows", rows);
        model.addAttribute("classes", isTeacher
                ? teacherClassNames(user)
                : studentRepository.findDistinctClassDisplay());
        model.addAttribute("statusNames", STATUS_NAMES);
        model.addAttribute("selectedDate", selectedDate.toString());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedClass", classQuery);
        model.addAttribute("name", nameTrim);
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("activePage", "attendance");
        return "attendance";
    }

    @GetMapping("/rollcall")
    public String rollCall(@RequestParam(required = false) String date, Model model) {
        LocalDate selected = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        List<Student> students = isTeacher
                ? studentsOfClass(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        Map<Long, String> currentMarks = new HashMap<>();
        for (Student s : students) {
            currentMarks.put(s.getId(),
                    attendanceRepository.findByStudentAndDate(s, selected)
                            .map(a -> a.getStatus().name())
                            .orElse("present"));
        }

        model.addAttribute("students", students);
        model.addAttribute("selectedDate", selected.toString());
        model.addAttribute("currentMarks", currentMarks);
        model.addAttribute("statuses", STATUS_NAMES);
        model.addAttribute("activePage", "attendance");
        return "attendance-rollcall";
    }

    @GetMapping("/rollcall-staff")
    public String staffRollCall(@RequestParam(required = false) String date, Model model) {
        // Staff attendance is admin-only.
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/attendance";
        }
        LocalDate selected = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<User> staff = userRepository.findByRole(Role.teacher);

        Map<Long, String> currentMarks = new HashMap<>();
        for (User u : staff) {
            currentMarks.put(u.getId(),
                    staffAttendanceRepository.findByStaffAndDate(u, selected)
                            .map(sa -> sa.getStatus().name())
                            .orElse("present"));
        }

        model.addAttribute("staff", staff);
        model.addAttribute("selectedDate", selected.toString());
        model.addAttribute("currentMarks", currentMarks);
        model.addAttribute("statuses", STATUS_NAMES);
        model.addAttribute("activePage", "attendance");
        return "attendance-staff";
    }

    // Expects repeated params studentIds and statuses in matching order, e.g.
    // studentIds=1&statuses=present&studentIds=2&statuses=absent
    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam String date,
                       @RequestParam List<Long> studentIds,
                       @RequestParam List<String> statuses) {
        LocalDate parsedDate = LocalDate.parse(date);
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        // Teachers may only save attendance for students in their own class.
        Set<Long> allowed = isTeacher ? classStudentIds(user) : null;

        for (int i = 0; i < studentIds.size(); i++) {
            if (i >= statuses.size()) break;
            Long sid = studentIds.get(i);
            if (allowed != null && !allowed.contains(sid)) continue;
            Student student = studentRepository.findById(sid).orElse(null);
            if (student == null) continue;

            Attendance record = attendanceRepository.findByStudentAndDate(student, parsedDate)
                    .orElseGet(Attendance::new);
            record.setStudent(student);
            record.setDate(parsedDate);
            record.setStatus(AttendanceStatus.valueOf(statuses.get(i)));
            attendanceRepository.save(record);
        }
        return "redirect:/attendance/rollcall?date=" + date + "&saved=true";
    }

    @PostMapping("/save-staff")
    @Transactional
    public String saveStaff(@RequestParam String date,
                            @RequestParam List<Long> staffIds,
                            @RequestParam List<String> statuses) {
        // Staff attendance is admin-only.
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/attendance";
        }
        LocalDate parsedDate = LocalDate.parse(date);
        for (int i = 0; i < staffIds.size(); i++) {
            if (i >= statuses.size()) break;
            User staff = userRepository.findById(staffIds.get(i)).orElse(null);
            if (staff == null) continue;

            StaffAttendance record = staffAttendanceRepository.findByStaffAndDate(staff, parsedDate)
                    .orElseGet(StaffAttendance::new);
            record.setStaff(staff);
            record.setDate(parsedDate);
            record.setStatus(AttendanceStatus.valueOf(statuses.get(i)));
            staffAttendanceRepository.save(record);
        }
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
        if (isTeacher) {
            // Teachers can only edit student attendance from their own class.
            if ("staff".equals(type)) {
                return "redirect:/attendance";
            }
            Attendance rec = attendanceRepository.findById(id).orElse(null);
            if (rec == null || !classStudentIds(user).contains(rec.getStudent().getId())) {
                return "redirect:/attendance";
            }
        }
        if ("staff".equals(type)) {
            staffAttendanceRepository.findById(id).ifPresent(record -> {
                record.setStatus(newStatus);
                record.setRemarks(remarks);
                staffAttendanceRepository.save(record);
            });
        } else {
            attendanceRepository.findById(id).ifPresent(record -> {
                record.setStatus(newStatus);
                record.setRemarks(remarks);
                attendanceRepository.save(record);
            });
        }
        return "redirect:/attendance";
    }

    /** Student view: only their own attendance records, newest first. */
    private String studentAttendance(Model model, User user) {
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            model.addAttribute("activePage", "attendance");
            return "my-attendance";
        }
        List<Attendance> records = attendanceRepository.findByStudent(student);
        records.sort(Comparator.comparing(Attendance::getDate).reversed());

        long present = records.stream().filter(a -> a.getStatus() == AttendanceStatus.present).count();
        long absent = records.stream().filter(a -> a.getStatus() == AttendanceStatus.absent).count();
        long late = records.stream().filter(a -> a.getStatus() == AttendanceStatus.late).count();
        long excused = records.stream().filter(a -> a.getStatus() == AttendanceStatus.excused).count();
        int rate = records.isEmpty() ? 0 : (int) Math.round(100.0 * present / records.size());

        model.addAttribute("student", student);
        model.addAttribute("records", records);
        model.addAttribute("presentCount", present);
        model.addAttribute("absentCount", absent);
        model.addAttribute("lateCount", late);
        model.addAttribute("excusedCount", excused);
        model.addAttribute("attendanceRate", rate);
        model.addAttribute("activePage", "attendance");
        return "my-attendance";
    }

    /** Students in the teacher's class-teacher class (classTeacherOf, e.g. "6-A"). */
    private List<Student> studentsOfClass(User user) {
        if (user == null) return List.of();
        String cls = user.getClassTeacherOf();
        if (cls == null || cls.isBlank()) return List.of();
        String[] parts = cls.split("-", 2);
        String className = parts[0].trim();
        String section = parts.length > 1 ? parts[1].trim() : "";
        return section.isBlank()
                ? studentRepository.findByClassNameOrderByLastNameAsc(className)
                : studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
    }

    /** Class names the teacher may filter by (only their own class-teacher class). */
    private List<String> teacherClassNames(User user) {
        if (user == null || user.getClassTeacherOf() == null || user.getClassTeacherOf().isBlank()) {
            return List.of();
        }
        return List.of(user.getClassTeacherOf().trim());
    }

    /** IDs of students in the teacher's class. */
    private Set<Long> classStudentIds(User user) {
        return studentsOfClass(user).stream().map(Student::getId).collect(Collectors.toSet());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
