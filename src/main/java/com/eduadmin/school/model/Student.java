package com.eduadmin.school.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @Column(nullable = false, unique = true)
    private String admissionNo;

    @Column(nullable = false)
    private String className;

    private String section;
    private String parentName;
    private String parentPhone;
    private String parentEmail;
    private LocalDate dateOfBirth;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attendance> attendanceRecords = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Fee> fees = new ArrayList<>();

    public Student() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getParentPhone() { return parentPhone; }
    public void setParentPhone(String parentPhone) { this.parentPhone = parentPhone; }

    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFullName() { return firstName + " " + lastName; }

    /** Returns the class with section, e.g. "6-A", or just the class if no section. */
    public String getClassDisplay() {
        if (section != null && !section.isBlank()) {
            return className + "-" + section;
        }
        return className;
    }
}
