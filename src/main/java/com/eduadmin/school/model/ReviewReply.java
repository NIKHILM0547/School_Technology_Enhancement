package com.eduadmin.school.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_replies")
public class ReviewReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    /** Author of the reply — can be a teacher or a student (both are Users). */
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ReviewReply() {}

    public ReviewReply(Review review, User author, String content) {
        this.review = review;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }

    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}