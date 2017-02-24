package com.danielfireman.gci.elasticsearch;

import org.elasticsearch.rest.RestChannel;

/**
 * Created by fireman on 2/24/17.
 */
public class ThreadRepo {
    public static ThreadLocal<RestChannel> channel = new ThreadLocal<>();
}
