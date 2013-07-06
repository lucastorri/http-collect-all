var q = require('q');

module.exports = function(id) {
  var har = baseHar(id);
  var promises = [];

  return {
    done: function() {
      return q.all(promises).then(function() { return har; })
    },
    entry: function(message) {
      message.push(har);
      promises.push(addEntry.apply(addEntry, message));
    }
  };
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
          id: 'all',
          title: 'http-collector bucket ' + id,
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

function addEntry(req, res, metadata, har) {

  var start = new Date(req.extra.timings[0]).toJSON();
  if (!har.log.pages[0].startedDateTime) {
    har.log.pages[0].startedDateTime = start;
  }

  var time = res.extra.timings[res.extra.timings.length - 1] - req.extra.timings[0]
  var url = (metadata.ssl ? 'https' : 'http') + '://' + req.headers['host'] + ':' + metadata.port + req.path;

  var entry = {
    pageref: 'all',
    startedDateTime: start,
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
        mimeType: findHeader(res, 'content-type').value,
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
    connection: metadata.request,
    comment: ""
  };

  if (findHeader(res, 'transfer-encoding').value == 'chunked') {
    res.content = unchunk(res.content);
  }

  var deferred = q.defer();

  decode(res.content, findHeader(res, 'content-encoding').value, function(a, buf) {
    entry.response.content.text = buf.utf8Slice();
    har.log.entries.push(entry);
    deferred.resolve(entry);
  });

  return deferred.promise;
}

function findHeader(msg, name) {
  return (msg.headers.filter(function(h) { return h.name == name; })[0] || {})
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