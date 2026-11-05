package com.carddemo.online.session;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controller methods declare a {@link SessionUser} parameter to receive the signed-on user. */
@Configuration
public class SessionWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return SessionUser.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                    NativeWebRequest request, WebDataBinderFactory binderFactory) {
                return request.getAttribute(SessionAuthFilter.SESSION_USER_ATTR, RequestAttributes.SCOPE_REQUEST);
            }
        });
    }
}
