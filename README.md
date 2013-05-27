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


## Usage example

Once everything has been set up:

```
curl -kv https://127.0.0.1:443 -H "Host: www.google.com.local"
```

```
curl -v http://127.0.0.1:80 -H "Host: www.google.com.local"
```



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


### Firewall rules

Current rules can be seen with:

```
sudo ipfw show
```

and cleaned with:

```
sudo ipfw flush
```

**http-collector** required that all ports be forwarded to the app single one (`8080`):

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
