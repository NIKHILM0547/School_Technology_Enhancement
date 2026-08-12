package com.eduadmin.school.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fee_id", nullable = false)
    private Fee fee;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    public Payment() {}

    public Payment(Fee fee, Double amount) {
        this.fee = fee;
        this.amount = amount;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Fee getFee() { return fee; }
    public void setFee(Fee fee) { this.fee = fee; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
