package com.eduadmin.school.service;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarksService {

    private static final List<String> DEFAULT_SUBJECTS = List.of(
            "Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science");

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

    public MarksService(StudentRepository studentRepository,
                        MarkRepository markRepository,
                        UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.markRepository = markRepository;
        this.userRepository = userRepository;
    }

    public List<StudentSummary> getStudentSummaries(String term, String classFilter, String name, String subject,
                                                     User user, boolean isTeacher) {
        String termQuery = (term != null && !term.isBlank()) ? term.trim() : defaultTerm();
        List<Student> students = isTeacher
                ? teacherVisibleStudents(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();
        String subjectQuery = (subject != null && !subject.isBlank()) ? subject.trim() : "";

        List<Student> scope = students.stream()
                .filter(s -> classQuery.isEmpty() || classQuery.equals(s.getClassDisplay()))
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
        return rows;
    }

    public List<SubjectView> getSubjectViews(String term, String classFilter, String name, String subject,
                                              User user, boolean isTeacher) {
        String termQuery = (term != null && !term.isBlank()) ? term.trim() : defaultTerm();
        String subjectQuery = (subject != null && !subject.isBlank()) ? subject.trim() : "";
        if (subjectQuery.isEmpty()) return List.of();

        List<Student> students = isTeacher
                ? teacherVisibleStudents(user)
                : studentRepository.findAllByOrderByLastNameAsc();

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        String nameTrim = (name != null && !name.isBlank()) ? name.trim() : "";
        String nameQuery = nameTrim.toLowerCase();

        List<Student> scope = students.stream()
                .filter(s -> classQuery.isEmpty() || classQuery.equals(s.getClassDisplay()))
                .filter(s -> nameQuery.isEmpty() || s.getFullName().toLowerCase().contains(nameQuery))
                .toList();

        Map<Long, Mark> marksBySubject = new HashMap<>();
        if (!scope.isEmpty()) {
            for (Mark m : markRepository.findByStudentIdsAndSubjectAndTerm(
                    scope.stream().map(Student::getId).toList(), subjectQuery, termQuery)) {
                marksBySubject.put(m.getStudent().getId(), m);
            }
        }

        List<SubjectView> subjectRows = new ArrayList<>();
        for (Student s : scope) {
            Mark m = marksBySubject.get(s.getId());
            Double pct = m != null ? m.getPercentage() : null;
            subjectRows.add(new SubjectView(s.getId(), s.getFullName(), s.getAdmissionNo(), s.getClassDisplay(),
                    m != null ? m.getMaxMarks() : null,
                    m != null ? m.getMarksObtained() : null,
                    pct,
                    m != null ? gradeFor(pct) : null));
        }
        subjectRows.sort(Comparator.comparing(SubjectView::name));
        return subjectRows;
    }

    public List<String> getSubjectOptions(List<Student> scope) {
        List<String> subjectOptions = new ArrayList<>();
        if (!scope.isEmpty()) {
            subjectOptions.addAll(markRepository.findDistinctSubjectsByStudentIds(
                    scope.stream().map(Student::getId).toList()));
        }
        for (String d : DEFAULT_SUBJECTS) {
            if (!subjectOptions.contains(d)) subjectOptions.add(d);
        }
        return subjectOptions;
    }

    public List<String> getSubjectOptionsForTeacher(User user, String classQuery, boolean isTeacher, List<Student> students) {
        List<String> subjectOptions = getSubjectOptions(students);
        if (isTeacher && user != null
                && (classQuery.isEmpty() || !classQuery.equals(user.getClassTeacherOf()))) {
            Set<String> taught = teacherSubjects(user);
            subjectOptions.removeIf(s -> !taught.contains(s));
            if (subjectOptions.isEmpty()) {
                subjectOptions.addAll(taught.isEmpty() ? DEFAULT_SUBJECTS : taught);
            }
        }
        return subjectOptions;
    }

    public List<MarkRow> getMarkRowsForEntry(String classFilter, String term, String subject, Long studentId,
                                              User user, boolean isTeacher) {
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
                return List.of();
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
        return rows;
    }

    @Transactional
    public String saveMarks(String classFilter, String term, String subject, Long studentId,
                            List<Long> studentIds, List<Double> obtained, List<Double> maxMarks,
                            User user) {
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

    @Transactional
    public void deleteMark(Long id, User user, String subject) {
        if (user == null || user.getRole() == Role.student) {
            return;
        }
        markRepository.findById(id).ifPresent(m -> {
            if (user.getRole() == Role.teacher && !canEdit(user, m.getStudent(), subject)) {
                return;
            }
            markRepository.delete(m);
        });
    }

    public Map<String, List<Mark>> getStudentMarksByTerm(Student student) {
        Map<String, List<Mark>> byTerm = new LinkedHashMap<>();
        for (Mark m : markRepository.findByStudentOrderByTermSubject(student)) {
            byTerm.computeIfAbsent(m.getTerm(), k -> new ArrayList<>()).add(m);
        }
        return byTerm;
    }

    public List<String> getDistinctTerms() {
        return markRepository.findDistinctTerms();
    }

    public String defaultTerm() {
        List<String> terms = markRepository.findDistinctTerms();
        return terms.isEmpty() ? "Term 1 2026" : terms.get(0);
    }

    public List<String> getClassesForUser(User user, boolean isTeacher) {
        if (isTeacher) {
            return teachableClassOptions(user);
        }
        return CLASSES;
    }

    public static String gradeFor(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B+";
        if (pct >= 60) return "B";
        if (pct >= 50) return "C";
        if (pct >= 40) return "D";
        return "F";
    }

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

    private List<String> teachableClassOptions(User user) {
        return teacherClasses(user).stream().sorted().toList();
    }

    private Set<String> teacherSubjects(User user) {
        Set<String> subjects = new HashSet<>();
        if (user != null && user.getSubject() != null) {
            for (String s : user.getSubject().split(",")) {
                if (!s.isBlank()) subjects.add(s.trim());
            }
        }
        return subjects;
    }

    private boolean canEdit(User user, Student student, String subject) {
        if (user.getClassTeacherOf() != null && !user.getClassTeacherOf().isBlank()
                && student.getClassDisplay().equals(user.getClassTeacherOf().trim())) {
            return true;
        }
        return teacherClasses(user).contains(student.getClassDisplay())
                && teacherSubjects(user).contains(subject);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public Student getStudentByUser(User user) {
        return studentRepository.findByUser(user).orElse(null);
    }

    public List<Student> teacherVisibleStudents(User user) {
        Set<String> classes = teacherClasses(user);
        return studentRepository.findAllByOrderByLastNameAsc().stream()
                .filter(s -> classes.contains(s.getClassDisplay()))
                .toList();
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAllByOrderByLastNameAsc();
    }

    public Student getStudentById(Long id) {
        return studentRepository.findById(id).orElse(null);
    }
}