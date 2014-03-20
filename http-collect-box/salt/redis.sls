redis-server:
  pkg.installed

redis-service:
  service.dead:
    - name: redis-server
    - require:
      - pkg: redis-server

# echo -e "SET lucastorri true\r\n" | netcat localhost 6379 # set myself