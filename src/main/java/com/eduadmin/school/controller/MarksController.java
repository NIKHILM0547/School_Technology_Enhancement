package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.service.MarksService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/marks")
public class MarksController {

    private final MarksService marksService;

    public MarksController(MarksService marksService) {
        this.marksService = marksService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String term,
                        @RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String name,
                        @RequestParam(required = false) String subject,
                        Model model) {
        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentMarks(model, user);
        }
        boolean isTeacher = user != null && user.getRole() == Role.teacher;

        List<MarksService.StudentSummary> rows = marksService.getStudentSummaries(term, classFilter, name, subject, user, isTeacher);
        List<MarksService.SubjectView> subjectRows = marksService.getSubjectViews(term, classFilter, name, subject, user, isTeacher);

        String termQuery = (term != null && !term.isBlank()) ? term.trim() : marksService.defaultTerm();
        List<Student> students = isTeacher
                ? marksService.teacherVisibleStudents(user)
                : marksService.getAllStudents();
        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();
        List<Student> scope = students.stream()
                .filter(s -> classQuery.isEmpty() || classQuery.equals(s.getClassDisplay()))
                .filter(s -> nameQuery.isEmpty() || s.getFullName().toLowerCase().contains(nameQuery))
                .toList();
        List<String> subjectOptions = marksService.getSubjectOptionsForTeacher(user, classQuery, isTeacher, scope);

        model.addAttribute("rows", rows);
        model.addAttribute("subjectRows", subjectRows);
        model.addAttribute("subjectOptions", subjectOptions);
        model.addAttribute("subjectQuery", subject != null && !subject.isBlank() ? subject.trim() : "");
        model.addAttribute("terms", marksService.getDistinctTerms());
        model.addAttribute("defaultTerm", marksService.defaultTerm());
        model.addAttribute("term", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("name", nameTrim);
        model.addAttribute("classes", marksService.getClassesForUser(user, isTeacher));
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("teacherClass", isTeacher && user != null ? user.getClassTeacherOf() : null);
        model.addAttribute("activePage", "marks");
        return "marks";
    }

    @GetMapping("/enter")
    public String enter(@RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String term,
                        @RequestParam(required = false) String subject,
                        @RequestParam(required = false) Long studentId,
                        Model model) {
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;

        List<MarksService.MarkRow> rows = marksService.getMarkRowsForEntry(classFilter, term, subject, studentId, user, isTeacher);
        if (rows.isEmpty() && studentId != null && isTeacher) {
            return "redirect:/marks";
        }

        List<Student> students;
        String classQuery;
        Student singleStudent = null;
        if (studentId != null) {
            singleStudent = marksService.getStudentById(studentId);
            students = singleStudent != null ? List.of(singleStudent) : List.of();
            classQuery = singleStudent != null ? singleStudent.getClassDisplay() : "";
        } else {
            students = isTeacher
                    ? marksService.teacherVisibleStudents(user)
                    : marksService.getAllStudents();
            classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        }

        String termQuery = (term != null && !term.isBlank()) ? term.trim() : marksService.defaultTerm();
        String subjectQuery = (subject != null && !subject.isBlank()) ? subject.trim() : marksService.DEFAULT_SUBJECTS.get(0);

        List<String> subjectOptions = marksService.getSubjectOptionsForTeacher(user, classQuery, isTeacher, students);

        model.addAttribute("rows", rows);
        model.addAttribute("subjects", subjectOptions);
        model.addAttribute("selectedSubject", subjectQuery);
        model.addAttribute("terms", marksService.getDistinctTerms());
        model.addAttribute("defaultTerm", marksService.defaultTerm());
        model.addAttribute("selectedTerm", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("classes", marksService.getClassesForUser(user, isTeacher));
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("student", singleStudent);
        model.addAttribute("studentId", studentId);
        model.addAttribute("activePage", "marks");
        return "marks-enter";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String classFilter,
                       @RequestParam String term,
                       @RequestParam String subject,
                       @RequestParam(required = false) Long studentId,
                       @RequestParam(required = false) List<Long> studentIds,
                       @RequestParam(required = false) List<Double> obtained,
                       @RequestParam(required = false) List<Double> maxMarks) {
        User user = currentUser();
        return marksService.saveMarks(classFilter, term, subject, studentId, studentIds, obtained, maxMarks, user);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String term,
                         @RequestParam(required = false) String subject,
                         @RequestParam(required = false) String classFilter,
                         @RequestParam(required = false) Long studentId) {
        User user = currentUser();
        marksService.deleteMark(id, user, subject);
        String redir = "redirect:/marks/enter?term=" + term + "&subject=" + subject;
        if (studentId != null) {
            redir += "&studentId=" + studentId;
        } else if (classFilter != null && !classFilter.isBlank()) {
            redir += "&classFilter=" + classFilter;
        }
        return redir;
    }

    private String studentMarks(Model model, User user) {
        Student student = marksService.getStudentByUser(user);
        if (student == null) {
            model.addAttribute("activePage", "marks");
            return "my-marks";
        }
        Map<String, List<com.eduadmin.school.model.Mark>> byTerm = marksService.getStudentMarksByTerm(student);
        model.addAttribute("student", student);
        model.addAttribute("terms", byTerm);
        model.addAttribute("activePage", "marks");
        return "my-marks";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return marksService.getUserByEmail(auth.getName());
    }
}