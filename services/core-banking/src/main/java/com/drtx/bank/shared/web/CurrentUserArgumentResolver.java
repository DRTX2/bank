package com.drtx.bank.shared.web;

import java.util.List;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controllers declare a {@link CurrentUser} parameter instead of digging into the security context. */
@Configuration(proxyBeanMethods = false)
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return CurrentUser.from(token.getToken());
        }
        throw new IllegalStateException("CurrentUser requested on an unauthenticated endpoint");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }
}
