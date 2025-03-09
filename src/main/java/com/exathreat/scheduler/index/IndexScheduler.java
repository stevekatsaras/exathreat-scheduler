package com.exathreat.scheduler.index;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IndexScheduler {

	@Autowired
	private IndexService indexService;

	// this job will purge old index events from elasticsearch.

	@Scheduled(cron = "@daily") // runs daily at midnight
	public void purge() {
		indexService.purge();
	}
	
}