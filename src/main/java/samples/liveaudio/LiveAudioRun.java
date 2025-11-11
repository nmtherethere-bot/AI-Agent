package samples.liveaudio;

import com.google.adk.agents.LiveRequestQueue;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.agents.RunConfig;
// Optional speech config imports removed due to current RunConfig API
// import com.google.genai.types.Modality;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiveAudioRun {

  public void runConversation() {
    ScienceTeacherAgent app = new ScienceTeacherAgent();

    InMemoryRunner runner = new InMemoryRunner(ScienceTeacherAgent.ROOT_AGENT);
    String userId = "local-user";
    var session = runner.sessionService().createSession(runner.appName(), userId).blockingGet();

    LiveRequestQueue liveRequestQueue = new LiveRequestQueue();

    RunConfig runConfig = RunConfig.builder().build();
    Flowable<Event> events = runner.runLive(session, liveRequestQueue, runConfig);

    AtomicBoolean isRunning = new AtomicBoolean(true);
    AtomicBoolean conversationEnded = new AtomicBoolean(false);

    Thread micThread = new Thread(() -> app.captureAndSendMicrophoneAudio(liveRequestQueue, isRunning));
    micThread.setName("mic-stream-" + UUID.randomUUID());
    micThread.start();

    try {
      app.processAudioOutput(events, isRunning, conversationEnded);
    } finally {
      // Signal end of input and cleanup
      isRunning.set(false);
      liveRequestQueue.close();
    }
  }

  public static void main(String[] args) {
    new LiveAudioRun().runConversation();
    System.out.println("Exiting Live Audio Run.");
  }
}