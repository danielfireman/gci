package com.danielfireman.gci.jooby;

import com.danielfireman.gci.GarbageCollectorControlInterceptor;
import com.danielfireman.gci.ShedResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Status;

/**
 * Jooby filter that uses {@link GarbageCollectorControlInterceptor} to control garbage
 * collection and decide whether to shed requests.
 *
 * @author danielfireman
 * @see GarbageCollectorControlInterceptor
 */
@Singleton
public class JoobyGciFilter implements Route.Filter {

    private GarbageCollectorControlInterceptor gci;

    @Inject
    JoobyGciFilter(GarbageCollectorControlInterceptor gci) {
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
        gci.after(shedResponse);
    }
}
