from __future__ import with_statement
from fabric.api import *
import boto.ec2

env.hosts = ['root@127.0.0.1:2222']
env.passwords = {'root@127.0.0.1:2222': 'root'}

def create_box():
  conn = boto.ec2.connect_to_region("us-west-2")
  a = conn.run_instances(
    'ami-fb68f8cb',
    instance_type='t1.micro',
    security_groups=['quick-start-1'])
  print(a)

def copy_files():
  run('rm -rf /srv/salt')
  put('salt', '/srv/')
  put('bootstrap.sh', '/opt', mirror_local_mode=True)

def bootstrap():
  copy_files()
  run('/opt/bootstrap.sh')