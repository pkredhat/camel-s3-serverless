package com.redhat;

import io.quarkus.funqy.Funq;
import io.quarkus.funqy.knative.events.CloudEvent;
import java.util.Objects;
import org.jboss.logging.Logger;

/**
 * Minimal Funqy target that simply logs whatever payload arrives.
 */
public class FunqyKnativeTarget {

    private static final Logger LOGGER = Logger.getLogger(FunqyKnativeTarget.class);

    @Funq("funqyKnativeTarget")
    public void logEvent(CloudEvent<String> event) {
        LOGGER.infov("Funqy event received: id={0}, type={1}, subject={2}, payload={3}",
                Objects.toString(event.id(), "<no-id>"),
                Objects.toString(event.type(), "<no-type>"),
                Objects.toString(event.subject(), "<no-subject>"),
                event.data());
    }
}
