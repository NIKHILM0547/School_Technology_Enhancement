package com.eduadmin.school.config;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final List<String> SUBJECTS = List.of(
            "Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science");

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FeeRepository feeRepository;
    private final MarkRepository markRepository;
    private final AttendanceRepository attendanceRepository;
    private final ReviewRepository reviewRepository;
    private final LeaveRequestRepository leaveRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, StudentRepository studentRepository,
                      FeeRepository feeRepository, MarkRepository markRepository,
                      AttendanceRepository attendanceRepository, ReviewRepository reviewRepository,
                      LeaveRequestRepository leaveRepository, PaymentRepository paymentRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.markRepository = markRepository;
        this.attendanceRepository = attendanceRepository;
        this.reviewRepository = reviewRepository;
        this.leaveRepository = leaveRepository;
        this.paymentRepository = paymentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        if (studentRepository.count() == 0) {
            seedDemoDataset();
        }
        linkDemoStudentLogins();
        seedMissingMarks();
        seedDemoLeaveRequests();
    }

    private void seedUsers() {
        if (!userRepository.existsByEmail("admin@school.test")) {
            User admin = new User("School Admin", "admin@school.test",
                    passwordEncoder.encode("admin123"), Role.admin);
            admin.setMobile("9123456789");
            userRepository.save(admin);
            System.out.println("Seeded demo admin login: admin@school.test / admin123");
        }

        if (!userRepository.existsByEmail("teacher@school.test")) {
            User teacher = new User("Rajesh Kumar", "teacher@school.test",
                    passwordEncoder.encode("teacher123"), Role.teacher);
            teacher.setSubject("Mathematics, Science");
            teacher.setAssignedClasses("6-A, 6-B, 7-A");
            teacher.setClassTeacherOf("6-A");
            teacher.setMobile("9876543210");
            userRepository.save(teacher);
            System.out.println("Seeded demo teacher login: teacher@school.test / teacher123");
        }

        seedStudentUser("student@school.test", "Aarav Sharma", "9876543211");
        seedStudentUser("diya.verma@school.test", "Diya Verma", "9876543212");
        seedStudentUser("kabir.singh@school.test", "Kabir Singh", "9876543213");
    }

    private void seedStudentUser(String email, String name, String mobile) {
        if (userRepository.existsByEmail(email)) return;
        User user = new User(name, email, passwordEncoder.encode("student123"), Role.student);
        user.setMobile(mobile);
        userRepository.save(user);
        System.out.println("Seeded demo student login: " + email + " / student123");
    }

    private void seedDemoDataset() {
        List<Student> students = demoStudents();
        studentRepository.saveAll(students);

        seedFees(students);
        seedMarks(students);
        seedAttendance(students);
        seedReviews(students);
        System.out.println("Seeded demo dataset: " + students.size()
                + " students with fees, marks, attendance and reviews.");
    }

    /** 15 demo students across 6-A, 6-B, 7-A, 7-B and 8-A. */
    private List<Student> demoStudents() {
        String[][] data = {
                {"Aarav", "Sharma", "6", "A", "S1001", "R. Sharma", "+911234567890"},
                {"Diya", "Verma", "6", "A", "S1002", "S. Verma", "+911234567891"},
                {"Kabir", "Singh", "7", "B", "S1003", "P. Singh", "+911234567892"},
                {"Ishaan", "Mehta", "6", "A", "S1004", "A. Mehta", "+911234567893"},
                {"Riya", "Kapoor", "6", "A", "S1005", "K. Kapoor", "+911234567894"},
                {"Vihaan", "Rao", "6", "B", "S1006", "V. Rao", "+911234567895"},
                {"Ananya", "Iyer", "6", "B", "S1007", "A. Iyer", "+911234567896"},
                {"Arjun", "Nair", "6", "B", "S1008", "R. Nair", "+911234567897"},
                {"Sara", "Khan", "7", "A", "S1009", "F. Khan", "+911234567898"},
                {"Advait", "Joshi", "7", "A", "S1010", "D. Joshi", "+911234567899"},
                {"Myra", "Patel", "7", "B", "S1011", "M. Patel", "+911234567800"},
                {"Dev", "Chauhan", "7", "B", "S1012", "N. Chauhan", "+911234567801"},
                {"Nisha", "Gupta", "7", "B", "S1013", "V. Gupta", "+911234567802"},
                {"Rohan", "Das", "8", "A", "S1014", "S. Das", "+911234567803"},
                {"Tanya", "Sen", "8", "A", "S1015", "A. Sen", "+911234567804"}
        };
        List<Student> students = new ArrayList<>();
        for (String[] d : data) {
            Student s = new Student();
            s.setFirstName(d[0]);
            s.setLastName(d[1]);
            s.setClassName(d[2]);
            s.setSection(d[3]);
            s.setAdmissionNo(d[4]);
            s.setParentName(d[5]);
            s.setParentPhone(d[6]);
            s.setSubjects(String.join(", ", SUBJECTS));
            students.add(s);
        }
        return students;
    }

    /** Two fee terms per student with varied payment statuses (paid / partial / overdue). */
    private void seedFees(List<Student> students) {
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            double term1Paid;
            double term2Paid;
            switch (i % 3) {
                case 0 -> { term1Paid = 15000; term2Paid = 5000; }
                case 1 -> { term1Paid = 7000; term2Paid = 0; }
                default -> { term1Paid = 0; term2Paid = 0; }
            }
            saveFee(student, "Term 1 2026", 15000.0, term1Paid, LocalDate.of(2026, 8, 15));
            saveFee(student, "Term 2 2026", 17000.0, term2Paid, LocalDate.of(2026, 12, 15));
        }
    }

    private void saveFee(Student student, String term, double amountDue, double amountPaid, LocalDate dueDate) {
        Fee fee = new Fee();
        fee.setStudent(student);
        fee.setTerm(term);
        fee.setAmountDue(amountDue);
        fee.setAmountPaid(amountPaid);
        fee.setDueDate(dueDate);
        fee.recomputeStatus();
        feeRepository.save(fee);
        if (amountPaid > 0) {
            paymentRepository.save(new Payment(fee, amountPaid));
        }
    }

    /** Marks for 2 terms x 6 subjects per student, generated deterministically. */
    private void seedMarks(List<Student> students) {
        List<String> terms = List.of("Term 1 2026", "Term 2 2026");
        List<Mark> marks = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            for (int t = 0; t < terms.size(); t++) {
                for (int j = 0; j < SUBJECTS.size(); j++) {
                    Mark mark = new Mark();
                    mark.setStudent(students.get(i));
                    mark.setTerm(terms.get(t));
                    mark.setSubject(SUBJECTS.get(j));
                    mark.setMaxMarks(100.0);
                    mark.setMarksObtained(55.0 + ((i * 7 + j * 11 + t * 5) % 41));
                    marks.add(mark);
                }
            }
        }
        markRepository.saveAll(marks);
    }

    /** Attendance for the last 20 weekdays, mostly present with a few absent/late. */
    private void seedAttendance(List<Student> students) {
        List<LocalDate> weekdays = lastWeekdays(20);
        List<Attendance> records = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            for (int d = 0; d < weekdays.size(); d++) {
                AttendanceStatus status = AttendanceStatus.present;
                if ((i * 3 + d * 5) % 13 == 0) {
                    status = AttendanceStatus.absent;
                } else if ((i * 7 + d) % 9 == 0) {
                    status = AttendanceStatus.late;
                }
                Attendance record = new Attendance();
                record.setStudent(student);
                record.setDate(weekdays.get(d));
                record.setStatus(status);
                records.add(record);
            }
        }
        attendanceRepository.saveAll(records);
    }

    /** The most recent Mon-Fri days before today. */
    private List<LocalDate> lastWeekdays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = LocalDate.now().minusDays(1);
        while (days.size() < count) {
            java.time.DayOfWeek dow = day.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                days.add(day);
            }
            day = day.minusDays(1);
        }
        return days;
    }

    private void seedReviews(List<Student> students) {
        if (reviewRepository.count() > 0) return;
        String[][] reviews = {
                {"Science lab improvement", "It would be great if the science lab had more microscopes for us to use in pairs."},
                {"Sports day was fantastic!", "Really enjoyed the sports day this year. Please keep more such events coming."},
                {"Library needs more books", "The library could use more fiction books and a longer reading period."},
                {"Canteen food suggestion", "Would love more healthy snack options in the canteen."}
        };
        List<Review> list = new ArrayList<>();
        for (int i = 0; i < reviews.length && i < students.size(); i++) {
            list.add(new Review(students.get(i), reviews[i][0], reviews[i][1]));
        }
        reviewRepository.saveAll(list);
    }

    private void seedDemoLeaveRequests() {
        if (leaveRepository.count() > 0) return;
        userRepository.findByEmail("student@school.test").ifPresent(studentUser ->
                studentRepository.findByAdmissionNoIgnoreCase("S1001").ifPresent(s -> {
                    LeaveRequest request = new LeaveRequest();
                    request.setApplicant(studentUser);
                    request.setStudent(s);
                    request.setFromDate(LocalDate.now().plusDays(2));
                    request.setToDate(LocalDate.now().plusDays(3));
                    request.setReason("Family function; need leave for two days.");
                    request.setStatus(LeaveStatus.pending);
                    request.setAppliedAt(LocalDateTime.now());
                    leaveRepository.save(request);
                })
        );
        userRepository.findByEmail("teacher@school.test").ifPresent(teacherUser -> {
            LeaveRequest request = new LeaveRequest();
            request.setApplicant(teacherUser);
            request.setFromDate(LocalDate.now().plusDays(5));
            request.setToDate(LocalDate.now().plusDays(5));
            request.setReason("Medical appointment.");
            request.setStatus(LeaveStatus.pending);
            request.setAppliedAt(LocalDateTime.now());
            leaveRepository.save(request);
        });
    }

    /** Links demo student logins to their student records (only if not linked yet). */
    private void linkDemoStudentLogins() {
        String[][] links = {
                {"student@school.test", "S1001"},
                {"diya.verma@school.test", "S1002"},
                {"kabir.singh@school.test", "S1003"}
        };
        for (String[] pair : links) {
            userRepository.findByEmail(pair[0]).ifPresent(u ->
                    studentRepository.findByAdmissionNoIgnoreCase(pair[1]).ifPresent(s -> {
                        if (s.getUser() == null) {
                            s.setUser(u);
                            studentRepository.save(s);
                        }
                    })
            );
        }
    }

    /** Idempotent: any student with no marks at all gets dummy marks for
     *  Term 1 2026, so report cards show data even on databases seeded before
     *  marks existed. Students with marks are left untouched. */
    private void seedMissingMarks() {
        String term = "Term 1 2026";
        List<Mark> toSave = new ArrayList<>();
        for (Student student : studentRepository.findAll()) {
            if (!markRepository.findByStudentOrderByTermSubject(student).isEmpty()) {
                continue;
            }
            for (int j = 0; j < SUBJECTS.size(); j++) {
                Mark mark = new Mark();
                mark.setStudent(student);
                mark.setSubject(SUBJECTS.get(j));
                mark.setTerm(term);
                mark.setMaxMarks(100.0);
                mark.setMarksObtained(55.0 + ((j * 11) % 41));
                toSave.add(mark);
            }
        }
        if (!toSave.isEmpty()) {
            markRepository.saveAll(toSave);
            System.out.println("Seeded dummy marks for " + (toSave.size() / SUBJECTS.size())
                    + " students (" + term + ").");
        }
    }
}
