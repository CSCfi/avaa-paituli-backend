package avaa.model;

import javax.validation.ConstraintViolation;
import java.util.Set;
import java.util.stream.Collectors;

public class Result {

    private String message;

    public Result(String message) {
        this.message = message;
    }

    public Result(Set<? extends ConstraintViolation<?>> violations) {
        this.message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
    }

    public String getMessage() {
        return message;
    }
}
