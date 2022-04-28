package com.example.demo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasRole('BOGUS')")
public interface ThingRepository extends JpaRepository<Thing, Long> {
  @Override
  @PreAuthorize("hasRole('BOGUS')")
  Optional<Thing> findById(Long id);
}
