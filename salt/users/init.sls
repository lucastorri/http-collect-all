/etc/firewall_rules.sh:
  file.managed:
    - source: salt://users/firewall_rules.sh
    - mode: 755

apply-firewall-rules:
  cmd.run:
    - name: '/etc/firewall_rules.sh'
    - require:
      - file: /etc/firewall_rules.sh
