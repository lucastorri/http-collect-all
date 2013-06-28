pip install boto
pip install fabric


Create file ~/.boto with

```
[Credentials]
aws_access_key_id = YOURACCESSKEY
aws_secret_access_key = YOURSECRETKEY
```


/usr/local/share/python/fab


salt-call --local state.highstate
