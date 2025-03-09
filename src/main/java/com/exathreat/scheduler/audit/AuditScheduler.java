package com.exathreat.scheduler.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditScheduler {

	@Autowired
	private AuditService auditService;

	// this job will purge old audit records from the database.
	
	@Scheduled(initialDelay = 60000, fixedDelay = 3600000) // initial delay: 1m, fixed rate every: 1h
	public void purge() {
		auditService.purge();
	}
}