package com.eduadmin.school.controller;

import com.eduadmin.school.model.Announcement;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.AnnouncementRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/announcements")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    public AnnouncementController(AnnouncementRepository announcementRepository,
                                  UserRepository userRepository) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
    }

    /** Everyone (admin, teacher, student) can view announcements. */
    @GetMapping
    public String list(Model model) {
        User user = currentUser();
        boolean isAdmin = user != null && user.getRole() == Role.admin;
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("announcements", announcementRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "announcements");
        return "announcements";
    }

    /** Only admins may post new announcements. */
    @PostMapping("/post")
    public String post(@RequestParam String title,
                       @RequestParam String message,
                       Model model) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/announcements";
        }

        String cleanTitle = title != null ? title.trim() : "";
        String cleanMessage = message != null ? message.trim() : "";
        if (cleanTitle.isEmpty() || cleanMessage.isEmpty()) {
            model.addAttribute("errorMessage", "Please provide both a title and a message.");
            model.addAttribute("isAdmin", true);
            model.addAttribute("announcements", announcementRepository.findAllByOrderByCreatedAtDesc());
            model.addAttribute("activePage", "announcements");
            return "announcements";
        }

        Announcement announcement = new Announcement(cleanTitle, cleanMessage, user);
        announcement.setCreatedAt(LocalDateTime.now());
        announcementRepository.save(announcement);
        return "redirect:/announcements";
    }

    /** Only admins may delete announcements. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/announcements";
        }
        announcementRepository.findById(id).ifPresent(announcementRepository::delete);
        return "redirect:/announcements";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}