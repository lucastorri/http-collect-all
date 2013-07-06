#!/usr/bin/env node

var express = require('express');
var redis = require('redis').createClient();
var crypto = require('crypto');

var port = process.env['UR_PORT'];
var key = process.env['UR_KEY'];;
var nonces = {};

function auth(req) {
  var reqNonce = req.header('X-Nonce');
  var reqHmac = req.header('Authorization');

  if (!nonces[reqNonce]) return false;

  var hmac = crypto.createHmac('sha1', key);
  hmac.update(req.params.user + '#' + reqNonce);
  var expectedHmac = hmac.digest('base64');
  
  delete nonces[reqNonce];
  var valid = reqHmac == expectedHmac;
  return valid;
}

redis.on('ready', function() {
  var app = express();

  app.get('/nonce', function(req, res) {
    crypto.randomBytes(16, function(ex, buf) {
      var nonce = buf.toString('hex');
      setTimeout(function() { 
        delete nonces[nonce];
      }, 10000);
      nonces[nonce] = true;
      res.send(nonce);
    });
  });
  
  app.put('/registry/:user', function(req, res) {
    auth(req) ? redis.set(req.params.user, true) : res.status(403);
    res.send();
  });

  app.delete('/registry/:user', function(req, res) {
    auth(req) ? redis.set(req.params.user, false) : res.status(403);
    res.send();
  });
  
  app.get('/registry/:user', function(req, res) {
    redis.get(req.params.user, function(err, v) {
      !v && res.status(404);
      res.send();
    });
  });

  app.listen(port);
  console.log('Starting server on port ' + port);
});