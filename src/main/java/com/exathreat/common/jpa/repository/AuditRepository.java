package com.exathreat.common.jpa.repository;

import java.time.ZonedDateTime;

import com.exathreat.common.jpa.entity.Audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRepository extends JpaRepository<Audit, Long> {

	@Modifying
	@Query("DELETE FROM Audit a WHERE a.audited < :audited")
	public int deleteByAuditedBefore(@Param("audited") ZonedDateTime audited);
}