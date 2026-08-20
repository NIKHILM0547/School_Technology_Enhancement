package com.eduadmin.school.config;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the logged-in user's display name, role and class available to every
 *  template (used by the layout fragment for the user chip and sidebar). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public GlobalModelAdvice(UserRepository userRepository, StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    @ModelAttribute("currentUserName")
    public String currentUserName() {
        User user = currentUser();
        return user == null ? null : user.getName();
    }

    /** True if the logged-in user is a teacher (used to hide admin/student-only menus). */
    @ModelAttribute("isTeacher")
    public boolean isTeacher() {
        User user = currentUser();
        return user != null && user.getRole() == Role.teacher;
    }

    /** The class of the logged-in user: the class a class teacher is in charge of,
     *  or the current class of a student. Empty for admins / plain teachers. */
    @ModelAttribute("currentUserClass")
    public String currentUserClass() {
        User user = currentUser();
        if (user == null) return "";
        if (user.getRole() == Role.teacher) {
            return user.getClassTeacherOf() != null ? user.getClassTeacherOf() : "";
        }
        if (user.getRole() == Role.student) {
            return studentRepository.findByUser(user)
                    .map(Student::getClassDisplay)
                    .orElse("");
        }
        return "";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}