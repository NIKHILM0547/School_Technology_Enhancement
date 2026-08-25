package com.eduadmin.school.service;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final FeeRepository feeRepository;
    private final ReviewRepository reviewRepository;
    private final AnnouncementRepository announcementRepository;

    public DashboardService(StudentRepository studentRepository,
                            UserRepository userRepository,
                            AttendanceRepository attendanceRepository,
                            StaffAttendanceRepository staffAttendanceRepository,
                            FeeRepository feeRepository,
                            ReviewRepository reviewRepository,
                            AnnouncementRepository announcementRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
        this.feeRepository = feeRepository;
        this.reviewRepository = reviewRepository;
        this.announcementRepository = announcementRepository;
    }

    public record StudentDashboardData(
            Student student,
            List<Fee> myFees,
            List<Fee> pendingFees,
            double remaining,
            long overdueCount,
            long upcomingCount,
            long fullyPaidCount,
            List<Attendance> myAttendance,
            int attendanceRate,
            long presentCount,
            long absentCount,
            long lateCount,
            List<Review> myReviews,
            List<Announcement> recentAnnouncements
    ) {}

    public record AdminDashboardData(
            long totalStudents,
            long totalTeachers,
            long presentStudents,
            long absentStudents,
            long lateStudents,
            int attendanceRate,
            long staffPresent,
            List<String> absentToday,
            double totalCollected,
            double outstanding,
            long overdueCount,
            List<Fee> pendingFees,
            List<Announcement> recentAnnouncements
    ) {}

    public record TeacherDashboardData(
            User teacher,
            String className,
            long totalStudents,
            int attendanceRate,
            long presentToday,
            long absentToday,
            long lateToday,
            List<String> absentNames,
            int markedToday,
            List<Announcement> recentAnnouncements
    ) {}

    public StudentDashboardData getStudentDashboardData(User user, LocalDate today) {
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            return new StudentDashboardData(null, List.of(), List.of(), 0, 0, 0, 0,
                    List.of(), 0, 0, 0, 0, List.of(), List.of());
        }

        List<Fee> myFees = feeRepository.findByStudent(student);
        List<Fee> pendingFees = myFees.stream()
                .filter(f -> f.getOutstanding() > 0)
                .filter(f -> f.getDueDate() == null || !f.getDueDate().isAfter(today))
                .sorted(Comparator.comparing(Fee::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        double remaining = pendingFees.stream().mapToDouble(Fee::getOutstanding).sum();
        long overdueCount = myFees.stream()
                .filter(f -> f.getOutstanding() > 0 && f.getDueDate() != null && f.getDueDate().isBefore(today))
                .count();
        long upcomingCount = myFees.stream()
                .filter(f -> f.getOutstanding() > 0 && f.getDueDate() != null && f.getDueDate().isAfter(today))
                .count();
        long fullyPaidCount = myFees.stream().filter(f -> f.getOutstanding() <= 0).count();

        List<Attendance> myAttendance = attendanceRepository.findByStudent(student);
        myAttendance.sort(Comparator.comparing(Attendance::getDate).reversed());
        long presentCount = count(myAttendance, AttendanceStatus.present);
        long absentCount = count(myAttendance, AttendanceStatus.absent);
        long lateCount = count(myAttendance, AttendanceStatus.late);
        long markedDays = myAttendance.size();
        int attendanceRate = markedDays == 0 ? 0
                : (int) Math.round(100.0 * presentCount / markedDays);

        List<Review> myReviews = reviewRepository.findByStudentOrderByCreatedAtDesc(student);
        List<Announcement> recentAnnouncements = announcementRepository.findTop5ByOrderByCreatedAtDesc();

        return new StudentDashboardData(student, myFees, pendingFees, remaining, overdueCount,
                upcomingCount, fullyPaidCount, myAttendance, attendanceRate,
                presentCount, absentCount, lateCount, myReviews, recentAnnouncements);
    }

    public AdminDashboardData getAdminDashboardData(LocalDate today) {
        long totalStudents = studentRepository.count();
        long totalTeachers = userRepository.countByRole(Role.teacher);

        List<Attendance> todayAttendance = attendanceRepository.findByDate(today);
        long presentStudents = count(todayAttendance, AttendanceStatus.present);
        long absentStudents = count(todayAttendance, AttendanceStatus.absent);
        long lateStudents = count(todayAttendance, AttendanceStatus.late);
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

        List<Announcement> recentAnnouncements = announcementRepository.findTop5ByOrderByCreatedAtDesc();

        return new AdminDashboardData(totalStudents, totalTeachers, presentStudents, absentStudents,
                lateStudents, attendanceRate, staffPresent, absentToday, totalCollected,
                outstanding, overdueCount, pendingFees, recentAnnouncements);
    }

    public TeacherDashboardData getTeacherDashboardData(User user, LocalDate today) {
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

        long presentToday = 0, absentToday = 0, lateToday = 0;
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
                default -> { }
            }
        }
        int attendanceRate = markedToday == 0 ? 0 : (int) Math.round(100.0 * presentToday / markedToday);

        List<Announcement> recentAnnouncements = announcementRepository.findTop5ByOrderByCreatedAtDesc();

        return new TeacherDashboardData(user, classTeacherOf != null && !classTeacherOf.isBlank() ? classTeacherOf : "—",
                (long) students.size(), attendanceRate, presentToday, absentToday, lateToday,
                absentNames, markedToday, recentAnnouncements);
    }

    private long count(List<Attendance> records, AttendanceStatus status) {
        return records.stream().filter(a -> a.getStatus() == status).count();
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}