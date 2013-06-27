monit:
  pkg.installed

monit-service:
  service.running:
    - name: monit
    - require:
      - pkg: monit

/etc/monit/monitrc:
  file.managed:
    - source: salt://monit/monitrc
    - mode: 600
    - require:
      - pkg: monit