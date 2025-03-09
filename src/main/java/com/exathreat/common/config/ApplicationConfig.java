package com.exathreat.common.config;

import javax.annotation.PostConstruct;

import com.exathreat.common.config.factory.AuditSettings;
import com.exathreat.common.config.factory.ElasticsearchSettings;
import com.exathreat.common.config.factory.InvoiceSettings;

import org.apache.http.HttpHost;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ApplicationConfig {

	@Autowired
	private AuditSettings auditSettings;

	@Autowired
	private ElasticsearchSettings elasticsearchSettings;

	@Autowired
	private InvoiceSettings invoiceSettings;

	@PostConstruct
	public void init() {
		log.info("auditSettings: " + auditSettings);
		log.info("elasticsearchSettings: " + elasticsearchSettings);
		log.info("invoiceSettings: " + invoiceSettings);
	}

	@Bean
	public RestHighLevelClient elasticsearchClient() {
		RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(
			elasticsearchSettings.getDomain(), 
			elasticsearchSettings.getPort(), 
			elasticsearchSettings.getScheme()));

		restClientBuilder.setHttpClientConfigCallback(httpAsyncClientBuilder -> httpAsyncClientBuilder
			.setDefaultIOReactorConfig(IOReactorConfig.custom()
				.setSoKeepAlive(true)
				.build()));
		
		restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> 
			requestConfigBuilder.setConnectTimeout(elasticsearchSettings.getConnectTimeout())
				.setSocketTimeout(elasticsearchSettings.getSocketTimeout()));
		
		return new RestHighLevelClient(restClientBuilder);
  }
	
}