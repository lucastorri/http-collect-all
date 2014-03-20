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

/etc/init/har-export.conf:
  file.managed:
    - source: salt://hc-dump/har-export.conf

/etc/init/user-registry.conf:
  file.managed:
    - source: salt://hc-dump/user-registry.conf


/etc/init.d/hc-dump:
  file.symlink:
    - target: /lib/init/upstart-job

/etc/init.d/har-export:
  file.symlink:
    - target: /lib/init/upstart-job

/etc/init.d/user-registry:
  file.symlink:
    - target: /lib/init/upstart-job


hc-dump-install:
  cmd.run: 
    - name: npm install --production
    - cwd: /opt/hc-dump
    - require:
      - pkg: npm

user-registry-key:
  cmd.run:
    - name: openssl rand -hex 32 > /opt/hc-dump/config/key

#TODO http://stackoverflow.com/questions/10801158/how-stable-is-s3fs-to-mount-an-amazon-s3-bucket-as-a-local-directory
/mnt/hc-dump:
  file.directory:
    - user: hc-dump
    - group: hc-dump
    - recurse:
      - user
      - group

/usr/local/bin/hc-dump:
  file.symlink:
    - target: /opt/hc-dump/bin/hc-dump

/usr/local/bin/har-export:
  file.symlink:
    - target: /opt/hc-dump/bin/har-export

/usr/local/bin/user-registry:
  file.symlink:
    - target: /opt/hc-dump/bin/user-registry