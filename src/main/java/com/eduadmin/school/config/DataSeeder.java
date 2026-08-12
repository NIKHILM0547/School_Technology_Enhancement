package com.eduadmin.school.config;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FeeRepository feeRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, StudentRepository studentRepository,
                       FeeRepository feeRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@school.test")) {
            userRepository.save(new User("School Admin", "admin@school.test",
                    passwordEncoder.encode("admin123"), Role.admin));
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

        if (!userRepository.existsByEmail("student@school.test")) {
            User student = new User("Aarav Sharma", "student@school.test",
                    passwordEncoder.encode("student123"), Role.student);
            student.setMobile("9876543211");
            userRepository.save(student);
            System.out.println("Seeded demo student login: student@school.test / student123");
        }

        if (studentRepository.count() == 0) {
            Student s1 = new Student();
            s1.setFirstName("Aarav"); s1.setLastName("Sharma"); s1.setAdmissionNo("S1001");
            s1.setClassName("6"); s1.setSection("A"); s1.setParentName("R. Sharma"); s1.setParentPhone("+911234567890");

            Student s2 = new Student();
            s2.setFirstName("Diya"); s2.setLastName("Verma"); s2.setAdmissionNo("S1002");
            s2.setClassName("6"); s2.setSection("A"); s2.setParentName("S. Verma"); s2.setParentPhone("+911234567891");

            Student s3 = new Student();
            s3.setFirstName("Kabir"); s3.setLastName("Singh"); s3.setAdmissionNo("S1003");
            s3.setClassName("7"); s3.setSection("B"); s3.setParentName("P. Singh"); s3.setParentPhone("+911234567892");

            studentRepository.saveAll(java.util.List.of(s1, s2, s3));

            for (Student s : studentRepository.findAll()) {
                Fee fee = new Fee();
                fee.setStudent(s);
                fee.setTerm("Term 1 2026");
                fee.setAmountDue(15000.0);
                fee.setAmountPaid(0.0);
                fee.setDueDate(LocalDate.of(2026, 8, 15));
                fee.recomputeStatus();
                feeRepository.save(fee);
            }
            System.out.println("Seeded sample students and fee records.");
        }

        // Link the demo student login to a student record so they can post reviews
        userRepository.findByEmail("student@school.test").ifPresent(u ->
                studentRepository.findByAdmissionNoIgnoreCase("S1001").ifPresent(s -> {
                    if (s.getUser() == null) {
                        s.setUser(u);
                        studentRepository.save(s);
                        System.out.println("Linked student@school.test to student S1001.");
                    }
                })
        );
    }
}
