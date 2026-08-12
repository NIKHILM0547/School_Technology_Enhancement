package com.eduadmin.school.controller;

import com.eduadmin.school.model.Fee;
import com.eduadmin.school.model.FeeStructure;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.FeeRepository;
import com.eduadmin.school.repository.FeeStructureRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/fees/structure")
public class FeeStructureController {

    private static final List<String> CLASSES = List.of(
            "1-A", "1-B", "1-C", "2-A", "2-B", "2-C",
            "3-A", "3-B", "3-C", "4-A", "4-B", "4-C",
            "5-A", "5-B", "5-C", "6-A", "6-B", "6-C",
            "7-A", "7-B", "7-C", "8-A", "8-B", "8-C",
            "9-A", "9-B", "9-C", "10-A", "10-B", "10-C",
            "11-A", "11-B", "11-C", "12-A", "12-B", "12-C"
    );

    private static final List<String> DEFAULT_TERMS = List.of("Term 1", "Term 2", "Term 3");

    private final FeeStructureRepository structureRepository;
    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public FeeStructureController(FeeStructureRepository structureRepository,
                                  FeeRepository feeRepository,
                                  StudentRepository studentRepository,
                                  UserRepository userRepository) {
        this.structureRepository = structureRepository;
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String classFilter, Model model) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        List<FeeStructure> structures = structureRepository.findAllByOrderByClassNameAscTermAsc();
        if (classFilter != null && !classFilter.isBlank()) {
            structures = structures.stream()
                    .filter(s -> s.getClassName().equals(classFilter.trim()))
                    .toList();
        }
        model.addAttribute("structures", structures);
        model.addAttribute("structureClasses", structures.stream()
                .map(FeeStructure::getClassName)
                .distinct()
                .toList());
        model.addAttribute("classes", CLASSES);
        model.addAttribute("terms", DEFAULT_TERMS);
        model.addAttribute("selectedClass", classFilter != null ? classFilter.trim() : "");
        model.addAttribute("studentCountByClass", studentCountByClass());
        model.addAttribute("activePage", "fees-structure");
        return "fee-structure";
    }

    /** Creates a fee structure row for one class + term. */
    @PostMapping("/new")
    @Transactional
    public String create(@RequestParam String className,
                         @RequestParam String term,
                         @RequestParam Double amountDue,
                         @RequestParam(required = false) String dueDate,
                         Model model) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        List<String> errors = new ArrayList<>();
        String cls = className != null ? className.trim() : "";
        String termName = term != null ? term.trim() : "";
        if (!CLASSES.contains(cls)) {
            errors.add("Please choose a valid class.");
        }
        if (termName.isEmpty()) {
            errors.add("Please provide a term name.");
        }
        if (amountDue == null || amountDue <= 0) {
            errors.add("Amount must be greater than 0.");
        }
        if (cls.isEmpty() || termName.isEmpty() || (amountDue == null || amountDue <= 0)) {
            errors.add("All fields are required.");
        } else if (structureRepository.findByClassNameAndTerm(cls, termName).isPresent()) {
            errors.add("A fee structure for " + cls + " / " + termName + " already exists.");
        }

        if (!errors.isEmpty()) {
            model.addAttribute("errorMessages", errors);
            List<FeeStructure> all = structureRepository.findAllByOrderByClassNameAscTermAsc();
            model.addAttribute("structures", all);
            model.addAttribute("structureClasses", all.stream()
                    .map(FeeStructure::getClassName)
                    .distinct()
                    .toList());
            model.addAttribute("classes", CLASSES);
            model.addAttribute("terms", DEFAULT_TERMS);
            model.addAttribute("selectedClass", cls);
            model.addAttribute("studentCountByClass", studentCountByClass());
            model.addAttribute("activePage", "fees-structure");
            return "fee-structure";
        }

        FeeStructure structure = new FeeStructure();
        structure.setClassName(cls);
        structure.setTerm(termName);
        structure.setAmountDue(amountDue);
        structure.setDueDate(dueDate != null && !dueDate.isBlank() ? LocalDate.parse(dueDate) : null);
        structureRepository.save(structure);
        return "redirect:/fees/structure?saved=true";
    }

    /** Updates the amount/due date of an existing structure row. */
    @PostMapping("/{id}/edit")
    @Transactional
    public String edit(@PathVariable Long id,
                       @RequestParam Double amountDue,
                       @RequestParam(required = false) String dueDate) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        if (amountDue == null || amountDue <= 0) {
            return "redirect:/fees/structure";
        }
        structureRepository.findById(id).ifPresent(s -> {
            s.setAmountDue(amountDue);
            s.setDueDate(dueDate != null && !dueDate.isBlank() ? LocalDate.parse(dueDate) : null);
            structureRepository.save(s);
        });
        return "redirect:/fees/structure?saved=true";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        structureRepository.deleteById(id);
        return "redirect:/fees/structure?saved=true";
    }

    /**
     * Applies the class fee structure to its students: for every structure row
     * of the class, upserts a per-student fee record (matched by term). Existing
     * records keep their amountPaid; the amount due / due date are refreshed.
     */
    @PostMapping("/apply")
    @Transactional
    public String apply(@RequestParam String className) {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/fees";
        }
        String cls = className != null ? className.trim() : "";
        if (cls.isEmpty()) {
            return "redirect:/fees/structure";
        }
        List<FeeStructure> structures = structureRepository.findByClassNameOrderByTermAsc(cls);
        if (structures.isEmpty()) {
            return "redirect:/fees/structure?noconfig=true";
        }
        List<Student> students = studentsOfClass(cls);
        int created = 0;
        int updated = 0;
        for (Student student : students) {
            for (FeeStructure structure : structures) {
                Fee fee = feeRepository.findByStudent_IdAndTerm(student.getId(), structure.getTerm()).orElse(null);
                if (fee == null) {
                    fee = new Fee();
                    fee.setStudent(student);
                    fee.setTerm(structure.getTerm());
                    created++;
                } else {
                    updated++;
                }
                fee.setAmountDue(structure.getAmountDue());
                fee.setDueDate(structure.getDueDate());
                fee.recomputeStatus();
                feeRepository.save(fee);
            }
        }
        return "redirect:/fees/structure?applied=true&students=" + students.size()
                + "&created=" + created + "&updated=" + updated;
    }

    /** Students in the given class display ("6-A" or plain "6"). */
    private List<Student> studentsOfClass(String classDisplay) {
        String[] parts = classDisplay.split("-", 2);
        String className = parts[0].trim();
        String section = parts.length > 1 ? parts[1].trim() : "";
        return section.isBlank()
                ? studentRepository.findByClassNameOrderByLastNameAsc(className)
                : studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
    }

    /** Map of class display -> number of enrolled students, for display on the page. */
    private Map<String, Long> studentCountByClass() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String c : CLASSES) {
            counts.put(c, (long) studentsOfClass(c).size());
        }
        return counts;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
