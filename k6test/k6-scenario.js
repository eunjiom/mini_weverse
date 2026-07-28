import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 게이트웨이를 거쳐 실제 유저 흐름(회원가입→로그인→탐색→팔로우→구독→글쓰기)을 그대로 재현한다.
// BASE_URL/ARTIST_ID는 --env로 덮어쓸 수 있다: k6 run -e BASE_URL=http://<서버IP>:8080 k6test/k6-scenario.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTIST_ID = __ENV.ARTIST_ID || '1';

const failureRate = new Rate('scenario_failures');

export const options = {
    scenarios: {
        ramping_users: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m', target: 50 },
                { duration: '30s', target: 100 },
                { duration: '1m', target: 100 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        scenario_failures: ['rate<0.05'],
    },
};

function uniqueEmail() {
    return `loadtest_${__VU}_${__ITER}_${Date.now()}@test.com`;
}

export default function () {
    const email = uniqueEmail();
    const password = 'loadtest1234';
    let ok = true;

    // 1. 회원가입
    const signupRes = http.post(
        `${BASE_URL}/signup`,
        JSON.stringify({ email, password, nickname: `lt_${__VU}_${__ITER}` }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    ok = check(signupRes, { '회원가입 201': (r) => r.status === 201 }) && ok;

    // 2. 로그인
    const loginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    ok = check(loginRes, { '로그인 200': (r) => r.status === 200 }) && ok;

    const accessToken = loginRes.json('accessToken');
    if (!accessToken) {
        failureRate.add(true);
        sleep(1);
        return;
    }
    const authHeaders = { headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' } };

    // 3. 아티스트 탐색
    const searchRes = http.get(`${BASE_URL}/artists?name=art`, authHeaders);
    ok = check(searchRes, { '아티스트 검색 200': (r) => r.status === 200 }) && ok;

    // 4. 팔로우
    const followRes = http.post(
        `${BASE_URL}/follows`,
        JSON.stringify({ artistId: Number(ARTIST_ID) }),
        authHeaders
    );
    ok = check(followRes, { '팔로우 204': (r) => r.status === 204 }) && ok;

    // 5. 게시글 목록 조회 (ARTIST 게시판)
    const postsRes = http.get(
        `${BASE_URL}/artists/${ARTIST_ID}/posts?boardType=ARTIST&size=20`,
        authHeaders
    );
    ok = check(postsRes, { '게시글 목록 200': (r) => r.status === 200 }) && ok;

    // 6. 멤버십 구독
    const subscribeRes = http.post(
        `${BASE_URL}/memberships`,
        JSON.stringify({ artistId: Number(ARTIST_ID) }),
        authHeaders
    );
    ok = check(subscribeRes, { '구독 200': (r) => r.status === 200 }) && ok;

    // 7. FEED 게시판에 글 작성 (팔로우한 아티스트라 작성 가능)
    const createPostRes = http.post(
        `${BASE_URL}/artists/${ARTIST_ID}/posts`,
        JSON.stringify({ boardType: 'FEED', content: `부하테스트 글 ${__VU}-${__ITER}`, membersOnly: false }),
        authHeaders
    );
    ok = check(createPostRes, { '글 작성 201': (r) => r.status === 201 }) && ok;

    // 8. 채팅 메시지 이력 조회 (게이트웨이+챗 구간 병목 확인용)
    const chatMessagesRes = http.get(`${BASE_URL}/api/chat/rooms/${ARTIST_ID}/messages`, authHeaders);
    ok = check(chatMessagesRes, { '채팅 메시지 조회 200': (r) => r.status === 200 }) && ok;

    // 9. 알림 목록 조회 (게이트웨이+알림 구간 병목 확인용)
    const notificationsRes = http.get(`${BASE_URL}/notifications`, authHeaders);
    ok = check(notificationsRes, { '알림 목록 조회 200': (r) => r.status === 200 }) && ok;

    // 10. 알림 읽음 처리
    const notificationsReadRes = http.post(`${BASE_URL}/notifications/read`, null, authHeaders);
    ok = check(notificationsReadRes, { '알림 읽음 처리 204': (r) => r.status === 204 }) && ok;

    failureRate.add(!ok);
    sleep(1);
}
