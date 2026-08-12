package com.eduadmin.school.repository;

import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(Role role);
    List<User> findByRole(Role role);
    List<User> findByAssignedClassesContaining(String classFilter);

    /** Filters by optional role/classFilter and searches by name. */
    @Query("SELECT u FROM User u WHERE "
            + "(:anyRole = true OR u.role = :role) "
            + "AND (:classFilter = '' OR u.assignedClasses LIKE CONCAT('%', :classFilter, '%')) "
            + "AND (:name = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "ORDER BY u.name")
    List<User> search(@Param("anyRole") boolean anyRole,
                      @Param("role") Role role,
                      @Param("classFilter") String classFilter,
                      @Param("name") String name);
}
