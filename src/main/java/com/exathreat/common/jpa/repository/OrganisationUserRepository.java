package com.exathreat.common.jpa.repository;

import com.exathreat.common.jpa.entity.Organisation;
import com.exathreat.common.jpa.entity.OrganisationUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganisationUserRepository extends JpaRepository<OrganisationUser, Long> {
	OrganisationUser findByOrganisationAndUserOwner(Organisation organisation, boolean userOwner);
}