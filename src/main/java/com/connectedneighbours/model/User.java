package com.connectedneighbours.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    private String id;
    private String email;
    private Boolean emailVerified;
    private Boolean totpEnabled;
    private String firstName;
    private String lastName;
    private String phone;
    private String role;
    private String status;
    private Double balance;
    private String address;
    /** Quartier de résidence. */
    private String districtId;
    /**
     * Quartier administré (rôle admin uniquement) — claim du JWT, renvoyé par
     * /auth/userinfo. Distinct de {@link #districtId} : un admin peut résider
     * ailleurs que dans le quartier dont il a la charge.
     */
    private String adminDistrictId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean synced;

    public User() {
    }

    public User(String id, String email, Boolean emailVerified, Boolean totpEnabled, String firstName, String lastName,
                String phone, String role, String status, Double balance) {
        this.id = id;
        this.email = email;
        this.emailVerified = emailVerified;
        this.totpEnabled = totpEnabled;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.balance = balance;
        this.synced = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Boolean getTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(Boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDistrictId() {
        return districtId;
    }

    public void setDistrictId(String districtId) {
        this.districtId = districtId;
    }

    public String getAdminDistrictId() {
        return adminDistrictId;
    }

    public void setAdminDistrictId(String adminDistrictId) {
        this.adminDistrictId = adminDistrictId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", emailVerified='" + isEmailVerified() + '\'' +
                ", totpEnabled=" + totpEnabled +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", phone='" + phone + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                ", balance=" + balance +
                ", address='" + address + '\'' +
                ", districtId='" + districtId + '\'' +
                ", synced=" + synced +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
