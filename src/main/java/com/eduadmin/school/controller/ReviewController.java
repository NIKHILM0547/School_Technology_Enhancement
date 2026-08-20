package com.eduadmin.school.controller;

import com.eduadmin.school.model.Review;
import com.eduadmin.school.model.ReviewReply;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.ReviewReplyRepository;
import com.eduadmin.school.repository.ReviewRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public ReviewController(ReviewRepository reviewRepository,
                            ReviewReplyRepository replyRepository,
                            UserRepository userRepository,
                            StudentRepository studentRepository) {
        this.reviewRepository = reviewRepository;
        this.replyRepository = replyRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    @GetMapping
    public String list(Model model) {
        User user = currentUser();
        boolean isStudent = user != null && user.getRole() == Role.student;
        model.addAttribute("isStudent", isStudent);
        model.addAttribute("canReply", user != null
                && (user.getRole() == Role.teacher || user.getRole() == Role.student));
        model.addAttribute("isTeacher", user != null && user.getRole() == Role.teacher);

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
        model.addAttribute("repliesByReview", repliesByReview(reviews));
        model.addAttribute("newReview", new Review());
        model.addAttribute("activePage", "reviews");
        return "reviews";
    }

    /** Groups all replies under their parent post for the template. */
    private Map<Long, List<ReviewReply>> repliesByReview(List<Review> reviews) {
        Map<Long, List<ReviewReply>> byReview = new HashMap<>();
        for (Review r : reviews) {
            byReview.put(r.getId(), replyRepository.findByReviewOrderByCreatedAtAsc(r));
        }
        return byReview;
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

    /** Teachers and students of the same class can reply to a post. */
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id,
                        @RequestParam String content,
                        Model model) {
        User user = currentUser();
        if (user == null) {
            return "redirect:/reviews";
        }
        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            return "redirect:/reviews";
        }

        if (user.getRole() != Role.teacher && !studentOfSameClass(user, review)) {
            return "redirect:/reviews";
        }

        String text = content != null ? content.trim() : "";
        if (text.isBlank()) {
            return "redirect:/reviews";
        }

        replyRepository.save(new ReviewReply(review, user, text));
        return "redirect:/reviews";
    }

    /** True if the given user is a student in the same class (and section) as the post author. */
    private boolean studentOfSameClass(User user, Review review) {
        Student me = studentRepository.findByUser(user).orElse(null);
        if (me == null) return false;
        Student author = review.getStudent();
        boolean sameSection = (me.getSection() == null || me.getSection().isBlank())
                && (author.getSection() == null || author.getSection().isBlank())
                || (me.getSection() != null && me.getSection().equals(author.getSection()));
        return me.getClassName().equals(author.getClassName()) && sameSection;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}