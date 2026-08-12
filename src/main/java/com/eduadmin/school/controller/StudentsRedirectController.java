package com.eduadmin.school.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentsRedirectController {

    /** Students are now managed via the Users tab; redirect any stale links. */
    @GetMapping("/students")
    public String students() {
        return "redirect:/users";
    }
}
