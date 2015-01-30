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

httpdict = {"etag": "ETag", "content-type": "Content-Type"}


def options():
    return [
        op.make_option('--remote-host', dest='remote_host',
                       default="localhost",
                       help="Hostname of the couchdb-lucene server. [%default]"),
        op.make_option('--remote-port', dest='remote_port', type='int',
                       default=5985,
                       help="Port of the couchdb-lucene server. [%default]"),
        op.make_option('--local-key', dest='key',
                       default="local",
                       help="Configured key name for this couchdb instance. [%default]"),
    ]


def main():
    parser = op.OptionParser(usage=__usage__, option_list=options())
    opts, args = parser.parse_args()

    if len(args):
        parser.error("Unrecognized arguments: %s" % ' '.join(args))
    for req in requests():
        res = httplib.HTTPConnection(opts.remote_host, opts.remote_port)
        try:
            resp = respond(res, req, opts.key)
        except Exception, e:
            body = traceback.format_exc()
            resp = mkresp(500, body, {"Content-Type": "text/plain"})

        res.close()
        sys.stdout.write(json.dumps(resp))
        sys.stdout.write("\n")
        sys.stdout.flush()


def requests():
    line = sys.stdin.readline()
    while line:
        yield json.loads(line)
        line = sys.stdin.readline()


def respond(res, req, key):
    path = req.get("path", [])

    # Drop name of external hook.
    del path[1]

    # URL-escape each part
    for index, item in enumerate(path):
        path[index] = urllib.quote(path[index], safe="")

    path = '/'.join(['', key] + path)
    params = urllib.urlencode(
        dict([k, v.encode('utf-8')] for k, v in req["query"].items()))
    path = '?'.join([path, params])

    req_headers = {}
    for h in req.get("headers", []):
        if h.lower() in ["accept", "if-none-match"]:
            req_headers[h] = req["headers"][h]

    # verb renamed to method in 0.11 onwards.
    if "method" in req:
        method = req["method"]
    else:
        method = req["verb"]

    res.request(method, path, headers=req_headers)
    resp = res.getresponse()

    resp_headers = {}
    for h, v in resp.getheaders():
        if h.lower() in httpdict:
            resp_headers[httpdict[h]] = resp.getheader(h, [])

    return mkresp(resp.status, resp.read(), resp_headers)


def mkresp(code, body, headers=None):
    ret = {"code": code, "body": body}
    if headers is not None:
        ret["headers"] = headers
    return ret


if __name__ == "__main__":
    main()

