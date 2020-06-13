package com.after_sunrise.dukascopy.grpc;

import com.after_sunrise.dukascopy.grpc.proto.DukascopyEndpointGrpc;
import com.after_sunrise.dukascopy.grpc.proto.DukascopyEndpointGrpc.DukascopyEndpointBlockingStub;
import com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.StatusRequest;
import com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.StatusResponse;
import com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.TickRequest;
import com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.TickResponse;
import com.google.protobuf.TextFormat;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.after_sunrise.dukascopy.grpc.Config.CK_CONNECTION_PATH;
import static com.after_sunrise.dukascopy.grpc.Config.CV_CONNECTION_PATH;
import static com.after_sunrise.dukascopy.grpc.Config.PATH_PREFIX_EPOLL;
import static com.after_sunrise.dukascopy.grpc.Config.PATH_PREFIX_KQUEUE;
import static com.after_sunrise.dukascopy.grpc.Config.loadProperties;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@Disabled
public class WebappClientTest {

    private static ManagedChannel CHANNEL;

    @BeforeAll
    static void setUpBefore() {

        Properties p = loadProperties(Paths.get("logs", "dukas-test.properties").toAbsolutePath().toString());

        String path = p.getProperty(CK_CONNECTION_PATH, CV_CONNECTION_PATH);

        if (StringUtils.startsWith(path, PATH_PREFIX_EPOLL)) {

            CHANNEL = NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(StringUtils.removeStart(path, PATH_PREFIX_EPOLL)))
                    .channelType(EpollDomainSocketChannel.class)
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .withOption(ChannelOption.SO_KEEPALIVE, null)
                    .usePlaintext()
                    .build();

        } else if (StringUtils.startsWith(path, PATH_PREFIX_KQUEUE)) {

            CHANNEL = NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(StringUtils.removeStart(path, PATH_PREFIX_KQUEUE)))
                    .channelType(KQueueDomainSocketChannel.class)
                    .eventLoopGroup(new KQueueEventLoopGroup())
                    .withOption(ChannelOption.SO_KEEPALIVE, null)
                    .usePlaintext()
                    .build();

        } else {

            CHANNEL = NettyChannelBuilder
                    .forAddress(new InetSocketAddress(Integer.parseInt(path)))
                    .channelType(NioSocketChannel.class)
                    .eventLoopGroup(new NioEventLoopGroup())
                    .usePlaintext()
                    .build();

        }

    }

    @AfterAll
    static void tearDownAfter() {

        CHANNEL.shutdown();

    }

    @Test
    @Disabled
    void testStatus() {

        DukascopyEndpointBlockingStub stub = DukascopyEndpointGrpc.newBlockingStub(CHANNEL);

        StatusResponse response = stub.status(StatusRequest.getDefaultInstance());

        System.err.println(TextFormat.shortDebugString(response));

    }

    @Test
    @Disabled
    void testSubscribe() throws InterruptedException {

        TickRequest request = TickRequest.newBuilder().addSymbols("USDJPY").build();

        DukascopyEndpointGrpc.newStub(CHANNEL).subscribe(request, new StreamObserver<>() {
            @Override
            public void onNext(TickResponse value) {
                System.err.println(TextFormat.shortDebugString(value));
            }

            @Override
            public void onCompleted() {
                System.err.println("Completed.");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Terminated : " + t);
            }
        });

        TimeUnit.MINUTES.sleep(3);

    }

}
