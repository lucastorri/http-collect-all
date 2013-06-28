/etc/init/http-collector.conf:
  file.managed:
    - source: salt://http-collector/http-collector.conf

/etc/init.d/http-collector:
  file.symlink:
    - target: /lib/init/upstart-job