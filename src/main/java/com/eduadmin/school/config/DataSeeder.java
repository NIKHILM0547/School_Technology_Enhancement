package com.eduadmin.school.config;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final List<String> SUBJECTS = List.of(
            "Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science");

    /** All classes the school offers (grades 1-12, sections A-C). */
    private static final List<String> CLASSES = List.of(
            "1-A", "1-B", "1-C", "2-A", "2-B", "2-C",
            "3-A", "3-B", "3-C", "4-A", "4-B", "4-C",
            "5-A", "5-B", "5-C", "6-A", "6-B", "6-C",
            "7-A", "7-B", "7-C", "8-A", "8-B", "8-C",
            "9-A", "9-B", "9-C", "10-A", "10-B", "10-C",
            "11-A", "11-B", "11-C", "12-A", "12-B", "12-C"
    );

    private static final String[] FIRST_NAMES = {
            "Aarav", "Diya", "Kabir", "Ishaan", "Riya", "Vihaan", "Ananya", "Arjun",
            "Sara", "Advait", "Myra", "Dev", "Nisha", "Rohan", "Tanya", "Aman",
            "Kavya", "Rahul", "Priya", "Nikhil", "Sneha", "Varun", "Pooja", "Aditya",
            "Meera", "Karan", "Isha", "Manav", "Divya", "Rajat", "Simran", "Yash",
            "Avni", "Om", "Tara", "Harsh", "Kiara", "Rudra", "Ira", "Veer"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Verma", "Singh", "Mehta", "Kapoor", "Rao", "Iyer", "Nair",
            "Khan", "Joshi", "Patel", "Chauhan", "Gupta", "Das", "Sen", "Reddy",
            "Pillai", "Bose", "Mishra", "Tripathi", "Agarwal", "Chopra", "Kulkarni", "Desai",
            "Naik", "Shetty", "Menon", "Bhat", "Malhotra", "Saxena", "Gandhi", "Kohli",
            "Bhandari", "Sethi", "Gill", "Chadha", "Sood", "Bakshi", "Goyal", "Ahuja"
    };

    /** One class teacher per class (indexed by CLASSES index); 6-A uses the demo
     *  teacher@school.test login, so it is skipped when assigning. */
    private static final String[] TEACHER_NAMES = {
            "Sunita Rao", "Amit Joshi", "Preeti Nair", "Vikram Mehta", "Kavita Singh", "Rakesh Iyer",
            "Neha Kapoor", "Sanjay Das", "Pooja Sharma", "Arvind Patel", "Divya Menon", "Manoj Gupta",
            "Ritu Verma", "Suresh Reddy", "Anjali Kulkarni", "Deepak Desai", "Lakshmi Pillai", "Nitin Bose",
            "Shweta Mishra", "Rajesh Malhotra", "Kiran Agarwal", "Pradeep Tripathi", "Sangeeta Chopra", "Vinod Shetty",
            "Alka Bhat", "Harish Saxena", "Meenakshi Naik", "Gaurav Chauhan", "Tanvi Rao", "Rahul Sen",
            "Jyoti Nair", "Ashok Khan", "Bindu Pillai", "Sameer Gupta", "Geeta Iyer", "Naveen Das"
    };

    /** One subject-specialist teacher per subject, covering all classes. */
    private static final String[][] SUBJECT_TEACHERS = {
            {"Mathematics", "Prof. Meera Raman"},
            {"English", "Ms. Anita D'Souza"},
            {"Hindi", "Shri Ramesh Tiwari"},
            {"Science", "Dr. Kavitha Krishnan"},
            {"Social Studies", "Mr. Farhan Qureshi"},
            {"Computer Science", "Mr. Anil Nene"}
    };

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FeeRepository feeRepository;
    private final MarkRepository markRepository;
    private final AttendanceRepository attendanceRepository;
    private final ReviewRepository reviewRepository;
    private final ClassTeacherRemarkRepository remarkRepository;
    private final LeaveRequestRepository leaveRepository;
    private final PaymentRepository paymentRepository;
    private final AnnouncementRepository announcementRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, StudentRepository studentRepository,
                      FeeRepository feeRepository, MarkRepository markRepository,
                      AttendanceRepository attendanceRepository, ReviewRepository reviewRepository,
                      ClassTeacherRemarkRepository remarkRepository,
                      LeaveRequestRepository leaveRepository, PaymentRepository paymentRepository,
                      AnnouncementRepository announcementRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.markRepository = markRepository;
        this.attendanceRepository = attendanceRepository;
        this.reviewRepository = reviewRepository;
        this.remarkRepository = remarkRepository;
        this.leaveRepository = leaveRepository;
        this.paymentRepository = paymentRepository;
        this.announcementRepository = announcementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedTeachers();
        ensureFeaturedStudents();
        ensureStudentDataset();
        seedStudentAccounts();
        seedMissingFees();
        seedMissingMarks();
        seedMissingAttendance();
        seedReviews();
        seedClassTeacherRemarks();
        seedDemoLeaveRequests();
        seedAnnouncements();
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

    /** Generates one class teacher per class (except 6-A, covered by the demo
     *  login) plus one subject-specialist teacher per subject. All use the
     *  password teacher123. Idempotent per email. */
    private void seedTeachers() {
        for (int c = 0; c < CLASSES.size(); c++) {
            String cls = CLASSES.get(c);
            if (cls.equals("6-A")) continue;
            String slug = cls.toLowerCase().replace("-", "");
            String email = "ct." + slug + "@school.test";
            if (userRepository.existsByEmail(email)) continue;
            String subj1 = SUBJECTS.get((c * 2) % SUBJECTS.size());
            String subj2 = SUBJECTS.get((c * 2 + 1) % SUBJECTS.size());
            User t = new User(TEACHER_NAMES[c], email, passwordEncoder.encode("teacher123"), Role.teacher);
            t.setSubject(subj1 + ", " + subj2);
            t.setAssignedClasses(assignedFor(cls, c));
            t.setClassTeacherOf(cls);
            t.setMobile(String.valueOf(9123456000L + c));
            userRepository.save(t);
        }
        for (int i = 0; i < SUBJECT_TEACHERS.length; i++) {
            String subject = SUBJECT_TEACHERS[i][0];
            String slug = subject.toLowerCase().replace(" ", "").replace("-", "");
            String email = "subject." + slug + "@school.test";
            if (userRepository.existsByEmail(email)) continue;
            User t = new User(SUBJECT_TEACHERS[i][1], email,
                    passwordEncoder.encode("teacher123"), Role.teacher);
            t.setSubject(subject);
            t.setAssignedClasses(String.join(", ", CLASSES));
            t.setMobile(String.valueOf(9123457000L + i));
            userRepository.save(t);
        }
        System.out.println("Seeded teachers for all classes (password: teacher123).");
    }

    /** The class teacher's own class plus its neighbours, so marks entry covers
     *  a small set of classes per teacher. */
    private String assignedFor(String cls, int index) {
        List<String> set = new ArrayList<>();
        set.add(cls);
        if (index > 0) set.add(CLASSES.get(index - 1));
        if (index < CLASSES.size() - 1) set.add(CLASSES.get(index + 1));
        return String.join(", ", set);
    }

    private void seedStudentUser(String email, String name, String mobile) {
        if (userRepository.existsByEmail(email)) return;
        User user = new User(name, email, passwordEncoder.encode("student123"), Role.student);
        user.setMobile(mobile);
        userRepository.save(user);
        System.out.println("Seeded demo student login: " + email + " / student123");
    }

    /** The three featured demo students keep fixed admission numbers S1001-S1003
     *  (used by the fixed demo logins and the demo leave request). */
    private void ensureFeaturedStudents() {
        ensureStudent("S1001", "Aarav", "Sharma", "6", "A", "R. Sharma", "+911234567890");
        ensureStudent("S1002", "Diya", "Verma", "6", "A", "S. Verma", "+911234567891");
        ensureStudent("S1003", "Kabir", "Singh", "7", "B", "P. Singh", "+911234567892");
    }

    private void ensureStudent(String admissionNo, String first, String last, String className,
                               String section, String parentName, String parentPhone) {
        if (studentRepository.findByAdmissionNoIgnoreCase(admissionNo).isPresent()) return;
        Student s = new Student();
        s.setFirstName(first);
        s.setLastName(last);
        s.setClassName(className);
        s.setSection(section);
        s.setAdmissionNo(admissionNo);
        s.setParentName(parentName);
        s.setParentPhone(parentPhone);
        s.setSubjects(String.join(", ", SUBJECTS));
        studentRepository.save(s);
    }

    /** Ensures every class (1-A .. 12-C) has 5-10 students, adding more whenever
     *  a class is below its target. Names are generated so that no two students
     *  in the whole school share the same full name. Idempotent. */
    private void ensureStudentDataset() {
        int added = 0;
        Set<String> used = new HashSet<>();
        for (Student s : studentRepository.findAll()) {
            used.add(s.getFirstName() + "|" + s.getLastName());
        }
        int g = 0;
        for (int c = 0; c < CLASSES.size(); c++) {
            String[] parts = CLASSES.get(c).split("-");
            List<Student> existing = studentRepository
                    .findByClassNameAndSectionOrderByLastNameAsc(parts[0], parts[1]);
            int target = 5 + (c % 6); // 5..10 per class, deterministic
            int idx = (int) studentRepository.count() + added;
            while (existing.size() < target) {
                String first;
                String last;
                do {
                    first = FIRST_NAMES[g % FIRST_NAMES.length];
                    last = LAST_NAMES[(g / FIRST_NAMES.length) % LAST_NAMES.length];
                    g++;
                } while (used.contains(first + "|" + last));
                used.add(first + "|" + last);
                Student s = new Student();
                s.setFirstName(first);
                s.setLastName(last);
                s.setClassName(parts[0]);
                s.setSection(parts[1]);
                s.setAdmissionNo(nextAdmissionNo());
                s.setParentName(FIRST_NAMES[(idx + 5) % FIRST_NAMES.length].charAt(0) + ". " + last);
                s.setParentPhone("+91" + (9123456700L + idx));
                s.setSubjects(String.join(", ", SUBJECTS));
                studentRepository.save(s);
                existing.add(s);
                added++;
                idx++;
            }
        }
        if (added > 0) {
            System.out.println("Seeded " + added + " students across " + CLASSES.size()
                    + " classes (5-10 per class, unique names).");
        }
    }

    private String nextAdmissionNo() {
        int max = 1003;
        for (String a : studentRepository.findAllAdmissionNos()) {
            String digits = a.replaceAll("\\D", "");
            if (!digits.isEmpty()) max = Math.max(max, Integer.parseInt(digits));
        }
        return "S" + (max + 1);
    }

    /** Creates a login (Role.student, password student123) for every student that
     *  does not have one yet, links it to the student record, and keeps the
     *  account's assignedClasses in sync with the student's class so the Users
     *  page can display it. The three featured students (S1001-S1003) keep their
     *  fixed demo logins. Idempotent. */
    private void seedStudentAccounts() {
        int created = 0;
        int synced = 0;
        for (Student s : studentRepository.findAll()) {
            if (s.getUser() == null) {
                User u = demoStudentLogin(s.getAdmissionNo());
                if (u == null) {
                    String email = "student." + s.getAdmissionNo().toLowerCase() + "@school.test";
                    if (userRepository.existsByEmail(email)) continue;
                    u = new User(s.getFullName(), email,
                            passwordEncoder.encode("student123"), Role.student);
                    u.setMobile(mobileFor(s.getAdmissionNo()));
                    u.setSubjects(s.getSubjects());
                    userRepository.save(u);
                }
                s.setUser(u);
                studentRepository.save(s);
                created++;
            }
            if (s.getUser() != null) {
                User linked = s.getUser();
                String cls = s.getClassDisplay();
                if (linked.getAssignedClasses() == null || linked.getAssignedClasses().isBlank()
                        || !cls.equals(linked.getAssignedClasses().trim())) {
                    linked.setAssignedClasses(cls);
                    userRepository.save(linked);
                    synced++;
                }
            }
        }
        if (created > 0 || synced > 0) {
            System.out.println("Linked/synced student logins for " + created + "/" + synced
                    + " students (password: student123).");
        }
    }

    /** Returns the fixed demo login for the featured students, or null. */
    private User demoStudentLogin(String admissionNo) {
        String email = switch (admissionNo) {
            case "S1001" -> "student@school.test";
            case "S1002" -> "diya.verma@school.test";
            case "S1003" -> "kabir.singh@school.test";
            default -> null;
        };
        return email == null ? null : userRepository.findByEmail(email).orElse(null);
    }

    /** 10-digit mobile starting with 9, derived from the admission number. */
    private String mobileFor(String admissionNo) {
        String digits = admissionNo.replaceAll("\\D", "");
        long n = digits.isEmpty() ? 1001 : Long.parseLong(digits);
        return String.valueOf(9123000000L + n);
    }

    /** Two fee terms per student for any student missing fee records. */
    private void seedMissingFees() {
        int count = 0;
        for (Student s : studentRepository.findAll()) {
            if (!feeRepository.findByStudent(s).isEmpty()) continue;
            saveFee(s, "Term 1 2026", 15000.0, 7000.0, LocalDate.of(2026, 8, 15));
            saveFee(s, "Term 2 2026", 17000.0, 0.0, LocalDate.of(2026, 12, 15));
            count++;
        }
        if (count > 0) {
            System.out.println("Seeded fees for " + count + " students.");
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

    /** Marks for 2 terms x 6 subjects for any student missing marks. Deterministic. */
    private void seedMissingMarks() {
        List<String> terms = List.of("Term 1 2026", "Term 2 2026");
        List<Mark> toSave = new ArrayList<>();
        int count = 0;
        for (Student student : studentRepository.findAll()) {
            if (!markRepository.findByStudentOrderByTermSubject(student).isEmpty()) continue;
            for (int t = 0; t < terms.size(); t++) {
                for (int j = 0; j < SUBJECTS.size(); j++) {
                    Mark mark = new Mark();
                    mark.setStudent(student);
                    mark.setSubject(SUBJECTS.get(j));
                    mark.setTerm(terms.get(t));
                    mark.setMaxMarks(100.0);
                    mark.setMarksObtained(35.0 + ((j * 11 + t * 5) % 61));
                    toSave.add(mark);
                }
            }
            count++;
        }
        if (!toSave.isEmpty()) {
            markRepository.saveAll(toSave);
            System.out.println("Seeded marks (2 terms) for " + count + " students.");
        }
    }

    /** Attendance for the recent weekdays (including today) for any student missing records. */
    private void seedMissingAttendance() {
        List<LocalDate> weekdays = lastWeekdays(20);
        int count = 0;
        for (Student student : studentRepository.findAll()) {
            Set<LocalDate> existing = attendanceRepository.findByStudent(student).stream()
                    .map(Attendance::getDate)
                    .collect(Collectors.toSet());
            List<Attendance> records = new ArrayList<>();
            long hash = (student.getId() != null ? student.getId() : 0L) * 2654435761L;
            for (int d = 0; d < weekdays.size(); d++) {
                LocalDate day = weekdays.get(d);
                if (existing.contains(day)) continue;
                AttendanceStatus status = AttendanceStatus.present;
                int roll = (int) ((hash ^ ((long) d * 97)) & 0x7fffffff) % 100;
                if (roll < 5) {
                    status = AttendanceStatus.absent;
                } else if (roll < 8) {
                    status = AttendanceStatus.late;
                }
                Attendance record = new Attendance();
                record.setStudent(student);
                record.setDate(day);
                record.setStatus(status);
                records.add(record);
            }
            if (!records.isEmpty()) {
                attendanceRepository.saveAll(records);
                count++;
            }
        }
        if (count > 0) {
            System.out.println("Seeded attendance for " + count + " students.");
        }
    }

    /** The most recent Mon-Fri days, starting from today. */
    private List<LocalDate> lastWeekdays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = LocalDate.now();
        while (days.size() < count) {
            java.time.DayOfWeek dow = day.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                days.add(day);
            }
            day = day.minusDays(1);
        }
        return days;
    }

    private void seedReviews() {
        if (reviewRepository.count() > 0) return;
        String[][] reviews = {
                {"Science lab improvement", "It would be great if the science lab had more microscopes for us to use in pairs."},
                {"Sports day was fantastic!", "Really enjoyed the sports day this year. Please keep more such events coming."},
                {"Library needs more books", "The library could use more fiction books and a longer reading period."},
                {"Canteen food suggestion", "Would love more healthy snack options in the canteen."}
        };
        List<Student> students = studentRepository.findAll();
        List<Review> list = new ArrayList<>();
        for (int i = 0; i < reviews.length && i < students.size(); i++) {
            list.add(new Review(students.get(i), reviews[i][0], reviews[i][1]));
        }
        reviewRepository.saveAll(list);
    }

    /** Sample class-teacher remarks for the demo 6-A class by the demo teacher
     *  (class teacher of 6-A), so report cards show a remark out of the box. */
    private void seedClassTeacherRemarks() {
        if (remarkRepository.count() > 0) return;
        userRepository.findByEmail("teacher@school.test").ifPresent(teacher -> {
            List<Student> students = studentRepository
                    .findByClassNameAndSectionOrderByLastNameAsc("6", "A");
            if (students.isEmpty()) return;
            String[] texts = {
                    "A bright student who participates well in class. Keep up the good work!",
                    "Good performance. Needs to work on consistency in homework.",
                    "Shows great improvement this term. Well done!"
            };
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < texts.length && i < students.size(); i++) {
                ClassTeacherRemark remark = new ClassTeacherRemark();
                remark.setStudent(students.get(i));
                remark.setRemark(texts[i]);
                remark.setCreatedBy(teacher);
                remark.setUpdatedBy(teacher);
                remark.setCreatedAt(now.minusDays(i));
                remark.setUpdatedAt(now.minusDays(i));
                remarkRepository.save(remark);
            }
            System.out.println("Seeded class teacher remarks for 6-A students.");
        });
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

    /** Sample announcements posted by the demo admin so all roles see the
     *  announcements feature out of the box. Idempotent: only seeds when empty. */
    private void seedAnnouncements() {
        if (announcementRepository.count() > 0) return;
        userRepository.findByEmail("admin@school.test").ifPresent(admin -> {
            String[][] items = {
                    {"Welcome to the new academic year!",
                     "Welcome back to all students, teachers and parents. Classes begin today at 8:00 AM. Please ensure all students carry their diaries and ID cards."},
                    {"Annual Sports Day update",
                     "The Annual Sports Day has been scheduled for next month. Class teachers are requested to share the list of participating students with the sports department by Friday."},
                    {"Parent-teacher meeting",
                     "A parent-teacher meeting will be held this Saturday from 9:00 AM to 12:00 PM. Teachers are requested to be present in their respective classrooms."}
            };
            LocalDateTime now = LocalDateTime.now();
            List<Announcement> list = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                Announcement a = new Announcement(items[i][0], items[i][1], admin);
                a.setCreatedAt(now.minusDays(i * 2));
                list.add(a);
            }
            announcementRepository.saveAll(list);
            System.out.println("Seeded demo announcements.");
        });
    }
}