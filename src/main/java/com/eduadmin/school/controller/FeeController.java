package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.service.FeeService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long studentId,
                       @RequestParam(required = false) String classFilter,
                       @RequestParam(required = false) String name,
                       Model model) {
        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            return studentView(model, user);
        }
        if (user != null && user.getRole() == Role.teacher) {
            return "redirect:/";
        }
        return adminView(model, studentId, classFilter, name);
    }

    private String adminView(Model model, Long studentId, String classFilter, String name) {
        List<Fee> fees = feeService.searchFees(studentId, classFilter, name);
        model.addAttribute("fees", fees);
        model.addAttribute("students", feeService.getAllStudents());
        model.addAttribute("classes", feeService.getAllClasses());
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedClass", classFilter != null && !classFilter.isBlank() ? classFilter.trim() : "");
        model.addAttribute("name", name != null && !name.isBlank() ? name.trim() : "");
        model.addAttribute("activePage", "fees");
        return "fees";
    }

    private String studentView(Model model, User user) {
        Student student = feeService.getStudentByUser(user);
        if (student == null) {
            model.addAttribute("activePage", "fees");
            return "my-fees";
        }
        List<Fee> myFees = feeService.getStudentFees(student);
        List<Payment> payments = feeService.getPaymentsForFees(myFees);
        List<FeeStructure> structure = feeService.getFeeStructureForClass(student.getClassDisplay());
        double totalPaid = feeService.getTotalPaid(myFees);
        LocalDate today = LocalDate.now();
        double remaining = feeService.getRemainingToPay(myFees, today);

        model.addAttribute("student", student);
        model.addAttribute("myFees", myFees);
        model.addAttribute("payments", payments);
        model.addAttribute("structure", structure);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("remaining", remaining);
        model.addAttribute("activePage", "fees");
        return "my-fees";
    }

    @PostMapping("/new")
    public String create(@RequestParam Long studentId,
                         @RequestParam String term,
                         @RequestParam Double amountDue,
                         @RequestParam(required = false, defaultValue = "0") Double amountPaid,
                         @RequestParam(required = false) String dueDate) {
        User user = currentUser();
        if (user != null && user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        feeService.createFee(studentId, term, amountDue, amountPaid, dueDate);
        return "redirect:/fees";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, @RequestParam Double amount) {
        User user = currentUser();
        feeService.payFee(id, amount, user);
        return "redirect:/fees";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return feeService.getUserByEmail(auth.getName());
    }
}