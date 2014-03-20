# HAR-Export

Dumps the content of the MongoDB **http-collector** instance as a HAR file.


## Viewing in the har-viewer

```
node index.js | har -
```



Testing the user-registry:

```
register() {
  key="xpto"
  user=$1
  nonce=`curl http://localhost:1234/nonce`
  auth=`echo -n "$user#$nonce" | openssl dgst -sha1 -hmac "$key" -binary | base64`
  curl -v -X PUT "http://localhost:1234/registry/$user" -H "X-Nonce: $nonce" -H "Authorization: $auth"
}

register lucastorri
```

Generate a random key:
```
openssl rand -hex 32
```
