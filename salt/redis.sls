redis-server:
  pkg.installed

redis-service:
  service.dead:
    - name: redis-server
    - require:
      - pkg: redis-server