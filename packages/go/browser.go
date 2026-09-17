package webpipe

import (
	"context"
	"fmt"
	"net/http"
	"time"
)

// BrowserResource creates browser sessions. Access it via Client.Browser.
type BrowserResource struct {
	http *httpClient
}

// Create starts a new browser session. The browser boots asynchronously —
// call WaitUntilRunning before sending commands for a controlled experience.
func (r *BrowserResource) Create(ctx context.Context, opts *CreateSessionOptions) (*BrowserSession, error) {
	var body CreateSessionOptions
	if opts != nil {
		body = *opts
	}
	var info SessionInfo
	if err := r.http.do(ctx, http.MethodPost, "/api/v1/browser", body, &info); err != nil {
		return nil, err
	}
	return &BrowserSession{http: r.http, info: info}, nil
}

// BrowserSession is a handle to a persistent headless Chromium on the server.
//
// Server-side facts: sessions expire after 30 minutes idle; at most 2
// concurrent running sessions per user. Always Close when done.
type BrowserSession struct {
	http *httpClient
	info SessionInfo
}

// ID is the session identifier.
func (s *BrowserSession) ID() string { return s.info.ID }

// Status is the last known session status (see SessionStatus* constants).
func (s *BrowserSession) Status() string { return s.info.Status }

// URL is the page the browser is currently on.
func (s *BrowserSession) URL() string { return s.info.URL }

func (s *BrowserSession) base() string { return "/api/v1/browser/" + s.info.ID }

// Refresh fetches the latest session state from the server.
func (s *BrowserSession) Refresh(ctx context.Context) error {
	var info SessionInfo
	if err := s.http.do(ctx, http.MethodGet, s.base(), nil, &info); err != nil {
		return err
	}
	s.info = info
	return nil
}

// WaitUntilRunning blocks until the session is ready to accept commands.
// pollInterval/timeout are seconds; timeout 0 means 60s.
func (s *BrowserSession) WaitUntilRunning(ctx context.Context, pollInterval, timeout float64) error {
	if pollInterval <= 0 {
		pollInterval = 1
	}
	if timeout <= 0 {
		timeout = 60
	}
	deadline := time.Now().Add(time.Duration(timeout * float64(time.Second)))
	for {
		if err := s.Refresh(ctx); err != nil {
			return err
		}
		switch s.info.Status {
		case SessionStatusRunning:
			return nil
		case SessionStatusStopped, SessionStatusError, SessionStatusExpired:
			return fmt.Errorf("webpipe: browser session %s entered terminal status %q", s.info.ID, s.info.Status)
		}
		if time.Now().After(deadline) {
			return &PollTimeoutError{What: "browser session " + s.info.ID, Timeout: timeout}
		}
		if !sleepCtx(ctx, time.Duration(pollInterval*float64(time.Second))) {
			return ctx.Err()
		}
	}
}

// Close stops the session and releases server resources.
func (s *BrowserSession) Close(ctx context.Context) error {
	if err := s.http.do(ctx, http.MethodDelete, s.base(), nil, nil); err != nil {
		return err
	}
	s.info.Status = SessionStatusStopped
	return nil
}

// Navigate jumps to a new URL (with anti-bot handling) and scrapes it.
func (s *BrowserSession) Navigate(ctx context.Context, url string, opts *NavigateOptions) (*Document, error) {
	body := struct {
		URL string `json:"url"`
		NavigateOptions
	}{URL: url}
	if opts != nil {
		body.NavigateOptions = *opts
	}
	return s.scrapePath(ctx, "/navigate", body)
}

// Scrape scrapes the current page without navigating (faster than Navigate).
func (s *BrowserSession) Scrape(ctx context.Context, opts *NavigateOptions) (*Document, error) {
	var body NavigateOptions
	if opts != nil {
		body = *opts
	}
	return s.scrapePath(ctx, "/scrape", body)
}

func (s *BrowserSession) scrapePath(ctx context.Context, suffix string, body any) (*Document, error) {
	var res struct {
		Data *Document `json:"data"`
	}
	if err := s.http.do(ctx, http.MethodPost, s.base()+suffix, body, &res); err != nil {
		return nil, err
	}
	if res.Data == nil {
		res.Data = &Document{}
	}
	return res.Data, nil
}

// Action runs a raw browser action and returns the raw API payload.
// Prefer the typed helpers (Click, Fill, ...) unless you need full control.
func (s *BrowserSession) Action(ctx context.Context, action *BrowserAction) (map[string]any, error) {
	var res map[string]any
	if err := s.http.do(ctx, http.MethodPost, s.base()+"/action", action, &res); err != nil {
		return nil, err
	}
	return res, nil
}

// Click clicks an element. waitAfter is extra ms to wait afterwards (0 = server default 1000).
func (s *BrowserSession) Click(ctx context.Context, selector string, waitAfter int) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "click", Selector: selector, WaitAfter: waitAfter})
}

// Fill types into an input.
func (s *BrowserSession) Fill(ctx context.Context, selector, value string) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "fill", Selector: selector, Value: value})
}

// Select picks a dropdown option by value or label.
func (s *BrowserSession) Select(ctx context.Context, selector, value string) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "select", Selector: selector, Value: value})
}

// Press sends a keyboard key (e.g. "Enter"), optionally focusing a selector first.
func (s *BrowserSession) Press(ctx context.Context, key, selector string, waitAfter int) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "press", Key: key, Selector: selector, WaitAfter: waitAfter})
}

// Scroll scrolls the page. direction: "down" (default) or "up"; amount in px (default 500).
func (s *BrowserSession) Scroll(ctx context.Context, direction string, amount int) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "scroll", Direction: direction, Amount: amount})
}

// Wait waits for the given milliseconds (max 30000).
func (s *BrowserSession) Wait(ctx context.Context, ms int) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "wait", Ms: ms})
}

// WaitFor waits until a selector appears. timeout in ms (default 10000).
func (s *BrowserSession) WaitFor(ctx context.Context, selector string, timeout int) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "wait_for", Selector: selector, Timeout: timeout})
}

// Screenshot takes a screenshot. The API returns a base64 PNG inside the payload.
func (s *BrowserSession) Screenshot(ctx context.Context, fullPage bool) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "screenshot", FullPage: &fullPage})
}

// Evaluate runs a JavaScript expression in the page. The return value of the
// expression is in the payload's "result" field.
func (s *BrowserSession) Evaluate(ctx context.Context, expression string) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "evaluate", Expression: expression})
}

// Hover hovers over an element.
func (s *BrowserSession) Hover(ctx context.Context, selector string) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "hover", Selector: selector})
}

// Check ticks a checkbox.
func (s *BrowserSession) Check(ctx context.Context, selector string) (map[string]any, error) {
	return s.Action(ctx, &BrowserAction{Type: "check", Selector: selector})
}

// GetCookies reads the browser's current cookies.
func (s *BrowserSession) GetCookies(ctx context.Context) ([]Cookie, error) {
	var res struct {
		Cookies []Cookie `json:"cookies"`
		Data    []Cookie `json:"data"`
	}
	if err := s.http.do(ctx, http.MethodGet, s.base()+"/cookies", nil, &res); err != nil {
		return nil, err
	}
	if res.Cookies != nil {
		return res.Cookies, nil
	}
	return res.Data, nil
}

// SetCookies injects cookies into the browser context.
func (s *BrowserSession) SetCookies(ctx context.Context, cookies []Cookie) error {
	body := map[string]any{"op": "set", "cookies": cookies}
	return s.http.do(ctx, http.MethodPost, s.base()+"/cookies", body, nil)
}

// ClearCookies removes all cookies from the browser context.
func (s *BrowserSession) ClearCookies(ctx context.Context) error {
	return s.http.do(ctx, http.MethodPost, s.base()+"/cookies", map[string]any{"op": "clear"}, nil)
}

// SaveSession persists cookies/storage under this session's session name so
// the next session created with the same SessionName is already logged in.
func (s *BrowserSession) SaveSession(ctx context.Context) error {
	return s.http.do(ctx, http.MethodPost, s.base()+"/save_session", nil, nil)
}
