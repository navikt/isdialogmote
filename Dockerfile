FROM navikt/java:common AS java-common
FROM openjdk:11-slim

RUN umask o+r

COPY --from=java-common /init-scripts /init-scripts
COPY --from=java-common /entrypoint.sh /entrypoint.sh
COPY --from=java-common /run-java.sh /run-java.sh
COPY --from=java-common /dumb-init /dumb-init

RUN apt-get update && apt-get install -y wget locales

RUN sed -i -e 's/# nb_NO.UTF-8 UTF-8/nb_NO.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LC_ALL="nb_NO.UTF-8"
ENV LANG="nb_NO.UTF-8"
ENV TZ="Europe/Oslo"
ENV APP_BINARY=app
ENV APP_JAR=app.jar
ENV MAIN_CLASS="Main"
ENV CLASSPATH="/app/WEB-INF/classes:/app/WEB-INF/lib/*"

WORKDIR /app

EXPOSE 8080

RUN groupadd -r apprunner && useradd -r -g apprunner apprunner
RUN chown -R apprunner /app
RUN chown -R apprunner /init-scripts
RUN chown apprunner /run-java.sh

RUN mkdir /opt/cdbg && \
     wget -qO- https://storage.googleapis.com/cloud-debugger/compute-java/debian-wheezy/cdbg_java_agent_gce.tar.gz | \
     tar xvz -C /opt/cdbg

USER apprunner

ENTRYPOINT ["/dumb-init", "--", "/entrypoint.sh"]

COPY build/libs/app.jar app.jar
ENV JAVA_OPTS="-agentpath:/opt/cdbg/cdbg_java_agent.so \
               -Dcom.google.cdbg.module=isdialogmote \
               -Dcom.google.cdbg.version=1.0 \
               -Dcom.google.cdbg.breakpoints.enable_canary=false \
               -Dlogback.configurationFile=logback.xml"

