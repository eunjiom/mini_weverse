import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// 메시지를 보낸 시점부터 팬 알림 목록에 실제로 뜨는 시점까지 걸린 시간(ms) — 아웃박스 폴링(5초)+Kafka
// 처리 시간을 합친 실측치. 4번 테스트(아웃박스 발행 지연)가 원하던 데이터를 이 테스트에서 같이 얻는다.
const chatToNotificationLatency = new Trend('chat_to_notification_latency_ms');

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
            maxDuration: '50s',
        },
    },
};

// setup()에서 딱 한 번: 알림을 받을 팬을 만들어 구독시키고, 방송할 아티스트를 로그인시켜둔다.
//
// 팬 이메일은 매 실행마다 새로 만들지 않고 고정값을 쓴다 — 원래 Date.now()로 매번 새 팬을 만들어
// 구독만 시키고 해지를 안 했더니, 반복 실행할수록(20회 이상) 아티스트의 활성 구독자가 스스로
// 24,221명까지 누적되며 fan-out 지연이 20초 테스트 창을 넘기는 자기잠식 버그가 있었다(트러블슈팅
// 참고). 고정 이메일로 같은 팬 1명만 재사용하면 두 번째 실행부터는 회원가입이 실패해도(중복 이메일)
// 로그인만으로 이어지고, 구독 API가 "신규 구독/갱신 겸용"이라 같은 팬이 다시 호출해도 새 구독자가
// 추가되는 게 아니라 기존 구독이 갱신되므로 이 아티스트의 활성 구독자 수는 항상 1명으로 유지된다.
export function setup() {
    const fanEmail = 'loadtest_dm_fan@test.com';
    const fanPassword = 'loadtest1234';

    const signupRes = http.post(
        `${BASE_URL}/signup`,
        JSON.stringify({ email: fanEmail, password: fanPassword, nickname: 'lt_dm_fan' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    // 두 번째 실행부터는 이미 가입된 이메일이라 409(중복)가 정상 — 아래 로그인으로 그대로 이어간다.
    check(signupRes, { '팬 회원가입 201 또는 중복(409)': (r) => r.status === 201 || r.status === 409 });

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

    // sendArtistMessage의 팬-아웃 대상 조회는 chat-service 로컬 MembershipPeriod 테이블을 보는데,
    // 이 테이블은 커뮤니티→챗 아웃박스 푸시(5초 폴링)가 끝나야 채워진다. 그 전에 방송하면
    // 대상이 0명으로 잡혀 알림 자체가 안 만들어지므로, 푸시가 끝날 시간을 확보한다.
    sleep(7);

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
    let sentAt = null;

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
                sentAt = Date.now();
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

    // 아웃박스 폴링(5초)+Kafka 처리 시간이 정확히 몇 초인지 모르니, 고정 대기 대신
    // 2초 간격으로 최대 20초까지 반복 확인하면서 실제로 도착한 시점을 잰다.
    const maxWaitMs = 20000;
    const pollIntervalSec = 2;
    let arrived = false;
    let notifRes;

    while (Date.now() - sentAt < maxWaitMs) {
        sleep(pollIntervalSec);
        notifRes = http.get(`${BASE_URL}/notifications`, {
            headers: { Authorization: `Bearer ${data.fanToken}` },
        });
        const notifications = notifRes.json('notifications') || [];
        if (notifications.some((n) => n.type === 'NEW_CHAT_MESSAGE')) {
            arrived = true;
            chatToNotificationLatency.add(Date.now() - sentAt);
            break;
        }
    }

    check(notifRes, { '알림 목록 조회 200': (r) => r.status === 200 });
    check(arrived, { '채팅→알림 전달 확인(NEW_CHAT_MESSAGE, 20초 이내)': (v) => v === true });
}
