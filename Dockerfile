FROM navikt/java:11
COPY build/libs/app.jar app.jar
ENV JAVA_OPTS="-agentpath:/opt/cdbg/cdbg_java_agent.so \
               -Dcom.google.cdbg.module=isdialogmote \
               -Dcom.google.cdbg.version=1.0 \
               -Dcom.google.cdbg.breakpoints.enable_canary=false \
               -Dlogback.configurationFile=logback.xml"

RUN mkdir /opt/cdbg && \
     wget -qO- https://storage.googleapis.com/cloud-debugger/compute-java/debian-wheezy/cdbg_java_agent_gce.tar.gz | \
     tar xvz -C /opt/cdbg

