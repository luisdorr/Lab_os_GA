package com.meuapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.sleep;

class Recurso {
    private int capacidadeCPU;
    private int capacidadeRAM;
    private int capacidadeDisco;

    public Recurso(int capacidadeCPU, int capacidadeRAM, int capacidadeDisco) {
        this.capacidadeCPU = capacidadeCPU;
        this.capacidadeRAM = capacidadeRAM;
        this.capacidadeDisco = capacidadeDisco;
    }

    public int getCapacidadeCPU() {
        return capacidadeCPU;
    }

    public int getCapacidadeRAM() {
        return capacidadeRAM;
    }

    public int getCapacidadeDisco() {
        return capacidadeDisco;
    }

    public synchronized void liberarRecursos(Recurso recurso) {
        this.capacidadeCPU += recurso.getCapacidadeCPU();
        this.capacidadeRAM += recurso.getCapacidadeRAM();
        this.capacidadeDisco += recurso.getCapacidadeDisco();
    }

    public synchronized void alocarRecursos(Recurso recurso) {
        this.capacidadeCPU -= recurso.getCapacidadeCPU();
        this.capacidadeRAM -= recurso.getCapacidadeRAM();
        this.capacidadeDisco -= recurso.getCapacidadeDisco();
    }
}

class Pod extends Thread {
    private String nome;
    private Recurso requisitosRecursos;
    private Worker worker;
    private EstadoPod estado;

    public enum EstadoPod {
        INICIANDO,
        EXECUTANDO,
        FINALIZADO
    }

    public Pod(String nome, Recurso requisitosRecursos) {
        this.nome = nome;
        this.requisitosRecursos = requisitosRecursos;
        this.estado = EstadoPod.INICIANDO;
    }

    public String getNome() {
        return nome;
    }

    public Recurso getRequisitosRecursos() {
        return requisitosRecursos;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public EstadoPod getEstado() {
        return estado;
    }

    @Override
    public void run() {
        try {
            System.out.println("Iniciando " + nome + " em " + worker.getNome());
            sleep(10000); // 10 segundos no estado de inicialização
            estado = EstadoPod.EXECUTANDO;
            sleep(60000); // 60 segundos no estado de execução
            estado = EstadoPod.FINALIZADO;
            worker.podConcluido(this);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Worker extends Thread {
    private String nome;
    private Recurso recursosMaximos; // Recursos máximos do Worker
    private Recurso recursosDisponiveis; // Recursos disponíveis para alocar pods
    List<Pod> listaPods = new ArrayList<>();
    ReentrantLock lock = new ReentrantLock();

    public Worker(String nome, Recurso recursos) {
        this.nome = nome;
        this.recursosMaximos = recursos;
        this.recursosDisponiveis = new Recurso(recursos.getCapacidadeCPU(), recursos.getCapacidadeRAM(), recursos.getCapacidadeDisco());
    }

    public String getNome() {
        return nome;
    }

    public Recurso getRecursosMaximos() {
        return recursosMaximos;
    }

    public Recurso getRecursosDisponiveis() {
        return recursosDisponiveis;
    }

    public synchronized boolean podeExecutarPod(Recurso requisitos) {
        // Verifica se os recursos disponíveis são suficientes
        return recursosDisponiveis.getCapacidadeCPU() >= requisitos.getCapacidadeCPU() &&
                recursosDisponiveis.getCapacidadeRAM() >= requisitos.getCapacidadeRAM() &&
                recursosDisponiveis.getCapacidadeDisco() >= requisitos.getCapacidadeDisco();
    }

    public synchronized void executarPod(Pod pod) {
        lock.lock();
        try {
            if (podeExecutarPod(pod.getRequisitosRecursos())) {
                recursosDisponiveis.alocarRecursos(pod.getRequisitosRecursos());
                listaPods.add(pod);
                pod.setWorker(this);
                pod.start();
            }
        } finally {
            lock.unlock();
        }
    }

    public synchronized void podConcluido(Pod pod) {
        if (pod.getEstado() == Pod.EstadoPod.FINALIZADO) {
            lock.lock();
            try {
                if (listaPods.contains(pod)) {
                    recursosDisponiveis.liberarRecursos(pod.getRequisitosRecursos());
                }
            } finally {
                lock.unlock();
            }
        }
    }
}

class ClusterManager {
    private List<Worker> workers = new ArrayList<>();
    private ExecutorService pool = Executors.newFixedThreadPool(2);
    private Recurso recursos = new Recurso(4, 8000, 20000);
    private int podsNoCluster = 0;

    public ClusterManager() {
        workers.add(new Worker("Worker1", new Recurso(12, 16000, 20000)));
        workers.add(new Worker("Worker2", new Recurso(16, 20000, 20000)));
    }

    public int getPodsNoCluster() {
        return podsNoCluster;
    }

    public void setPodsNoCluster(int podsNoCluster) {
        this.podsNoCluster = podsNoCluster;
    }

    public Recurso getRecursos() {
        return recursos;
    }

    public void adicionarWorker(Worker worker) {
        workers.add(worker);
    }

    public Worker getWorkerComMaiorPontuacao() {
        Worker workerComMaiorPontuacao = null;
        double maiorPontuacao = -1;

        for (Worker worker : workers) {
            double pontuacao = worker.getRecursosDisponiveis().getCapacidadeCPU() * 0.3
                    + worker.getRecursosDisponiveis().getCapacidadeRAM() * 0.2
                    + worker.getRecursosDisponiveis().getCapacidadeDisco() * 0.1;

            if (pontuacao > maiorPontuacao) {
                maiorPontuacao = pontuacao;
                workerComMaiorPontuacao = worker;
            }
        }

        return workerComMaiorPontuacao;
    }

    public void adicionarPod(Pod pod) {

        Worker workerComMaiorPontuacao = getWorkerComMaiorPontuacao();

        if (workerComMaiorPontuacao != null && workerComMaiorPontuacao.podeExecutarPod(pod.getRequisitosRecursos())) {
            workerComMaiorPontuacao.executarPod(pod);
            setPodsNoCluster(getPodsNoCluster() + 1);
        } else {
            Worker novoWorker = new Worker("Worker" + (workers.size() + 1), new Recurso(12, 16000, 20000));
            adicionarWorker(novoWorker);
            novoWorker.executarPod(pod);
            setPodsNoCluster(getPodsNoCluster() + 1);
        }
    }

    public void mostrarInfoWorkers() {
        System.out.println("++++++++++++++++++++++++++++++++++    WORKERS   +++++++++++++++++++++++++++++++");
        System.out.println("NOME\t\tCPU (em uso | Total)\tMEMÓRIA (em uso | Total)\tDISCO (em uso | Total)");
        for (Worker worker : workers) {
            int cpuEmUso = worker.getRecursosMaximos().getCapacidadeCPU() - worker.getRecursosDisponiveis().getCapacidadeCPU();
            int cpuTotal = worker.getRecursosMaximos().getCapacidadeCPU();
            int ramEmUso = worker.getRecursosMaximos().getCapacidadeRAM() - worker.getRecursosDisponiveis().getCapacidadeRAM();
            int ramTotal = worker.getRecursosMaximos().getCapacidadeRAM();
            int discoEmUso = worker.getRecursosMaximos().getCapacidadeDisco() - worker.getRecursosDisponiveis().getCapacidadeDisco();
            int discoTotal = worker.getRecursosMaximos().getCapacidadeDisco();

            System.out.println(worker.getNome() + "\t\t[" + cpuEmUso + " | " + cpuTotal + "]\t\t\t\t[" +
                    ramEmUso + " | " + ramTotal + "]\t\t\t\t[" +
                    discoEmUso + " | " + discoTotal + "]");
        }

        System.out.println("\n++++++++++++++++++++++++++++++++++    PODS  +++++++++++++++++++++++++++++++");
        System.out.println("NOME\t\tCPU\t\tMEMÓRIA\t\tDISCO\t\tESTADO\t\tNÓ");
        for (Worker worker : workers) {
            for (Pod pod : worker.listaPods) {
                System.out.println(pod.getNome() + "\t\t" + pod.getRequisitosRecursos().getCapacidadeCPU() + "\t\t" +
                        pod.getRequisitosRecursos().getCapacidadeRAM() + "\t\t" + pod.getRequisitosRecursos().getCapacidadeDisco() +
                        "\t\t" + pod.getEstado() + "\t\t" + worker.getNome());
            }
        }
    }
}

public class SimuladorKubernetes {
    public static void main(String[] args) throws InterruptedException {
        ClusterManager clusterManager = new ClusterManager();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Criar Pods");
            System.out.println("2. Mostrar informações dos Workers");
            System.out.println("3. Parar o programa");
            System.out.print("Escolha uma opção: ");

            int opcao = scanner.nextInt();

            switch (opcao) {
                case 1:
                    System.out.print("Quantos pods deseja criar? ");
                    int numPods = scanner.nextInt();
                    criarPods(clusterManager, numPods);
                    sleep(1000);
                    break;
                case 2:
                    clusterManager.mostrarInfoWorkers();
                    break;
                case 3:
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    private static void criarPods(ClusterManager clusterManager, int numPods) {
        for (int i = 1; i <= numPods; i++) {
            Pod pod = new Pod("Pod" + clusterManager.getPodsNoCluster(), new Recurso((int) (Math.random() * 2 + 1), (int) (Math.random() * 2000 + 1001), (int) (Math.random() * 200 + 1)));
            clusterManager.adicionarPod(pod);
        }
    }
}
