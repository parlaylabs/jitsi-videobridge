#!/bin/bash

cd $WORKSPACE/fatline-head
docker login -u highfive -p face2face -e eng@highfive.com docker.fatline.io
docker pull docker.fatline.io/jitsi-videobridge
python test/videobridge_test_runner.py jitsi-videobridge
