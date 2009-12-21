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
        op.make_option('--remote-host', dest='remote_host',
            default="localhost",
            help="Hostname of the couchdb-lucene server. [%default]"),
        op.make_option('--remote-port', dest='remote_port', type='int',
            default=5985,
            help="Port of the couchdb-lucene server. [%default]"),
        op.make_option('--local-host', dest='local_host',
            default="localhost",
            help="Hostname of this couchdb instance. [%default]"),
        op.make_option('--local-port', dest='local_port', type='int',
            default=5984,
            help="Port of this couchdb instance. [%default]"),
    ]

def main():
    parser = op.OptionParser(usage=__usage__, option_list=options())
    opts, args = parser.parse_args()

    if len(args):
        parser.error("Unrecognized arguments: %s" % ' '.join(args))
    res = httplib.HTTPConnection(opts.remote_host, opts.remote_port)
    for req in requests():
        try:
            resp = respond(res, req, opts.local_host, opts.local_port)
        except Exception, e:
            body = traceback.format_exc()
            resp = mkresp(500, body, {"Content-Type": "text/plain"})
            res = httplib.HTTPConnection(opts.remote_host, opts.remote_port)

        sys.stdout.write(json.dumps(resp))
        sys.stdout.write("\n")
        sys.stdout.flush()

def requests():
    line = sys.stdin.readline()
    while line:
        yield json.loads(line)
        line = sys.stdin.readline()

def respond(res, req, host, port):
    path = req.get("path", [])

    if len(path) != 4:
        body = "\n".join([
            "Invalid path: %s" % '/'.join([''] + path),
            "Paths should be: /db_name/_fti/ddocid/index_name?q=...",
            "'ddocid' is from the '_design/ddocid' that defines index_name"
        ])
        return mkresp(400, body, {"Content-Type": "text/plain"})

    # Drop name of external hook.
    del path[1]

    if req["query"] == {}:
        path = '/'.join(['', 'info', host, str(port)] + path)
    else:
        path = '/'.join(['', 'search', host, str(port)] + path)
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

