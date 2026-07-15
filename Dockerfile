FROM sugamflow-common-libs:local AS build
WORKDIR /workspace

COPY docker/maven-docker-settings.xml /root/.m2/settings.xml
COPY docker/mvn-package-retry.sh /usr/local/bin/mvn-package-retry.sh
COPY ledger-service ./ledger-service
RUN sed -i 's/\r$//' /usr/local/bin/mvn-package-retry.sh \
    && chmod +x /usr/local/bin/mvn-package-retry.sh \
    && sh /usr/local/bin/mvn-package-retry.sh ledger-service/pom.xml \
    && cp /workspace/ledger-service/target/*-SNAPSHOT.jar /workspace/ledger-service/app.jar

FROM sugamflow-jre:local
WORKDIR /app
COPY --from=build /workspace/ledger-service/app.jar app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "app.jar"]
