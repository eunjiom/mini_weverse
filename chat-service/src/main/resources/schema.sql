-- 같은 (fan_user_id, artist_id)에 열린(ended_at IS NULL) 구독 기간은 하나만 존재해야 한다.
-- MembershipVerifier.markActive가 "확인 후 저장"이 아니라 저장을 바로 시도하고 이 제약 위반을
-- 잡아서 무시하는 방식으로 동시/중복 요청에도 안전하게 동작한다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_membership_periods_open
    ON membership_periods (fan_user_id, artist_id)
    WHERE ended_at IS NULL;
