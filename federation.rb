#!/usr/bin/ruby

require 'net/http'
require 'json'

servers = %w(localhost localhost)
result = {}

threads=[]
for server in servers
  threads << Thread.new(server) do |url|
    h = Net::HTTP.new(url, 5984)
    resp = h.get('/' + ARGV[0] + '/_fti?' + ARGV[1])
    json = JSON.parse(resp.body)

    if (!result.has_key?('q')) then
      result = json
    else
      # Accounting
      result['total_rows'] += json['total_rows']
      # Merge in new rows
      result['rows'].concat json['rows']      
      if json.has_key?('sort_order')
        result['rows'].sort!{|a,b| a['sort_order'] <=> b['sort_order']}
      else
        result['rows'].sort!{|b,a| [a['score'],a['_id']] <=> [b['score'],b['_id']]}
      end
      # Drop extraneous rows.
      result['rows'].slice!(result['limit']..result['rows'].size)      
    end
  end
end
# Wait for all responses
threads.each{|thr| thr.join}

puts JSON.generate result
