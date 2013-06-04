# http-collector

A proxy that captures all requests made in between for debugging purposes. Suppose you have a system _A_ that depends on a second one, _B_:

```
  +----------+                          +----------+
  | System A |  ==== http request ===>  | System B |
  +----------+                          +----------+
  a.system.com                          b.system.com
```

_B_ is identified by its hostname _b.system.com_ and _A_ is configured to access _B_ through that hostname. By changing system's A configuration to point to _a.system.com.local_ instead, the same request will be routed through **http-collector** and requests/responses stored for later inspections:

```
  +----------+                          +-----------+
  | System A |  ==== http request ===>  |   http-   |
  +----------+                          | collector |
  a.system.com                          |  +-----+  |
                                        |  | req |  |
  +----------+                          |  | res |  |
  | System B |  <=== fwd. request ====  |  +-----+  |
  +----------+                          +-----------+
  b.system.com                            **.local
```

**http-collector** does not change anything on the request other than the host (_b.system.com.local_ to _b.system.com_) before forwarding the request. It runs on port `8080` and is able to receive both `http` and `https` traffic through the same port.


## Implementation

**http-collector** is built atop of [netty](http://netty.io/), both to create the server and issue the request to the destination system on an asynchronous fashion.




## Setup guidelines for Mac OS


### DNS proxy

Add the following entry to your `/etc/hosts`:

```
127.0.0.1	*.local
```

and run

```
python dnsproxy.py -s `dig | grep -oEi "\d+\.\d+\.\d+\.\d+" | head -n 1`
```

**[dnsproxy](https://code.google.com/p/marlon-tools/source/browse/tools/dnsproxy/dnsproxy.py)** is a simple DNS proxy server with support for wildcard hosts. The line above will get the current DNS in use by your system and proxy it. For convenience, there is a copy of **dnsproxy** available on the `dns` directory.

Finally, change your network settings to user `127.0.0.1` as your DNS server.


### Firewall rules

Current rules can be seen with:

```
sudo ipfw show
```

and cleaned with:

```
sudo ipfw flush
```

**http-collector** requires that all ports be forwarded to the app's single one (`8080`):

One single rule can be added with:

```
sudo ipfw add 100 fwd 127.0.0.1,8080 tcp from any to any 80 in
```

or for the most common http ports utilised:

```
for port in 80 443 8000 8081 8181 8443
do
	sudo ipfw add 100 fwd 127.0.0.1,8080 tcp from any to any $port in
done
```


## Future plans

* Make a Linux VM with all the firewall settings set, a DNS proxy, and ready to deploy a jar with **http-collector**
* Append the received parts to a log that can be used later on. Options:
	* Assuming that a _full request_ is composed by a request and a response of both the frontend and the backend, use MongoDB to store that _full request_. That should be put on a same collection and can be used to store partial HTTP messages (chunks). Each collection is identified by an unique Id that represents that _full request_, while chunks are identified inside the collection by its type (request/response), to what direction (client -> frontend/frontend -> backend), and an ordering number for each chunk (0, 1, …). Each part should be accompanied by a timestamp on when it was received. _Full request_ Ids can, for now, be generated on the **http-collector** itself, being moved on the future to an Id generation app.
	* Use Apache Kafka to store chunks, also identified like described on the option below. Create a second app that consumes that information and merge it all and then finally stores it.



## Usage example

Once everything has been set up:


```
curl -v http://127.0.0.1:80 -H "Host: www.google.com.local"
| * About to connect() to 127.0.0.1 port 80 (#0)
| *   Trying 127.0.0.1...
| * connected
| * Connected to 127.0.0.1 (127.0.0.1) port 80 (#0)
| > GET / HTTP/1.1
| > User-Agent: curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5
| > Accept: */*
| > Host: www.google.com.local
| >
| < HTTP/1.1 302 Found
| < Location: http://www.google.de/
| < Cache-Control: private
| < Content-Type: text/html; charset=UTF-8
| < Set-Cookie: PREF=ID=4d4fa449123f0b64:FF=0:TM=1369682844:LM=1369682844:S=FMQBhUJzFDield1X; expires=Wed, 27-May-2015 19:27:24 GMT; path=/; domain=.google.com
| < Set-Cookie: NID=67=EK0i8Td32tqRncHO4a7BFdh-QczsF8DOLExbLVfHqg0s8AClt9uJTaXnFiYi3W32gd7gZ7aj6yXTzeeCc72d0RZHTUC7hi5eP0itjFmvD8YvKsfFnfgCcOx9Iqooe6Z_; expires=Tue, 26-Nov-2013 19:27:24 GMT; path=/; domain=.google.com; HttpOnly
| < P3P: CP="This is not a P3P policy! See http://www.google.com/support/accounts/bin/answer.py?hl=en&answer=151657 for more info."
| < Date: Mon, 27 May 2013 19:27:24 GMT
| < Server: gws
| < X-XSS-Protection: 1; mode=block
| < X-Frame-Options: SAMEORIGIN
| < Transfer-Encoding: chunked
| <
| <HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
| <TITLE>302 Moved</TITLE></HEAD><BODY>
| <H1>302 Moved</H1>
| The document has moved
| <A HREF="http://www.google.de/">here</A>.
| </BODY></HTML>
| * Connection #0 to host 127.0.0.1 left intact
| * Closing connection #0
```

```
curl -kv https://127.0.0.1:443 -H "Host: www.google.com.local"
| * About to connect() to 127.0.0.1 port 443 (#0)
| *   Trying 127.0.0.1...
| * connected
| * Connected to 127.0.0.1 (127.0.0.1) port 443 (#0)
| * SSLv3, TLS handshake, Client hello (1):
| * SSLv3, TLS handshake, Server hello (2):
| * SSLv3, TLS handshake, CERT (11):
| * SSLv3, TLS handshake, Server key exchange (12):
| * SSLv3, TLS handshake, Server finished (14):
| * SSLv3, TLS handshake, Client key exchange (16):
| * SSLv3, TLS change cipher, Client hello (1):
| * SSLv3, TLS handshake, Finished (20):
| * SSLv3, TLS change cipher, Client hello (1):
| * SSLv3, TLS handshake, Finished (20):
| * SSL connection using EDH-RSA-DES-CBC3-SHA
| * Server certificate:
| *    subject: C=KR; ST=Kyunggi-do; L=Seongnam-si; O=The Netty Project; OU=Contributors; CN=securechat.example.netty.gleamynode.net
| *    start date: 2008-06-19 05:45:40 GMT
| *    expire date: 2008-06-19 05:45:40 GMT
| *    common name: securechat.example.netty.gleamynode.net (does not match '127.0.0.1')
| *    issuer: C=KR; ST=Kyunggi-do; L=Seongnam-si; O=The Netty Project; OU=Contributors; CN=securechat.example.netty.gleamynode.net
| *    SSL certificate verify result: self signed certificate (18), continuing anyway.
| > GET / HTTP/1.1
| > User-Agent: curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5
| > Accept: */*
| > Host: www.google.com.local
| >
| < HTTP/1.1 302 Found
| < Location: https://www.google.de/
| < Cache-Control: private
| < Content-Type: text/html; charset=UTF-8
| < Set-Cookie: PREF=ID=acd5f69836355076:FF=0:TM=1369683252:LM=1369683252:S=VouDaGojYd-iSf2e; expires=Wed, 27-May-2015 19:34:12 GMT; path=/; domain=.google.com
| < Set-Cookie: NID=67=k2OyBW5sZE0za6RRduAG0JJS5clyE3qkSMuLpOKnWmeuPyF3DNDPPbe2YWd1RCkFqXiJqvXgkdGzZYvvm8Zu0qkJDF4c-0tds-jCDuQFvbceKdXDXX8qtkXroxpjNE6a; expires=Tue, 26-Nov-2013 19:34:12 GMT; path=/; domain=.google.com; HttpOnly
| < P3P: CP="This is not a P3P policy! See http://www.google.com/support/accounts/bin/answer.py?hl=en&answer=151657 for more info."
| < Date: Mon, 27 May 2013 19:34:12 GMT
| < Server: gws
| < X-XSS-Protection: 1; mode=block
| < X-Frame-Options: SAMEORIGIN
| < Transfer-Encoding: chunked
| <
| <HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
| <TITLE>302 Moved</TITLE></HEAD><BODY>
| <H1>302 Moved</H1>
| The document has moved
| <A HREF="https://www.google.de/">here</A>.
| </BODY></HTML>
| * Connection #0 to host 127.0.0.1 left intact
| * Closing connection #0
| * SSLv3, TLS alert, Client hello (1):
```


