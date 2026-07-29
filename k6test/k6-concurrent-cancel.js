import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

// 이미 구독 중인 유저 1명의 멤버십 1건에 "동시 취소" 요청을 N건 동시에 보내서,
// 정확히 1건만 성공하고(Lost Update 없음) 나머지는 500 없이 정상 거부되는지 확인한다.
// k6 run -e BASE_URL=http://localhost:9090 -e ARTIST_ID=1 -e VUS=1300 k6test/k6-concurrent-cancel.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:9090';
const ARTIST_ID = Number(__ENV.ARTIST_ID || '1');
const VUS = Number(__ENV.VUS || '10');

const serverErrorRate = new Rate('cancel_server_errors');
const cancelSuccessCount = new Counter('cancel_success_total');
const cancelRejectedCount = new Counter('cancel_rejected_total');

export const options = {
    scenarios: {
        concurrent_cancel: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: VUS,
            maxDuration: '60s',
        },
    },
    thresholds: {
        cancel_server_errors: ['rate==0'],
    },
};

// setup()은 테스트 전체에서 딱 한 번만 실행된다 — 취소 대상이 될 구독을 미리 만들어둔다.
export function setup() {
    const email = `loadtest_cancel_${Date.now()}@test.com`;
    const password = 'loadtest1234';

    const signupRes = http.post(
        `${BASE_URL}/signup`,
        JSON.stringify({ email, password, nickname: 'lt_cancel' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(signupRes, { '회원가입 201': (r) => r.status === 201 });

    const loginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const accessToken = loginRes.json('accessToken');
    check(accessToken, { '로그인 성공': (t) => !!t });

    const authHeaders = { headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' } };

    // 최초 구독 (여기서부터 "이미 구독 중" 상태를 만든 뒤 동시 취소를 시작한다)
    const subscribeRes = http.post(
        `${BASE_URL}/memberships`,
        JSON.stringify({ artistId: ARTIST_ID }),
        authHeaders
    );
    check(subscribeRes, { '최초 구독 200': (r) => r.status === 200 });
    const membershipId = subscribeRes.json('membershipId');

    return { accessToken, membershipId };
}

// 모든 VU가 setup()에서 만든 같은 유저 토큰으로, 같은 멤버십 하나를 동시에 취소 요청한다.
export default function (data) {
    const authHeaders = { headers: { Authorization: `Bearer ${data.accessToken}`, 'Content-Type': 'application/json' } };

    const cancelRes = http.post(
        `${BASE_URL}/memberships/${data.membershipId}/cancel`,
        null,
        authHeaders
    );

    check(cancelRes, { '취소 500 아님': (r) => r.status !== 500 });
    serverErrorRate.add(cancelRes.status === 500);
    if (cancelRes.status === 200) {
        cancelSuccessCount.add(1);
    } else if (cancelRes.status === 400) {
        cancelRejectedCount.add(1);
    }
}
