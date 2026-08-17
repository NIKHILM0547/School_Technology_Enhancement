package com.eduadmin.school.model;

import jakarta.persistence.*;

@Entity
@Table(name = "marks",
        uniqueConstraints = @UniqueConstraint(name = "uk_marks_student_subject_term",
                columnNames = {"student_id", "subject", "term"}))
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String term;

    @Column(nullable = false)
    private Double marksObtained;

    @Column(nullable = false)
    private Double maxMarks = 100.0;

    public Mark() {}

    /** Percentage for this single subject, rounded to one decimal. */
    public double getPercentage() {
        if (maxMarks == null || maxMarks == 0) return 0;
        return Math.round(1000.0 * marksObtained / maxMarks) / 10.0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public Double getMarksObtained() { return marksObtained; }
    public void setMarksObtained(Double marksObtained) { this.marksObtained = marksObtained; }

    public Double getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Double maxMarks) { this.maxMarks = maxMarks; }
}
