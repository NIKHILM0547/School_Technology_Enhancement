package com.eduadmin.school.repository;

import com.eduadmin.school.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findAllByOrderByCreatedAtDesc();
    List<Announcement> findTop5ByOrderByCreatedAtDesc();
}