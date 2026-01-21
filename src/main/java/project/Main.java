package project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import project.Messages.CrashMessage;
import project.Messages.LaunchMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        int N = 3;  // number of processes
        int f = 1;  // number of faulty processes
        int M = 3;  // number of operations per process

        final ActorSystem system = ActorSystem.create("ABD-System");
        ActorRef[] processes = new ActorRef[N];

        System.out.println("=== SYSTEM START (N=" + N + ", f=" + f + ", M=" + M + ") ===");

        for (int i = 0; i < N; i++) {
            processes[i] = system.actorOf(Props.create(Process.class, i, N, M, processes), "process-" + i);
        }

        for (int i = 0; i < N; i++) {
            // Send the full list of processes to each process
            processes[i].tell(new Messages.updateProcessListMessage(processes), ActorRef.noSender());
        }

        // Pause to ensure all processes are created before proceeding
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // Crash f processes
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < N; i++) indices.add(i);
        Collections.shuffle(indices); // Mélange aléatoire

        List<Integer> faulty = indices.subList(0, f);
        List<Integer> correct = indices.subList(f, N);

        System.out.println("Faulty processes: " + faulty);
        System.out.println("Correct processes: " + correct);

        for (int i : faulty) {
            processes[i].tell(new CrashMessage(), ActorRef.noSender());
        }

        // Launch operations on correct processes
        System.out.println(">>> Launching operations...");

        for (int i : correct) {
            processes[i].tell(new LaunchMessage(), ActorRef.noSender());
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}