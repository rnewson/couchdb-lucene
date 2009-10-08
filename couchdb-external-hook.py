#!/usr/bin/python

import sys
import urllib

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
    f = urllib.urlopen("http://%s:%s/search/%s/%s/%s?%s" % (host, port,
                                                            req["path"][0], req["path"][2], req["path"][3],
                                                            urllib.urlencode(req["query"])))
    body = f.read()
    sys.stdout.write("%s\n" % json.dumps({"code":f.getcode(),"body":body}))
    sys.stdout.flush()

def main():
    for req in requests():
        respond(req)

if __name__ == "__main__":
    main()
