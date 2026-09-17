package webpipe

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"time"
)

// retryableStatuses are retried with exponential backoff.
var retryableStatuses = map[int]bool{429: true, 500: true, 502: true, 503: true, 504: true}

// httpClient is the internal transport: auth headers, retries, error mapping.
type httpClient struct {
	baseURL    string
	apiKey     string
	maxRetries int
	userAgent  string
	client     *http.Client
}

func (h *httpClient) newRequest(ctx context.Context, method, path string, body any) (*http.Request, error) {
	var reader io.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("webpipe: encode request: %w", err)
		}
		reader = bytes.NewReader(raw)
	}
	req, err := http.NewRequestWithContext(ctx, method, h.baseURL+path, reader)
	if err != nil {
		return nil, fmt.Errorf("webpipe: build request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+h.apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", h.userAgent)
	return req, nil
}

func backoff(res *http.Response, attempt int) time.Duration {
	if res != nil {
		if ra := res.Header.Get("Retry-After"); ra != "" {
			if seconds, err := strconv.ParseFloat(ra, 64); err == nil && seconds >= 0 {
				return time.Duration(seconds * float64(time.Second))
			}
		}
	}
	return time.Duration(0.5*float64(uint(1)<<attempt)) * time.Second
}

// do executes the request with retries and decodes the JSON body into out.
func (h *httpClient) do(ctx context.Context, method, path string, body, out any) error {
	var lastErr error
	for attempt := 0; ; {
		req, err := h.newRequest(ctx, method, path, body)
		if err != nil {
			return err
		}
		res, err := h.client.Do(req)
		if err != nil {
			lastErr = fmt.Errorf("webpipe: %s %s: %w", method, path, err)
			if attempt < h.maxRetries {
				attempt++
				if !sleepCtx(ctx, backoff(nil, attempt)) {
					return ctx.Err()
				}
				continue
			}
			return lastErr
		}

		raw, readErr := io.ReadAll(res.Body)
		res.Body.Close()
		if readErr != nil {
			return fmt.Errorf("webpipe: read response: %w", readErr)
		}

		if retryableStatuses[res.StatusCode] && attempt < h.maxRetries {
			attempt++
			if !sleepCtx(ctx, backoff(res, attempt)) {
				return ctx.Err()
			}
			continue
		}
		if res.StatusCode >= 400 {
			return apiErrorFrom(res.StatusCode, raw)
		}
		if out == nil || len(raw) == 0 {
			return nil
		}
		if err := json.Unmarshal(raw, out); err != nil {
			return fmt.Errorf("webpipe: decode response: %w", err)
		}
		return nil
	}
}

func apiErrorFrom(status int, raw []byte) *APIError {
	apiErr := &APIError{StatusCode: status, Message: http.StatusText(status)}
	var body struct {
		Error string `json:"error"`
	}
	if err := json.Unmarshal(raw, &body); err == nil && body.Error != "" {
		apiErr.Message = body.Error
		apiErr.Body = json.RawMessage(raw)
	}
	return apiErr
}

// sleepCtx sleeps for d, returning false if ctx is done first.
func sleepCtx(ctx context.Context, d time.Duration) bool {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-t.C:
		return true
	}
}
