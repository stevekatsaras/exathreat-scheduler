package com.exathreat.scheduler.invoice;

import java.util.Map;

import com.exathreat.common.jpa.entity.Organisation;
import com.exathreat.common.jpa.entity.OrganisationInvoice;
import com.exathreat.common.jpa.entity.OrganisationSubscription;
import com.exathreat.common.jpa.entity.OrganisationUser;
import com.exathreat.common.jpa.entity.SystemUser;
import com.exathreat.common.jpa.repository.OrganisationUserRepository;
import com.exathreat.common.jpa.repository.SystemUserRepository;
import com.exathreat.common.support.MailSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InvoiceMailer {

	@Autowired
	private MailSupport mailSupport;

	@Autowired
	private OrganisationUserRepository organisationUserRepository;

	@Autowired
	private SystemUserRepository systemUserRepository;
	
	@Async
	@Transactional(readOnly = true)
	public void sendDataThresholdEmail(OrganisationInvoice organisationInvoice, Integer dataPercent) throws Exception {
		OrganisationSubscription organisationSubscription = organisationInvoice.getOrganisationSubscription();
		Organisation organisation = organisationSubscription.getOrganisation();

		OrganisationUser organisationUser = organisationUserRepository.findByOrganisationAndUserOwner(organisation, true);
		SystemUser systemUser = systemUserRepository.findByEmailAddress(organisationUser.getEmailAddress());

		Map<String, Object> variables = Map.of(
			"organisationUser", organisationUser, 
			"systemUser", systemUser, 
			"organisationInvoice", organisationInvoice, 
			"dataPercent", dataPercent, 
			"logo", "logo");

		mailSupport.sendEmail(
			organisationUser.getEmailAddress(), 
			"Exathreat invoice for " + organisation.getOrgName() + " has exceeded " + dataPercent + "% of its data limit.", 
			"organisation/invoice/emails/threshold", 
			variables);
	}

	@Async
	@Transactional(readOnly = true)
	public void sendDueEmail(OrganisationInvoice organisationInvoice) throws Exception {
		OrganisationSubscription organisationSubscription = organisationInvoice.getOrganisationSubscription();
		Organisation organisation = organisationSubscription.getOrganisation();

		OrganisationUser organisationUser = organisationUserRepository.findByOrganisationAndUserOwner(organisation, true);
		SystemUser systemUser = systemUserRepository.findByEmailAddress(organisationUser.getEmailAddress());

		Map<String, Object> variables = Map.of(
			"organisationUser", organisationUser, 
			"systemUser", systemUser, 
			"organisationInvoice", organisationInvoice, 
			"logo", "logo");

		mailSupport.sendEmail(organisationUser.getEmailAddress(), "Exathreat invoice for " + organisation.getOrgName() + " is due.", "organisation/invoice/emails/due", variables);
	}





		// private void sendMail(OrganisationInvoice organisationInvoice) throws Exception {
	// 	OrganisationUser organisationUser = organisationUserRepository.findByOrganisationAndUserOwner(organisation, true);
	// 	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy");
	
	// 	Map<String, Object> variables = new HashMap<String, Object>();
	// 	variables.put("userGivenName", organisationUser.getGivenName());
	// 	variables.put("orgName", organisation.getOrgName());
	// 	variables.put("invPeriodFrom", dtf.format(organisationInvoice.getPeriodFrom().withZoneSameInstant(ZoneOffset.UTC)));
	// 	variables.put("invPeriodTo", dtf.format(organisationInvoice.getPeriodTo().withZoneSameInstant(ZoneOffset.UTC)));
	// 	variables.put("invDataIngestTotal", organisationInvoice.getDataIngestTotal());
	// 	variables.put("invAmountTotal", organisationInvoice.getAmountTotal());
	// 	variables.put("subGbDivisible", organisationSubscription.getSubscription().getGbDivisible());
	// 	variables.put("subCurrency", organisationSubscription.getSubscription().getCurrency());
	// 	variables.put("logo", "logo");
	// 	mailSupport.sendEmail(organisationUser.getEmailAddress(), "Exathreat Invoice Due (Organisation: " + organisationUser.getOrganisation().getOrgCode() + ")", "organisation/invoice/emails/due", variables);
	// }
}