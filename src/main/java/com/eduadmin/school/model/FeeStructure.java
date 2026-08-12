package com.eduadmin.school.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Fee structure for a class: the standard fee amount (and due date) for a
 * term, e.g. class "6-A" Term 1 = 15000. Used as a template that can be
 * applied to create/update per-student fee records.
 */
@Entity
@Table(name = "fee_structures",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_name", "term"}))
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Class display name, e.g. "6-A" (matches Student.getClassDisplay()). */
    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(nullable = false)
    private String term;

    @Column(name = "amount_due", nullable = false)
    private Double amountDue;

    @Column(name = "due_date")
    private LocalDate dueDate;

    public FeeStructure() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public Double getAmountDue() { return amountDue; }
    public void setAmountDue(Double amountDue) { this.amountDue = amountDue; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}