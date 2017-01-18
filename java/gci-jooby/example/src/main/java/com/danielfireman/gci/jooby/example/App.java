package com.danielfireman.gci.jooby.example;

import com.danielfireman.gci.jooby.JoobyGciFilter;
import org.jooby.Jooby;

/**
 * Example of application that uses {@code JoobyGciFilter}.
 *
 * @author danielfireman
 * @see JoobyGciFilter
 */
public class App extends Jooby {

    {
        use("GET", "*", new JoobyGciFilter());
        get("/", () -> "Hello Garbage Collector Control Interceptor");
    }

    public static void main(final String[] args) {
        run(App::new, args);
    }

}
