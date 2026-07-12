package relish.relishAuthVelocity.utils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import relish.relishAuthVelocity.exceptions.PluginException;

public class Result<T> {
    private final T value;
    private final PluginException error;
    private final boolean success;

    private Result(T value, PluginException error, boolean success) {
        this.value = value;
        this.error = error;
        this.success = success;
    }

    public static <T> Result<T> success(T value) {
        return new Result<T>(value, null, true);
    }

    public static <T> Result<T> success() {
        return new Result<T>(null, null, true);
    }

    public static <T> Result<T> failure(PluginException error) {
        return new Result<T>(null, error, false);
    }

    public static <T> Result<T> failure(PluginException.ErrorCode errorCode, String message) {
        return new Result<T>(null, new PluginException(errorCode, message), false);
    }

    public static <T> Result<T> failure(PluginException.ErrorCode errorCode, String message, Throwable cause) {
        return new Result<T>(null, new PluginException(errorCode, message, cause), false);
    }

    public static <T> Result<T> of(ThrowingSupplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        }
        catch (PluginException e) {
            return Result.failure(e);
        }
        catch (Exception e) {
            return Result.failure(new PluginException(PluginException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }

    public static Result<Void> ofVoid(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return Result.success();
        }
        catch (PluginException e) {
            return Result.failure(e);
        }
        catch (Exception e) {
            return Result.failure(new PluginException(PluginException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }

    public boolean isSuccess() {
        return this.success;
    }

    public boolean isFailure() {
        return !this.success;
    }

    public T getValue() {
        return this.value;
    }

    public Optional<T> getValueOptional() {
        return Optional.ofNullable(this.value);
    }

    public PluginException getError() {
        return this.error;
    }

    public String getErrorMessage() {
        return this.error != null ? this.error.getMessage() : null;
    }

    public PluginException.ErrorCode getErrorCode() {
        return this.error != null ? this.error.getErrorCode() : null;
    }

    public T getOrDefault(T defaultValue) {
        return this.success ? this.value : defaultValue;
    }

    public T getOrThrow() throws PluginException {
        if (!this.success) {
            throw this.error;
        }
        return this.value;
    }

    public Result<T> onSuccess(Consumer<T> consumer) {
        if (this.success && this.value != null) {
            consumer.accept(this.value);
        }
        return this;
    }

    public Result<T> onFailure(Consumer<PluginException> consumer) {
        if (!this.success && this.error != null) {
            consumer.accept(this.error);
        }
        return this;
    }

    public <U> Result<U> map(Function<T, U> mapper) {
        if (this.success) {
            return Result.success(mapper.apply(this.value));
        }
        return Result.failure(this.error);
    }

    public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
        if (this.success) {
            return mapper.apply(this.value);
        }
        return Result.failure(this.error);
    }

    @FunctionalInterface
    public static interface ThrowingSupplier<T> {
        public T get() throws Exception;
    }

    @FunctionalInterface
    public static interface ThrowingRunnable {
        public void run() throws Exception;
    }
}
