#!/bin/bash

cd $WORKSPACE/jitsi-videobridge

mvn clean install test

docker login -u highfive -p face2face -e eng@highfive.com docker.fatline.io
docker build --build-arg ENV_NAME=${environment} -f docker/Dockerfile -t docker.fatline.io/${environment}/jitsi-videobridge .
docker push docker.fatline.io/${environment}/jitsi-videobridge
