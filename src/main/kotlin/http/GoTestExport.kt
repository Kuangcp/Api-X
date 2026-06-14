package http

/** 生成 Go 单元测试代码。 */
fun requestToGoTestMethod(method: String, url: String, headersText: String, bodyText: String): String {
    val m = method.trim().uppercase().ifEmpty { "GET" }
    val u = url.trim()

    val headerLines = parseHeaders(headersText)
    val headerMapEntries = headerLines.joinToString("\n") { (k, v) ->
        "\t\t${goStringLiteral(k)}: ${goStringLiteral(v)},"
    }
    val body = bodyWirePayloadForHttp(bodyText, headersText)
    val bodyLine = if (body.isNotBlank()) {
        "\tbody := ${goStringLiteral(body)}"
    } else {
        "\tvar body string"
    }

    return """
package main

import (
	"bytes"
	"compress/gzip"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
)

func TestApiRequest(t *testing.T) {
	url := ${goStringLiteral(u)}

	headers := map[string]string{
$headerMapEntries
	}

$bodyLine

	var bodyReader io.Reader
	if body != "" {
		bodyReader = strings.NewReader(body)
	}

	req, err := http.NewRequest(${goStringLiteral(m)}, url, bodyReader)
	if err != nil {
		t.Fatal(err)
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	fmt.Println("Status:", resp.Status)
	for k, v := range resp.Header {
		fmt.Printf("%s: %s\n", k, strings.Join(v, ", "))
	}

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatal(err)
	}

	if resp.Header.Get("Content-Encoding") == "gzip" ||
		len(respBody) >= 2 && respBody[0] == 0x1f && respBody[1] == 0x8b {
		reader, err := gzip.NewReader(bytes.NewReader(respBody))
		if err == nil {
			defer reader.Close()
			respBody, err = io.ReadAll(reader)
			if err != nil {
				t.Fatal(err)
			}
		}
	}

	fmt.Println()
	fmt.Println(string(respBody))
}
""".trimIndent()
}

private fun goStringLiteral(s: String): String {
    val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}
