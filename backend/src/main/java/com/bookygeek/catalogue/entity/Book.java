package com.bookygeek.catalogue.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/**
 * A catalogue book (columns exactly as in {@code V1__init_schema.sql}). {@code stockQuantity} is {@code null} for an
 * eBook, which is always available. Books come from seed data and are never created or edited by a request here;
 * stock changes belong to the order module's use cases (later phases).
 */
@Entity
@Table(name = "book")
public class Book {

    public static final String EBOOK = "EBOOK";

    @Id
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "author", nullable = false, length = 120)
    private String author;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "publisher", nullable = false, length = 120)
    private String publisher;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "language", nullable = false, length = 40)
    private String language;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "front_cover_url", nullable = false)
    private String frontCoverUrl;

    @Column(name = "back_cover_url")
    private String backCoverUrl;

    @Column(name = "listed", nullable = false)
    private boolean listed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "book_category", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "category_id")
    @BatchSize(size = 50)
    private Set<Long> categoryIds = new TreeSet<>();

    protected Book() {
        // for JPA
    }

    /** An eBook is always available; a physical book is available while at least one copy is in stock. */
    public boolean isAvailable() {
        return EBOOK.equals(format) || (stockQuantity != null && stockQuantity > 0);
    }

    public boolean isEbook() {
        return EBOOK.equals(format);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getFormat() {
        return format;
    }

    public String getLanguage() {
        return language;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public String getFrontCoverUrl() {
        return frontCoverUrl;
    }

    public String getBackCoverUrl() {
        return backCoverUrl;
    }

    public boolean isListed() {
        return listed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Long> getCategoryIds() {
        return categoryIds;
    }
}
