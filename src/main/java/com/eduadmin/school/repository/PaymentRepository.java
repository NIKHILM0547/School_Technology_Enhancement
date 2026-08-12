package com.eduadmin.school.repository;

import com.eduadmin.school.model.Fee;
import com.eduadmin.school.model.Payment;
import com.eduadmin.school.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByFeeOrderByPaidAtDesc(Fee fee);
    List<Payment> findByFee_StudentOrderByPaidAtDesc(Student student);
    List<Payment> findByFeeInOrderByPaidAtDesc(List<Fee> fees);
}
