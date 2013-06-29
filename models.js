#!/usr/bin/env node

var mongoose = require('mongoose-q')(require('mongoose'));

mongoose.connect('mongodb://127.0.0.1/requests');
var db = mongoose.connection;
db.on('error', function(err) {
  console.error('connection error:', err)
  process.exit(1);
});
exports.db = db;

var reqSchema = mongoose.Schema({
  request: String,
  layer: String,
  direction: String,
  index: Number,
  timestamp: Number,
  content: Buffer
}, { collection : 'chunks' });
exports.Req = mongoose.model('Req', reqSchema);

var metadataSchema = mongoose.Schema({
  request: String,
  port: Number,
  ssl: Boolean,
  gzip: Boolean,
  user: String,
  bucket: String
}, { collection: 'metadata' });
exports.Metadata = mongoose.model('Metadata', metadataSchema);

var closedSchema = mongoose.Schema({
  layer: String,
  request: String,
  timestamp: Number
}, { collection: 'closed' });
exports.Closed = mongoose.model('Closed', closedSchema);
