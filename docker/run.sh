#!/bin/bash

echo "org.jitsi.videobridge.SINGLE_PORT_HARVESTER_PORT=$MEDIA_SINGLE_PORT" >> /root/.sip-communicator/sip-communicator.properties
echo "org.jitsi.videobridge.TCP_HARVESTER_PORT=$TCP_FALLBACK_PORT" >> /root/.sip-communicator/sip-communicator.properties
echo "org.jitsi.videobridge.TCP_HARVESTER_MAPPED_PORT=$TCP_FALLBACK_MAPPED_PORT" >> /root/.sip-communicator/sip-communicator.properties
echo "org.jitsi.videobridge.rest.jetty.port=$REST_PORT" >> /root/.sip-communicator/sip-communicator.properties
export LD_LIBRARY_PATH="/jvb/lib/native/linux-64"
export JAVA_OPTS="-Xms3G -Xmx3G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:+UseStringDeduplication -XX:+PrintGCTimeStamps -Xloggc:gc.log -XX:+PrintGCDetails -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=jvb-mem.hprof -XX:+CrashOnOutOfMemoryError"

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
mainClass="org.jitsi.videobridge.Main"
for f in "$SCRIPT_DIR"/lib/*.jar
do
  JITSI_CLASSPATH="$JITSI_CLASSPATH":$f
done

java -Djava.library.path=/jvb/lib/native/linux-64 -Djava.util.logging.config.file=/jvb/lib/$ENV_NAME.properties -Dnet.java.sip.communicator.SC_HOME_DIR_NAME=.sip-communicator \
-Djava.library.path=$LD_LIBRARY_PATH -agentpath:/jvb/lib/yourkit/linux-x86-64/libyjpagent.so=sessionname=jitsi-videobridge -cp $JITSI_CLASSPATH $mainClass "--apis=rest"
