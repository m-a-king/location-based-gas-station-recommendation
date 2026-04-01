#!/bin/bash
# PostToolUse hook: .kt 파일 수정 후 실행
#
# 학습 포인트:
#   - PostToolUse는 도구 실행 "후"에 실행됨
#   - exit code로 차단 불가 (이미 실행됨)
#   - stdout으로 Claude에게 피드백 전달 가능

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# .kt 파일이 아니면 무시
if [[ "$FILE_PATH" != *.kt ]]; then
  exit 0
fi

# Claude에게 메시지 전달 (stdout → Claude 컨텍스트에 추가됨)
echo "💡 $FILE_PATH 수정됨. 테스트가 필요하면 ./gradlew test 를 실행하세요."

exit 0
