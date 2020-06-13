package com.after_sunrise.dukascopy.grpc;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.after_sunrise.dukascopy.grpc.Config.CK_PROPERTIES;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class WebappServerTest {

    public static void main(String[] args) throws Exception {

        System.setProperty("java.awt.headless", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty(CK_PROPERTIES, Paths.get("logs", "dukas-test.properties").toAbsolutePath().toString());

        WebAppContext context = new WebAppContext();
        context.setContextPath("/test");
        context.setBaseResource(new PathResource(getDirectory("src/main/webapp")));
        context.setExtraClasspath(getDirectory("build/classes/java/main").toString());
        context.setLogUrlOnStart(true);

        Server jetty = new Server(65535);
        jetty.setHandler(context);
        jetty.start();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> stop(jetty), 1, TimeUnit.DAYS);
        executor.shutdown();

        jetty.join();

    }

    private static Path getDirectory(String path) throws IOException {

        Path p = Paths.get(path).toAbsolutePath();

        if (Files.isDirectory(p)) return p;

        throw new IOException("Invalid directory path : " + p);

    }

    private static void stop(Server server) {
        try {
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
