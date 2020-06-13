package com.after_sunrise.dukascopy.grpc;

import com.after_sunrise.dukascopy.grpc.proto.DukascopyEndpointGrpc.DukascopyEndpointImplBase;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConnectionStatusMessage;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IInstrumentStatusMessage;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.after_sunrise.dukascopy.grpc.Config.CK_CONNECTION_PATH;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CONNECTION_PATH;
import static com.after_sunrise.dukascopy.grpc.Config.PATH_PREFIX_EPOLL;
import static com.after_sunrise.dukascopy.grpc.Config.PATH_PREFIX_KQUEUE;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Strategy extends DukascopyEndpointImplBase implements IStrategy {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Clock clock;

    private final Properties properties;

    private final Cleaner cleaner;

    private final ScheduledExecutorService executor;

    private final AtomicReference<Optional<Triple<IContext, Server, Service>>> reference;

    public Strategy(Clock clock, Properties properties, Cleaner cleaner, ScheduledExecutorService executor) {
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.properties = Objects.requireNonNull(properties, "Properties is required.");
        this.cleaner = Objects.requireNonNull(cleaner, "Cleaner is required.");
        this.executor = Objects.requireNonNull(executor, "Executor is required.");
        this.reference = new AtomicReference<>(Optional.empty());
    }

    @Override
    public synchronized void onStart(IContext context) {

        String path = properties.getProperty(CK_CONNECTION_PATH, CV_CONNECTION_PATH);

        logger.info("Starting server : {}", path);

        onStop(); // Ensure previous server is shutdown.

        Service service = new Service(clock, properties, context);

        try {

            Server server;

            if (StringUtils.startsWith(path, PATH_PREFIX_EPOLL)) {

                Path p = Paths.get(StringUtils.removeStart(path, PATH_PREFIX_EPOLL));
                Files.deleteIfExists(p);

                EventLoopGroup b = new EpollEventLoopGroup();
                EventLoopGroup w = new EpollEventLoopGroup();

                cleaner.register(service, b::shutdownGracefully);
                cleaner.register(service, w::shutdownGracefully);

                server = NettyServerBuilder
                        .forAddress(new DomainSocketAddress(p.toString()))
                        .channelType(EpollServerDomainSocketChannel.class)
                        .bossEventLoopGroup(b)
                        .workerEventLoopGroup(w)
                        .withChildOption(ChannelOption.SO_KEEPALIVE, null)
                        .executor(executor)
                        .addService(service)
                        .build().start();

            } else if (StringUtils.startsWith(path, PATH_PREFIX_KQUEUE)) {

                Path p = Paths.get(StringUtils.removeStart(path, PATH_PREFIX_KQUEUE));
                Files.deleteIfExists(p);

                EventLoopGroup b = new KQueueEventLoopGroup();
                EventLoopGroup w = new KQueueEventLoopGroup();

                cleaner.register(service, b::shutdownGracefully);
                cleaner.register(service, w::shutdownGracefully);

                server = NettyServerBuilder
                        .forAddress(new DomainSocketAddress(p.toString()))
                        .channelType(KQueueServerDomainSocketChannel.class)
                        .bossEventLoopGroup(b)
                        .workerEventLoopGroup(w)
                        .withChildOption(ChannelOption.SO_KEEPALIVE, null)
                        .executor(executor)
                        .addService(service)
                        .build().start();

            } else {

                SocketAddress address;

                if (StringUtils.contains(path, ':')) {

                    String host = StringUtils.substringBefore(path, ":");

                    String port = StringUtils.substringAfter(path, ":");

                    address = new InetSocketAddress(host, Integer.parseInt(port));

                } else {

                    address = new InetSocketAddress(Integer.parseInt(path));

                }

                EventLoopGroup b = new NioEventLoopGroup();
                EventLoopGroup w = new NioEventLoopGroup();

                cleaner.register(service, b::shutdownGracefully);
                cleaner.register(service, w::shutdownGracefully);

                server = NettyServerBuilder
                        .forAddress(address)
                        .channelType(NioServerSocketChannel.class)
                        .bossEventLoopGroup(b)
                        .workerEventLoopGroup(w)
                        .executor(executor)
                        .addService(service)
                        .build().start();

            }

            reference.set(Optional.of(Triple.of(context, server, service)));

        } catch (Throwable e) {

            logger.error("Failed to start server : {}", path, e);

            executor.execute(context::stop); // Trigger IClient restart.

        }

    }

    @Override
    public synchronized void onStop() {

        reference.getAndSet(Optional.empty()).ifPresent(ref -> {

            logger.info("Terminating server.");

            ref.getMiddle().shutdownNow();

            ref.getRight().onShutdown();

        });

    }

    @Override
    public void onMessage(IMessage message) {

        if (message instanceof IConnectionStatusMessage) {

            IConnectionStatusMessage im = (IConnectionStatusMessage) message;

            logger.debug("Connection status : {} - {}", im.getCreationTime(), im.isConnected());

        }

        if (message instanceof IInstrumentStatusMessage) {

            IInstrumentStatusMessage im = (IInstrumentStatusMessage) message;

            logger.debug("Instrument status : {} - {} - {}", im.getCreationTime(), im.getInstrument().name(), im.isTradable());

        }

    }

    @Override
    public void onAccount(IAccount account) {

        logger.debug("Account status : {} - {}", account.getAccountId(), account.isConnected());

    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
        // Do nothing
    }

    @Override
    public void onTick(Instrument instrument, ITick tick) {

        logger.trace("Tick update : {}", lazyString(instrument, tick));

        reference.get().map(Triple::getRight).ifPresent(s -> s.onTick(instrument, tick));

    }

    @VisibleForTesting
    Object lazyString(Instrument instrument, ITick tick) {
        return new Object() {
            @Override
            public String toString() {
                return "{i=" + instrument.name()
                        + ",t=" + tick.getTime()
                        + ",a=" + tick.getAsk()
                        + ",A=" + tick.getAskVolume()
                        + ",b=" + tick.getBid()
                        + ",B=" + tick.getBidVolume()
                        + "}";
            }
        };
    }

}
