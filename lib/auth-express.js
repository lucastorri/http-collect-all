
var express = require('express');
var crypto = require('crypto');

var app = express();
var key = process.env['KEY'];
var nonces = {};

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

function auth(req, values) {
  var reqNonce = req.header('X-Nonce');
  var reqHmac = req.header('Authorization');

  if (!nonces[reqNonce]) return false;

  values.push(reqNonce);
  var hmac = crypto.createHmac('sha1', key);
  hmac.update(values.join('#'));
  var expectedHmac = hmac.digest('base64');
  
  delete nonces[reqNonce];
  var valid = reqHmac == expectedHmac;
  return valid;
}

module.exports = app;
app.verifyValues = function(req) {
  return [];
};
app.auth = function(callback) {
  return function(req, res) {
    var values = app.verifyValues(req);
    var valid = auth(req, values);
    if (valid) {
      callback(req, res);
    } else {
      res.status(403);
      res.send();
    }
  }
};