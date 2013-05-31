#!/usr/bin/env node

var mongoose = require('mongoose');
var prettyjson = require('prettyjson');
var parser = require('./http-parser');
var Buffer = require('buffer').Buffer;

mongoose.connect('mongodb://127.0.0.1/requests');
var db = mongoose.connection;
db.on('error', console.error.bind(console, 'connection error:'));

db.once('open', function callback () {

  var reqSchema = mongoose.Schema({
      request: String,
      layer: String,
      direction: String,
      index: Number,
      timestamp: Number,
      content: String
  }, { collection : 'chunks' });
  var Req = mongoose.model('Req', reqSchema);


  Req.find(function (err, reqs) {
    if (err) { /* TODO handle err */ }
    var byRequest = groupBy('request', reqs);

    {
      var firstReq = byRequest[Object.keys(byRequest)[0]];

      var byLayer = groupBy('layer', firstReq);
      var frontend = byLayer['FRONTEND'];
      var backend = byLayer['BACKEND'];

      var frontendByDirection = groupBy('direction', frontend);
      var backendByDirection = groupBy('direction', backend);

      var frontendInbound = frontendByDirection['INBOUND'];
      var frontendOutbound = frontendByDirection['OUTBOUND'];
      var backendInbound = backendByDirection['INBOUND'];
      var backendOutbound = backendByDirection['OUTBOUND'];

      var req = parse(backendOutbound, parser.request);
      var res = parse(backendInbound, parser.response);
      console.log(req);
      console.log(res);

      var entry = {
        pageref: "page_0",
        startedDateTime: "2009-04-16T12:07:23.596Z",
        time: 50,
        request: {
          method: "GET",
          url: "http://www.example.com/path/?param=value",
          httpVersion: "HTTP/1.1",
          cookies: [],
          headers: [],
          queryString: [],
          postData: {
            mimeType: 'text/html'
          },
          headersSize: 150,
          bodySize: 0,
          comment: ""
        },
        response: {
          status: 200,
          statusText: "OK",
          httpVersion: "HTTP/1.1",
          cookies: [],
          headers: [],
          content: {
            size: 0,
            mimeType: 'text/html'
          },
          redirectURL: "",
          headersSize: 160,
          bodySize: 850,
          comment: ""
        },
        cache: {
        },
        timings: {
          blocked: 0,
          dns: -1,
          connect: 15,
          send: 20,
          wait: 38,
          receive: 12,
          ssl: -1,
          comment: ""
        },
        serverIPAddress: "10.0.0.1",
        connection: "52492",
        comment: ""
      }
    }

    var har = baseHar();
    har.log.entries.push(entry);
    //log(har);
  });  

});

function groupBy(key, array) {
  var grouped = {};
  array.forEach(function(e) {
    var k = e[key];
    if (!grouped[k]) {
      grouped[k] = [];
    }
    grouped[k].push(e);
  });
  return grouped;
}

function baseHar() {
  return {
    log: {
      version : "1.2",
      creator : {
        name: "",
        version: "",
        comment: ""
      },
      browser : {
        name: "",
        version: "",
        comment: ""
      },
      pages: [
        {
          startedDateTime: "2009-04-16T12:07:25.123+01:00",
          id: "bucket_XYZ",
          title: "Bucket XYZ",
          pageTimings: {
            onContentLoad: 1720,
            onLoad: 2500,
            comment: ""
          },
          comment: ""
        }
      ],
      entries: [],
      comment: ""
    }
  };
}

function parse(chunks, message) {
  var p = parser.Parser(message);
  chunks.forEach(function(chunk) {
    p.parse(new Buffer(chunk.content));
  });
  return p.end();
}

function log(o) {
  console.log(prettyjson.render(o));
  console.log(JSON.stringify(o));
}

var Buffer = require('buffer').Buffer;
