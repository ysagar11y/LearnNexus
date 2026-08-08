package com.learnnexus.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    @Query("select o from OrgUnit o order by o.path, o.name")
    List<OrgUnit> findAllOrdered();

    @Query("select o from OrgUnit o where o.parentId is null order by o.name")
    List<OrgUnit> findRoots();

    /** The unit itself plus everything beneath it. */
    @Query("select o from OrgUnit o where o.id = :rootId or o.path like concat(:pathPrefix, '%') order by o.path, o.name")
    List<OrgUnit> findSubtree(@Param("rootId") UUID rootId, @Param("pathPrefix") String pathPrefix);

    boolean existsByParentId(UUID parentId);
}
