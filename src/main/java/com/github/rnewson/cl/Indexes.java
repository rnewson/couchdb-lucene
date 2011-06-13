package com.github.rnewson.cl;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public final class Indexes implements ServletContextListener {

    @Override
    public void contextDestroyed(final ServletContextEvent event) {

    }

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        System.err.println(event);
    }

}
