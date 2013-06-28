var Buffer = require('buffer').Buffer;

var states = {
  started: 'STARTED',
  newLine: 'NEW_LINE',
  header: 'HEADER',
  body: 'BODY'
};

var transitions = function() {
  var transition = {};
  transition[states.started] = {
    next: function(char) {
      return (char === 0x0a) ? states.newLine : states.started;
    }
  };
  transition[states.newLine] = {
    next: function(char) { 
      return (char === 0x0a) ? states.body : states.header;
    }
  };
  transition[states.header] = { 
    next: function(char) {
      return (char === 0x0a) ? states.newLine : states.header;
    }
  };
  transition[states.body] = {
    next: function() {
      return states.body;
    }
  };
  return transition
};

var messages = (function() {
  var request = transitions();
  request[states.started].action = function(line, req) {
    var m = line.asciiSlice().match(/([^\s]+)\s+([^\s]+)\s+([^\s]+)[\n\r]+/);
    req.method = m[1];
    req.path = m[2];
    req.version = m[3];
  };
  request[states.newLine].action = function() {};
  request[states.header].action = function(header, req) {
    if (!req.cookies) req.cookies = [];
    if (!req.headers) req.headers = [];
    var h = parseHeader(header);
    if (h.name == 'cookie' || h.name == 'set-cookie') {
      req.cookies.push(h.value); //TODO transform to a cookie object
    } else {
      req.headers[h.name] = h.value;
    }
    req.headers.push(h);
  };
  request[states.body].action = function(content, req) {
    req.content = content;
  };

  var response = transitions();
  response[states.started].action = function(line, res) {
    var m = line.asciiSlice().match(/([^\s]+)\s+([^\s]+)\s+([^\s]+)[\n\r]+/);
    res.version = m[1];
    res.statusCode = m[2];
    res.statusMessage = m[3];
  };
  response[states.newLine].action = function() {};
  response[states.header].action = request[states.header].action;
  response[states.body].action = request[states.body].action;

  function parseHeader(header) {
    var m = header.asciiSlice().match(/([^:]+):\s*(.*)[\n\r]+/);
    return {
      name: m[1].toLowerCase(),
      value: m[2]
    };
  }

  return {
    request: request,
    response: response
  };
})();


function Parser(message) {

  var state = states.started;
  var line = [];
  var parsed = {};

  function parse(buffer) {
    for (var c, i = 0; c = buffer[i++], c != undefined;) {
      line.push(c);

      if (state === states.body || c === 0x0d) continue;

      var t = message[state];
      state = t.next(c);
      if (c === 0x0a) {
        t.action(new Buffer(line), parsed);
        line = [];
      }

    }
  }

  function end() {
    var t = message[state];
    t.action(new Buffer(line), parsed);
    return parsed;
  }

  return {
    parse: parse,
    end: end
  };

}

exports.request = messages.request;
exports.response = messages.response;
exports.Parser = Parser;


/*
var p = Parser(messages.request);
p.parse(new Buffer("POST /hello HTTP/1.1\r\n"));
p.parse(new Buffer("Host: www.nokia.com\r\n"));
p.parse(new Buffer("Content-Length: 10\r\n"));
p.parse(new Buffer("\r\n"));
p.parse(new Buffer("0123456789"));

console.log(p.end());



var p = Parser(messages.response);
p.parse(new Buffer("HTTP/1.1 200 OK\r\n"));
p.parse(new Buffer("Content-Type: text/html; charset=UTF-8\r\n"));
p.parse(new Buffer("Content-Length: 11\r\n"));
p.parse(new Buffer("\r\n"));
p.parse(new Buffer("hello world"));

console.log(p.end());
*/