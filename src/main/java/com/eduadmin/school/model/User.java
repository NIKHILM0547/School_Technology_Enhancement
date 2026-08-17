package com.eduadmin.school.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Email(message = "Please provide a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = true)
    private String subject;

    @Column(nullable = true)
    private String assignedClasses;

    @Column(nullable = true)
    private String classTeacherOf;

    /** Subjects a student studies, comma-separated (e.g. "Mathematics, Science").
     *  Mirrored onto the linked Student record so report cards can use them. */
    @Column(nullable = true)
    private String subjects;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number (must be 10 digits starting with 6-9)")
    @Column(nullable = false)
    private String mobile;

    /** Not persisted; admission number lives on the linked Student record. */
    @Transient
    private String admissionNo;

    public User() {}

    public User(String name, String email, String password, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getAssignedClasses() { return assignedClasses; }
    public void setAssignedClasses(String assignedClasses) { this.assignedClasses = assignedClasses; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getClassTeacherOf() { return classTeacherOf; }
    public void setClassTeacherOf(String classTeacherOf) { this.classTeacherOf = classTeacherOf; }

    public String getSubjects() { return subjects; }
    public void setSubjects(String subjects) { this.subjects = subjects; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
}
