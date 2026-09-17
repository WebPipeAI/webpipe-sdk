# frozen_string_literal: true

require "net/http"
require "json"
require "uri"

module Webpipe
  # Thin net/http wrapper: auth headers, retries with backoff, error mapping.
  # @api private
  class HttpClient
    RETRYABLE_STATUSES = [429, 500, 502, 503, 504].freeze
    RETRYABLE_ERRORS = [
      Net::OpenTimeout, Net::ReadTimeout, SocketError, SystemCallError
    ].freeze

    STATUS_TO_ERROR = {
      400 => BadRequestError,
      401 => AuthenticationError,
      403 => ForbiddenError,
      404 => NotFoundError,
      429 => RateLimitError
    }.freeze

    def initialize(base_url:, api_key:, timeout:, max_retries:, user_agent:)
      @base_uri = URI(base_url)
      @api_key = api_key
      @timeout = timeout
      @max_retries = max_retries
      @user_agent = user_agent
    end

    def get(path) = request("GET", path)
    def post(path, body = nil) = request("POST", path, body)
    def delete(path) = request("DELETE", path)

    def request(method, path, body = nil)
      attempt = 0
      loop do
        response = perform(method, path, body)
        if RETRYABLE_STATUSES.include?(response.code.to_i) && attempt < @max_retries
          attempt += 1
          sleep backoff(response, attempt)
          next
        end
        return handle(response)
      rescue *RETRYABLE_ERRORS
        attempt += 1
        raise if attempt > @max_retries

        sleep(0.5 * (2**attempt))
        retry
      end
    end

    private

    def perform(method, path, body)
      http = Net::HTTP.new(@base_uri.host, @base_uri.port)
      http.use_ssl = @base_uri.scheme == "https"
      http.open_timeout = @timeout
      http.read_timeout = @timeout

      request_class = Net::HTTP.const_get(method.capitalize)
      request = request_class.new(path)
      request["Authorization"] = "Bearer #{@api_key}"
      request["Content-Type"] = "application/json"
      request["User-Agent"] = @user_agent
      request.body = JSON.generate(body) if body

      http.request(request)
    end

    def handle(response)
      status = response.code.to_i
      payload = begin
        body = response.body.to_s
        body.empty? ? {} : JSON.parse(body)
      rescue JSON::ParserError
        {}
      end

      return payload if status < 400

      message = payload.is_a?(Hash) && payload["error"] ? payload["error"] : response.message
      error_class = status >= 500 ? ServerError : (STATUS_TO_ERROR[status] || ApiError)
      raise error_class.new(message, status_code: status, body: payload)
    end

    def backoff(response, attempt)
      retry_after = response["Retry-After"]
      return Float(retry_after) if retry_after

      0.5 * (2**attempt)
    rescue ArgumentError, TypeError
      0.5 * (2**attempt)
    end
  end
end
