package com.bx.imcommon.mq.api;

import java.util.List;

/**
 * Message queue abstraction for future implementations.
 */
public interface MessageQueueTemplate {

    /**
     * Enqueue a payload.
     */
    Long enqueue(String queue, Object payload);

    /**
     * Dequeue a single payload.
     */
    Object dequeue(String queue);

    /**
     * Dequeue a batch of payloads.
     */
    List<Object> dequeue(String queue, int batchSize);
}
