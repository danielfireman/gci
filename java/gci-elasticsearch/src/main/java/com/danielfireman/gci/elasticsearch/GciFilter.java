package com.danielfireman.gci.elasticsearch;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;

public class GciFilter implements ActionFilter {

    // GCI must be the first filter.
    @Override
    public int order() {
        return 0;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(Task task, String action, Request request, ActionListener<Response> listener, ActionFilterChain<Request, Response> chain) {
        if (request.getClass().equals(SearchRequest.class)) {
            RestChannel channel = ThreadRepo.channel.get();
            if (channel == null) {
                chain.proceed(task, action, request, listener);
                return;
            }
            // TODO(danielfireman): Use GCI to decide whether to shed the request and send a response back.
            // BytesRestResponse resp = new BytesRestResponse(RestStatus.OK, "Boo");
            // channel.sendResponse(resp);
            // return;
        }
        chain.proceed(task, action, request, listener);
    }
}
