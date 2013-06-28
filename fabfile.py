from __future__ import with_statement
from fabric.api import *
import boto.ec2

env.hosts = ['root@127.0.0.1:2222']
env.passwords = {'root@127.0.0.1:2222': 'root'}

def create_box():
  conn = boto.ec2.connect_to_region('us-west-2')
  a = conn.run_instances(
    'ami-fb68f8cb',
    instance_type='t1.micro',
    security_groups=['quick-start-1'])
  print(a)

def copy_base_files():
  run('rm -rf /srv/salt')
  put('salt', '/srv/')
  put('bootstrap.sh', '/opt', mirror_local_mode=True)

def copy_all_files():
  copy_base_files()
  copy_http_collector()

def make_http_collector():
  local('mvn -f http-collector/pom.xml package')

def copy_http_collector():
  make_http_collector()
  put('http-collector/target/http-collector', '/usr/local/bin/', mirror_local_mode=True)

def bootstrap():
  copy_all_files()
  run('/opt/bootstrap.sh')