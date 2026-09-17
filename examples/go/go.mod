module github.com/webpipe-ai/webpipe-sdk/examples/go

go 1.22

require github.com/webpipe-ai/webpipe-sdk/packages/go v0.0.0

// Local development: use the in-repo SDK. Once the module is published,
// remove this replace and `go get github.com/webpipe-ai/webpipe-sdk/packages/go`.
replace github.com/webpipe-ai/webpipe-sdk/packages/go => ../../packages/go
