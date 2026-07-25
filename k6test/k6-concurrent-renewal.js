import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

// 이미 구독 중인 유저 1명에게 "동시 갱신" 요청을 N건 동시에 보내서,
// 낙관적 락 충돌이 500 없이(순차 처리 또는 정상 에러코드로) 처리되는지 확인한다.
// BASE_URL/ARTIST_ID/VUS는 --env로 덮어쓸 수 있다:
// k6 run -e BASE_URL=http://<서버IP>:8080 -e ARTIST_ID=1 -e VUS=10 k6test/k6-concurrent-renewal.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTIST_ID = Number(__ENV.ARTIST_ID || '1');
const VUS = Number(__ENV.VUS || '10');

const serverErrorRate = new Rate('renewal_server_errors');

export const options = {
    scenarios: {
        concurrent_renewal: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: VUS,
            maxDuration: '30s',
        },
    },
    thresholds: {
        renewal_server_errors: ['rate==0'],
    },
};

// setup()은 테스트 전체에서 딱 한 번만 실행된다 — 갱신 대상이 될 구독을 미리 만들어둔다.
export function setup() {
    const email = `loadtest_renew_${Date.now()}@test.com`;
    const password = 'loadtest1234';

    const signupRes = http.post(
        `${BASE_URL}/signup`,
        JSON.stringify({ email, password, nickname: 'lt_renew' }),
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

    // 최초 구독 (여기서부터 "이미 구독 중" 상태를 만든 뒤 동시 갱신을 시작한다)
    const subscribeRes = http.post(
        `${BASE_URL}/memberships`,
        JSON.stringify({ artistId: ARTIST_ID }),
        authHeaders
    );
    check(subscribeRes, { '최초 구독 200': (r) => r.status === 200 });

    return { accessToken };
}

// 모든 VU가 setup()에서 만든 같은 유저 토큰으로, 같은 아티스트를 동시에 갱신 요청한다.
export default function (data) {
    const authHeaders = { headers: { Authorization: `Bearer ${data.accessToken}`, 'Content-Type': 'application/json' } };

    const renewRes = http.post(
        `${BASE_URL}/memberships`,
        JSON.stringify({ artistId: ARTIST_ID }),
        authHeaders
    );

    check(renewRes, { '갱신 500 아님': (r) => r.status !== 500 });
    serverErrorRate.add(renewRes.status === 500);
}
