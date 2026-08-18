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

@Controller
@RequestMapping("/marks")
public class MarksController {

    /** Standard subjects shown on the entry screen (editable via the datalist). */
    private static final List<String> DEFAULT_SUBJECTS =
            List.of("Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science");

    /** All classes the school offers, used for the class filter dropdown. */
    private static final List<String> CLASSES = List.of(
            "1-A", "1-B", "1-C", "2-A", "2-B", "2-C",
            "3-A", "3-B", "3-C", "4-A", "4-B", "4-C",
            "5-A", "5-B", "5-C", "6-A", "6-B", "6-C",
            "7-A", "7-B", "7-C", "8-A", "8-B", "8-C",
            "9-A", "9-B", "9-C", "10-A", "10-B", "10-C",
            "11-A", "11-B", "11-C", "12-A", "12-B", "12-C"
    );

    public record MarkRow(Long markId, Long studentId, String name, String admissionNo, String classDisplay,
                          String subject, Double maxMarks, Double obtained, Double percentage) {}

    public record StudentSummary(Long id, String name, String admissionNo, String classDisplay,
                                 String term, int subjects, double total, double maxTotal,
                                 double percentage, String grade) {}

    public record SubjectView(Long studentId, String name, String admissionNo, String classDisplay,
                              Double maxMarks, Double obtained, Double percentage, String grade) {}

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

    /** Main marks page. Admins/teachers get a per-student summary grid (optionally
     *  filtered to one subject via the subject dropdown); students get their own
     *  marks per term. */
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

        String termQuery = (term != null && !term.isBlank()) ? term.trim() : defaultTerm();
        List<Student> students = isTeacher
                ? teacherVisibleStudents(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        final String classFilterQuery = classQuery;
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();
        String subjectQuery = (subject != null && !subject.isBlank()) ? subject.trim() : "";

        // Students after the class + name filters.
        List<Student> scope = students.stream()
                .filter(s -> classFilterQuery.isEmpty() || classFilterQuery.equals(s.getClassDisplay()))
                .filter(s -> nameQuery.isEmpty() || s.getFullName().toLowerCase().contains(nameQuery))
                .toList();

        Map<Long, List<Mark>> marksByStudent = new HashMap<>();
        if (!students.isEmpty()) {
            for (Mark m : markRepository.findByStudentIdsAndTerm(
                    students.stream().map(Student::getId).toList(), termQuery)) {
                marksByStudent.computeIfAbsent(m.getStudent().getId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<StudentSummary> rows = new ArrayList<>();
        for (Student s : scope) {
            List<Mark> ms = marksByStudent.getOrDefault(s.getId(), List.of());
            double total = ms.stream().mapToDouble(Mark::getMarksObtained).sum();
            double maxTotal = ms.stream().mapToDouble(Mark::getMaxMarks).sum();
            double pct = maxTotal == 0 ? 0 : Math.round(1000.0 * total / maxTotal) / 10.0;
            rows.add(new StudentSummary(s.getId(), s.getFullName(), s.getAdmissionNo(), s.getClassDisplay(),
                    termQuery, ms.size(), total, maxTotal, pct, gradeFor(pct)));
        }
        rows.sort(Comparator.comparing(StudentSummary::name));

        // Subjects the filtered students actually have marks for, plus the defaults.
        List<String> subjectOptions = new ArrayList<>();
        if (!scope.isEmpty()) {
            subjectOptions.addAll(markRepository.findDistinctSubjectsByStudentIds(
                    scope.stream().map(Student::getId).toList()));
        }
        for (String d : DEFAULT_SUBJECTS) {
            if (!subjectOptions.contains(d)) subjectOptions.add(d);
        }

        // Per-subject view: one row per student showing their mark for the selected subject.
        List<SubjectView> subjectRows = new ArrayList<>();
        if (!subjectQuery.isEmpty()) {
            Map<Long, Mark> marksBySubject = new HashMap<>();
            if (!scope.isEmpty()) {
                for (Mark m : markRepository.findByStudentIdsAndSubjectAndTerm(
                        scope.stream().map(Student::getId).toList(), subjectQuery, termQuery)) {
                    marksBySubject.put(m.getStudent().getId(), m);
                }
            }
            for (Student s : scope) {
                Mark m = marksBySubject.get(s.getId());
                Double pct = m != null ? m.getPercentage() : null;
                subjectRows.add(new SubjectView(s.getId(), s.getFullName(), s.getAdmissionNo(),
                        s.getClassDisplay(),
                        m != null ? m.getMaxMarks() : null,
                        m != null ? m.getMarksObtained() : null,
                        pct,
                        m != null ? gradeFor(pct) : null));
            }
            subjectRows.sort(Comparator.comparing(SubjectView::name));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("subjectRows", subjectRows);
        model.addAttribute("subjectOptions", subjectOptions);
        model.addAttribute("subjectQuery", subjectQuery);
        model.addAttribute("terms", markRepository.findDistinctTerms());
        model.addAttribute("defaultTerm", defaultTerm());
        model.addAttribute("term", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("name", nameTrim);
        model.addAttribute("classes", isTeacher ? teachableClassOptions(user) : CLASSES);
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("teacherClass", isTeacher && user != null ? user.getClassTeacherOf() : null);
        model.addAttribute("activePage", "marks");
        return "marks";
    }

    /** Entry screen: pick class/term/subject, then enter one mark per student.
     *  With a studentId only that student is shown (per-student edit); otherwise
     *  the whole class grid is shown. Total and percentage are computed automatically. */
    @GetMapping("/enter")
    public String enter(@RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String term,
                        @RequestParam(required = false) String subject,
                        @RequestParam(required = false) Long studentId,
                        Model model) {
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;

        List<Student> students;
        String classQuery;
        Student singleStudent = null;
        if (studentId != null) {
            final Student found = studentRepository.findById(studentId).orElse(null);
            singleStudent = found;
            students = found != null ? List.of(found) : List.of();
            classQuery = found != null ? found.getClassDisplay() : "";
            if (isTeacher && found != null
                    && !teacherVisibleStudents(user).stream().anyMatch(s -> s.getId().equals(found.getId()))) {
                return "redirect:/marks";
            }
        } else {
            students = isTeacher
                    ? teacherVisibleStudents(user)
                    : studentRepository.findAllByOrderByLastNameAsc();
            classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        }

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

        // Subjects for the students being shown (their recorded subjects + defaults).
        List<Long> ids = students.stream().map(Student::getId).toList();
        List<String> subjectOptions = new ArrayList<>();
        if (!ids.isEmpty()) {
            subjectOptions.addAll(markRepository.findDistinctSubjectsByStudentIds(ids));
        }
        for (String d : DEFAULT_SUBJECTS) {
            if (!subjectOptions.contains(d)) subjectOptions.add(d);
        }
        // Teachers may only enter marks for subjects they teach — except in their
        // own class-teacher class, where any subject is allowed.
        if (isTeacher && user != null
                && (classQuery.isEmpty() || !classQuery.equals(user.getClassTeacherOf()))) {
            Set<String> taught = teacherSubjects(user);
            subjectOptions.removeIf(s -> !taught.contains(s));
            if (subjectOptions.isEmpty()) {
                subjectOptions.addAll(taught.isEmpty() ? DEFAULT_SUBJECTS : taught);
            }
        }

        model.addAttribute("rows", rows);
        model.addAttribute("subjects", subjectOptions);
        model.addAttribute("selectedSubject", subjectQuery);
        model.addAttribute("terms", markRepository.findDistinctTerms());
        model.addAttribute("defaultTerm", defaultTerm());
        model.addAttribute("selectedTerm", termQuery);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("classes", isTeacher ? teachableClassOptions(user) : CLASSES);
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("student", singleStudent);
        model.addAttribute("studentId", studentId);
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
                       @RequestParam(required = false) Long studentId,
                       @RequestParam(required = false) List<Long> studentIds,
                       @RequestParam(required = false) List<Double> obtained,
                       @RequestParam(required = false) List<Double> maxMarks) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/marks";
        }
        boolean isTeacher = user.getRole() == Role.teacher;

        if (studentIds != null && obtained != null) {
            for (int i = 0; i < studentIds.size(); i++) {
                if (i >= obtained.size()) break;
                Long sid = studentIds.get(i);
                Student student = studentRepository.findById(sid).orElse(null);
                if (student == null) continue;
                if (isTeacher && !canEdit(user, student, subject)) continue;
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
        if (studentId != null) {
            redir += "&studentId=" + studentId;
        } else if (classFilter != null && !classFilter.isBlank()) {
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
                         @RequestParam(required = false) String classFilter,
                         @RequestParam(required = false) Long studentId) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/marks";
        }
        markRepository.findById(id).ifPresent(m -> {
            if (user.getRole() == Role.teacher && !canEdit(user, m.getStudent(), subject)) {
                return;
            }
            markRepository.delete(m);
        });
        String redir = "redirect:/marks/enter?term=" + term + "&subject=" + subject;
        if (studentId != null) {
            redir += "&studentId=" + studentId;
        } else if (classFilter != null && !classFilter.isBlank()) {
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

    /** Classes a teacher may edit marks for: their class-teacher class plus the
     *  classes they teach a subject in (assignedClasses, comma-separated). */
    private Set<String> teacherClasses(User user) {
        Set<String> classes = new HashSet<>();
        if (user == null) return classes;
        if (user.getClassTeacherOf() != null && !user.getClassTeacherOf().isBlank()) {
            classes.add(user.getClassTeacherOf().trim());
        }
        if (user.getAssignedClasses() != null) {
            for (String c : user.getAssignedClasses().split(",")) {
                if (!c.isBlank()) classes.add(c.trim());
            }
        }
        return classes;
    }

    /** Sorted classes a teacher may edit, used for the class filter dropdown. */
    private List<String> teachableClassOptions(User user) {
        return teacherClasses(user).stream().sorted().toList();
    }

    /** Subjects a teacher teaches, from their subject field (comma-separated). */
    private Set<String> teacherSubjects(User user) {
        Set<String> subjects = new HashSet<>();
        if (user != null && user.getSubject() != null) {
            for (String s : user.getSubject().split(",")) {
                if (!s.isBlank()) subjects.add(s.trim());
            }
        }
        return subjects;
    }

    /** Students a teacher may edit marks for: their class-teacher class (any
     *  subject) plus all classes they teach a subject in. */
    private List<Student> teacherVisibleStudents(User user) {
        Set<String> classes = teacherClasses(user);
        return studentRepository.findAllByOrderByLastNameAsc().stream()
                .filter(s -> classes.contains(s.getClassDisplay()))
                .toList();
    }

    /** True if the teacher may enter a mark for this student+subject: their own
     *  class-teacher class allows any subject; other classes only for subjects
     *  they teach. */
    private boolean canEdit(User user, Student student, String subject) {
        if (user.getClassTeacherOf() != null && !user.getClassTeacherOf().isBlank()
                && student.getClassDisplay().equals(user.getClassTeacherOf().trim())) {
            return true;
        }
        return teacherClasses(user).contains(student.getClassDisplay())
                && teacherSubjects(user).contains(subject);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
