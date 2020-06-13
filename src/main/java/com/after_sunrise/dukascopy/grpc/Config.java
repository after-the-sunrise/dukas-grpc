package com.after_sunrise.dukascopy.grpc;

import com.dukascopy.api.system.IClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.lang.ref.Cleaner;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Config extends AbstractModule {

    private static final String CONF_PREFIX = "dukas-grpc.";

    public static final String CK_PROPERTIES = CONF_PREFIX + "properties";
    public static final String CV_PROPERTIES = CONF_PREFIX + "properties";

    public static final String CK_VERSION = CONF_PREFIX + "version";
    public static final String CV_VERSION = "0.0.0";

    public static final String CK_CREDENTIAL_JNLP = CONF_PREFIX + "credential.jnlp";
    public static final String CV_CREDENTIAL_JNLP = "http://platform.dukascopy.com/demo/jforex.jnlp";

    public static final String CK_CREDENTIAL_USER = CONF_PREFIX + "credential.user";
    public static final String CV_CREDENTIAL_USER = "foo";

    public static final String CK_CREDENTIAL_PASS = CONF_PREFIX + "credential.pass";
    public static final String CV_CREDENTIAL_PASS = "bar";

    public static final String CK_CONNECTION_WAIT = CONF_PREFIX + "connection.wait";
    public static final Duration CV_CONNECTION_WAIT = Duration.ofSeconds(5);

    public static final String CK_CONNECTION_PATH = CONF_PREFIX + "connection.path";
    public static final String CV_CONNECTION_PATH = "32004";

    public static final String PATH_PREFIX_EPOLL = "epoll:";
    public static final String PATH_PREFIX_KQUEUE = "kqueue:";

    private final IClient client;

    private final ScheduledExecutorService executor;

    public Config(IClient client, ScheduledExecutorService executor) {
        this.client = Objects.requireNonNull(client, "IClient is required.");
        this.executor = Objects.requireNonNull(executor, "Executor is required.");
    }

    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(Properties.class).toInstance(loadProperties(System.getProperty(CK_PROPERTIES, CV_PROPERTIES)));
        bind(Cleaner.class).toInstance(Cleaner.create());
        bind(ScheduledExecutorService.class).toInstance(executor);
        bind(IClient.class).toInstance(client);
        binder().bind(Client.class).asEagerSingleton();
    }

    @VisibleForTesting
    static Properties loadProperties(String path) {

        Logger logger = LoggerFactory.getLogger(Config.class);

        Properties p = new Properties();

        System.getenv().forEach(p::setProperty);

        System.getProperties().forEach(p::put);

        try {

            Path absolute = Paths.get(path).toAbsolutePath();

            p.load(new ByteArrayInputStream(Files.readAllBytes(absolute)));

            logger.info("Loaded filepath properties : {} ({})", absolute, p.size());

        } catch (Exception e1) {

            try {

                URL url = Resources.getResource(path);

                p.load(new ByteArrayInputStream(Resources.toByteArray(url)));

                logger.info("Loaded classpath properties : {} ({} bytes)", url, p.size());

            } catch (Exception e2) {

                logger.debug("Skipped loading properties : {}", path);

            }

        }

        return p;

    }

}
