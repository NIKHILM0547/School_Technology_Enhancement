package com.eduadmin.school.controller;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.FeeRepository;
import com.eduadmin.school.repository.FeeStructureRepository;
import com.eduadmin.school.repository.PaymentRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
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

    private final FeeRepository feeRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public FeeController(FeeRepository feeRepository, StudentRepository studentRepository,
                         UserRepository userRepository, PaymentRepository paymentRepository,
                         FeeStructureRepository feeStructureRepository) {
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.feeStructureRepository = feeStructureRepository;
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
        return adminView(model, studentId, classFilter, name);
    }

    /** Admin/ledger view: all students, filters, add/pay. */
    private String adminView(Model model, Long studentId, String classFilter, String name) {
        String nameQuery = (name != null && !name.isBlank()) ? name.trim() : "";
        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        Long studentIdQuery = (studentId != null) ? studentId : 0L;
        List<Fee> fees = feeRepository.search(studentIdQuery, classQuery, nameQuery);
        model.addAttribute("fees", fees);
        model.addAttribute("students", studentRepository.findAllByOrderByLastNameAsc());
        model.addAttribute("classes", studentRepository.findDistinctClassDisplay());
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedClass", classQuery);
        model.addAttribute("name", nameQuery);
        model.addAttribute("activePage", "fees");
        return "fees";
    }

    /** Student view: only their own fees, payment history, and pay option. */
    private String studentView(Model model, User user) {
        Student student = studentRepository.findByUser(user).orElse(null);
        if (student == null) {
            model.addAttribute("activePage", "fees");
            return "my-fees";
        }
        List<Fee> myFees = feeRepository.findByStudent(student);
        List<Payment> payments = paymentRepository.findByFeeInOrderByPaidAtDesc(myFees);
        List<FeeStructure> structure = feeStructureRepository.findByClassNameOrderByTermAsc(student.getClassDisplay());
        double totalPaid = myFees.stream().mapToDouble(Fee::getAmountPaid).sum();
        LocalDate today = LocalDate.now();
        // "Remaining to pay" = outstanding fees that are already due (overdue or due
        // today/earlier). Future terms are not counted here.
        double remaining = myFees.stream()
                .filter(f -> f.getOutstanding() > 0)
                .filter(f -> f.getDueDate() == null || !f.getDueDate().isAfter(today))
                .mapToDouble(Fee::getOutstanding)
                .sum();

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
        if (studentId != null && term != null && !term.isBlank()
                && amountDue != null && amountDue > 0) {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student != null) {
                Fee fee = new Fee();
                fee.setStudent(student);
                fee.setTerm(term.trim());
                fee.setAmountDue(amountDue);
                double paid = amountPaid != null ? Math.max(amountPaid, 0.0) : 0.0;
                fee.setAmountPaid(paid);
                fee.setDueDate(dueDate != null && !dueDate.isBlank() ? LocalDate.parse(dueDate) : null);
                fee.recomputeStatus();
                feeRepository.save(fee);
                if (paid > 0) {
                    paymentRepository.save(new Payment(fee, paid));
                }
            }
        }
        return "redirect:/fees";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, @RequestParam Double amount) {
        if (amount == null || amount <= 0) {
            return "redirect:/fees";
        }
        Fee fee = feeRepository.findById(id).orElse(null);
        if (fee == null) {
            return "redirect:/fees";
        }

        User user = currentUser();
        if (user != null && user.getRole() == Role.student) {
            // Students can only pay their own fee records.
            Student me = studentRepository.findByUser(user).orElse(null);
            if (me == null || !me.getId().equals(fee.getStudent().getId())) {
                return "redirect:/fees";
            }
        } else if (user != null && user.getRole() != Role.admin) {
            return "redirect:/fees";
        }

        fee.setAmountPaid(fee.getAmountPaid() + amount);
        fee.recomputeStatus();
        feeRepository.save(fee);
        paymentRepository.save(new Payment(fee, amount));
        return "redirect:/fees";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
