#
# This is the external process hook script that forwards requests from CouchDB
# to couchdb-lucene.  It requires the following:
#
# * A Ruby interpreter (http://www.ruby-lang.org/en/downloads/)
# * The RubyGems package management system (http://docs.rubygems.org/read/chapter/3)
# * The 'json' ruby gem (sudo gem install json)
#
# == Configuration options
# couchdb.lucene.host=mymachine.somehost.com - The name of the machine hosting
#    the couchdb-lucene server (default is localhost)
# couchdb.lucene.port=1234 - The port the couchdb-lucene is running on
#    (default is 5985)
# couchdb.log.dir=/some/log/dir - The directory you wish to use to store the
#    logs from this script (default is /var/log/couchdb)
#
# == Usage
# Include the following in your CouchDB local.ini file in the [external] section:
# fti=/path/to/ruby /usr/lib/couchdb/couchdb-lucene/couchdb-external-hook.rb [options]
#
# Example:
# fti=/path/to/ruby /usr/lib/couchdb/couchdb-lucene/couchdb-external-hook.rb couchdb.log.dir=/some/log/dir couchdb.lucene.port=1234
#
# == Author
# John Wood
# john_p_wood@yahoo.com
# http://johnpwood.net
#

require 'net/http'
require 'zlib'
require 'rubygems'
require 'json'


DEFAULT_LOG_DIR = '/var/log/couchdb/'
DEFAULT_LUCENE_HOST = 'localhost'
DEFAULT_LUCENE_PORT = 5985
LOGFILE = 'couchdb-external-hook.log'


def args
  @@arg_array ||= begin
    args = {}
    ARGV.each do |token|
      key, value = token.strip.split('=')
      args[key] = value
    end
    args
  end
end

def log(message)
  t = Time.now
  @@log << t.strftime("%Y-%m-%d %H:%M:%S") + ":#{t.usec}" << " :: " << message << "\n"
  @@log.flush
end

def logfile
  @@logfile ||= begin
    logdir = args['couchdb.log.dir'] || DEFAULT_LOG_DIR
    logdir << '/' unless logdir =~ /\/$/
    logdir + LOGFILE
  end
end

def respond_with_error(code, message)
  puts ({"code" => code, "body" => message}).to_json
  STDOUT.flush
  true
end

def parse_path(path)
  [path[0], path[2], path[3]]
end

def build_query_string(input_hash)
  couch_query = input_hash['query']
  if couch_query.nil?
    respond_with_error(400, "No query found in request.") and return
  end

  database, design_doc, view = parse_path(input_hash['path'])
  if database.nil? ||design_doc.nil? || view.nil?
    respond_with_error(400, 'Path must contain datbase name, design doc name, and view name.') and return
  end

  command = couch_query['q'].nil? ? 'info' : 'search' 
  lucene_query = "/#{command}/#{database}/#{design_doc}/#{view}?"
  couch_query.each do |name, value|
    lucene_query << "#{name}=#{value}&"
  end
  lucene_query.sub!(/[&?]$/, '')    

  log "couchdb-lucene query: #{lucene_query}"
  lucene_query
end

def send_query_to_couchdb_lucene(input_hash, query_string)
  @@host ||=  args['couchdb.lucene.host'] || DEFAULT_LUCENE_HOST
  @@port ||= (args['couchdb.lucene.port'] || DEFAULT_LUCENE_PORT).to_i

  connection = Net::HTTP.new(@@host, @@port)
  resp, data = connection.get(query_string, input_hash['headers'])

  data = inflate(data)
  resp.delete("Content-Encoding")

  log "couchdb-lucene response: (#{resp.code}) #{data}"

  if resp.code.to_i == 200
    headers = {}
    resp.each_capitalized {|k,v| headers[k] = v}

    response = {"code" => resp.code, "headers" => headers}
    resp.content_type =~ /json/ ? response['json'] = data : response['body'] = data
    
    log "Response to CouchDB: #{response.to_json}"
    puts response.to_json
    STDOUT.flush
  else
    respond_with_error(resp.code, resp.message)
  end
rescue Exception => e
  log "Error encountered when sending request to couchdb-lucene: #{e.inspect}"
  respond_with_error(500, "Unexpected error when sending request to couchdb-lucene.")
end

def inflate(string)
  gz = Zlib::GzipReader.new(StringIO.new(string))
  gz.read
rescue
  string
end

def execute_query(line)
  log "Request from CouchDB: #{line}"
  input_hash = JSON.parse(line)
  query_string = build_query_string(input_hash)
  send_query_to_couchdb_lucene(input_hash, query_string) unless query_string.nil?
end


#
# Entry point
#
File.open(logfile, 'a') do |log|
  @@log = log
  log "Searcher started."
  
  execute_query(STDIN.gets)  # Uncomment for testing; reloads script every time

#  while (line = STDIN.gets)   #
#    execute_query(line)       # Comment out for testing; loads script once
#  end                         #
end
