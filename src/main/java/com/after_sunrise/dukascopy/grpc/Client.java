package com.after_sunrise.dukascopy.grpc;

import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.time.Clock;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static com.after_sunrise.dukascopy.grpc.Config.CK_CONNECTION_WAIT;
import static com.after_sunrise.dukascopy.grpc.Config.CK_CREDENTIAL_JNLP;
import static com.after_sunrise.dukascopy.grpc.Config.CK_CREDENTIAL_PASS;
import static com.after_sunrise.dukascopy.grpc.Config.CK_CREDENTIAL_USER;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CONNECTION_WAIT;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CREDENTIAL_JNLP;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CREDENTIAL_PASS;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CREDENTIAL_USER;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Client implements ISystemListener, Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Clock clock;

    private final Properties properties;

    private final Cleaner cleaner;

    private final IClient client;

    private final ScheduledExecutorService executor;

    @Inject
    public Client(Clock clock, Properties properties, Cleaner cleaner, IClient client, ScheduledExecutorService executor) {
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.properties = Objects.requireNonNull(properties, "Properties is required.");
        this.cleaner = Objects.requireNonNull(cleaner, "Cleaner is required.");
        this.client = Objects.requireNonNull(client, "IClient is required.");
        this.executor = Objects.requireNonNull(executor, "Executor is required.");
        this.client.setSystemListener(this);
        this.executor.execute(this);
    }

    @Override
    public synchronized void onConnect() {

        logger.info("IClient connected.");

        startStrategy();

    }

    @VisibleForTesting
    synchronized void startStrategy() {

        if (executor.isShutdown()) {

            logger.info("IClient strategy skipped.");

        } else {

            long id = client.startStrategy(new Strategy(clock, properties, cleaner, executor));

            logger.info("Started strategy : {}", id);

        }

    }

    @Override
    public synchronized void onDisconnect() {

        logger.info("IClient disconnected.");

        client.getStartedStrategies().forEach((id, strategy) -> {

            client.stopStrategy(id);

            logger.info("Stopped strategy : [{}] {}", id, strategy);

        });

        String millis = properties.getProperty(CK_CONNECTION_WAIT, String.valueOf(CV_CONNECTION_WAIT.toMillis()));

        executor.schedule(this, Long.parseLong(millis), MILLISECONDS); // Attempt reconnect.

    }

    @Override
    public void run() {

        if (executor.isShutdown()) {

            logger.info("IClient connection skipped.");

            return;

        }

        String jnlp = properties.getProperty(CK_CREDENTIAL_JNLP, CV_CREDENTIAL_JNLP);
        String user = properties.getProperty(CK_CREDENTIAL_USER, CV_CREDENTIAL_USER);
        String pass = properties.getProperty(CK_CREDENTIAL_PASS, CV_CREDENTIAL_PASS);

        try {

            logger.info("IClient connecting... (url={}, user={})", jnlp, user);

            client.connect(jnlp, user, pass);

        } catch (Exception e) {

            String millis = properties.getProperty(CK_CONNECTION_WAIT, String.valueOf(CV_CONNECTION_WAIT.toMillis()));

            logger.warn("IClient connection failure. Reconnecting in {} ms...", millis, e);

            executor.schedule(this, Long.parseLong(millis), MILLISECONDS);

        }

    }

    @Override
    public void onStart(long processId) {
        logger.debug("IClient process started : {}", processId);
    }

    @Override
    public void onStop(long processId) {

        if (executor.isShutdown()) {

            logger.debug("IClient process stopped : {}", processId);

        } else {

            String millis = properties.getProperty(CK_CONNECTION_WAIT, String.valueOf(CV_CONNECTION_WAIT.toMillis()));

            logger.warn("IClient process aborted. Restarting in {} ms...", millis);

            executor.schedule(this::startStrategy, Long.parseLong(millis), MILLISECONDS);

        }

    }

}
