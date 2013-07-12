#!/usr/bin/env node

var fs = require('fs');
var q = require('q');

var models = require('./models');
var parser = require('./http-parser');
var output = process.argv[2];

var writefile = q.denodeify(fs.writeFile);
var mkdirp = require('mkdirp');

var groups = {
  'FRONTEND_INBOUND': parser.request,
  'BACKEND_OUTBOUND': parser.request,
  'BACKEND_INBOUND': parser.response,
  'FRONTEND_OUTBOUND': parser.response
};

var exit = {
  withSuccess: function(id) {
    console.log('Success ' + id)
    process.exit(0);
  },
  withError: function(err) {
    err && console.error(err.stack);
    process.exit(1);
  },
  withNoEntryLeft: function() {
    console.error('None Left');
    process.exit(20);
  }
};

//TODO to enable the script for parallel use: http://docs.mongodb.org/manual/tutorial/isolate-sequence-of-operations/
//TODO make it more efficient with trigger: http://stackoverflow.com/questions/9691316/how-to-listen-for-changes-to-a-mongodb-collection
var req = models.Closed.findOneQ({})
.then(function(closed) {
  if (!closed) exit.withNoEntryLeft();
  return models.Metadata.findOneQ({request: closed.request});
})
.then(function(metadata) {
  return chunksGroupedByLayerAndDirection(metadata.request).then(function(grouped) {
    return {
      metadata: metadata,
      grouped: grouped
    };
  });
});

var files = req.then(function(req) {
  var m = req.metadata;
  var dir = output + '/' + m.user + '/' + m.bucket + '/' + m.request + '/';
  mkdirp.sync(dir)
  var files = req.grouped.map(function(group) {
    var groupName = group.layer + '_' + group.direction;
    var httpMessage = parse(group.all, groups[groupName]);
    var file = dir + groupName + '.json';
    return writefile(file, JSON.stringify(httpMessage));
  });
  files.push(writefile(dir + 'metadata.json', JSON.stringify(req.metadata), { encoding: 'utf8', mode: '0666', flag: 'w' }));
  return files;
})
.all();

var id = req.then(function(req) {
  return req.metadata.request;
});

q.all([id, files]).then(function(all) {
  return models.Closed.removeQ({request: all[0]});
})
.then(function() {
  return id;
})
.then(exit.withSuccess)
.fail(exit.withError);

function chunksGroupedByLayerAndDirection(request) {
  return q.nfcall(
    models.Req.collection.group.bind(models.Req.collection),
    ['layer', 'direction'], 
    { request: request },
    { all: [] },
    function(obj, prev) { prev.all.push(obj); },
    false
  );
}

function parse(chunks, type) {
  var p = parser.Parser(type);
  chunks.forEach(function(chunk) {
    try {
      p.parse(chunk.content.buffer);
    } catch(err) {
      console.log(chunk);
      throw err;
    }
  });
  var parsed = p.end();
  parsed.extra = {
    timings: chunks.map(function(c) { return c.timestamp; })
  };
  return parsed;
}