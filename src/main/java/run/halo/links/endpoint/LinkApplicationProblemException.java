package run.halo.links.endpoint;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class LinkApplicationProblemException extends ResponseStatusException {

    static final String INVALID_APPLICATION =
        "https://halo.run/probs/invalid-link-application";
    static final String INVALID_CAPTCHA =
        "https://halo.run/probs/invalid-link-application-captcha";
    static final String APPLICATION_DISABLED =
        "https://halo.run/probs/link-application-disabled";
    static final String DUPLICATE_APPLICATION =
        "https://halo.run/probs/duplicate-link-application";
    static final String CAPACITY_REACHED =
        "https://halo.run/probs/link-application-capacity-reached";
    static final String REQUEST_NOT_PERMITTED =
        "https://halo.run/probs/request-not-permitted";
    static final String APPLICATION_UNAVAILABLE =
        "https://halo.run/probs/link-application-unavailable";

    private LinkApplicationProblemException(HttpStatus status, String type, String detail) {
        super(status, detail);
        setType(URI.create(type));
        setDetail(detail);
    }

    static LinkApplicationProblemException invalidApplication(String message) {
        var exception = new LinkApplicationProblemException(HttpStatus.BAD_REQUEST,
            INVALID_APPLICATION, "提交内容不符合要求");
        exception.getBody().setProperty("errors", List.of(message));
        return exception;
    }

    static LinkApplicationProblemException invalidCaptcha() {
        return new LinkApplicationProblemException(HttpStatus.BAD_REQUEST, INVALID_CAPTCHA,
            "验证码错误或已过期，请重新获取");
    }

    static LinkApplicationProblemException disabled() {
        return new LinkApplicationProblemException(HttpStatus.FORBIDDEN, APPLICATION_DISABLED,
            "友链申请功能暂未开放");
    }

    static LinkApplicationProblemException duplicate() {
        return new LinkApplicationProblemException(HttpStatus.CONFLICT, DUPLICATE_APPLICATION,
            "该链接已提交申请");
    }

    static LinkApplicationProblemException capacityReached() {
        return new LinkApplicationProblemException(HttpStatus.CONFLICT, CAPACITY_REACHED,
            "待审核申请数量已达上限，请稍后再试");
    }

    static LinkApplicationProblemException rateLimited(long retryAfterSeconds) {
        var exception = new LinkApplicationProblemException(HttpStatus.TOO_MANY_REQUESTS,
            REQUEST_NOT_PERMITTED, "请求过于频繁，请稍后再试");
        exception.getBody().setProperty("retryAfterSeconds", Math.max(1, retryAfterSeconds));
        return exception;
    }

    static LinkApplicationProblemException unavailable() {
        return new LinkApplicationProblemException(HttpStatus.SERVICE_UNAVAILABLE,
            APPLICATION_UNAVAILABLE, "服务暂时不可用，请稍后再试");
    }
}
