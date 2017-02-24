package com.danielfireman.gci.elasticsearch;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

public class GciActionIntegrationTest extends ESIntegTestCase {
    @Test
    public void testHelloWorld() throws Exception {
        SearchResponse response = client().search(new SearchRequest()).get();
        System.out.println("\n\n\n*********** Booooo "+response.toString()+" ***********\n\n");
    }
}
