package com.safepath.backend.repository;

import com.safepath.backend.entity.RouteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RouteResultRepository extends JpaRepository<RouteResult, UUID> {
}
