package com.danielfireman.gci.elasticsearch;

import com.danielfireman.gci.GarbageCollectorControlInterceptor;
import com.danielfireman.gci.ShedResponse;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;

public class GciFilter implements ActionFilter {

    private GarbageCollectorControlInterceptor gci;

    @Inject
    public GciFilter(GarbageCollectorControlInterceptor gci) {
        this.gci = gci;
    }

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
            ShedResponse shedResponse = gci.before();
            if (shedResponse.shouldShed) {
                BytesRestResponse resp = new BytesRestResponse(RestStatus.SERVICE_UNAVAILABLE, "");
                String duration = Double.toString(((double) shedResponse.unavailabilityDuration.toMillis()) / 1000d);
                resp.addHeader("Retry-After", duration);
                gci.after(shedResponse);
                channel.sendResponse(resp);
                return;
            }
            gci.after(shedResponse);
        }
        chain.proceed(task, action, request, listener);
    }
}
