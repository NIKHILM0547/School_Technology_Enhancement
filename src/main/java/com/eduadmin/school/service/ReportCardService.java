package com.eduadmin.school.service;

import com.eduadmin.school.model.Attendance;
import com.eduadmin.school.model.AttendanceStatus;
import com.eduadmin.school.model.Fee;
import com.eduadmin.school.model.Mark;
import com.eduadmin.school.model.SchoolSettings;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.repository.AttendanceRepository;
import com.eduadmin.school.repository.FeeRepository;
import com.eduadmin.school.repository.MarkRepository;
import com.eduadmin.school.repository.SchoolSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders the report card XHTML template via Thymeleaf and converts it to a
 * PDF stream. The school logo is embedded as a base64 data-URI so the PDF
 * renderer (flying-saucer) can resolve it without a file/URL round trip.
 */
@Service
public class ReportCardService {

    private static final List<String> SUBJECT_HEADERS = List.of("Mathematics", "English", "Hindi", "Science");

    /** One subject row on the printed report card. */
    public record SubjectMark(String subject, Double maxMarks, Double obtained, Double percentage, String grade) {}

    /** Letter grade from a percentage (mirrors MarksController.gradeFor). */
    public static String gradeFor(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B+";
        if (pct >= 60) return "B";
        if (pct >= 50) return "C";
        if (pct >= 40) return "D";
        return "F";
    }

    private final SchoolSettingsRepository settingsRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeRepository feeRepository;
    private final MarkRepository markRepository;
    private final SpringTemplateEngine reportCardTemplateEngine;

    public ReportCardService(SchoolSettingsRepository settingsRepository,
                             AttendanceRepository attendanceRepository,
                             FeeRepository feeRepository,
                             MarkRepository markRepository,
                             @org.springframework.beans.factory.annotation.Qualifier("reportCardTemplateEngine")
                             SpringTemplateEngine reportCardTemplateEngine) {
        this.settingsRepository = settingsRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeRepository = feeRepository;
        this.markRepository = markRepository;
        this.reportCardTemplateEngine = reportCardTemplateEngine;
    }

    public SchoolSettings settings() {
        return settingsRepository.findById(SchoolSettings.ID).orElseGet(SchoolSettings::defaults);
    }

    public List<String> distinctTerms() {
        // A term may exist only as marks (or only as fees); union both so the
        // report card always offers the term a student actually has data for.
        Set<String> terms = new TreeSet<>();
        feeRepository.findAll().forEach(f -> {
            if (f.getTerm() != null && !f.getTerm().isBlank()) terms.add(f.getTerm());
        });
        terms.addAll(markRepository.findDistinctTerms());
        return new ArrayList<>(terms);
    }

    /** Detects the real image MIME type from the file's magic bytes, so the
     *  embedded data-URI always starts with "image/..." (flying-saucer only
     *  decodes data URIs whose scheme is data:image/). Falls back to the
     *  browser-supplied content type, then to image/png. */
    public static String detectImageType(byte[] bytes, String fallbackContentType) {
        if (bytes != null && bytes.length >= 4) {
            if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
                return "image/png";
            }
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
                return "image/gif";
            }
            if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
                return "image/webp";
            }
            if (bytes[0] == 'B' && bytes[1] == 'M') {
                return "image/bmp";
            }
        }
        if (fallbackContentType != null && fallbackContentType.toLowerCase().startsWith("image/")) {
            return fallbackContentType;
        }
        return "image/png";
    }

    /** Re-encodes an image to PNG bytes so it is guaranteed to be readable by
     *  OpenPDF (Image.getInstance only supports PNG/JPEG/GIF/BMP). WebP/HEIC/etc.
     *  are decoded via ImageIO (with the twelve-monkeys plugin) and rewritten as
     *  PNG. Returns the original bytes if decoding/re-encoding fails. */
    public static byte[] normalizeToPng(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return bytes;
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) return bytes;
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", png)) return bytes;
            return png.toByteArray();
        } catch (Exception e) {
            return bytes;
        }
    }

    public byte[] generatePdf(Student student, String term, Model model) {
        try {
            SchoolSettings settings = settings();
            model.addAttribute("settings", settings);
            model.addAttribute("student", student);
            model.addAttribute("term", term);
            model.addAttribute("today", LocalDate.now());

            List<Attendance> attendance = attendanceRepository.findByStudent(student);
            long present = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.present).count();
            long absent = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.absent).count();
            long late = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.late).count();
            int rate = attendance.isEmpty() ? 0 : (int) Math.round(100.0 * present / attendance.size());
            model.addAttribute("presentCount", present);
            model.addAttribute("absentCount", absent);
            model.addAttribute("lateCount", late);
            model.addAttribute("attendanceRate", rate);

            List<Mark> marks = markRepository.findByStudentAndTermOrderBySubject(student, term);
            Map<String, Mark> marksBySubject = new LinkedHashMap<>();
            for (Mark m : marks) {
                if (m.getSubject() != null) marksBySubject.put(m.getSubject(), m);
            }
            List<String> subjectNames = new ArrayList<>();
            for (String h : SUBJECT_HEADERS) subjectNames.add(h);
            for (String s : marksBySubject.keySet()) {
                if (!subjectNames.contains(s)) subjectNames.add(s);
            }
            double marksTotal = marks.stream().mapToDouble(Mark::getMarksObtained).sum();
            double marksMax = marks.stream().mapToDouble(Mark::getMaxMarks).sum();
            double marksPct = marksMax == 0 ? 0 : Math.round(1000.0 * marksTotal / marksMax) / 10.0;
            List<SubjectMark> subjectRows = new ArrayList<>();
            for (String subject : subjectNames) {
                Mark m = marksBySubject.get(subject);
                subjectRows.add(new SubjectMark(subject,
                        m != null ? m.getMaxMarks() : 100.0,
                        m != null ? m.getMarksObtained() : null,
                        m != null ? m.getPercentage() : null,
                        m != null ? gradeFor(m.getPercentage()) : "—"));
            }
            model.addAttribute("subjectRows", subjectRows);
            model.addAttribute("marksTotal", marksTotal);
            model.addAttribute("marksMax", marksMax);
            model.addAttribute("marksPct", marksPct);
            model.addAttribute("hasMarks", !marks.isEmpty());
            model.addAttribute("totalGrade", marks.isEmpty() ? "—" : gradeFor(marksPct));

            if (settings.getLogoBytes() != null && settings.getLogoBytes().length > 0) {
                byte[] logoPng = normalizeToPng(settings.getLogoBytes());
                String type = detectImageType(logoPng, settings.getLogoContentType());
                String dataUri = "data:" + type + ";base64,"
                        + Base64.getEncoder().encodeToString(logoPng);
                model.addAttribute("logoDataUri", dataUri);
            } else {
                model.addAttribute("logoDataUri", null);
            }

            Context context = new Context();
            context.setVariables(model.asMap());
            String html = reportCardTemplateEngine.process("reportcard-pdf", context);

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate report card PDF", e);
        }
    }
}