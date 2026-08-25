package com.eduadmin.school.service;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import com.eduadmin.school.service.SmsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final List<String> SUBJECTS = List.of(
            "Mathematics", "Science", "English", "Hindi", "Social Studies",
            "Physics", "Chemistry", "Biology", "Computer Science", "History",
            "Geography", "Sanskrit", "Physical Education", "Art", "Music"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");

    private static final List<String> CLASSES = List.of(
            "1-A", "1-B", "1-C", "2-A", "2-B", "2-C",
            "3-A", "3-B", "3-C", "4-A", "4-B", "4-C",
            "5-A", "5-B", "5-C", "6-A", "6-B", "6-C",
            "7-A", "7-B", "7-C", "8-A", "8-B", "8-C",
            "9-A", "9-B", "9-C", "10-A", "10-B", "10-C",
            "11-A", "11-B", "11-C", "12-A", "12-B", "12-C"
    );

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    public UserService(UserRepository userRepository, StudentRepository studentRepository,
                       PasswordEncoder passwordEncoder, SmsService smsService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
    }

    public List<User> getUsersForList(User current, String role, String classFilter, String name) {
        boolean classTeacher = isClassTeacher(current);
        String teacherClass = classTeacher ? current.getClassTeacherOf() : null;
        String nameQuery = (name != null && !name.isBlank()) ? name.trim() : "";

        if (classTeacher) {
            List<User> users = userRepository.findByRoleAndAssignedClasses(Role.student, teacherClass);
            users = users.stream()
                    .filter(u -> nameQuery.isBlank() || u.getName().toLowerCase().contains(nameQuery.toLowerCase()))
                    .toList();
            Map<Long, String> admissionByUser = new HashMap<>();
            for (Student s : studentRepository.findAll()) {
                if (s.getUser() != null) admissionByUser.put(s.getUser().getId(), s.getAdmissionNo());
            }
            for (User u : users) u.setAdmissionNo(admissionByUser.get(u.getId()));
            return users;
        }

        boolean anyRole = role == null || role.isBlank();
        Role selected = null;
        if (!anyRole) {
            try {
                selected = Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                anyRole = true;
                role = null;
            }
        }
        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        return userRepository.search(anyRole, selected, classQuery, nameQuery);
    }

    public void enrichUsersWithAdmission(List<User> users) {
        Map<Long, String> admissionByUser = new HashMap<>();
        for (Student s : studentRepository.findAll()) {
            if (s.getUser() != null) admissionByUser.put(s.getUser().getId(), s.getAdmissionNo());
        }
        for (User u : users) u.setAdmissionNo(admissionByUser.get(u.getId()));
    }

    public Map<String, String> getClassTeacherNames() {
        Map<String, String> names = new HashMap<>();
        for (User t : userRepository.findByRole(Role.teacher)) {
            if (t.getClassTeacherOf() != null && !t.getClassTeacherOf().isBlank()) {
                names.put(t.getClassTeacherOf().trim(), t.getName());
            }
        }
        return names;
    }

    public List<String> getSubjects() {
        return SUBJECTS;
    }

    public List<String> getClasses() {
        return CLASSES;
    }

    public List<Role> getAvailableRoles(boolean classTeacher) {
        return classTeacher ? List.of(Role.student) : List.of(Role.teacher, Role.student);
    }

    public List<String> getFilterRoles(boolean classTeacher) {
        return classTeacher ? List.of("student") : List.of("teacher", "student");
    }

    @Transactional
    public List<String> validateNewUser(User user, String subjects, User current, boolean classTeacher) {
        List<String> errors = new ArrayList<>();

        if (user.getName() == null || user.getName().isBlank()) {
            errors.add("Name is required");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            errors.add("Email is required");
        } else if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            errors.add("Please provide a valid email address");
        } else if (userRepository.existsByEmail(user.getEmail())) {
            errors.add("A user with this email already exists");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            errors.add("Password is required");
        }
        if (user.getRole() == null) {
            errors.add("Role is required");
        }
        if (user.getMobile() == null || user.getMobile().isBlank()) {
            errors.add("Mobile number is required");
        } else if (!MOBILE_PATTERN.matcher(user.getMobile()).matches()) {
            errors.add("Invalid mobile number (must be 10 digits starting with 6-9)");
        }
        if (user.getRole() == Role.student
                && user.getAdmissionNo() != null && !user.getAdmissionNo().isBlank()
                && studentRepository.existsByAdmissionNoIgnoreCase(user.getAdmissionNo().trim())) {
            errors.add("A student with this admission number already exists");
        }
        return errors;
    }

    @Transactional
    public User createUser(User user, String subjects, User current, boolean classTeacher) {
        if (classTeacher) {
            user.setRole(Role.student);
            user.setAssignedClasses(current.getClassTeacherOf());
            user.setClassTeacherOf(null);
        }

        String rawPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (user.getRole() == Role.student) {
            user.setSubjects(normalizeSubjects(subjects));
        }
        userRepository.save(user);

        if (user.getRole() == Role.student) {
            createLinkedStudent(user);
        }

        if (user.getMobile() != null && !user.getMobile().isBlank()) {
            String msg = "Welcome " + user.getName() + "! Your account is ready. Email: "
                    + user.getEmail() + " / Password: " + rawPassword;
            smsService.send(user.getMobile(), msg);
        }
        return user;
    }

    private void createLinkedStudent(User user) {
        Student student = new Student();
        String[] nameParts = user.getName().trim().split("\\s+", 2);
        student.setFirstName(nameParts[0]);
        student.setLastName(nameParts.length > 1 ? nameParts[1] : "");
        String[] classParts = user.getAssignedClasses() != null
                ? user.getAssignedClasses().split("-", 2)
                : new String[] {""};
        student.setClassName(classParts[0].trim());
        student.setSection(classParts.length > 1 && !classParts[1].isBlank() ? classParts[1].trim() : null);
        student.setAdmissionNo(user.getAdmissionNo() != null && !user.getAdmissionNo().isBlank()
                ? user.getAdmissionNo().trim()
                : generateAdmissionNo());
        student.setSubjects(user.getSubjects());
        student.setUser(user);
        studentRepository.save(student);
    }

    private String generateAdmissionNo() {
        int max = 0;
        for (String a : studentRepository.findAllAdmissionNos()) {
            String digits = a.replaceAll("\\D", "");
            if (!digits.isEmpty()) max = Math.max(max, Integer.parseInt(digits));
        }
        return "S" + (max + 1);
    }

    private String normalizeSubjects(String subjects) {
        if (subjects == null || subjects.isBlank()) return null;
        return Arrays.stream(subjects.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    public void deleteUser(Long id, User current, boolean classTeacher) {
        User target = userRepository.findById(id).orElseThrow();
        if (classTeacher && !mayManageStudent(current, target)) {
            throw new SecurityException("You can only manage student accounts of your own class");
        }
        userRepository.deleteById(id);
    }

    public void promoteStudent(Long id, String newClass, User current, boolean classTeacher) {
        User target = userRepository.findById(id).orElseThrow();

        if (classTeacher && !mayManageStudent(current, target)) {
            throw new SecurityException("You can only manage student accounts of your own class");
        }
        if (target.getRole() != Role.student) {
            throw new IllegalArgumentException("Only student accounts can be promoted");
        }
        if (newClass == null || newClass.isBlank()) {
            throw new IllegalArgumentException("Please select a class");
        }

        String targetClass = newClass.trim();
        String[] parts = targetClass.split("-", 2);
        if (parts[0].isBlank()) {
            throw new IllegalArgumentException("Invalid class");
        }

        target.setAssignedClasses(targetClass);
        userRepository.save(target);

        studentRepository.findByUser(target).ifPresent(s -> {
            s.setClassName(parts[0].trim());
            s.setSection(parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null);
            studentRepository.save(s);
        });
    }

    @Transactional
    public void updateUser(Long id, String name, String email, String password, String subject,
                           String assignedClasses, String mobile, String classTeacherOf, String subjects,
                           User current, boolean classTeacher) {
        User user = userRepository.findById(id).orElseThrow();

        if (classTeacher) {
            if (!mayManageStudent(current, user)) {
                throw new SecurityException("You can only manage student accounts of your own class");
            }
            assignedClasses = current.getClassTeacherOf();
        }

        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address");
        }
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new IllegalArgumentException("Invalid mobile number (must be 10 digits starting with 6-9)");
        }

        user.setName(name);
        user.setEmail(email);
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        user.setSubject(subject);
        user.setAssignedClasses(assignedClasses);
        user.setMobile(mobile);
        user.setClassTeacherOf(classTeacherOf);
        if (user.getRole() == Role.student) {
            user.setSubjects(normalizeSubjects(subjects));
            studentRepository.findByUser(user).ifPresent(s -> {
                s.setSubjects(user.getSubjects());
                studentRepository.save(s);
            });
        }
        userRepository.save(user);
    }

    public boolean isClassTeacher(User user) {
        return user != null && user.getRole() == Role.teacher
                && user.getClassTeacherOf() != null && !user.getClassTeacherOf().isBlank();
    }

    private boolean mayManageStudent(User classTeacher, User target) {
        if (target.getRole() != Role.student) return false;
        String targetClass = target.getAssignedClasses();
        String ownClass = classTeacher.getClassTeacherOf();
        return targetClass != null && targetClass.trim().equals(ownClass);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}