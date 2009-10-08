#!/usr/bin/python

import sys
import httplib

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
    # remove '_fti' from path
    path = req["path"]
    del path[1]

    url = "/search/%s/%s/%s?" % (path[0],path[1],path[2])
    for key in req["query"]:
        url += "%s=%s&" % (key, req["query"][key])
    conn = httplib.HTTPConnection('localhost', 5985)
    if "Accept-Encoding" in req["headers"]:
        del req["headers"]["Accept-Encoding"]
    conn.request(req["verb"], url, "", req["headers"])
    resp = conn.getresponse()
    body = resp.read()
    conn.close()

    if body.startswith("{"):
        sys.stdout.write("%s\n" % json.dumps({"code":resp.status,"json":json.loads(body)}))
    else:
        sys.stdout.write("%s\n" % json.dumps({"code":resp.status,"body":body}))
    sys.stdout.flush()

def main():
    for req in requests():
        respond(req)

if __name__ == "__main__":
    main()
