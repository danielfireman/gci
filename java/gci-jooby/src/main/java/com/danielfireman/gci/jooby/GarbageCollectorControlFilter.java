package com.danielfireman.gci.jooby;

import com.danielfireman.gci.GcControlInterceptor;
import com.danielfireman.gci.ShedResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Status;

/**
 * Jooby filter that uses {@link GcControlInterceptor} to control garbage
 * collection and decide whether to shed requests.
 *
 * @author danielfireman
 * @see GcControlInterceptor
 */
@Singleton
public class GarbageCollectorControlFilter implements Route.Filter {

    private GcControlInterceptor gci;

    @Inject
    GarbageCollectorControlFilter(GcControlInterceptor gci) {
        this.gci = gci;
    }

    public void handle(Request request, Response response, Route.Chain chain) throws Throwable {
        ShedResponse shedResponse = gci.before();
        if (shedResponse.shouldShed) {
            String duration = Double.toString(((double)shedResponse.unavailabilityDuration.toMillis())/1000d);
            response.header("Retry-After", duration)
                    .status(Status.SERVICE_UNAVAILABLE)
                    .length(0)
                    .end();
        } else {
            chain.next(request, response);
        }
        gci.post(shedResponse);
    }
}
