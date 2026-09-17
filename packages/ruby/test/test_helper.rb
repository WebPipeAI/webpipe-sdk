# frozen_string_literal: true

require "minitest/autorun"
require "webrick"
require "json"
require "socket"

require_relative "../lib/webpipe"

# Servlet that delegates every HTTP method to a dispatcher proc.
# (mount_proc only registers do_GET/do_POST — DELETE would 405.)
class DispatchServlet < WEBrick::HTTPServlet::AbstractServlet
  def initialize(server, dispatcher)
    super(server)
    @dispatcher = dispatcher
  end

  def service(req, res)
    @dispatcher.call(req, res)
  end
end

# Spins up a WEBrick mock server on an ephemeral port. Handlers are
# [method, path] => ->(req, res) or a queue of responses as [status, body].
class MockServer
  attr_reader :url, :requests

  # responses: { "METHOD /path" => [[status, body_hash], ...] } — shifted per call.
  def initialize(responses)
    @responses = responses.transform_values { |v| v.is_a?(Array) && v.first.is_a?(Array) ? v.dup : [v] }
    @requests = []
    @server = WEBrick::HTTPServer.new(
      Port: 0, Logger: WEBrick::Log.new(File::NULL), AccessLog: []
    )
    @url = "http://127.0.0.1:#{@server.config[:Port]}"

    dispatcher = lambda do |req, res|
      key = "#{req.request_method} #{req.path}"
      queue = @responses[key]
      status, body = queue && !queue.empty? ? queue.shift : [404, { "error" => "no mock for #{key}" }]
      @requests << { method: req.request_method, path: req.path,
                     body: req.body && !req.body.empty? ? JSON.parse(req.body) : nil,
                     headers: req.header }
      res.status = status
      res["Content-Type"] = "application/json"
      res.body = JSON.generate(body)
    end
    @server.mount("/", DispatchServlet, dispatcher)
    @thread = Thread.new { @server.start }
    sleep 0.05 until up?
  end

  def up?
    TCPSocket.new("127.0.0.1", @server.config[:Port]).close
    true
  rescue Errno::ECONNREFUSED
    false
  end

  def shutdown
    @server.shutdown
    @thread.join(2)
  end

  def last_request = @requests.last
end
