package com.eduadmin.school.controller;

import com.eduadmin.school.model.Mark;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.MarkRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/marks")
public class MarksController {

    /** Standard subjects shown on the entry screen (editable via the datalist). */
    private static final List<String> DEFAULT_SUBJECTS =
            List.of("Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science");

    public record MarkRow(Long markId, Long studentId, String name, String admissionNo, String classDisplay,
                          String subject, Double maxMarks, Double obtained, Double percentage) {}

    public record StudentSummary(Long id, String name, String admissionNo, String classDisplay,
                                 String term, int subjects, double total, double maxTotal,
                                 double percentage, String grade) {}

    private final StudentRepository studentRepository;
    private final MarkRepository markRepository;
    private final UserRepository userRepository;

    public MarksController(StudentRepository studentRepository,
                           MarkRepository markRepository,
                           UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.markRepository = markRepository;
        this.userRepository = userRepository;
    }

    /** Main marks page. Admins/teachers get a per-student summary grid;
     *  students get their own marks per term. */
    @GetMapping
    public String index(@RequestParam(required = false) String term,
                        @RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String name,
                        Model model) {
        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentMarks(model, user);
        }
        boolean isTeacher = user != null && user.getRole() == Role.teacher;

        String termQuery = (term != null && !term.isBlank()) ? term.trim() : defaultTerm();
        List<Student> students = isTeacher
                ? studentsOfClass(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        if (isTeacher) classQuery = "";
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();

        Map<Long, List<Mark>> marksByStudent = new HashMap<>();
        if (!students.isEmpty()) {
            for (Mark m : markRepository.findByStudentIdsAndTerm(
                    students.stream().map(Student::getId).toList(), termQuery)) {
                marksByStudent.computeIfAbsent(m.getStudent().getId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<StudentSummary> rows = new ArrayList<>();
        for (Student s : students) {
            if (!classQuery.isEmpty() && !classQuery.equals(s.getClassDisplay())) continue;
            if (!nameQuery.isEmpty() && !s.getFullName().toLowerCase().contains(nameQuery)) continue;
            List<Mark> ms = marksByStudent.getOrDefault(s.getId(), List.of());
            double total = ms.stream().mapToDouble(Mark::getMarksObtained).sum();
            double maxTotal = ms.stream().mapToDouble(Mark::getMaxMarks).sum();
            double pct = maxTotal == 0 ? 0 : Math.round(1000.0 * total / maxTotal) / 10.0;
            rows.add(new StudentSummary(s.getId(), s.getFullName(), s.getAdmissionNo(), s.getClassDisplay(),
                    termQuery, ms.size(), total, maxTotal, pct, gradeFor(pct)));
        }
        rows.sort(Comparator.comparing(StudentSummary::name));

        model.addAttribute("rows", rows);
        model.addAttribute("terms", markRepository.findDistinctTerms());
        model.addAttribute("defaultTerm", defaultTerm());
        model.addAttribute("term", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("name", nameTrim);
        model.addAttribute("classes", studentRepository.findDistinctClassDisplay());
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("teacherClass", isTeacher && user != null ? user.getClassTeacherOf() : null);
        model.addAttribute("activePage", "marks");
        return "marks";
    }

    /** Entry screen: pick class/term/subject, then enter one mark per student.
     *  Total and percentage are computed automatically. */
    @GetMapping("/enter")
    public String enter(@RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String term,
                        @RequestParam(required = false) String subject,
                        Model model) {
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        List<Student> students = isTeacher
                ? studentsOfClass(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        if (isTeacher) classQuery = "";
        String termQuery = (term != null && !term.isBlank()) ? term.trim() : defaultTerm();
        String subjectQuery = (subject != null && !subject.isBlank()) ? subject.trim() : DEFAULT_SUBJECTS.get(0);

        List<MarkRow> rows = new ArrayList<>();
        for (Student s : students) {
            if (!classQuery.isEmpty() && !classQuery.equals(s.getClassDisplay())) continue;
            Mark m = markRepository.findByStudentAndSubjectAndTerm(s, subjectQuery, termQuery).orElse(null);
            rows.add(new MarkRow(m != null ? m.getId() : null, s.getId(), s.getFullName(), s.getAdmissionNo(), s.getClassDisplay(),
                    subjectQuery,
                    m != null ? m.getMaxMarks() : 100.0,
                    m != null ? m.getMarksObtained() : null,
                    m != null ? m.getPercentage() : null));
        }
        rows.sort(Comparator.comparing(MarkRow::name));

        model.addAttribute("rows", rows);
        model.addAttribute("subjects", markRepository.findDistinctSubjects().isEmpty()
                ? DEFAULT_SUBJECTS : mergedSubjects());
        model.addAttribute("selectedSubject", subjectQuery);
        model.addAttribute("terms", markRepository.findDistinctTerms());
        model.addAttribute("defaultTerm", defaultTerm());
        model.addAttribute("selectedTerm", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("classes", studentRepository.findDistinctClassDisplay());
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("activePage", "marks");
        return "marks-enter";
    }

    /** Saves one mark per student for the given class/term/subject. Upserts
     *  existing records (one row per student+subject+term). */
    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) String classFilter,
                       @RequestParam String term,
                       @RequestParam String subject,
                       @RequestParam(required = false) List<Long> studentIds,
                       @RequestParam(required = false) List<Double> obtained,
                       @RequestParam(required = false) List<Double> maxMarks) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/marks";
        }
        boolean isTeacher = user.getRole() == Role.teacher;
        Set<Long> allowed = isTeacher ? studentsOfClass(user).stream().map(Student::getId).collect(Collectors.toSet()) : null;

        if (studentIds != null && obtained != null) {
            for (int i = 0; i < studentIds.size(); i++) {
                if (i >= obtained.size()) break;
                Long sid = studentIds.get(i);
                if (allowed != null && !allowed.contains(sid)) continue;
                Student student = studentRepository.findById(sid).orElse(null);
                if (student == null) continue;
                Double value = obtained.get(i);
                if (value == null || value < 0) continue;

                double max = 100.0;
                if (maxMarks != null && i < maxMarks.size() && maxMarks.get(i) != null && maxMarks.get(i) > 0) {
                    max = maxMarks.get(i);
                }

                Mark mark = markRepository.findByStudentAndSubjectAndTerm(student, subject, term)
                        .orElseGet(Mark::new);
                mark.setStudent(student);
                mark.setSubject(subject);
                mark.setTerm(term);
                mark.setMaxMarks(max);
                mark.setMarksObtained(value);
                markRepository.save(mark);
            }
        }
        String redir = "redirect:/marks/enter?term=" + term + "&subject=" + subject;
        if (classFilter != null && !classFilter.isBlank()) {
            redir += "&classFilter=" + classFilter;
        }
        return redir + "&saved=true";
    }

    /** Deletes a single mark record (admin or the student's own class teacher). */
    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String term,
                         @RequestParam(required = false) String subject,
                         @RequestParam(required = false) String classFilter) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/marks";
        }
        markRepository.findById(id).ifPresent(m -> {
            if (user.getRole() == Role.teacher
                    && !studentsOfClass(user).stream().anyMatch(s -> s.getId().equals(m.getStudent().getId()))) {
                return;
            }
            markRepository.delete(m);
        });
        String redir = "redirect:/marks/enter?term=" + term + "&subject=" + subject;
        if (classFilter != null && !classFilter.isBlank()) {
            redir += "&classFilter=" + classFilter;
        }
        return redir;
    }

    /** Student view: their own marks per term, with totals and percentages. */
    private String studentMarks(Model model, User user) {
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            model.addAttribute("activePage", "marks");
            return "my-marks";
        }
        Map<String, List<Mark>> byTerm = new LinkedHashMap<>();
        for (Mark m : markRepository.findByStudentOrderByTermSubject(student)) {
            byTerm.computeIfAbsent(m.getTerm(), k -> new ArrayList<>()).add(m);
        }
        model.addAttribute("student", student);
        model.addAttribute("terms", byTerm);
        model.addAttribute("activePage", "marks");
        return "my-marks";
    }

    private List<String> mergedSubjects() {
        List<String> all = new ArrayList<>(markRepository.findDistinctSubjects());
        for (String s : DEFAULT_SUBJECTS) {
            if (!all.contains(s)) all.add(s);
        }
        return all;
    }

    private String defaultTerm() {
        List<String> terms = markRepository.findDistinctTerms();
        return terms.isEmpty() ? "Term 1 2026" : terms.get(0);
    }

    /** Letter grade from a percentage. */
    public static String gradeFor(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B+";
        if (pct >= 60) return "B";
        if (pct >= 50) return "C";
        if (pct >= 40) return "D";
        return "F";
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

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
