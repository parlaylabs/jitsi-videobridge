#!/bin/bash
/_/jitsi-videobridge/resources/install-docker.sh
wait
running=`ps -ef | grep docker | grep -vc grep`
if [[ "$running" -lt "1" ]]; then
  sudo service docker start
fi
docker login --username="highfive" --password="face2face" --email="" docker.fatline.io
docker pull docker.fatline.io/cloud-dev/disco-dvcs:latest
docker stop disco-dvcs && docker rm disco-dvcs
docker run --privileged --net=host --name=disco-dvcs -d docker.fatline.io/cloud-dev/disco-dvcs:latest
