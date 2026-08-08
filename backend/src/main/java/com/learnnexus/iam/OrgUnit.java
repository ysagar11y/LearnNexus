package com.learnnexus.iam;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A node in a tenant's organisation tree (Acme → Engineering → Backend).
 *
 * <p>{@link #path} is a materialised path of ancestor ids. It exists so that
 * "everything under this manager's unit" is one indexed {@code LIKE 'path%'}
 * predicate instead of a recursive query on every report.
 */
@Entity
@Table(name = "org_units")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnit extends TenantScoped {

    public static final String PATH_SEPARATOR = "/";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    private String code;

    @Column(nullable = false)
    private String path = PATH_SEPARATOR;

    @Column(nullable = false)
    private short depth = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Path of this unit's own subtree, including itself. */
    public String subtreePath() {
        return path + id + PATH_SEPARATOR;
    }

    public void placeUnder(OrgUnit parent) {
        if (parent == null) {
            this.parentId = null;
            this.path = PATH_SEPARATOR;
            this.depth = 0;
        } else {
            this.parentId = parent.getId();
            this.path = parent.subtreePath();
            this.depth = (short) (parent.getDepth() + 1);
        }
    }
}
