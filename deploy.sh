#!/usr/bin/env bash
set -euo pipefail

KUBERNETES_NAMESPACE=$(oc project -q)
echo "?? Using namespace: $KUBERNETES_NAMESPACE"

# Build & deploy with Quarkus Kubernetes extension
mvn clean package \
    -DskipTests -Dquarkus.kubernetes.deploy=true \
    2>&1 | tee build.log

# Apply additional OpenShift resources if present
if [ -d src/main/k8s ]; then
  echo "?? Applying k8s resources from src/main/k8s..."
  oc apply -f src/main/k8s/ -n "${KUBERNETES_NAMESPACE}"
fi

echo "? Deployment completed to namespace: ${KUBERNETES_NAMESPACE}"

# wait for pod to be ready (optional)
oc rollout status deployment/minio -n "$KUBERNETES_NAMESPACE"

# grab the console route host
MINIO_CONSOLE=$(oc get route minio-console -n "$KUBERNETES_NAMESPACE" -o jsonpath='{.spec.host}')
CAMEL_ROUTE=$(oc get route citizens-demo-camel-s3-serverless-route -n "$KUBERNETES_NAMESPACE" -o jsonpath='{.spec.host}')

clear

echo
echo "? MinIO deployed in namespace: $KUBERNETES_NAMESPACE"
echo "?? MinIO Credentials: admin / admin123"
echo "?? MinIO Admin URL: https://$MINIO_CONSOLE"
echo "?? Kafka WEB URL: https://kafka-ui-citizens-demo.apps.<cluster-domain>"
echo "?? Kafka Validator: http://kafka-consumer-app-citizens-demo.apps.<cluster-domain>"
echo "?? Camel JSON->XML Service URL: http://$CAMEL_ROUTE/process/json2xml"
