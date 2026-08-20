package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import com.eduadmin.school.service.SmsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/users")
public class UserController {

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

    public UserController(UserRepository userRepository, StudentRepository studentRepository,
                          PasswordEncoder passwordEncoder, SmsService smsService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
    }

    /** The logged-in user (email = principal name), or null if none. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    /** True if the given user is a teacher who is the class teacher of a class. */
    private boolean isClassTeacher(User user) {
        return user != null && user.getRole() == Role.teacher
                && user.getClassTeacherOf() != null && !user.getClassTeacherOf().isBlank();
    }

    /** Map of class display (e.g. "6-A") -> name of the teacher who is its class teacher. */
    private Map<String, String> classTeacherNames() {
        Map<String, String> names = new HashMap<>();
        for (User t : userRepository.findByRole(Role.teacher)) {
            if (t.getClassTeacherOf() != null && !t.getClassTeacherOf().isBlank()) {
                names.put(t.getClassTeacherOf().trim(), t.getName());
            }
        }
        return names;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String role,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       Model model) {
        User current = currentUser();
        boolean classTeacher = isClassTeacher(current);
        String teacherClass = classTeacher ? current.getClassTeacherOf() : null;

        String nameQuery = (name != null && !name.isBlank()) ? name.trim() : "";

        // Class teachers only see (and manage) the student accounts of their own class
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
            model.addAttribute("users", users);
            model.addAttribute("newUser", new User());
            model.addAttribute("availableRoles", List.of(Role.student));
            model.addAttribute("subjects", SUBJECTS);
            model.addAttribute("classes", CLASSES);
            model.addAttribute("classTeacherNames", classTeacherNames());
            model.addAttribute("selectedRole", "student");
            model.addAttribute("selectedClass", teacherClass);
            model.addAttribute("name", nameQuery);
            model.addAttribute("filterRoles", List.of("student"));
            model.addAttribute("isClassTeacher", true);
            model.addAttribute("teacherClass", teacherClass);
            model.addAttribute("activePage", "users");
            return "users";
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
        List<User> users = userRepository.search(anyRole, selected, classQuery, nameQuery);
        Map<Long, String> admissionByUser = new HashMap<>();
        for (Student s : studentRepository.findAll()) {
            if (s.getUser() != null) admissionByUser.put(s.getUser().getId(), s.getAdmissionNo());
        }
        for (User u : users) u.setAdmissionNo(admissionByUser.get(u.getId()));
        model.addAttribute("users", users);
        model.addAttribute("newUser", new User());
        model.addAttribute("availableRoles", List.of(Role.teacher, Role.student));
        model.addAttribute("subjects", SUBJECTS);
        model.addAttribute("classes", CLASSES);
        model.addAttribute("classTeacherNames", classTeacherNames());
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedClass", classQuery);
        model.addAttribute("name", nameQuery);
        model.addAttribute("filterRoles", List.of("teacher", "student"));
        model.addAttribute("isClassTeacher", false);
        model.addAttribute("teacherClass", null);
        model.addAttribute("activePage", "users");
        return "users";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("newUser") User user,
                         @RequestParam(required = false) String subjects,
                         BindingResult result, Model model) {
        User current = currentUser();
        boolean classTeacher = isClassTeacher(current);

        // A class teacher can only register student accounts and only for their own class
        if (classTeacher) {
            user.setRole(Role.student);
            user.setAssignedClasses(current.getClassTeacherOf());
            user.setClassTeacherOf(null);
        }

        boolean hasError = false;

        if (user.getName() == null || user.getName().isBlank()) {
            result.rejectValue("name", "required", "Name is required");
            hasError = true;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            result.rejectValue("email", "required", "Email is required");
            hasError = true;
        } else if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            result.rejectValue("email", "invalid", "Please provide a valid email address");
            hasError = true;
        } else if (userRepository.existsByEmail(user.getEmail())) {
            result.rejectValue("email", "duplicate", "A user with this email already exists");
            hasError = true;
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            result.rejectValue("password", "required", "Password is required");
            hasError = true;
        }
        if (user.getRole() == null) {
            result.rejectValue("role", "required", "Role is required");
            hasError = true;
        }
        if (user.getMobile() == null || user.getMobile().isBlank()) {
            result.rejectValue("mobile", "required", "Mobile number is required");
            hasError = true;
        } else if (!MOBILE_PATTERN.matcher(user.getMobile()).matches()) {
            result.rejectValue("mobile", "invalid", "Invalid mobile number (must be 10 digits starting with 6-9)");
            hasError = true;
        }
        if (user.getRole() == Role.student
                && user.getAdmissionNo() != null && !user.getAdmissionNo().isBlank()
                && studentRepository.existsByAdmissionNoIgnoreCase(user.getAdmissionNo().trim())) {
            result.rejectValue("admissionNo", "duplicate", "A student with this admission number already exists");
            hasError = true;
        }

        if (hasError) {
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("availableRoles", classTeacher ? List.of(Role.student) : List.of(Role.teacher, Role.student));
            model.addAttribute("subjects", SUBJECTS);
            model.addAttribute("classes", CLASSES);
            model.addAttribute("classTeacherNames", classTeacherNames());
            model.addAttribute("filterRoles", classTeacher ? List.of("student") : List.of("teacher", "student"));
            model.addAttribute("selectedRole", classTeacher ? "student" : null);
            model.addAttribute("selectedClass", classTeacher ? current.getClassTeacherOf() : null);
            model.addAttribute("isClassTeacher", classTeacher);
            model.addAttribute("teacherClass", classTeacher ? current.getClassTeacherOf() : null);
            model.addAttribute("activePage", "users");
            return "users";
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
        return "redirect:/users";
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

    /** Clean a comma-separated multi-select: trims, drops blanks and dedupes. */
    private String normalizeSubjects(String subjects) {
        if (subjects == null || subjects.isBlank()) return null;
        return java.util.Arrays.stream(subjects.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User current = currentUser();
        boolean classTeacher = isClassTeacher(current);
        User target = userRepository.findById(id).orElseThrow();
        if (classTeacher && !mayManageStudent(current, target)) {
            redirectAttributes.addFlashAttribute("error", "You can only manage student accounts of your own class");
            return "redirect:/users";
        }
        userRepository.deleteById(id);
        return "redirect:/users";
    }

    /** Class teachers may only manage a user if it is a student of their own class. */
    private boolean mayManageStudent(User classTeacher, User target) {
        if (target.getRole() != Role.student) return false;
        String targetClass = target.getAssignedClasses();
        String ownClass = classTeacher.getClassTeacherOf();
        return targetClass != null && targetClass.trim().equals(ownClass);
    }

    /** Promote a student account to another class. The linked Student record and the
     *  account's assigned class are updated together. */
    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id,
                          @RequestParam String newClass,
                          RedirectAttributes redirectAttributes) {
        User current = currentUser();
        boolean classTeacher = isClassTeacher(current);
        User target = userRepository.findById(id).orElseThrow();

        if (classTeacher && !mayManageStudent(current, target)) {
            redirectAttributes.addFlashAttribute("error", "You can only manage student accounts of your own class");
            return "redirect:/users";
        }
        if (target.getRole() != Role.student) {
            redirectAttributes.addFlashAttribute("error", "Only student accounts can be promoted");
            return "redirect:/users";
        }
        if (newClass == null || newClass.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Please select a class");
            return "redirect:/users";
        }

        String targetClass = newClass.trim();
        String[] parts = targetClass.split("-", 2);
        if (parts[0].isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Invalid class");
            return "redirect:/users";
        }

        target.setAssignedClasses(targetClass);
        userRepository.save(target);

        studentRepository.findByUser(target).ifPresent(s -> {
            s.setClassName(parts[0].trim());
            s.setSection(parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null);
            studentRepository.save(s);
        });

        redirectAttributes.addFlashAttribute("success",
                target.getName() + " was promoted to " + targetClass);
        return "redirect:/users";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam String email,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) String subject,
                         @RequestParam(required = false) String assignedClasses,
                         @RequestParam(required = false) String mobile,
                         @RequestParam(required = false) String classTeacherOf,
                         @RequestParam(required = false) String subjects,
                         RedirectAttributes redirectAttributes) {
        User current = currentUser();
        boolean classTeacher = isClassTeacher(current);
        User user = userRepository.findById(id).orElseThrow();

        if (classTeacher) {
            if (!mayManageStudent(current, user)) {
                redirectAttributes.addFlashAttribute("error", "You can only manage student accounts of your own class");
                return "redirect:/users";
            }
            assignedClasses = current.getClassTeacherOf();
        }

        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            redirectAttributes.addFlashAttribute("error", "A user with this email already exists");
            return "redirect:/users";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            redirectAttributes.addFlashAttribute("error", "Please provide a valid email address");
            return "redirect:/users";
        }
        if (mobile == null || mobile.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Mobile number is required");
            return "redirect:/users";
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            redirectAttributes.addFlashAttribute("error", "Invalid mobile number (must be 10 digits starting with 6-9)");
            return "redirect:/users";
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
        redirectAttributes.addFlashAttribute("success", "User updated successfully");
        return "redirect:/users";
    }
}
