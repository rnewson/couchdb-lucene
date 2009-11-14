#!/usr/bin/python

import httplib
import optparse as op
import sys
import traceback
import urllib

try:
    import json
except:
    import simplejson as json

__usage__ = "%prog [OPTIONS]"

def options():
    return [
        op.make_option('-u', '--url', dest='url',
            default="127.0.0.1",
            help="Host of the CouchDB-Lucene indexer. [%default]"),
        op.make_option('-p', '--port', dest='port', type='int',
            default=5985,
            help="Port of the CouchDB-Lucene indexer. [%default]")
    ]

def main():
    parser = op.OptionParser(usage=__usage__, option_list=options())
    opts, args = parser.parse_args()

    if len(args):
        parser.error("Unrecognized arguments: %s" % ' '.join(args))

    res = httplib.HTTPConnection(opts.url, opts.port)
    for req in requests():
        try:
            resp = respond(res, req)
        except Exception, e:
            body = traceback.format_exc()
            resp = mkresp(500, body, {"Content-Type": "text/plain"})
            res = httplib.HTTPConnection(opts.url, opts.port)

        sys.stdout.write(json.dumps(resp))
        sys.stdout.write("\n")
        sys.stdout.flush()

def requests():
    line = sys.stdin.readline()
    while line:
        yield json.loads(line)
        line = sys.stdin.readline()

def respond(res, req):
    path = req.get("path", [])

    if len(path) != 4:
        body = "\n".join([
            "Invalid path: %s" % '/'.join([''] + path),
            "Paths should be: /db_name/_fti/docid/index_name?q=...",
            "'docid' is from the '_design/docid' that defines index_name"
        ])
        return mkresp(400, body, {"Content-Type": "text/plain"})

    path = '/'.join(['', 'search', path[0], path[2], path[3]])
    path = '?'.join([path, urllib.urlencode(req["query"])])

    req_headers = {}
    for h in req.get("headers", []):
        if h.lower() in ["accept", "if-none-match"]:
            req_headers[h] = req["headers"][h]

    res.request("GET", path, headers=req_headers)
    resp = res.getresponse()

    resp_headers = {}
    for h, v in resp.getheaders():
        if h.lower() in ["content-type", "etag"]:
            resp_headers[h] = resp.getheader(h, [])

    return mkresp(resp.status, resp.read(), resp_headers)

def mkresp(code, body, headers=None):
    ret = {"code": code, "body": body}
    if headers is not None:
        ret["headers"] = headers
    return ret

if __name__ == "__main__":
    main()

