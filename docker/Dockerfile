#
# To build from the jitsi-videobridge root dir:
# > docker login -u highfive -p <password> -e eng@highfive.com docker.fatline.io
# > docker build -f docker/Dockerfile -t docker.fatline.io/<env>/jitsi-videobridge .
# Example run:
# > docker run -d --net=host -e ENV_NAME="environment-name" -e MEDIA_SINGLE_PORT=<desired media port> docker.fatline.io/jitsi-videobridge
#
FROM docker.fatline.io/java:8
ARG ENV_NAME=local

ENV MEDIA_SINGLE_PORT="5005"
ENV REST_PORT="8081"
ENV TCP_FALLBACK_PORT=""
ENV TCP_FALLBACK_MAPPED_PORT=""
ENV ENV_NAME=$ENV_NAME

RUN mkdir /root/.sip-communicator
COPY docker/sip-communicator.properties /root/.sip-communicator/sip-communicator.properties

RUN mkdir /jvb

WORKDIR /jvb

COPY target/jitsi-videobridge-linux-x64-1.0-SNAPSHOT/lib /jvb/lib
COPY target/jitsi-videobridge-linux-x64-1.0-SNAPSHOT/jitsi-videobridge*.jar /jvb/lib

COPY docker/run.sh /jvb/run.sh
RUN chmod +x ./run.sh

CMD ./run.sh
