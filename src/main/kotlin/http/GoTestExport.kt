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

    return buildString {
        appendLine("package main")
        appendLine()
        appendLine("import (")
        appendLine("\t\"fmt\"")
        appendLine("\t\"io\"")
        appendLine("\t\"net/http\"")
        appendLine("\t\"strings\"")
        appendLine("\t\"testing\"")
        appendLine(")")
        appendLine()
        appendLine("func TestApiRequest(t *testing.T) {")
        appendLine("\turl := ${goStringLiteral(u)}")
        appendLine()
        appendLine("\theaders := map[string]string{")
        append(headerMapEntries)
        appendLine()
        appendLine("\t}")
        appendLine()
        appendLine(bodyLine)
        appendLine()
        appendLine("\tvar bodyReader io.Reader")
        appendLine("\tif body != \"\" {")
        appendLine("\t\tbodyReader = strings.NewReader(body)")
        appendLine("\t}")
        appendLine()
        appendLine("\treq, err := http.NewRequest(${goStringLiteral(m)}, url, bodyReader)")
        appendLine("\tif err != nil {")
        appendLine("\t\tt.Fatal(err)")
        appendLine("\t}")
        appendLine("\tfor k, v := range headers {")
        appendLine("\t\treq.Header.Set(k, v)")
        appendLine("\t}")
        appendLine()
        appendLine("\tclient := &http.Client{}")
        appendLine("\tresp, err := client.Do(req)")
        appendLine("\tif err != nil {")
        appendLine("\t\tt.Fatal(err)")
        appendLine("\t}")
        appendLine("\tdefer resp.Body.Close()")
        appendLine()
        appendLine("\tfmt.Println(\"Status:\", resp.Status)")
        appendLine("\tfor k, v := range resp.Header {")
        appendLine("\t\tfmt.Printf(\"%s: %s\\n\", k, strings.Join(v, \", \"))")
        appendLine("\t}")
        appendLine()
        appendLine("\trespBody, err := io.ReadAll(resp.Body)")
        appendLine("\tif err != nil {")
        appendLine("\t\tt.Fatal(err)")
        appendLine("\t}")
        appendLine("\tfmt.Println()")
        appendLine("\tfmt.Println(string(respBody))")
        appendLine("}")
    }
}

private fun goStringLiteral(s: String): String {
    val escaped = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}
