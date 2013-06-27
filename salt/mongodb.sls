mongodb:
  pkg.installed

mongodb-service:
  service.dead:
    - name: mongodb
    - require:
      - pkg: mongodb
