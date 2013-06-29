nodejs:
  pkg.installed

npm:
  pkg.installed

hc-dump:
  group:
    - present
  user.present:
    - gid_from_name: true
    - createhome: false
    - require:
      - group: hc-dump

/etc/init/hc-dump.conf:
  file.managed:
    - source: salt://hc-dump/hc-dump.conf

/etc/init.d/hc-dump:
  file.symlink:
    - target: /lib/init/upstart-job

hc-dump-install:
  cmd.run: 
    - name: npm install --production
    - cwd: /opt/hc-dump
    - require:
      - pkg: npm

/mnt/hc-dump:
  file.directory:
    - user: hc-dump
    - group: hc-dump
    - recurse:
      - user
      - group

/usr/local/bin/hc-dump:
  file.symlink:
    - target: /opt/hc-dump/hc-dump