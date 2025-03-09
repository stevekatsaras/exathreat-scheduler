package com.exathreat.common.jpa.repository;

import com.exathreat.common.jpa.entity.OrganisationIndex;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganisationIndexRepository extends JpaRepository<OrganisationIndex, Long> {
	
}