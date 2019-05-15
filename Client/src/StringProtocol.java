import org.smartboot.socket.Protocol;
import org.smartboot.socket.transport.AioSession;

import java.nio.ByteBuffer;

public class StringProtocol implements Protocol<String> {
    @Override
    public String decode(ByteBuffer buffer, AioSession<String> session) {
        buffer.mark();
        byte length = buffer.get();
        if (buffer.remaining() < length) {
            buffer.reset();
            return null;
        }
        byte[] body = new byte[length];
        buffer.get(body);
        buffer.mark();
        return new String(body);
    }
}