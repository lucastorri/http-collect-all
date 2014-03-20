# http-collect-all

This is a group of four different but related projects, each one with their own *README.md*, the most interesting being **http-collect**, which is the heart of the whole thing. They were all in private git repos and I decide to make them open because I haven't worked on them for almost a year.

The idea was stolen from a comercial website (I cannot recal which anymore) that provides the same functionality. I just decided I could do an alternative solution.

The idea is to have a hosted man-in-the-middle proxy and by modifying just a bit the endpoint you have in your system pointing for dependent downstream system, you can capture all http trafic that you app is doing for later analysis.

## Projects and Buzzwords

* **http-collect**: a hosted man-in-the-middle http/https proxy. Parts of the http traffic are stored on mongodb;
  * java
  * mongodb
  * redis
  * netty
* **hc-dump**: dumps finished http requests collected by **http-collect** to json files in the [har format](http://www.softwareishard.com/blog/har-12-spec/);
  * nodejs
  * mongodb
* **netztee**: web UI where a given user could visualize his/her collected http requests;
  * scala
  * play framework
  * har viewer
* **http-collect-box**: automation for deploying the previous projects in Amazon AWS.
  * python
  * fabric
  * boto
  * salt stack


## Things I would like to see

* Nice UI
* A Javascript DSL for searching the recorded traffic, modify, and replay it.


## Related

* [mitmproxy](http://mitmproxy.org/)