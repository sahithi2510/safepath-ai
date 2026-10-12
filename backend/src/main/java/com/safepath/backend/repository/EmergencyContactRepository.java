package com.safepath.backend.repository;

import com.safepath.backend.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

    List<EmergencyContact> findByUserIdOrderByPrimaryDescCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);
}
