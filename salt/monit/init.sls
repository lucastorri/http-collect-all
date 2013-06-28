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

monit-reload:
  cmd.run:
    - name: /usr/sbin/service monit reload
    - require:
      - file: /etc/monit/monitrc

monit-validate:
  cmd.run:
    - name: /usr/bin/monit validate
    - require:
      - cmd: monit-reload
