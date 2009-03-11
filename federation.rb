#!/usr/bin/ruby

require 'net/http'
require 'json'

servers = %w(localhost localhost localhost localhost)
total_rows = 0
page_size = 0
top_rows = []
threads=[]
for server in servers
  threads << Thread.new(server) do |url|
    h = Net::HTTP.new(url, 5984)
    resp = h.get('/enron/_fti?q=content:enron&sort=/_id&skip=10')
    json = JSON.parse(resp.body)
    
    # Accumulate federated result
    page_size = json['limit']

    total_rows = total_rows + json['total_rows']
    for row in json['rows']
      # remember origin server.
      row['server'] = server
      # Add it.
      top_rows << row
    end    
    # Sort the partial result
    if json.has_key?('sort_order')
      top_rows.sort!{|a,b| a['sort_order'] <=> b['sort_order']}
    else
      top_rows.sort!{|a,b| a['_id'] <=> b['_id']}
    end
    # Drop extraneous hits.
    top_rows.slice!(page_size..top_rows.size)
  end
end
# Wait for all responses
threads.each{|thr| thr.join}

result =  JSON.generate ['total_rows' => total_rows, 'rows' => top_rows]
puts result
