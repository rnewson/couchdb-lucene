#!/usr/bin/python

import sys
import urllib

from urllib2 import Request, urlopen, HTTPError

host = "localhost"
port = 5985

try:
    # Python 2.6
    import json
except:
    # Prior to 2.6 requires simplejson
    import simplejson as json

def requests():
    # 'for line in sys.stdin' won't work here
    line = sys.stdin.readline()
    while line:
        yield json.loads(line)
        line = sys.stdin.readline()

def respond(req):
    hreq = Request("http://%s:%s/search/%s/%s/%s?%s" % (host, port,
                                                                req["path"][0], req["path"][2], req["path"][3],
                                                                urllib.urlencode(req["query"])))
    if "If-None-Match" in req["headers"]:
        hreq.add_header("If-None-Match", req["headers"]["If-None-Match"])
    try:
        f = urlopen(hreq)
        body = f.read()
        headers = f.info().dict
        sys.stdout.write("%s\n" % json.dumps({"code":f.getcode(),"headers":headers,"body":body}))
    except HTTPError, e:
        sys.stdout.write("%s\n" % json.dumps({"code":e.code}))
    sys.stdout.flush()

def main():
    for req in requests():
        respond(req)

if __name__ == "__main__":
    main()
