package com.exathreat.scheduler.index;

import java.time.ZonedDateTime;
import java.util.List;

import com.exathreat.common.config.factory.ElasticsearchFactory;
import com.exathreat.common.jpa.entity.OrganisationIndex;
import com.exathreat.common.jpa.entity.OrganisationInvoice;
import com.exathreat.common.jpa.entity.enums.OrganisationInvoiceStatusEnum;
import com.exathreat.common.jpa.entity.enums.OrganisationSubscriptionStatusEnum;
import com.exathreat.common.jpa.repository.OrganisationIndexRepository;
import com.exathreat.common.jpa.repository.OrganisationInvoiceRepository;
import com.exathreat.common.jpa.repository.OrganisationSubscriptionRepository;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IndexService {

	@Autowired
	private ElasticsearchFactory elasticsearchFactory;

	@Autowired
	private OrganisationIndexRepository organisationIndexRepository;

	@Autowired
	private OrganisationInvoiceRepository organisationInvoiceRepository;

	@Autowired
	private OrganisationSubscriptionRepository organisationSubscriptionRepository;
	
	public void purge() {		
		List<OrganisationIndex> organisationIndexes = organisationIndexRepository.findAll();
		for (OrganisationIndex organisationIndex : organisationIndexes) {
			try {
				OrganisationInvoice organisationInvoice = organisationInvoiceRepository.findByOrganisationSubscriptionAndStatus(
					organisationSubscriptionRepository.findByOrganisationAndStatus(organisationIndex.getOrganisation(), OrganisationSubscriptionStatusEnum.Active.name()), 
					OrganisationInvoiceStatusEnum.New.name());
				
				ZonedDateTime purgeDate = organisationInvoice.getPeriodFrom().minusDays(organisationIndex.getRetentionDays());
				
				DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(organisationIndex.getAliasName());
				deleteByQueryRequest.setQuery(QueryBuilders.rangeQuery("@timestamp").lt(purgeDate));
				elasticsearchFactory.deleteByQuery(deleteByQueryRequest);
			}
			catch (Exception exception) {
				exception.printStackTrace();
			}
		}
	}
}