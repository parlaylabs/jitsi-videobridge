#
# To build:
# Move this Dockerfile to a location where it is on the same level as 
#  the 'jitsi-videobridge' dir
# > docker build -t <image name> .
# Example run:
# > docker run -d --net=host -e REST_PORT=<desired REST port> -e MEDIA_SINGLE_PORT=<desired media port> <image name>
#
FROM ubuntu:trusty

MAINTAINER bbaldino <brian@highfive.com>

ENV VIDEOBRIDGE_BUILDNUM="586"

# These variables could be overriden to configure Prosody 
#ENV VIDEOBRIDGE_SECRET="fUb8iJ5aRk5Urg1Y"
#ENV XMPP_DOMAIN="example.com"
#ENV XMPP_SUBDOMAIN="jitsi-videobridge"
#ENV XMPP_HOST="localhost"
#ENV XMPP_PORT="5275"

ENV APIS="rest"
ENV MEDIA_SINGLE_PORT="5005"
ENV REST_PORT="8081"
ENV MEDIA_MIN_PORT="10000"
ENV MEDIA_MAX_PORT="20000"
ENV ENV_NAME="stagingrtc"


# Install videobridge and dependencies
USER root
# To get 'add-apt-repository'
RUN apt-get update
RUN apt-get install -y software-properties-common
# Add a separate repo to install java 1.8
RUN add-apt-repository -y ppa:webupd8team/java
# Need this to auto-accept the java license prompt that would pop up otherwise
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections

RUN apt-get -y install \
  maven \
  git \
  oracle-java8-installer \
  oracle-java8-set-default

# Create videobridge user
RUN mkdir /jvb && \
  groupadd -r jvb && \
  useradd -r -g jvb -d /jvb -s /sbin/nologin -c "Jitsi Videobridge User" jvb && \
  chown -R jvb:jvb /jvb

# Configure and run
USER jvb

WORKDIR /jvb
ADD . jitsi-videobridge
USER root
RUN chown -R jvb:jvb .
USER jvb

WORKDIR /jvb/jitsi-videobridge
RUN mvn clean install

RUN mkdir /jvb/.sip-communicator
RUN echo "org.jitsi.impl.neomedia.transform.srtp.SRTPCryptoContext.checkReplay=false" > /jvb/.sip-communicator/sip-communicator.properties

# Create the run script
RUN echo "#!/bin/bash" > run.sh
# We want to be able to pass these in at container-run-time, not container-build-time.  So echo these lines into the run script
#  just before we run it
# NOTE: we escape the environment variables here so the run.sh script evaluates them at runtime, otherwise they'll be set as the default set in
#  this file
RUN echo "echo \"org.jitsi.videobridge.SINGLE_PORT_HARVESTER_PORT=\$MEDIA_SINGLE_PORT\" >> /jvb/.sip-communicator/sip-communicator.properties" >> run.sh
RUN echo "echo \"org.jitsi.videobridge.rest.jetty.port=\$REST_PORT\" >> /jvb/.sip-communicator/sip-communicator.properties" >> run.sh

RUN echo "mvn exec:java -Dexec.args=\"--apis=$APIS\" -Djava.library.path=/jvb/jitsi-videobridge/lib/native/linux-64 -Djava.util.logging.config.file=/jvb/jitsi-videobridge/lib/\"\$ENV_NAME.properties\" -Dnet.java.sip.communicator.SC_HOME_DIR_NAME=.sip-communicator" >> run.sh
RUN chmod +x ./run.sh

CMD ./run.sh
