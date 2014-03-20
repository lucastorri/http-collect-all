authbind:
  pkg.installed

http-collector:
  group:
    - present
  user.present:
    - gid_from_name: true
    - createhome: false
    - require:
      - group: http-collector

/etc/authbind/byport/80:
  file.managed:
    - mode: 755
    - user: http-collector
    - require:
      - pkg: authbind
      - user: http-collector

/etc/authbind/byport/443:
  file.managed:
    - mode: 755
    - user: http-collector
    - require:
      - pkg: authbind
      - user: http-collector


/etc/init/http-collector.conf:
  file.managed:
    - source: salt://http-collector/http-collector.conf

/etc/init.d/http-collector:
  file.symlink:
    - target: /lib/init/upstart-job