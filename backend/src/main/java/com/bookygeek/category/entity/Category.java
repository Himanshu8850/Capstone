package com.bookygeek.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A node of the two-level category tree (Fiction and Non-Fiction with their leaf categories). The catalogue data is
 * read-only for the application: categories come from the seed data and are never written by a request.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
