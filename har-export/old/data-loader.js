var prettyjson = require('prettyjson');

var models = require('./models');
var har = require('./har-exporter');



models.db.once('open', function callback() {
  
  function bucketsMR() {
    var o = {};

    o.map = function () {
      emit({ user: this.user, bucket: this.bucket }, this);
    };

    o.reduce = function (k, vals) { 
      return { requests: vals }; 
    };

    return o;
  }

  function chunksMR(requests) {
    var o = {};

    o.scope = {
      requests: requests
    };

    o.map = function () {
      if (requests.indexOf(this.request) != -1) emit(this.request, this);
    };

    o.reduce = function (k, vals) {
      return { chunks: vals.sort(function(a,b) { return a.index - b.index; }) };
    };

    return o;
  } 

  models.Metadata.mapReduce(bucketsMR(), function (err, buckets) {
    if (err) console.log(err) && process.exit(1);
    
    var processed = 0;
    buckets.forEach(function(bucketResponse) {
      if (!(bucketResponse.value instanceof Array)) bucketResponse.value = [bucketResponse.value];
      
      var requests = {};
      bucketResponse.value.forEach(function(request) {
        requests[request.request] = { metadata: request };
      });

      var bucket = {
        id: bucketResponse._id,
        requests: requests
      };

      models.Req.mapReduce(chunksMR(Object.keys(requests)), function (err, chunksPerRequest) {
        if (err) console.log(err) && process.exit(1);

        chunksPerRequest.forEach(function(chunks) {
          bucket.requests[chunks._id].chunks = chunks.value.chunks;
        });

        har(bucket);
        if (++processed == buckets.length) process.exit(0);
      });
      
    });

  });

});