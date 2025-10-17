// NotificationPreferenceRepository.java
package com.example.notificationservice.repository;

import com.example.notificationservice.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
}