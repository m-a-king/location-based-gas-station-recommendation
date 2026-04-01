#!/bin/bash
# PostToolUse hook: service/controller 파일 생성 시 테스트 파일 확인
#
# 새로 배우는 것:
#   - PostToolUse는 차단 불가 → 대신 stdout으로 Claude에게 메시지 전달
#   - Claude는 이 stdout 내용을 자신의 컨텍스트로 읽어서 반응함

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# .kt 파일이 아니면 무시
[[ "$FILE_PATH" != *.kt ]] && exit 0

# service/ 또는 controller/ 경로만 체크
if ! echo "$FILE_PATH" | grep -qE "/(service|controller)/"; then
  exit 0
fi

# 파일 이름 추출
FILENAME=$(basename "$FILE_PATH" .kt)
PROJECT_DIR="$CLAUDE_PROJECT_DIR"

# 대응하는 테스트 파일 존재 확인
TEST_FILE=$(find "$PROJECT_DIR/src/test" -name "${FILENAME}Test.kt" 2>/dev/null | head -1)

if [ -z "$TEST_FILE" ]; then
  # stdout → Claude 컨텍스트에 추가됨
  echo "⚠️  ${FILENAME}.kt 에 대응하는 테스트 파일(${FILENAME}Test.kt)이 없습니다."
  echo "   테스트 작성이 필요하면 src/test/kotlin 하위에 만들어주세요."
fi

exit 0
