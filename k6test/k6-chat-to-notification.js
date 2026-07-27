import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';

// 챗+알림 병목 테스트 (2-8): 아티스트가 WebSocket/STOMP로 방송 메시지를 보내면
// 활성 구독자(팬) 전원에게 알림 아웃박스 이벤트가 쌓이고, 아웃박스 스케줄러(5초 폴링)가
// Kafka로 발행 -> notification-service가 소비하는 전체 구간을 한 번 실행해서 Zipkin으로 관찰한다.
// k6 run -e BASE_URL=http://<서버IP>:8080 -e ARTIST_ID=1 k6test/k6-chat-to-notification.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, 'ws') + '/api/chat/ws-chat';
const ARTIST_ID = Number(__ENV.ARTIST_ID || '1');
const ARTIST_EMAIL = __ENV.ARTIST_EMAIL || 'artist1@test.com';
const ARTIST_PASSWORD = __ENV.ARTIST_PASSWORD || 'artist1234';

export const options = {
    scenarios: {
        chat_to_notification: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

// setup()에서 딱 한 번: 알림을 받을 팬을 만들어 구독시키고, 방송할 아티스트를 로그인시켜둔다.
export function setup() {
    const fanEmail = `loadtest_dm_fan_${Date.now()}@test.com`;
    const fanPassword = 'loadtest1234';

    const signupRes = http.post(
        `${BASE_URL}/signup`,
        JSON.stringify({ email: fanEmail, password: fanPassword, nickname: 'lt_dm_fan' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(signupRes, { '팬 회원가입 201': (r) => r.status === 201 });

    const fanLoginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ email: fanEmail, password: fanPassword }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const fanToken = fanLoginRes.json('accessToken');
    check(fanToken, { '팬 로그인 성공': (t) => !!t });

    const subscribeRes = http.post(
        `${BASE_URL}/memberships`,
        JSON.stringify({ artistId: ARTIST_ID }),
        { headers: { Authorization: `Bearer ${fanToken}`, 'Content-Type': 'application/json' } }
    );
    check(subscribeRes, { '팬 구독 200': (r) => r.status === 200 });

    const artistLoginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ email: ARTIST_EMAIL, password: ARTIST_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(artistLoginRes, { '아티스트 로그인 200': (r) => r.status === 200 });

    // WS 핸드셰이크는 Authorization 헤더가 아니라 accessToken 쿠키로 인증한다(JwtHandshakeInterceptor).
    const artistCookie = artistLoginRes.cookies.accessToken
        ? artistLoginRes.cookies.accessToken[0].value
        : null;
    check(artistCookie, { '아티스트 accessToken 쿠키 확보': (c) => !!c });

    return { fanToken, artistCookie };
}

export default function (data) {
    const params = { headers: { Cookie: `accessToken=${data.artistCookie}` } };
    let connected = false;
    let sent = false;

    const res = ws.connect(WS_URL, params, function (socket) {
        socket.on('open', function () {
            socket.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\x00');
        });

        socket.on('message', function (msg) {
            if (msg.indexOf('CONNECTED') === 0) {
                connected = true;
                const body = JSON.stringify({ content: `병목테스트 아티스트 방송 ${Date.now()}` });
                const frame = `SEND\ndestination:/app/rooms/${ARTIST_ID}/artist-message\ncontent-type:application/json\n\n${body}\x00`;
                socket.send(frame);
                sent = true;
                socket.setTimeout(function () {
                    socket.close();
                }, 1000);
            }
        });

        socket.on('error', function (e) {
            console.error(`WS error: ${e.error()}`);
        });
    });

    check(res, { 'WS 핸드셰이크 101': (r) => r && r.status === 101 });
    check(connected, { 'STOMP CONNECTED 수신': (v) => v === true });
    check(sent, { '아티스트 메시지 SEND 완료': (v) => v === true });

    // 아웃박스 폴링(5초) + Kafka 처리 시간만큼 대기한 뒤, 팬 쪽에 알림이 실제로 도착했는지 확인한다.
    sleep(8);

    const notifRes = http.get(`${BASE_URL}/notifications`, {
        headers: { Authorization: `Bearer ${data.fanToken}` },
    });
    check(notifRes, { '알림 목록 조회 200': (r) => r.status === 200 });

    const notifications = notifRes.json('notifications') || [];
    const hasNewChatMessageNotice = notifications.some((n) => n.type === 'NEW_CHAT_MESSAGE');
    check(hasNewChatMessageNotice, { '채팅→알림 전달 확인(NEW_CHAT_MESSAGE)': (v) => v === true });
}
