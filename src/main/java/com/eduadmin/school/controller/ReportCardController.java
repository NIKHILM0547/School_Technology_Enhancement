package com.eduadmin.school.controller;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.SchoolSettings;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.SchoolSettingsRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import com.eduadmin.school.service.ReportCardService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/reportcards")
public class ReportCardController {

    private final SchoolSettingsRepository settingsRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ReportCardService reportCardService;

    public ReportCardController(SchoolSettingsRepository settingsRepository,
                                StudentRepository studentRepository,
                                UserRepository userRepository,
                                ReportCardService reportCardService) {
        this.settingsRepository = settingsRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.reportCardService = reportCardService;
    }

    /** Report card landing: settings + student list. Admins see everyone,
     *  teachers only their own class-teacher class. */
    @GetMapping
    public String index(@RequestParam(required = false) String classFilter,
                        @RequestParam(required = false) String name,
                        @RequestParam(required = false) String year,
                        Model model) {
        User user = currentUser();
        boolean isTeacher = user != null && user.getRole() == Role.teacher;
        boolean isAdmin = user != null && user.getRole() == Role.admin;

        // School branding is backend/admin data; only expose it to admins.
        if (isAdmin) {
            SchoolSettings settings = reportCardService.settings();
            model.addAttribute("settings", settings);
            model.addAttribute("hasLogo", settings.getLogoBytes() != null && settings.getLogoBytes().length > 0);
            model.addAttribute("hasPrincipalSignature",
                    settings.getPrincipalSignatureBytes() != null && settings.getPrincipalSignatureBytes().length > 0);
        }
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isTeacher", isTeacher);
        if (isTeacher && user != null) {
            model.addAttribute("hasSignature",
                    user.getSignatureBytes() != null && user.getSignatureBytes().length > 0);
        }

        List<Student> students = isTeacher
                ? studentsOfClass(user)
                : studentRepository.findAllByOrderByLastNameAsc();
        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        String nameQuery = (name != null && !name.isBlank()) ? name.trim().toLowerCase() : "";
        if (isTeacher) {
            classQuery = "";
        }
        String cq = classQuery, nq = nameQuery;
        students = students.stream()
                .filter(s -> cq.isEmpty() || cq.equals(s.getClassDisplay()))
                .filter(s -> nq.isEmpty() || s.getFullName().toLowerCase().contains(nq))
                .toList();

        model.addAttribute("students", students);
        model.addAttribute("classQuery", classQuery);
        model.addAttribute("name", name != null ? name.trim() : "");
        model.addAttribute("classes", studentRepository.findDistinctClassDisplay());
        model.addAttribute("teacherClass", isTeacher && user != null ? user.getClassTeacherOf() : null);
        List<String> years = reportCardService.distinctYears();
        String selectedYear = (year != null && !year.isBlank())
                ? year.trim()
                : (years.isEmpty() ? String.valueOf(java.time.LocalDate.now().getYear()) : years.get(0));
        model.addAttribute("years", years);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("termChoices", List.of("Term 1", "Term 2", "Final Term"));
        model.addAttribute("defaultTermChoice", "Final Term");
        model.addAttribute("activePage", "reportcards");
        return "reportcards";
    }

    /** Registers the school name / address and uploads the logo. Admin only. */
    @PostMapping("/settings")
    public String saveSettings(@RequestParam String schoolName,
                               @RequestParam(required = false) String address,
                               @RequestParam(required = false) MultipartFile logo,
                               @RequestParam(required = false) MultipartFile principalSignature) throws IOException {
        User user = currentUser();
        if (user == null || user.getRole() != Role.admin) {
            return "redirect:/reportcards";
        }
        SchoolSettings settings = reportCardService.settings();
        settings.setSchoolName(schoolName == null || schoolName.isBlank()
                ? "EduAdmin School" : schoolName.trim());
        settings.setAddress(address != null ? address.trim() : "");
        if (logo != null && !logo.isEmpty()) {
            byte[] raw = logo.getBytes();
            byte[] png = ReportCardService.normalizeToPng(raw);
            settings.setLogoBytes(png);
            settings.setLogoContentType(ReportCardService.detectImageType(png, "image/png"));
        }
        if (principalSignature != null && !principalSignature.isEmpty()) {
            byte[] raw = principalSignature.getBytes();
            byte[] png = ReportCardService.normalizeToPng(raw);
            settings.setPrincipalSignatureBytes(png);
            settings.setPrincipalSignatureContentType(ReportCardService.detectImageType(png, "image/png"));
        }
        settingsRepository.save(settings);
        return "redirect:/reportcards?saved=true";
    }

    /** Streams the stored logo so the page can preview it. */
    @GetMapping("/settings/logo")
    public ResponseEntity<Resource> logo() {
        SchoolSettings settings = reportCardService.settings();
        if (settings.getLogoBytes() == null || settings.getLogoBytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String type = settings.getLogoContentType() != null ? settings.getLogoContentType() : MediaType.IMAGE_PNG_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(new ByteArrayResource(settings.getLogoBytes()));
    }

    /** Streams the principal's signature so the admin page can preview it. */
    @GetMapping("/settings/principal-signature")
    public ResponseEntity<Resource> principalSignature() {
        SchoolSettings settings = reportCardService.settings();
        if (settings.getPrincipalSignatureBytes() == null || settings.getPrincipalSignatureBytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String type = settings.getPrincipalSignatureContentType() != null
                ? settings.getPrincipalSignatureContentType() : MediaType.IMAGE_PNG_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(new ByteArrayResource(settings.getPrincipalSignatureBytes()));
    }

    /** Streams the logged-in teacher's signature so the page can preview it. */
    @GetMapping("/signature")
    public ResponseEntity<Resource> signature() {
        User user = currentUser();
        if (user == null || user.getRole() != Role.teacher
                || user.getSignatureBytes() == null || user.getSignatureBytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String type = user.getSignatureContentType() != null
                ? user.getSignatureContentType() : MediaType.IMAGE_PNG_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(new ByteArrayResource(user.getSignatureBytes()));
    }

    /** Uploads the logged-in teacher's signature. Teacher only. */
    @PostMapping("/signature")
    public String saveSignature(@RequestParam(required = false) MultipartFile signature) throws IOException {
        User user = currentUser();
        if (user == null || user.getRole() != Role.teacher) {
            return "redirect:/reportcards";
        }
        if (signature != null && !signature.isEmpty()) {
            byte[] raw = signature.getBytes();
            byte[] png = ReportCardService.normalizeToPng(raw);
            user.setSignatureBytes(png);
            user.setSignatureContentType(ReportCardService.detectImageType(png, "image/png"));
            userRepository.save(user);
        }
        return "redirect:/reportcards?signatureSaved=true";
    }

    /** Generates the annual PDF report card for one student (shows Term 1,
     *  Term 2 and the Final combined result for the chosen academic year).
     *  Admin/teacher only. */
    @GetMapping("/{studentId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long studentId,
                                      @RequestParam(required = false) String term,
                                      @RequestParam(required = false) String year,
                                      Model model) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getRole() == Role.teacher && !studentsOfClass(user).stream()
                .anyMatch(s -> s.getId().equals(student.getId()))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        String academicYear = (year != null && !year.isBlank())
                ? year.trim()
                : String.valueOf(java.time.LocalDate.now().getYear());
        String termChoice = (term != null && !term.isBlank()) ? term.trim() : "Final Term";

        // Embed the student's class teacher's signature, if uploaded.
        User classTeacher = user.getRole() == Role.teacher
                ? user
                : userRepository.findByRoleAndClassTeacherOf(Role.teacher, student.getClassDisplay()).orElse(null);
        if (classTeacher != null && classTeacher.getSignatureBytes() != null
                && classTeacher.getSignatureBytes().length > 0) {
            byte[] sigPng = ReportCardService.normalizeToPng(classTeacher.getSignatureBytes());
            String type = ReportCardService.detectImageType(sigPng, classTeacher.getSignatureContentType());
            model.addAttribute("classTeacherSignatureDataUri",
                    "data:" + type + ";base64,"
                            + java.util.Base64.getEncoder().encodeToString(sigPng));
        } else {
            model.addAttribute("classTeacherSignatureDataUri", null);
        }

        // Embed the principal's signature, if uploaded.
        SchoolSettings settings = reportCardService.settings();
        if (settings.getPrincipalSignatureBytes() != null && settings.getPrincipalSignatureBytes().length > 0) {
            byte[] sigPng = ReportCardService.normalizeToPng(settings.getPrincipalSignatureBytes());
            String type = ReportCardService.detectImageType(sigPng, settings.getPrincipalSignatureContentType());
            model.addAttribute("principalSignatureDataUri",
                    "data:" + type + ";base64,"
                            + java.util.Base64.getEncoder().encodeToString(sigPng));
        } else {
            model.addAttribute("principalSignatureDataUri", null);
        }

        byte[] pdf = reportCardService.generatePdf(student, academicYear, termChoice, model);
        String filename = URLEncoder.encode(student.getFullName().replace(" ", "_") + "_report.pdf",
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /** Students in the teacher's class-teacher class (e.g. "6-A"). */
    private List<Student> studentsOfClass(User user) {
        if (user == null) return List.of();
        String cls = user.getClassTeacherOf();
        if (cls == null || cls.isBlank()) return List.of();
        String[] parts = cls.split("-", 2);
        String className = parts[0].trim();
        String section = parts.length > 1 ? parts[1].trim() : "";
        return section.isBlank()
                ? studentRepository.findByClassNameOrderByLastNameAsc(className)
                : studentRepository.findByClassNameAndSectionOrderByLastNameAsc(className, section);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}