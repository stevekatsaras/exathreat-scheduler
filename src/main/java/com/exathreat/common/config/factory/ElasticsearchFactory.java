package com.exathreat.common.config.factory;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchFactory {

	@Autowired
	private RestHighLevelClient elasticsearchClient;

	public BulkByScrollResponse deleteByQuery(DeleteByQueryRequest deleteByQueryRequest) throws Exception {
		return elasticsearchClient.deleteByQuery(deleteByQueryRequest, RequestOptions.DEFAULT);
	}
	
	public SearchResponse searchIndex(SearchRequest searchRequest) throws Exception {
		return elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
	}
}