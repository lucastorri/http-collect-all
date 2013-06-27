redis-server:
  pkg.installed

# redis-service:
#   service.running:
#     - name: redis-server
#     - require:
#       - pkg: redis-server