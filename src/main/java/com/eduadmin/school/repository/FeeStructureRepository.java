package com.eduadmin.school.repository;

import com.eduadmin.school.model.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {
    List<FeeStructure> findAllByOrderByClassNameAscTermAsc();
    List<FeeStructure> findByClassNameOrderByTermAsc(String className);
    Optional<FeeStructure> findByClassNameAndTerm(String className, String term);
}
