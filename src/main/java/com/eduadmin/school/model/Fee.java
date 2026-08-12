package com.eduadmin.school.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fees")
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private String term;

    @Column(nullable = false)
    private Double amountDue;

    @Column(nullable = false)
    private Double amountPaid = 0.0;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.unpaid;

    @OneToMany(mappedBy = "fee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    public enum Status { unpaid, partial, paid, overdue }

    public Fee() {}

    /** Recomputes and sets the status based on amounts and due date. */
    public void recomputeStatus() {
        if (amountPaid >= amountDue) {
            status = Status.paid;
        } else if (amountPaid > 0) {
            status = Status.partial;
        } else if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            status = Status.overdue;
        } else {
            status = Status.unpaid;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public Double getAmountDue() { return amountDue; }
    public void setAmountDue(Double amountDue) { this.amountDue = amountDue; }

    public Double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(Double amountPaid) { this.amountPaid = amountPaid; }

    public Double getOutstanding() { return amountDue - amountPaid; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
}
