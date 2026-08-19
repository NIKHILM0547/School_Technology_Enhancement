package com.eduadmin.school.controller;

import com.eduadmin.school.model.ClassTeacherRemark;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.ClassTeacherRemarkRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/remarks")
public class ClassTeacherRemarkController {

    public record RemarkRow(Long studentId, String name, String admissionNo, String classDisplay,
                            Long remarkId, String remark, String updatedByName,
                            String updatedAtDisplay) {}

    private final StudentRepository studentRepository;
    private final ClassTeacherRemarkRepository remarkRepository;
    private final UserRepository userRepository;

    public ClassTeacherRemarkController(StudentRepository studentRepository,
                                        ClassTeacherRemarkRepository remarkRepository,
                                        UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.remarkRepository = remarkRepository;
        this.userRepository = userRepository;
    }

    /** Main page. A class teacher sees only the students of her own class;
     *  admins may pick any class. Students are not allowed. */
    @GetMapping
    public String index(@RequestParam(required = false) String classFilter, Model model) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/";
        }
        boolean isTeacher = user.getRole() == Role.teacher;

        String classQuery;
        List<Student> students;
        if (isTeacher) {
            String teacherClass = user.getClassTeacherOf();
            if (teacherClass == null || teacherClass.isBlank()) {
                model.addAttribute("rows", List.of());
                model.addAttribute("classes", List.of());
                model.addAttribute("classQuery", "");
                model.addAttribute("isTeacher", true);
                model.addAttribute("teacherClass", null);
                model.addAttribute("noClassAssigned", true);
                model.addAttribute("activePage", "remarks");
                return "class-teacher-remarks";
            }
            students = studentsOfClass(teacherClass);
            classQuery = teacherClass;
            model.addAttribute("noClassAssigned", false);
        } else {
            List<String> classes = studentRepository.findDistinctClassDisplay();
            classQuery = (classFilter != null && !classFilter.isBlank())
                    ? classFilter.trim()
                    : (classes.isEmpty() ? "" : classes.get(0));
            students = classQuery.isBlank()
                    ? List.of()
                    : studentsOfClass(classQuery);
            model.addAttribute("classes", classes);
            model.addAttribute("noClassAssigned", false);
        }

        model.addAttribute("rows", buildRows(students));
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("teacherClass", isTeacher ? user.getClassTeacherOf() : null);
        model.addAttribute("activePage", "remarks");
        return "class-teacher-remarks";
    }

    /** Saves (or updates) the class teacher remark for one student. Only the
     *  student's own class teacher (or an admin) may write it. */
    @PostMapping("/save")
    public String save(@RequestParam Long studentId,
                       @RequestParam(required = false) String remark,
                       @RequestParam(required = false) String classFilter) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/";
        }
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return "redirect:/remarks";
        }
        if (!canManage(user, student)) {
            return "redirect:/remarks";
        }

        String text = remark != null ? remark.trim() : "";
        ClassTeacherRemark entity = remarkRepository.findByStudent(student).orElse(null);
        if (text.isEmpty()) {
            if (entity != null) {
                remarkRepository.delete(entity);
            }
        } else {
            if (entity == null) {
                entity = new ClassTeacherRemark();
                entity.setStudent(student);
                entity.setCreatedBy(user);
                entity.setCreatedAt(LocalDateTime.now());
            }
            entity.setRemark(text);
            entity.setUpdatedBy(user);
            entity.setUpdatedAt(LocalDateTime.now());
            remarkRepository.save(entity);
        }

        String redir = "redirect:/remarks";
        if (!classFilter.isBlank()) {
            redir += "?classFilter=" + classFilter;
        }
        return redir + (classFilter.isBlank() ? "?saved=true" : "&saved=true");
    }

    private List<RemarkRow> buildRows(List<Student> students) {
        List<RemarkRow> rows = new ArrayList<>();
        if (students.isEmpty()) return rows;

        Map<Long, ClassTeacherRemark> byStudent = remarkRepository
                .findByStudentIn(students).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));

        Map<Long, User> usersById = new HashMap<>();
        byStudent.values().forEach(r -> {
            if (r.getUpdatedBy() != null) usersById.putIfAbsent(r.getUpdatedBy().getId(), r.getUpdatedBy());
            if (r.getCreatedBy() != null) usersById.putIfAbsent(r.getCreatedBy().getId(), r.getCreatedBy());
        });

        for (Student s : students.stream().sorted(Comparator.comparing(Student::getFullName)).toList()) {
            ClassTeacherRemark r = byStudent.get(s.getId());
            User updatedBy = r != null && r.getUpdatedBy() != null ? r.getUpdatedBy() : null;
            rows.add(new RemarkRow(s.getId(), s.getFullName(), s.getAdmissionNo(), s.getClassDisplay(),
                    r != null ? r.getId() : null,
                    r != null ? r.getRemark() : "",
                    updatedBy != null ? updatedBy.getName() : null,
                    r != null ? r.getUpdatedAt().format(java.time.format.DateTimeFormatter
                            .ofPattern("dd MMM yyyy, hh:mm a")) : null));
        }
        return rows;
    }

    /** True if the user may manage remarks for this student: admins always,
     *  teachers only in their own class-teacher class. */
    private boolean canManage(User user, Student student) {
        if (user.getRole() == Role.admin) return true;
        String teacherClass = user.getClassTeacherOf();
        return teacherClass != null && !teacherClass.isBlank()
                && student.getClassDisplay().equals(teacherClass.trim());
    }

    /** Students of a class in "6-A" form, sorted by name. */
    private List<Student> studentsOfClass(String cls) {
        String[] parts = cls.split("-", 2);
        String className = parts[0].trim();
        String section = parts.length > 1 ? parts[1].trim() : "";
        return section.isBlank()
                ? studentRepository.findByClassNameOrderByLastNameAsc(className)
                : studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}