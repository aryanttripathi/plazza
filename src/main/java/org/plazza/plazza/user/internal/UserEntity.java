package org.plazza.plazza.user.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A rider. Internal to the {@code user} module — other modules receive
 * {@link org.plazza.plazza.user.UserView} instead of this managed entity.
 */
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(name = "uk_users_phone", columnNames = "phone"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserEntity(String name, String phone) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.phone = phone;
        this.createdAt = Instant.now();
    }

    /** Identity is the assigned id; entities are equal when they denote the same row. */
    @Override
    public boolean equals(Object other) {
        return other instanceof UserEntity that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
