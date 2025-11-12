package com.redhat;

import io.quarkus.funqy.Funq;
import io.quarkus.funqy.knative.events.CloudEvent;
import io.quarkus.funqy.knative.events.CloudEventMapping;
import org.jboss.logging.Logger;

/**
 * Minimal Funqy target that simply logs whatever payload arrives.
 */
public class FunqyKnativeTarget {

    private static final Logger LOGGER = Logger.getLogger(FunqyKnativeTarget.class);

    @Funq("funqyKnativeTarget")
    @CloudEventMapping(trigger = "funqyKnativeTarget", ceType = "com.redhat.citizens.funqy.reversal")
    public void logEvent(CloudEvent<String> event) {
        LOGGER.infov("Funqy event received: id={0}, type={1}, subject={2}, payload={3}",
                event.id().orElse("<no-id>"),
                event.type().orElse("<no-type>"),
                event.subject().orElse("<no-subject>"),
                event.data());
    }
}
