package project;

import java.io.Serializable;
import akka.actor.ActorRef;

public class Messages {

    public static class LaunchMessage implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public static class CrashMessage implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public static class ReadRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int r; // Sequence number for the read operation

        public ReadRequest(int r) {
            this.r = r;
        }
    }

    public static class ReadResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int v; // Value read
        public final int t; // Timestamp of the value
        public final int r; // Sequence number for the read operation

        public ReadResponse(int v, int t, int r) {
            this.v = v;
            this.t = t;
            this.r = r;
        }
    }

    public static class WriteRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int v; // Value to write
        public final int t; // Timestamp of the value
        public final int r; // Sequence number for the write operation

        public WriteRequest(int v, int t, int r) {
            this.v = v;
            this.t = t;
            this.r = r;
        }
    }

    public static class WriteAck implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int v; // Value acknowledged
        public final int t; // Timestamp of the value
        public final int r; // Sequence number for the write operation

        public WriteAck(int v, int t, int r) {
            this.v = v;
            this.t = t;
            this.r = r;
        }
    }

    public static class updateProcessListMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public final ActorRef[] process;

        public updateProcessListMessage(ActorRef[] process) {
            this.process = process;
        }
    }
}