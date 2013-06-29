#!/bin/bash

echo Bootstraping saltstack

# Install saltstack
apt-get install python-software-properties -y
add-apt-repository ppa:saltstack/salt -y
add-apt-repository ppa:richarvey/nodejs -y
apt-get update -y
apt-get install salt-minion -y
apt-get install salt-master -y

# Set salt master location and start minion
salt-call --local state.highstate
