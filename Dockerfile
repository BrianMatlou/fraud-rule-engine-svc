FROM artifacts.capitecbank.co.za/odin-docker-shared-local/capiteccorretto:25-alpine-jdk

ARG JAR_FILE=fraud-rule-engine-0.0.1-SNAPSHOT.jar
ENV JAR_FILE=$JAR_FILE

COPY target/$JAR_FILE /

USER 1000
EXPOSE 8080
ENV TZ="Africa/Johannesburg"

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /$JAR_FILE"]