#!/usr/bin/env node

var q = require('q');
var fs = require('fs');
var har = require('./har');

var port = process.env['HE_PORT'];
var dumps = process.argv[2];

var readdir = q.denodeify(fs.readdir)
var readfile = q.denodeify(fs.readFile)

function createHar(user, bucket) {
  var dir = dumps + '/' + user + '/' + bucket + '/';

  return readdir(dir)
    .then(function(requests) {
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
      return JSON.stringify(har);
    });
}

function listBuckets(user) {
  var dir = dumps + '/' + user + '/';
  return readdir(dir);
}

function message(dir, request) {
  var d = dir + '/' + request + '/';
  var req = readfile(d + 'FRONTEND_INBOUND.json');
  var res = readfile(d + 'FRONTEND_OUTBOUND.json');
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

var app = require('./auth-express');
app.verifyValues = function(req) {
  return [req.params.user, req.params.bucket].filter(function(v) { return v; });
};

app.get('/:user/:bucket', app.auth(function(req, res) {
  res.set('Content-Type', 'application/json');
  createHar(req.params.user, req.params.bucket).then(function(json) {
    res.send(json);
  })
  .fail(function(err) {
    res.status(404);
    res.send({ error: err });
  });
}));

app.get('/:user', app.auth(function(req, res) {
  res.set('Content-Type', 'application/json');
  listBuckets(req.params.user).then(function(buckets) {
    res.send({ buckets: buckets });
  })
  .fail(function(err) {
    res.status(404);
    res.send({ error: err });
  });
}));

app.listen(port);
