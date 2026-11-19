package com.bookygeek.catalogue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A writer's short biography (columns exactly as in {@code V3__author_profile.sql}); keyed by the author name. */
@Entity
@Table(name = "author_profile")
public class AuthorProfile {

    @Id
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "bio", nullable = false, length = 600)
    private String bio;

    protected AuthorProfile() {
        // for JPA
    }

    public String getName() {
        return name;
    }

    public String getBio() {
        return bio;
    }
}
