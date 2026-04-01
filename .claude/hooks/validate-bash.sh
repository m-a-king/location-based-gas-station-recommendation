#!/bin/bash
# PreToolUse hook: 위험한 Bash 명령어 차단
#
# 학습 포인트:
#   - stdin JSON에서 command 필드를 추출
#   - 패턴 매칭으로 위험 명령어 감지
#   - exit 2로 차단, stderr로 이유 전달

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [ -z "$COMMAND" ]; then
  exit 0
fi

# 차단할 패턴들
BLOCKED_PATTERNS=(
  "rm -rf /"
  "DROP TABLE"
  "DROP DATABASE"
  "git push --force"
  "git reset --hard HEAD~"
)

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qi "$pattern"; then
    echo "❌ 차단: 위험 명령어 감지 → '$pattern'" >&2
    echo "   실행하려던 명령: $COMMAND" >&2
    exit 2
  fi
done

# application.local.yaml을 git add하려는 시도 차단
if echo "$COMMAND" | grep -q "git add" && echo "$COMMAND" | grep -q "application.local"; then
  echo "❌ 차단: application.local.yaml을 git에 추가할 수 없습니다." >&2
  exit 2
fi

exit 0
