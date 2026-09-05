# MCP 서버 연결 (Claude Desktop / Claude Code)

docmind 는 Spring AI MCP Server(WebMVC, SSE 전송)로 두 툴을 노출한다.

| 툴 | 입력 | 출력 |
|---|---|---|
| `search_docs` | `query`, `topK?` | 관련 청크 목록(문서명·페이지·점수·snippet) |
| `ask_docs` | `question` | 근거 기반 답변 + citations |

## Claude Desktop — `claude_desktop_config.json`

Claude Desktop 은 stdio 서버를 기본으로 하므로 SSE 서버는 `mcp-remote` 브리지로 연결한다.

```json
{
  "mcpServers": {
    "docmind": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/sse"]
    }
  }
}
```

## Claude Code

```bash
claude mcp add --transport sse docmind http://localhost:8080/sse
```

기동 로그에 `Registered tools: 2` 가 보이면 정상이다.
