
import edu.cwru.sepia.action.Action;
import java.util.List;


public class scheduleOutput {
    List<Action> actions;
    int makeSpan;

    public scheduleOutput(List<Action> actions, int makeSpan) {
        this.actions = actions;
        this.makeSpan = makeSpan;
    }
}
