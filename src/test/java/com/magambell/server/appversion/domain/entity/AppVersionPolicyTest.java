package com.magambell.server.appversion.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.magambell.server.appversion.domain.enums.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppVersionPolicyTest {

    // ── compareVersions ───────────────────────────────────────────────────

    @Test
    @DisplayName("compareVersions: v1이 null이면 0을 반환한다")
    void compareVersions_v1Null_returnsZero() {
        assertThat(AppVersionPolicy.compareVersions(null, "1.0.0")).isEqualTo(0);
    }

    @Test
    @DisplayName("compareVersions: v2가 null이면 0을 반환한다")
    void compareVersions_v2Null_returnsZero() {
        assertThat(AppVersionPolicy.compareVersions("1.0.0", null)).isEqualTo(0);
    }

    @Test
    @DisplayName("compareVersions: 같은 버전이면 0을 반환한다")
    void compareVersions_equalVersions_returnsZero() {
        assertThat(AppVersionPolicy.compareVersions("1.2.3", "1.2.3")).isEqualTo(0);
    }

    @Test
    @DisplayName("compareVersions: v1이 v2보다 낮으면 -1을 반환한다")
    void compareVersions_v1LessThanV2_returnsMinusOne() {
        assertThat(AppVersionPolicy.compareVersions("1.0.0", "2.0.0")).isEqualTo(-1);
        assertThat(AppVersionPolicy.compareVersions("1.9.9", "2.0.0")).isEqualTo(-1);
    }

    @Test
    @DisplayName("compareVersions: v1이 v2보다 높으면 1을 반환한다")
    void compareVersions_v1GreaterThanV2_returnsOne() {
        assertThat(AppVersionPolicy.compareVersions("2.0.0", "1.0.0")).isEqualTo(1);
        assertThat(AppVersionPolicy.compareVersions("1.0.1", "1.0.0")).isEqualTo(1);
    }

    @Test
    @DisplayName("compareVersions: 길이가 다를 때 짧은 쪽의 없는 파트는 0으로 취급한다")
    void compareVersions_differentLengths_missingPartsAreZero() {
        assertThat(AppVersionPolicy.compareVersions("1.0", "1.0.0")).isEqualTo(0);
        assertThat(AppVersionPolicy.compareVersions("1.0.1", "1.0")).isEqualTo(1);
        assertThat(AppVersionPolicy.compareVersions("1.0", "1.0.1")).isEqualTo(-1);
    }

    // ── create / builder 브랜치 ───────────────────────────────────────────

    @Test
    @DisplayName("create: forceUpdate가 null이면 false로 설정된다")
    void create_forceUpdateNull_defaultsFalse() {
        AppVersionPolicy policy = AppVersionPolicy.create(
                Platform.ANDROID, "2.0.0", "1.0.0", "1.5.0",
                null,   // forceUpdate = null
                "https://play.google.com", null, "릴리즈 노트");

        assertThat(policy.getForceUpdate()).isFalse();
    }

    @Test
    @DisplayName("create: forceUpdate가 true이면 true로 설정된다")
    void create_forceUpdateTrue_setsTrue() {
        AppVersionPolicy policy = AppVersionPolicy.create(
                Platform.IOS, "2.0.0", "1.0.0", null,
                Boolean.TRUE,
                null, "https://apps.apple.com", null);

        assertThat(policy.getForceUpdate()).isTrue();
    }

    // ── requiresForceUpdate ───────────────────────────────────────────────

    @Test
    @DisplayName("requiresForceUpdate: 현재 버전이 최소 지원 버전보다 낮으면 true를 반환한다")
    void requiresForceUpdate_currentBelowMin_returnsTrue() {
        AppVersionPolicy policy = buildAndroid("2.0.0", "1.5.0", null);

        assertThat(policy.requiresForceUpdate("1.0.0")).isTrue();
    }

    @Test
    @DisplayName("requiresForceUpdate: 현재 버전이 최소 지원 버전 이상이면 false를 반환한다")
    void requiresForceUpdate_currentAtOrAboveMin_returnsFalse() {
        AppVersionPolicy policy = buildAndroid("2.0.0", "1.5.0", null);

        assertThat(policy.requiresForceUpdate("1.5.0")).isFalse();
        assertThat(policy.requiresForceUpdate("2.0.0")).isFalse();
    }

    // ── recommendsUpdate ─────────────────────────────────────────────────

    @Test
    @DisplayName("recommendsUpdate: recommendedMinVersion이 null이면 false를 반환한다")
    void recommendsUpdate_recommendedVersionNull_returnsFalse() {
        AppVersionPolicy policy = buildAndroid("2.0.0", "1.0.0", null);

        assertThat(policy.recommendsUpdate("1.0.0")).isFalse();
    }

    @Test
    @DisplayName("recommendsUpdate: 현재 버전이 권장 최소 버전보다 낮으면 true를 반환한다")
    void recommendsUpdate_currentBelowRecommended_returnsTrue() {
        AppVersionPolicy policy = buildAndroid("2.0.0", "1.0.0", "1.8.0");

        assertThat(policy.recommendsUpdate("1.5.0")).isTrue();
    }

    @Test
    @DisplayName("recommendsUpdate: 현재 버전이 권장 최소 버전 이상이면 false를 반환한다")
    void recommendsUpdate_currentAtOrAboveRecommended_returnsFalse() {
        AppVersionPolicy policy = buildAndroid("2.0.0", "1.0.0", "1.8.0");

        assertThat(policy.recommendsUpdate("1.8.0")).isFalse();
        assertThat(policy.recommendsUpdate("2.0.0")).isFalse();
    }

    // ── getStoreUrl ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getStoreUrl: ANDROID 플랫폼이면 androidStoreUrl을 반환한다")
    void getStoreUrl_android_returnsAndroidUrl() {
        AppVersionPolicy policy = AppVersionPolicy.create(
                Platform.ANDROID, "1.0.0", "1.0.0", null,
                false, "https://play.google.com", "https://apps.apple.com", null);

        assertThat(policy.getStoreUrl()).isEqualTo("https://play.google.com");
    }

    @Test
    @DisplayName("getStoreUrl: IOS 플랫폼이면 iosStoreUrl을 반환한다")
    void getStoreUrl_ios_returnsIosUrl() {
        AppVersionPolicy policy = AppVersionPolicy.create(
                Platform.IOS, "1.0.0", "1.0.0", null,
                false, "https://play.google.com", "https://apps.apple.com", null);

        assertThat(policy.getStoreUrl()).isEqualTo("https://apps.apple.com");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: forceUpdate가 null이면 기존 값을 유지한다")
    void update_forceUpdateNull_keepsExistingValue() {
        AppVersionPolicy policy = buildAndroid("1.0.0", "1.0.0", null);
        policy.update("2.0.0", "1.5.0", null, null, null, null, null);

        assertThat(policy.getLatestVersion()).isEqualTo("2.0.0");
        assertThat(policy.getForceUpdate()).isFalse();
    }

    @Test
    @DisplayName("update: forceUpdate가 non-null이면 새 값으로 변경한다")
    void update_forceUpdateNonNull_setsNewValue() {
        AppVersionPolicy policy = buildAndroid("1.0.0", "1.0.0", null);
        policy.update("2.0.0", "1.5.0", null, Boolean.TRUE, null, null, null);

        assertThat(policy.getForceUpdate()).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private AppVersionPolicy buildAndroid(String latest, String minSupported, String recommended) {
        return AppVersionPolicy.create(
                Platform.ANDROID, latest, minSupported, recommended,
                false, "https://play.google.com", null, null);
    }
}
