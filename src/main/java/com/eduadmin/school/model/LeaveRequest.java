package com.eduadmin.school.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who applied (a student or a teacher). */
    @ManyToOne
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    /** Populated when the applicant is a student; null for teachers. */
    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status = LeaveStatus.pending;

    /** Admin note when the request is approved or rejected. */
    @Column(length = 1000)
    private String reviewComment;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(nullable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    public LeaveRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getApplicant() { return applicant; }
    public void setApplicant(User applicant) { this.applicant = applicant; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    /** Display name: student full name if linked, otherwise the applicant's name. */
    public String getApplicantDisplayName() {
        if (student != null) return student.getFullName();
        return applicant != null ? applicant.getName() : "Unknown";
    }

    /** Class display for students, "Staff" for teachers. */
    public String getApplicantClassDisplay() {
        if (student != null) return student.getClassDisplay();
        return "Staff";
    }
}
