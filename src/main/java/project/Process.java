package project;
import akka.actor.ActorRef;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import project.Messages.CrashMessage;
import project.Messages.LaunchMessage;
import project.Messages.ReadRequest;
import project.Messages.ReadResponse;
import project.Messages.WriteAck;
import project.Messages.WriteRequest;

import java.util.HashSet;
import java.util.Set;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    
    private int localValue = 0;
    private int localTS = 0;
    private int r = 0; // Sequence number for operations
    
    // Configuration
    private final int id;
    private final int N; // number of processes
    private final int M; // number of operations to perform
    private ActorRef[] processes;
    
    // Temporal state
    private boolean operatingPut = false;
    private int currentPutRequestNumber = 0;
    private boolean operatingGet = false;
    private int currentGetRequestNumber = 0;
    private Set<ReadResponse> readResponsesPut = new HashSet<>();
    private Set<ReadResponse> readResponsesGet = new HashSet<>();
    private Set<ActorRef> writeAcksPut = new HashSet<>();
    private Set<ActorRef> writeAcksGet = new HashSet<>();

    // operations counters
    private int putOperationsDone = 0;
    private int getOperationsDone = 0;
    
    public Process(int id, int N, int M, ActorRef[] processes) {
        this.id = id;
        this.N = N;
        this.M = M;
        this.processes = processes;
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof LaunchMessage) {
            log.info("Process {} launched", getSelf().path().name());
            // Start the first operation
            doNextOperation();
        } else if (message instanceof CrashMessage) {
            log.info("Process {} crashed", getSelf().path().name());
            getContext().stop(getSelf());
        } else if (message instanceof ReadRequest) {
            handleReadRequest((ReadRequest) message);
        } else if (message instanceof ReadResponse) {
            handleReadResponse((ReadResponse) message);
        } else if (message instanceof WriteRequest) {
            handleWriteRequest((WriteRequest) message);
        } else if (message instanceof WriteAck) {
            handleWriteAck((WriteAck) message);
        } else if (message instanceof Messages.updateProcessListMessage) {
            Messages.updateProcessListMessage msg = (Messages.updateProcessListMessage) message;
            this.processes = msg.process;
        }
    }

    private void doNextOperation() {
        // Implementation of operation scheduling 
        if (putOperationsDone < M) {
            Put(id*N + putOperationsDone + 1);
            putOperationsDone++;
        } else if (getOperationsDone < M) {
            Get();
            getOperationsDone++;
        } else {
            log.info("Process {} completed all operations", getSelf().path().name());
        }
    }

    private void handleReadRequest(ReadRequest msg) {
        log.info("Process {} received ReadRequest r={}", getSelf().path().name(), msg.r);
        getSender().tell(new ReadResponse(localValue, localTS, msg.r), getSelf());
    }

    private void handleWriteRequest(WriteRequest msg) {
        log.info("Process {} received WriteRequest v={}, t={}, r={}", getSelf().path().name(), msg.v, msg.t, msg.r);
        if (msg.t > localTS || (msg.t == localTS && msg.v > localValue)) {
            localValue = msg.v;
            localTS = msg.t;
        }
        getSender().tell(new WriteAck(localValue, localTS, msg.r), getSelf());
    }

    private void handleReadResponse(ReadResponse msg) {
        log.info("Process {} received ReadResponse v={}, t={}, r={}", getSelf().path().name(), msg.v, msg.t, msg.r);
        if (operatingPut && msg.r == currentPutRequestNumber) {
            readResponsesPut.add(msg);
            if (readResponsesPut.size() > N / 2) {
                // Majority reached for Put
                int maxTS = -1;
                for (ReadResponse resp : readResponsesPut) {
                    if (resp.t > maxTS) {
                        maxTS = resp.t;
                    }
                }
                localTS = maxTS + 1;
                for (ActorRef p : processes) {
                    p.tell(new WriteRequest(localValue, localTS, currentPutRequestNumber), getSelf());
                }
                readResponsesPut.clear();
            }
        } else if (operatingGet && msg.r == currentGetRequestNumber) {
            readResponsesGet.add(msg);
            if (readResponsesGet.size() > N / 2) {
                // Majority reached for Get
                int maxTS = -1;
                int valueToGet = 0;
                for (ReadResponse resp : readResponsesGet) {
                    if (resp.t > maxTS) {
                        maxTS = resp.t;
                        valueToGet = resp.v;
                    }
                }
                localTS = maxTS;
                for (ActorRef p : processes) {
                    p.tell(new WriteRequest(valueToGet, localTS, currentGetRequestNumber), getSelf());
                }
                readResponsesGet.clear();
            }
        }
    }

    private void handleWriteAck(WriteAck msg) {
        log.info("Process {} received WriteAck v={}, t={}", getSelf().path().name(), msg.v, msg.t);
        if (operatingPut && msg.r == currentPutRequestNumber) {
            writeAcksPut.add(getSender());
            if (writeAcksPut.size() > N / 2) {
                // Majority reached for Put
                operatingPut = false;
                writeAcksPut.clear();
                log.info("Process {} completed Put operation with value {}", getSelf().path().name(), localValue);

                // Start next operation
                doNextOperation();
            }
        } else if (operatingGet && msg.r == currentGetRequestNumber) {
            writeAcksGet.add(getSender());
            if (writeAcksGet.size() > N / 2) {
                // Majority reached for Get
                operatingGet = false;
                writeAcksGet.clear();
                log.info("Process {} completed Get operation with value {}", getSelf().path().name(), msg.v);

                // Start next operation
                doNextOperation();
            }
        }
    }

    public void Put(int value) {
        if (operatingPut || operatingGet) {
            log.warning("Process {} is already operating", getSelf().path().name());
            return;
        }
        log.info("Process {} starting Put operation with value {}", getSelf().path().name(), value);
        operatingPut = true;
        currentPutRequestNumber = id * N + ++r;
        localValue = value;
        readResponsesPut.clear();
        writeAcksPut.clear();
        for (ActorRef p : processes) {
            p.tell(new ReadRequest(currentPutRequestNumber), getSelf());
        }

        // then wait for responses in handleReadResponse
        // when majority is reached, send WriteRequest to all processes
        // then wait for WriteAck in handleWriteAck
    }

    public void Get() {
        if (operatingPut || operatingGet) {
            log.warning("Process {} is already operating", getSelf().path().name());
            return;
        }
        log.info("Process {} starting Get operation", getSelf().path().name());
        operatingGet = true;
        currentGetRequestNumber = id * N + ++r;
        readResponsesGet.clear();
        writeAcksGet.clear();
        for (ActorRef p : processes) {
            p.tell(new ReadRequest(currentGetRequestNumber), getSelf());
        }

        // then wait for responses in handleReadResponse
        // when majority is reached, send WriteRequest with the value of highest timestamp to all processes
        // then wait for WriteAck in handleWriteAck
    }
}