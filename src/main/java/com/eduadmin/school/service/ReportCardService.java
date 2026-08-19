package com.eduadmin.school.service;

import com.eduadmin.school.model.Attendance;
import com.eduadmin.school.model.AttendanceStatus;
import com.eduadmin.school.model.ClassTeacherRemark;
import com.eduadmin.school.model.Fee;
import com.eduadmin.school.model.Mark;
import com.eduadmin.school.model.SchoolSettings;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.repository.AttendanceRepository;
import com.eduadmin.school.repository.ClassTeacherRemarkRepository;
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
    private final ClassTeacherRemarkRepository remarkRepository;
    private final SpringTemplateEngine reportCardTemplateEngine;

    public ReportCardService(SchoolSettingsRepository settingsRepository,
                             AttendanceRepository attendanceRepository,
                             FeeRepository feeRepository,
                             MarkRepository markRepository,
                             ClassTeacherRemarkRepository remarkRepository,
                             @org.springframework.beans.factory.annotation.Qualifier("reportCardTemplateEngine")
                             SpringTemplateEngine reportCardTemplateEngine) {
        this.settingsRepository = settingsRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeRepository = feeRepository;
        this.markRepository = markRepository;
        this.remarkRepository = remarkRepository;
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

    /** Distinct academic years found in marks/fee terms (e.g. "2026"), falling
     *  back to the current year when there is no data yet. */
    public List<String> distinctYears() {
        Set<String> years = new TreeSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{4})");
        for (String t : distinctTerms()) {
            java.util.regex.Matcher m = p.matcher(t);
            String last = null;
            while (m.find()) last = m.group(1);
            if (last != null) years.add(last);
        }
        if (years.isEmpty()) years.add(String.valueOf(LocalDate.now().getYear()));
        return new ArrayList<>(years);
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

    public byte[] generatePdf(Student student, String year, String termChoice, Model model) {
        try {
            SchoolSettings settings = settings();
            model.addAttribute("settings", settings);
            model.addAttribute("student", student);
            model.addAttribute("year", year);
            model.addAttribute("termChoice", termChoice);
            model.addAttribute("today", LocalDate.now());

            // Attendance for the whole academic year (report card is annual).
            LocalDate yearStart = LocalDate.of(Integer.parseInt(year), 1, 1);
            LocalDate yearEnd = LocalDate.of(Integer.parseInt(year), 12, 31);
            List<Attendance> attendance = attendanceRepository.findByStudent(student).stream()
                    .filter(a -> a.getDate() != null
                            && !a.getDate().isBefore(yearStart) && !a.getDate().isAfter(yearEnd))
                    .toList();
            long present = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.present).count();
            long absent = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.absent).count();
            long late = attendance.stream().filter(a -> a.getStatus() == AttendanceStatus.late).count();
            int rate = attendance.isEmpty() ? 0 : (int) Math.round(100.0 * present / attendance.size());
            model.addAttribute("presentCount", present);
            model.addAttribute("absentCount", absent);
            model.addAttribute("lateCount", late);
            model.addAttribute("attendanceRate", rate);

            String term1Name = "Term 1 " + year;
            String term2Name = "Term 2 " + year;
            List<Mark> term1Marks = markRepository.findByStudentAndTermOrderBySubject(student, term1Name);
            List<Mark> term2Marks = markRepository.findByStudentAndTermOrderBySubject(student, term2Name);

            // Union of subjects seen in either term, fixed headers first.
            Map<String, Mark> t1BySubject = new LinkedHashMap<>();
            Map<String, Mark> t2BySubject = new LinkedHashMap<>();
            for (Mark m : term1Marks) t1BySubject.put(m.getSubject(), m);
            for (Mark m : term2Marks) t2BySubject.put(m.getSubject(), m);
            List<String> subjectNames = new ArrayList<>(SUBJECT_HEADERS);
            for (Mark m : term1Marks) if (!subjectNames.contains(m.getSubject())) subjectNames.add(m.getSubject());
            for (Mark m : term2Marks) if (!subjectNames.contains(m.getSubject())) subjectNames.add(m.getSubject());

            boolean includeTerm2 = !"Term 1".equals(termChoice);

            List<SubjectMark> term1Rows = new ArrayList<>();
            List<SubjectMark> term2Rows = new ArrayList<>();
            List<SubjectMark> finalRows = new ArrayList<>();
            for (String subject : subjectNames) {
                Mark m1 = t1BySubject.get(subject);
                Mark m2 = t2BySubject.get(subject);
                term1Rows.add(row(m1));
                term2Rows.add(row(m2));
                // Final = combined marks through the selected cutoff ("Final Term" = whole year).
                double fOb = 0, fMax = 0;
                boolean any = false;
                if (m1 != null) { fOb += m1.getMarksObtained(); fMax += m1.getMaxMarks(); any = true; }
                if (includeTerm2 && m2 != null) { fOb += m2.getMarksObtained(); fMax += m2.getMaxMarks(); any = true; }
                if (any) {
                    double fPct = fMax == 0 ? 0 : Math.round(1000.0 * fOb / fMax) / 10.0;
                    finalRows.add(new SubjectMark(subject, fMax, fOb, fPct, gradeFor(fPct)));
                } else {
                    finalRows.add(new SubjectMark(subject, 100.0, null, null, "—"));
                }
            }

            model.addAttribute("term1Rows", term1Rows);
            model.addAttribute("term2Rows", term2Rows);
            model.addAttribute("finalRows", finalRows);
            model.addAttribute("hasTerm1Marks", !term1Marks.isEmpty());
            model.addAttribute("hasTerm2Marks", !term2Marks.isEmpty());
            model.addAttribute("hasMarks", !term1Marks.isEmpty() || !term2Marks.isEmpty());

            model.addAttribute("term1Total", sumObtained(term1Marks));
            model.addAttribute("term1Max", sumMax(term1Marks));
            double t1Pct = percent(term1Marks);
            model.addAttribute("term1Pct", t1Pct);
            model.addAttribute("term1Grade", term1Marks.isEmpty() ? "—" : gradeFor(t1Pct));
            model.addAttribute("term2Total", sumObtained(term2Marks));
            model.addAttribute("term2Max", sumMax(term2Marks));
            double t2Pct = percent(term2Marks);
            model.addAttribute("term2Pct", t2Pct);
            model.addAttribute("term2Grade", term2Marks.isEmpty() ? "—" : gradeFor(t2Pct));

            List<Mark> finalMarks = new ArrayList<>();
            finalMarks.addAll(term1Marks);
            if (includeTerm2) finalMarks.addAll(term2Marks);
            double finalTotal = finalMarks.stream().mapToDouble(Mark::getMarksObtained).sum();
            double finalMax = finalMarks.stream().mapToDouble(Mark::getMaxMarks).sum();
            double finalPct = finalMax == 0 ? 0 : Math.round(1000.0 * finalTotal / finalMax) / 10.0;
            model.addAttribute("finalTotal", finalTotal);
            model.addAttribute("finalMax", finalMax);
            model.addAttribute("finalPct", finalPct);
            model.addAttribute("finalGrade", finalMarks.isEmpty() ? "—" : gradeFor(finalPct));

            ClassTeacherRemark remark = remarkRepository.findByStudent(student).orElse(null);
            model.addAttribute("classTeacherRemark", remark != null ? remark.getRemark() : null);
            model.addAttribute("classTeacherName", remark != null && remark.getUpdatedBy() != null
                    ? remark.getUpdatedBy().getName() : null);

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

    /** One subject row for a term; empty mark → placeholder values. */
    private SubjectMark row(Mark m) {
        if (m == null) return new SubjectMark("", 100.0, null, null, "—");
        return new SubjectMark(m.getSubject(), m.getMaxMarks(), m.getMarksObtained(),
                m.getPercentage(), gradeFor(m.getPercentage()));
    }

    private double sumObtained(List<Mark> marks) {
        return marks.stream().mapToDouble(Mark::getMarksObtained).sum();
    }

    private double sumMax(List<Mark> marks) {
        return marks.stream().mapToDouble(Mark::getMaxMarks).sum();
    }

    private double percent(List<Mark> marks) {
        double max = sumMax(marks);
        return max == 0 ? 0 : Math.round(1000.0 * sumObtained(marks) / max) / 10.0;
    }
}