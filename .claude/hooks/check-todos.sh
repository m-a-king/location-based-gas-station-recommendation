#!/bin/bash
# Stop hook: 응답 완료 후 수정된 파일에 TODO 남아있는지 체크
#
# 새로 배우는 것:
#   - Stop 이벤트는 Claude의 응답이 끝난 뒤 실행
#   - git diff로 이번 작업에서 수정된 파일만 검사
#   - stdout은 다음 Claude 응답 컨텍스트에 주입됨

# 이번 세션에서 수정된 .kt 파일 중 TODO가 남은 것 찾기
TODO_FILES=$(git diff HEAD --name-only 2>/dev/null | grep '\.kt$' | while read f; do
  if grep -q "TODO\|FIXME\|HACK" "$f" 2>/dev/null; then
    echo "$f"
  fi
done)

if [ -n "$TODO_FILES" ]; then
  echo "📝 수정된 파일에 TODO/FIXME가 남아있습니다:"
  echo "$TODO_FILES" | while read f; do
    grep -n "TODO\|FIXME\|HACK" "$f" | while read line; do
      echo "   $f: $line"
    done
  done
fi

exit 0