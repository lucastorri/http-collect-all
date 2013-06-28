#!/usr/bin/env node

var prettyjson = require('prettyjson');
var Buffer = require('buffer').Buffer;
var zlib = require('zlib');

var parser = require('./http-parser');
var models = require('./models');


var groupName = "test";
var har = baseHar(groupName);


models.db.once('open', function callback() {

  models.Closed.find(function(err, closed) {
    checkError(err);
    var byRequest = groupBy('request', closed);
    var ids = Object.keys(byRequest);

    ids.forEach(function(id) {

      var metadata;
      var reqs;

      models.Metadata.findOne({ request: id }, function(err, m) {
        checkError(err);
        metadata = m;
        go();
      });

      models.Req.find({ request: id }, function(err, r) {
        checkError(err);
        reqs = r;
        go();
      });

      function go() {
        if (metadata && reqs) {
          createEntry(metadata, reqs, function() {
            if (har.log.entries.length == ids.length) {
              har.log.entries = har.log.entries.sort(function(a,b) { return Date.parse(a.startedDateTime) - Date.parse(b.startedDateTime); });
              log(har);
              process.exit();
            }  
          });
        }
      }

    });

    function checkError(err) {
      if (err) {
        console.log(err);
        process.exit(1);
      }
    }

  });

});

function createEntry(metadata, allChunks, done) {

  var id = metadata.request;

  var byLayer = groupBy('layer', allChunks);
  var frontend = byLayer['FRONTEND'];
  var backend = byLayer['BACKEND'];

  var frontendByDirection = groupBy('direction', frontend);
  var backendByDirection = groupBy('direction', backend); 

  var frontendInbound = sortBy('index', frontendByDirection['INBOUND']);
  var frontendOutbound = sortBy('index', frontendByDirection['OUTBOUND']);
  var backendInbound = sortBy('index', backendByDirection['INBOUND']);
  var backendOutbound = sortBy('index', backendByDirection['OUTBOUND']);

  // console.log(allChunks);
  // console.log('frontendInbound: ' + frontendInbound.length);
  // console.log('frontendOutbound: ' + frontendOutbound.length);
  // console.log('backendInbound: ' + backendInbound.length);
  // console.log('backendOutbound: ' + backendOutbound.length);
  // console.log();

  var req = parse(backendOutbound, parser.request);
  var res = parse(backendInbound, parser.response);

  if (!har.log.pages[0].startedDateTime) {
    har.log.pages[0].startedDateTime = new Date(frontendInbound[0].timestamp).toJSON();
  }

  var time = frontendOutbound[frontendOutbound.length - 1].timestamp - frontendInbound[0].timestamp;
  time = (time % 2 == 0) ? time : time - 1;

  var url = (metadata.ssl ? 'https' : 'http') + '://' + req.headers['host'] + ':' + metadata.port + req.path;

  var entry = {
    pageref: groupName,
    startedDateTime: new Date(frontendInbound[0].timestamp).toJSON(),
    time: time,
    request: {
      method: req.method,
      url: url,
      httpVersion: req.version,
      cookies: req.cookies,
      headers: req.headers,
      queryString: [],
      //postData: {
      //  mimeType: req.headers['content-type']
      //},
      headersSize: 0,
      bodySize: req.content.length,
      comment: ""
    },
    response: {
      status: res.statusCode,
      statusText: res.statusMessage,
      httpVersion: res.version,
      cookies: res.cookies,
      headers: res.headers,
      content: {
        size: res.content.length,
        mimeType: res.headers['content-type'],
        text: res.content.utf8Slice()
      },
      redirectURL: "",
      headersSize: 0,
      bodySize: res.content.length,
      comment: ""
    },
    cache: {
    },
    timings: {
      blocked: 0,
      dns: -1,
      connect: 0,
      send: parseInt(time / 2),
      wait: 0,
      receive: parseInt(time / 2),
      ssl: (metadata.ssl) ? 0 : -1,
      comment: ""
    },
    serverIPAddress: "10.0.0.1",
    connection: id,
    comment: ""
  }

  if (res.headers['transfer-encoding'] == 'chunked') {
    res.content = unchunk(res.content);
  }

  decode(res.content, res.headers['content-encoding'], function(a, buf) {
    entry.response.content.text = buf.utf8Slice();
    har.log.entries.push(entry);
    done();
  });
}


function sortBy(key, array) {
  if (!array || array.length == 0) return array;
  var type = typeof array[0][key];
  if (type === 'number') {
    return array.sort(function(a, b) {
      return a - b;
    });
  } else if (type === 'string') {
    return array.sort(function(a,b) {
      return a.localeCompare(b);
    });
  }
  return array.sort(function(a, b) {
    if (a == b) return 0;
    return (a > b) ? 1 : -1;
  });
}

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

function baseHar(id) {
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
          startedDateTime: undefined,
          id: id,
          title: "http-collector bucket #n",
          pageTimings: {
            onContentLoad: 0,
            onLoad: 0,
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
    try {
      p.parse(new Buffer(chunk.content));
    } catch(err) {
      console.log(chunk);
      throw err;
    }
  });
  return p.end();
}

function unchunk(chunkedContent) {
  var content = [];
  for (var i = 0; i < chunkedContent.length;) {
    var lengthBytes = [];
    for (var c; c = chunkedContent[i++], c != 0x0a && c != 0x0d;) {
      lengthBytes.push(c);
    }
    i += 1;

    var lengthString = lengthBytes.map(function(c) { return String.fromCharCode(c); }).join('');
    var chunkLength = parseInt(lengthString, 16);
    
    var limit = i + chunkLength;
    while (i < limit) {
      content.push(chunkedContent[i++]);
    }
    i += 2;
  }
  return new Buffer(content);
}

function decode(content, contentEncoding, callback) {
  switch (contentEncoding) {
    case 'gzip':
      zlib.unzip(content, callback);
      break;
    case 'deflate':
      zlib.inflate(content, callback);
      break;
    default:
      callback(null, content);
      break;
  }
}

function log(o) {
  //console.log(prettyjson.render(o));
  console.log(JSON.stringify(o));
}
