#!/usr/bin/python

import getopt, sys
import urllib

from urllib2 import Request, urlopen, HTTPError, URLError

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

def respond(url, req):
    hreq = Request("%s/search/%s/%s/%s?%s" % (url,
                                              req["path"][0], req["path"][2], req["path"][3],
                                              urllib.urlencode(req["query"])))
    for header in ["Accept", "If-None-Match"]:
        if header in req["headers"]:
            hreq.add_header(header, req["headers"][header])
    try:
        f = urlopen(hreq)
        body = f.read()
        headers = {"Content-Type":f.info().getheader("Content-Type"), "ETag":f.info().getheader("ETag")}
        sys.stdout.write("%s\n" % json.dumps({"code":f.getcode(),"headers":headers,"body":body}))
    except HTTPError, e:
        sys.stdout.write("%s\n" % json.dumps({"code":e.code}))
    except URLError, e:
        sys.stdout.write("%s\n" % json.dumps({"code":500, "body":"is couchdb-lucene running?\n"}))
    sys.stdout.flush()

def main():
    try:
        opts, args = getopt.getopt(sys.argv[1:], 'u:', ['url='])
    except getopt.GetoptError:
        sys.exit(2)

    url = 'http://localhost:5985'
    for opt, arg in opts:
        if opt in ('-u', '--url'):
            url = arg
        else:
            assert False, "unhandled option " + opt

    for req in requests():
        respond(url, req)

if __name__ == "__main__":
    main()
