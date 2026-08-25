package com.eduadmin.school.service;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final FeeStructureRepository feeStructureRepository;

    public AttendanceService(StudentRepository studentRepository,
                             AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             StaffAttendanceRepository staffAttendanceRepository,
                             FeeStructureRepository feeStructureRepository) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
        this.feeStructureRepository = feeStructureRepository;
    }

    public record AttendanceRow(Long id, String type, String name, String className,
                                LocalDate date, AttendanceStatus status, String remarks) {}

    public List<AttendanceRow> getAttendanceRows(LocalDate date, String typeFilter,
                                                  String classFilter, String nameFilter,
                                                  User user, boolean isTeacher) {
        String typeQuery = (typeFilter != null && !typeFilter.isBlank()) ? typeFilter.trim() : "";
        if (!typeQuery.equals("student") && !typeQuery.equals("teacher")) {
            typeQuery = "";
        }

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        if ("teacher".equals(typeQuery)) {
            classQuery = "";
        }
        String nameQuery = (nameFilter != null && !nameFilter.isBlank()) ? nameFilter.trim().toLowerCase() : "";

        List<Student> visibleStudents;
        if (isTeacher) {
            visibleStudents = studentsOfClass(user);
        } else if ("teacher".equals(typeQuery)) {
            visibleStudents = List.of();
        } else {
            visibleStudents = studentRepository.findAllByOrderByLastNameAsc();
        }

        Map<Long, Attendance> studentRecords = new HashMap<>();
        for (Attendance a : attendanceRepository.findByDate(date)) {
            studentRecords.put(a.getStudent().getId(), a);
        }
        Map<Long, StaffAttendance> staffRecords = new HashMap<>();
        for (StaffAttendance sa : staffAttendanceRepository.findByDate(date)) {
            staffRecords.put(sa.getStaff().getId(), sa);
        }

        List<AttendanceRow> rows = new ArrayList<>();
        boolean classFiltered = !classQuery.isEmpty();
        for (Student s : visibleStudents) {
            if (classFiltered && !classQuery.equals(s.getClassDisplay())) continue;
            if (!nameQuery.isEmpty() && !s.getFullName().toLowerCase().contains(nameQuery)) continue;
            Attendance rec = studentRecords.get(s.getId());
            if (rec == null) continue;
            rows.add(new AttendanceRow(rec.getId(), "student", s.getFullName(), s.getClassDisplay(),
                    date, rec.getStatus(), rec.getRemarks()));
        }

        if (!isTeacher && !"student".equals(typeQuery) && !classFiltered) {
            for (User u : userRepository.findByRole(Role.teacher)) {
                if (!nameQuery.isEmpty() && !u.getName().toLowerCase().contains(nameQuery)) continue;
                StaffAttendance rec = staffRecords.get(u.getId());
                if (rec == null) continue;
                rows.add(new AttendanceRow(rec.getId(), "staff", u.getName(), "Staff",
                        date, rec.getStatus(), rec.getRemarks()));
            }
        }
        rows.sort(Comparator.comparing(AttendanceRow::name));
        return rows;
    }

    public List<AttendanceRow> getAttendanceRowsWithUnmarked(LocalDate date, String typeFilter,
                                                              String classFilter, String nameFilter,
                                                              String statusFilter,
                                                              User user, boolean isTeacher) {
        String typeQuery = (typeFilter != null && !typeFilter.isBlank()) ? typeFilter.trim() : "";
        if (!typeQuery.equals("student") && !typeQuery.equals("teacher")) {
            typeQuery = "";
        }

        AttendanceStatus selectedStatus = null;
        boolean anyStatus = true;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                selectedStatus = AttendanceStatus.valueOf(statusFilter);
                anyStatus = false;
            } catch (IllegalArgumentException ignored) {
            }
        }

        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        if ("teacher".equals(typeQuery)) {
            classQuery = "";
        }
        String nameQuery = (nameFilter != null && !nameFilter.isBlank()) ? nameFilter.trim().toLowerCase() : "";

        List<Student> visibleStudents;
        if (isTeacher) {
            visibleStudents = studentsOfClass(user);
        } else if ("teacher".equals(typeQuery)) {
            visibleStudents = List.of();
        } else {
            visibleStudents = studentRepository.findAllByOrderByLastNameAsc();
        }

        Map<Long, Attendance> studentRecords = new HashMap<>();
        for (Attendance a : attendanceRepository.findByDate(date)) {
            studentRecords.put(a.getStudent().getId(), a);
        }
        Map<Long, StaffAttendance> staffRecords = new HashMap<>();
        for (StaffAttendance sa : staffAttendanceRepository.findByDate(date)) {
            staffRecords.put(sa.getStaff().getId(), sa);
        }

        List<AttendanceRow> rows = new ArrayList<>();
        boolean classFiltered = !classQuery.isEmpty();
        for (Student s : visibleStudents) {
            if (classFiltered && !classQuery.equals(s.getClassDisplay())) continue;
            if (!nameQuery.isEmpty() && !s.getFullName().toLowerCase().contains(nameQuery)) continue;
            Attendance rec = studentRecords.get(s.getId());
            if (rec == null && !anyStatus) continue;
            AttendanceStatus st = (rec != null) ? rec.getStatus() : null;
            if (rec != null && !anyStatus && st != selectedStatus) continue;
            rows.add(new AttendanceRow(rec != null ? rec.getId() : null, "student", s.getFullName(), s.getClassDisplay(),
                    date, st, rec != null ? rec.getRemarks() : null));
        }

        if (!isTeacher && !"student".equals(typeQuery) && !classFiltered) {
            for (User u : userRepository.findByRole(Role.teacher)) {
                if (!nameQuery.isEmpty() && !u.getName().toLowerCase().contains(nameQuery)) continue;
                StaffAttendance rec = staffRecords.get(u.getId());
                if (rec == null && !anyStatus) continue;
                AttendanceStatus st = (rec != null) ? rec.getStatus() : null;
                if (rec != null && !anyStatus && st != selectedStatus) continue;
                rows.add(new AttendanceRow(rec != null ? rec.getId() : null, "staff", u.getName(), "Staff",
                        date, st, rec != null ? rec.getRemarks() : null));
            }
        }
        rows.sort(Comparator.comparing(AttendanceRow::name));
        return rows;
    }

    public Map<Long, String> getCurrentStudentMarks(List<Student> students, LocalDate date) {
        Map<Long, String> marks = new HashMap<>();
        for (Student s : students) {
            marks.put(s.getId(),
                    attendanceRepository.findByStudentAndDate(s, date)
                            .map(a -> a.getStatus().name())
                            .orElse("present"));
        }
        return marks;
    }

    public Map<Long, String> getCurrentStaffMarks(List<User> staff, LocalDate date) {
        Map<Long, String> marks = new HashMap<>();
        for (User u : staff) {
            marks.put(u.getId(),
                    staffAttendanceRepository.findByStaffAndDate(u, date)
                            .map(sa -> sa.getStatus().name())
                            .orElse("present"));
        }
        return marks;
    }

    @Transactional
    public void saveStudentAttendance(LocalDate date, List<Long> studentIds, List<String> statuses, User user, boolean isTeacher) {
        Set<Long> allowed = isTeacher ? classStudentIds(user) : null;

        for (int i = 0; i < studentIds.size(); i++) {
            if (i >= statuses.size()) break;
            Long sid = studentIds.get(i);
            if (allowed != null && !allowed.contains(sid)) continue;
            Student student = studentRepository.findById(sid).orElse(null);
            if (student == null) continue;

            Attendance record = attendanceRepository.findByStudentAndDate(student, date)
                    .orElseGet(Attendance::new);
            record.setStudent(student);
            record.setDate(date);
            record.setStatus(AttendanceStatus.valueOf(statuses.get(i)));
            attendanceRepository.save(record);
        }
    }

    @Transactional
    public void saveStaffAttendance(LocalDate date, List<Long> staffIds, List<String> staffStatuses,
                                    List<Long> studentIds, List<String> studentStatuses) {
        if (staffIds != null) {
            for (int i = 0; i < staffIds.size(); i++) {
                if (i >= staffStatuses.size()) break;
                User staff = userRepository.findById(staffIds.get(i)).orElse(null);
                if (staff == null) continue;

                StaffAttendance record = staffAttendanceRepository.findByStaffAndDate(staff, date)
                        .orElseGet(StaffAttendance::new);
                record.setStaff(staff);
                record.setDate(date);
                record.setStatus(AttendanceStatus.valueOf(staffStatuses.get(i)));
                staffAttendanceRepository.save(record);
            }
        }
        if (studentIds != null) {
            for (int i = 0; i < studentIds.size(); i++) {
                if (i >= studentStatuses.size()) break;
                Student student = studentRepository.findById(studentIds.get(i)).orElse(null);
                if (student == null) continue;

                Attendance record = attendanceRepository.findByStudentAndDate(student, date)
                        .orElseGet(Attendance::new);
                record.setStudent(student);
                record.setDate(date);
                record.setStatus(AttendanceStatus.valueOf(studentStatuses.get(i)));
                attendanceRepository.save(record);
            }
        }
    }

    public boolean canEditAttendance(User user, boolean isTeacher, String type, Long id) {
        if (isTeacher) {
            if ("staff".equals(type)) return false;
            Attendance rec = attendanceRepository.findById(id).orElse(null);
            return rec != null && classStudentIds(user).contains(rec.getStudent().getId());
        }
        return true;
    }

    @Transactional
    public void editAttendance(Long id, String type, AttendanceStatus newStatus, String remarks) {
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
    }

    public List<Attendance> getStudentAttendance(Student student) {
        List<Attendance> records = attendanceRepository.findByStudent(student);
        records.sort(Comparator.comparing(Attendance::getDate).reversed());
        return records;
    }

    public Student getStudentAttendance(User user) {
        return studentRepository.findByUser(user).orElse(null);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public List<String> getClassNamesForFilter(User user, boolean isTeacher) {
        if (isTeacher) {
            return teacherClassNames(user);
        }
        return feeStructureRepository.findAllByOrderByClassNameAscTermAsc().stream()
                .map(FeeStructure::getClassName)
                .distinct()
                .toList();
    }

    public List<String> getStatusNames() {
        return Arrays.stream(AttendanceStatus.values())
                .map(AttendanceStatus::name)
                .collect(Collectors.toList());
    }

    public List<Student> getStudentsForRollCall(User user, boolean isTeacher) {
        return isTeacher ? studentsOfClass(user) : studentRepository.findAllByOrderByLastNameAsc();
    }

    public List<User> getAllStaff() {
        return userRepository.findByRole(Role.teacher);
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAllByOrderByLastNameAsc();
    }

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

    private List<String> teacherClassNames(User user) {
        if (user == null || user.getClassTeacherOf() == null || user.getClassTeacherOf().isBlank()) {
            return List.of();
        }
        return List.of(user.getClassTeacherOf().trim());
    }

    private Set<Long> classStudentIds(User user) {
        return studentsOfClass(user).stream().map(Student::getId).collect(Collectors.toSet());
    }
}