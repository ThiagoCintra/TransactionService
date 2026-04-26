package com.transactionservice.domains;

import com.transactionservice.model.session.SessionDTO;

/**
 * Domain interface for fetching session information from authentication service.
 */
public interface LoginClientDomain {

    SessionDTO getSession(String token);

}
