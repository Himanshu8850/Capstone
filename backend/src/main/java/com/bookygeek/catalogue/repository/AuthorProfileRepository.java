package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.entity.AuthorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorProfileRepository extends JpaRepository<AuthorProfile, String> {
}
