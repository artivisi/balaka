package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.security.CspNonceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Exposes CSP nonce to Thymeleaf templates as a model attribute.
 *
 * Makes the nonce available in templates via ${cspNonce} expression,
 * allowing inline scripts and styles to include the nonce attribute.
 *
 * Example usage in Thymeleaf:
 * <script th:attr="nonce=${cspNonce}">...</script>
 * <style th:attr="nonce=${cspNonce}">...</style>
 */
@Component
public class CspNonceInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, ModelAndView modelAndView) {
        if (modelAndView != null && !isRedirect(modelAndView)) {
            String nonce = (String) request.getAttribute(CspNonceFilter.CSP_NONCE_ATTRIBUTE);
            if (nonce != null) {
                modelAndView.addObject("cspNonce", nonce);
            }
        }
    }

    /** Attributes added to a redirect's model are appended to the Location URL as query parameters. */
    private static boolean isRedirect(ModelAndView modelAndView) {
        String viewName = modelAndView.getViewName();
        return (viewName != null && viewName.startsWith("redirect:"))
                || modelAndView.getView() instanceof RedirectView;
    }
}
