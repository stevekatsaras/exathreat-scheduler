package com.exathreat.scheduler.invoice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InvoiceScheduler {

	@Autowired
	private InvoiceService invoiceService;

	// this job will record the data ingested (size in bytes, number of events) against a new invoice
	
	@Scheduled(initialDelay = 15000, fixedDelay = 60000) // initial delay: 15s, fixed rate every: 1m
	public void doNewInvoices() {
		invoiceService.doNewInvoices();
	}

	// this job will process an invoice and mark it due

	@Scheduled(initialDelay = 60000, fixedDelay = 3600000) // initial delay: 1m, fixed rate every: 1h
	public void doProcessingInvoices() {
		invoiceService.doProcessingInvoices();
	}

}