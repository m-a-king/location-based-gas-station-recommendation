#!/bin/bash
# PreToolUse hook: 민감한 파일 수정 차단
#
# 동작 원리:
#   Claude가 Edit/Write 도구를 쓰려 할 때 이 스크립트가 먼저 실행됨.
#   stdin으로 JSON이 들어옴 → 파일 경로 추출 → 보호 파일이면 exit 2로 차단.
#
# Exit code 의미:
#   0 = 진행 허용
#   2 = 차단 (stderr 메시지가 Claude에게 전달됨)

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# 파일 경로가 없으면 통과 (Write 이외의 도구)
if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# 커밋 금지 파일 목록
PROTECTED=(
  "application.local.yaml"
  ".env"
  "gradle.properties"
)

for pattern in "${PROTECTED[@]}"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "❌ 차단: '$FILE_PATH' 는 보호된 파일입니다. 직접 수정하세요." >&2
    exit 2
  fi
done

exit 0