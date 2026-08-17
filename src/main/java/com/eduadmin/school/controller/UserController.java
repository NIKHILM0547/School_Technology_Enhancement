package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import com.eduadmin.school.service.SmsService;
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

    @GetMapping
    public String list(@RequestParam(required = false) String role,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       Model model) {
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
        String nameQuery = (name != null && !name.isBlank()) ? name.trim() : "";
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
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedClass", classQuery);
        model.addAttribute("name", nameQuery);
        model.addAttribute("filterRoles", List.of("teacher", "student"));
        model.addAttribute("activePage", "users");
        return "users";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("newUser") User user,
                         @RequestParam(required = false) String subjects,
                         BindingResult result, Model model) {
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
            model.addAttribute("availableRoles", List.of(Role.teacher, Role.student));
            model.addAttribute("subjects", SUBJECTS);
            model.addAttribute("classes", CLASSES);
            model.addAttribute("filterRoles", List.of("teacher", "student"));
            model.addAttribute("selectedRole", null);
            model.addAttribute("selectedClass", null);
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
    public String delete(@PathVariable Long id) {
        userRepository.deleteById(id);
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
        User user = userRepository.findById(id).orElseThrow();

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
