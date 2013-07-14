#!/usr/bin/env node

var redis = require('redis').createClient();
var app = require('./auth-express');

var port = process.env['UR_PORT'];

app.verifyValues = function(req) {
  return [req.params.user];
};

redis.on('ready', function() {

  app.put('/registry/:user', app.auth(function(req, res) {
    redis.set(req.params.user, true);
    res.send();
  }));

  app.delete('/registry/:user', app.auth(function(req, res) {
    redis.set(req.params.user, false);
    res.send();
  }));
  
  app.get('/registry/:user', app.auth(function(req, res) {
    redis.get(req.params.user, function(err, v) {
      res.send(v == 'true' ? 200 : 404);
    });
  }));

  app.listen(port);
  console.log('Starting server on port ' + port);

});