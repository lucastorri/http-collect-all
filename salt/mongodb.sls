mongodb:
  pkg.installed

# mongodb-service:
#   service.running:
#     - name: mongodb
#     - require:
#       - pkg: mongodb

# /etc/default/mongodb:
#   file.managed:
#     - contents: export ENABLE_MONGODB="no"