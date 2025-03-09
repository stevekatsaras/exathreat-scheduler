package com.exathreat.scheduler.audit;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.exathreat.common.config.factory.AuditSettings;
import com.exathreat.common.jpa.repository.AuditRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	@Autowired
	private AuditRepository auditRepository;

	@Autowired
	private AuditSettings auditSettings;

	@Transactional(readOnly = false)
	public void purge() {
		try {
			ZonedDateTime purgeDateTime = ZonedDateTime.now(ZoneOffset.UTC).minusDays(auditSettings.getPurgeOlderDays());
			auditRepository.deleteByAuditedBefore(purgeDateTime);
		}
		catch (Exception exception) {
			exception.printStackTrace();
		}
	}
}