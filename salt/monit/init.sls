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

monit-restart-all:
  cmd.run:
    - name: /usr/bin/monit restart all
    - require:
      - file: /etc/monit/monitrc
