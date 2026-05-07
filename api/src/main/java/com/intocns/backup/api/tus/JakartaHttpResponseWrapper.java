package com.intocns.backup.api.tus;

import com.intocns.backup.domain.port.http.HttpResponseWrapper;
import jakarta.servlet.http.HttpServletResponse;

public final class JakartaHttpResponseWrapper implements HttpResponseWrapper {

    private final HttpServletResponse delegate;

    public JakartaHttpResponseWrapper(HttpServletResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object raw() {
        return delegate;
    }
}
