package com.redhat;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class Routes extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {

        System.out.println(" | Camel Routes starting up... | ");

        /*
        Kafka (JSON) -> Translate to XML -> Kafka
        */
        from("kafka:{{kafka.package.receiver}}?groupId=camel-transformer")
            .routeId("PackageReceiverToDeliverer")
            .log("?? Received package JSON: ${body}")
            // Transform JSON -> XML (attached is an XSL for rules formatting - we have this customized specifically)
            .to("xj:com/redhat/json2xml.xsl?transformDirection=JSON2XML")
            .log("?? Transformed package to XML: ${body}")
            .to("kafka:{{kafka.package.deliverer}}")
            .log("? Delivered package to 'package-deliverer': ${body}");



        /*
        Minio File Drop (JSON) -> Translate to XML -> Push to Kafka
        */
        from("minio:{{minio.bucket}}"
            + "?accessKey={{minio.accessKey}}"
            + "&secretKey={{minio.secretKey}}"
            + "&endpoint={{minio.endpoint}}"
            + "&secure=true")
            .routeId("MinioToKafka")
            .log("Picked up from MinIO bucket: ${header.CamelFileName}")
            .to("xj:com/redhat/json2xml.xsl?transformDirection=JSON2XML")
            .to("kafka:package-deliverer")
            .log("Delivered to Kafka: ${body}");




        /*
        AWS File Drop (JSON) -> Translate to XML -> Push to Kafka
        */
        from("aws2-s3://{{aws.s3.bucket}}"
            + "?accessKey={{aws.s3.accessKey}}"
            + "&secretKey={{aws.s3.secretKey}}"
            + "&region={{aws.s3.region}}"
            + "&prefix={{aws.s3.prefix:}}"
            + "&deleteAfterRead={{aws.s3.deleteAfterRead}}"
            + "&autoCreateBucket=false"
            + "&delay={{aws.s3.pollDelay}}"
            + "&bridgeErrorHandler=true")
            .routeId("S3ToKafka")
            .startupOrder(20)
            .log("?? Picked up from S3 bucket {{aws.s3.bucket}}: ${header.CamelAwsS3Key}")
            .wireTap("seda:funqyKnativeEvent")
            .to("xj:com/redhat/json2xml.xsl?transformDirection=JSON2XML")
            .to("kafka:package-deliverer")
            .log("Delivered S3 payload to Kafka: ${body}");
  



        /*
         REST -> JSON in -> XML out (Note: using DVB's XSLT)
         */
        rest("/process")
            .post("/json2xml")
                .consumes("application/json")
                .produces("application/xml")
                .to("direct:json2xml");
        from("direct:json2xml")
            .routeId("JsonToXml")
            .log("?? Received JSON: ${body}")
            // replace with your own XSLT or schema resource
            .to("xj:com/redhat/json2xml.xsl?transformDirection=JSON2XML")
            .log("?? Returning XML: ${body}");


            
        /*
         REST -> JSON payload -> CloudEvent -> Knative Broker (Funqy trigger)
         */
        rest("/funqy")
            .post("/event")
                .consumes("application/json")
                .produces("application/json")
                .to("seda:funqyKnativeEvent");
        from("seda:funqyKnativeEvent")
            .routeId("FunqyKnativeBridge")
            .log("?? Forwarding payload to Knative broker '{{knative.broker.url}}': ${body}")
            .convertBodyTo(String.class)
            .removeHeaders("ce-*")
            .setHeader("ce-specversion", simple("{{knative.event.specVersion}}"))
            .setHeader("ce-type", simple("{{knative.event.type}}"))
            .setHeader("ce-source", simple("{{knative.event.source}}"))
            .setHeader("ce-subject", simple("{{knative.event.subject}}"))
            .setHeader("ce-time", simple("${date:now:yyyy-MM-dd'T'HH:mm:ssXXX}"))
            .setHeader("ce-id", simple("${exchangeId}"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .toD("{{knative.broker.scheme}}://{{knative.broker.url}}"
                + "?httpMethod=POST"
                + "&bridgeEndpoint=true"
                + "&throwExceptionOnFailure=true")
            .log("?? Funqy CloudEvent sent to Knative broker at {{knative.broker.url}}")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setBody(simple("{\"status\":\"funqy-event-sent\"}"));
    }
}
