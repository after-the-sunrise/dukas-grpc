package com.after_sunrise.dukascopy.grpc;

import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.UncaughtExceptionHandler;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Listener extends GuiceServletContextListener implements ThreadFactory, UncaughtExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ThreadFactory delegate = Executors.defaultThreadFactory();

    private final AtomicInteger counter = new AtomicInteger();

    private final IClient client;

    private final ScheduledExecutorService executor;

    public Listener() throws ReflectiveOperationException {

        client = ClientFactory.getDefaultInstance();

        executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), this);

    }

    @Override
    public Thread newThread(Runnable r) {

        Thread thread = delegate.newThread(r);

        thread.setName(String.format("%s_%03d", getClass().getSimpleName(), counter.incrementAndGet()));

        thread.setDaemon(true);

        thread.setUncaughtExceptionHandler(this);

        return thread;

    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {

        logger.error("Uncaught exception : {}", t, e);

    }

    @Override
    protected Injector getInjector() {

        Config config = new Config(client, executor);

        return Guice.createInjector(new ServletModule(), config);

    }

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {

        logger.info("Servlet context initialized.");

        super.contextInitialized(servletContextEvent);

    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

        logger.info("Servlet context destroyed.");

        executor.shutdown();

        client.disconnect();

        super.contextDestroyed(servletContextEvent);

    }

}
