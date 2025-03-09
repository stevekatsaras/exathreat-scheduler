package com.exathreat.scheduler.invoice;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.exathreat.common.config.factory.ElasticsearchFactory;
import com.exathreat.common.config.factory.InvoiceSettings;
import com.exathreat.common.jpa.entity.Organisation;
import com.exathreat.common.jpa.entity.OrganisationInvoice;
import com.exathreat.common.jpa.entity.OrganisationSubscription;
import com.exathreat.common.jpa.entity.enums.OrganisationInvoiceStatusEnum;
import com.exathreat.common.jpa.entity.enums.OrganisationStatusEnum;
import com.exathreat.common.jpa.entity.enums.OrganisationSubscriptionStatusEnum;
import com.exathreat.common.jpa.entity.enums.SubscriptionModelEnum;
import com.exathreat.common.jpa.entity.enums.SubscriptionPricePeriodEnum;
import com.exathreat.common.jpa.repository.OrganisationInvoiceRepository;
import com.exathreat.common.jpa.repository.OrganisationSubscriptionRepository;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

	@Autowired
	private ElasticsearchFactory elasticsearchFactory;

	@Autowired
	private InvoiceMailer invoiceMailer;

	@Autowired
	private InvoiceSettings invoiceSettings;

	@Autowired
	private OrganisationInvoiceRepository organisationInvoiceRepository;

	@Autowired
	private OrganisationSubscriptionRepository organisationSubscriptionRepository;
	
	public void doNewInvoices() {
		List<OrganisationInvoice> organisationInvoices = organisationInvoiceRepository.findByStatusOrderByIdAsc(OrganisationInvoiceStatusEnum.New.name());
		for (OrganisationInvoice organisationInvoice : organisationInvoices) {
			try {
				Map<String, BigDecimal> invoiceMetadata = getInvoiceMetadata(organisationInvoice);
				organisationInvoice = calculateDataAndCost(organisationInvoice, invoiceMetadata);
				organisationInvoice = checkDataThresholds(organisationInvoice, invoiceMetadata);
				organisationInvoice = organisationInvoiceRepository.saveAndFlush(organisationInvoice);

				if (ZonedDateTime.now(ZoneOffset.UTC).isAfter(organisationInvoice.getPeriodTo().withZoneSameInstant(ZoneOffset.UTC))) {
					if (organisationInvoice.getOrganisationSubscription().getOrganisation().getStatus().equals(OrganisationStatusEnum.Active.name())) {
						createNewInvoice(organisationInvoice);
					}
					organisationInvoice.setStatus(OrganisationInvoiceStatusEnum.Processing.name());
					organisationInvoice.setModified(ZonedDateTime.now(ZoneOffset.UTC));
					organisationInvoiceRepository.saveAndFlush(organisationInvoice);
				}
			}
			catch (Exception exception) {
				exception.printStackTrace();
			}
		}
	}

	public void doProcessingInvoices() {
		List<OrganisationInvoice> organisationInvoices = organisationInvoiceRepository.findByStatusOrderByIdAsc(OrganisationInvoiceStatusEnum.Processing.name());
		for (OrganisationInvoice organisationInvoice : organisationInvoices) {
			try {
				Map<String, BigDecimal> invoiceMetadata = getInvoiceMetadata(organisationInvoice);
				organisationInvoice = calculateDataAndCost(organisationInvoice, invoiceMetadata);

				organisationInvoice.setDateDue(ZonedDateTime.now(ZoneOffset.UTC).plusDays(invoiceSettings.getDueDays()));
				organisationInvoice.setStatus((organisationInvoice.getAmountTotal() == 0) ? OrganisationInvoiceStatusEnum.Paid.name() : OrganisationInvoiceStatusEnum.Due.name());
				organisationInvoice.setModified(ZonedDateTime.now(ZoneOffset.UTC));
				organisationInvoiceRepository.saveAndFlush(organisationInvoice);

				if (OrganisationInvoiceStatusEnum.Due.name().equals(organisationInvoice.getStatus())) {
					invoiceMailer.sendDueEmail(organisationInvoice);
				}
			}
			catch (Exception exception) {
				exception.printStackTrace();
			}
		}
	}

	// get metadata from ES - returns a map containing:
	// 1. dataIngestTotal - total number of bytes for that invoice period
	// 2. eventTotal - total number of events for that invoice period

	private Map<String, BigDecimal> getInvoiceMetadata(OrganisationInvoice organisationInvoice) throws Exception {
		String orgCode = organisationInvoice.getOrganisationSubscription().getOrganisation().getOrgCode();

		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.size(1);
		searchSourceBuilder.query(QueryBuilders
			.boolQuery()
				.filter(QueryBuilders.rangeQuery("@timestamp")
					.gte(organisationInvoice.getPeriodFrom().withZoneSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli())
					.lte(organisationInvoice.getPeriodTo().withZoneSameInstant(ZoneOffset.UTC).toInstant().toEpochMilli())
					.format("epoch_millis")));

		searchSourceBuilder.aggregation(AggregationBuilders.sum("bytes")
			.field("bytes"));

		SearchRequest searchRequest = new SearchRequest("org-" + orgCode + "-v1-data");
		searchRequest.source(searchSourceBuilder);
		searchRequest.scroll(TimeValue.timeValueSeconds(10));	// don't need the scroll, really

		SearchResponse searchResponse = elasticsearchFactory.searchIndex(searchRequest);
		ParsedSum parsedSum = (ParsedSum) searchResponse.getAggregations().asMap().get("bytes");

		return Map.of("dataIngestTotal", new BigDecimal(parsedSum.getValueAsString()), "eventTotal", new BigDecimal(searchResponse.getHits().getTotalHits().value));
	}

	// calculates the data and the associated cost (based on the subscription model)

	@SuppressWarnings("unchecked")
	private OrganisationInvoice calculateDataAndCost(OrganisationInvoice organisationInvoice, Map<String, BigDecimal> invoiceMetadata) throws Exception {
		Map<String, Object> subscription = organisationInvoice.getOrganisationSubscription().getSubscription();
		String model = (String) subscription.get("model");

		if (SubscriptionModelEnum.Paid.name().equals(model)) {
			BigDecimal dataIngested = invoiceMetadata.get("dataIngestTotal").divide(new BigDecimal((Integer) subscription.get("dataAmount")));
			BigDecimal dataCost = dataIngested.multiply(new BigDecimal((Integer) subscription.get("priceAmount")));

			organisationInvoice.setAmountTotal(dataCost.longValue());
		}
		else if (SubscriptionModelEnum.Free.name().equals(model)) {
			if (invoiceMetadata.get("dataIngestTotal").longValue() > new BigDecimal((Integer) subscription.get("dataAmount")).longValue()) {
				Map<String, Integer> dataExcess = (Map<String, Integer>) subscription.get("dataExcess");

				BigDecimal dataExcessInBytes = invoiceMetadata.get("dataIngestTotal").subtract(new BigDecimal((Integer) subscription.get("dataAmount")));
				BigDecimal dataExcessIngested = dataExcessInBytes.divide(new BigDecimal((Integer) dataExcess.get("dataAmount")));
				BigDecimal dataExcessCost = dataExcessIngested.multiply(new BigDecimal((Integer) dataExcess.get("priceAmount")));

				organisationInvoice.setAmountTotal(dataExcessCost.longValue());
			}
		}

		organisationInvoice.setEventTotal(invoiceMetadata.get("eventTotal").longValue());
		organisationInvoice.setDataIngestTotal(invoiceMetadata.get("dataIngestTotal").longValue());
		organisationInvoice.setModified(ZonedDateTime.now(ZoneOffset.UTC));
		return organisationInvoice;
	}

	// checks the data thresholds for invoices that have a 'free' subscription and notifies the account owner if their invoice is nearing/exceeding the plan

	private OrganisationInvoice checkDataThresholds(OrganisationInvoice organisationInvoice, Map<String, BigDecimal> invoiceMetadata) throws Exception {
		Map<String, Object> subscription = organisationInvoice.getOrganisationSubscription().getSubscription();
		String model = (String) subscription.get("model");

		if (SubscriptionModelEnum.Free.name().equals(model)) {
			BigDecimal dataPercentageUsed = invoiceMetadata.get("dataIngestTotal").divide(new BigDecimal((Integer) subscription.get("dataAmount")));

			if (!organisationInvoice.getData50() && dataPercentageUsed.doubleValue() > 0.5) {				// the 50% threshold has not yet exceeded for this invoice, so let's check
				organisationInvoice.setData50(true);
				invoiceMailer.sendDataThresholdEmail(organisationInvoice, 50);
			}
			else if (!organisationInvoice.getData75() && dataPercentageUsed.doubleValue() > 0.75) {	// the 75% threshold has not yet exceeded for this invoice, so let's check
				organisationInvoice.setData75(true);
				invoiceMailer.sendDataThresholdEmail(organisationInvoice, 75);
			}
			else if (!organisationInvoice.getData100() && dataPercentageUsed.doubleValue() > 1) {		// the 100% threshold has not yet exceeded for this invoice, so let's check
				organisationInvoice.setData100(true);
				invoiceMailer.sendDataThresholdEmail(organisationInvoice, 100);
			}
		}
		return organisationInvoice;
	}

	// creates a new invoice for the next period cycle, deriving many of the settings from the organisationInvoiceActive (elapsed invoice)

	@Transactional(readOnly = false)
	private void createNewInvoice(OrganisationInvoice organisationInvoiceActive) throws Exception {
		OrganisationSubscription organisationSubscriptionActive = organisationInvoiceActive.getOrganisationSubscription();
		Organisation organisation = organisationSubscriptionActive.getOrganisation();

		OrganisationInvoice organisationInvoiceNew = OrganisationInvoice.builder()
			.invCode(UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12))
			.periodFrom(organisationInvoiceActive.getPeriodTo().withZoneSameInstant(ZoneOffset.UTC).plus(1, ChronoUnit.MILLIS))
			.status(OrganisationInvoiceStatusEnum.New.name())
			.eventTotal(Long.valueOf(0))
			.dataIngestTotal(Long.valueOf(0))
			.data50(false)
			.data75(false)
			.data100(false)	
			.amountTotal(Long.valueOf(0))
			.created(ZonedDateTime.now(ZoneOffset.UTC))
			.modified(ZonedDateTime.now(ZoneOffset.UTC))
			.build();
		
		OrganisationSubscription organisationSubscriptionNew = organisationSubscriptionRepository.findByOrganisationAndStatus(organisation, OrganisationSubscriptionStatusEnum.New.name());
		if (organisationSubscriptionNew != null) {
			organisationInvoiceNew.setPeriodTo(getInvoicePeriodTo(organisationInvoiceNew.getPeriodFrom(), organisationSubscriptionNew));
			organisationInvoiceNew.setOrganisationSubscription(organisationSubscriptionNew);

			organisationSubscriptionActive.setStatus(OrganisationSubscriptionStatusEnum.Discontinued.name());
			organisationSubscriptionActive.setEndDate(ZonedDateTime.now(ZoneOffset.UTC));
			organisationSubscriptionActive.setModified(ZonedDateTime.now(ZoneOffset.UTC));
			organisationSubscriptionRepository.saveAndFlush(organisationSubscriptionActive);

			organisationSubscriptionNew.setStatus(OrganisationSubscriptionStatusEnum.Active.name());
			organisationSubscriptionNew.setStartDate(ZonedDateTime.now(ZoneOffset.UTC));
			organisationSubscriptionNew.setModified(ZonedDateTime.now(ZoneOffset.UTC));
			organisationSubscriptionRepository.saveAndFlush(organisationSubscriptionNew);
		}
		else {
			organisationInvoiceNew.setPeriodTo(getInvoicePeriodTo(organisationInvoiceNew.getPeriodFrom(), organisationSubscriptionActive));
			organisationInvoiceNew.setOrganisationSubscription(organisationSubscriptionActive);
		}
		organisationInvoiceRepository.saveAndFlush(organisationInvoiceNew);
	}

	private ZonedDateTime getInvoicePeriodTo(ZonedDateTime orgInvoicePeriodFrom, OrganisationSubscription organisationSubscription) throws Exception {
		return 
			(SubscriptionPricePeriodEnum.Month.name().equals(organisationSubscription.getSubscription().get("pricePeriod"))) ? orgInvoicePeriodFrom.plus(1, ChronoUnit.MONTHS).minus(1, ChronoUnit.DAYS) : 
			(SubscriptionPricePeriodEnum.Year.name().equals(organisationSubscription.getSubscription().get("pricePeriod"))) ? orgInvoicePeriodFrom.plus(1, ChronoUnit.YEARS).minus(1, ChronoUnit.DAYS) : null;
	}
}