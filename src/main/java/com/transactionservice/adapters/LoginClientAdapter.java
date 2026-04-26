package com.transactionservice.adapters;

import com.transactionservice.domains.LoginClientDomain;
import com.transactionservice.model.session.SessionDTO;
import com.transactionservice.infrastructure.client.LoginClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginClientAdapter implements LoginClientDomain {

    private final LoginClient loginClient;

    @Override
    public SessionDTO getSession(String token) {
        return loginClient.getSession(token);
    }
}
