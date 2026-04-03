#!/bin/bash
# Kotlin 메서드명이 동사형으로 시작하는지 확인한다.
# 팩토리 관용어(of, from, around, aroundPolyline 등)는 허용한다.

FILE="${CLAUDE_TOOL_INPUT_FILE_PATH:-}"
[[ "$FILE" != *.kt ]] && exit 0

VERBS="get|set|is|has|can|should|find|search|save|load|fetch|create|build|make|run|handle|process|convert|calculate|check|validate|update|delete|remove|add|map|filter|sort|parse|format|send|receive|download|import|export|recommend|rank|refine|resolve|register|apply|execute|login|encrypt|open|post|do|stub|assert"
FACTORY="of|from|around|aroundPolyline|aroundCenter|invoke"

VIOLATIONS=$(grep -nE "^\s+(private |internal |override |suspend )*(fun )([a-z][a-zA-Z0-9]+)\(" "$FILE" \
  | grep -vE "fun ($VERBS|$FACTORY)[A-Z(]" \
  | grep -vE "//")

if [[ -n "$VIOLATIONS" ]]; then
  echo "⚠️  동사형으로 시작하지 않는 메서드가 있습니다:"
  echo "$VIOLATIONS"
fi
exit 0
