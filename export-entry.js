var fs = require('fs');
var q = require('q');

var models = require('./models');
var parser = require('./http-parser');


var groups = {
  'FRONTEND_INBOUND': parser.request,
  'BACKEND_OUTBOUND': parser.request,
  'BACKEND_INBOUND': parser.response,
  'FRONTEND_OUTBOUND': parser.response
};

var exit = {
  withSuccess: function() {
    process.exit(0);
  },
  withError: function(err) {
    err && console.error(err.stack);
    process.exit(1);
  },
  withNoEntryLeft: function() {
    process.exit(2);
  }
};

//TODO to enable the script for parallel use: http://docs.mongodb.org/manual/tutorial/isolate-sequence-of-operations/
var req = models.Closed.findOneQ({})
.then(function(closed) {
  if (!closed) exit.withNoEntryLeft();
  return models.Metadata.findOneQ({request: closed.request});
})
.then(function(metadata) {
  var driver = chunksGroupedByLayerAndDirection(metadata.request);
  return models.Req.mapReduceQ(driver).then(function(groups) {
    var grouped = {};
    groups.forEach(function(group) {
      grouped[group._id] = ('chunks' in group.value) ? group.value.chunks : [group.value];
    })
    return grouped;
  }).then(function(grouped) {
    return {
      metadata: metadata,
      grouped: grouped
    };
  });
});

var files = req.then(function(req) {
  return Object.keys(req.grouped).map(function(group) {
    var httpMessage = parse(req.grouped[group], groups[group]);
    var m = req.metadata;
    var file = m.user + '_' + m.bucket + '_' + m.request + '_' + group + '.json';
    return q.nfcall(fs.writeFile, file, JSON.stringify(httpMessage));
  });
})
.all();

var id = req.then(function(req) {
  return req.metadata.request;
});

q.all(id, files).then(function(id, files) {
  return [
    models.Closed.removeQ({request: id}),
    models.Metadata.removeQ({request: id}),
    models.Req.removeQ({request: id})
  ];
})
.all()
.then(exit.withSuccess)
.fail(exit.withError);

function chunksGroupedByLayerAndDirection(request) {
  return {
    scope: { request: request },
    map: function () {
      this.request == request && emit(this.layer + '_' + this.direction, this);
    },
    reduce: function (k, vals) { 
      return { chunks: vals.sort(function(a,b) { return a.index - b.index; }) }; 
    }
  };
}

function parse(chunks, message) {
  var p = parser.Parser(message);
  chunks.forEach(function(chunk) {
    try {
      p.parse(chunk.content.buffer);
    } catch(err) {
      console.log(chunk);
      throw err;
    }
  });
  return p.end();
}