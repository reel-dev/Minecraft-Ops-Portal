# Registry digests resolved on 2026-10-25. No host Java installation required.
FROM docker:29.7.2-cli@sha256:3f4743208d2338c934d7b8bcfbe1bb54c0b2355c510ad5e0f31c0c4a54bd704e AS cli
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
COPY --from=cli /usr/local/bin/docker /usr/local/bin/docker
RUN mkdir -p /app/data /home/agent/.docker /run/docker-read-only \
    && chown -R 10001:10001 /app /home/agent /run/docker-read-only
ENV HOME=/home/agent
USER 10001:10001
# A separate context avoids a direct Docker socket in the application container.
RUN docker --config /home/agent/.docker context create read-only \
    --docker host=unix:///run/docker-read-only/docker.sock
WORKDIR /app
COPY agent/target/smp-host-agent-0.1.0-SNAPSHOT.jar /app/agent.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/agent.jar"]
