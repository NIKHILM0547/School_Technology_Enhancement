package com.eduadmin.school.controller;

import com.eduadmin.school.model.Review;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.ReviewRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public ReviewController(ReviewRepository reviewRepository,
                            UserRepository userRepository,
                            StudentRepository studentRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    @GetMapping
    public String list(Model model) {
        User user = currentUser();
        boolean isStudent = user != null && user.getRole() == Role.student;
        model.addAttribute("isStudent", isStudent);

        List<Review> reviews;
        if (isStudent) {
            // Students only see reviews from students in their own class (and section).
            Student me = studentRepository.findByUser(user).orElse(null);
            if (me == null) {
                reviews = List.of();
            } else if (me.getSection() != null && !me.getSection().isBlank()) {
                reviews = reviewRepository.findByStudent_ClassNameAndStudent_SectionOrderByCreatedAtDesc(
                        me.getClassName(), me.getSection());
            } else {
                reviews = reviewRepository.findByStudent_ClassNameOrderByCreatedAtDesc(me.getClassName());
            }
            model.addAttribute("myStudent", me);
        } else {
            reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        }

        model.addAttribute("reviews", reviews);
        model.addAttribute("newReview", new Review());
        model.addAttribute("activePage", "reviews");
        return "reviews";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("newReview") Review review, Model model) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.student) {
            return "redirect:/reviews";
        }
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            return "redirect:/reviews";
        }

        String title = review.getTitle() != null ? review.getTitle().trim() : "";
        String content = review.getContent() != null ? review.getContent().trim() : "";
        if (title.isBlank() || content.isBlank()) {
            return "redirect:/reviews";
        }

        reviewRepository.save(new Review(student, title, content));
        return "redirect:/reviews";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
