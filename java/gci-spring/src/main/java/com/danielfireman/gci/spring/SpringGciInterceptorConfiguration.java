package com.danielfireman.gci.spring;

import com.danielfireman.gci.GarbageCollectorControlInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} to be exported by the application that would
 * like to use {@link SpringGciInterceptor}.
 *
 * @author danielfireman
 * @see SpringGciInterceptor
 */
@Configuration
@ComponentScan
public class SpringGciInterceptorConfiguration {
    @Bean
    public SpringGciInterceptor springGciInterceptor() {
        return new SpringGciInterceptor();
    }

    @Bean
    public GarbageCollectorControlInterceptor gciInterceptor() {
        return new GarbageCollectorControlInterceptor();
    }
}
