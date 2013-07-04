#!/usr/bin/env node

var q = require('q');
var fs = require('fs');
var Buffer = require('buffer').Buffer;
var har = require('./har');

var dumps = process.argv[2];
var user = process.argv[3];
var bucket = process.argv[4];

var readdir = q.denodeify(fs.readdir)
var readfile = q.denodeify(fs.readFile)

var dir = dumps + '/' + user + '/' + bucket + '/';

readdir(dir).then(function(requests) {
  return requests.map(function(request) {
    return message(dir, request)
  });
})
.all()
.then(function(messages) {
  var id = user + '-' + bucket;
  var h = har(id);
  messages.forEach(h.entry);
  return h.done();
})
.then(function(har) {
  console.log(har.log);
  process.exit(0);
})
.fail(function(err) {
  console.log(err.stack);
  process.exit(1);
});


function message(dir, request) {
  var d = dir + '/' + request + '/';
  var req = readfile(d + 'BACKEND_OUTBOUND.json');
  var res = readfile(d + 'BACKEND_INBOUND.json');
  var metadata = readfile(d + 'metadata.json');
  return q.all([req, res, metadata]).then(function(messages) {
    return messages.map(function(message) {
      var parsed = JSON.parse(message);
      if (parsed.content) {
        parsed.content = new Buffer(parsed.content);
      }
      return parsed;
    })
  });
}