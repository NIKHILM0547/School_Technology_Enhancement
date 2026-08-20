package com.eduadmin.school.repository;

import com.eduadmin.school.model.Review;
import com.eduadmin.school.model.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {
    List<ReviewReply> findByReviewOrderByCreatedAtAsc(Review review);
}