package com.bookygeek.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A saved delivery address of one customer (columns exactly as in {@code V1__init_schema.sql}). Orders copy the
 * address into their own {@code ship_*} columns, so editing or deleting a saved address never changes order history.
 */
@Entity
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone", nullable = false, length = 10)
    private String phone;

    @Column(name = "address_line1", nullable = false, length = 150)
    private String addressLine1;

    @Column(name = "address_line2", length = 150)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "state", nullable = false, length = 60)
    private String state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pin_code", nullable = false, length = 6)
    private String pinCode;

    @Column(name = "country", nullable = false, length = 56)
    private String country;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {
        // for JPA
    }

    public Address(long customerId, String fullName, String phone, String addressLine1, String addressLine2,
            String city, String state, String pinCode, String country, Instant now) {
        this.customerId = customerId;
        this.createdAt = now;
        replaceWith(fullName, phone, addressLine1, addressLine2, city, state, pinCode, country, now);
    }

    /** Full replacement of the editable fields (the contract's PUT has replace semantics). */
    public void replaceWith(String fullName, String phone, String addressLine1, String addressLine2, String city,
            String state, String pinCode, String country, Instant now) {
        this.fullName = fullName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.pinCode = pinCode;
        this.country = country;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPinCode() {
        return pinCode;
    }

    public String getCountry() {
        return country;
    }
}
