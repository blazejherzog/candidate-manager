package com.candidate_manager.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "candidate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column
    private String city;

    @Column
    private String headline;

    @Column
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Candidate(UUID userId, String email, String firstName, String lastName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean hasVersion(long expectedVersion) {
        return version == expectedVersion;
    }

    public void updateDetails(CandidateDetails details, Instant now) {
        firstName = details.firstName();
        lastName = details.lastName();
        phoneNumber = details.phoneNumber();
        city = details.city();
        headline = details.headline();
        summary = details.summary();
        updatedAt = now;
    }
}
