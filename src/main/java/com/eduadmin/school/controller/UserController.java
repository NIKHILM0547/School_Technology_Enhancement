package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.User;
import com.eduadmin.school.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userService.getUserByEmail(auth.getName());
    }

    @GetMapping
    public String list(@RequestParam(required = false) String role,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       Model model) {
        User current = currentUser();
        boolean classTeacher = userService.isClassTeacher(current);
        String teacherClass = classTeacher ? current.getClassTeacherOf() : null;

        List<User> users = userService.getUsersForList(current, role, classFilter, name);
        userService.enrichUsersWithAdmission(users);

        model.addAttribute("users", users);
        model.addAttribute("newUser", new User());
        model.addAttribute("availableRoles", userService.getAvailableRoles(classTeacher));
        model.addAttribute("subjects", userService.getSubjects());
        model.addAttribute("classes", userService.getClasses());
        model.addAttribute("classTeacherNames", userService.getClassTeacherNames());
        model.addAttribute("selectedRole", classTeacher ? "student" : role);
        model.addAttribute("selectedClass", classTeacher ? teacherClass : (classFilter != null && !classFilter.isBlank() ? classFilter.trim() : ""));
        model.addAttribute("name", name != null && !name.isBlank() ? name.trim() : "");
        model.addAttribute("filterRoles", userService.getFilterRoles(classTeacher));
        model.addAttribute("isClassTeacher", classTeacher);
        model.addAttribute("teacherClass", classTeacher ? teacherClass : null);
        model.addAttribute("activePage", "users");
        return "users";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("newUser") User user,
                         @RequestParam(required = false) String subjects,
                         BindingResult result, Model model) {
        User current = currentUser();
        boolean classTeacher = userService.isClassTeacher(current);

        List<String> errors = userService.validateNewUser(user, subjects, current, classTeacher);
        if (!errors.isEmpty()) {
            errors.forEach(e -> result.rejectValue("name", "error", e));
        }

        if (result.hasErrors()) {
            model.addAttribute("users", userService.getUsersForList(current, null, null, ""));
            userService.enrichUsersWithAdmission(model.asMap().containsKey("users") ? (List<User>) model.getAttribute("users") : List.of());
            model.addAttribute("availableRoles", userService.getAvailableRoles(classTeacher));
            model.addAttribute("subjects", userService.getSubjects());
            model.addAttribute("classes", userService.getClasses());
            model.addAttribute("classTeacherNames", userService.getClassTeacherNames());
            model.addAttribute("filterRoles", userService.getFilterRoles(classTeacher));
            model.addAttribute("selectedRole", classTeacher ? "student" : null);
            model.addAttribute("selectedClass", classTeacher ? current.getClassTeacherOf() : null);
            model.addAttribute("isClassTeacher", classTeacher);
            model.addAttribute("teacherClass", classTeacher ? current.getClassTeacherOf() : null);
            model.addAttribute("activePage", "users");
            return "users";
        }

        userService.createUser(user, subjects, current, classTeacher);
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User current = currentUser();
        boolean classTeacher = userService.isClassTeacher(current);
        try {
            userService.deleteUser(id, current, classTeacher);
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/users";
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id,
                          @RequestParam String newClass,
                          RedirectAttributes redirectAttributes) {
        User current = currentUser();
        boolean classTeacher = userService.isClassTeacher(current);
        try {
            userService.promoteStudent(id, newClass, current, classTeacher);
            redirectAttributes.addFlashAttribute("success", "Student promoted successfully");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
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
        boolean classTeacher = userService.isClassTeacher(current);
        try {
            userService.updateUser(id, name, email, password, subject, assignedClasses, mobile, classTeacherOf, subjects, current, classTeacher);
            redirectAttributes.addFlashAttribute("success", "User updated successfully");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}