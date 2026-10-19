package io.github.jahn2007.edifierx.core;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
public interface Transport extends AutoCloseable {
    CompletionStage<Void> connect(Consumer<byte[]> receiver, Consumer<Throwable> failure);
    CompletionStage<Void> send(byte[] bytes);
    @Override void close();
}
