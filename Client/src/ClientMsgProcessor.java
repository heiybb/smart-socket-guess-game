import org.smartboot.socket.MessageProcessor;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.transport.AioSession;

import static org.smartboot.socket.StateMachineEnum.SESSION_CLOSED;

/**
 * @author BOBIN YUAN s3677943@student.rmit.edu.au
 * <p>
 * Handle the message the client receive and the state event
 */
public class ClientMsgProcessor implements MessageProcessor<String> {

    @Override
    public void process(AioSession<String> session, String msg) {
        System.out.println(msg);
        if (msg.startsWith("Game round start") && Client.AUTO_GUESS) {
            System.out.println("Start auto guess process");
            Client.autoGuess();
        }

    }

    @Override
    public void stateEvent(AioSession<String> session, StateMachineEnum stateMachineEnum, Throwable throwable) {
        if (stateMachineEnum == SESSION_CLOSED) {
            System.out.println("Remote connection closed, quit game now");
            System.exit(0);
        }
    }
}