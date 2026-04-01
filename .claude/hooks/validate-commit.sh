#!/bin/bash
# PreToolUse hook: git commit 메시지 형식 검증
#
# 새로 배우는 것: 도구의 특정 인자(argument)를 파싱해서 조건 분기

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# git commit 명령이 아니면 통과
if ! echo "$COMMAND" | grep -q "git commit"; then
  exit 0
fi

# 커밋 메시지 추출 (-m "..." 패턴, macOS 호환)
MSG=$(echo "$COMMAND" | sed -n 's/.*-m "\([^"]*\)".*/\1/p')

if [ -z "$MSG" ]; then
  exit 0
fi

# 허용된 prefix 목록
VALID_PREFIXES=("feat:" "fix:" "refactor:" "docs:" "test:" "chore:" "style:" "perf:")

for prefix in "${VALID_PREFIXES[@]}"; do
  if echo "$MSG" | grep -q "^$prefix"; then
    exit 0  # 올바른 형식
  fi
done

echo "❌ 차단: 커밋 메시지 형식 오류" >&2
echo "   입력: \"$MSG\"" >&2
echo "   필요: feat: / fix: / refactor: / docs: / test: / chore: 중 하나로 시작해야 합니다." >&2
exit 2
