package webpipe

import (
	"errors"
	"fmt"
)

// Sentinel error categories. Classify any *APIError with errors.Is:
//
//	doc, err := client.Scrape(ctx, url, nil)
//	if errors.Is(err, webpipe.ErrAuthentication) { ... }
var (
	ErrBadRequest     = errors.New("webpipe: bad request (400)")
	ErrAuthentication = errors.New("webpipe: authentication failed (401)")
	ErrForbidden      = errors.New("webpipe: forbidden (403)")
	ErrNotFound       = errors.New("webpipe: not found (404)")
	ErrRateLimit      = errors.New("webpipe: rate limited (429)")
	ErrServer         = errors.New("webpipe: server error (5xx)")
)

// APIError is an error response returned by the WebPipe API. It always
// carries the server's original error message and the HTTP status code.
type APIError struct {
	StatusCode int
	Message    string
	Body       any
}

func (e *APIError) Error() string {
	return fmt.Sprintf("webpipe: [%d] %s", e.StatusCode, e.Message)
}

// Is maps the HTTP status code onto the sentinel category errors.
func (e *APIError) Is(target error) bool {
	switch {
	case e.StatusCode == 400:
		return target == ErrBadRequest
	case e.StatusCode == 401:
		return target == ErrAuthentication
	case e.StatusCode == 403:
		return target == ErrForbidden
	case e.StatusCode == 404:
		return target == ErrNotFound
	case e.StatusCode == 429:
		return target == ErrRateLimit
	case e.StatusCode >= 500:
		return target == ErrServer
	}
	return false
}

// CrawlError is returned when a crawl job reaches status "failed".
type CrawlError struct {
	JobID  string
	Errors []any
}

func (e *CrawlError) Error() string {
	return fmt.Sprintf("webpipe: crawl job %s failed: %v", e.JobID, e.Errors)
}

// PollTimeoutError is returned when polling does not finish within the
// allotted timeout (crawl / wait-until-running).
type PollTimeoutError struct {
	What    string
	Timeout float64 // seconds
}

func (e *PollTimeoutError) Error() string {
	return fmt.Sprintf("webpipe: %s did not finish within %.0fs", e.What, e.Timeout)
}
