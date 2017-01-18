package com.danielfireman.gci.spring;

import com.danielfireman.gci.GarbageCollectorControlInterceptor;
import com.danielfireman.gci.ShedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Spring interceptor that uses {@link GarbageCollectorControlInterceptor} to control garbage
 * collection and decide whether to shed requests.
 *
 * @author danielfireman
 * @see GarbageCollectorControlInterceptor
 */
@Configuration
public class SpringGciInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private GarbageCollectorControlInterceptor gci;

    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object o)
            throws Exception {
        System.out.print("Got it");
        ShedResponse shedResponse = gci.before();
        if (shedResponse.shouldShed) {
            System.out.println("\n\n SHED \n\n");
            String duration = Double.toString(((double) shedResponse.unavailabilityDuration.toMillis()) / 1000d);
            response.addHeader("Retry-After", duration);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentLength(0);
        }
        gci.after(shedResponse);
        return !shedResponse.shouldShed;
    }
}
