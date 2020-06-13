package com.after_sunrise.dukascopy.grpc;

import com.after_sunrise.dukascopy.grpc.proto.DukascopyEndpointGrpc.DukascopyEndpointImplBase;
import com.dukascopy.api.IContext;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.after_sunrise.dukascopy.grpc.Config.CK_VERSION;
import static com.after_sunrise.dukascopy.grpc.Config.CV_VERSION;
import static com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.StatusRequest;
import static com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.StatusResponse;
import static com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.TickRequest;
import static com.after_sunrise.dukascopy.grpc.proto.DukascopyProto.TickResponse;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Service extends DukascopyEndpointImplBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Lock lock = new ReentrantLock();

    private final Map<StreamObserver<TickResponse>, Set<Instrument>> listeners = new ConcurrentHashMap<>();

    private final Clock clock;

    private final Properties properties;

    private final IContext context;

    public Service(Clock clock, Properties properties, IContext context) {
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.properties = Objects.requireNonNull(properties, "Properties is required.");
        this.context = Objects.requireNonNull(context, "Context is required.");
    }

    @Override
    public void status(StatusRequest request, StreamObserver<StatusResponse> observer) {

        StatusResponse.Builder builder = StatusResponse.newBuilder();

        builder.setVersion(properties.getProperty(CK_VERSION, CV_VERSION));

        builder.setSystemTime(clock.millis());

        builder.setServerTime(context.getTime());

        builder.setConnected(context.getAccount().isConnected());

        builder.setAccount(context.getAccount().getAccountId());

        context.getSubscribedInstruments().forEach(i -> builder.addSymbols(i.name()));

        observer.onNext(builder.build());

        observer.onCompleted();

    }

    @Override
    public void subscribe(TickRequest request, StreamObserver<TickResponse> observer) {

        Set<Instrument> instruments = new HashSet<>();

        for (String symbol : new HashSet<>(request.getSymbolsList())) {

            Instrument i = Instrument.valueOf(symbol);

            if (i == null) {

                observer.onError(new IOException("Unknown symbol : " + symbol));

                return; // Reject registration.

            }

            instruments.add(i);

        }

        if (instruments.isEmpty()) {

            observer.onCompleted();

            return; // Complete immediately.

        }

        logger.info("Registering listener : {} ({} symbols)", observer, instruments.size());

        listeners.put(observer, Collections.unmodifiableSet(instruments));

        adjustSubscriptions();

    }

    public void onTick(Instrument instrument, ITick tick) {

        if (instrument == null || tick == null) {
            return;
        }

        TickResponse response = TickResponse.newBuilder()
                .setSymbol(instrument.name())
                .setTime(tick.getTime())
                .setAskPrice(tick.getAsk())
                .setAskSize(tick.getAskVolume())
                .setBidPrice(tick.getBid())
                .setBidSize(tick.getBidVolume())
                .build();

        int errors = 0;

        for (Entry<StreamObserver<TickResponse>, Set<Instrument>> entry : listeners.entrySet()) {

            if (!entry.getValue().contains(instrument)) {
                continue;
            }

            StreamObserver<TickResponse> observer = entry.getKey();

            try {

                observer.onNext(response);

            } catch (RuntimeException e) {

                errors++; // Trigger adjustment regardless of the listener removal result.

                Set<Instrument> removed = listeners.remove(observer);

                if (removed == null) {
                    continue;
                }

                try {

                    logger.info("Terminating listener : {} - {} - {}", instrument.name(), observer, e.toString());

                    observer.onError(e);

                } catch (RuntimeException x) {
                    // Ignore. Expected to fail if the client has already disconnected.
                }

            }

        }

        if (errors > 0) {

            logger.debug("Adjusting subscription : {} ({} errors).", instrument.name(), errors);

            adjustSubscriptions();

        }

    }

    public void onShutdown() {

        listeners.keySet().forEach(observer -> {

            Set<Instrument> removed = listeners.remove(observer);

            if (removed == null) {
                return;
            }

            try {

                logger.info("Completing listener : {} ({} symbols)", observer, removed.size());

                observer.onCompleted();

            } catch (RuntimeException x) {
                // Ignore. Expected to fail if the client has already disconnected.
            }

        });

        adjustSubscriptions();

    }

    @VisibleForTesting
    void adjustSubscriptions() {

        lock.lock();

        try {

            Set<Instrument> toBe = new HashSet<>();

            listeners.values().forEach(toBe::addAll);

            Set<Instrument> asIs = context.getSubscribedInstruments();

            context.unsubscribeInstruments(new HashSet<>(Sets.difference(asIs, toBe)));

            context.setSubscribedInstruments(toBe);

        } finally {

            lock.unlock();

        }

    }

}
