package com.eduadmin.school.service;

import com.eduadmin.school.model.*;
import com.eduadmin.school.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeeService {

    private final FeeRepository feeRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public FeeService(FeeRepository feeRepository, StudentRepository studentRepository,
                      UserRepository userRepository, PaymentRepository paymentRepository,
                      FeeStructureRepository feeStructureRepository) {
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.feeStructureRepository = feeStructureRepository;
    }

    public List<Fee> searchFees(Long studentId, String classFilter, String name) {
        String nameQuery = (name != null && !name.isBlank()) ? name.trim() : "";
        String classQuery = (classFilter != null && !classFilter.isBlank()) ? classFilter.trim() : "";
        Long studentIdQuery = (studentId != null) ? studentId : 0L;
        return feeRepository.search(studentIdQuery, classQuery, nameQuery);
    }

    public List<String> getAllClasses() {
        return feeStructureRepository.findAllByOrderByClassNameAscTermAsc().stream()
                .map(FeeStructure::getClassName)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAllByOrderByLastNameAsc();
    }

    public List<Fee> getStudentFees(Student student) {
        return feeRepository.findByStudent(student);
    }

    public List<Payment> getPaymentsForFees(List<Fee> fees) {
        return paymentRepository.findByFeeInOrderByPaidAtDesc(fees);
    }

    public List<FeeStructure> getFeeStructureForClass(String className) {
        return feeStructureRepository.findByClassNameOrderByTermAsc(className);
    }

    public double getTotalPaid(List<Fee> fees) {
        return fees.stream().mapToDouble(Fee::getAmountPaid).sum();
    }

    public double getRemainingToPay(List<Fee> fees, LocalDate today) {
        return fees.stream()
                .filter(f -> f.getOutstanding() > 0)
                .filter(f -> f.getDueDate() == null || !f.getDueDate().isAfter(today))
                .mapToDouble(Fee::getOutstanding)
                .sum();
    }

    @Transactional
    public void createFee(Long studentId, String term, Double amountDue, Double amountPaid, String dueDate) {
        if (studentId != null && term != null && !term.isBlank()
                && amountDue != null && amountDue > 0) {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student != null) {
                Fee fee = new Fee();
                fee.setStudent(student);
                fee.setTerm(term.trim());
                fee.setAmountDue(amountDue);
                double paid = amountPaid != null ? Math.max(amountPaid, 0.0) : 0.0;
                fee.setAmountPaid(paid);
                fee.setDueDate(dueDate != null && !dueDate.isBlank() ? LocalDate.parse(dueDate) : null);
                fee.recomputeStatus();
                feeRepository.save(fee);
                if (paid > 0) {
                    paymentRepository.save(new Payment(fee, paid));
                }
            }
        }
    }

    @Transactional
    public void payFee(Long id, Double amount, User user) {
        if (amount == null || amount <= 0) {
            return;
        }
        Fee fee = feeRepository.findById(id).orElse(null);
        if (fee == null) {
            return;
        }

        if (user != null && user.getRole() == Role.student) {
            Student me = studentRepository.findByUser(user).orElse(null);
            if (me == null || !me.getId().equals(fee.getStudent().getId())) {
                return;
            }
        } else if (user != null && user.getRole() != Role.admin) {
            return;
        }

        fee.setAmountPaid(fee.getAmountPaid() + amount);
        fee.recomputeStatus();
        feeRepository.save(fee);
        paymentRepository.save(new Payment(fee, amount));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public Student getStudentByUser(User user) {
        return studentRepository.findByUser(user).orElse(null);
    }
}