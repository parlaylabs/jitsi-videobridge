#!/bin/bash
# Check if already installed, skip if so.
installed=$(dpkg -l | grep -wc docker-engine)
if [[ $installed == "1" ]]; then
  exit 0
fi
# General apt update and certificate install
sudo dpkg --configure -a
sudo apt-get --assume-yes update
sudo apt-get --assume-yes install apt-transport-https ca-certificates

# Install keys for docker APT PPA
sudo apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D
echo deb https://apt.dockerproject.org/repo ubuntu-xenial main | sudo tee /etc/apt/sources.list.d/docker.list

# Install kernel pre-requisites
sudo apt-get --assume-yes update
sudo apt-get --assume-yes install linux-image-extra-$(uname -r) linux-image-extra-virtual

# Install docker
sudo apt-get --assume-yes update
sudo apt-get --assume-yes purge lxc-docker
sudo apt-get --assume-yes purge docker.io
apt-cache policy docker-engine
sudo apt-get --assume-yes install docker-engine

# Add group & user for docker run permissions
sudo groupadd docker
sudo gpasswd -a ubuntu docker
