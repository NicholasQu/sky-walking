package com.a.eye.skywalking.routing.disruptor.request;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.disruptor.AbstractSpanDisruptor;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by xin on 2016/11/29.
 */
public class RequestSpanDisruptor extends AbstractSpanDisruptor {
    private static ILog logger = LogManager.getLogger(RequestSpanDisruptor.class);
    private Disruptor<RequestSpanHolder>     requestSpanDisruptor;
    private RingBuffer<RequestSpanHolder>    requestSpanRingBuffer;
    private RouteSendRequestSpanEventHandler eventHandler;

    public RequestSpanDisruptor(String connectionURL) {
        requestSpanDisruptor = new Disruptor<RequestSpanHolder>(new RequestSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        eventHandler = new RouteSendRequestSpanEventHandler(connectionURL);
        requestSpanDisruptor.handleEventsWith(eventHandler);
        requestSpanDisruptor.start();
        requestSpanRingBuffer = requestSpanDisruptor.getRingBuffer();
    }

    public boolean saveRequestSpan(RequestSpan requestSpan) {
        long sequence = this.getRingBufferSequence(requestSpanRingBuffer);
        if(this.isShutdown()){
            return false;
        }

        try {

            RequestSpanHolder data = requestSpanRingBuffer.get(sequence);
            data.setRequestSpan(requestSpan);

            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO, "RequestSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("RequestSpan trace-id[{}] store failure..", requestSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR, "RequestSpan store failure.");
            return false;
        } finally {
            requestSpanRingBuffer.publish(sequence);
        }
    }

    public void shutdown() {
        super.shutdown();
        eventHandler.stop();
        requestSpanDisruptor.shutdown();
    }
}
