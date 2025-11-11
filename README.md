# citizens-demo-camel-s3-serverless

Technology Stack:

- Quarkus
- Camel (Quarkus-Camel)
- Kafka and MinIO

---

## ?? Local Kafka, MinIO & Quarkus Stack

Spin up the full citizens-demo-camel-s3-serverless topology locally (Kafka + ZooKeeper + MinIO + the Quarkus service) to iterate without relying on remote clusters.

1. Start everything with `docker compose -f docker-compose.kafka-minio.yml up --build -d` (use `docker-compose` if you are still on Compose v1). The multi-stage image now runs `./mvnw package -DskipTests` inside the build, so you only need a local `./mvnw package` when you want to fail fast before composing.
2. Kafka listens on `localhost:9092`, ZooKeeper on `localhost:2181`, MinIO on `http://localhost:9000` (console: `http://localhost:9001`, creds `admin/admin123`). The stack uses `minio/minio:latest` so you always get the latest stable server build.
3. After MinIO boots the first time, log into the console (or run `docker exec minio mc mb local/camel-drop`) to create the `camel-drop` bucket manually; it persists on the named volume across restarts.
4. Kafka UI (Provectus) is available at `http://localhost:8081` for browsing topics/consumers.
5. The Quarkus route container exposes `http://localhost:8080`; it automatically points `camel.component.kafka.brokers` at the internal Kafka service.
6. Tear down and clean the named volumes with `docker compose -f docker-compose.kafka-minio.yml down -v` when you are done.

---

## ☁️ AWS S3 Intake

If you also need to ingest files dropped into an Amazon S3 bucket, the application now ships with a Camel AWS2-S3 consumer route.

1. Provide the bucket name, region, optional prefix, and credentials (`aws.s3.bucket`, `aws.s3.region`, `aws.s3.accessKey`, `aws.s3.secretKey`) via `application.properties`, env vars, or your secrets store (avoid committing secrets).
2. Control polling/deletion with `aws.s3.pollDelay` (milliseconds between polls) and `aws.s3.deleteAfterRead`.
3. Each object is processed just like the MinIO drop: JSON -> XML via `json2xml.xsl`, then published to `{{kafka.package.deliverer}}`.

Comment out the S3 route in `Routes.java` if you no longer need it.

---

## ?? Scenario

ACME is required to send data from its payment system, which is unfortunately a monolithic system that only accepts XML. ACME takes payment pre-requests and drops them into a Kafka Queue named `package-receiver` (as pre-rendered JSON). The topics are populated by dropping files into a folder, or by running a REST API End Point.

An internal process converts the JSON to XML and sends it to another queue `package-deliverer` where the monolithic system picks up the message and runs an internal task (not in scope) to processes the payment.

The monolithic system is being upgraded to be modernized, so a new requirement has arrived to move the payment transactions back to the other systems. The architects have decided that the best way to do this is to reverse the data and convert it from XML back to JSON.

---

## ?? Challenge

We will be using Camel extensions to vastly simplify the process.

- camel-quarkus-xj
- camel-minio
- camel-quarkus-kafka
- camel-quarkus-seda

We have proofed out a workflow as follows:

1. Take a sample data payload
2. Drop it into a file, or, use the REST API
3. Parse the code through Camel and let it work its magic to convert
4. Drop the code parsed/rendered into another queue

We have designed the system to do JSON -> XML. We would like you to finish the process, by reversing it and converting the data from XML -> JSON.

1. Log in to MINIO and create a new bucket named `camel-drop`
2. Create 2 new topics in Kafka:
   - `<user>-package-receiver`
   - `<user>-package-deliverer`
   Generate an XML formatted file (use the `sample.xml` in your resources folder) as a blueprint of the XML.
3. Generate your topics @ https://kafka-ui-citizens-demo.apps.cluster-domain
   - In your Java folder, edit the `Routes.java` file to write the reversing logic. HINT: Flip the same functions that are currently in there.
     - a. Create a DSL that will take an HTTP request that ingests `<XML>`. This DSL can be accessed via CURL or an API tool like POSTMAN.
     - b. Create a DSL that picks up a sample file in `<user>-package-receiver`, renders XML -> JSON.
     - c. Create a DSL that receives a file from your drop folder; this will require you to create a new bucket in MINIO.
4. For all of the above 3 (file, REST, Kafka) send the final result to your `<user>-package-deliverer` topic.
   - DSL Helper for REST: https://camel.apache.org/manual/rest-dsl.html
5. If you are testing the endpoint, you can use this string:

```bash
curl -X POST   http://citizens-demo-camel-s3-serverless-route-project-devspaces.apps.cluster-domain/process/json2xml   -H "Content-Type: application/json"   -H "Accept: application/xml"   -d '{"transactionId":"1","customerId":"12221","amount":100,"currency":"USD","status":"Pending","message":"Transaction from company x"}'
<?xml version="1.0" encoding="UTF-8"?>
<transaction>
    <transactionId>1</transactionId>
    <customerId>12221</customerId>
    <amount>100</amount>
    <currency>USD</currency>
    <status>Pending</status>
    <message>Transaction from company x</message>
</transaction>
```

6. Validate your output ?? http://kafka-consumer-app-citizens-demo.apps.cluster-domain

---

## ✅ Key Takeaways

- You have created a Camel Route
- Parsed one file format to another with 1 line of code
- Harnessed the power of Camel Transformations / Extensions
- Quickly leveraged a file bucket and Kafka
