#!/bin/bash

iptables -t nat -F

for port in 80 443 8000 8081 8181 8443
do
  iptables -t nat -A PREROUTING -i eth0 -p tcp --dport $port -j REDIRECT --to-port 8080
done

iptables-save

# iptables -t nat -L -n -v # list rules