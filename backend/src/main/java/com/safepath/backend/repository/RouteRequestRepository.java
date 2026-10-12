package com.safepath.backend.repository;

import com.safepath.backend.entity.RouteRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RouteRequestRepository extends JpaRepository<RouteRequest, UUID> {
}
