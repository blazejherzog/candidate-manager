package com.candidate_manager.messaging;

import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;

public final class OutboxRelayTestSupport {

    private OutboxRelayTestSupport() {
    }

    public static void relayAllPendingEvents(OutboxRelay outboxRelay) {
        int publishedEvents;
        do {
            publishedEvents = outboxRelay.publishPendingEvents();
        } while (publishedEvents > 0);
    }
}
