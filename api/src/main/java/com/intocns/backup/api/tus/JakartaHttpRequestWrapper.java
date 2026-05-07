package com.intocns.backup.api.tus;

import com.intocns.backup.domain.port.http.HttpRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

public final class JakartaHttpRequestWrapper implements HttpRequestWrapper {

    private final HttpServletRequest delegate;

    public JakartaHttpRequestWrapper(HttpServletRequest delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object raw() {
        return delegate;
    }
}
