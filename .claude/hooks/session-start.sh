#!/bin/bash
# SessionStart hook: 대화 시작 시 환경변수 자동 세팅
#
# 새로 배우는 것:
#   - SessionStart는 대화 시작 시 딱 한 번 실행
#   - $CLAUDE_ENV_FILE 에 export 문을 쓰면 Claude 세션에 환경변수 주입됨
#   - 전역 settings.json의 지저분한 export allow 목록을 대체

# DB 연결 정보
cat >> "$CLAUDE_ENV_FILE" << 'EOF'
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:13306/estimedb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=secretpw
export DISCORD_BOT_TOKEN=dummy
export SLACK_BOT_TOKEN=dummy
export URL_ORIGIN_CLIENT=http://localhost:3000
EOF

echo "✅ 환경변수 로드 완료 (DB, Discord, Slack, CORS)"
exit 0