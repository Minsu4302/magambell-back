package com.magambell.server.auth.app.service;

import com.magambell.server.auth.app.port.in.AuthUseCase;
import com.magambell.server.auth.app.port.in.request.SocialLoginServiceRequest;
import com.magambell.server.auth.app.port.in.request.SocialWithdrawServiceRequest;
import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.auth.domain.model.JwtToken;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.common.exception.NotFoundException;
import com.magambell.server.common.security.CustomUserDetails;
import com.magambell.server.user.app.dto.OAuthUserInfo;
import com.magambell.server.user.app.port.in.dto.UserDTO;
import com.magambell.server.user.app.port.in.dto.UserSocialAccountDTO;
import com.magambell.server.user.app.port.out.OAuthClient;
import com.magambell.server.user.app.port.out.UserCommandPort;
import com.magambell.server.user.app.port.out.UserQueryPort;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
public class AuthService implements AuthUseCase {

    private static final String TEST_PASSWORD = "test1234";

    private final Map<ProviderType, OAuthClient> oAuthClientMap;
    private final UserQueryPort userQueryPort;
    private final UserCommandPort userCommandPort;
    private final JwtService jwtService;

    public AuthService(final List<OAuthClient> oAuthClients, final UserQueryPort userQueryPort,
                       final UserCommandPort userCommandPort,
                   final JwtService jwtService) {
        this.oAuthClientMap = oAuthClients.stream()
                .collect(Collectors.toMap(OAuthClient::getProviderType, Function.identity()));
        this.userQueryPort = userQueryPort;
        this.userCommandPort = userCommandPort;
        this.jwtService = jwtService;
    }

    @Transactional
    @Override
    public JwtToken loginOrSignUp(final SocialLoginServiceRequest request) {
        OAuthClient oAuthClient = oAuthClientMap.get(request.providerType());
        OAuthUserInfo userInfo = oAuthClient.getUserInfo(request.authCode());

        User user = userQueryPort.findUserBySocial(userInfo.providerType(),
                        userInfo.id())
                .orElseGet(() -> oAuthSignUp(userInfo, request));

        return jwtService.createJwtToken(user.getId(), user.getUserRole());
    }

    @Transactional
    @Override
    public void withdrawUser(final SocialWithdrawServiceRequest request, final CustomUserDetails customUserDetails) {
        OAuthClient oAuthClient = oAuthClientMap.get(request.providerType());
        Long userId = customUserDetails.userId();
        User user = userQueryPort.findById(userId);
        oAuthClient.userWithdraw(request.authCode());

        user.withdraw();
        userCommandPort.deleteBySocial(request.providerType(), userId);
    }

    @Transactional
    @Override
    public JwtToken reissueAccessToken(final String refreshToken) {
        return jwtService.reissueAccessToken(refreshToken);
    }

    @Override
    public JwtToken userTest() {
        User user = findOrCreateTestUser(
                "test-user@bitepick.co.kr",
                "테스트 고객",
                "01011112222",
                UserRole.CUSTOMER
        );
        return jwtService.createJwtToken(user.getId(), user.getUserRole());
    }

    @Override
    public JwtToken ownerTest() {
        User user = findOrCreateTestUser(
                "test-owner@bitepick.co.kr",
                "테스트 사장",
                "01011113333",
                UserRole.OWNER
        );
        return jwtService.createJwtToken(user.getId(), user.getUserRole());
    }

    @Override
    public JwtToken adminTest() {
        User user = findOrCreateTestUser(
                "test-admin@bitepick.co.kr",
                "테스트 관리자",
                "01011114444",
                UserRole.ADMIN
        );
        return jwtService.createJwtToken(user.getId(), user.getUserRole());
    }

    private User findOrCreateTestUser(final String email, final String name, final String phoneNumber,
                                      final UserRole userRole) {
        try {
            return userQueryPort.getUser(email, TEST_PASSWORD);
        } catch (NotFoundException e) {
            return userCommandPort.register(new UserDTO(email, TEST_PASSWORD, name, phoneNumber, userRole));
        }
    }

    private User oAuthSignUp(final OAuthUserInfo userInfo, final SocialLoginServiceRequest request) {
        validateSignUpFields(request.nickName(), request.userRole(), request.phoneNumber());
        validateUserRole(request.userRole());

        UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO(userInfo.email(),
                request.name(),
                request.nickName(),
                request.phoneNumber(),
                userInfo.providerType(),
                userInfo.id(),
                request.userRole());

        return userCommandPort.registerBySocial(userSocialAccountDTO);
    }

    private void validateSignUpFields(final String nickName, final UserRole userRole, final String phoneNumber) {
        if (nickName == null || nickName.isBlank()) {
            throw new InvalidRequestException(ErrorCode.INVALID_NICK_NAME);
        }
        if (userQueryPort.existsByNickName(nickName)) {
            throw new DuplicateException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (userRole == null) {
            throw new InvalidRequestException(ErrorCode.INVALID_USER_ROLE);
        }
//        if (phoneNumber == null) {
//            throw new InvalidRequestException(ErrorCode.INVALID_PHONE_NUMBER);
//        }
//        if (!phoneNumber.matches("^(?!.*-)[0-9]{10,11}$")) {
//            throw new InvalidRequestException(ErrorCode.USER_VALID_PHONE);
//        }
    }

    private void validateUserRole(final UserRole userRole) {
        if (!userRole.isUserAssignable()) {
            throw new InvalidRequestException(ErrorCode.USER_ROLE_NOT_ASSIGNABLE);
        }
    }
}
