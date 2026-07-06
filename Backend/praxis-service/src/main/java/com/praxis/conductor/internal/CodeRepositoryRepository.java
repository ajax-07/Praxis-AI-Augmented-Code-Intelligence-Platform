package com.praxis.conductor.internal;

import com.praxis.conductor.domain.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * JPA repository for the code-source Repository entity. Named with the double
 * "Repository" to keep the domain word ("Repository" = a codebase) distinct
 * from the Spring stereotype — worth the awkward name to avoid confusion.
 */
public interface CodeRepositoryRepository extends JpaRepository<Repository, UUID> {
}
